package dsh.nebsclient.mod.handler

import dsh.nebsclient.core.message.GetBlocks
import dsh.nebsclient.core.message.GetEntities
import dsh.nebsclient.core.message.Response
import dsh.nebsclient.mod.runtime.ClientThread
import dsh.nebsclient.mod.runtime.Json
import dsh.nebsclient.mod.runtime.Json.round2
import dsh.nebsclient.mod.runtime.NotInWorldException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.scores.DisplaySlot

object ObservationHandlers {
    fun status(): Response = ClientThread.withPlayer { mc, p ->
        val json = buildJsonObject {
            put("name", p.name.string)
            put("x", round2(p.x))
            put("y", round2(p.y))
            put("z", round2(p.z))
            put("blockX", p.blockPosition().x)
            put("blockY", p.blockPosition().y)
            put("blockZ", p.blockPosition().z)
            put("yaw", round2(Mth.wrapDegrees(p.yRot).toDouble()))
            put("pitch", round2(p.xRot.toDouble()))
            put("health", round2(p.health.toDouble()))
            put("maxHealth", round2(p.maxHealth.toDouble()))
            put("absorption", round2(p.absorptionAmount.toDouble()))
            put("food", p.foodData.foodLevel)
            put("saturation", round2(p.foodData.saturationLevel.toDouble()))
            put("xpLevel", p.experienceLevel)
            put("xpProgress", round2(p.experienceProgress.toDouble()))
            put("gameMode", mc.gameMode?.playerMode?.name ?: "unknown")
            put("dimension", p.level().dimension().identifier().toString())
            put("onGround", p.onGround())
            put("inWater", p.isInWater)
            put("sprinting", p.isSprinting)
            put("sneaking", p.isShiftKeyDown)
            put("dead", p.isDeadOrDying)
            put("selectedSlot", p.inventory.selectedSlot)
            put("heldItem", if (p.inventory.selectedItem.isEmpty) kotlinx.serialization.json.JsonNull else Json.item(p.inventory.selectedItem))
        }
        Response.result(
            "${p.name.string} at ${p.blockPosition().toShortString()}, health ${round2(p.health.toDouble())}/${round2(p.maxHealth.toDouble())}",
            json,
        )
    }

    private fun section(slot: Int) = when (slot) {
        in 0..8 -> "hotbar"
        in 9..35 -> "main"
        in 36..39 -> "armor"
        Inventory.SLOT_OFFHAND -> "offhand"
        else -> "other"
    }

    fun inventory(): Response = ClientThread.withPlayer { _, p ->
        val inv = p.inventory
        val slots = buildJsonArray {
            for (i in 0 until inv.containerSize) {
                val stack = inv.getItem(i)
                if (stack.isEmpty) continue
                addJsonObject {
                    put("slot", i)
                    put("section", section(i))
                    Json.item(stack).forEach { (k, v) -> put(k, v) }
                }
            }
        }
        Response.result(
            "${slots.size} non-empty slots, slot ${inv.selectedSlot} selected",
            buildJsonObject {
                put("selectedSlot", inv.selectedSlot)
                put("slots", slots)
            },
        )
    }

    fun block(x: Int, y: Int, z: Int): Response = ClientThread.withPlayer { mc, _ ->
        val level = mc.level ?: throw NotInWorldException()
        val pos = BlockPos(x, y, z)
        val state = level.getBlockState(pos)
        Response.result(Json.blockId(state), buildJsonObject {
            Json.block(pos, state).forEach { (k, v) -> put(k, v) }
            put("loaded", level.isLoaded(pos))
        })
    }

    fun blocks(m: GetBlocks): Response = ClientThread.withPlayer(timeoutMs = 15_000) { mc, _ ->
        val level = mc.level ?: throw NotInWorldException()
        val counts = sortedMapOf<String, Int>()
        val list = buildJsonArray {
            var listed = 0
            for (pos in BlockPos.betweenClosed(minOf(m.x1, m.x2), minOf(m.y1, m.y2), minOf(m.z1, m.z2), maxOf(m.x1, m.x2), maxOf(m.y1, m.y2), maxOf(m.z1, m.z2))) {
                val state = level.getBlockState(pos)
                if (state.isAir) continue
                val id = Json.blockId(state)
                counts[id] = (counts[id] ?: 0) + 1
                if (listed++ < 4096) add(Json.block(pos.immutable(), state))
            }
        }
        Response.result(
            "${counts.values.sum()} non-air blocks",
            buildJsonObject {
                putJsonObject("counts") { counts.forEach { (k, v) -> put(k, v) } }
                put("blocks", list)
                put("truncated", list.size < counts.values.sum())
            },
        )
    }

    fun lookTarget(): Response = ClientThread.withPlayer { mc, p ->
        when (val hit = mc.hitResult) {
            is BlockHitResult if hit.type == HitResult.Type.BLOCK -> {
                val state = mc.level!!.getBlockState(hit.blockPos)
                Response.result("Looking at ${Json.blockId(state)} at ${hit.blockPos.toShortString()}", buildJsonObject {
                    put("type", "block")
                    put("face", hit.direction.serializedName)
                    put("distance", round2(p.eyePosition.distanceTo(hit.location)))
                    Json.block(hit.blockPos, state).forEach { (k, v) -> put(k, v) }
                })
            }
            is EntityHitResult -> Response.result("Looking at ${hit.entity.name.string}", buildJsonObject {
                put("type", "entity")
                put("entity", Json.entity(hit.entity, p))
            })
            else -> Response.result("Looking at nothing", buildJsonObject { put("type", "miss") })
        }
    }

    fun entities(m: GetEntities): Response = ClientThread.withPlayer { mc, p ->
        val level = mc.level ?: throw NotInWorldException()
        val found = level.entitiesForRendering()
            .filter { it !== p && p.distanceTo(it) <= m.radius }
            .filter { e ->
                m.entityType == null || BuiltInRegistries.ENTITY_TYPE.getKey(e.type).let { it.toString() == m.entityType || it.path == m.entityType }
            }
            .sortedBy { p.distanceTo(it) }
            .take(200)
        Response.result(
            "${found.size} entities within ${m.radius} blocks",
            buildJsonObject { putJsonArray("entities") { found.forEach { add(Json.entity(it, p)) } } },
        )
    }

    fun players(): Response = ClientThread.withPlayer { _, p ->
        val players = p.connection.onlinePlayers.sortedBy { it.profile.name() }
        Response.result(
            "${players.size} players online",
            buildJsonObject {
                putJsonArray("players") {
                    players.forEach {
                        addJsonObject {
                            put("name", it.profile.name())
                            put("uuid", it.profile.id().toString())
                            put("latency", it.latency)
                            put("gameMode", it.gameMode?.name ?: "unknown")
                        }
                    }
                }
            },
        )
    }

    fun world(): Response = ClientThread.withPlayer { mc, p ->
        val level = mc.level ?: throw NotInWorldException()
        val dayTime = level.overworldClockTime
        Response.result(
            "${level.dimension().identifier()}, time ${dayTime % 24000}",
            buildJsonObject {
                put("dimension", level.dimension().identifier().toString())
                put("timeOfDay", dayTime % 24000)
                put("day", dayTime / 24000)
                put("gameTime", level.levelData.gameTime)
                put("raining", level.isRaining)
                put("thundering", level.isThundering)
                put("difficulty", level.levelData.difficulty.serializedName)
                put("server", mc.currentServer?.ip ?: "")
                put("players", p.connection.onlinePlayers.size)
            },
        )
    }

    fun scoreboard(): Response = ClientThread.withPlayer { mc, _ ->
        val scoreboard = mc.level?.scoreboard ?: throw NotInWorldException()
        val objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR)
            ?: return@withPlayer Response.result("No sidebar scoreboard", buildJsonObject { put("visible", false) })
        val lines = scoreboard.listPlayerScores(objective).filter { !it.isHidden }.sortedByDescending { it.value() }
        Response.result(
            objective.displayName.string,
            buildJsonObject {
                put("visible", true)
                put("title", objective.displayName.string)
                putJsonArray("lines") {
                    lines.forEach {
                        addJsonObject {
                            put("text", (it.display() ?: it.ownerName()).string)
                            put("value", it.value())
                        }
                    }
                }
            },
        )
    }

    fun effects(): Response = ClientThread.withPlayer { _, p ->
        val effects = p.activeEffects.sortedBy { it.effect.registeredName }
        Response.result(
            "${effects.size} active effects",
            buildJsonObject {
                putJsonArray("effects") {
                    effects.forEach {
                        addJsonObject {
                            put("id", it.effect.registeredName)
                            put("level", it.amplifier + 1)
                            put("durationTicks", if (it.isInfiniteDuration) -1 else it.duration)
                        }
                    }
                }
            },
        )
    }

    @Suppress("unused")
    private fun JsonObject.asJson() = this
}
