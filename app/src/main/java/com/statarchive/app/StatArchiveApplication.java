package com.statarchive.app;

import android.app.Application;
import android.os.Build;
import android.webkit.WebView;

public class StatArchiveApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        /*
         * v1.5.12 uses a fresh WebView data directory so an old/corrupted
         * service-worker + CacheStorage state from earlier preview/theme
         * experiments cannot keep overriding the restored v58 viewer.
         *
         * This does not delete the old profile; it simply isolates this
         * release in a clean profile. Android requires this call before the
         * first WebView instance is created.
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                WebView.setDataDirectorySuffix("statarchive_v1512");
            } catch (Throwable ignored) {
            }
        }
    }
}
