package br.kt.legenddungeon.sign

import Br.API.ActionBar
import Br.API.Data.BrConfigurationSerializable
import Br.API.TitleUtils
import br.kt.legenddungeon.Game
import br.kt.legenddungeon.Setting
import io.lumine.xikage.mythicmobs.MythicMobs
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location

class TitleSign : LDSign {

    @BrConfigurationSerializable.Config(Path = "Title")
    var title: String? = null
    @BrConfigurationSerializable.Config(Path = "Subtitle")
    var subtitle: String? = null

    constructor(loc: Location) : super("Title", loc)
    constructor(args: Map<String, Any>) : super(args, "Title")

    override fun read(info: Array<String>): Boolean {
        title = ChatColor.translateAlternateColorCodes('&',
                (info.getOrNull(0) ?: "") + (info.getOrNull(1) ?: ""))
        subtitle = ChatColor.translateAlternateColorCodes('&', info.getOrNull(2) ?: "")
        return true
    }

    override fun onTrigger(game: Game, loc: Location) {
        for (p in game.getPlayers()) {
            TitleUtils.sendTitle(p, 10, 30, 10, title, subtitle)
        }
    }

}

class StartSign : LDSign, UnTriggerable {

    constructor(loc: Location) : super("Start", loc)
    constructor(args: Map<String, Any>) : super(args, "Start")

    override fun onTrigger(game: Game, loc: Location) {
    }

    override fun read(info: Array<String>): Boolean {
        return true
    }
}

class MobSign : LDSign {
    constructor(args: Map<String, Any>) : super(args, "Mob")

    @BrConfigurationSerializable.Config(Path = "MobName")
    private var mobName: String? = null // line 1
    @BrConfigurationSerializable.Config(Path = "MobAmount")
    private var mobAmount: Int = 1 // line 2

    override fun onTrigger(game: Game, loc: Location) {
        if (mobName != null) {
            for (i in 1..mobAmount)
                MythicMobs.inst().mobManager.spawnMob(mobName, loc)
        }
    }

    override fun read(info: Array<String>): Boolean {
        if (info.isEmpty())
            return false
        mobName = info[0]
        if (info.size > 1) {
            try {
                mobAmount = info[1].toInt()
            } catch (e: NumberFormatException) {
                mobAmount = 1
                return true
            }
        }
        return true
    }


    constructor(loc: Location) : super("Mob", loc)

}

class MessageSign : LDSign {

    constructor(loc: Location) : super("Message", loc)
    constructor(args: Map<String, Any>) : super(args, "Message")

    @BrConfigurationSerializable.Config(Path = "Message")
    private var message: String = ""

    override fun onTrigger(game: Game, loc: Location) {
        for (p in game.getPlayers()) {
            p.sendMessage("${Setting.Message_Prefix} $message")
        }
    }

    override fun read(info: Array<String>): Boolean {
        if (info.isEmpty())
            return false
        var str = ""
        for (s in info) {
            str += s
        }
        message = ChatColor.translateAlternateColorCodes('&', str)
        return true
    }

}

class ActionBarSign : LDSign {
    constructor(loc: Location) : super("ActionBar", loc)
    constructor(args: Map<String, Any>) : super(args, "ActionBar")

    @BrConfigurationSerializable.Config(Path = "Message")
    private var message: String = ""

    override fun onTrigger(game: Game, loc: Location) {
        for (p in game.getPlayers()) {
            ActionBar.sendActionBar(p, "${Setting.Message_Prefix} $message")
        }
    }

    override fun read(info: Array<String>): Boolean {
        if (info.isEmpty())
            return false
        var str = ""
        for (s in info) {
            str += s
        }
        message = ChatColor.translateAlternateColorCodes('&', str)
        return true
    }

}

class TeleportSign : LDSign {
    constructor(loc: Location) : super("Teleport", loc) {
        x = loc.blockX
        y = loc.blockY
        z = loc.blockZ
    }

    constructor(args: Map<String, Any>) : super(args, "Teleport")

    @BrConfigurationSerializable.Config
    var x: Int = 0
    @BrConfigurationSerializable.Config
    var y: Int = 0
    @BrConfigurationSerializable.Config
    var z: Int = 0

    override fun onTrigger(game: Game, loc: Location) {
        val target = Location(loc.world, x.toDouble(), y.toDouble(), z.toDouble())
        for (p in game.getPlayers()) {
            p.teleport(target)
        }
    }

    override fun read(info: Array<String>): Boolean {
        if (info.isEmpty()) {
            this.x = location.blockX
            this.y = location.blockY
            this.z = location.blockZ
            return true
        }
        if (info[0].isEmpty()) {
            this.x = location.blockX
            this.y = location.blockY
            this.z = location.blockZ
            return true
        }
        val str = info[0].split(" ")
        try {
            x = str[0].toInt()
            y = str[1].toInt()
            z = str[2].toInt()
        } catch (e: NumberFormatException) {
            return false
        } catch (e: ArrayIndexOutOfBoundsException) {
            return false
        }
        return true
    }

}

class CommandSign : LDSign {
    constructor(loc: Location) : super("Command", loc)
    constructor(args: Map<String, Any>) : super("Command", args["Location"] as Location) {
        val cmd = args.get("Command") as? String
        if (cmd == null) {
            val cmd = args.get("command") as? String
            if (cmd != null) {
                command = cmd
            }
        } else {
            command = cmd
        }
        super.triggers = args.get("Triggers") as TriggerList
    }

    @BrConfigurationSerializable.Config(Path = "Command")
    private var command: String = ""

    override fun serialize(): MutableMap<String, Any> {
        val map = LinkedHashMap<String, Any>()
        map["Location"] = this.location
        map["Command"] = this.command
        map["Triggers"] = this.triggers
        return map
    }

    override fun onTrigger(game: Game, loc: Location) {
        if (command.isEmpty())
            return
        for (p in game.getPlayers()) {
            val cmd = command.replace("%p", p.name)
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)
        }
    }

    override fun read(info: Array<String>): Boolean {
        if (info.isEmpty())
            return false
        var str = ""
        for (s in info) {
            str += s
        }
        command = str
        return true
    }


}

class CompleteSign : LDSign {
    constructor(loc: Location) : super("Complete", loc)
    constructor(args: Map<String, Any>) : super(args, "Complete")

    override fun onTrigger(game: Game, loc: Location) {
        game.win()
    }

    override fun read(info: Array<String>): Boolean {
        return true
    }

}