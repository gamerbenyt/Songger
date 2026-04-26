package com.github.gamerbenyt.playy;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;

import java.nio.charset.StandardCharsets;
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
	public static boolean webEnabled = true;
	public static String publicIP = "unknown";
	public static List<String> localIPs = new ArrayList<>();

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
		startWebServer();
	}

	public static void startWebServer() {
		if (!webEnabled) {
			System.out.println("Playy web server is disabled.");
			return;
		}

		if (server != null) {
			System.out.println("Playy web server already running.");
			return;
		}

		try {
			localIPs = getLocalIPs();
			publicIP = getPublicIP();

			server = HttpServer.create(new InetSocketAddress(8081), 0);
			server.createContext("/", new StaticHandler());
			server.createContext("/request", new RequestHandler());
			server.createContext("/queue", new QueueHandler());
			server.createContext("/songs", new SongsHandler());
			server.createContext("/status", new StatusHandler());
			server.setExecutor(Executors.newCachedThreadPool());
			server.start();
			System.out.println("Song request web server started on port 8081");

			if (isWindows()) {
				try {
					enableWindowsPortForwarding();
					System.out.println("Windows port forwarding/firewall rule applied.");
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void stopWebServer() {
		if (server != null) {
			server.stop(0);
			server = null;
			System.out.println("Song request web server stopped.");
		}
	}

	private static boolean isWindows() {
		return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows");
	}

	private static void enableWindowsPortForwarding() throws IOException, InterruptedException {
		ProcessBuilder firewall = new ProcessBuilder("cmd.exe", "/c",
			"netsh", "advfirewall", "firewall", "add", "rule",
			"name=Playy Web", "dir=in", "action=allow", "protocol=TCP", "localport=8081");
		firewall.redirectErrorStream(true);
		Process fwProc = firewall.start();
		readProcessOutput(fwProc);
		fwProc.waitFor(10, TimeUnit.SECONDS);

		ProcessBuilder portproxy = new ProcessBuilder("cmd.exe", "/c",
			"netsh", "interface", "portproxy", "add", "v4tov4",
			"listenport=8081", "listenaddress=0.0.0.0",
			"connectport=8081", "connectaddress=127.0.0.1");
		portproxy.redirectErrorStream(true);
		Process proxyProc = portproxy.start();
		readProcessOutput(proxyProc);
		proxyProc.waitFor(10, TimeUnit.SECONDS);
	}

	private static void readProcessOutput(Process process) throws IOException {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			String line;
			while ((line = reader.readLine()) != null) {
				System.out.println(line);
			}
		}
	}

	private static List<String> getLocalIPs() {
		List<String> ips = new ArrayList<>();
		try {
			Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
			while (ifaces.hasMoreElements()) {
				NetworkInterface ni = ifaces.nextElement();
				if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
				Enumeration<InetAddress> addresses = ni.getInetAddresses();
				while (addresses.hasMoreElements()) {
					InetAddress addr = addresses.nextElement();
					if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
						ips.add(addr.getHostAddress());
					}
				}
			}
		} catch (Exception ignored) {}
		return ips;
	}

	private static String getPublicIP() {
		try {
			URL url = new URL("https://api.ipify.org");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setConnectTimeout(3000);
			conn.setReadTimeout(3000);
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
				return reader.readLine();
			}
		} catch (Exception e) {
			return "unknown";
		}
	}

	private static List<String> listSongFiles() {
		try {
			return Files.list(SONG_DIR)
				.filter(path -> !Files.isDirectory(path))
				.map(path -> path.getFileName().toString())
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.collect(Collectors.toList());
		} catch (IOException e) {
			return Collections.emptyList();
		}
	}

	private static String escapeHtml(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
	}

	private static String quoteForCommand(String value) {
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	static class StaticHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			String html = """
				<html>
				<head>
					<meta charset='utf-8'>
					<title>Playy Song Queue</title>
					<style>
						body{margin:0;font-family:Segoe UI,Arial,sans-serif;background:#121212;color:#f5f5f5;}
						header{background:#20232a;padding:24px 32px;color:#fff;}
						h1{margin:0;font-size:2rem;}
						h2{margin-top:0;}
						main{padding:24px;max-width:1000px;margin:auto;}
						section{background:#1d1f26;border:1px solid #333;border-radius:16px;padding:20px;margin-bottom:20px;box-shadow:0 8px 24px rgba(0,0,0,.35);}
						label{display:block;margin-bottom:12px;font-weight:600;}
						select,input,button{width:100%;padding:12px;border-radius:10px;border:1px solid #444;background:#15171e;color:#f5f5f5;font-size:1rem;}
						button{cursor:pointer;background:#61dafb;color:#111;border:none;transition:.2s;}
						button:hover{background:#52c7e7;}
						table{width:100%;border-collapse:collapse;margin-top:12px;}
						th,td{padding:12px;text-align:left;border-bottom:1px solid #333;}
						th{color:#ccc;}
						@media(max-width:720px){body{padding:0;}section{border-radius:0;}}
					</style>
				</head>
				<body>
					<header>
						<h1>Playy Song Queue</h1>
						<p>Request songs from the player’s library and approve them from Minecraft.</p>
					</header>
					<main>
						<section id='status'>
							<h2>Server Status</h2>
							<div id='statusContent'>Loading status...</div>
						</section>
						<section>
							<h2>Request a Song</h2>
							<form id='requestForm'>
								<label for='song'>Choose a song</label>
								<select id='song' name='song'></select>
								<button type='submit'>Request Song</button>
							</form>
							<div id='requestMessage' style='margin-top:12px;color:#8bd18b;'></div>
						</section>
						<section>
							<h2>Pending Requests</h2>
							<div id='queue'>Loading...</div>
						</section>
					</main>
					<script>
					const statusEl=document.getElementById('statusContent');
					const queueEl=document.getElementById('queue');
					const songSelect=document.getElementById('song');
					const requestMessage=document.getElementById('requestMessage');
					function loadStatus(){
						fetch('/status').then(r=>r.json()).then(data=>{
							statusEl.innerHTML=`<p><strong>Enabled:</strong> ${data.enabled}</p><p><strong>Local IPs:</strong> ${data.localIPs.length?data.localIPs.join(', '):'none found'}</p><p><strong>Public IP:</strong> ${data.publicIP}</p><p>Use <code>${data.commandPrefix}queueweb enable</code> or <code>${data.commandPrefix}queueweb disable</code> in-game.</p>`;
						}).catch(()=>{statusEl.innerText='Unable to reach status endpoint.';});
					}
					function loadSongs(){
						fetch('/songs').then(r=>r.json()).then(files=>{
							songSelect.innerHTML='';
							if(files.length===0){
								songSelect.innerHTML='<option value="">No songs found in songs/</option>';
							}else{
								files.forEach(file=>{
									const opt=document.createElement('option');
									opt.value=file.name;
									opt.textContent=file.name+' ('+file.size+')';
									songSelect.appendChild(opt);
								});
							}
						}).catch(()=>{songSelect.innerHTML='<option value="">Unable to load songs</option>';});
					}
					function loadQueue(){
						fetch('/queue').then(r=>r.json()).then(queue=>{
							if(queue.length===0){
								queueEl.innerHTML='<p>No pending requests.</p>';
							}else{
								queueEl.innerHTML='<table><thead><tr><th>#</th><th>Song</th></tr></thead><tbody>'+queue.map((song,i)=>`<tr><td>${i+1}</td><td>${song}</td></tr>`).join('')+'</tbody></table>';
							}
						}).catch(()=>{queueEl.innerText='Unable to load queue.';});
					}
					function submitRequest(e){
						e.preventDefault();
						const song=songSelect.value;
						if(!song){
							requestMessage.innerText='Choose a song first.';
							return;
						}
						fetch('/request',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'song='+encodeURIComponent(song)}).then(()=>{
							requestMessage.innerText='Request sent!';
							loadQueue();
							setTimeout(()=>requestMessage.innerText='',4000);
						}).catch(()=>{requestMessage.innerText='Failed to send request.';});
					}
					document.getElementById('requestForm').addEventListener('submit',submitRequest);
					loadStatus();
					loadSongs();
					loadQueue();
					setInterval(loadQueue,3000);
					</script>
				</body>
				</html>
			""";
			exchange.sendResponseHeaders(200, html.getBytes().length);
			OutputStream os = exchange.getResponseBody();
			os.write(html.getBytes());
			os.close();
		}
	}

	static class RequestHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			if ("POST".equals(exchange.getRequestMethod())) {
				String body = new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
				Map<String, String> params = Arrays.stream(body.split("&"))
					.map(pair -> pair.split("=", 2))
					.filter(parts -> parts.length == 2)
					.collect(Collectors.toMap(parts -> URLDecoder.decode(parts[0], StandardCharsets.UTF_8), parts -> URLDecoder.decode(parts[1], StandardCharsets.UTF_8)));
				String song = params.getOrDefault("song", "");
				if (song.isBlank()) {
					exchange.sendResponseHeaders(400, 0);
					exchange.getResponseBody().close();
					return;
				}
				pendingRequests.add(song);
				if (MC.player != null) {
					MC.player.sendMessage(net.minecraft.text.Text.of("Song request: " + song + " (" + pendingRequests.size() + " in queue). Type " + Config.getConfig().prefix + "approve to approve or " + Config.getConfig().prefix + "deny to deny."), false);
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
			exchange.sendResponseHeaders(200, json.getBytes().length);
			OutputStream os = exchange.getResponseBody();
			os.write(json.getBytes());
			os.close();
		}
	}

	static class SongsHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			List<String> files = listSongFiles();
			String json = files.stream().map(filename -> {
				Path path = SONG_DIR.resolve(filename);
				String size = "unknown";
				try {
					long bytes = Files.size(path);
					if (bytes < 1024) size = bytes + " B";
					else if (bytes < 1048576) size = String.format("%.1f KB", bytes / 1024.0);
					else size = String.format("%.1f MB", bytes / 1048576.0);
				} catch (IOException ignored) {}
				return "{\"name\":\"" + filename.replace("\"", "\\\"") + "\",\"size\":\"" + size + "\"}";
			}).collect(Collectors.joining(",", "[", "]"));
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, json.getBytes().length);
			OutputStream os = exchange.getResponseBody();
			os.write(json.getBytes());
			os.close();
		}
	}

	static class StatusHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			String local = localIPs.stream().map(ip -> "\"" + ip + "\"").collect(Collectors.joining(",", "[", "]"));
			String json = "{\"enabled\":" + (server != null) + ",\"localIPs\":" + local + ",\"publicIP\":\"" + publicIP.replace("\"", "\\\"") + "\",\"commandPrefix\":\"" + Config.getConfig().prefix.replace("\"", "\\\"") + "\"}";
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, json.getBytes().length);
			OutputStream os = exchange.getResponseBody();
			os.write(json.getBytes());
			os.close();
		}
	}
}
