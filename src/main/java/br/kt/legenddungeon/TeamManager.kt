package br.kt.legenddungeon

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.scheduler.BukkitTask
import java.io.File

class Team(var leader: Player) {
    val members = ArrayList<Player>()
    var inGame = false

    fun getPlayers(): List<Player> {
        val list = ArrayList<Player>(members)
        list.add(leader)
        return list
    }

    fun broadcast(msg: String) {
        for (p in this.getPlayers()) {
            p.sendMessage(msg)
        }
    }

    fun inTeam(p: Player): Boolean {
        return p == leader || members.contains(p)
    }

    fun join(p: Player) {
        this.broadcast("§6[${p.name}]加入了小队")
        members.add(p)
        TeamManager.playerMap[p.name] = leader.name
    }

    fun leave(p: Player, kick: Boolean = false) {
        if (p === leader) {
            this.disband()
            return
        }
        if (!kick)
            this.broadcast("§6[${p.name}]离开了队伍")
        else
            this.broadcast("§6[${p.name}]被踢出队伍")
        this.members.remove(p)
        TeamManager.playerMap.remove(p.name)
    }

    fun disband() {
        this.broadcast("§6队伍已被队长解散")
        for (key in TeamManager.inviteMap.keys) {
            if (TeamManager.inviteMap[key]?.leader == this.leader.name) {
                TeamManager.inviteMap[key]?.cancel()
                TeamManager.inviteMap.remove(key)
            }
        }
        for (p in this.getPlayers()) {
            TeamManager.playerMap.remove(p.name)
        }
        this.members.clear()
        TeamManager.teamMap.remove(leader.name)
    }

    fun isLeader(p: Player): Boolean = this.leader === p

    fun play(dun: Dungeon?): String {
        if (dun == null) {
            return "§c找不到副本或参加副本的队伍过多"
        }
        if (inGame) {
            return "当前队伍已经在游戏中了"
        }
        if (!dun.isEnable) {
            return "§c这个副本没有开启"
        }
        return dun.createGame(this)
    }

    fun isDisband(): Boolean {
        return !TeamManager.teamMap.containsKey(this.leader.name)
    }
}


data class InviteData(val leader: String, val task: BukkitTask) {
    fun cancel() {
        this.task.cancel()
    }

    fun getTeam(): Team? = TeamManager.teamMap[leader]
}

object TeamManager {
    val teamMap: MutableMap<String, Team> = HashMap()//队长->队伍
    val playerMap: MutableMap<String, String> = HashMap()//玩家->队长

    val inviteMap: MutableMap<String, InviteData> = HashMap()//受邀玩家
    fun getTeam(p: Player): Team? = teamMap[p.name]

    fun createTeam(leader: Player): String {
        if (playerMap.containsKey(leader.name)) {
            return "§c你已经在一个队伍里了"
        }
        val team: Team = Team(leader)
        playerMap[leader.name] = leader.name
        teamMap[leader.name] = team
        return "§6队伍创建成功"
    }

    fun init() {
        Bukkit.getPluginManager().registerEvents(
                object : Listener {
                    @EventHandler
                    fun onPlayerQuit(evt: PlayerQuitEvent) {
                        if (playerMap.containsKey(evt.player.name)) {
                            val team = teamMap[playerMap[evt.player.name]]
                            if (team?.inGame == false)
                                team.leave(evt.player)
                        }
                    }
                }, Main.getMain()
        )
        val cmd = Main.getMain().getCommand("LegendDungeonTeam")
        cmd.setExecutor { p, command, label, args ->
            Boolean
            if (args.isEmpty() || args[0].equals("help", true)) {
                return@setExecutor false
            }
            if (p !is Player) {
                return@setExecutor false
            }
            when (args[0]) {
                "create" -> {
                    if (playerMap.containsKey(p.name)) {
                        p.sendMessage("§c你已经在一个队伍里了")
                        return@setExecutor true
                    }
                    p.sendMessage(createTeam(p))
                    return@setExecutor true
                }
                "leave" -> {
                    if (!playerMap.containsKey(p.name)) {
                        p.sendMessage("§c你没有在任何队伍里")
                        return@setExecutor true
                    }
                    val t = getTeam(p)
                    t?.leave(p)
                    return@setExecutor true
                }
                "invite" -> {
                    if (!playerMap.containsKey(p.name)) {
                        p.sendMessage("§c你没有在任何队伍里")
                        return@setExecutor true
                    }
                    if (args.size < 2) {
                        p.sendMessage("§c参数不足 需要指定邀请玩家")
                        return@setExecutor true
                    }
                    p.sendMessage(invite(p, args[1]))
                    return@setExecutor true
                }
                "accept" -> {
                    accept(p)
                    return@setExecutor true
                }
                "refuse" -> {
                    refuse(p)
                    return@setExecutor true
                }
                "kick" -> {
                    if (!playerMap.containsKey(p.name)) {
                        p.sendMessage("§c你没有在任何队伍里")
                        return@setExecutor true
                    }
                    if (args.size < 2) {
                        p.sendMessage("§c参数不足 需要指定邀请玩家")
                        return@setExecutor true
                    }
                    val target = Bukkit.getPlayerExact(args[1])
                    if (target === null) {
                        p.sendMessage("§c找不到玩家")
                        return@setExecutor true
                    }
                    val team = getTeam(p)!!
                    if (p != team.leader) {
                        p.sendMessage("§c你不能踢出小队成员")
                        return@setExecutor true
                    }
                    if (p == target) {
                        p.sendMessage("§c你不能踢出你自己")
                        return@setExecutor true
                    }
                    if (!team.members.contains(target)) {
                        p.sendMessage("§c对方不在你的队伍中")
                        return@setExecutor true
                    }
                    team.leave(target, true)
                    p.sendMessage("§c操作完成")
                    return@setExecutor true
                }
                "leader" -> {
                    if (!playerMap.containsKey(p.name)) {
                        p.sendMessage("§c你没有在任何队伍里")
                        return@setExecutor true
                    }
                    if (args.size < 2) {
                        p.sendMessage("§c参数不足 需要指定邀请玩家")
                        return@setExecutor true
                    }
                    val target = Bukkit.getPlayerExact(args[1])
                    if (target === null) {
                        p.sendMessage("§c找不到玩家")
                        return@setExecutor true
                    }
                    val team = getTeam(p)!!
                    if (p != team.leader) {
                        p.sendMessage("§c你不是队长")
                        return@setExecutor true
                    }
                    if (p == target) {
                        p.sendMessage("§c把队伍权限交给自己毫无意义")
                        return@setExecutor true
                    }
                    if (!team.members.contains(target)) {
                        p.sendMessage("§c对方不在你的队伍中")
                        return@setExecutor true
                    }
                    team.members.add(team.leader)
                    team.members.remove(target)
                    team.leader = target
                    team.broadcast("§c队长已经被移交给${target.name}")
                    return@setExecutor true
                }
                "disband" -> {
                    if (!playerMap.containsKey(p.name)) {
                        p.sendMessage("§c你没有在任何队伍里")
                        return@setExecutor true
                    }
                    val team = getTeam(p)!!
                    if (p != team.leader) {
                        p.sendMessage("§c你不是队长")
                        return@setExecutor true
                    }
                    team.disband()
                    return@setExecutor true
                }
                "play" -> {
                    if (args.size > 2 && args[2] == "tryforcreate") {
                        return@setExecutor true
                    }
                    if (!playerMap.containsKey(p.name)) {
                        Bukkit.getScheduler().runTaskLater(Main.getMain(), {
                            p.chat("/ldp create")
                            p.chat("/ldp play ${args[1]} tryforcreate")
                        }, 1L)
                        return@setExecutor true
                    }
                    val team = getTeam(p)!!
                    if (p != team.leader) {
                        p.sendMessage("§c你不是队长")
                        return@setExecutor true
                    }
                    if (args.size < 2) {
                        p.sendMessage("§c参数不足")
                        return@setExecutor true
                    }
                    p.sendMessage(team.play(DungeonManager.getDungeon(args[1])))
                    return@setExecutor true
                }
            }
            return@setExecutor false
        }
    }

    fun invite(form: Player, target: String): String {
        val team = getTeam(form) ?: return "§c你没有在队伍中"
        if (!team.isLeader(form)) {
            return "§c你不是队长"
        }
        val p = Bukkit.getPlayerExact(target) ?: return "§c找不到玩家$target"
        if (inviteMap.containsKey(p.name)) {
            return "§c对方还有一个未处理的邀请 请稍后重试"
        }
        if (playerMap.containsKey(p.name)) {
            return "§c对方已经在一个小队中"
        }
        if (team.getPlayers().size >= Setting.Team_Max_Members) {
            return "§c小队已满人 无法邀请"
        }
        inviteMap[p.name] = InviteData(
                team.leader.name,
                Bukkit.getScheduler().runTaskLater(Main.getMain(), {
                    p.sendMessage("§6您有一个组队请求已过期")
                    inviteMap.remove(p.name)
                }, 600)
        )
        p.sendMessage("§6[${team.leader.name}]的小队 向你发送邀请")
        p.sendMessage("输入/ldp accept接受或/ldp decline拒绝")
        return "§6已成功发送邀请"
    }

    fun accept(p: Player): String {
        if (!inviteMap.containsKey(p.name)) {
            return "§c你没有待处理的请求"
        }
        val data = inviteMap.remove(p.name)!!
        data.cancel()
        if (!teamMap.containsKey(data.leader)) {
            return "§c对方小队已解散 无法接受请求"
        }
        val team = data.getTeam()!!
        if (team.getPlayers().size >= Setting.Team_Max_Members) {
            return "§c对方小队已满人 无法加入"
        }
        team.join(p)
        return "§6成功加入小队$[${team.leader.name}]"
    }

    fun refuse(p: Player): String {
        if (!inviteMap.containsKey(p.name)) {
            return "§c你没有待处理的请求"
        }
        val data = inviteMap.remove(p.name)!!
        data.cancel()
        if (!teamMap.containsKey(data.leader)) {
            return "§6成功拒绝了邀请"
        }
        val team = data.getTeam()!!
        team.broadcast("§6${p.name}拒绝了组队请求")
        return "§6成功拒绝了邀请"
    }
}

object PlayerManager {
    val playerFrom = HashMap<String, Location>()

    fun doIt(f: (HashMap<String, Location>) -> Unit) {
        f(playerFrom)
        this.save()
    }

    fun load() {
        val f = File(Main.getMain().dataFolder, "lastLocation.yml")
        if (f.exists()) {
            val data = YamlConfiguration.loadConfiguration(f)
            for (key in data.getKeys(false)) {
                playerFrom[key] = data[key] as Location
            }
        }
        Bukkit.getPluginManager().registerEvents(object : Listener {
            @EventHandler
            fun onJoin(evt: PlayerJoinEvent) {
                if (playerFrom.containsKey(evt.player.name)) {
                    val loc = playerFrom.remove(evt.player.name)
                    evt.player.teleport(loc)
                }
            }
        }, Main.getMain())
    }

    fun save() {
        val f = File(Main.getMain().dataFolder, "lastLocation.yml")
        val data = YamlConfiguration()
        for (e in playerFrom) {
            data[e.key] = e.value
        }
        data.save(f)
    }
}
