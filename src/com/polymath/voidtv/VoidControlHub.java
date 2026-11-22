package com.polymath.voidtv;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.webkit.WebView;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

public class VoidControlHub implements LiveServer.Host {
    private static final String TAG = "VoidControlHub";
    private static final int SERVER_PORT = 4444;

    private static final VoidControlHub INSTANCE = new VoidControlHub();

    private Context appContext;
    private LiveServer liveServer;
    private MainActivity activeLauncher;
    private VoidAccessibilityService activeService;
    private WebView launcherWebView;

    public static VoidControlHub getInstance() {
        return INSTANCE;
    }

    private VoidControlHub() {}

    public synchronized void init(Context context) {
        if (appContext == null && context != null) {
            appContext = context.getApplicationContext();
        }
        if (liveServer == null && appContext != null) {
            String token = readControlToken(appContext);
            liveServer = new LiveServer(SERVER_PORT, launcherWebView, appContext, this, token);
            liveServer.start();
            Log.i(TAG, "LiveServer started 24/7 on port " + SERVER_PORT);
        }
    }

    public synchronized void registerLauncher(MainActivity activity, WebView webView) {
        this.activeLauncher = activity;
        this.launcherWebView = webView;
        if (liveServer != null) {
            liveServer.setWebView(webView);
        }
    }

    public synchronized void unregisterLauncher(MainActivity activity) {
        if (this.activeLauncher == activity) {
            this.activeLauncher = null;
            this.launcherWebView = null;
        }
    }

    public synchronized void registerService(VoidAccessibilityService service) {
        this.activeService = service;
        Log.i(TAG, "VoidAccessibilityService registered in VoidControlHub");
    }

    public synchronized void unregisterService(VoidAccessibilityService service) {
        if (this.activeService == service) {
            this.activeService = null;
            Log.i(TAG, "VoidAccessibilityService unregistered from VoidControlHub");
        }
    }

    private static String readControlToken(Context ctx) {
        File tokenFile = new File(Environment.getExternalStorageDirectory(), "voidtv/control.token");
        if (!tokenFile.exists()) {
            tokenFile = new File(ctx.getFilesDir(), "control.token");
        }
        if (tokenFile.exists() && tokenFile.isFile()) {
            try (FileInputStream fis = new FileInputStream(tokenFile)) {
                byte[] data = new byte[(int) tokenFile.length()];
                int read = fis.read(data);
                if (read > 0) return new String(data, 0, read, StandardCharsets.UTF_8).trim();
            } catch (Exception ignored) {}
        }
        return null;
    }

    // =========================================================================
    // LiveServer.Host Implementation (Dynamic Hybrid Routing)
    // =========================================================================

    @Override
    public String state() {
        if (activeLauncher != null && activeLauncher.isForeground()) {
            return activeLauncher.state();
        }
        String app = activeService != null ? activeService.getCurrentPackage() : "unknown";
        boolean a11y = activeService != null;
        return "{\"mode\":\"APP\",\"app\":\"" + escape(app) + "\",\"accessibility\":" + a11y + "}";
    }

    @Override
    public boolean key(String name) {
        if (name == null || name.trim().isEmpty()) return false;

        // If launcher is in the foreground, it handles launcher navigation and in-app browser
        if (activeLauncher != null && activeLauncher.isForeground()) {
            return activeLauncher.key(name);
        }

        // External app in foreground: dispatch system-wide via AccessibilityService
        if (activeService != null) {
            return activeService.handleKey(name);
        }

        return false;
    }

    @Override
    public boolean sendText(String text) {
        if (text == null || text.isEmpty()) return false;

        if (activeLauncher != null && activeLauncher.isForeground()) {
            return activeLauncher.sendText(text);
        }

        if (activeService != null) {
            return activeService.injectText(text);
        }

        return false;
    }

    @Override
    public boolean mouse(float dx, float dy, boolean click, boolean scroll, String action) {
        if (activeLauncher != null && activeLauncher.isForeground() && activeLauncher.isBrowserShowing()) {
            return activeLauncher.mouse(dx, dy, click, scroll, action);
        }

        if (activeService != null) {
            return activeService.handleMouse(dx, dy, click, scroll, action);
        }

        return false;
    }

    @Override
    public boolean tap(String label) {
        if (activeLauncher != null && activeLauncher.isForeground()) {
            return activeLauncher.tap(label);
        }

        if (activeService != null) {
            return activeService.tapByLabel(label);
        }

        return false;
    }

    @Override
    public boolean launch(String pkg) {
        if (pkg == null || pkg.trim().isEmpty()) return false;
        Context ctx = appContext != null ? appContext : activeLauncher;
        if (ctx == null) return false;
        return TvBridge.launchApp(ctx, pkg.trim());
    }

    @Override
    public boolean openUrl(String url) {
        if (url == null || url.trim().isEmpty()) return false;
        if (activeLauncher != null) {
            return activeLauncher.openUrl(url);
        }
        if (appContext != null) {
            Intent intent = new Intent(appContext, MainActivity.class);
            intent.setAction(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            appContext.startActivity(intent);
            return true;
        }
        return false;
    }

    @Override
    public boolean switchInput(String input) {
        Context ctx = appContext != null ? appContext : activeLauncher;
        if (ctx == null) return false;
        return TvBridge.switchInput(ctx, input);
    }

    @Override
    public String inputs() {
        Context ctx = appContext != null ? appContext : activeLauncher;
        if (ctx == null) return "[]";
        return TvBridge.getTvInputsJson(ctx);
    }

    @Override
    public String apps() {
        Context ctx = appContext != null ? appContext : activeLauncher;
        if (ctx == null) return "[]";
        return TvBridge.getInstalledAppsJson(ctx);
    }

    @Override
    public boolean volume(String action) {
        if (action == null) return false;
        Context ctx = appContext != null ? appContext : activeLauncher;
        if (ctx == null) return false;

        String act = action.trim().toUpperCase(java.util.Locale.ROOT);
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return false;

        if ("VOL_UP".equals(act) || "UP".equals(act) || "VOLUME_UP".equals(act)) {
            am.adjustSuggestedStreamVolume(AudioManager.ADJUST_RAISE,
                AudioManager.USE_DEFAULT_STREAM_TYPE,
                AudioManager.FLAG_SHOW_UI);
            return true;
        } else if ("VOL_DOWN".equals(act) || "DOWN".equals(act) || "VOLUME_DOWN".equals(act)) {
            am.adjustSuggestedStreamVolume(AudioManager.ADJUST_LOWER,
                AudioManager.USE_DEFAULT_STREAM_TYPE,
                AudioManager.FLAG_SHOW_UI);
            return true;
        } else if ("MUTE".equals(act) || "VOLUME_MUTE".equals(act)) {
            am.adjustSuggestedStreamVolume(AudioManager.ADJUST_TOGGLE_MUTE,
                AudioManager.USE_DEFAULT_STREAM_TYPE,
                AudioManager.FLAG_SHOW_UI);
            return true;
        }
        return false;
    }

    @Override
    public String browserConfig() {
        if (activeLauncher != null) {
            return activeLauncher.browserConfig();
        }
        if (appContext != null) {
            return new VoidShield(appContext).getStatusJson();
        }
        return "{}";
    }

    @Override
    public boolean updateBrowserConfig(String adblock, String popups, String ua, String cookies) {
        if (activeLauncher != null) {
            return activeLauncher.updateBrowserConfig(adblock, popups, ua, cookies);
        }
        if (appContext != null) {
            VoidShield vs = new VoidShield(appContext);
            if (adblock != null && !adblock.isEmpty()) vs.setAdBlockEnabled("true".equalsIgnoreCase(adblock) || "1".equals(adblock));
            if (popups != null && !popups.isEmpty()) vs.setPopupBlockEnabled("true".equalsIgnoreCase(popups) || "1".equals(popups));
            if (ua != null && !ua.isEmpty()) vs.setDesktopUaEnabled("desktop".equalsIgnoreCase(ua) || "true".equalsIgnoreCase(ua));
            if (cookies != null && !cookies.isEmpty()) vs.setCookiesEnabled("true".equalsIgnoreCase(cookies) || "1".equals(cookies));
            return true;
        }
        return false;
    }

    @Override
    public boolean clearBrowserData() {
        if (activeLauncher != null) {
            return activeLauncher.clearBrowserData();
        }
        return true;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
