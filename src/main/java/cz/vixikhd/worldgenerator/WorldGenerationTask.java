package cz.vixikhd.worldgenerator;

import net.minecraft.server.v1_8_R3.IChunkProvider;
import net.minecraft.server.v1_8_R3.WorldServer;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WorldGenerationTask implements Runnable {

    public static final int SQUARE_SIZE = 50;
    public static final int CHUNKS_PER_TASK = 50;
    public static final int CHUNK_COUNT = (1 + (SQUARE_SIZE * 2)) * (1 + (SQUARE_SIZE * 2));

    private static final int CHUNK_MAP_SIZE = CHUNK_COUNT-1;

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
        this.world = this.plugin.getServer().createWorld(WorldCreator.name(this.name));
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

            if(this.currentPosition == CHUNK_MAP_SIZE) {
                break;
            }
        }

        // Unloading chunks is much slower than generating and populating!
        for(Long hash : unloadQueue) {
            this.loaded.remove(hash);
            this.world.unloadChunk(this.getHashX(hash), this.getHashZ(hash));
        }

        if(this.currentPosition != CHUNK_MAP_SIZE) {
            return;
        }

        float time = ((float)(System.currentTimeMillis() - this.startTime)) / 1000;
        double speed = Math.round(((double) CHUNK_COUNT / time) * 100d) / 100d;
        double speedTicks = speed / 20;

        System.out.println("World generation finished!");
        System.out.println("Finished in " + time + " seconds ("+speed+" chunks per second; "+speedTicks+" chunks per tick)!");

        this.world.save();
        this.plugin.getServer().unloadWorld(this.world, true);

        this.plugin.getServer().getScheduler().cancelTask(this.plugin.taskIds.remove(this));
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

            Field worldField = (craftWorld).getClass().getDeclaredField("world");
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
