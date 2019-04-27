package br.kt.legenddungeon.trigger

import Br.API.Data.BrConfigurationSerializable
import br.kt.legenddungeon.Game
import org.bukkit.Location
import org.bukkit.configuration.serialization.ConfigurationSerialization

abstract class Trigger : BrConfigurationSerializable {
    val type: TriggerType

    constructor(type: TriggerType) {
        this.type = type
    }

    abstract fun create(game: Game): InGameTrigger

    abstract fun arguments(args: Array<String>): Boolean

}

abstract class InGameTrigger {

    abstract fun check(loc: Location): Boolean
    open fun close() {}
}

enum class TriggerType(val type: String, val clazz: Class<out Trigger>, val des: Array<String>) {
    TIMES("Times", TimesTrigger::class.java, arrayOf("触发器可触发的次数 不设定默认1次", "参数: [i次数]")),//触发器可触发的次数 不使用这个触发器默认1次
    NEAR("Near", NearTrigger::class.java, arrayOf("玩家靠近触发", "参数: [d半径] (i玩家数量:1)")),//玩家靠近触发 参数半径
    KILL("Kill", KillTrigger::class.java, arrayOf("玩家击杀怪物触发", "参数: [s怪物名] (i数量:1)")),//玩家击杀触发 参数怪物与数量
    DEATH("Death", DeathTrigger::class.java, arrayOf("玩家死亡时触发", "参数: (i死亡参数:1)")),//玩家死亡触发
    KILLALL("KillAll", KillAllTrigger::class.java, arrayOf("怪物全部死亡时触发", "无需参数"));

    init {
        ConfigurationSerialization.registerClass(clazz)
        println("注册$type")
    }

    companion object {
        fun getTriggerType(str: String): TriggerType? {
            for (t in TriggerType.values()) {
                if (t.type.equals(str, true)) {
                    return t
                }
            }
            return null
        }
    }

    fun <T : Trigger> newInstance(): T {
        return this.clazz.newInstance() as T
    }
}