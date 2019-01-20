@file:JvmName("WorldManager")

package br.kt.legenddungeon.world

import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.WorldType

typealias WorldCreatorConsumer = (WorldCreator) -> Unit

fun createFlatWorld(name: String): World {
    return createWorld(name) {
        it.generateStructures(false)
        it.type(WorldType.FLAT)
    }
}

fun createWorld(name: String, func: WorldCreatorConsumer): World {
    val wc = WorldCreator.name(name)
    func(wc)
    val w = wc.createWorld()
    w.isAutoSave = true
    return w
}

fun loadWorld(name: String) {
    WorldCreator.name("LD_Base_$name").createWorld()
}
