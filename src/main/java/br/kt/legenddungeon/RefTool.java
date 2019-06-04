package br.kt.legenddungeon;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class RefTool {
    public static void setKiller(LivingEntity e, Player killer) {
        try {
            Method m_entityliving = e.getClass().getMethod("getHandle");
            Object o_entityliving = m_entityliving.invoke(e);
            Field f_killer = o_entityliving.getClass().getField("killer");
            Method m_entityhuman = killer.getClass().getMethod("getHandle");
            Object o_entityhuman = m_entityhuman.invoke(killer);
            f_killer.set(o_entityliving, o_entityhuman);
        } catch (NoSuchMethodException ex) {
            ex.printStackTrace();
        } catch (IllegalAccessException ex) {
            ex.printStackTrace();
        } catch (InvocationTargetException ex) {
            ex.printStackTrace();
        } catch (NoSuchFieldException ex) {
            ex.printStackTrace();
        }
    }
}
