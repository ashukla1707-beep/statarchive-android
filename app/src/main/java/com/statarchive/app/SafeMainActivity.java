package com.statarchive.app;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import androidx.activity.OnBackPressedCallback;

/**
 * Thin safety wrapper around the existing MainActivity.
 *
 * MainActivity intentionally remains unchanged so all established WebView,
 * scanner, file-picker and update behavior stays intact. This wrapper owns
 * only the Android Back edge case that previously hid Preview without calling
 * the web reader's cleanup routine.
 */
public class SafeMainActivity extends MainActivity {

    private static final String CLOSE_PREVIEW_IF_OPEN_JS =
            "(function(){try{" +
            "var el=document.getElementById('previewOverlay');" +
            "if(!el)return false;" +
            "var s=window.getComputedStyle(el);" +
            "if(s.display==='none'||s.visibility==='hidden')return false;" +
            "if(typeof window.closePreview==='function'){window.closePreview();return true;}" +
            "return false;" +
            "}catch(e){return false;}})();";

    private OnBackPressedCallback cleanupAwareBackCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        cleanupAwareBackCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                WebView webView = findWebView(getWindow().getDecorView());
                if (webView == null) {
                    delegateToMainActivityBackHandler();
                    return;
                }

                webView.evaluateJavascript(CLOSE_PREVIEW_IF_OPEN_JS, value -> {
                    if ("true".equals(value)) {
                        return;
                    }
                    delegateToMainActivityBackHandler();
                });
            }
        };

        /* Added after MainActivity's callback, so this one runs first. If it
           does not handle Preview it temporarily disables itself and delegates
           to MainActivity's established overlay/history behavior. */
        getOnBackPressedDispatcher().addCallback(this, cleanupAwareBackCallback);
    }

    private void delegateToMainActivityBackHandler() {
        if (cleanupAwareBackCallback == null) {
            finish();
            return;
        }

        cleanupAwareBackCallback.setEnabled(false);
        try {
            getOnBackPressedDispatcher().onBackPressed();
        } finally {
            if (!isFinishing() && !isDestroyed()) {
                cleanupAwareBackCallback.setEnabled(true);
            }
        }
    }

    private WebView findWebView(View view) {
        if (view instanceof WebView) {
            return (WebView) view;
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                WebView found = findWebView(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }
}
