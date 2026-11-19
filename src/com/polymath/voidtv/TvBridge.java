package com.polymath.voidtv;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TvBridge {
    private final Context context;
    private final Map<String, String> iconCache = new HashMap<>();

    public TvBridge(Context context) {
        this.context = context;
    }

    private String extractIconBase64(PackageManager pm, String packageName, Drawable icon) {
        if (iconCache.containsKey(packageName)) {
            return iconCache.get(packageName);
        }
        try {
            if (icon == null) {
                icon = pm.getApplicationIcon(packageName);
            }
            Bitmap bitmap;
            if (icon instanceof BitmapDrawable) {
                bitmap = ((BitmapDrawable) icon).getBitmap();
            } else {
                int w = icon.getIntrinsicWidth() > 0 ? Math.min(icon.getIntrinsicWidth(), 128) : 96;
                int h = icon.getIntrinsicHeight() > 0 ? Math.min(icon.getIntrinsicHeight(), 128) : 96;
                bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                icon.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                icon.draw(canvas);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 85, baos);
            String base64 = "data:image/png;base64," + Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
            iconCache.put(packageName, base64);
            return base64;
        } catch (Exception e) {
            return "";
        }
    }

    @JavascriptInterface
    public String getInstalledApps() {
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(intent, 0);

        if (list == null || list.isEmpty()) {
            Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
            launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            list = pm.queryIntentActivities(launcherIntent, 0);
        }

        JSONArray array = new JSONArray();
        if (list != null) {
            for (ResolveInfo info : list) {
                try {
                    String pkg = info.activityInfo.packageName;
                    JSONObject obj = new JSONObject();
                    obj.put("label", info.loadLabel(pm).toString());
                    obj.put("packageName", pkg);
                    obj.put("activityName", info.activityInfo.name);

                    // Extract actual high-res application icon
                    String iconBase64 = extractIconBase64(pm, pkg, info.loadIcon(pm));
                    obj.put("icon", iconBase64);

                    array.put(obj);
                } catch (Exception ignored) {}
            }
        }
        return array.toString();
    }

    @JavascriptInterface
    public String getAppIcon(String packageName) {
        if (iconCache.containsKey(packageName)) {
            return iconCache.get(packageName);
        }
        try {
            PackageManager pm = context.getPackageManager();
            return extractIconBase64(pm, packageName, null);
        } catch (Exception e) {
            return "";
        }
    }

    @JavascriptInterface
    public boolean launchApp(String packageName) {
        PackageManager pm = context.getPackageManager();
        Intent launchIntent = pm.getLeanbackLaunchIntentForPackage(packageName);
        if (launchIntent == null) {
            launchIntent = pm.getLaunchIntentForPackage(packageName);
        }
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launchIntent);
            return true;
        }
        return false;
    }

    @JavascriptInterface
    public void openSettings() {
        Intent intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @JavascriptInterface
    public String getSystemTelemetry() {
        try {
            JSONObject obj = new JSONObject();
            Runtime runtime = Runtime.getRuntime();
            long totalMem = runtime.totalMemory();
            long freeMem = runtime.freeMemory();
            long usedMem = totalMem - freeMem;
            obj.put("jvm_used_mb", usedMem / (1024 * 1024));
            obj.put("android_version", Build.VERSION.RELEASE);
            obj.put("device_model", Build.MODEL);
            return obj.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    @JavascriptInterface
    public String getConfigFile(String fileName) {
        try {
            File f = new File("/sdcard/voidtv", fileName);
            if (!f.exists()) return "";
            FileInputStream fis = new FileInputStream(f);
            byte[] buf = new byte[(int) f.length()];
            fis.read(buf);
            fis.close();
            return new String(buf, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }
}
