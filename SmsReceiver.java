package com.example.otpfrauddetection;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.Toast;

public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "OTP_FRAUD";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            if (intent == null) return;
            if (!"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) return;

            Bundle bundle = intent.getExtras();
            if (bundle == null) return;

            Object[] pdus = (Object[]) bundle.get("pdus");
            if (pdus == null) return;

            for (Object pdu : pdus) {
                SmsMessage smsMessage;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    String format = bundle.getString("format");
                    smsMessage = SmsMessage.createFromPdu((byte[]) pdu, format);
                } else {
                    smsMessage = SmsMessage.createFromPdu((byte[]) pdu);
                }
                String body = smsMessage.getMessageBody();
                if (body != null) {
                    // 🔥 Debug: confirm SMS received
                    Toast.makeText(context, "📩 SMS received!", Toast.LENGTH_SHORT).show();

                    Intent serviceIntent = new Intent(context, DetectionService.class);
                    serviceIntent.putExtra("sms_body", body);
                    context.startService(serviceIntent);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "SMS receive error", e);
        }
    }
}