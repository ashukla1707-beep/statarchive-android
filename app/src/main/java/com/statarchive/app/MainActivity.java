package com.statarchive.app;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();

        /* Website functionality */
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        /*
         * IMPORTANT FOR RESPONSIVE MOBILE LAYOUT
         *
         * Your website already contains:
         * <meta name="viewport"
         *       content="width=device-width, initial-scale=1.0">
         *
         * These settings allow WebView to respect that viewport
         * instead of shrinking a desktop-width page.
         */
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);

        /* Keep normal Android/mobile scaling */
        settings.setTextZoom(100);
        settings.setDefaultZoom(WebSettings.ZoomDensity.MEDIUM);

        /* Do not scale the entire desktop page down */
        webView.setInitialScale(0);

        /* Disable manual browser-style zoom controls */
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        /*
         * IMPORTANT:
         * Do NOT replace the user agent with a desktop user agent.
         * Android WebView's normal user agent already contains "Mobile".
         */
        settings.setUserAgentString(settings.getUserAgentString());

        webView.setWebViewClient(new WebViewClient());

        /*
         * Android Back button / gesture:
         * go back inside StatArchive first.
         */
        getOnBackPressedDispatcher().addCallback(
            this,
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {

                    if (webView.canGoBack()) {
                        webView.goBack();
                    } else {
                        finish();
                    }
                }
            }
        );

        webView.loadUrl(
            "https://stat-archive.lustats.workers.dev/"
        );
    }

    @Override
    protected void onDestroy() {

        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }
}
