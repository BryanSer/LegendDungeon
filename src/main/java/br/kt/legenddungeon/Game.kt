package br.kt.legenddungeon

import br.kt.legenddungeon.sign.InGameSign
import br.kt.legenddungeon.sign.StartSign
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.util.*

class Game(val world: World, val id: Int, val dun: Dungeon, val team: Team) : Listener {
    val uuid: UUID = world.uid

    private val signs = ArrayList<InGameSign>()
    private val task: BukkitTask
    private var deathTimes = 0

    var startLocation: Location? = null

    init {
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
            for (sign in this.signs) {
                sign.checkTrigger()
            }
        }, 5, 5)
    }

    val playerFrom: MutableMap<String, Location> = HashMap()

    fun start() {
        for (p in team.getPlayers()) {
            playerFrom[p.name] = p.location
            p.teleport(this.startLocation)
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
        if (msg.matches(Regex("/?leave"))) {
            this.leave(evt.player)
        } else {
            evt.player.sendMessage("§c副本内不能使用命令 可输入/leave离开副本")
        }
        evt.isCancelled = true

    }

    @EventHandler
    fun onDeath(evt: PlayerDeathEvent) {
        if (!inGame(evt.entity)) {
            return
        }
        deathTimes++
        if (deathTimes >= dun.maxDeath) {
            this.gameover()
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onRespawn(evt: PlayerRespawnEvent) {
        if (inGame(evt.player)) {
            evt.respawnLocation = this.startLocation
        }
    }

    fun inWorld(wd: World): Boolean = wd === world

    fun getPlayers(): List<Player> = team.getPlayers()

    fun win() {
        this.broadcast("§6副本挑战成功 正在传送回原来的世界")
        destroy()
    }

    fun gameover() {
        this.broadcast("§c副本挑战失败 正在传送回原来的世界")
        destroy()
    }

    fun destroy() {
        for (p in team.getPlayers()) {
            p.teleport(playerFrom[p.name])
        }
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