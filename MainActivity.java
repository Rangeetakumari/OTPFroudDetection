package com.example.otpfrauddetection;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_SMS = 100;
    private static final int PERMISSION_USAGE = 101;
    private static final int PERMISSION_NOTIFICATION = 102;
    private static final String PREFS_NAME = "OTP_FRAUD_PREFS";
    private static final String KEY_PROTECTION = "protection_enabled";

    private TextView tvSmsStatus, tvUsageStatus, tvUpiStatus, tvAlert;
    private Switch switchProtection;
    private static TextView staticAlert;

    public static boolean isAppForeground = false;

    private Handler statusHandler = new Handler();
    private Runnable statusUpdater = new Runnable() {
        @Override
        public void run() {
            if (isAppForeground) {
                updateUpiStatus();   // 🔁 UPI status updates every 200ms
                updateAlertWithCurrentStatus();
                statusHandler.postDelayed(this, 200);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvSmsStatus = findViewById(R.id.tvSmsStatus);
        tvUsageStatus = findViewById(R.id.tvUsageStatus);
        tvUpiStatus = findViewById(R.id.tvUpiStatus);
        tvAlert = findViewById(R.id.tvAlert);
        staticAlert = tvAlert;

        Button btnGrantSms = findViewById(R.id.btnGrantSms);
        Button btnGrantUsage = findViewById(R.id.btnGrantUsage);

        btnGrantSms.setOnClickListener(v -> requestSmsPermission());
        btnGrantUsage.setOnClickListener(v -> requestUsagePermission());

        switchProtection = findViewById(R.id.switchProtection);

        // Restore protection state
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isEnabled = prefs.getBoolean(KEY_PROTECTION, false);
        switchProtection.setChecked(isEnabled);
        if (isEnabled) {
            startService(new Intent(this, ForegroundService.class));
        }

        switchProtection.setOnCheckedChangeListener((buttonView, isChecked) -> {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_PROTECTION, isChecked)
                    .apply();

            if (isChecked) {
                requestNotificationPermission();
                startService(new Intent(this, ForegroundService.class));
                Toast.makeText(this, "✅ Protection enabled", Toast.LENGTH_SHORT).show();
            } else {
                stopService(new Intent(this, ForegroundService.class));
                Toast.makeText(this, "❌ Protection disabled", Toast.LENGTH_SHORT).show();
                tvAlert.setVisibility(android.view.View.GONE);
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().clear().apply();
            }
        });

        if (switchProtection.isChecked()) {
            startService(new Intent(this, ForegroundService.class));
        }

        // Overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, 100);
            }
        }

        // Battery optimization
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }

        updateUI();
        updateUpiStatus();      // initial update
        updateAlertWithCurrentStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        isAppForeground = true;

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isEnabled = prefs.getBoolean(KEY_PROTECTION, false);
        if (isEnabled && !switchProtection.isChecked()) {
            switchProtection.setChecked(true);
            startService(new Intent(this, ForegroundService.class));
        }

        updateUI();
        updateUpiStatus();      // update when app comes to foreground
        updateAlertWithCurrentStatus();
        statusHandler.removeCallbacks(statusUpdater);
        statusHandler.post(statusUpdater);  // start periodic updates
    }

    @Override
    protected void onPause() {
        super.onPause();
        isAppForeground = false;
        statusHandler.removeCallbacks(statusUpdater);
    }

    // ---- Permission methods ----

    private void requestSmsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS},
                    PERMISSION_SMS);
        } else {
            Toast.makeText(this, "SMS already granted", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestUsagePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), getPackageName());
            if (mode != AppOpsManager.MODE_ALLOWED) {
                Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                startActivityForResult(intent, PERMISSION_USAGE);
            } else {
                Toast.makeText(this, "Usage already granted", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        PERMISSION_NOTIFICATION);
            }
        }
    }

    // ---- UI update methods ----

    private void updateUI() {
        boolean smsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
                == PackageManager.PERMISSION_GRANTED;
        tvSmsStatus.setText("SMS Permission: " + (smsGranted ? "✅ GRANTED" : "❌ DENIED"));

        boolean usageGranted = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), getPackageName());
            usageGranted = (mode == AppOpsManager.MODE_ALLOWED);
        }
        tvUsageStatus.setText("Usage Access: " + (usageGranted ? "✅ GRANTED" : "❌ DENIED"));
    }

    // 🔁 **UPI STATUS UPDATE – called every 200ms**
    private void updateUpiStatus() {
        boolean appRunning = AppStatusChecker.isAnyBankingAppRunning(this);
        String status;
        if (appRunning) {
            String appName = getCurrentBankingAppName();
            status = "RUNNING\nActive: " + (appName != null ? appName : "Unknown App");
        } else {
            status = "NOT RUNNING\nNo UPI App Detected";
        }
        tvUpiStatus.setText("UPI Status: " + status);
    }

    // Helper to get the friendly app name
    private String getCurrentBankingAppName() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
                if (usm != null) {
                    long now = System.currentTimeMillis();
                    List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 10000, now);
                    if (stats != null) {
                        for (UsageStats stat : stats) {
                            String pkg = stat.getPackageName();
                            if (AppScanner.isBankingApp(pkg)) {
                                if (now - stat.getLastTimeUsed() < 5000) {
                                    return getFriendlyAppName(pkg);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) { /* ignore */ }
        }
        // Fallback
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                List<android.app.ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
                if (procs != null) {
                    for (android.app.ActivityManager.RunningAppProcessInfo proc : procs) {
                        if (proc.importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE) {
                            for (String pkg : proc.pkgList) {
                                if (AppScanner.isBankingApp(pkg)) {
                                    return getFriendlyAppName(pkg);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) { /* ignore */ }
        return null;
    }

    private String getFriendlyAppName(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            String label = pm.getApplicationLabel(ai).toString();
            if (label != null && !label.isEmpty() && !label.equals(packageName)) {
                return label;
            }
        } catch (Exception e) { /* ignore */ }
        // Fallback map
        if (packageName.equals("in.org.npci.upiapp")) return "BHIM";
        if (packageName.equals("com.phonepe.app")) return "PhonePe";
        if (packageName.equals("com.google.android.apps.nbu.paisa.user")) return "Google Pay";
        if (packageName.equals("com.paytm.app")) return "Paytm";
        if (packageName.equals("com.sbi.SBIPAY")) return "SBI Pay";
        if (packageName.equals("com.bankofbaroda.mobile")) return "BOB World";
        return packageName;
    }

    // ---- Alert methods ----

    private void updateAlertWithCurrentStatus() {
        boolean appRunning = AppStatusChecker.isAnyBankingAppRunning(this);
        String msg;
        boolean danger;
        if (appRunning) {
            String appName = getCurrentBankingAppName();
            msg = "✅ SAFE – Banking app is running" + (appName != null ? " (" + appName + ")" : "");
            danger = false;
        } else {
            msg = "⚠️ FRAUD ALERT! Banking OTP: (No UPI App Open)";
            danger = true;
        }
        saveStatusAlert(msg, danger);
        updateAlert(msg, danger);
    }

    private void saveStatusAlert(String message, boolean isDanger) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .putString("last_alert_message", message)
                .putBoolean("last_alert_danger", isDanger)
                .apply();
    }

    public static void updateAlert(final String message, final boolean isDanger) {
        if (staticAlert == null) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            staticAlert.setVisibility(android.view.View.VISIBLE);
            staticAlert.setText(message);
            if (isDanger) {
                staticAlert.setBackgroundColor(android.graphics.Color.parseColor("#ffebee"));
                staticAlert.setTextColor(android.graphics.Color.RED);
            } else {
                staticAlert.setBackgroundColor(android.graphics.Color.parseColor("#e8f5e9"));
                staticAlert.setTextColor(android.graphics.Color.GREEN);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_SMS) updateUI();
        else if (requestCode == PERMISSION_NOTIFICATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100) {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Please enable \"Display over other apps\" for this app", Toast.LENGTH_LONG).show();
            }
        }
        if (requestCode == PERMISSION_USAGE) {
            updateUI();
        }
    }
}