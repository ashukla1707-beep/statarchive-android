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

        // Required by StatArchive
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        // IMPORTANT: use normal mobile responsive layout
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);

        // Prevent Android WebView from behaving like a desktop browser
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        // Normal mobile WebView user agent
        String userAgent = settings.getUserAgentString();
        userAgent = userAgent.replace("; wv", "");
        settings.setUserAgentString(userAgent);

        webView.setWebViewClient(new WebViewClient());

        // Handle Android back gesture/button inside the website
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

        webView.loadUrl("https://stat-archive.lustats.workers.dev/");
    }
}
