package com.example.otpfrauddetection;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.util.List;

public class DetectionService extends android.app.Service {
    private static final String TAG = "OTP_FRAUD";
    private static final String PREFS_NAME = "OTP_FRAUD_PREFS";
    private HandlerThread mHandlerThread;
    private Handler mHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            mHandlerThread = new HandlerThread("DetectionThread");
            mHandlerThread.start();
            mHandler = new Handler(mHandlerThread.getLooper());
        } catch (Exception e) {
            Log.e(TAG, "onCreate error", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;
        String smsBody = intent.getStringExtra("sms_body");
        if (smsBody == null) return START_STICKY;
        if (mHandler != null) {
            mHandler.post(() -> processSms(smsBody));
        }
        return START_STICKY;
    }

    private void processSms(String smsBody) {
        try {
            String upper = smsBody.toUpperCase();

            // 1. Check OTP indicator
            boolean hasOtp = false;
            for (String otpWord : Constants.OTP_KEYWORDS) {
                if (upper.contains(otpWord)) {
                    hasOtp = true;
                    break;
                }
            }
            if (!hasOtp) {
                Log.d(TAG, "No OTP keyword");
                return;
            }

            // 2. Check banking keyword
            boolean isBanking = false;
            String matchedBank = "";
            for (String bank : Constants.BANKING_KEYWORDS) {
                if (upper.contains(bank)) {
                    isBanking = true;
                    matchedBank = bank;
                    break;
                }
            }
            if (!isBanking) {
                Log.d(TAG, "No banking keyword");
                return;
            }

            // 3. Check if any banking app is running
            boolean appRunning = AppStatusChecker.isAnyBankingAppRunning(this);
            Log.d(TAG, "Banking app running: " + appRunning);

            // 🔥 **DEBUG TOAST** – shows detection result on the device
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(getApplicationContext(),
                            "Detection: " + appRunning, Toast.LENGTH_SHORT).show()
            );

            // 4. Extract OTP digits
            String otpDigits = extractOTP(smsBody);

            // 5. Build heading and message
            final String heading;
            final String message;
            final boolean danger;

            if (appRunning) {
                String appName = getAppName(this, getCurrentBankingAppPackage(this));
                heading = "✅ SAFE";
                message = "Banking OTP " + otpDigits + " (" + appName + " is open)";
                danger = false;
            } else {
                heading = "⚠️ FRAUD ALERT";
                message = "No banking app running! OTP: " + otpDigits;
                danger = true;
            }

            saveAlert(heading + "\n" + message, danger);

            // 6. Show overlay toast (with fallback) – ALWAYS called
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    OverlayToast.show(getApplicationContext(), heading, message, danger, 7000);
                } catch (Exception e) {
                    // Fallback to normal system toast
                    Toast.makeText(getApplicationContext(), heading + "\n" + message, Toast.LENGTH_LONG).show();
                }
            });

            // 7. Vibrate only on fraud (20 seconds)
            if (danger) {
                Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (v != null) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createOneShot(20000, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        v.vibrate(20000);
                    }
                }
            }

            // 8. Update in‑app UI if foreground
            new Handler(Looper.getMainLooper()).post(() -> {
                if (MainActivity.isAppForeground) {
                    MainActivity.updateAlert(heading + "\n" + message, danger);
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "processSms error", e);
        }
    }

    // ----- Helper methods -----

    private String extractOTP(String smsBody) {
        StringBuilder digits = new StringBuilder();
        for (char c : smsBody.toCharArray()) {
            if (Character.isDigit(c)) digits.append(c);
        }
        String result = digits.toString();
        if (result.length() >= 4) return result.substring(0, Math.min(6, result.length()));
        return result.length() > 0 ? result : "123456";
    }

    private String getCurrentBankingAppPackage(Context context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            try {
                android.app.usage.UsageStatsManager usm =
                        (android.app.usage.UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
                if (usm != null) {
                    long now = System.currentTimeMillis();
                    List<android.app.usage.UsageStats> stats =
                            usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, now - 10000, now);
                    if (stats != null) {
                        for (android.app.usage.UsageStats stat : stats) {
                            String pkg = stat.getPackageName();
                            if (AppScanner.isBankingApp(pkg)) {
                                if (now - stat.getLastTimeUsed() < 5000) {
                                    return pkg;
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String getAppName(Context context, String packageName) {
        if (packageName == null) return "Unknown";
        try {
            android.content.pm.PackageManager pm = context.getPackageManager();
            android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(ai).toString();
        } catch (Exception e) {
            return packageName;
        }
    }

    private void saveAlert(String message, boolean isDanger) {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            prefs.edit()
                    .putString("last_alert_message", message)
                    .putBoolean("last_alert_danger", isDanger)
                    .apply();
        } catch (Exception e) {
            Log.e(TAG, "saveAlert error", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mHandlerThread != null) mHandlerThread.quitSafely();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}