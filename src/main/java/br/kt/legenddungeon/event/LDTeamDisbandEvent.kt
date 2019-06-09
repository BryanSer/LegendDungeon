package br.kt.legenddungeon.event

import br.kt.legenddungeon.Team
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class LDTeamDisbandEvent(
        val team: Team
) : Event() {

    override fun getHandlers(): HandlerList = handler

    companion object {
        val handler: HandlerList = HandlerList()
        @JvmStatic
        fun getHandlerList(): HandlerList = handler
    }
}