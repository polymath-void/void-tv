package com.polymath.voidtv;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TvBridge {
    private final MainActivity activity;
    private static final Map<String, String> iconCache = new HashMap<>();

    public TvBridge(MainActivity activity) {
        this.activity = activity;
    }

    private static String extractIconBase64(PackageManager pm, String packageName, Drawable icon) {
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
    public boolean isAccessibilityServiceEnabled() {
        return VoidAccessibilityService.getInstance() != null;
    }

    @JavascriptInterface
    public void openAccessibilitySettings() {
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception ignored) {}
    }

    @JavascriptInterface
    public String getInstalledApps() {
        return getInstalledAppsJson(activity);
    }

    public static String getInstalledAppsJson(Context context) {
        if (context == null) return "[]";
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

                    String iconBase64 = extractIconBase64(pm, pkg, info.loadIcon(pm));
                    obj.put("icon", iconBase64);

                    array.put(obj);
                } catch (Exception ignored) {}
            }
        }
        return array.toString();
    }

    @JavascriptInterface
    public boolean launchApp(String packageName) {
        return launchApp(activity, packageName);
    }

    public static boolean launchApp(Context context, String packageName) {
        if (context == null || packageName == null) return false;
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
        activity.startActivity(intent);
    }

    @JavascriptInterface
    public void openBrowser(String url) {
        activity.runOnUiThread(() -> activity.showBrowser(url));
    }

    @JavascriptInterface
    public void closeBrowser() {
        activity.runOnUiThread(() -> activity.hideBrowser());
    }

    @JavascriptInterface
    public String listDirectory(String targetPath) {
        JSONArray array = new JSONArray();
        try {
            File dir = (targetPath == null || targetPath.isEmpty())
                ? Environment.getExternalStorageDirectory()
                : new File(targetPath);

            if (!dir.exists() || !dir.isDirectory()) {
                dir = Environment.getExternalStorageDirectory();
            }

            File[] files = dir.listFiles();
            if (files != null) {
                Arrays.sort(files, (a, b) -> {
                    if (a.isDirectory() && !b.isDirectory()) return -1;
                    if (!a.isDirectory() && b.isDirectory()) return 1;
                    return a.getName().compareToIgnoreCase(b.getName());
                });

                for (File f : files) {
                    if (f.getName().startsWith(".")) continue; // skip hidden files

                    JSONObject obj = new JSONObject();
                    obj.put("name", f.getName());
                    obj.put("path", f.getAbsolutePath());
                    obj.put("isDir", f.isDirectory());
                    obj.put("size", f.length());

                    if (f.isDirectory()) {
                        File[] sub = f.listFiles();
                        obj.put("itemCount", sub != null ? sub.length : 0);
                        obj.put("formattedSize", (sub != null ? sub.length : 0) + " items");
                    } else {
                        obj.put("formattedSize", formatFileSize(f.length()));
                    }

                    String ext = "";
                    int dot = f.getName().lastIndexOf('.');
                    if (dot >= 0) {
                        ext = f.getName().substring(dot + 1).toLowerCase();
                    }
                    obj.put("ext", ext);
                    array.put(obj);
                }
            }
        } catch (Exception ignored) {}
        return array.toString();
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %cB", bytes / Math.pow(1024, exp), pre);
    }

    @JavascriptInterface
    public boolean openFile(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) return false;

            String ext = "";
            int dot = file.getName().lastIndexOf('.');
            if (dot >= 0) ext = file.getName().substring(dot + 1).toLowerCase();

            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (mime == null) mime = "*/*";

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(file), mime);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
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
    public String getTvInputs() {
        return getTvInputsJson(activity);
    }

    public static String getTvInputsJson(Context context) {
        if (context == null) return "[]";
        JSONArray array = new JSONArray();
        try {
            TvInputManager tim = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
            if (tim != null) {
                List<TvInputInfo> list = tim.getTvInputList();
                if (list != null) {
                    for (TvInputInfo info : list) {
                        try {
                            JSONObject obj = new JSONObject();
                            String id = info.getId();
                            obj.put("id", id);
                            obj.put("type", info.getType());
                            obj.put("isPassthrough", info.isPassthroughInput());
                            CharSequence cs = info.loadLabel(context);
                            String label = cs != null ? cs.toString() : id;
                            obj.put("label", label);

                            String typeName = "OTHER";
                            String upperId = id.toUpperCase(java.util.Locale.ROOT);
                            if (info.getType() == TvInputInfo.TYPE_HDMI || upperId.contains("HDMI")) {
                                typeName = "HDMI";
                            } else if (info.getType() == TvInputInfo.TYPE_COMPOSITE || upperId.contains("AV")) {
                                typeName = "AV";
                            } else if (info.getType() == TvInputInfo.TYPE_TUNER || upperId.contains("DTV") || upperId.contains("ATV")) {
                                typeName = "TUNER";
                            }
                            obj.put("typeName", typeName);

                            array.put(obj);
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}
        return array.toString();
    }

    @JavascriptInterface
    public boolean switchInput(String inputKey) {
        return switchInput(activity, inputKey);
    }

    public static boolean switchInput(Context context, String inputKey) {
        if (context == null) return false;
        try {
            Intent intent = new Intent("com.android.tv.action.VIEW_INPUTS");
            intent.setComponent(new android.content.ComponentName("com.apps.livetv", "com.apps.livetv.SelectInputActivity"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);

            final String key = (inputKey != null) ? inputKey.trim().toUpperCase(java.util.Locale.ROOT) : "";
            if (context instanceof MainActivity) {
                MainActivity act = (MainActivity) context;
                if (key.contains("HDMI1") || key.equals("HDMI 1")) {
                    act.getWindow().getDecorView().postDelayed(() -> {
                        try {
                            Runtime.getRuntime().exec("input tap 132 255");
                        } catch (Exception ignored) {}
                    }, 450);
                } else if (key.contains("HDMI2") || key.equals("HDMI 2")) {
                    act.getWindow().getDecorView().postDelayed(() -> {
                        try {
                            Runtime.getRuntime().exec("input tap 132 328");
                        } catch (Exception ignored) {}
                    }, 450);
                } else if (key.contains("AV") || key.contains("COMPOSITE")) {
                    act.getWindow().getDecorView().postDelayed(() -> {
                        try {
                            Runtime.getRuntime().exec("input tap 132 401");
                        } catch (Exception ignored) {}
                    }, 450);
                }
            }
            return true;
        } catch (Exception e) {
            try {
                Intent fallback = new Intent("com.android.tv.action.VIEW_INPUTS");
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
                return true;
            } catch (Exception ignored) {
                return false;
            }
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

    @JavascriptInterface
    public boolean saveConfigFile(String fileName, String content) {
        try {
            File dir = new File("/sdcard/voidtv");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, fileName);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
            fos.write(content.getBytes("UTF-8"));
            fos.flush();
            fos.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @JavascriptInterface
    public String getLocalIpAddress() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> en = java.net.NetworkInterface.getNetworkInterfaces();
            while (en.hasMoreElements()) {
                java.net.NetworkInterface intf = en.nextElement();
                java.util.Enumeration<java.net.InetAddress> enumIpAddr = intf.getInetAddresses();
                while (enumIpAddr.hasMoreElements()) {
                    java.net.InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof java.net.Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }
}
