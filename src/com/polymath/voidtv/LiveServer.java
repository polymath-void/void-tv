package com.polymath.voidtv;

import android.content.Context;
import android.webkit.WebView;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LiveServer {
    /** Bridge the HTTP endpoints need from the Activity. */
    public interface Host {
        String state();
        boolean key(String name);
        boolean tap(String label);
        boolean launch(String pkg);
        boolean openUrl(String url);
        boolean switchInput(String input);
        String inputs();
        String apps();
        boolean sendText(String text);
        boolean mouse(float dx, float dy, boolean click, boolean scroll, String action);
        boolean volume(String action);
        String browserConfig();
        boolean updateBrowserConfig(String adblock, String popups, String ua, String cookies);
        boolean clearBrowserData();
        String updateApp(String url);
    }

    private final int port;
    private WebView webView;
    private final Context context;
    private final Host host;
    private final String controlToken;
    private final ExecutorService executor;
    private ServerSocket serverSocket;
    private boolean running = false;

    public void setWebView(WebView webView) {
        this.webView = webView;
    }

    public LiveServer(int port, WebView webView, Context context, Host host, String controlToken) {
        this.port = port;
        this.webView = webView;
        this.context = context;
        this.host = host;
        this.controlToken = controlToken;
        this.executor = Executors.newFixedThreadPool(8, r -> {
            Thread t = new Thread(r, "LiveServer-Worker");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        running = true;
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                while (running) {
                    Socket client = serverSocket.accept();
                    executor.execute(() -> handleClient(client));
                }
            } catch (Exception ignored) {}
        }, "LiveServer-Acceptor").start();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception ignored) {}
        executor.shutdownNow();
    }

    private static final ThreadLocal<Boolean> CURRENT_KEEP_ALIVE = ThreadLocal.withInitial(() -> Boolean.TRUE);

    private void handleClient(Socket client) {
        try {
            client.setTcpNoDelay(true);
            client.setSoTimeout(10000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));

            while (running && !client.isClosed()) {
                String requestLine = reader.readLine();
                if (requestLine == null || requestLine.isEmpty()) {
                    break;
                }

                int contentLength = 0;
                String origin = null;
                String authHeader = null;
                boolean keepAlive = true;
                String header;
                while ((header = reader.readLine()) != null && !header.isEmpty()) {
                    String lower = header.toLowerCase(Locale.ROOT);
                    if (lower.startsWith("content-length:")) {
                        try {
                            contentLength = Integer.parseInt(header.substring(15).trim());
                        } catch (Exception ignored) {}
                    } else if (lower.startsWith("origin:")) {
                        origin = header.substring(7).trim();
                    } else if (lower.startsWith("connection:")) {
                        if (lower.contains("close")) keepAlive = false;
                    } else if (lower.startsWith("x-void-token:")) {
                        authHeader = header.substring(13).trim();
                    } else if (lower.startsWith("authorization:")) {
                        String val = header.substring(14).trim();
                        if (val.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
                            authHeader = val.substring(7).trim();
                        }
                    }
                }

                CURRENT_KEEP_ALIVE.set(keepAlive);

                String body = "";
                if (contentLength > 0) {
                    char[] buf = new char[contentLength];
                    int read = 0;
                    while (read < contentLength) {
                        int r = reader.read(buf, read, contentLength - read);
                        if (r == -1) break;
                        read += r;
                    }
                    body = new String(buf, 0, read);
                }

                route(client, requestLine, body, origin, authHeader);
                if (!keepAlive) break;
            }
        } catch (Exception ignored) {
        } finally {
            CURRENT_KEEP_ALIVE.remove();
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    private boolean isUntrustedOrigin(String origin) {
        if (origin == null || origin.isEmpty() || "null".equals(origin)) {
            return false;
        }
        String lower = origin.toLowerCase(Locale.ROOT);
        // Allow localhost and 127.0.0.1
        if (lower.startsWith("http://127.0.0.1") || lower.startsWith("http://localhost")) {
            return false;
        }
        // Allow same-origin access on our port
        if (lower.contains(":" + port)) {
            return false;
        }
        // External public websites attempting drive-by requests
        return true;
    }

    private void route(Socket client, String requestLine, String body, String origin, String authHeader) throws Exception {
        String[] parts = requestLine.split(" ");
        String method = parts.length > 0 ? parts[0] : "GET";
        String target = parts.length > 1 ? parts[1] : "/";

        String path = target;
        String query = "";
        int q = target.indexOf('?');
        if (q >= 0) {
            path = target.substring(0, q);
            query = target.substring(q + 1);
        }

        // 1. Static Web / PWA Asset Serving
        if ("/".equals(path) || "/index.html".equals(path) || "/remote".equals(path)) {
            if (serveAsset(client, "remote/index.html", "text/html; charset=utf-8", false)) return;
        }
        if ("/manifest.webmanifest".equals(path) || "/manifest.json".equals(path)) {
            if (serveAsset(client, "remote/manifest.webmanifest", "application/manifest+json; charset=utf-8", false)) return;
        }
        if ("/sw.js".equals(path)) {
            if (serveAsset(client, "remote/sw.js", "application/javascript; charset=utf-8", true)) return;
        }

        // Other static files in remote/
        String cleanPath = path.startsWith("/") ? path.substring(1) : path;
        if (cleanPath.startsWith("remote/")) {
            cleanPath = cleanPath.substring(7);
        }
        if (!cleanPath.isEmpty() && serveAsset(client, "remote/" + cleanPath, getMimeType(cleanPath), false)) {
            return;
        }

        // Cross-Origin Protection: block untrusted external web pages from accessing control endpoints
        if (isUntrustedOrigin(origin)) {
            reply(client, 403, "application/json", "{\"error\":\"Cross-origin request rejected from " + escape(origin) + "\"}");
            return;
        }

        // 2. REST API Routes
        switch (path) {
            case "/ping":
                reply(client, 200, "text/plain", "PONG");
                return;
            case "/state":
                reply(client, 200, "application/json", host.state());
                return;
            case "/apps":
                reply(client, 200, "application/json", host.apps());
                return;
            case "/key": {
                String k = param(query, "k", body);
                boolean ok = host.key(k);
                reply(client, ok ? 200 : 400, "application/json", "{\"ok\":" + ok + ",\"key\":\"" + escape(k) + "\"}");
                return;
            }
            case "/text": {
                String t = param(query, "t", body);
                if (t.isEmpty()) t = param(query, "text", body);
                boolean ok = host.sendText(t);
                reply(client, ok ? 200 : 400, "application/json", "{\"ok\":" + ok + ",\"text\":\"" + escape(t) + "\"}");
                return;
            }
            case "/volume": {
                String v = param(query, "v", body);
                if (v.isEmpty()) v = param(query, "action", body);
                boolean ok = host.volume(v);
                reply(client, ok ? 200 : 400, "application/json", "{\"ok\":" + ok + ",\"action\":\"" + escape(v) + "\"}");
                return;
            }
            case "/mouse": {
                float dx = parseFloat(param(query, "dx", body), 0f);
                float dy = parseFloat(param(query, "dy", body), 0f);
                boolean click = "true".equalsIgnoreCase(param(query, "click", body));
                boolean scroll = "true".equalsIgnoreCase(param(query, "scroll", body));
                String action = param(query, "action", body);
                if (action.isEmpty() && scroll) action = "scroll";
                if (action.isEmpty() && click) action = "click";
                boolean ok = host.mouse(dx, dy, click, scroll, action);
                reply(client, ok ? 200 : 400, "application/json", "{\"ok\":" + ok + "}");
                return;
            }
            case "/tap": {
                String label = param(query, "label", body);
                boolean ok = host.tap(label);
                reply(client, ok ? 200 : 404, "application/json", "{\"ok\":" + ok + ",\"label\":\"" + escape(label) + "\"}");
                return;
            }
            case "/launch": {
                String pkg = param(query, "pkg", body);
                boolean ok = host.launch(pkg);
                reply(client, ok ? 200 : 404, "application/json", "{\"ok\":" + ok + ",\"pkg\":\"" + escape(pkg) + "\"}");
                return;
            }
            case "/open": {
                String url = param(query, "url", body);
                boolean ok = host.openUrl(url);
                reply(client, ok ? 200 : 400, "application/json", "{\"ok\":" + ok + "}");
                return;
            }
            case "/inputs":
                reply(client, 200, "application/json", host.inputs());
                return;
            case "/input": {
                String in = param(query, "id", body);
                if (in.isEmpty()) in = param(query, "name", body);
                boolean ok = host.switchInput(in);
                reply(client, ok ? 200 : 400, "application/json", "{\"ok\":" + ok + ",\"input\":\"" + escape(in) + "\"}");
                return;
            }
            case "/browser/config": {
                if ("POST".equals(method)) {
                    String adblock = param(query, "adblock", body);
                    String popups = param(query, "popups", body);
                    String ua = param(query, "ua", body);
                    String cookies = param(query, "cookies", body);
                    boolean ok = host.updateBrowserConfig(adblock, popups, ua, cookies);
                    reply(client, ok ? 200 : 400, "application/json", host.browserConfig());
                } else {
                    reply(client, 200, "application/json", host.browserConfig());
                }
                return;
            }
            case "/browser/clear": {
                boolean ok = host.clearBrowserData();
                reply(client, ok ? 200 : 400, "application/json", "{\"ok\":" + ok + "}");
                return;
            }
            case "/update": {
                if ("POST".equals(method)) {
                    String updateUrl = param(query, "url", body);
                    if (updateUrl.isEmpty()) {
                        reply(client, 400, "application/json", "{\"ok\":false,\"error\":\"Missing update URL parameter\"}");
                        return;
                    }
                    String res = host.updateApp(updateUrl);
                    reply(client, 200, "application/json", res);
                } else {
                    reply(client, 405, "application/json", "{\"error\":\"POST only\"}");
                }
                return;
            }
            case "/eval": {
                String token = param(query, "token", null);
                if (token.isEmpty() && authHeader != null) {
                    token = authHeader;
                }
                if (controlToken != null && !controlToken.isEmpty() && !controlToken.equals(token)) {
                    reply(client, 401, "application/json", "{\"error\":\"Unauthorized: invalid or missing control token\"}");
                    return;
                }
                if ("POST".equals(method)) {
                    final String js = body;
                    if (webView != null) {
                        webView.post(() -> webView.evaluateJavascript(js, null));
                        reply(client, 200, "text/plain", "OK");
                    } else {
                        reply(client, 503, "text/plain", "Launcher WebView not available");
                    }
                } else {
                    reply(client, 405, "text/plain", "POST only");
                }
                return;
            }
            default:
                reply(client, 404, "text/plain", "not found");
        }
    }

    private boolean serveAsset(Socket client, String assetPath, String contentType, boolean isServiceWorker) {
        if (context == null) return false;
        try {
            InputStream is = context.getAssets().open(assetPath);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            is.close();
            byte[] payload = baos.toByteArray();
            boolean keep = CURRENT_KEEP_ALIVE.get();
            String head = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + payload.length + "\r\n"
                + (isServiceWorker ? "Service-Worker-Allowed: /\r\n" : "")
                + "Cache-Control: no-cache, no-store, must-revalidate\r\n"
                + "Pragma: no-cache\r\n"
                + "Expires: 0\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Access-Control-Allow-Headers: Content-Type, X-Void-Token, Authorization\r\n"
                + "Connection: " + (keep ? "keep-alive" : "close") + "\r\n\r\n";
            OutputStream out = client.getOutputStream();
            out.write(head.getBytes(StandardCharsets.UTF_8));
            out.write(payload);
            out.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String getMimeType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (path.endsWith(".json") || path.endsWith(".webmanifest")) return "application/manifest+json; charset=utf-8";
        if (path.endsWith(".svg")) return "image/svg+xml; charset=utf-8";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    private static float parseFloat(String s, float def) {
        if (s == null || s.isEmpty()) return def;
        try { return Float.parseFloat(s); } catch (Exception e) { return def; }
    }

    /** Value from ?name=..., falling back to a plain-text body. */
    private static String param(String query, String name, String body) {
        if (query != null && !query.isEmpty()) {
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                String k = eq >= 0 ? pair.substring(0, eq) : pair;
                if (k.equals(name)) {
                    String v = eq >= 0 ? pair.substring(eq + 1) : "";
                    try {
                        return URLDecoder.decode(v, "UTF-8");
                    } catch (Exception e) {
                        return v;
                    }
                }
            }
        }
        if (body == null) return "";
        String trimmed = body.trim();
        if (trimmed.startsWith(name + "=")) {
            int eq = trimmed.indexOf('=');
            String v = trimmed.substring(eq + 1);
            int amp = v.indexOf('&');
            if (amp >= 0) v = v.substring(0, amp);
            try {
                return URLDecoder.decode(v, "UTF-8");
            } catch (Exception e) {
                return v;
            }
        }
        return trimmed;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    private static void reply(Socket client, int code, String type, String body) throws Exception {
        if (body == null) body = "";
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        String status = code == 200 ? "200 OK" : code == 401 ? "401 Unauthorized" : code == 403 ? "403 Forbidden" : code == 404 ? "404 Not Found" : code == 405 ? "405 Method Not Allowed" : "400 Bad Request";
        boolean keep = CURRENT_KEEP_ALIVE.get();
        String head = "HTTP/1.1 " + status + "\r\n"
            + "Content-Type: " + type + "\r\n"
            + "Content-Length: " + payload.length + "\r\n"
            + "Access-Control-Allow-Origin: *\r\n"
            + "Access-Control-Allow-Headers: Content-Type, X-Void-Token, Authorization\r\n"
            + "Connection: " + (keep ? "keep-alive" : "close") + "\r\n\r\n";
        OutputStream out = client.getOutputStream();
        out.write(head.getBytes(StandardCharsets.UTF_8));
        out.write(payload);
        out.flush();
    }
}
