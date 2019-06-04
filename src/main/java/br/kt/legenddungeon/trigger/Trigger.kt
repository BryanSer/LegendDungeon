package br.kt.legenddungeon.trigger

import Br.API.Data.BrConfigurationSerializable
import br.kt.legenddungeon.Game
import org.bukkit.Location
import org.bukkit.configuration.serialization.ConfigurationSerialization

abstract class Trigger(val type: String) : BrConfigurationSerializable {

    abstract fun create(game: Game): InGameTrigger

    abstract fun arguments(args: Array<String>): Boolean

}

abstract class InGameTrigger {

    abstract fun check(loc: Location): Boolean
    open fun close() {}
}

object TriggerManager {
    val registered = mutableListOf<TriggerData>()
    fun register(type: String, clazz: Class<out Trigger>, des: Array<String>) {
        val td = TriggerData(type, clazz, des)
        registered += td
    }

    fun init() {
        register("Times", TimesTrigger::class.java, arrayOf("触发器可触发的次数 不设定默认1次", "参数: [i次数]"))//触发器可触发的次数 不使用这个触发器默认1次
        register("Near", NearTrigger::class.java, arrayOf("玩家靠近触发", "参数: [d半径] (i玩家数量:1)"))//玩家靠近触发 参数半径
        register("Kill", KillTrigger::class.java, arrayOf("玩家击杀怪物触发", "参数: [s怪物名] (i数量:1)"))//玩家击杀触发 参数怪物与数量
        register("Death", DeathTrigger::class.java, arrayOf("玩家死亡时触发", "参数: (i死亡参数:1)"))//玩家死亡触发
        register("KillAll", KillAllTrigger::class.java, arrayOf("怪物全部死亡时触发", "无需参数"))
    }

    fun getTriggerType(str: String): TriggerData? {
        for (t in registered) {
            if (t.type.equals(str, true)) {
                return t
            }
        }
        return null
    }
}

data class TriggerData(
        val type: String,
        val clazz: Class<out Trigger>,
        val des: Array<String>
) {
    init {
        ConfigurationSerialization.registerClass(clazz)
    }

    fun <T : Trigger> newInstance(): T {
        return this.clazz.newInstance() as T
    }
}
