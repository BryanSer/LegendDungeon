package br.kt.legenddungeon.sign

import Br.API.Data.BrConfigurationSerializable
import br.kt.legenddungeon.Game
import br.kt.legenddungeon.trigger.InGameTrigger
import br.kt.legenddungeon.trigger.TimesTrigger
import br.kt.legenddungeon.trigger.Trigger
import org.bukkit.Location
import org.bukkit.configuration.serialization.ConfigurationSerialization

typealias TriggerList = ArrayList<Trigger>
typealias InGameTriggerList = ArrayList<InGameTrigger>

abstract class LDSign : BrConfigurationSerializable {

    var type: SignType
    @BrConfigurationSerializable.Config(Path = "Location")
    var location: Location
        private set
    @BrConfigurationSerializable.Config(Path = "Triggers")
    val triggers: TriggerList = TriggerList()

    protected constructor(type: SignType, loc: Location) {
        this.type = type
        this.location = loc
    }

    constructor(args: Map<String, Any>, type: SignType) {
        this.type = type
        location = args["Location"] as Location
        BrConfigurationSerializable.deserialize(args, this)
    }

    //数组长度3  可能抛出ill arg
    abstract fun read(info: Array<String>): Boolean

    abstract fun onTrigger(game: Game, loc: Location)


    fun getTimes(): Int {
        var times = 1
        for (t in triggers) {
            if (t is TimesTrigger) {
                times = t.times
                break
            }
        }
        return times
    }

    fun createInGameSign(game: Game): InGameSign {
        val loc = this.location.clone()
        loc.world = game.world
        return InGameSign(this, game, loc)
    }

    fun getInGameLocation(game: Game): Location {
        val loc = this.location.clone()
        loc.world = game.world
        return loc
    }

}

class InGameSign(
        private val sign: LDSign,
        private val game: Game,
        private val location: Location
) {
    private var triggerIndex = 0
    private var triggeredTimes = 0
    private val maxTriggerTimes = this.sign.getTimes()
    private val triggers = InGameTriggerList()

    init {
        for (tri in sign.triggers) {
            triggers.add(tri.create(game))
        }
    }

    fun registerTrigger(tri: Trigger) {
        if (sign is UnTriggerable)
            return
        sign.triggers.add(tri)
    }

    fun checkTrigger() {
        if (triggeredTimes >= maxTriggerTimes) {
            return
        }
        if (triggers.size <= triggerIndex) {
            if (triggers.isEmpty()) {
                sign.onTrigger(this.game, this.location)
                triggeredTimes++
            }
            return
        }
        val tri = triggers[triggerIndex]
        if (tri.check(location)) {
            triggerIndex++
        }
        if (triggerIndex >= triggers.size) {
            sign.onTrigger(this.game, this.location)
            triggeredTimes++
            if (triggeredTimes < maxTriggerTimes) {
                triggerIndex = 0
            }
        }
    }
}

interface UnTriggerable

enum class SignType(val type: String, private val clazz: Class<out LDSign>, val des: Array<String>) {
    START("Start", StartSign::class.java, arrayOf("标识副本开始位置", "无参数 不接受触发器")),
    MOB("Mob", MobSign::class.java, arrayOf("标识怪物刷新点", "参数: [s怪物名] | (i数量:1)", "接受触发器 若无参数 将会自动在玩家进入副本执行")),
    MESSAGE("Message", MessageSign::class.java, arrayOf("向玩家发送消息", "参数: [s信息] 三行信息将会合并", "接受触发器")),
    ACTIONBAR("ActionBar", ActionBarSign::class.java, arrayOf("向玩家发送物品栏上方的小字", "参数: [s信息] 三行信息将会合并", "接受触发器")),
    TELEPORT(":", TeleportSign::class.java, arrayOf("将玩家传送", "参数: (s x y z坐标) 接受触发器", "无参数表示传送到牌子所在位置")),
    COMMAND("Command", CommandSign::class.java, arrayOf("执行命令", "参数: [s命令]多行将会合并 换行不代表空格", "%p为玩家名字变量 命令为后台执行")),
    COMPLETE("Complete", CompleteSign::class.java, arrayOf("触发本牌子后完成副本", "无参数 接受触发器"));

    init {
        ConfigurationSerialization.registerClass(clazz)
        println("注册$type")
    }

    fun <T : LDSign> newInstance(loc: Location): T {
        val cons = this.clazz.getConstructor(Location::class.java)
        return cons.newInstance(loc) as T
    }

    companion object {
        fun getSignType(type: String): SignType? {
            for (t in SignType.values()) {
                if (t.type.equals(type, ignoreCase = true))
                    return t
            }
            return null
        }
    }
}

