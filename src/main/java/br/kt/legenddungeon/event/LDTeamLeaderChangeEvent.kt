package br.kt.legenddungeon.event

import br.kt.legenddungeon.Team
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class LDTeamLeaderChangeEvent(
        val team: Team,
        val oldLeader: String,
        val newLeader: String
) : Event() {

    override fun getHandlers(): HandlerList = handler

    companion object {
        val handler: HandlerList = HandlerList()
        @JvmStatic
        fun getHandlerList(): HandlerList = handler
    }
}