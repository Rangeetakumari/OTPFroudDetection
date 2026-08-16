package com.example.otpfrauddetection;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

public class OverlayToast {
    private static final String TAG = "OTP_FRAUD";
    private static WindowManager windowManager;
    private static View toastView;
    private static Handler handler = new Handler(Looper.getMainLooper());

    public static void show(Context context, String heading, String message, boolean isDanger) {
        show(context, heading, message, isDanger, 4000);
    }

    public static void show(Context context, String heading, String message, boolean isDanger, int durationMs) {
        // 1️⃣ If overlay permission is not granted → fallback to PopupActivity
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(context)) {
                PopupActivity.show(context, heading + "\n" + message, isDanger);
                return;
            }
        }

        try {
            if (windowManager == null) {
                windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                if (windowManager == null) {
                    PopupActivity.show(context, heading + "\n" + message, isDanger);
                    return;
                }
            }

            if (toastView != null) {
                try { windowManager.removeView(toastView); } catch (Exception ignored) {}
                toastView = null;
            }

            LayoutInflater inflater = LayoutInflater.from(context);
            toastView = inflater.inflate(R.layout.overlay_toast, null);
            if (toastView == null) {
                PopupActivity.show(context, heading + "\n" + message, isDanger);
                return;
            }

            TextView tvHeading = toastView.findViewById(R.id.tvHeading);
            TextView tvMessage = toastView.findViewById(R.id.tvPopupMessage);

            if (tvHeading == null || tvMessage == null) {
                tvMessage = toastView.findViewById(R.id.tvPopupMessage);
                if (tvMessage != null) {
                    tvMessage.setText(heading + "\n" + message);
                } else {
                    PopupActivity.show(context, heading + "\n" + message, isDanger);
                    return;
                }
            } else {
                tvHeading.setText(heading);
                tvMessage.setText(message);
            }

            if (isDanger) {
                toastView.setBackgroundColor(Color.RED);
                if (tvHeading != null) tvHeading.setTextColor(Color.WHITE);
                tvMessage.setTextColor(Color.WHITE);
            } else {
                toastView.setBackgroundColor(Color.GREEN);
                if (tvHeading != null) tvHeading.setTextColor(Color.BLACK);
                tvMessage.setTextColor(Color.BLACK);
            }

            int layoutType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ?
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                    WindowManager.LayoutParams.TYPE_PHONE;

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    android.graphics.PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            params.y = 0;

            windowManager.addView(toastView, params);
            Log.d(TAG, "Overlay toast shown");

            handler.postDelayed(() -> {
                if (toastView != null && windowManager != null) {
                    try {
                        windowManager.removeView(toastView);
                        toastView = null;
                    } catch (Exception ignored) {}
                }
            }, durationMs);

        } catch (Exception e) {
            Log.e(TAG, "Overlay failed", e);
            // 🔥 Ultimate fallback: PopupActivity
            PopupActivity.show(context, heading + "\n" + message, isDanger);
        }
    }
}