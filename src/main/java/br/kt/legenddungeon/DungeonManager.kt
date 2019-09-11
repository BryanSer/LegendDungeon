package br.kt.legenddungeon

import br.kt.legenddungeon.sign.LDSign
import br.kt.legenddungeon.sign.SignManager
import br.kt.legenddungeon.world.loadWorld
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.SignChangeEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerTeleportEvent
import java.io.File

object DungeonManager : Listener {
    private val dungeons: MutableMap<String, Dungeon> = HashMap()

    val whereFrom: MutableMap<String, Location> = HashMap()

    fun init() {
        Bukkit.getPluginManager().registerEvents(this, Main.getMain())
    }

    var loadConfig = false
    @EventHandler
    fun onPlayerJoin(evt: PlayerJoinEvent) {
        if (!loadConfig) {
            load()
            loadConfig = true
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTeleport(evt: PlayerTeleportEvent) {
        val dun = getDungeon(evt.to.world)
        if (dun != null && evt.from.world != evt.to.world) {
            whereFrom[evt.player.name] = evt.from.clone()
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEdit(evt: SignChangeEvent) {
        if (!evt.player.isOp) {
            return
        }
        val block = evt.block
        val loc = block.location
        val dun = getDungeon(loc.world) ?: return
        if (dun.isEnable) {
            evt.player.sendMessage("§c无法对开启中的副本修改内部的牌子")
            return
        }
        var type = evt.getLine(0)
        if (type == null || type.isEmpty()) {
            return
        }
        if (type.matches(Regex("\\[[a-zA-Z]*\\]"))) {
            type = type.replace("]", "").replace("[", "")
            val signtype = SignManager.getSignType(type)
            if (signtype === null) {
                evt.isCancelled = true
                evt.player.sendMessage("§c找不到合适的牌子类型")
                return
            }
            val ldsign = signtype.newInstance<LDSign>(block.location)
            val list = ArrayList<String>()
            for (i in 1..(evt.lines.size - 1)) {
                list.add(evt.lines[i])
            }
            try {
                if (!ldsign.read(list.toArray(arrayOfNulls(list.size)))) {
                    evt.player.sendMessage("§c牌子参数不正确 请重新放置")
                    evt.isCancelled = true
                    return
                }
            } catch (e: IllegalArgumentException) {
                evt.player.sendMessage("§c牌子参数不正确 请重新放置")
                evt.isCancelled = true
                return
            }
            dun.signs.add(ldsign)
            evt.setLine(0, "§b§l[${signtype.type}]")
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDestroy(evt: BlockBreakEvent) {
        if (!evt.player.isOp) {
            return
        }
        val block = evt.block
        val loc = block.location
        val dun = getDungeon(loc.world) ?: return
        if (dun.isEnable) {
            evt.isCancelled = true
            evt.player.sendMessage("§c无法对开启中的副本修改内部的牌子")
            return
        }
        var tarsign: LDSign? = null
        for (sign in dun.signs) {
            if (sign.location.block == block) {
                tarsign = sign
                break
            }
        }
        if (tarsign !== null) {
            dun.signs.remove(tarsign)
        }
    }

    fun isSign(b: Block): Boolean {
        return b.type == Material.SIGN || b.type == Material.WALL_SIGN || b.type == Material.SIGN_POST
    }

    fun getDungeon(w: World): Dungeon? {
        for (v in dungeons.values) {
            if (v.baseWorld === w) {
                return v
            }
        }
        return null
    }

    fun getDungeons(): Map<String, Dungeon> = dungeons

    fun getDungeon(name: String): Dungeon? = dungeons[name]

    fun deleteDungeon(dun: Dungeon) {
        if (dun.isEnable) {
            dun.isEnable = false
        }
        dungeons.remove(dun.name)
        dun.delete()
    }

    fun addDungeon(dun: Dungeon) {
        dungeons[dun.name] = dun
    }

    fun load() {
        val folder = File(Main.getMain().dataFolder, "Dungeons")
        if (!folder.exists()) {
            folder.mkdirs()
            return
        }
        for (f in folder.listFiles()) {
            val name = f.name.removeSuffix(".yml")
            val worldname = "LD_Base_$name"
            if (Bukkit.getWorld(worldname) == null) {
                loadWorld(name)
            }
            val data = YamlConfiguration.loadConfiguration(f)
            dungeons[name] = data["Dungeon"] as Dungeon
        }
    }

    fun load(name: String): String {
        val folder = File(Main.getMain().dataFolder, "Dungeons")
        if (!folder.exists()) {
            return "§c找不到数据"
        }
        val f = File(folder, "$name.yml")
        if (!f.exists()) {
            return "§c找不到数据"
        }
        val worldname = "LD_Base_$name"
        if (Bukkit.getWorld(worldname) == null) {
            loadWorld(name)
        }
        val data = YamlConfiguration.loadConfiguration(f)
        println(data)
        dungeons[name] = data["Dungeon"] as Dungeon
        return "§6读取成功"
    }

    fun save() {
        if(!loadConfig){
            return
        }
        val folder = File(Main.getMain().dataFolder, "Dungeons")
        if (!folder.exists()) {
            folder.mkdirs()
        }
        val saved = ArrayList<String>()
        for (e in dungeons) {
            val f = File(folder, e.key + ".yml")
            val yaml = YamlConfiguration()
            yaml.set("Dungeon", e.value)
            yaml.save(f)
            saved.add(e.key + ".yml")
        }
        for (f in folder.listFiles()) {
            if (!saved.contains(f.name)) {
                f.delete()
            }
        }
    }

    fun save(dun: Dungeon) {
        val folder = File(Main.getMain().dataFolder, "Dungeons")
        if (!folder.exists()) {
            folder.mkdirs()
        }
        val f = File(folder, dun.name + ".yml")
        val yaml = YamlConfiguration()
        yaml.set("Dungeon", dun)
        yaml.save(f)
    }
}