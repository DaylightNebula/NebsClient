package com.nebs.mod.handler

import com.nebs.core.message.ClickButton
import com.nebs.core.message.ClickSlot
import com.nebs.core.message.Response
import com.nebs.mod.runtime.ClientThread
import com.nebs.mod.runtime.Json
import com.nebs.mod.runtime.NotInWorldException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.events.ContainerEventHandler
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.world.inventory.ContainerInput

object GuiHandlers {
    private fun openScreen(screen: Screen?): Screen = screen ?: throw IllegalStateException("No screen is open")

    /** Every visible widget on [screen], depth first, in the order the screen lists them. */
    private fun widgets(handler: ContainerEventHandler, into: MutableList<AbstractWidget> = mutableListOf()): List<AbstractWidget> {
        handler.children().forEach {
            if (it is AbstractWidget && it.visible) into += it
            if (it is ContainerEventHandler) widgets(it, into)
        }
        return into
    }

    private fun buttons(screen: Screen) = widgets(screen).filter { it !is EditBox }

    fun openInventory(): Response = ClientThread.withPlayer { mc, p ->
        mc.gui.setScreen(InventoryScreen(p))
        Response.ok("Opened the inventory")
    }

    fun closeScreen(): Response = ClientThread.call { mc ->
        val screen = openScreen(mc.gui.screen())
        val player = mc.player
        if (player != null && screen is AbstractContainerScreen<*>) player.closeContainer() else screen.onClose()
        Response.ok("Closed ${screen.javaClass.simpleName}")
    }

    fun screen(): Response = ClientThread.call { mc ->
        val screen = mc.gui.screen()
            ?: return@call Response.result("No screen open", buildJsonObject { put("open", false) })
        val buttons = buttons(screen)
        val fields = widgets(screen).filterIsInstance<EditBox>()
        val json = buildJsonObject {
            put("open", true)
            put("type", screen.javaClass.simpleName)
            put("title", screen.title.string)
            put("pauses", screen.isPauseScreen)
            putJsonArray("buttons") {
                buttons.forEachIndexed { i, w ->
                    addJsonObject {
                        put("index", i)
                        put("label", w.message.string)
                        put("active", w.isActive)
                    }
                }
            }
            putJsonArray("textFields") {
                fields.forEachIndexed { i, f ->
                    addJsonObject {
                        put("index", i)
                        put("value", f.value)
                        put("focused", f.isFocused)
                    }
                }
            }
            if (screen is AbstractContainerScreen<*>) {
                val menu = screen.menu
                putJsonObject("container") {
                    put("id", menu.containerId)
                    put("size", menu.slots.size)
                    putJsonArray("slots") {
                        menu.slots.forEachIndexed { i, slot ->
                            if (!slot.item.isEmpty) {
                                addJsonObject {
                                    put("slot", i)
                                    Json.item(slot.item).forEach { (k, v) -> put(k, v) }
                                }
                            }
                        }
                    }
                    put("carried", if (menu.carried.isEmpty) JsonNull else Json.item(menu.carried))
                }
            }
        }
        Response.result("${screen.javaClass.simpleName}: ${screen.title.string}", json)
    }

    fun clickSlot(m: ClickSlot): Response = ClientThread.withPlayer { mc, p ->
        val menu = p.containerMenu
        if (m.slot !in 0 until menu.slots.size) throw IllegalArgumentException("Slot must be between 0 and ${menu.slots.size - 1}")
        val gameMode = mc.gameMode ?: throw NotInWorldException()
        gameMode.handleContainerInput(menu.containerId, m.slot, m.button, ContainerInput.valueOf(m.mode.uppercase()), p)
        val item = menu.getSlot(m.slot).item
        Response.ok("Clicked slot ${m.slot} (${m.mode}); it now holds " + if (item.isEmpty) "nothing" else "${item.count} ${item.hoverName.string}")
    }

    fun clickButton(m: ClickButton): Response = ClientThread.call { mc ->
        val screen = openScreen(mc.gui.screen())
        val buttons = buttons(screen)
        val button = if (m.label.startsWith("#")) {
            m.label.drop(1).toIntOrNull()?.let { buttons.getOrNull(it) }
        } else {
            buttons.firstOrNull { it.message.string.equals(m.label, ignoreCase = true) }
                ?: buttons.firstOrNull { it.message.string.contains(m.label, ignoreCase = true) }
        } ?: throw IllegalArgumentException("No button '${m.label}'. Buttons: ${buttons.mapIndexed { i, b -> "#$i ${b.message.string}" }.joinToString()}")
        if (!button.isActive) throw IllegalStateException("Button '${button.message.string}' is disabled")
        val x = button.x + button.width / 2.0
        val y = button.y + button.height / 2.0
        // onClick presses the widget directly; mouseClicked would hit-test against the real cursor position.
        button.onClick(MouseButtonEvent(x, y, MouseButtonInfo(0, 0)), false)
        Response.ok("Clicked '${button.message.string}'")
    }

    fun typeText(text: String): Response = ClientThread.call { mc ->
        val screen = openScreen(mc.gui.screen())
        val field = screen.focused as? EditBox
            ?: widgets(screen).filterIsInstance<EditBox>().singleOrNull()?.also { screen.focused = it }
        if (field != null) {
            field.insertText(text)
            return@call Response.ok("Typed into the text field; it now reads '${field.value}'")
        }
        text.codePoints().forEach { screen.charTyped(CharacterEvent(it)) }
        Response.ok("Typed ${text.length} characters")
    }
}
