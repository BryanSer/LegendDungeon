package br.kt.legenddungeon.event

import br.kt.legenddungeon.Game
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class LDGameDestroyEvent(val game: Game) : Event() {

    override fun getHandlers(): HandlerList = handler

    companion object {
        val handler: HandlerList = HandlerList()
        @JvmStatic
        fun getHandlerList(): HandlerList = handler
    }
}