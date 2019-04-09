package br.kt.legenddungeon;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ListIterator;

import Br.API.Utils;

public class Setting {

    public static String Message_Prefix;
    public static int Team_Max_Members = 4;
    private static final String RESPAWN_COIN_KEY_LORE = "§1§2§3§3§3§r";

    public static boolean isRespawnCoin(ItemStack is) {
        if (is == null) {
            return false;
        }
        if (!is.hasItemMeta()) {
            return false;
        }
        ItemMeta im = is.getItemMeta();
        if (!im.hasLore()) {
            return false;
        }
        return im.getLore().stream().anyMatch(s -> s.contains(RESPAWN_COIN_KEY_LORE));
    }

    public static boolean hasRespawnCoinAndRemove(Player p) {
        PlayerInventory inv = p.getInventory();
        ListIterator<ItemStack> it = inv.iterator();
        while (it.hasNext()) {
            ItemStack is = it.next();
            if (isRespawnCoin(is)) {
                is = is.clone();
                it.remove();
                if (is.getAmount() > 1) {
                    is.setAmount(is.getAmount() - 1);
                    Utils.safeGiveItem(p, is);
                }
                return true;
            }
        }
        return false;
    }
}
