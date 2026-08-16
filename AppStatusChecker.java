package com.example.otpfrauddetection;

import android.app.ActivityManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.util.List;

public class AppStatusChecker {
    private static final String TAG = "OTP_FRAUD";

    public static boolean isAnyBankingAppRunning(Context context) {
        Log.d(TAG, "Checking banking app status...");

        // 1. Foreground
        if (isBankingAppForeground(context)) {
            Log.d(TAG, "✅ SAFE: Banking app in foreground");
            return true;
        }

        // 2. Background process
        if (isBankingAppRunningInBackground(context)) {
            Log.d(TAG, "✅ SAFE: Banking app in background (process)");
            return true;
        }

        // 3. Recently used (last 60s)
        if (isBankingAppRecentlyUsed(context)) {
            Log.d(TAG, "✅ SAFE: Banking app recently used (within 60s)");
            return true;
        }

        Log.d(TAG, "❌ No banking app detected");
        return false;
    }

    private static boolean isBankingAppForeground(Context context) {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return false;

            List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
            if (processes == null) return false;

            for (ActivityManager.RunningAppProcessInfo p : processes) {
                if (p.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    for (String pkg : p.pkgList) {
                        if (AppScanner.isBankingApp(pkg)) {
                            Log.d(TAG, "Foreground process: " + pkg);
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Foreground check error", e);
        }
        return false;
    }

    private static boolean isBankingAppRunningInBackground(Context context) {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return false;

            List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
            if (processes == null) return false;

            for (ActivityManager.RunningAppProcessInfo p : processes) {
                if (p.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE) {
                    for (String pkg : p.pkgList) {
                        if (AppScanner.isBankingApp(pkg)) {
                            Log.d(TAG, "Background process: " + pkg + " (importance=" + p.importance + ")");
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Background process check error", e);
        }
        return false;
    }

    private static boolean isBankingAppRecentlyUsed(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Log.w(TAG, "UsageStats not available on this Android version");
            return false;
        }

        try {
            UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) {
                Log.w(TAG, "UsageStatsManager is null");
                return false;
            }

            long now = System.currentTimeMillis();
            // Query last 2 minutes (120 seconds)
            List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 120000, now);
            if (stats == null || stats.isEmpty()) {
                Log.d(TAG, "No UsageStats returned – maybe permission not granted?");
                return false;
            }

            for (UsageStats stat : stats) {
                String pkg = stat.getPackageName();
                if (AppScanner.isBankingApp(pkg)) {
                    long lastUsed = stat.getLastTimeUsed();
                    long diff = now - lastUsed;
                    if (diff < 60000) {
                        Log.d(TAG, "UsageStats: " + pkg + " last used " + diff + "ms ago");
                        return true;
                    } else {
                        Log.d(TAG, "UsageStats: " + pkg + " last used " + diff + "ms ago (too old)");
                    }
                }
            }
        } catch (SecurityException e) {
            Log.w(TAG, "UsageStats permission denied – please grant Usage Access in Settings");
        } catch (Exception e) {
            Log.e(TAG, "UsageStats error", e);
        }
        return false;
    }
}