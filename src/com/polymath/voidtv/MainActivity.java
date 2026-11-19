package com.polymath.voidtv;

import android.app.Activity;
import android.os.Bundle;
import android.os.FileObserver;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import java.io.File;

public class MainActivity extends Activity {
    private WebView webView;
    private LiveServer liveServer;
    private FileObserver fileObserver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState) ;

        // Ensure immersive fullscreen on TV
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        File configDir = new File("/sdcard/voidtv");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        webView.addJavascriptInterface(new TvBridge(this), "Android");
        webView.loadUrl("file:///android_asset/runtime/index.html");

        setContentView(webView);

        // Start live coding REPL server on 0.0.0.0:4444
        liveServer = new LiveServer(4444, webView);
        liveServer.start();

        // Watch /sdcard/voidtv for changes
        try {
            fileObserver = new FileObserver(configDir.getPath(), FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO) {
                @Override
                public void onEvent(int event, String path) {
                    webView.post(() -> {
                        webView.evaluateJavascript("window.HotReload && window.HotReload.reloadConfig();", null);
                    });
                }
            };
            fileObserver.startWatching();
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (liveServer != null) liveServer.stop();
        if (fileObserver != null) fileObserver.stopWatching();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        String key = null;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                key = "UP";
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                key = "DOWN";
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                key = "LEFT";
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                key = "RIGHT";
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                key = "SELECT";
                break;
            case KeyEvent.KEYCODE_BACK:
                key = "BACK";
                break;
            case KeyEvent.KEYCODE_MENU:
                key = "MENU";
                break;
            case KeyEvent.KEYCODE_0: case KeyEvent.KEYCODE_1: case KeyEvent.KEYCODE_2:
            case KeyEvent.KEYCODE_3: case KeyEvent.KEYCODE_4: case KeyEvent.KEYCODE_5:
            case KeyEvent.KEYCODE_6: case KeyEvent.KEYCODE_7: case KeyEvent.KEYCODE_8:
            case KeyEvent.KEYCODE_9:
                key = String.valueOf(keyCode - KeyEvent.KEYCODE_0);
                break;
        }

        if (key != null) {
            final String finalKey = key;
            webView.post(() -> {
                webView.evaluateJavascript("window.FocusEngine && window.FocusEngine.onKey('" + finalKey + "');", null);
            });
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
