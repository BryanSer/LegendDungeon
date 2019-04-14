package br.kt.legenddungeon

import Br.API.CallBack
import Br.API.TitleUtils
import Br.API.Utils
import br.kt.legenddungeon.sign.InGameSign
import br.kt.legenddungeon.sign.StartSign
import io.lumine.xikage.mythicmobs.MythicMobs
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.util.*

class Game(
        val world: World,
        val id: Int,
        val dun: Dungeon,
        val team: Team,
        val lootRule: LootRule
) : Listener {
    val uuid: UUID = world.uid

    private val signs = ArrayList<InGameSign>()
    private val task: BukkitTask
    val playerDeathTimes = mutableMapOf<String, Int>()
    private val startTIme = System.currentTimeMillis()
    var gameStop = false
    var checkMobAliveTime = 0L

    var startLocation: Location? = null
    private var lastRespawnMessage: Int = 0

    val playerFrom: MutableMap<String, Location> = HashMap()

    init {
        team.playingGame = this
        for (s in dun.signs) {
            if (s is StartSign) {
                startLocation = s.getInGameLocation(this)
                continue
            }
            signs.add(s.createInGameSign(this))
        }
        if (startLocation === null) {
            throw IllegalStateException("没有开始副本的地方")
        }

        Bukkit.getPluginManager().registerEvents(this, Main.getMain())

        task = Bukkit.getScheduler().runTaskTimer(Main.getMain(), {
            if (gameStop) {
                return@runTaskTimer
            }
            if (!checkAlive()) {
                gameover()
                return@runTaskTimer
            }
            checkDropItem()
            checkMobAliveTime++
            if (checkMobAliveTime > 100) {
                if (checkMobAliveTime % 50 == 0L) {
                    checkMobAlive()
                }
            }
            lastRespawnMessage = (++lastRespawnMessage) % 400;
            if (lastRespawnMessage == 0)
                for (p in getPlayers()) {
                    if (p.gameMode == GameMode.SPECTATOR) {
                        for (p in getPlayers())
                            Utils.sendCommandButton(p, "§e§l点击此处复活死亡的队友", "/ldp respawn")
                        break;
                    }
                }
            if (System.currentTimeMillis() - startTIme >= this.dun.timeLimit * 60 * 1000L) {
                this.gameover(true)
                return@runTaskTimer
            }
            for (sign in this.signs) {
                sign.checkTrigger()
            }
        }, 100, 2)
    }

    fun checkMobAlive() {
        val mobs = MythicMobs.inst().mobManager
        for (e in world.entities) {
            val mob = mobs.getMythicMobInstance(e)
            if (mob != null) {
                return
            }
        }
        this.win()
    }

    fun checkDropItem() {
        val list = mutableListOf<Item>()
        for (item in this.world.getEntitiesByClass(Item::class.java)) {
            if (!isPlayerDrop(item))
                list.add(item)
        }
        val it = list.iterator()
        var add = false
        while (it.hasNext()) {
            val item = it.next()
            it.remove()
            val itemStack = item.itemStack.clone()
            if (lootRule === LootRule.RANDOM) {
                val amount = itemStack.amount
                itemStack.amount = 1
                for (i in 1..amount) {
                    val t = randomPlayer()
                    Utils.safeGiveItem(t, itemStack)
                }
            } else {
                team.lootItem.add(itemStack)
                add = true
            }
            item.remove()
        }
        if (add) {
            Utils.sendCommandButton(this.team.leader, "§6掉落物品已放入分配库 请点击§b§l此处§6打开", "/ldp loot ingameopen")
        }
    }

    private fun randomPlayer(): Player {
        val target = RANDOM.nextInt(this.team.size())
        if (target == 0) {
            return this.team.leader
        }
        return this.team.members[target - 1]
    }

    @EventHandler
    fun onEntityDeath(evt: EntityDeathEvent) {
        if (evt.entity.world !== this.world) {
            return
        }
        if (lootRule === LootRule.RANDOM) {
            evt.drops.forEach {
                val item = it.clone()
                item.amount = 1
                for (i in 1..it.amount) {
                    val t = randomPlayer()
                    Utils.safeGiveItem(t, item)
                }
            }
        } else {
            evt.drops.map { it.clone() }.forEach {
                team.lootItem.add(it)
            }
            Utils.sendCommandButton(this.team.leader, "§6掉落物品已放入分配库 请点击§b§l此处§6打开", "/ldp loot")
        }
        evt.drops.clear()
    }

    fun checkAlive(): Boolean {
        for (p in getPlayers()) {
            if (p.isDead) {
                continue
            }
            if (p.gameMode != GameMode.SPECTATOR) {
                return true
            }
        }
        return false
    }

    fun start() {
        for (p in team.getPlayers()) {
            playerFrom[p.name] = p.location
            p.teleport(this.startLocation)
            playerDeathTimes[p.name] = 0
        }
        PlayerManager.doIt {
            for (e in playerFrom) {
                it[e.key] = e.value
            }
        }
        when (this.lootRule) {
            LootRule.LEADER -> {
                this.broadcast("§6§l队长选择的掉落模式: §a§l[队长分配制]")
            }
            LootRule.RANDOM -> {
                this.broadcast("§6§l队长选择的掉落模式: §b§l[随机分配制]")
            }
        }
    }

    fun leave(p: Player, tip: Boolean = false) {
        if (p == this.team.leader) {
            this.broadcast("§c队长离开了副本 副本自动结束")
            this.destroy()
            this.team.disband()
            return
        }
        team.leave(p)
        val loc = playerFrom.remove(p.name)
        p.teleport(loc)
        PlayerManager.doIt {
            it.remove(p.name)
        }
        this.broadcast("§e§l玩家${p.name}离开了副本")
    }

    fun broadcast(msg: String) {
        for (p in this.getPlayers()) {
            p.sendMessage(msg)
        }
    }

    fun inGame(p: Player): Boolean = this.team.inTeam(p)

    @EventHandler
    fun onCommand(evt: PlayerCommandPreprocessEvent) {
        if (!inGame(evt.player)) {
            return
        }
        val msg = evt.message.toLowerCase()
        if (msg.contains("ldp loot ingameopen")) {
            return
        }
        if (msg.matches(Regex("/?leave"))) {
            this.leave(evt.player)
        } else {
            val msg = if (msg[0] == '/') msg.replaceFirst("/", "") else msg
            for (pre in Setting.AllowCommand) {
                if (msg.startsWith(pre.toLowerCase())) {
                    return;
                }
            }
            val cb = ComponentBuilder("§c你不允许在副本内使用任何命令,如果想离开")
            cb.append(ComponentBuilder("§e§n请点击此处").event(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/leave")).create())
            Dungeon.sendMessage(evt.player, cb.create())
        }
        evt.isCancelled = true

    }

    @EventHandler
    fun onQuit(evt: PlayerQuitEvent) {
        if (inGame(evt.player)) {
            this.leave(evt.player, true)
        }
    }

    @EventHandler
    fun onDeath(evt: PlayerDeathEvent) {
        if (!inGame(evt.entity)) {
            return
        }
        val time = (playerDeathTimes[evt.entity.name] ?: 0) + 1
        playerDeathTimes[evt.entity.name] = time
        evt.entity.spigot().respawn()
    }

    fun onDropItem(evt: PlayerDropItemEvent) {
        if (inGame(evt.player)) {
            setPlayerDrop(evt.itemDrop)
        }
    }

    companion object {
        fun setPlayerDrop(item: Item) {
            item.setMetadata("LegendDungeon_PlayerDrop",
                    FixedMetadataValue(Main.getMain(), true))
        }

        @JvmStatic
        fun isPlayerDrop(item: Item): Boolean {
            if (item.hasMetadata("LegendDungeon_PlayerDrop")) {
                if (item.getMetadata("LegendDungeon_PlayerDrop")
                                [0].asBoolean()) {
                    return true
                }
            }
            return false;
        }

        @JvmField
        val RANDOM = Random()

        @JvmStatic
        fun sendRespawnRequest(p: Player, time: Int, game: Game?) {
            if (game == null || game.gameStop) {
                return
            }
            CallBack.cancelButtonRequest(p)
            CallBack.sendButtonRequest(p, arrayOf("§8[§c§l系统§8]您的复活次数已用完,是否使用复活币复活？ §e§l§n点击我使用复活币"),
                    { p: Player, input: Int? ->
                        if (input === null) {
                            return@sendButtonRequest
                        }
                        if (game.gameStop) {
                            return@sendButtonRequest
                        }
                        if (p.gameMode == GameMode.SPECTATOR) {
                            return@sendButtonRequest
                        }
                        if (Setting.hasRespawnCoinAndRemove(p)) {
                            p.teleport(game.startLocation)
                            p.gameMode = GameMode.SURVIVAL
                        } else {
                            p.sendMessage("§c你没有足够的复活币")
                            sendRespawnRequest(p, time, game)
                        }
                    }, time)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onRespawn(evt: PlayerRespawnEvent) {
        if (inGame(evt.player)) {
            evt.respawnLocation = this.startLocation
            val death = playerDeathTimes[evt.player.name] ?: 0
            if (death > this.dun.maxDeath) {
                evt.player.sendMessage("§c你的复活次数已经用尽")
                Bukkit.getScheduler().runTaskLater(Main.getMain(), {
                    evt.player.gameMode = GameMode.SPECTATOR
                    sendRespawnRequest(evt.player, this.dun.timeLimit * 60, this)
                }, 1L)
            }
        }
    }

    fun inWorld(wd: World): Boolean = wd === world

    fun getPlayers(): List<Player> = team.getPlayers()

    fun win() {
        this.broadcast("§6副本挑战成功 30秒后传送回原来的世界")
        gameStop = true
        val task = object : BukkitRunnable() {
            var time = 30
            override fun run() {
                if (time <= 0) {
                    this.cancel()
                    destroy()
                    return
                }
                for (p in getPlayers()) {
                    TitleUtils.sendTitle(p, 1, 18, 1, "§6副本挑战成功", "${time}秒后传送回原来的世界")
                }
                time--
            }
        }.runTaskTimer(Main.getMain(), 20L, 20L)
    }

    fun gameover(overtime: Boolean = false) {
        if (overtime) {
            this.broadcast("§c副本挑战失败")
            this.destroy()
        }
        this.broadcast("§c副本挑战失败 30秒内可使用复活币")
        gameStop = true
        for (p in getPlayers()) {
            sendRespawnRequest(p, 30, this)
        }
        val task = object : BukkitRunnable() {
            var time = 30
            override fun run() {
                if (checkAlive()) {
                    this.cancel()
                    gameStop = false
                    return
                }
                for (p in getPlayers()) {
                    TitleUtils.sendTitle(p, 1, 18, 1, "§c副本挑战失败", "${time}秒内可使用复活币")
                }
                time--
                if (time <= 0) {
                    this.cancel()
                    destroy()
                    return
                }
            }
        }.runTaskTimer(Main.getMain(), 20L, 20L)
    }

    fun destroy() {
        for (p in team.getPlayers()) {
            CallBack.cancelButtonRequest(p)
            p.teleport(playerFrom[p.name])
        }
        PlayerManager.doIt {
            for (k in playerFrom.keys) {
                it.remove(k)
            }
        }
        team.playingGame = null
        this.team.inGame = false
        this.dun.removeGame(this)
        HandlerList.unregisterAll(this)
        this.task.cancel()
        val f = File(Bukkit.getWorldContainer(), this.world.name)
        Bukkit.unloadWorld(this.world.name, false)
        delete(f)
    }

    private fun delete(f: File) {
        if (f.isDirectory) {
            for (d in f.listFiles()) {
                delete(d)
            }
        }
        f.delete()
    }

}