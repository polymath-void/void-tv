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
        boolean mouse(float dx, float dy, boolean click);
        boolean volume(String action);
    }

    private final int port;
    private final WebView webView;
    private final Context context;
    private final Host host;
    private ServerSocket serverSocket;
    private boolean running = false;

    public LiveServer(int port, WebView webView, Context context, Host host) {
        this.port = port;
        this.webView = webView;
        this.context = context;
        this.host = host;
    }

    public void start() {
        running = true;
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                while (running) {
                    Socket client = serverSocket.accept();
                    handleClient(client);
                }
            } catch (Exception ignored) {}
        }).start();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception ignored) {}
    }

    private void handleClient(Socket client) {
        new Thread(() -> {
            try {
                client.setSoTimeout(5000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                String requestLine = reader.readLine();
                if (requestLine == null) {
                    client.close();
                    return;
                }

                int contentLength = 0;
                String header;
                while ((header = reader.readLine()) != null && !header.isEmpty()) {
                    if (header.toLowerCase().startsWith("content-length:")) {
                        contentLength = Integer.parseInt(header.substring(15).trim());
                    }
                }

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

                route(client, requestLine, body);
                client.close();
            } catch (Exception ignored) {
                try { client.close(); } catch (Exception ignored2) {}
            }
        }).start();
    }

    private void route(Socket client, String requestLine, String body) throws Exception {
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
                boolean ok = host.mouse(dx, dy, click);
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
            case "/eval":
                if ("POST".equals(method)) {
                    final String js = body;
                    webView.post(() -> webView.evaluateJavascript(js, null));
                    reply(client, 200, "text/plain", "OK");
                } else {
                    reply(client, 405, "text/plain", "POST only");
                }
                return;
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
            String head = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + payload.length + "\r\n"
                + (isServiceWorker ? "Service-Worker-Allowed: /\r\n" : "")
                + "Cache-Control: " + (isServiceWorker ? "no-cache" : "public, max-age=3600") + "\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Connection: close\r\n\r\n";
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
        return body == null ? "" : body.trim();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void reply(Socket client, int code, String type, String body) throws Exception {
        if (body == null) body = "";
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        String status = code == 200 ? "200 OK" : code == 404 ? "404 Not Found" : code == 405 ? "405 Method Not Allowed" : "400 Bad Request";
        String head = "HTTP/1.1 " + status + "\r\n"
            + "Content-Type: " + type + "\r\n"
            + "Content-Length: " + payload.length + "\r\n"
            + "Access-Control-Allow-Origin: *\r\n"
            + "Connection: close\r\n\r\n";
        OutputStream out = client.getOutputStream();
        out.write(head.getBytes(StandardCharsets.UTF_8));
        out.write(payload);
        out.flush();
    }
}
