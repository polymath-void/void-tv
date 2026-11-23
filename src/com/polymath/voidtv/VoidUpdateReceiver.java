package com.polymath.voidtv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;

public class VoidUpdateReceiver extends BroadcastReceiver {
    private static final String TAG = "VoidUpdate";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();

        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            Log.i(TAG, "VoidTV was successfully updated. Relaunching launcher...");
            relaunchApp(context);
            return;
        }

        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);

        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                Log.i(TAG, "Update requires user confirmation dialog");
                Intent confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(confirmIntent);
                }
                break;

            case PackageInstaller.STATUS_SUCCESS:
                Log.i(TAG, "PackageInstaller session succeeded!");
                relaunchApp(context);
                break;

            case PackageInstaller.STATUS_FAILURE:
            case PackageInstaller.STATUS_FAILURE_ABORTED:
            case PackageInstaller.STATUS_FAILURE_BLOCKED:
            case PackageInstaller.STATUS_FAILURE_CONFLICT:
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
            case PackageInstaller.STATUS_FAILURE_INVALID:
            case PackageInstaller.STATUS_FAILURE_STORAGE:
                Log.e(TAG, "PackageInstaller failed: " + status + " (" + message + ")");
                break;

            default:
                Log.w(TAG, "PackageInstaller status: " + status);
                break;
        }
    }

    private static void relaunchApp(Context context) {
        try {
            Intent launch = context.getPackageManager().getLeanbackLaunchIntentForPackage(context.getPackageName());
            if (launch == null) {
                launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            }
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(launch);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to relaunch VoidTV", e);
        }
    }
}
