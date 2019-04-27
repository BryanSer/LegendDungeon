@file:Suppress("unused")

package br.kt.legenddungeon

import Br.API.GUI.Ex.UIManager
import br.kt.legenddungeon.sign.LDSign
import br.kt.legenddungeon.sign.SignType
import br.kt.legenddungeon.sign.UnTriggerable
import br.kt.legenddungeon.trigger.Trigger
import br.kt.legenddungeon.trigger.TriggerType
import me.clip.placeholderapi.external.EZPlaceholderHook
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.configuration.serialization.ConfigurationSerialization
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class Main : JavaPlugin() {

    override fun onEnable() {
        mainInstance = this
        run {
            val list = ArrayList<File>()
            for (f in Bukkit.getWorldContainer().listFiles()) {
                if (f.name.startsWith("LD_Game_")) {
                    list.add(f)
                }
            }
            list.forEach(Dungeon::delete)
        }
        for (k in TriggerType.values());
        for (k in SignType.values());
        ConfigurationSerialization.registerClass(Dungeon::class.java)
        PlayerManager.load()
        DungeonManager.load()
        TeamManager.init()
        Setting.loadConfig()
        initDunCommand()
        DungeonManager.init()
        UIManager.RegisterUI(RespawnUI())
        UIManager.RegisterUI(LootUI())
        hookPAPI()
        Bukkit.getPluginManager().registerEvents(Game.Companion, this)

    }

    override fun onDisable() {
        DungeonManager.save()
        PlayerManager.save()
    }

    companion object {
        private var mainInstance: Main? = null
        @JvmStatic
        fun getMain(): Main {
            return mainInstance as Main
        }
        fun hookPAPI() {
            object : EZPlaceholderHook(mainInstance, "legenddungeon") {
                override fun onPlaceholderRequest(p: Player, params: String): String {
                    when (params) {
                        "teamleader" -> {
                            val t = TeamManager.getTeam(p)
                            return t?.leader?.name ?: ""
                        }
                        "dun" -> {
                            val t = TeamManager.getTeam(p)
                            return t?.playingGame?.dun?.name ?: ""
                        }
                        "team1" -> {
                            val t = TeamManager.getTeam(p)
                            if (t == null) {
                                return ""
                            }
                            val pt = t.members.getOrNull(0)
                            return pt?.name ?: ""
                        }
                        "team2" -> {
                            val t = TeamManager.getTeam(p)
                            if (t == null) {
                                return ""
                            }
                            val pt = t.members.getOrNull(1)
                            return pt?.name ?: ""
                        }
                        "team3" -> {
                            val t = TeamManager.getTeam(p)
                            if (t == null) {
                                return ""
                            }
                            val pt = t.members.getOrNull(2)
                            return pt?.name ?: ""
                        }
                    }
                    return ""
                }
            }.hook()
        }
    }

    fun initDunCommand() {
        this.getCommand("LegendDungeon")
                .setExecutor { p, command, label, args ->
                    Boolean
                    if (args.isEmpty() || args[0] == "help") {
                        return@setExecutor false
                    }
                    if (!p.isOp) {
                        if (args[0].equals("play", true)) {
                            Bukkit.dispatchCommand(p, "ldp play ${args[1]}")
                            return@setExecutor true
                        }
                        return@setExecutor true
                    }
                    if (p !is Player) {
                        return@setExecutor false
                    }
                    when (args[0]) {
                        "create" -> {
                            if (args.size < 2) {
                                p.sendMessage("§c参数不足")
                                return@setExecutor false
                            }
                            val name = args[1]
                            if (DungeonManager.getDungeons().containsKey(name)) {
                                p.sendMessage("§c已存在同名副本")
                                return@setExecutor true
                            }
                            val dun = Dungeon(name)
                            DungeonManager.addDungeon(dun)
                            p.teleport(Location(dun.baseWorld, 0.0, 5.0, 0.0))
                            p.sendMessage("§6已创建副本$name")
                            return@setExecutor true
                        }
                        "goto" -> {
                            if (args.size < 2) {
                                p.sendMessage("§c参数不足")
                                return@setExecutor false
                            }
                            val dun = DungeonManager.getDungeon(args[1])
                            if (dun === null) {
                                p.sendMessage("§c找不到副本")
                                return@setExecutor true
                            }
                            p.teleport(Location(dun.baseWorld, 0.0, 5.0, 0.0))
                            p.sendMessage("§6已传送至${args[1]}")
                            return@setExecutor true
                        }
                        "triggers" -> {
                            p.sendMessage("§e§l[]为必填参数 ()为可选参数 i表示整数 d表示小数 s表示字符串")
                            for (tri in TriggerType.values()) {
                                p.sendMessage("§6${tri.type}: ")
                                for (s in tri.des) {
                                    p.sendMessage("§b    $s")
                                }
                            }
                            return@setExecutor true
                        }
                        "signs" -> {
                            p.sendMessage("§e§l[]为必填参数 ()为可选参数 i表示整数 d表示小数 s表示字符串")
                            for (tri in SignType.values()) {
                                p.sendMessage("§6${tri.type}: ")
                                for (s in tri.des) {
                                    p.sendMessage("§b    $s")
                                }
                            }
                            return@setExecutor true
                        }
                        "cleartgr" -> {
                            val targetBlock = p.getTargetBlock(null as MutableSet<Material>?, 50)
                            if (targetBlock == null || !DungeonManager.isSign(targetBlock)) {
                                p.sendMessage("§c你没有指向牌子")
                                return@setExecutor true
                            }
                            val loc = targetBlock.location
                            val dun = DungeonManager.getDungeon(loc.world)
                            if (dun == null) {
                                p.sendMessage("§c你不在副本世界")
                                return@setExecutor true
                            }
                            if (dun.isEnable) {
                                p.sendMessage("§c无法对开启中的副本修改内部的牌子")
                                return@setExecutor true
                            }
                            var tarsign: LDSign? = null
                            for (sign in dun.signs) {
                                if (sign.location.block == targetBlock) {
                                    tarsign = sign
                                    break
                                }
                            }
                            if (tarsign === null) {
                                p.sendMessage("§c这个不是副本牌子")
                                return@setExecutor true
                            }
                            if (tarsign is UnTriggerable) {
                                p.sendMessage("§c这个牌子不接受触发器")
                                return@setExecutor true
                            }
                            tarsign.triggers.clear()
                            p.sendMessage("§6清除完成")
                            return@setExecutor true
                        }
                        "tgr", "trigger" -> {
                            if (args.size < 2) {
                                p.sendMessage("§c参数不足 请输入/$label triggers 查看触发器说明")
                                return@setExecutor true
                            }

                            val targetBlock = p.getTargetBlock(null as MutableSet<Material>?, 50)
                            if (targetBlock == null || !DungeonManager.isSign(targetBlock)) {
                                p.sendMessage("§c你没有指向牌子")
                                return@setExecutor true
                            }
                            val loc = targetBlock.location
                            val dun = DungeonManager.getDungeon(loc.world)
                            if (dun == null) {
                                p.sendMessage("§c你不在副本世界")
                                return@setExecutor true
                            }
                            if (dun.isEnable) {
                                p.sendMessage("§c无法对开启中的副本修改内部的牌子")
                                return@setExecutor true
                            }
                            var tarsign: LDSign? = null
                            for (sign in dun.signs) {
                                if (sign.location.block == targetBlock) {
                                    tarsign = sign
                                    break
                                }
                            }
                            if (tarsign === null) {
                                p.sendMessage("§c这个不是副本牌子")
                                return@setExecutor true
                            }
                            if (tarsign is UnTriggerable) {
                                p.sendMessage("§c这个牌子不接受触发器")
                                return@setExecutor true
                            }
                            val trit = TriggerType.getTriggerType(args[1])
                            if (trit === null) {
                                p.sendMessage("§c找不到触发器${args[1]}")
                                return@setExecutor true
                            }
                            val arg = ArrayList<String>()
                            if (args.size > 2)
                                for (i in 2..(args.size - 1)) {
                                    arg.add(args[i])
                                }
                            val tri = trit.newInstance<Trigger>()
                            if (tri.arguments(arg.toArray(arrayOfNulls(arg.size)))) {
                                p.sendMessage("§6添加成功")
                                tarsign.triggers.add(tri)
                            } else {
                                p.sendMessage("§c参数不正确")
                            }
                            return@setExecutor true
                        }
                        "maxdeath" -> {
                            if (args.size < 3) {
                                p.sendMessage("§c参数不足")
                                return@setExecutor true
                            }
                            val dun = DungeonManager.getDungeon(args[1])
                            if (dun === null) {
                                p.sendMessage("§c找不到副本")
                                return@setExecutor true
                            }
                            dun.maxDeath = args[2].toInt()
                            p.sendMessage("§6设置完成")
                            return@setExecutor true
                        }
                        "time" -> {
                            if (args.size < 3) {
                                p.sendMessage("§c参数不足")
                                return@setExecutor true
                            }
                            val dun = DungeonManager.getDungeon(args[1])
                            if (dun === null) {
                                p.sendMessage("§c找不到副本")
                                return@setExecutor true
                            }
                            dun.timeLimit = args[2].toInt()
                            p.sendMessage("§6设置完成")
                            return@setExecutor true
                        }
                        "toggopen" -> {
                            if (args.size < 2) {
                                p.sendMessage("§c参数不足")
                                return@setExecutor true
                            }
                            val dun = DungeonManager.getDungeon(args[1])
                            if (dun === null) {
                                p.sendMessage("§c找不到副本")
                                return@setExecutor true
                            }
                            dun.isEnable = (!dun.isEnable)
                            if (dun.isEnable) {
                                p.sendMessage("§6设置完成 当前副本状态为: 启用")
                            } else {
                                p.sendMessage("§6设置完成 当前副本状态为: 关闭")
                            }
                            return@setExecutor true
                        }
                        "delete" -> {
                            if (args.size < 2) {
                                p.sendMessage("§c参数不足")
                                return@setExecutor true
                            }
                            val dun = DungeonManager.getDungeon(args[1])
                            if (dun === null) {
                                p.sendMessage("§c找不到副本")
                                return@setExecutor true
                            }
                            dun.isEnable = false
                            DungeonManager.deleteDungeon(dun)
                            return@setExecutor true
                        }
                        "play" -> {
                            Bukkit.dispatchCommand(p, "ldp play ${args[1]}")
                            return@setExecutor true
                        }
                    }
                    return@setExecutor false
                }
    }
}