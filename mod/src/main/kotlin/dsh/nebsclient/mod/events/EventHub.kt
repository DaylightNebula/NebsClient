package dsh.nebsclient.mod.events

import dsh.nebsclient.core.MessageCodec
import dsh.nebsclient.core.message.Event
import dsh.nebsclient.mod.runtime.Ticker
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * Keeps the chat history (for `chat-history` / `wait-for-chat`) and pushes [Event]s to
 * `subscribe`d connections.
 */
object EventHub {
    data class ChatLine(val seq: Long, val time: Long, val type: String, val sender: String?, val text: String) {
        fun toJson(): JsonObject = buildJsonObject {
            put("seq", seq)
            put("time", time)
            put("type", type)
            sender?.let { put("sender", it) }
            put("text", text)
        }
    }

    private const val HISTORY = 500
    private val history = ArrayDeque<ChatLine>()
    private val seq = AtomicLong()

    private class Subscriber(val types: Set<String>, val write: (String) -> Unit)

    private val subscribers = CopyOnWriteArrayList<Subscriber>()

    fun init() {
        ClientReceiveMessageEvents.CHAT.register { message, _, sender, _, _ -> record("chat", sender?.name(), message.string) }
        ClientReceiveMessageEvents.GAME.register { message, overlay -> record(if (overlay) "action-bar" else "system", null, message.string) }
        ClientPlayConnectionEvents.JOIN.register { _, _, mc -> publish("join") { put("server", mc.currentServer?.ip ?: "") } }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> publish("disconnect") {} }
        Ticker.onTick(::watch)
    }

    // --- chat history ---

    private fun record(type: String, sender: String?, text: String) {
        val line = ChatLine(seq.incrementAndGet(), System.currentTimeMillis(), type, sender, text)
        synchronized(history) {
            history.addLast(line)
            if (history.size > HISTORY) history.removeFirst()
        }
        publish(type) {
            put("seq", line.seq)
            sender?.let { put("sender", it) }
            put("message", text)
        }
    }

    fun lastSeq(): Long = seq.get()

    fun history(count: Int): List<ChatLine> = synchronized(history) { history.takeLast(count) }

    /** Waits for a line after [since] matching [regex]. */
    fun awaitChat(regex: Regex, since: Long, timeoutMs: Long): ChatLine {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            synchronized(history) { history.firstOrNull { it.seq > since && regex.containsMatchIn(it.text) } }?.let { return it }
            Thread.sleep(50)
        }
        throw IllegalStateException("No chat message matching '${regex.pattern}' within ${timeoutMs / 1000.0}s")
    }

    // --- subscriptions ---

    /** Sends every event in [types] (all when empty) to [write] until the returned handle is closed or a write fails. */
    fun subscribe(types: Collection<String>, write: (String) -> Unit): AutoCloseable {
        val subscriber = Subscriber(types.ifEmpty { Event.TYPES }.toSet(), write)
        subscribers += subscriber
        return AutoCloseable { subscribers -= subscriber }
    }

    private fun publish(type: String, data: JsonObjectBuilder.() -> Unit) {
        val targets = subscribers.filter { type in it.types }
        if (targets.isEmpty()) return
        val line = MessageCodec.encode(Event(type, buildJsonObject(data), System.currentTimeMillis()))
        targets.forEach {
            try {
                it.write(line)
            } catch (_: IOException) {
                subscribers -= it
            }
        }
    }

    // --- state watchers (run every tick while anyone is subscribed) ---

    private var health = -1f
    private var food = -1
    private var dead = false
    private var screen: String? = null
    private var inventoryHash = 0
    private var ticks = 0

    private fun watch(mc: Minecraft) {
        if (subscribers.isEmpty()) return
        val player = mc.player
        if (player != null) {
            if (player.health != health || player.foodData.foodLevel != food) {
                health = player.health
                food = player.foodData.foodLevel
                publish("health") {
                    put("health", health)
                    put("food", food)
                }
            }
            if (player.isDeadOrDying != dead) {
                dead = player.isDeadOrDying
                publish(if (dead) "death" else "respawn") {}
            }
            if (++ticks % 10 == 0) {
                val inv = player.inventory
                val hash = (0 until inv.containerSize).fold(1) { h, i ->
                    val stack = inv.getItem(i)
                    31 * h + if (stack.isEmpty) 0 else stack.item.hashCode() * 64 + stack.count
                }
                if (hash != inventoryHash) {
                    if (inventoryHash != 0) publish("inventory") {}
                    inventoryHash = hash
                }
            }
        }
        val current = mc.gui.screen()
        val name = current?.javaClass?.simpleName
        if (name != screen) {
            screen = name
            publish("screen") {
                put("screen", name ?: "none")
                current?.let { put("title", it.title.string) }
            }
        }
    }
}
