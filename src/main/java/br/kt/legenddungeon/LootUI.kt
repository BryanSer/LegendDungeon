package br.kt.legenddungeon

import Br.API.GUI.Ex.BaseUI
import Br.API.GUI.Ex.Item
import Br.API.GUI.Ex.SnapshotFactory
import Br.API.ItemBuilder
import Br.API.Utils
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType

public class LootUI : BaseUI() {
    private val factory = SnapshotFactory.getDefaultSnapshotFactory(this) { p, m ->
        val team = TeamManager.getTeam(p)
        if (team == null) return@getDefaultSnapshotFactory
        if (team.leader !== p) {
            return@getDefaultSnapshotFactory
        }
        m["Team"] = team
        m["Page"] = 0

    }
    private val contains = arrayOfNulls<Item>(54)


    private val nextPage = ItemBuilder.getBuilder(Material.ARROW).name("§6下一页").build()
    private val prevPage = ItemBuilder.getBuilder(Material.ARROW).name("§6上一页").build()

    init {
        super.Rows = 6
        super.Name = "LDLUI"
        super.DisplayName = "§6§l战利品列表"
        super.AllowShift = false

        for (i in 0..44) {
            val index = i
            contains[index] = Item.getNewInstance { p ->
                val team = this.getTeam(p)
                if (team == null) {
                    null
                } else {
                    val snap = this.getSnapshot(p)
                    val page = snap.getData("Page") as Int
                    val target = page * 45 + index
                    val list = team.lootItem
                    if (list.size >= target) {
                        return@getNewInstance null
                    }
                    return@getNewInstance list[target].clone()
                }
            }.setClick(ClickType.LEFT) { p ->
                val team = this.getTeam(p)
                if (team == null) {
                    return@setClick
                }
                val snap = this.getSnapshot(p)
                val page = snap.getData("Page") as Int
                val target = page * 45 + index
                val list = team.lootItem
                if (list.size >= target) {
                    return@setClick
                }
                val item = list.removeAt(target).clone()
                Utils.safeGiveItem(p, item)
                p.sendMessage("§6物品已放入你的背包 请分配给队友")
            }.setUpdateIcon(true).setUpdate(true)
        }

        contains[45] = Item.getNewInstance { p: Player ->
            val team = this.getTeam(p)
            if (team == null) {
                null
            } else {
                val snap = this.getSnapshot(p)
                val page = snap.getData("Page") as Int
                if (page == 0) null else prevPage
            }
        }.setClick(ClickType.LEFT) {
            val snap = this.getSnapshot(it)
            val page = snap.getData("Page") as Int
            if (page > 0) {
                snap.setData("Page", page - 1)
            }
        }

        contains[53] = Item.getNewInstance { p: Player ->
            val team = this.getTeam(p)
            if (team == null) {
                null
            } else {
                nextPage
            }
        }.setClick(ClickType.LEFT) {
            val snap = this.getSnapshot(it)
            snap.setData("Page", snap.getData("Page") as Int + 1)
        }
    }

    fun getTeam(p: Player): Team? {
        val snap = this.getSnapshot(p)
        return snap.getData("Team") as Team?
    }

    override fun getItem(player: Player, i: Int): Item? = contains[i]
    override fun getSnapshotFactory(): SnapshotFactory<*>? {
        return factory
    }
}