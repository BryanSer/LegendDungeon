package br.kt.legenddungeon

import Br.API.CallBack
import Br.API.TitleUtils
import Br.API.Utils
import br.kt.legenddungeon.event.LDGameDestroyEvent
import br.kt.legenddungeon.event.LDMobKillEvent
import br.kt.legenddungeon.sign.InGameSign
import br.kt.legenddungeon.sign.StartSign
import io.lumine.xikage.mythicmobs.MythicMobs
import io.lumine.xikage.mythicmobs.api.bukkit.events.MythicMobDeathEvent
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.util.*

data class FromData(
        val loc: Location,
        val mode: GameMode
) {
    fun go(p: Player) {
        p.sendMessage("§6正在离开副本")
        p.teleport(loc)
        p.gameMode = mode
    }
}

val DAMAGE_BOOT = true

class Game(
        world: World,
        val id: Int,
        val dun: Dungeon,
        val team: Team,
        val lootRule: LootRule? = LootRule.RANDOM
) : Listener {
    val uuid: UUID = world.uid
    val world: World
        get() = Bukkit.getWorld(uuid)

    private val signs = ArrayList<InGameSign>()
    private val task: BukkitTask
    val playerDeathTimes = mutableMapOf<String, Int>()
    private val startTIme = System.currentTimeMillis()
    var gameStop = false
    private var gameWin = false

    var respawnLocation: Location? = null
    private var lastRespawnMessage: Int = 0

    val playerFrom: MutableMap<String, FromData> = HashMap()


//    @EventHandler(priority = EventPriority.MONITOR)
//    fun damageBoot(evt: EntityDamageByEntityEvent) {
//        if (EnableRespawnCoin)
//            if (evt.damager.world === this.world && evt.damager !is Player && evt.damager is LivingEntity) {
//                val entity = MythicMobs.inst().mobManager.getMythicMobInstance(evt.damager)
//                        ?: return
//                evt.damage = entity.damage * 1.5
//            }
//    }

    init {
        team.playingGame = this
        for (s in dun.signs) {
            if (s is StartSign) {
                respawnLocation = s.getInGameLocation(this)
                continue
            }
            signs.add(s.createInGameSign(this))
        }
        if (respawnLocation === null) {
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
            lastRespawnMessage = (++lastRespawnMessage) % 400;
            if (lastRespawnMessage == 0 && EnableRespawnCoin)
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

    private fun processDrop(item: ItemStack, messaged: MutableSet<String>) {
        val amount = item.amount
        for (i in 1..amount) {
            val target = item.clone()
            target.amount = 1
            val p = randomPlayer()
            var list = TeamManager.playerLoot[p.name]
            if (list == null) {
                list = ArrayList()
                TeamManager.playerLoot[p.name] = list
            }
            list!!.add(target)
            if (!messaged.contains(p.name)) {
                Utils.sendCommandButton(p, "§6掉落物品已放入自有库 请点击§b§l此处§6打开", "/ldp drop ingameopen")
                messaged += p.name
            }
        }
    }

//    @EventHandler(priority = EventPriority.MONITOR)
//    fun onEntityDamage(evt: EntityDamageByEntityEvent) {
//        if (!EnableLootRule) {
//            if (evt.damager.world === this.world)
//                evt.isCancelled = false
//        }
//    }

//    fun checkDropItem() {
//        if (!EnableLootRule) {
//            return
//        }
//        val list = mutableListOf<Item>()
//        for (item in this.world.getEntitiesByClass(Item::class.java)) {
//            if (!isPlayerDrop(item))
//                list.add(item)
//        }
//        val it = list.iterator()
//        var add = false
//        while (it.hasNext()) {
//            val item = it.next()
//            it.remove()
//            val itemStack = item.itemStack.clone()
//            if (lootRule === LootRule.RANDOM) {
//                val amount = itemStack.amount
//                itemStack.amount = 1
//                for (i in 1..amount) {
//                    val t = randomPlayer()
//                    dropItem(t, itemStack)
//                }
//            } else {
//                team.lootItem.add(itemStack)
//                add = true
//            }
//            item.remove()
//        }
//        if (add) {
//            Utils.sendCommandButton(this.team.leader, "§6掉落物品已放入分配库 请点击§b§l此处§6打开", "/ldp loot ingameopen")
//        }
//    }

    private fun randomPlayer(): Player {
        val target = RANDOM.nextInt(this.team.size())
        if (target == 0) {
            return this.team.leader
        }
        return this.team.members[target - 1]
    }

    private val lastDamage = mutableMapOf<Int?, String>()

    @EventHandler(priority = EventPriority.LOWEST)
    fun onEntityDamage_setKiller(evt: EntityDamageByEntityEvent) {
        if (evt.entity.world != this.world) {
            return
        }
        val p = if (evt.damager is Player) {
            evt.damager as Player
        } else {
            if (evt.damager is Projectile) {
                val proj = evt.damager as Projectile
                if (proj.shooter is Player) {
                    proj.shooter as Player
                } else {
                    return;
                }
            } else {
                return;
            }
        }
        lastDamage[evt.entity.entityId] = p.name
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onEntityDeath_setKiller(evt: EntityDeathEvent) {
        if (evt.entity.world != this.world) {
            return
        }
        if (evt.entity.killer != null) {
            return
        }
        val p = Bukkit.getPlayerExact(lastDamage[evt.entity.entityId] ?: return) ?: return
        RefTool.setKiller(evt.entity, p)
    }

    @EventHandler
    fun onEntityDeath(evt: MythicMobDeathEvent) {
        if (!EnableLootRule) {
            return
        }
        if (evt.entity.world !== this.world) {
            return
        }
        if (lootRule === LootRule.RANDOM) {
            val msg = mutableSetOf<String>()
            evt.drops.forEach {
                processDrop(it, msg)
            }
        } else {
            evt.drops.map { it.clone() }.forEach {
                team.lootItem.add(it)
            }
            Utils.sendCommandButton(this.team.leader, "§6掉落物品已放入分配库 请点击§b§l此处§6打开", "/ldp loot ingameopen")
        }
        evt.drops.clear()
    }

    @EventHandler
    fun onEntityDeath(evt: EntityDeathEvent) {
        if (!EnableLootRule) {
            return
        }
        if (evt.entity.world !== this.world) {
            return
        }
        val ldmke = LDMobKillEvent(this, evt.entity)
        Bukkit.getPluginManager().callEvent(ldmke)
        val mm = MythicMobs.inst().mobManager.getMythicMobInstance(evt.entity)
        if (mm !== null) {
            return
        }
        if (lootRule === LootRule.RANDOM) {
            evt.drops.forEach {
                val msg = mutableSetOf<String>()
                processDrop(it, msg)
            }
        } else {
            evt.drops.map { it.clone() }.forEach {
                team.lootItem.add(it)
            }
            Utils.sendCommandButton(this.team.leader, "§6掉落物品已放入分配库 请点击§b§l此处§6打开", "/ldp loot ingameopen")
        }
        evt.drops.clear()
    }

    fun checkAlive(): Boolean {
        for (p in getPlayers()) {
            if (p.isDead) {
                return true
            }
            if (p.gameMode != GameMode.SPECTATOR) {
                return true
            }
        }
        return false
    }

    fun start() {
        for (p in team.getPlayers()) {
            playerFrom[p.name] = FromData(p.location, p.gameMode)
            p.teleport(this.respawnLocation)
            playerDeathTimes[p.name] = 0
            p.gameMode = GameMode.ADVENTURE
        }
        PlayerManager.doIt {
            for (e in playerFrom) {
                it[e.key] = e.value.loc
            }
        }
        if (EnableLootRule) {
            when (this.lootRule) {
                LootRule.LEADER -> {
                    this.broadcast("§6§l队长选择的掉落模式: §a§l[队长分配制]")
                }
                LootRule.RANDOM -> {
                    this.broadcast("§6§l队长选择的掉落模式: §b§l[随机分配制]")
                }
            }
        }
    }

    val leftPlayers = mutableListOf<String>()

    fun leave(p: Player, tip: Boolean = false) {
        if (p.isDead)
            p.spigot().respawn()
        if (p == this.team.leader && !gameWin) {
            val fd = playerFrom.remove(p.name)
            if (fd != null) {
                fd.go(p)
            }
            this.broadcast("§c队长离开了副本 副本自动结束")
            this.destroy()
            //this.team.disband()
            return
        }
        if (!gameWin)
            team.leave(p)
        val fd = playerFrom.remove(p.name)
        if (fd != null) {
            fd.go(p)
        }

        PlayerManager.doIt {
            it.remove(p.name)
        }
        this.broadcast("§e§l玩家${p.name}离开了副本")
        if (gameWin) {
            leftPlayers.add(p.name)
            val list = this.world.getEntitiesByClass(Player::class.java)
            if (list.isEmpty()) {
                this.destroy()
            }
        }
    }

    fun broadcast(msg: String) {
        for (p in this.getPlayers()) {
            p.sendMessage(msg)
        }
    }

    fun inGame(p: Player): Boolean = this.team.inTeam(p) && !this.leftPlayers.contains(p.name)

    fun anyDeath(): Boolean {
        for (p in this.getPlayers()) {
            if (dead.contains(p.name)) {
                return true
            }
        }
        return false
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onCommand(evt: PlayerCommandPreprocessEvent) {
        if (!inGame(evt.player)) {
            return
        }
        val msg = evt.message.toLowerCase()
        if (msg.contains("ldp loot ingameopen")) {
            if (dead.contains(evt.player.name)) {
                evt.isCancelled = true
                return
            }

            return
        }
        if (msg.contains("ldp drop ingameopen")) {
            if (dead.contains(evt.player.name)) {
                evt.isCancelled = true
                return
            }

            return
        }
        if (msg.contains("ldp respawn ingame")) {
            evt.isCancelled = true
            val p = evt.player
            if (p.gameMode != GameMode.SPECTATOR) {
                return
            }
            if (Setting.hasRespawnCoinAndRemove(p)) {
                p.teleport(this.respawnLocation)
                p.gameMode = GameMode.ADVENTURE
            } else {
                p.sendMessage("§c你没有足够的复活币")
            }
            return
        }
        if (msg.contains("ldp respawn")) {
            return
        }
        if (msg.matches(Regex("/?leave"))) {
            if (anyDeath()) {
                evt.isCancelled = true
                return
            }
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

    val dead = mutableSetOf<String>()

    @EventHandler
    fun onDeath(evt: PlayerDeathEvent) {
        if (!inGame(evt.entity)) {
            return
        }
        val time = (playerDeathTimes[evt.entity.name] ?: 0) + 1
        playerDeathTimes[evt.entity.name] = time

        dead += evt.entity.name
        Bukkit.getScheduler().runTaskLater(Main.getMain(), {
            evt.entity.spigot().respawn()
            evt.entity.teleport(this.respawnLocation)
            val death = playerDeathTimes[evt.entity.name] ?: 0
            if (death > this.dun.maxDeath) {
                evt.entity.sendMessage("§c你的复活次数已经用尽")
                evt.entity.gameMode = GameMode.SPECTATOR
                if (EnableRespawnCoin)
                    Utils.sendCommandButton(evt.entity, "§8[§c§l系统§8]您的复活次数已用完,是否使用复活币复活？ §e§l§n点击我使用复活币", "/ldp respawn ingame")
            }
            dead -= evt.entity.name
        }, 10)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onDropItem(evt: PlayerDropItemEvent) {
        if (!EnableLootRule) {
            return
        }
        if (inGame(evt.player)) {
            if (lootRule != LootRule.LEADER) {
                evt.isCancelled = true
            }
//            setPlayerDrop(evt.itemDrop)
        }
    }

    companion object : Listener {
        const val GAMEOVER_WAIT_TIME: Int = 30
//        val drop = mutableSetOf<Int>()
//
//        fun dropItem(p: Player, item: ItemStack) {
//            safeGiveItem(p, item) { p, item ->
//            }
//        }
//
//        @JvmStatic
//        fun safeGiveItem(p: Player, item: ItemStack, whenInvFull: (Player, ItemStack) -> Unit) {
//            val item = item.clone()
//            if (p.inventory.firstEmpty() == -1 && item.maxStackSize == 1) { // 背包满了
//                whenInvFull(p, item)
//                return
//            }
//            var allowCount = 0
//            for (sInvItem in p.inventory.contents) {
//                if (sInvItem != null && sInvItem.type != Material.AIR) {
//                    if (sInvItem.isSimilar(item)) {
//                        allowCount += sInvItem.maxStackSize - sInvItem.amount
//                    }
//                } else {
//                    allowCount += item.maxStackSize
//                }
//                if (allowCount >= item.amount) {
//                    break
//                }
//            }
//            if (allowCount < item.amount) {
//                val dropItems = item.clone()
//                dropItems.amount = item.amount - allowCount
//                item.amount = allowCount
//                whenInvFull(p, dropItems)
//            }
//            if (item.maxStackSize != 0) {
//                for (i in 0 until item.amount / item.maxStackSize) {
//                    val giveItem = item.clone()
//                    giveItem.amount = giveItem.maxStackSize
//                    p.inventory.addItem(giveItem)
//                }
//            }
//            if (item.maxStackSize > 1) {
//                val leftItemCount = item.amount % item.maxStackSize
//                if (leftItemCount != 0) {
//                    val giveItem = item.clone()
//                    giveItem.amount = leftItemCount
//                    p.inventory.addItem(giveItem)
//                }
//            }
//            p.updateInventory()
//        }
//
//        @EventHandler
//        fun onPick(evt: PlayerPickupItemEvent) {
//            drop.remove(evt.item.entityId)
//        }
//
//        fun setPlayerDrop(item: Item) {
//            drop.add(item.entityId)
//        }
//
//        @JvmStatic
//        fun isPlayerDrop(item: Item): Boolean {
//            return drop.contains(item.entityId)
//        }

        @JvmField
        val RANDOM = Random()

    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onRespawn(evt: PlayerRespawnEvent) {
        if (inGame(evt.player)) {
            evt.respawnLocation = this.respawnLocation
//            val death = playerDeathTimes[evt.player.name] ?: 0
//            if (death > this.dun.maxDeath) {
//                evt.player.sendMessage("§c你的复活次数已经用尽")
//                if (EnableRespawnCoin)
//                    Bukkit.getScheduler().runTaskLater(Main.getMain(), {
//                        evt.player.gameMode = GameMode.SPECTATOR
//                        Utils.sendCommandButton(evt.player, "§8[§c§l系统§8]您的复活次数已用完,是否使用复活币复活？ §e§l§n点击我使用复活币", "/ldp respawn ingame")
//                    }, 1L)
//            }
        }
    }

    fun inWorld(wd: World): Boolean = wd === world

    fun getPlayers(): List<Player> = team.getPlayers().filter(::inGame).toList()

    fun win() {
        this.broadcast("§6副本挑战成功 30秒后传送回原来的世界")
        gameStop = true
        gameWin = true
        val task = object : BukkitRunnable() {

            var time = 30
            override fun run() {
                if (time <= 0) {
                    this.cancel()
                    destroy()
                    return
                }
                if (time == 30 || time <= 10)
                    for (p in getPlayers()) {
                        TitleUtils.sendTitle(p, 1, 18, 1, "§6副本挑战成功", "${time}秒后传送回原来的世界")
                    }
                time--
            }
        }.runTaskTimer(Main.getMain(), 20L, 20L)
    }

    fun gameover(overtime: Boolean = false) {
        if (overtime || !EnableRespawnCoin) {
            this.broadcast("§c副本挑战失败")
            this.destroy()
        }
        gameStop = true
        //gameWin = true
        if (EnableRespawnCoin) {
            this.broadcast("§c副本挑战失败 $GAMEOVER_WAIT_TIME 秒内可使用复活币")
            for (p in getPlayers()) {
                Utils.sendCommandButton(p, "§8[§c§l系统§8]队伍全体的复活次数已用完,是否使用复活币复活？ §e§l§n点击我使用复活币", "/ldp respawn")
            }
        } else {
            destroy()
            return
        }
        val task = object : BukkitRunnable() {
            var time = GAMEOVER_WAIT_TIME
            override fun run() {
                if (checkAlive()) {
                    this.cancel()
                    gameStop = false
                    gameWin = false
                    return
                }
                if (time == GAMEOVER_WAIT_TIME || time <= 10)
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

    private var destoryed = false

    fun destroy() {
        if (destoryed) {
            return
        }
        this.broadcast("§c正在摧毁副本世界")
        destoryed = true
        val evt = LDGameDestroyEvent(this)
        Bukkit.getPluginManager().callEvent(evt)
        HandlerList.unregisterAll(this)
        for (s in signs) {
            s.destroy()
        }
        for (p in team.getPlayers()) {
            CallBack.cancelButtonRequest(p)
            playerFrom[p.name]?.go(p)
        }
        PlayerManager.doIt {
            for (k in playerFrom.keys) {
                it.remove(k)
            }
        }
        for (e in this.world.entities) {
            e.remove()
        }
        team.playingGame = null
        this.team.inGame = false
        this.dun.removeGame(this)
        this.task.cancel()
        val f = File(Bukkit.getWorldContainer(), this.world.name)
        val del = File(f, "session.lock")
        del.delete()
        Bukkit.unloadWorld(this.world, false)
        delete(f)
        respawnLocation = null
        signs.clear()
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