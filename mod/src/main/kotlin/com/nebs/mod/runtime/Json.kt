package com.nebs.mod.runtime

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import kotlin.math.round

/** JSON views of game objects, shared by the observation commands. */
object Json {
    fun round2(value: Double) = round(value * 100) / 100

    fun item(stack: ItemStack): JsonObject = buildJsonObject {
        put("id", BuiltInRegistries.ITEM.getKey(stack.item).toString())
        put("count", stack.count)
        put("name", stack.hoverName.string)
        if (stack.isDamageableItem) {
            put("damage", stack.damageValue)
            put("maxDamage", stack.maxDamage)
        }
        val enchantments = stack.enchantments
        if (enchantments.entrySet().isNotEmpty()) {
            put("enchantments", buildJsonObject {
                enchantments.entrySet().forEach { put(it.key.registeredName, it.intValue) }
            })
        }
    }

    fun block(pos: BlockPos, state: BlockState): JsonObject = buildJsonObject {
        put("x", pos.x)
        put("y", pos.y)
        put("z", pos.z)
        put("block", blockId(state))
        val properties = state.values.toList()
        if (properties.isNotEmpty()) {
            put("properties", buildJsonObject { properties.forEach { put(it.property().name, it.valueName()) } })
        }
    }

    fun blockId(state: BlockState): String = BuiltInRegistries.BLOCK.getKey(state.block).toString()

    fun entity(entity: Entity, from: Entity? = null): JsonObject = buildJsonObject {
        put("id", entity.id)
        put("type", BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString())
        put("name", entity.name.string)
        put("uuid", entity.uuid.toString())
        put("x", round2(entity.x))
        put("y", round2(entity.y))
        put("z", round2(entity.z))
        if (from != null) put("distance", round2(from.distanceTo(entity).toDouble()))
        if (entity is LivingEntity) {
            put("health", round2(entity.health.toDouble()))
            put("maxHealth", round2(entity.maxHealth.toDouble()))
        }
        if (entity is Player) put("player", true)
    }

    /** Finds an entity by numeric id, or a player by name. */
    fun findEntity(mc: Minecraft, target: String): Entity? {
        val level = mc.level ?: return null
        target.toIntOrNull()?.let { return level.getEntity(it) }
        return level.players().firstOrNull { it.name.string.equals(target, ignoreCase = true) }
    }
}
