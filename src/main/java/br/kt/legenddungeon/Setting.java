package br.kt.legenddungeon;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class Setting {

    public static String Message_Prefix;
    public static int Team_Max_Members = 4;
    private static final String RESPAWN_COIN_KEY_LORE = "§1§2§3§3§3§r";

    public static boolean isRespawnCoin(ItemStack is) {
        if (!is.hasItemMeta()) {
            return false;
        }
        ItemMeta im = is.getItemMeta();
        if (!im.hasLore()) {
            return false;
        }
        return im.getLore().stream().anyMatch(s -> s.contains(RESPAWN_COIN_KEY_LORE));
    }
}
