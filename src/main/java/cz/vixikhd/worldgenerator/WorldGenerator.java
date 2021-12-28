package cz.vixikhd.worldgenerator;

import cz.vixikhd.worldgenerator.server.ServerThread;
import cz.vixikhd.worldgenerator.task.RestartServerTask;
import cz.vixikhd.worldgenerator.task.WorldGenerationTask;
import net.minecraft.server.v1_8_R3.BiomeBase;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.bukkit.command.CommandExecutor;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

public class WorldGenerator extends JavaPlugin implements CommandExecutor {

    public Map<WorldGenerationTask, Integer> taskIds = new HashMap<>();

    public ServerThread serverThread;

    public boolean generating = false;

    @Override
    public void onEnable() {
        File convertorDirectory = new File("convertor");
        if(!(convertorDirectory.exists())) {
            try {
                // Creating convertor directory
                convertorDirectory.mkdir();

                // Downloading php
                System.setProperty("http.agent", "Chrome");

                File targetPhpFile = new File("convertor/PHP-8.0-Linux-x86_64.tar.gz");

                URLConnection conn = new URL("https://jenkins.pmmp.io/job/PHP-8.0-Aggregate/lastSuccessfulBuild/artifact/PHP-8.0-Linux-x86_64.tar.gz").openConnection();
                conn.addRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.0)");
                FileUtils.copyInputStreamToFile(conn.getInputStream(), targetPhpFile);

//                FileUtils.copyURLToFile(new URL("https://jenkins.pmmp.io/job/PHP-8.0-Aggregate/lastSuccessfulBuild/artifact/PHP-8.0-Linux-x86_64.tar.gz"), targetPhpFile);

                // Extracting zip
//                ZipFile zip = new ZipFile(targetPhpFile);
//                zip.extractAll("convertor");

                // Extracting tar.gz
                FileOutputStream outputStream = new FileOutputStream("setup.sh");
                IOUtils.copy(this.getResource("setup.sh"), outputStream);
                outputStream.close();

                new File("setup.sh").setExecutable(true);

                Process process = Runtime.getRuntime().exec("./setup.sh");
                String s = null;
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                while ((s = bufferedReader.readLine()) != null) {
                    System.out.println(s);
                }

                // Removing php.zip
                targetPhpFile.delete();

                // Removing Linux folder
//                FileUtils.deleteDirectory(new File("convertor/Linux"));

                new File("setup.sh").delete();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        this.getDataFolder().mkdir();
        this.saveResource("config.yml", false);

        try {
            Field biomesField = BiomeBase.class.getDeclaredField("biomes");
            biomesField.setAccessible(true);

            Field modifiers = biomesField.getClass().getDeclaredField("modifiers");
            modifiers.setAccessible(true);
            modifiers.setInt(biomesField, biomesField.getModifiers() & ~Modifier.FINAL);

            if (biomesField.get(null) instanceof BiomeBase[]) {
                BiomeBase[] biomes = (BiomeBase[]) biomesField.get(null);
                biomes[BiomeBase.OCEAN.id] = BiomeBase.FOREST;
                biomes[BiomeBase.EXTREME_HILLS.id] = BiomeBase.PLAINS;
                biomes[BiomeBase.SWAMPLAND.id] = BiomeBase.PLAINS;
                biomes[BiomeBase.FROZEN_OCEAN.id] = BiomeBase.FOREST;
                biomes[BiomeBase.FROZEN_RIVER.id] = BiomeBase.RIVER;
                biomes[BiomeBase.ICE_PLAINS.id] = BiomeBase.PLAINS;
                biomes[BiomeBase.ICE_MOUNTAINS.id] = BiomeBase.FOREST;
                biomes[BiomeBase.BEACH.id] = BiomeBase.PLAINS;
                biomes[BiomeBase.DESERT_HILLS.id] = BiomeBase.FOREST;
                biomes[BiomeBase.SMALL_MOUNTAINS.id] = BiomeBase.FOREST;
                biomes[BiomeBase.JUNGLE.id] = BiomeBase.BIRCH_FOREST;
                biomes[BiomeBase.JUNGLE_HILLS.id] = BiomeBase.SAVANNA;
                biomes[BiomeBase.JUNGLE_EDGE.id] = BiomeBase.SAVANNA;
                biomes[BiomeBase.DEEP_OCEAN.id] = BiomeBase.ROOFED_FOREST;
                biomes[BiomeBase.STONE_BEACH.id] = BiomeBase.PLAINS;
                biomes[BiomeBase.COLD_BEACH.id] = BiomeBase.PLAINS;
                biomes[BiomeBase.MEGA_TAIGA.id] = BiomeBase.PLAINS;
                biomes[BiomeBase.MEGA_TAIGA_HILLS.id] = BiomeBase.TAIGA_HILLS;
                biomes[BiomeBase.MESA.id] = BiomeBase.DESERT;
                biomes[BiomeBase.MESA_PLATEAU.id] = BiomeBase.DESERT;
                biomes[BiomeBase.MESA_PLATEAU_F.id] = BiomeBase.DESERT;
                biomes[BiomeBase.EXTREME_HILLS_PLUS.id] = BiomeBase.DESERT;
                biomes[BiomeBase.TAIGA_HILLS.id] = BiomeBase.PLAINS;
                biomes[BiomeBase.SAVANNA_PLATEAU.id] = BiomeBase.SAVANNA;

                biomesField.set(null, biomes);
            }
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
            return;
        }

        if(new File("maps").mkdir()) {
            this.getLogger().info("Created /maps/ folder for pre-generated worlds.");
        }

        int port = this.getConfig().getInt("http-server-port");
        this.getLogger().info("Opening http server on *:" + port + "!");

        this.serverThread = new ServerThread(this, port);
        this.serverThread.start();

        this.getServer().getScheduler().scheduleSyncDelayedTask(this, new RestartServerTask(this), 20 * 60 * 60 * 12); // 2 Restarts per day restart

        this.checkForNextGeneration();
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

    public void checkForNextGeneration() {
        if(!this.generating) {
            this.generateWorld("uhc-world_" + (int)Math.floor(System.currentTimeMillis() / 1000F));
        }
    }
}
