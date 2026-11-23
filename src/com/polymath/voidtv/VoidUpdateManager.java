package com.polymath.voidtv;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VoidUpdateManager {
    private static final String TAG = "VoidUpdateManager";

    public interface UpdateCallback {
        void onProgress(String message, int percent);
        void onSuccess(String message);
        void onError(String error);
    }

    public static void installFromUrlAsync(Context context, String apkUrl, UpdateCallback callback) {
        new Thread(() -> {
            try {
                if (callback != null) callback.onProgress("Connecting to download server...", 5);
                File target = new File(context.getCacheDir(), "voidtv_update.apk");
                if (target.exists()) target.delete();

                URL url = new URL(apkUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(15000);
                conn.connect();

                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) {
                    throw new Exception("HTTP download failed with response code: " + code);
                }

                long totalBytes = conn.getContentLength();
                InputStream in = conn.getInputStream();
                OutputStream out = new FileOutputStream(target);
                byte[] buf = new byte[16384];
                long readBytes = 0;
                int n;

                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    readBytes += n;
                    if (totalBytes > 0 && callback != null) {
                        int pct = 5 + (int) ((readBytes * 45) / totalBytes);
                        callback.onProgress("Downloading APK: " + (readBytes / 1024) + " KB", pct);
                    }
                }
                out.flush();
                out.close();
                in.close();
                conn.disconnect();

                if (callback != null) callback.onProgress("Staging APK into PackageInstaller...", 60);
                installFromFile(context, target, callback);
            } catch (Exception e) {
                Log.e(TAG, "Update failed", e);
                if (callback != null) callback.onError(e.getMessage() != null ? e.getMessage() : e.toString());
            }
        }, "VoidUpdate-Worker").start();
    }

    public static boolean installFromFile(Context context, File apkFile, UpdateCallback callback) throws Exception {
        if (!apkFile.exists() || apkFile.length() <= 0) {
            throw new Exception("Target APK file does not exist or is empty");
        }

        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        );
        params.setAppPackageName(context.getPackageName());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31-34): Self-updates can bypass user action requirement
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        }

        int sessionId = installer.createSession(params);
        PackageInstaller.Session session = installer.openSession(sessionId);

        long size = apkFile.length();
        try (InputStream in = new FileInputStream(apkFile);
             OutputStream out = session.openWrite("voidtv_update", 0, size)) {
            byte[] buf = new byte[32768];
            int n;
            long written = 0;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                written += n;
                if (callback != null) {
                    int pct = 60 + (int) ((written * 35) / size);
                    callback.onProgress("Streaming to OS installer: " + (written / 1024) + " KB", pct);
                }
            }
            session.fsync(out);
        }

        if (callback != null) callback.onProgress("Committing installation session...", 98);

        Intent intent = new Intent(context, VoidUpdateReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags);
        session.commit(pendingIntent.getIntentSender());
        session.close();

        if (callback != null) callback.onSuccess("Update session committed successfully (ID: " + sessionId + ")");
        return true;
    }
}
