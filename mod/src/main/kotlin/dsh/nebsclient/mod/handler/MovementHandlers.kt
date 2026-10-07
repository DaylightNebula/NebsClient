package dsh.nebsclient.mod.handler

import dsh.nebsclient.core.message.Follow
import dsh.nebsclient.core.message.Goto
import dsh.nebsclient.core.message.Look
import dsh.nebsclient.core.message.LookAt
import dsh.nebsclient.core.message.Move
import dsh.nebsclient.core.message.Response
import dsh.nebsclient.mod.runtime.ClientThread
import dsh.nebsclient.mod.runtime.Hooks
import dsh.nebsclient.mod.runtime.Input
import dsh.nebsclient.mod.runtime.Json
import dsh.nebsclient.mod.runtime.Json.round2
import dsh.nebsclient.mod.runtime.Movement
import dsh.nebsclient.mod.runtime.NotInWorldException
import dsh.nebsclient.mod.runtime.Ticker
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2
import kotlin.math.sqrt

object MovementHandlers {
    /** Points the player at [yaw]/[pitch] without the usual interpolation from the old rotation. */
    fun setRotation(player: LocalPlayer, yaw: Float, pitch: Float) {
        player.yRot = yaw
        player.xRot = pitch.coerceIn(-90f, 90f)
        player.yRotO = player.yRot
        player.xRotO = player.xRot
        player.yHeadRot = yaw
        player.yHeadRotO = yaw
    }

    fun yawTo(dx: Double, dz: Double): Float = Math.toDegrees(atan2(-dx, dz)).toFloat()

    /** Turns the player's eyes toward [target]. */
    fun lookAt(player: LocalPlayer, target: Vec3) {
        val eye = player.eyePosition
        val dx = target.x - eye.x
        val dy = target.y - eye.y
        val dz = target.z - eye.z
        setRotation(player, yawTo(dx, dz), (-Math.toDegrees(atan2(dy, sqrt(dx * dx + dz * dz)))).toFloat())
    }

    private fun key(mc: Minecraft, direction: String): KeyMapping = when (direction) {
        "forward" -> mc.options.keyUp
        "back" -> mc.options.keyDown
        "left" -> mc.options.keyLeft
        else -> mc.options.keyRight
    }

    fun look(m: Look): Response = ClientThread.withPlayer { _, p ->
        setRotation(p, m.yaw, m.pitch)
        Response.ok("Looking at yaw ${m.yaw}, pitch ${m.pitch}")
    }

    fun lookAt(m: LookAt): Response = ClientThread.withPlayer { _, p ->
        lookAt(p, Vec3(m.x, m.y, m.z))
        Response.ok("Looking at ${m.x} ${m.y} ${m.z} (yaw ${round2(p.yRot.toDouble())}, pitch ${round2(p.xRot.toDouble())})")
    }

    fun move(m: Move): Response {
        val key = ClientThread.withPlayer { mc, _ -> key(mc, m.direction) }
        val limit = m.ticks
        var ticks = 0
        val future = Movement.begin(
            Ticker.start { mc ->
                if (mc.player == null) throw NotInWorldException()
                Input.hold(key)
                if (limit != null && ++ticks >= limit) Unit else null
            },
            key,
        )
        if (limit == null) return Response.ok("Holding ${m.direction} until 'stop'")
        Ticker.await(future, limit * 50L + 10_000, "Movement didn't finish")
        return Response.ok("Moved ${m.direction} for $limit ticks")
    }

    fun jump(): Response {
        val key = ClientThread.withPlayer { mc, p ->
            if (!p.onGround() && !p.isInWater) throw IllegalStateException("Can't jump: not on the ground")
            mc.options.keyJump
        }
        var ticks = 0
        try {
            Ticker.run(5_000, "Jump didn't finish") {
                Input.hold(key)
                if (++ticks >= 2) Unit else null
            }
        } finally {
            Input.release(key)
        }
        return Response.ok("Jumped")
    }

    fun sneak(on: Boolean): Response = holdToggle(on, "sneak") { it.options.keyShift }

    fun sprint(on: Boolean): Response = holdToggle(on, "sprint") { it.options.keySprint }

    private fun holdToggle(on: Boolean, what: String, key: (Minecraft) -> KeyMapping): Response = ClientThread.withPlayer { mc, _ ->
        if (on) Input.hold(key(mc)) else Input.release(key(mc))
        Response.ok(if (on) "Holding $what" else "Released $what")
    }

    fun stop(): Response {
        Movement.cancel()
        Hooks.forceAttack = false
        ClientThread.call { Input.releaseAll() }
        return Response.ok("Stopped and released all keys")
    }

    /** Steers toward a point: forward, sprinting when far, jumping when blocked. */
    private class Walker(mc: Minecraft) {
        val forward = mc.options.keyUp
        val sprint = mc.options.keySprint
        val jump = mc.options.keyJump
        private var jumpTicks = 0

        fun step(player: LocalPlayer, x: Double, z: Double, distance: Double) {
            setRotation(player, yawTo(x - player.x, z - player.z), player.xRot)
            Input.hold(forward)
            if (distance > 4) Input.hold(sprint) else Input.release(sprint)
            if (player.horizontalCollision && player.onGround()) jumpTicks = 3
            if (jumpTicks-- > 0) Input.hold(jump) else Input.release(jump)
        }

        fun idle() {
            Input.release(forward)
            Input.release(sprint)
            Input.release(jump)
        }
    }

    fun goto(m: Goto): Response {
        val walker = ClientThread.withPlayer { mc, _ -> Walker(mc) }
        var best = Double.MAX_VALUE
        var sinceProgress = 0
        val future = Movement.begin(
            Ticker.start { mc ->
                val p = mc.player ?: throw NotInWorldException()
                val dx = m.x - p.x
                val dz = m.z - p.z
                val distance = sqrt(dx * dx + dz * dz)
                if (distance < 0.6) return@start p.position()
                walker.step(p, m.x, m.z, distance)
                if (distance < best - 0.1) {
                    best = distance
                    sinceProgress = 0
                } else if (++sinceProgress > 60) {
                    throw IllegalStateException(
                        "Stuck at ${round2(p.x)} ${round2(p.z)}, ${round2(distance)} blocks from the target (goto walks in a straight line)",
                    )
                }
                null
            },
            walker.forward, walker.sprint, walker.jump,
        )
        val end = Ticker.await(future, (m.timeout * 1000).toLong(), "Didn't reach ${m.x} ${m.z} within ${m.timeout}s")
        return Response.ok("Arrived at ${round2(end.x)} ${round2(end.y)} ${round2(end.z)}")
    }

    fun follow(m: Follow): Response {
        val (walker, name) = ClientThread.withPlayer { mc, _ ->
            val target = Json.findEntity(mc, m.target) ?: throw IllegalArgumentException("No player or entity '${m.target}' nearby")
            Walker(mc) to target.name.string
        }
        var missing = 0
        Movement.begin(
            Ticker.start<Unit> { mc ->
                val p = mc.player ?: throw NotInWorldException()
                val target = Json.findEntity(mc, m.target)
                if (target == null) {
                    walker.idle()
                    if (++missing > 200) throw IllegalStateException("Lost sight of ${m.target}")
                    return@start null
                }
                missing = 0
                val distance = p.distanceTo(target).toDouble()
                if (distance > m.distance) walker.step(p, target.x, target.z, distance - m.distance) else walker.idle()
                lookAt(p, target.eyePosition)
                null
            },
            walker.forward, walker.sprint, walker.jump,
        )
        return Response.ok("Following $name until 'stop'")
    }
}
