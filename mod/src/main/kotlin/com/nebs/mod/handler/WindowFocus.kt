package com.nebs.mod.handler

import net.minecraft.client.Minecraft
import org.lwjgl.sdl.SDLHints
import org.lwjgl.sdl.SDLVideo

/** Brings the game window to the front and gives it keyboard focus. Must run on the client thread. */
object WindowFocus {
    fun request(minecraft: Minecraft) {
        val window = minecraft.window.handle()

        // By default operating systems only let an already-focused app raise its windows; these hints
        // ask SDL to activate the window even when another application (e.g. the CLI's terminal) is in front.
        SDLHints.SDL_SetHint(SDLHints.SDL_HINT_FORCE_RAISEWINDOW, "1")
        SDLHints.SDL_SetHint(SDLHints.SDL_HINT_WINDOW_ACTIVATE_WHEN_RAISED, "1")

        if (SDLVideo.SDL_GetWindowFlags(window) and SDLVideo.SDL_WINDOW_MINIMIZED != 0L) {
            SDLVideo.SDL_RestoreWindow(window)
        }
        SDLVideo.SDL_RaiseWindow(window)
    }
}
