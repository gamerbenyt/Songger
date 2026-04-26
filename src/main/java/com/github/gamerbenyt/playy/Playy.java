package com.github.gamerbenyt.playy;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;

import java.nio.file.Files;
import java.nio.file.Path;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class Playy implements ModInitializer {
	public static final MinecraftClient MC = MinecraftClient.getInstance();
	public static final int NOTEBLOCK_BASE_ID = Block.getRawIdFromState(Blocks.NOTE_BLOCK.getDefaultState())-1;

	public static final Path SONG_DIR = Path.of("songs");
	public static final Path Playy_DIR = Path.of("Playy");
	public static final Path PLAYLISTS_DIR = Path.of("Playy/playlists");

	public static List<String> pendingRequests = new ArrayList<>();
	public static HttpServer server;

	@Override
	public void onInitialize() {
		if (!Files.exists(SONG_DIR)) {
			Util.createDirectoriesSilently(SONG_DIR);
		}
		if (!Files.exists(Playy_DIR)) {
			Util.createDirectoriesSilently(Playy_DIR);
		}
		if (!Files.exists(PLAYLISTS_DIR)) {
			Util.createDirectoriesSilently(PLAYLISTS_DIR);
		}

		CommandProcessor.initCommands();

		// Start web server
		try {
			server = HttpServer.create(new InetSocketAddress(8080), 0);
			server.createContext("/", new StaticHandler());
			server.createContext("/request", new RequestHandler());
			server.createContext("/queue", new QueueHandler());
			server.setExecutor(Executors.newCachedThreadPool());
			server.start();
			System.out.println("Song request web server started on port 8080");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	static class StaticHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			String html = "<html><head><title>Song Request</title></head><body><h1>Song Request Queue</h1><div id='queue'></div><form action='/request' method='post'><input name='song' placeholder='Song name' required><button>Request Song</button></form><script>function updateQueue(){fetch('/queue').then(r=>r.json()).then(q=>{document.getElementById('queue').innerHTML='<h2>Pending Requests:</h2>' + q.map((s,i)=>'<div>'+(i+1)+'. '+s+'</div>').join('');});} setInterval(updateQueue,2000); updateQueue();</script></body></html>";
			exchange.sendResponseHeaders(200, html.length());
			OutputStream os = exchange.getResponseBody();
			os.write(html.getBytes());
			os.close();
		}
	}

	static class RequestHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			if ("POST".equals(exchange.getRequestMethod())) {
				InputStream is = exchange.getRequestBody();
				String body = new String(is.readAllBytes());
				String song = URLDecoder.decode(body.split("=")[1], "UTF-8");
				pendingRequests.add(song);
				if (MC.player != null) {
					MC.player.sendMessage(net.minecraft.text.Text.of("Song request: " + song + " (" + pendingRequests.size() + " in queue). Type /approve to approve or /deny to deny."), false);
				}
				exchange.sendResponseHeaders(200, 0);
			} else {
				exchange.sendResponseHeaders(405, 0);
			}
			exchange.getResponseBody().close();
		}
	}

	static class QueueHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			String json = "[" + pendingRequests.stream().map(s -> "\"" + s.replace("\"", "\\\"") + "\"").collect(Collectors.joining(",")) + "]";
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, json.length());
			OutputStream os = exchange.getResponseBody();
			os.write(json.getBytes());
			os.close();
		}
	}
}
