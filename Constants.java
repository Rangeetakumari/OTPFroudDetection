package com.example.otpfrauddetection;

import java.util.Arrays;
import java.util.List;

public class Constants {
    public static final List<String> BANKING_APPS = Arrays.asList(
            "com.phonepe.app",
            "com.google.android.apps.nbu.paisa.user",
            "in.org.npci.upiapp",
            "com.paytm.app",
            "com.sbi.SBIPAY",
            "com.bankofbaroda.mobile",
            "com.hdfc.bank",
            "com.icici.bank",
            "com.yfs.android.pockets"
    );

    public static final List<String> BANKING_KEYWORDS = Arrays.asList(
            "SBI", "BOB", "BOI", "PHONEPAY", "GOOGLE PAY", "BHIM", "PAYTM",
            "UPI", "HDFC", "ICICI", "AXIS", "CANARA", "UNION", "KOTAK",
            "YES BANK", "IDBI", "PNB", "CITI", "HSBC", "AMEX", "DBS"
    );

    public static final List<String> OTP_KEYWORDS = Arrays.asList(
            "OTP", "AUTH", "VERIFICATION", "CODE"
    );
}