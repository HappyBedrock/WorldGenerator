package cz.vixikhd.worldgenerator;

import net.minecraft.server.v1_8_R3.BiomeBase;
import org.apache.commons.io.FileUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

public class WorldGenerator extends JavaPlugin implements CommandExecutor {

    private static WorldGenerator instance;

    public Map<WorldGenerationTask, Integer> taskIds = new HashMap<>();

    @Override
    public void onEnable() {
        WorldGenerator.instance = this;

        this.getCommand("gen").setExecutor(this);

        try {
            Field biomesField = BiomeBase.class.getDeclaredField("biomes");
            biomesField.setAccessible(true);

            Field modifiers = biomesField.getClass().getDeclaredField("modifiers");
            modifiers.setAccessible(true);
            modifiers.setInt(biomesField, biomesField.getModifiers() & ~Modifier.FINAL);

            if (biomesField.get(null) instanceof BiomeBase[]) {
                BiomeBase[] biomes = (BiomeBase[]) biomesField.get(null);
                biomes[BiomeBase.DEEP_OCEAN.id] = BiomeBase.ROOFED_FOREST;
                biomes[BiomeBase.OCEAN.id] = BiomeBase.FOREST;

                biomesField.set(null, biomes);
            }
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(args.length < 1) {
            return false;
        }

        this.generateWorld(args[0]);
        return true;
    }

    private void generateWorld(String name) {
        // Unload world if exists
        if(this.getServer().getWorld(name) != null) {
            // I hope that bool means force
            this.getServer().unloadWorld(name, true);
        }

        // Delete world if exists
        if(name != null) {
            try {
                FileUtils.deleteDirectory(new File(this.getServer().getWorldContainer().getAbsolutePath() + "/" + name));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        WorldGenerationTask task = new WorldGenerationTask(this, name);
        this.taskIds.put(task, this.getServer().getScheduler().scheduleSyncRepeatingTask(this, task, 5, 2));
    }

    public static WorldGenerator getInstance() {
        return WorldGenerator.instance;
    }
}
