package com.statarchive.app;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);

        webView.setBackgroundColor(Color.rgb(7, 10, 15));

        setContentView(webView);

        /*
         * IMPORTANT:
         * Keep StatArchive below Android's status bar
         * and above the navigation/gesture bar.
         */
        ViewCompat.setOnApplyWindowInsetsListener(webView, (view, windowInsets) -> {

            Insets systemBars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
            );

            view.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    systemBars.bottom
            );

            return windowInsets;
        });

        WebSettings settings = webView.getSettings();

        /* Website functionality */
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        /* Use StatArchive's real responsive mobile layout */
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);

        settings.setTextZoom(100);

        /* Don't shrink the desktop layout */
        webView.setInitialScale(0);

        /* Disable browser-style zoom controls */
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        /*
         * Keep Android WebView's normal mobile user-agent.
         * Do not substitute a desktop UA.
         */
        settings.setUserAgentString(
                settings.getUserAgentString()
        );

        webView.setWebViewClient(new WebViewClient());

        /*
         * Android Back button / gesture
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
