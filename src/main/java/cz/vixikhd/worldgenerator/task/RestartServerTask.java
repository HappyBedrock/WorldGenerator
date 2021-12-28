package cz.vixikhd.worldgenerator.task;

import cz.vixikhd.worldgenerator.WorldGenerator;

public class RestartServerTask implements Runnable {

    private final WorldGenerator plugin;

    public RestartServerTask(WorldGenerator plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        this.plugin.getServer().shutdown();
    }
}
