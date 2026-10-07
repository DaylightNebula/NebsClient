package dsh.nebsclient.mod.runtime

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** A command needs the player, but the client isn't in a world. */
class NotInWorldException : IllegalStateException("Not in a world; connect to a server first")

/** Flags read by mixins. */
object Hooks {
    /** While true, the game behaves as if the attack button were held (see MinecraftMixin). */
    @JvmField
    @Volatile
    var forceAttack = false
}

/** Runs code on the client thread from socket threads. */
object ClientThread {
    val minecraft: Minecraft get() = Minecraft.getInstance()

    /** Runs [block] on the client thread and returns its result. */
    fun <T> call(timeoutMs: Long = 5_000, block: (Minecraft) -> T): T {
        val mc = minecraft
        if (mc.isSameThread) return block(mc)
        val future = CompletableFuture.supplyAsync({ block(mc) }, mc) // Minecraft is an Executor for its own thread
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            throw IllegalStateException("The client didn't respond in time (is it still loading?)")
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }

    /** Like [call], but fails with [NotInWorldException] unless a player exists. */
    fun <T> withPlayer(timeoutMs: Long = 5_000, block: (Minecraft, LocalPlayer) -> T): T =
        call(timeoutMs) { mc -> block(mc, mc.player ?: throw NotInWorldException()) }
}

/**
 * Per-tick tasks for commands that take time (moving, mining, waiting). A task's step runs on the
 * client thread at the end of every tick until it returns a value or throws.
 */
object Ticker {
    private class Task<T : Any>(val step: (Minecraft) -> T?, val future: CompletableFuture<T>)

    private val tasks = CopyOnWriteArrayList<Task<*>>()
    private val tickListeners = CopyOnWriteArrayList<(Minecraft) -> Unit>()

    fun init() {
        ClientTickEvents.START_CLIENT_TICK.register { Input.apply() }
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            tasks.forEach { run(it, mc) }
            tickListeners.forEach { it(mc) }
        }
    }

    /** Calls [listener] at the end of every tick, forever. */
    fun onTick(listener: (Minecraft) -> Unit) {
        tickListeners += listener
    }

    /** Starts a task; the returned future completes with the step's first non-null result. Cancel it to stop the task. */
    fun <T : Any> start(step: (Minecraft) -> T?): CompletableFuture<T> {
        val task = Task(step, CompletableFuture<T>())
        tasks += task
        task.future.whenComplete { _, _ -> tasks.remove(task) }
        return task.future
    }

    /** Waits for [future]; on timeout cancels it and fails with [timeoutMessage]. */
    fun <T> await(future: CompletableFuture<T>, timeoutMs: Long, timeoutMessage: String): T = try {
        future.get(timeoutMs, TimeUnit.MILLISECONDS)
    } catch (_: TimeoutException) {
        future.cancel(false)
        throw IllegalStateException(timeoutMessage)
    } catch (_: java.util.concurrent.CancellationException) {
        throw IllegalStateException("Interrupted by another command")
    } catch (e: ExecutionException) {
        throw e.cause ?: e
    }

    /** [start] + [await]. */
    fun <T : Any> run(timeoutMs: Long, timeoutMessage: String, step: (Minecraft) -> T?): T =
        await(start(step), timeoutMs, timeoutMessage)

    private fun <T : Any> run(task: Task<T>, mc: Minecraft) {
        if (task.future.isDone) return
        try {
            task.step(mc)?.let { task.future.complete(it) }
        } catch (e: Exception) {
            task.future.completeExceptionally(e)
        }
    }
}

/**
 * Keys held down by commands. They are re-pressed at the start of every tick, because the game
 * releases keys on its own (e.g. when a screen opens or the window loses focus).
 */
object Input {
    private val held = ConcurrentHashMap.newKeySet<KeyMapping>()

    fun hold(key: KeyMapping) {
        held += key
        key.setDown(true)
    }

    fun release(key: KeyMapping) {
        if (held.remove(key)) key.setDown(false)
    }

    fun isHeld(key: KeyMapping) = key in held

    fun releaseAll() {
        held.forEach { it.setDown(false) }
        held.clear()
    }

    internal fun apply() = held.forEach { it.setDown(true) }
}

/**
 * The single movement command in progress (move, goto, follow). Starting another, or `stop`,
 * cancels it; its keys are released when it ends.
 */
object Movement {
    @Volatile
    private var current: CompletableFuture<*>? = null

    fun <T> begin(future: CompletableFuture<T>, vararg keys: KeyMapping): CompletableFuture<T> {
        current?.let { if (it !== future) it.cancel(false) }
        current = future
        future.whenComplete { _, _ -> keys.forEach(Input::release) }
        return future
    }

    fun cancel() {
        current?.cancel(false)
        current = null
    }
}
