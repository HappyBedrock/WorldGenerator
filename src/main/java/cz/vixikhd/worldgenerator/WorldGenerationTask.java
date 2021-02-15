package cz.vixikhd.worldgenerator;

import net.minecraft.server.v1_8_R3.Chunk;
import net.minecraft.server.v1_8_R3.IChunkProvider;
import net.minecraft.server.v1_8_R3.WorldServer;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.craftbukkit.v1_8_R3.CraftChunk;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WorldGenerationTask implements Runnable {

    public static final int SQUARE_SIZE = 50;
    public static final int MAX_CHUNK_COUNT = 30;

    public static final int CHUNK_COUNT = (1 + (SQUARE_SIZE * 2)) * (1 + (SQUARE_SIZE * 2));

    private final WorldGenerator plugin;
    private final String name;

    private final long startTime = System.currentTimeMillis();

    private World world;

    private final List<Long> generating = new ArrayList<>();
    private final List<Long> populating = new ArrayList<>();
    private final List<Long> generated = new ArrayList<>();

    public WorldGenerationTask(WorldGenerator plugin, String name) {
        this.plugin = plugin;
        this.name = name;

        this.startGenerating();
    }

    private void startGenerating() {
        this.world = this.plugin.getServer().createWorld(WorldCreator.name(this.name));
    }

    @Override
    public void run() {
        if(this.generated.size() == CHUNK_COUNT) {
            this.plugin.getServer().getScheduler().cancelTask(this.plugin.taskIds.get(this));

            this.world.save();
            this.plugin.getServer().unloadWorld(this.world, true);

            double time = Math.round(System.currentTimeMillis() - this.startTime) / 1000d;

            double speed = Math.round(((double) CHUNK_COUNT / time) * 100d) / 100d;
            double speedTicks = speed / 20;

            this.plugin.getLogger().info("World generation finished!");
            this.plugin.getLogger().info("Generation of world " + this.name + " finished in " + time + " seconds (= "+speed+" chunks per second; "+speedTicks+" chunks per tick)!");
            return;
        }

        int i = this.generating.size() + this.populating.size();
        for(int x = -SQUARE_SIZE; x <= SQUARE_SIZE; x++) {
            for(int z = -SQUARE_SIZE; z <= SQUARE_SIZE; z++) {
                long hash = this.chunkHash(x, z);
                if(this.generated.contains(hash)) {
                    continue; // We don't care already generated chunks
                }

                if(this.populating.contains(hash)) {
                    if(this.isChunkPopulated(x, z)) {
                        i--;

                        this.generated.add(hash);
                        this.populating.remove(hash);

                        this.world.getChunkAt(x, z).unload();

                        int percentage = (this.generated.size() * 100) / CHUNK_COUNT;
                        System.out.println("Generated chunk at " + x + ":" + z + " (" + percentage + "% done)");
                    }

                    continue; // We must not generate that chunk again
                }

                if(this.generating.contains(hash)) {
                    if(this.isChunkGenerated(x, z)) {
                        this.populating.add(hash);
                        this.generating.remove(hash);

                        this.populateChunk(x, z);
                    }

                    continue; // We must not generate that chunk again
                }

                if(i == MAX_CHUNK_COUNT) {
                    return; // Server is generating max chunk count, so we should block requesting another chunks
                }

                this.generating.add(hash);
                this.generateChunk(x, z);
                i++;
            }
        }
    }

    private void generateChunk(int x, int z) {
        this.world.getChunkAt(x, z).load();
    }

    private void populateChunk(int x, int z) {
        try {
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

    private boolean isChunkGenerated(int x, int z) {
        return this.world.getChunkAt(x, z).getBlock(1, 1, 1) != null;
    }

    private boolean isChunkPopulated(int x, int z) {
        try {
            CraftChunk chunk = (CraftChunk) this.world.getChunkAt(x, z);
            Field field = chunk.getClass().getDeclaredField("weakChunk");
            field.setAccessible(true);

            Chunk minecraftChunk = ((WeakReference<Chunk>) field.get(chunk)).get();

            if (minecraftChunk != null) {
                if(minecraftChunk.isDone()) {
                    return true;
                }

                return minecraftChunk.isDone(); // isTerrainPopulated
            }
        }
        catch (NoSuchFieldException | IllegalAccessException exception) {
            exception.printStackTrace();
        }

        return false;
    }

    private long chunkHash(int x, int z) {
        return (((long)x) << 32) | (z & 0xffffffffL);
    }
}
