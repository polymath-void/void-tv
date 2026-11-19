package com.polymath.voidtv;

import android.webkit.WebView;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class LiveServer {
    private final int port;
    private final WebView webView;
    private ServerSocket serverSocket;
    private boolean running = false;

    public LiveServer(int port, WebView webView) {
        this.port = port;
        this.webView = webView;
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
                BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
                String line = reader.readLine();
                if (line == null) {
                    client.close();
                    return;
                }

                if (line.startsWith("POST /eval")) {
                    int contentLength = 0;
                    while (true) {
                        String header = reader.readLine();
                        if (header == null || header.isEmpty()) break;
                        if (header.toLowerCase().startsWith("content-length: ")) {
                            contentLength = Integer.parseInt(header.substring(16).trim());
                        }
                    }

                    char[] body = new char[contentLength];
                    int read = 0;
                    while (read < contentLength) {
                        int r = reader.read(body, read, contentLength - read);
                        if (r == -1) break;
                        read += r;
                    }
                    String js = new String(body);

                    webView.post(() -> webView.evaluateJavascript(js, null));

                    OutputStream out = client.getOutputStream();
                    String resp = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 2\r\n\r\nOK";
                    out.write(resp.getBytes());
                    out.flush();
                } else if (line.startsWith("GET /ping")) {
                    OutputStream out = client.getOutputStream();
                    String resp = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 4\r\n\r\nPONG";
                    out.write(resp.getBytes());
                    out.flush();
                } else {
                    OutputStream out = client.getOutputStream();
                    String resp = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n";
                    out.write(resp.getBytes());
                    out.flush();
                }
                client.close();
            } catch (Exception ignored) {
                try { client.close(); } catch (Exception ignored2) {}
            }
        }).start();
    }
}
