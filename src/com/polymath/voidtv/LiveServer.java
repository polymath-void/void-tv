package com.polymath.voidtv;

import android.webkit.WebView;

import java.io.BufferedReader;
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
    }

    private final int port;
    private final WebView webView;
    private final Host host;
    private ServerSocket serverSocket;
    private boolean running = false;

    public LiveServer(int port, WebView webView, Host host) {
        this.port = port;
        this.webView = webView;
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

        switch (path) {
            case "/ping":
                reply(client, 200, "text/plain", "PONG");
                return;
            case "/state":
                reply(client, 200, "application/json", host.state());
                return;
            case "/key": {
                String k = param(query, "k", body);
                boolean ok = host.key(k);
                reply(client, ok ? 200 : 400, "application/json", "{\"ok\":" + ok + ",\"key\":\"" + escape(k) + "\"}");
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
