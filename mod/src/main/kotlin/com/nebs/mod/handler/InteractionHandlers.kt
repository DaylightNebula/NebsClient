package com.nebs.mod.handler

import com.nebs.core.message.Attack
import com.nebs.core.message.HoldItem
import com.nebs.core.message.Mine
import com.nebs.core.message.Place
import com.nebs.core.message.Response
import com.nebs.core.message.UseItem
import com.nebs.core.message.UseOn
import com.nebs.mod.runtime.ClientThread
import com.nebs.mod.runtime.Hooks
import com.nebs.mod.runtime.Input
import com.nebs.mod.runtime.Json
import com.nebs.mod.runtime.Json.round2
import com.nebs.mod.runtime.NotInWorldException
import com.nebs.mod.runtime.Ticker
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.util.concurrent.atomic.AtomicBoolean

object InteractionHandlers {
    private val mining = AtomicBoolean(false)

    private fun gameMode(mc: Minecraft): MultiPlayerGameMode = mc.gameMode ?: throw NotInWorldException()

    private fun swingAttack(p: LocalPlayer) = p.swing(InteractionHand.MAIN_HAND, p.getItemInHand(InteractionHand.MAIN_HAND).attackAnimation, false)

    private fun swingInteract(p: LocalPlayer, hand: InteractionHand) = p.swing(hand, p.getItemInHand(hand).interactAnimation, false)

    /** Fails unless [point] is within the player's block reach. */
    private fun checkReach(p: LocalPlayer, point: Vec3) {
        val distance = p.eyePosition.distanceTo(point)
        val reach = p.blockInteractionRange()
        if (distance > reach) throw IllegalStateException("Too far: ${round2(distance)} blocks away, reach is ${round2(reach)}")
    }

    /** Fails unless [entity] is within the player's entity reach (the server ignores hits beyond it). */
    private fun checkReach(p: LocalPlayer, entity: net.minecraft.world.entity.Entity) {
        val distance = Math.sqrt(entity.boundingBox.distanceToSqr(p.eyePosition))
        val reach = p.entityInteractionRange()
        if (distance > reach) throw IllegalStateException("Too far from ${entity.name.string}: ${round2(distance)} blocks away, reach is ${round2(reach)}")
    }

    private fun faceCenter(pos: BlockPos, face: Direction): Vec3 =
        Vec3.atCenterOf(pos).add(face.stepX * 0.5, face.stepY * 0.5, face.stepZ * 0.5)

    fun attack(m: Attack): Response = ClientThread.withPlayer { mc, p ->
        val gameMode = gameMode(mc)
        val id = m.entity
        if (id != null) {
            val entity = mc.level?.getEntity(id) ?: throw IllegalArgumentException("No entity with id $id")
            checkReach(p, entity)
            MovementHandlers.lookAt(p, entity.boundingBox.center)
            gameMode.attack(p, entity)
            swingAttack(p)
            return@withPlayer Response.ok("Attacked ${entity.name.string} (id ${entity.id})")
        }
        when (val hit = mc.hitResult) {
            is EntityHitResult -> {
                gameMode.attack(p, hit.entity)
                swingAttack(p)
                Response.ok("Attacked ${hit.entity.name.string} (id ${hit.entity.id})")
            }
            is BlockHitResult if hit.type == HitResult.Type.BLOCK -> {
                gameMode.startDestroyBlock(hit.blockPos, hit.direction)
                swingAttack(p)
                Response.ok("Hit ${Json.blockId(mc.level!!.getBlockState(hit.blockPos))} at ${hit.blockPos.toShortString()}")
            }
            else -> {
                swingAttack(p)
                Response.ok("Swung at nothing")
            }
        }
    }

    /** One right-click, like vanilla's use key: entity, then block, then the item itself, for each hand. */
    private fun useOnce(mc: Minecraft, p: LocalPlayer): String {
        val gameMode = gameMode(mc)
        for (hand in InteractionHand.entries) {
            val stack = p.getItemInHand(hand)
            when (val hit = mc.hitResult) {
                is EntityHitResult -> if (gameMode.interact(p, hit.entity, hit, hand).consumesAction()) {
                    swingInteract(p, hand)
                    return "Used ${stack.hoverName.string} on ${hit.entity.name.string}"
                }
                is BlockHitResult if hit.type == HitResult.Type.BLOCK ->
                    if (gameMode.useItemOn(p, hand, hit).consumesAction()) {
                        swingInteract(p, hand)
                        return "Used ${stack.hoverName.string} on ${Json.blockId(mc.level!!.getBlockState(hit.blockPos))} at ${hit.blockPos.toShortString()}"
                    }
                else -> {}
            }
            if (!stack.isEmpty && gameMode.useItem(p, hand).consumesAction()) return "Used ${stack.hoverName.string}"
        }
        return "Nothing happened"
    }

    fun use(m: UseItem): Response {
        if (m.ticks == 0) return ClientThread.withPlayer { mc, p -> Response.ok(useOnce(mc, p)) }
        // Hold the use key first, or the game stops using the item (eating, drawing a bow) right away.
        val (key, what) = ClientThread.withPlayer { mc, p ->
            Input.hold(mc.options.keyUse)
            mc.options.keyUse to useOnce(mc, p)
        }
        var ticks = 0
        try {
            Ticker.run(m.ticks * 50L + 10_000, "Use didn't finish") { if (++ticks >= m.ticks) Unit else null }
        } finally {
            Input.release(key)
        }
        return Response.ok("$what (held for ${m.ticks} ticks)")
    }

    fun useOn(m: UseOn): Response = ClientThread.withPlayer { mc, p ->
        val pos = BlockPos(m.x, m.y, m.z)
        val face = Direction.entries.first { it.serializedName == m.face }
        val point = faceCenter(pos, face)
        checkReach(p, point)
        MovementHandlers.lookAt(p, point)
        val block = Json.blockId(mc.level!!.getBlockState(pos))
        if (gameMode(mc).useItemOn(p, InteractionHand.MAIN_HAND, BlockHitResult(point, face, pos, false)).consumesAction()) {
            swingInteract(p, InteractionHand.MAIN_HAND)
            Response.ok("Used $block at ${pos.toShortString()}")
        } else {
            Response.error("Nothing happened using $block at ${pos.toShortString()}")
        }
    }

    fun mine(m: Mine): Response {
        val pos = BlockPos(m.x, m.y, m.z)
        val center = Vec3.atCenterOf(pos)
        val block = ClientThread.withPlayer { mc, p ->
            val state = mc.level!!.getBlockState(pos)
            if (state.isAir) throw IllegalStateException("There is no block at ${pos.toShortString()}")
            checkReach(p, center)
            MovementHandlers.lookAt(p, center)
            Json.blockId(state)
        }
        if (!mining.compareAndSet(false, true)) throw IllegalStateException("Already mining")
        var blocked = 0
        var ticks = 0
        try {
            // Holding attack lets vanilla do the breaking (progress, swing, particles) on whatever the crosshair hits,
            // so keep the crosshair on the target and check nothing else is in the way.
            Hooks.forceAttack = true
            Ticker.run((m.timeout * 1000).toLong(), "Couldn't break $block within ${m.timeout}s") { mc ->
                val p = mc.player ?: throw NotInWorldException()
                if (mc.level!!.getBlockState(pos).isAir) return@run Unit
                ticks++
                MovementHandlers.lookAt(p, center)
                val hit = mc.hitResult
                if (hit is BlockHitResult && hit.type == HitResult.Type.BLOCK && hit.blockPos == pos) {
                    blocked = 0
                } else if (++blocked > 10) {
                    val what = if (hit is BlockHitResult && hit.type == HitResult.Type.BLOCK) Json.blockId(mc.level!!.getBlockState(hit.blockPos)) + " at " + hit.blockPos.toShortString() else "nothing"
                    throw IllegalStateException("Can't reach $block: the crosshair is on $what")
                }
                null
            }
        } finally {
            Hooks.forceAttack = false
            mining.set(false)
        }
        return Response.ok("Mined $block at ${pos.toShortString()} in $ticks ticks")
    }

    fun place(m: Place): Response = ClientThread.withPlayer { mc, p ->
        val level = mc.level!!
        val pos = BlockPos(m.x, m.y, m.z)
        val existing = level.getBlockState(pos)
        if (!existing.canBeReplaced()) throw IllegalStateException("${pos.toShortString()} is occupied by ${Json.blockId(existing)}")
        val held = p.getItemInHand(InteractionHand.MAIN_HAND)
        if (held.isEmpty) throw IllegalStateException("Not holding anything to place")
        for (direction in Direction.entries) {
            val neighbour = pos.relative(direction)
            val state = level.getBlockState(neighbour)
            if (state.isAir || state.canBeReplaced()) continue
            val face = direction.opposite // the neighbour's face that touches pos
            val point = faceCenter(neighbour, face)
            if (p.eyePosition.distanceTo(point) > p.blockInteractionRange()) continue
            MovementHandlers.lookAt(p, point)
            val result = gameMode(mc).useItemOn(p, InteractionHand.MAIN_HAND, BlockHitResult(point, face, neighbour, false))
            if (!result.consumesAction()) throw IllegalStateException("Couldn't place ${held.hoverName.string} against ${Json.blockId(state)}")
            swingInteract(p, InteractionHand.MAIN_HAND)
            return@withPlayer Response.ok("Placed ${held.hoverName.string} at ${pos.toShortString()}")
        }
        throw IllegalStateException("No solid block within reach next to ${pos.toShortString()} to place against")
    }

    fun selectSlot(slot: Int): Response = ClientThread.withPlayer { _, p ->
        p.inventory.selectedSlot = slot
        val item = p.inventory.selectedItem
        Response.ok("Selected slot $slot" + if (item.isEmpty) " (empty)" else " (${item.hoverName.string})")
    }

    fun hold(m: HoldItem): Response = ClientThread.withPlayer { mc, p ->
        val id = if (':' in m.item) m.item else "minecraft:${m.item}"
        val inventory = p.inventory
        val slot = (0 until inventory.containerSize).firstOrNull {
            val stack = inventory.getItem(it)
            !stack.isEmpty && BuiltInRegistries.ITEM.getKey(stack.item).toString() == id
        } ?: throw IllegalArgumentException("No $id in the inventory")
        when (slot) {
            in 0..8 -> inventory.selectedSlot = slot
            in 9..35 -> {
                if (p.containerMenu !== p.inventoryMenu) throw IllegalStateException("Close the open container first")
                // Swap it into the selected hotbar slot (inventory slots 9-35 are menu slots 9-35).
                gameMode(mc).handleContainerInput(p.inventoryMenu.containerId, slot, inventory.selectedSlot, ContainerInput.SWAP, p)
            }
            Inventory.SLOT_OFFHAND -> swapHandsPacket(p)
            else -> throw IllegalStateException("$id is worn as armor")
        }
        Response.ok("Holding $id")
    }

    fun drop(all: Boolean): Response = ClientThread.withPlayer { mc, p ->
        val held = p.getItemInHand(InteractionHand.MAIN_HAND)
        if (held.isEmpty) throw IllegalStateException("Not holding anything")
        val what = if (all) "${held.count} ${held.hoverName.string}" else "1 ${held.hoverName.string}"
        gameMode(mc).dropItem(p, all)
        Response.ok("Dropped $what")
    }

    private fun swapHandsPacket(p: LocalPlayer) =
        p.connection.send(ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN))

    fun swapHands(): Response = ClientThread.withPlayer { _, p ->
        swapHandsPacket(p)
        Response.ok("Swapped hands")
    }

    fun interact(entityId: Int): Response = ClientThread.withPlayer { mc, p ->
        val entity = mc.level?.getEntity(entityId) ?: throw IllegalArgumentException("No entity with id $entityId")
        checkReach(p, entity)
        MovementHandlers.lookAt(p, entity.boundingBox.center)
        for (hand in InteractionHand.entries) {
            if (gameMode(mc).interact(p, entity, EntityHitResult(entity), hand).consumesAction()) {
                swingInteract(p, hand)
                return@withPlayer Response.ok("Interacted with ${entity.name.string}")
            }
        }
        Response.error("Nothing happened interacting with ${entity.name.string}")
    }
}
