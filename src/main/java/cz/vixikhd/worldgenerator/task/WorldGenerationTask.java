package cz.vixikhd.worldgenerator.task;

import cz.vixikhd.worldgenerator.WorldGenerator;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.ZipParameters;
import net.minecraft.server.v1_8_R3.CustomWorldSettingsFinal;
import net.minecraft.server.v1_8_R3.IChunkProvider;
import net.minecraft.server.v1_8_R3.WorldServer;
import org.apache.commons.io.FileUtils;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.generator.BlockPopulator;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WorldGenerationTask implements Runnable {

    public static final int SQUARE_SIZE = 20; // 50
    public static final int CHUNKS_PER_TASK = 50; // 50
    public static final int CHUNK_COUNT = (1 + (SQUARE_SIZE * 2)) * (1 + (SQUARE_SIZE * 2));

    private static final int CHUNK_MAP_SIZE = CHUNK_COUNT - 1;

    private final WorldGenerator plugin;
    private final String name;

    private final long startTime = System.currentTimeMillis();

    private World world;

    private final Map<Integer, Long> hashMap = new HashMap<>();
    private int currentPosition = 0;

    private final List<Long> generated = new ArrayList<>();
    private final List<Long> loaded = new ArrayList<>();

    public WorldGenerationTask(WorldGenerator plugin, String name) {
        this.plugin = plugin;
        this.name = name;

        this.startGenerating();
    }

    private void startGenerating() {
        this.plugin.generating = true;

        CustomWorldSettingsFinal.CustomWorldSettings settings = new CustomWorldSettingsFinal.CustomWorldSettings();
        // Iron cluster count (orig. 20)
        settings.ai = 25;
        // Diamond cluster count (orig. 1)
        settings.au = 5;
        // Gold cluster count (orig.2)
        settings.am = 8;
        // Lapis cluster count (orig. 1)
        settings.ay = 8;
        // Redstone cluster count (orig. 8)
        settings.aq = 10;

        // Diamond max height (orig. 16)
        settings.aw = 40;
        // Gold max height (orig. 32)
        settings.ao = 50;
        // Redstone max height (orig. 16)
        settings.as = 50;

        // Lapis center height(orig. 16)
        settings.az = 25;
        // Lapis spred (orig. 16)
        settings.aA = 25;

        this.world = this.plugin.getServer().createWorld(WorldCreator.name(this.name).generatorSettings(""));

        int i = 0;
        for(int x = -SQUARE_SIZE; x <= SQUARE_SIZE; x++) {
            for(int z = -SQUARE_SIZE; z <= SQUARE_SIZE; z++) {
                this.hashMap.put(i++, this.chunkHash(x, z));
            }
        }
    }

    @Override
    public void run() {
        ArrayList<Long> unloadQueue = new ArrayList<>(this.loaded);
        for (int i = 0; i < CHUNKS_PER_TASK; i++) {
            if(!this.hashMap.containsKey(this.currentPosition)) {
                System.out.println("Position for next chunk is not cached. " + this.currentPosition + "/" + CHUNK_MAP_SIZE + " Breaking cycle");
                break;
            }

            long mainChunkHash = this.hashMap.get(this.currentPosition++);
            int x = this.getHashX(mainChunkHash);
            int z = this.getHashZ(mainChunkHash);

            for(int xx = -1; xx <= 1; xx++) {
                for(int zz = -1; zz <= 1; zz++) {
                    if(this.isBehindBorder(x + xx, z + zz)) {
                        continue;
                    }

                    long hash = this.chunkHash(x + xx, z + zz);
                    if(this.loaded.contains(hash)) {
                        continue;
                    }

                    // We are using the chunk, so we won't unload it
                    unloadQueue.remove(hash);

                    // Adding to generated queue (Maybe useless)
                    if(!this.generated.contains(hash)) {
                        this.generated.add(hash);
                    }

                    // Chunk will be loaded after generating
                    this.loaded.add(hash);
                    this.generateChunk(x + xx, z + zz); // Used for generating & loading together
                }
            }

            this.populateChunk(x, z);

            int percentage = (this.currentPosition * 100) / CHUNK_COUNT;
            System.out.println("Generated chunk at " + x + ":" + z + "; Done="+percentage+"%");

            if(this.currentPosition >= CHUNK_MAP_SIZE) {
                break;
            }
        }

        // Unloading chunks is much slower than generating and populating!
        for(Long hash : unloadQueue) {
            this.loaded.remove(hash);
            this.world.unloadChunk(this.getHashX(hash), this.getHashZ(hash));
        }

        if(this.currentPosition < CHUNK_MAP_SIZE) {
            System.out.println("Returning as we didn't reach chunk map size.");
            return;
        }

        float time = ((float)(System.currentTimeMillis() - this.startTime)) / 1000;
        double speed = Math.round(((double) CHUNK_COUNT / time) * 100d) / 100d;
        double speedTicks = speed / 20;

        System.out.println("World generation finished!");
        System.out.println("Finished in " + time + " seconds ("+speed+" chunks per second; "+speedTicks+" chunks per tick)!");

        this.world.save();
        this.plugin.getServer().unloadWorld(this.world, true);

        File file = new File(this.name);

        System.out.println("Converting the world...");
        this.convertWorld(this.name);
        System.out.println("World converted!");

        File zip = new File("maps/" + this.name + ".zip");

        try {
            ZipFile zipFile = new ZipFile(zip);
            zipFile.addFolder(file, new ZipParameters());
        } catch (ZipException e) {
            e.printStackTrace();
        }

        try {
            FileUtils.deleteDirectory(file);
        } catch (IOException e) {
            e.printStackTrace();
        }

        this.plugin.getServer().getScheduler().cancelTask(this.plugin.taskIds.remove(this));

        this.plugin.generating = false;

        this.plugin.checkForNextGeneration();
    }

    private void convertWorld(String name) {
        try {
            // Test (Converting world)
            Process process = Runtime.getRuntime().exec("./convertor/bin/php7/bin/php " + new File("convertor/WorldConvertor.phar").getAbsolutePath() + " --path " + new File(name).getAbsolutePath());
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            // Read the output from the command
            System.out.println("Here is the standard output of the command:\n");
            String s;
            while ((s = reader.readLine()) != null) {
                System.out.println(s);
            }

            // Read any errors from the attempted command
            System.out.println("Here is the standard error of the command (if any):\n");
            while ((s = errorReader.readLine()) != null) {
                System.out.println(s);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean isBehindBorder(int x, int z) {
        return Math.abs(x) > SQUARE_SIZE || Math.abs(z) > SQUARE_SIZE;
    }

    private void generateChunk(int x, int z) {
        this.world.getChunkAt(x, z).load();
    }

    private void populateChunk(int x, int z) {
        try {
            // Loading chunks around

            CraftWorld craftWorld = (CraftWorld)this.world;

            Field worldField = craftWorld.getClass().getDeclaredField("world");
            worldField.setAccessible(true);
            WorldServer world = (WorldServer) worldField.get(craftWorld);

            Field providerField = world.getClass().getSuperclass().getDeclaredField("chunkProvider");
            providerField.setAccessible(true);
            IChunkProvider provider = (IChunkProvider) providerField.get(world);

            // why is populate() method called getChunkAt() ??
            provider.getChunkAt(provider, x, z);
        }
        catch (NoSuchFieldException | IllegalAccessException exception) {
            exception.printStackTrace();
        }
    }

    private long chunkHash(int x, int z) {
        return (((long)x) << 32) | (z & 0xffffffffL);
    }

    private int getHashX(long hash) {
        return (int) (hash >> 32);
    }

    private int getHashZ(long hash) {
        return (int) hash;
    }
}
