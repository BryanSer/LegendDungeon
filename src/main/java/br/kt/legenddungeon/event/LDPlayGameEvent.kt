package br.kt.legenddungeon.event

import br.kt.legenddungeon.Dungeon
import br.kt.legenddungeon.Team
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class LDPlayGameEvent(
        val team: Team,
        val dun: Dungeon
) : Event(), Cancellable {

    var cancel: Boolean = false

    override fun setCancelled(cancel: Boolean) {
        this.cancel = cancel
    }

    override fun isCancelled(): Boolean = cancel

    override fun getHandlers(): HandlerList = handler

    companion object {
        val handler: HandlerList = HandlerList()
        @JvmStatic
        fun getHandlerList(): HandlerList = handler
    }
}