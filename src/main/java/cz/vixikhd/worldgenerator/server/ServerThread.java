package cz.vixikhd.worldgenerator.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import cz.vixikhd.worldgenerator.WorldGenerator;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;

public class ServerThread extends Thread {

    public WorldGenerator plugin;
    public int port;

    public ServerThread(WorldGenerator plugin, int port) {
        this.plugin = plugin;
        this.port = port;
    }

    @Override
    public void run() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(this.port), 0);
            server.createContext("/", new DownloadHandler(this));
            server.createContext("/maps", new MapsCountHandler());
            server.setExecutor(null);
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class DownloadHandler implements HttpHandler {

        public ServerThread thread;

        public DownloadHandler(ServerThread thread) {
            this.thread = thread;
        }

        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            File[] maps = new File("maps").listFiles();
            if(maps == null || maps.length == 0) {
                httpExchange.sendResponseHeaders(200, 0);

                OutputStream stream = httpExchange.getResponseBody();
                stream.write(new byte[]{});
                stream.close();
                return;
            }

            File file = maps[0];

            httpExchange.sendResponseHeaders(200, file.length());

            OutputStream stream = httpExchange.getResponseBody();
            stream.write(Files.readAllBytes(file.toPath()));
            stream.close();

            file.delete();
        }
    }

    static class MapsCountHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            String response = String.valueOf(Objects.requireNonNull(new File("maps").listFiles()).length);

            httpExchange.sendResponseHeaders(200, response.length());

            OutputStream stream = httpExchange.getResponseBody();
            stream.write(response.getBytes(StandardCharsets.UTF_8));
            stream.close();
        }
    }
}
