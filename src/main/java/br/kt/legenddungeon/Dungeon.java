package br.kt.legenddungeon;

import net.md_5.bungee.api.chat.BaseComponent;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import Br.API.Data.BrConfigurationSerializable;
import br.kt.legenddungeon.sign.LDSign;
import br.kt.legenddungeon.world.WorldManager;

public class Dungeon implements BrConfigurationSerializable {
    public static final int MAX_DUNGEON_AMOUNT = 20;

    @Config
    private String name;
    @Config
    private List<LDSign> signs = new ArrayList<>();
    @Config
    private boolean enable = false;
    @Config
    private int MaxDeath = 5;
    @Config
    private int TimeLimit = 15;

    public Map<UUID, Game> games = new HashMap<>();

    private Set<Integer> creating = new HashSet<>();

    public int newId() {
        if (games.size() >= MAX_DUNGEON_AMOUNT) {
            return -1;
        }
        int maxid = 0;
        for (Game game : games.values()) {
            if (game.getId() > maxid) {
                maxid = game.getId();
            }
        }
        while (creating.contains(maxid + 1)) {
            maxid++;
        }
        return maxid + 1;
    }

    public Dungeon(String name) {
        this.name = name;
        init();
    }

    public Dungeon(Map<String, Object> args) {
        BrConfigurationSerializable.deserialize(args, this);
        init();
    }

    public void init() {
        if (TimeLimit <= 0) {
            TimeLimit = 15;
        }
        World w = getBaseWorld();
        if (w == null) {
            w = WorldManager.createFlatWorld(this.getBaseWorldName());
        }
    }

    public String createGame(Team team, LootRule rule) {
        int id = newId();
        if (id == -1) {
            return "创建失败 副本已满";
        }
        World baseWorld = getBaseWorld();
        //baseWorld.save();
        creating.add(id);
        Bukkit.getScheduler().runTaskLaterAsynchronously(Main.Companion.getMain(), () -> {
            File old = new File(Bukkit.getWorldContainer(), baseWorld.getName());
            File tar = new File(Bukkit.getWorldContainer(), String.format("LD_Game_%s_%d", name, id));
            try {
                copyDir(old.getAbsolutePath(), tar.getAbsolutePath());
                for (File f : tar.listFiles()) {
                    if (f.getName().contains("session.lock")) {
                        f.delete();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                delete(new File(Bukkit.getWorldContainer(), String.format("LD_Game_%s_%d", name, id)));
                this.creating.remove(id);
                team.broadcast("§c副本创建失败");
                return;
            }
            Bukkit.getScheduler().runTaskLater(Main.Companion.getMain(), () -> {
                if (team.isDisband()) {
                    delete(new File(Bukkit.getWorldContainer(), String.format("LD_Game_%s_%d", name, id)));
                    this.creating.remove(id);
                    return;
                }
                World gw = WorldManager.createFlatWorld(String.format("LD_Game_%s_%d", name, id));
                for (LDSign sign : this.signs) {
                    Location loc = sign.getLocation().clone();
                    loc.setWorld(gw);
                    loc.getBlock().setType(Material.AIR);
                }
                gw.setAutoSave(false);
                gw.setGameRuleValue("doMobSpawning", "false");
                this.creating.remove(id);
                Game game = new Game(gw, id, this, team, rule);
                games.put(game.getUuid(), game);
                game.start();
            }, 20);
        }, 20);
        team.setInGame(true);
        return "正在创建游戏";
    }

    public int getTimeLimit() {
        return TimeLimit;
    }

    public void setTimeLimit(int timeLimit) {
        TimeLimit = timeLimit;
    }

    public static void copyDir(String sourcePath, String newPath) throws IOException {
        File file = new File(sourcePath);
        String[] filePath = file.list();

        if (!(new File(newPath)).exists()) {
            (new File(newPath)).mkdir();
        }
        for (int i = 0; i < filePath.length; i++) {
            if ((new File(sourcePath + file.separator + filePath[i])).isDirectory()) {
                copyDir(sourcePath + file.separator + filePath[i], newPath + file.separator + filePath[i]);
            }
            if (new File(sourcePath + file.separator + filePath[i]).isFile()) {
                copyFile(sourcePath + file.separator + filePath[i], newPath + file.separator + filePath[i]);
            }

        }
    }

    public static void copyFile(String oldPath, String newPath) throws IOException {
        if (oldPath.contains("uid.dat") || oldPath.contains("session.lock")) {
            return;
        }
        File oldFile = new File(oldPath);
        File file = new File(newPath);
        FileInputStream in = new FileInputStream(oldFile);
        FileOutputStream out = new FileOutputStream(file);
        byte[] buffer = new byte[2097152];
        int readByte = 0;
        while ((readByte = in.read(buffer)) != -1) {
            out.write(buffer, 0, readByte);
        }

        in.close();
        out.close();
    }

    public static void delete(File f) {
        if (f.isDirectory()) {
            for (File d : f.listFiles()) {
                delete(d);
            }
        }
        f.delete();
    }

    public void removeGame(Game g) {
        this.games.remove(g.getUuid());
    }

    public World getBaseWorld() {
        return Bukkit.getWorld(getBaseWorldName());
    }

    public String getBaseWorldName() {
        return String.format("LD_Base_%s", name);
    }

    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
        if (!enable) {
            for (Game game : this.games.values()) {
                game.broadcast("§c该副本已被关闭");
                game.destroy();
            }
            this.games.clear();
        }
    }

    public int getMaxDeath() {
        return MaxDeath;
    }

    public void setMaxDeath(int maxDeath) {
        MaxDeath = maxDeath;
    }

    public List<LDSign> getSigns() {
        return signs;
    }

    public String getName() {
        return name;
    }

    public void delete() {
        for (Player p : this.getBaseWorld().getPlayers()) {
            Location from = DungeonManager.INSTANCE.getWhereFrom().get(p.getName());
            if (from != null) {
                p.teleport(from);
            } else {
                p.kickPlayer("§c副本世界已删除");
            }
        }
        Bukkit.unloadWorld(this.getBaseWorldName(), false);
        delete(new File(Bukkit.getWorldContainer(), this.getBaseWorldName()));
    }

    public static void sendMessage(Player p, BaseComponent[] cb) {
        p.spigot().sendMessage(cb);
    }
}
