package com.example.otpfrauddetection;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

public class AppOpenReceiver extends BroadcastReceiver {
    private static final String TAG = "OTP_FRAUD";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            if (intent.getAction() != null && intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED")) {
                Bundle bundle = intent.getExtras();
                if (bundle != null) {
                    Object[] pdus = (Object[]) bundle.get("pdus");
                    if (pdus != null) {
                        for (Object pdu : pdus) {
                            SmsMessage smsMessage;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                String format = bundle.getString("format");
                                smsMessage = SmsMessage.createFromPdu((byte[]) pdu, format);
                            } else {
                                smsMessage = SmsMessage.createFromPdu((byte[]) pdu);
                            }
                            String messageBody = smsMessage.getMessageBody();
                            if (messageBody != null) {
                                Intent serviceIntent = new Intent(context, DetectionService.class);
                                serviceIntent.putExtra("sms_body", messageBody);
                                context.startService(serviceIntent);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "SMS receive error", e);
        }
    }
}