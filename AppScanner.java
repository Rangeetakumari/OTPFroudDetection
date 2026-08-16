package com.example.otpfrauddetection;

public class AppScanner {
    public static boolean isBankingApp(String packageName) {
        if (packageName == null) return false;
        for (String pkg : Constants.BANKING_APPS) {
            if (pkg.equals(packageName)) return true;
        }
        return false;
    }
}