package com.statarchive.app;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * Launch wrapper that keeps the existing Stat Archive splash on screen while
 * the live WebView is loading. This removes the dark/blank frame between the
 * native splash and the first rendered web page without changing the WebView
 * client, bridge, navigation, or verification behavior.
 */
public class LaunchMainActivity extends VerifiedMainActivity {

    private static final String SITE_PREFIX = "https://stat-archive.lustats.workers.dev";

    private Handler launchHandler;
    private View launchCover;

    private final Runnable readyCheck = new Runnable() {
        @Override
        public void run() {
            WebView webView = findWebView(getWindow().getDecorView());
            boolean ready = webView != null
                    && webView.getProgress() >= 85
                    && webView.getUrl() != null
                    && webView.getUrl().startsWith(SITE_PREFIX);

            if (ready) {
                removeLaunchCover();
                return;
            }

            if (launchHandler != null && !isFinishing() && !isDestroyed()) {
                launchHandler.postDelayed(this, 60L);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        installLaunchCover();
    }

    private void installLaunchCover() {
        ViewGroup content = findViewById(android.R.id.content);
        if (content == null) {
            return;
        }

        FrameLayout cover = new FrameLayout(this);
        cover.setBackgroundColor(Color.BLACK);
        cover.setClickable(true);
        cover.setFocusable(true);

        ImageView image = new ImageView(this);
        image.setImageResource(R.drawable.splash_exact);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setAdjustViewBounds(false);
        image.setBackgroundColor(Color.BLACK);

        cover.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        content.addView(cover, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        launchCover = cover;
        launchHandler = new Handler(Looper.getMainLooper());
        launchHandler.post(readyCheck);
    }

    private void removeLaunchCover() {
        if (launchHandler != null) {
            launchHandler.removeCallbacks(readyCheck);
        }

        View cover = launchCover;
        if (cover == null) {
            return;
        }

        if (cover.getParent() instanceof ViewGroup) {
            ((ViewGroup) cover.getParent()).removeView(cover);
        }
        launchCover = null;
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

    @Override
    protected void onDestroy() {
        if (launchHandler != null) {
            launchHandler.removeCallbacksAndMessages(null);
        }
        launchCover = null;
        super.onDestroy();
    }
}
