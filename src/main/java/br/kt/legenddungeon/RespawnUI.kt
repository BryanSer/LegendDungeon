package br.kt.legenddungeon

import Br.API.GUI.Ex.BaseUI
import Br.API.GUI.Ex.Item
import Br.API.GUI.Ex.SnapshotFactory
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import java.util.*

class RespawnUI : BaseUI() {
    private val factory = SnapshotFactory.getDefaultSnapshotFactory(this) { p, m ->
        val team = TeamManager.getTeam(p)
        if (team == null) {
            return@getDefaultSnapshotFactory
        }
        if (team!!.inGame) {
            m["Game"] = team.playingGame
        }
    }

    private val contains = arrayOfNulls<Item>(9)

    private fun getGame(p: Player): Game? {
        val snap = this.getSnapshot(p)
        return snap.getData("Game") as Game?
    }

    fun getSkull(player: Player): ItemStack {
        val `is` = ItemStack(Material.SKULL_ITEM, 1, 3.toShort())
        val im = `is`.itemMeta as SkullMeta
        im.owningPlayer = player
        `is`.itemMeta = im
        return `is`
    }

    init {
        super.AllowShift = false
        super.Name = "LDRUI"
        super.DisplayName = "§6复活队友"
        super.Rows = 1
        for (i in 0..8) {
            val index = i
            contains[index] = Item.getNewInstance { p ->
                val game = getGame(p) ?: return@getNewInstance null
                val list = game.team.getPlayers()
                if (list.size <= index) {
                    return@getNewInstance null
                }
                val target = list[index]
                if (target.gameMode != GameMode.SPECTATOR) {
                    return@getNewInstance null
                }
                val item = getSkull(target)
                val im = item.itemMeta
                im.displayName = "§6${target.name}"
                val death = game.playerDeathTimes[target.name] ?: 0
                var live = game.dun.maxDeath - death
                live = if (live < 0) 0 else live
                im.lore = Arrays.asList(
                        "§6剩余生命数: $live",
                        "§6左键点击花费复活币复活${target.name}"
                )
                item.itemMeta = im
                item
            }.setClick(ClickType.LEFT) { p: Player ->
                val game = getGame(p) ?: return@setClick
                val list = game.team.getPlayers()
                if (list.size <= index) {
                    return@setClick
                }
                val target = list[index]
                if (target.gameMode != GameMode.SPECTATOR) {
                    return@setClick
                }
                if (Setting.hasRespawnCoinAndRemove(p)) {
                    target.teleport(game.respawnLocation)
                    target.gameMode = GameMode.ADVENTURE
                } else {
                    p.sendMessage("§c你没有足够的复活币")
                }
            }
        }
    }

    override fun getItem(player: Player, i: Int): Item? {
        return contains[i]
    }

    override fun getSnapshotFactory(): SnapshotFactory<*> {
        return factory
    }
}
