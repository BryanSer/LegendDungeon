package br.kt.legenddungeon.event

import br.kt.legenddungeon.LootRule
import br.kt.legenddungeon.Team
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class LDGameRuleSettingEvent(
        val team: Team,
        var rule: LootRule? = null
) : Event() {

    override fun getHandlers(): HandlerList = handler

    companion object {
        val handler: HandlerList = HandlerList()
        @JvmStatic
        fun getHandlerList(): HandlerList = handler
    }
}