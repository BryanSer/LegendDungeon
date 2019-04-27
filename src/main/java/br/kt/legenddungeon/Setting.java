package br.kt.legenddungeon;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Setting {

    public static String Message_Prefix = "[LegendDungeon]";
    public static int Team_Max_Members = 4;
    private static String RESPAWN_COIN_KEY_LORE = "§1§2§3§3§3§r";
    public static List<String> AllowCommand = new ArrayList<>();

    public static void loadConfig() {
        File f = new File(Main.getMain().getDataFolder(), "config.yml");
        if (!f.exists()) {
            Main.getMain().saveDefaultConfig();
        }
        AllowCommand.add("brapi");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(f);
        AllowCommand.addAll(config.getStringList("AllowCommand"));
        RESPAWN_COIN_KEY_LORE = ChatColor.translateAlternateColorCodes('&', config.getString("RespawnCoinLore"));
    }

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
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack is = contents[i];
            if (isRespawnCoin(is)) {
                is = is.clone();

                if (is.getAmount() > 1) {
                    is.setAmount(is.getAmount() - 1);
                    contents[i] = is;
                } else {
                    contents[i] = null;
                }
                inv.setContents(contents);
                return true;
            }
        }
        return false;
    }
}
