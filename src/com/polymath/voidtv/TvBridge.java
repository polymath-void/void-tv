package com.polymath.voidtv;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Build;
import android.os.BatteryManager;
import android.content.IntentFilter;
import android.webkit.JavascriptInterface;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;

public class TvBridge {
    private final Context context;

    public TvBridge(Context context) {
        this.context = context;
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
                    JSONObject obj = new JSONObject();
                    obj.put("label", info.loadLabel(pm).toString());
                    obj.put("packageName", info.activityInfo.packageName);
                    obj.put("activityName", info.activityInfo.name);
                    array.put(obj);
                } catch (Exception ignored) {}
            }
        }
        return array.toString();
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
