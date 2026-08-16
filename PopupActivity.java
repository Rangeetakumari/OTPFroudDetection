package com.example.otpfrauddetection;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

public class PopupActivity extends Activity {
    private static final String EXTRA_MESSAGE = "message";
    private static final String EXTRA_IS_DANGER = "is_danger";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_popup);

        Window window = getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(android.view.Gravity.BOTTOM);
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }

        String message = getIntent().getStringExtra(EXTRA_MESSAGE);
        boolean isDanger = getIntent().getBooleanExtra(EXTRA_IS_DANGER, false);

        TextView tvMessage = findViewById(R.id.tvPopupMessage);
        tvMessage.setText(message);

        if (isDanger) {
            findViewById(R.id.popupContainer).setBackgroundColor(android.graphics.Color.RED);
            tvMessage.setTextColor(android.graphics.Color.WHITE);
        } else {
            findViewById(R.id.popupContainer).setBackgroundColor(android.graphics.Color.GREEN);
            tvMessage.setTextColor(android.graphics.Color.BLACK);
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!isFinishing()) finish();
        }, 4000);
    }

    public static void show(Context context, String message, boolean isDanger) {
        Intent intent = new Intent(context, PopupActivity.class);
        intent.putExtra(EXTRA_MESSAGE, message);
        intent.putExtra(EXTRA_IS_DANGER, isDanger);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
    }
}