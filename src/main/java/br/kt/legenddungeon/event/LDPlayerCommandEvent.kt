package br.kt.legenddungeon.event

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class LDPlayerCommandEvent(
        val player: Player
) : Event(), Cancellable {
    override fun setCancelled(cancel: Boolean) {
        this.cancel = cancel
    }

    override fun isCancelled(): Boolean = cancel

    private var cancel: Boolean = false

    override fun getHandlers(): HandlerList = handler

    companion object {
        val handler: HandlerList = HandlerList()
        @JvmStatic
        fun getHandlerList(): HandlerList = handler
    }
}