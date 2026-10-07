package dsh.nebsclient.api

import kotlinx.serialization.Serializable
import java.nio.file.Path

// Results of NebsClient queries. Field names match the JSON the mod sends.

/** Client states for [NebsClient.waitFor]. */
public enum class ClientState(internal val wire: String) {
    LOADING("loading"),
    MENU("menu"),
    CONNECTING("connecting"),
    IN_WORLD("in-world"),
    ALIVE("alive"),
    DEAD("dead"),
    ;

    public companion object {
        @JvmStatic
        public fun of(wire: String): ClientState = entries.firstOrNull { it.wire == wire } ?: throw IllegalArgumentException("Unknown state '$wire'")
    }
}

public enum class MoveDirection(internal val wire: String) { FORWARD("forward"), BACK("back"), LEFT("left"), RIGHT("right") }

public enum class BlockFace(internal val wire: String) { DOWN("down"), UP("up"), NORTH("north"), SOUTH("south"), WEST("west"), EAST("east") }

/** How [NebsClient.clickSlot] clicks. */
public enum class ClickMode(internal val wire: String) {
    PICKUP("pickup"), QUICK_MOVE("quick_move"), SWAP("swap"), CLONE("clone"), THROW("throw"), QUICK_CRAFT("quick_craft"), PICKUP_ALL("pickup_all"),
}

/** Event names for [NebsClient.subscribe]. */
public object Events {
    public const val CHAT: String = "chat"
    public const val SYSTEM: String = "system"
    public const val ACTION_BAR: String = "action-bar"
    public const val JOIN: String = "join"
    public const val DISCONNECT: String = "disconnect"
    public const val DEATH: String = "death"
    public const val RESPAWN: String = "respawn"
    public const val HEALTH: String = "health"
    public const val SCREEN: String = "screen"
    public const val INVENTORY: String = "inventory"
}

public data class PingInfo(val name: String?, val uuid: String?, val state: ClientState, val server: String?)

@Serializable
public data class ItemInfo(
    val id: String,
    val count: Int,
    val name: String,
    val damage: Int? = null,
    val maxDamage: Int? = null,
    val enchantments: Map<String, Int> = emptyMap(),
)

@Serializable
public data class PlayerStatus(
    val name: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val blockX: Int,
    val blockY: Int,
    val blockZ: Int,
    val yaw: Double,
    val pitch: Double,
    val health: Double,
    val maxHealth: Double,
    val absorption: Double,
    val food: Int,
    val saturation: Double,
    val xpLevel: Int,
    val xpProgress: Double,
    val gameMode: String,
    val dimension: String,
    val onGround: Boolean,
    val inWater: Boolean,
    val sprinting: Boolean,
    val sneaking: Boolean,
    val dead: Boolean,
    val selectedSlot: Int,
    val heldItem: ItemInfo? = null,
)

/** One inventory slot: hotbar 0-8, main 9-35, armor 36-39, offhand 40. */
@Serializable
public data class InventorySlot(
    val slot: Int,
    val section: String,
    val id: String,
    val count: Int,
    val name: String,
    val damage: Int? = null,
    val maxDamage: Int? = null,
    val enchantments: Map<String, Int> = emptyMap(),
)

@Serializable
public data class Inventory(val selectedSlot: Int, val slots: List<InventorySlot>) {
    /** The item in [slot], or `null` if it's empty. */
    public fun slot(slot: Int): InventorySlot? = slots.firstOrNull { it.slot == slot }

    /** Total count of an item id (`dirt` or `minecraft:dirt`) across all slots. */
    public fun count(item: String): Int {
        val id = if (':' in item) item else "minecraft:$item"
        return slots.filter { it.id == id }.sumOf { it.count }
    }
}

@Serializable
public data class BlockInfo(
    val x: Int,
    val y: Int,
    val z: Int,
    val block: String,
    val properties: Map<String, String> = emptyMap(),
    val loaded: Boolean = true,
) {
    public val isAir: Boolean get() = block == "minecraft:air" || block == "minecraft:cave_air" || block == "minecraft:void_air"
}

@Serializable
public data class BlocksInfo(val counts: Map<String, Int>, val blocks: List<BlockInfo>, val truncated: Boolean)

@Serializable
public data class EntityInfo(
    val id: Int,
    val type: String,
    val name: String,
    val uuid: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val distance: Double? = null,
    val health: Double? = null,
    val maxHealth: Double? = null,
    val player: Boolean = false,
)

/** What the crosshair is on: [type] is `block`, `entity` or `miss`. */
@Serializable
public data class LookTarget(
    val type: String,
    val face: String? = null,
    val distance: Double? = null,
    val x: Int? = null,
    val y: Int? = null,
    val z: Int? = null,
    val block: String? = null,
    val properties: Map<String, String> = emptyMap(),
    val entity: EntityInfo? = null,
)

@Serializable
public data class PlayerListEntry(val name: String, val uuid: String, val latency: Int, val gameMode: String)

@Serializable
public data class WorldInfo(
    val dimension: String,
    val timeOfDay: Long,
    val day: Long,
    val gameTime: Long,
    val raining: Boolean,
    val thundering: Boolean,
    val difficulty: String,
    val server: String,
    val players: Int,
)

@Serializable
public data class ButtonInfo(val index: Int, val label: String, val active: Boolean)

@Serializable
public data class TextFieldInfo(val index: Int, val value: String, val focused: Boolean)

@Serializable
public data class ContainerSlot(
    val slot: Int,
    val id: String,
    val count: Int,
    val name: String,
    val damage: Int? = null,
    val maxDamage: Int? = null,
    val enchantments: Map<String, Int> = emptyMap(),
)

@Serializable
public data class ContainerInfo(val id: Int, val size: Int, val slots: List<ContainerSlot>, val carried: ItemInfo? = null)

/** The open screen; [open] is false when none is. */
@Serializable
public data class ScreenInfo(
    val open: Boolean,
    val type: String? = null,
    val title: String? = null,
    val pauses: Boolean = false,
    val buttons: List<ButtonInfo> = emptyList(),
    val textFields: List<TextFieldInfo> = emptyList(),
    val container: ContainerInfo? = null,
)

/** A chat line; [type] is `chat`, `system` or `action-bar`. [seq] works with `waitForChat(since = …)`. */
@Serializable
public data class ChatMessage(val seq: Long, val time: Long, val type: String, val sender: String? = null, val text: String)

@Serializable
public data class ChatHistory(val lastSeq: Long, val messages: List<ChatMessage>)

@Serializable
public data class ScoreLine(val text: String, val value: Int)

@Serializable
public data class Scoreboard(val visible: Boolean, val title: String? = null, val lines: List<ScoreLine> = emptyList())

@Serializable
public data class EffectInfo(val id: String, val level: Int, val durationTicks: Int)

@Serializable
public data class PerfInfo(
    val fps: Int,
    val memoryUsedMb: Long,
    val memoryMaxMb: Long,
    val loadedChunks: Int,
    val entities: Int,
    val windowWidth: Int,
    val windowHeight: Int,
)

public data class ScreenshotInfo(val path: Path, val width: Int, val height: Int)

/** Something that happened in the client; see [Events] for [type]s. */
public data class ClientEvent(val type: String, val data: Map<String, String>, val time: Long)

/** Receives events from [NebsClient.subscribe]. Called on a background thread. */
public fun interface EventListener {
    public fun onEvent(event: ClientEvent)
}

/** An event stream from [NebsClient.subscribe]; close it to stop. */
public interface Subscription : AutoCloseable {
    override fun close()
}
