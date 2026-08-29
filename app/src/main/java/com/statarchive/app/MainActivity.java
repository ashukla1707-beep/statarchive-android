package com.statarchive.app;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

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

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(7, 10, 15));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(7, 10, 15));

        root.addView(
                webView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        setContentView(root);

        ViewCompat.setOnApplyWindowInsetsListener(
                root,
                (view, windowInsets) -> {

                    Insets bars = windowInsets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                    );

                    view.setPadding(
                            bars.left,
                            bars.top,
                            bars.right,
                            bars.bottom
                    );

                    return windowInsets;
                }
        );

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);

        settings.setTextZoom(100);

        webView.setInitialScale(0);

        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        settings.setUserAgentString(
                settings.getUserAgentString()
        );

        webView.setWebViewClient(
                new WebViewClient() {

                    @Override
                    public void onPageFinished(
                            WebView view,
                            String url
                    ) {
                        super.onPageFinished(view, url);

                        view.evaluateJavascript(
        "(function() {" +
        "  document.documentElement.classList.add('stat-archive-pwa');" +

        "  function finishStatArchiveCurve() {" +
        "    var curve = document.querySelector('.gaussian-curve');" +
        "    if (!curve) return;" +

        "    curve.style.setProperty('animation', 'none', 'important');" +
        "    curve.style.setProperty('transition', 'none', 'important');" +
        "    curve.style.setProperty('stroke-dashoffset', '0', 'important');" +
        "    curve.style.setProperty('opacity', '1', 'important');" +
        "  }" +

        "  setTimeout(finishStatArchiveCurve, 3800);" +
        "  setTimeout(finishStatArchiveCurve, 5000);" +
        "})();",
        null
);
                    }
                }
        );

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
