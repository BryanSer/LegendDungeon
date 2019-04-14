package br.kt.legenddungeon.trigger

import Br.API.Data.BrConfigurationSerializable
import br.kt.legenddungeon.Game
import br.kt.legenddungeon.Main
import io.lumine.xikage.mythicmobs.MythicMobs
import io.lumine.xikage.mythicmobs.mobs.ActiveMob
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.PlayerDeathEvent

class TimesTrigger : Trigger {
    constructor() : super(TriggerType.TIMES)

    override fun create(game: Game): InGameTrigger {
        return object : InGameTrigger() {
            override fun check(loc: Location): Boolean {
                return true
            }
        }
    }

    @BrConfigurationSerializable.Config
    var times: Int = 1
        private set

    override fun arguments(args: Array<String>): Boolean {
        if (args.isEmpty())
            return false
        try {
            times = args[0].toInt()
        } catch (e: NumberFormatException) {
            return false
        }
        return true
    }

    constructor(args: Map<String, Any>) : super(TriggerType.TIMES) {
        BrConfigurationSerializable.deserialize(args, this)
    }
}

class NearTrigger : Trigger {
    constructor() : super(TriggerType.NEAR)

    override fun create(game: Game): InGameTrigger {
        val maxdistance = radius * radius
        return object : InGameTrigger() {
            override fun check(loc: Location): Boolean {
                var found = 0
                for (e in loc.world.getNearbyEntities(loc, radius, radius, radius)) {
                    if (e is Player && loc.distanceSquared(e.location) < maxdistance) {
                        found++
                        if (found >= minPlayer) {
                            return true
                        }
                    }
                }
                return false
            }
        }
    }

    @BrConfigurationSerializable.Config
    var radius: Double = 1.0
        private set
    @BrConfigurationSerializable.Config
    var minPlayer: Int = 1
        private set

    override fun arguments(args: Array<String>): Boolean {
        if (args.isEmpty()) return false
        try {
            radius = args[0].toDouble()
            if (args.size > 1) {
                minPlayer = args[1].toInt()
            }
        } catch (e: NumberFormatException) {
            return false
        }
        return true
    }

    constructor(args: Map<String, Any>) : super(TriggerType.NEAR) {
        BrConfigurationSerializable.deserialize(args, this)
    }
}

class KillTrigger : Trigger {
    constructor() : super(TriggerType.KILL)

    constructor(args: Map<String, Any>) : super(TriggerType.KILL) {
        BrConfigurationSerializable.deserialize(args, this)
    }

    inner class InGameKillTrigger(val game: Game) : InGameTrigger(), Listener {
        private var killed = 0

        init {
            Bukkit.getPluginManager().registerEvents(this, Main.getMain())
        }

        @EventHandler
        fun onKill(evt: EntityDeathEvent) {
            if (evt.entity.world !== game.world) {
                return
            }
            val e: ActiveMob? = MythicMobs.inst().mobManager.getMythicMobInstance(evt.entity)
                    ?: return
            if (e != null) {
                if (e.type.internalName.equals(mobName, true)) {
                    killed++
                }
            }
        }

        override fun check(loc: Location): Boolean {
            if (killed >= amount) {
                killed -= amount
                return true
            }
            return false
        }

        override fun close() {
            HandlerList.unregisterAll(this)
        }
    }

    override fun create(game: Game): InGameTrigger {
        return InGameKillTrigger(game)
    }

    @BrConfigurationSerializable.Config
    var mobName: String? = null
        private set
    @BrConfigurationSerializable.Config
    var amount = 1
        private set


    override fun arguments(args: Array<String>): Boolean {
        if (args.isEmpty()) return false
        mobName = args[0]
        if (args.size > 1) {
            try {
                amount = args[1].toInt()
            } catch (e: NumberFormatException) {
                return false
            }
        }
        return true
    }


}

class DeathTrigger : Trigger {
    constructor() : super(TriggerType.DEATH)

    constructor(args: Map<String, Any>) : super(TriggerType.DEATH) {
        BrConfigurationSerializable.deserialize(args, this)
    }

    @BrConfigurationSerializable.Config
    private var amount = 1

    inner class InGameDeathTrigger(val game: Game) : InGameTrigger(), Listener {

        init {
            Bukkit.getPluginManager().registerEvents(this, Main.getMain())
        }

        var death = 0
        @EventHandler
        fun onDeath(evt: PlayerDeathEvent) {
            if (evt.entity.location.world !== game.world) {
                return
            }
            death++
        }

        override fun check(loc: Location): Boolean {
            if (death >= amount) {
                death -= amount
                return true
            }
            return false
        }

        override fun close() {
            HandlerList.unregisterAll(this)
        }
    }

    override fun arguments(args: Array<String>): Boolean {
        if (args.isEmpty())
            return true
        try {
            amount = args[0].toInt()
        } catch (e: NumberFormatException) {
            return false
        }
        return true
    }

    override fun create(game: Game): InGameTrigger {
        return InGameDeathTrigger(game)
    }

}
