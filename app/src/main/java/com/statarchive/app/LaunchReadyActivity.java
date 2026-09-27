package com.statarchive.app;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.ImageView;

import androidx.core.splashscreen.SplashScreen;

/**
 * Single launch activity that bridges Android's system splash to the loaded app.
 *
 * The native splash artwork stays above the WebView while the Stat Archive page
 * loads underneath it. The overlay is removed only after the website publishes
 * data-stat-startup-ready="1", so users never see the empty WebView/black gap or
 * the home counters populating after launch.
 *
 * The website hero may already animate while hidden. Before revealing the page,
 * Android swaps in a fresh hero SVG frozen at frame zero. Only after the splash
 * has completely disappeared is the existing hero-animation script reloaded, so
 * the user sees the graph animation from its true first frame with no reset flash.
 */
public class LaunchReadyActivity extends VerifiedMainActivity {

    private static final long READY_POLL_MS = 80L;
    private static final long MIN_SPLASH_VISIBLE_MS = 700L;
    private static final long FAILSAFE_MS = 10_000L;

    private static final String PREPARE_HERO_JS =
            "(function(){try{" +
            "var hero=document.querySelector('.hero-probability');" +
            "var oldSvg=hero&&hero.querySelector('.probability-svg');" +
            "if(!hero||!oldSvg)return false;" +
            "var fresh=oldSvg.cloneNode(true);" +
            "var curve=fresh.querySelector('.gaussian-curve');" +
            "if(curve){" +
            "var len=1000;try{var measured=curve.getTotalLength();if(isFinite(measured)&&measured>1)len=measured;}catch(_e){}" +
            "curve.style.setProperty('animation','none','important');" +
            "curve.style.setProperty('transition','none','important');" +
            "curve.style.setProperty('stroke-dasharray',len+' '+len,'important');" +
            "curve.style.setProperty('stroke-dashoffset',String(len),'important');" +
            "curve.style.setProperty('opacity','1','important');" +
            "}" +
            "fresh.querySelectorAll('.data-dot').forEach(function(dot){" +
            "dot.style.setProperty('opacity','0','important');" +
            "dot.style.setProperty('animation','none','important');" +
            "dot.style.setProperty('transition','none','important');" +
            "dot.style.setProperty('transform','translateY(0px)','important');" +
            "});" +
            "oldSvg.replaceWith(fresh);" +
            "window.__STAT_ARCHIVE_HERO_ANIMATION_V3__=false;" +
            "var guard=document.getElementById('statHeroDotPreStartGuard');" +
            "if(guard)guard.remove();" +
            "return true;" +
            "}catch(e){return false;}})();";

    private static final String START_HERO_JS =
            "(function(){try{" +
            "var old=document.querySelector('script[data-native-hero-replay]');" +
            "if(old)old.remove();" +
            "var script=document.createElement('script');" +
            "script.src='assets/js/hero-animation.js?native-replay='+Date.now();" +
            "script.async=false;" +
            "script.dataset.nativeHeroReplay='1';" +
            "document.body.appendChild(script);" +
            "return true;" +
            "}catch(e){return false;}})();";

    private final Handler launchHandler = new Handler(Looper.getMainLooper());

    private ImageView launchOverlay;
    private WebView launchWebView;
    private long createdAtMs;
    private boolean launchOverlayDismissed;

    private final Runnable readyPoll = new Runnable() {
        @Override
        public void run() {
            if (launchOverlayDismissed || isFinishing() || isDestroyed()) {
                return;
            }

            long elapsed = SystemClock.uptimeMillis() - createdAtMs;
            if (elapsed >= FAILSAFE_MS) {
                beginRevealSequence();
                return;
            }

            WebView webView = launchWebView;
            if (webView == null) {
                launchWebView = findWebView(getWindow().getDecorView());
                scheduleNextPoll();
                return;
            }

            webView.evaluateJavascript(
                    "(function(){try{return document.documentElement.dataset.statStartupReady==='1';}catch(e){return false;}})();",
                    value -> {
                        if (launchOverlayDismissed || isFinishing() || isDestroyed()) {
                            return;
                        }

                        if ("true".equals(value)) {
                            long nowElapsed = SystemClock.uptimeMillis() - createdAtMs;
                            long remaining = MIN_SPLASH_VISIBLE_MS - nowElapsed;
                            if (remaining > 0L) {
                                launchHandler.postDelayed(
                                        LaunchReadyActivity.this::beginRevealSequence,
                                        remaining
                                );
                            } else {
                                beginRevealSequence();
                            }
                        } else {
                            scheduleNextPoll();
                        }
                    }
            );
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        createdAtMs = SystemClock.uptimeMillis();
        installLaunchOverlay();
        launchWebView = findWebView(getWindow().getDecorView());
        launchHandler.post(readyPoll);
    }

    private void installLaunchOverlay() {
        ViewGroup content = findViewById(android.R.id.content);
        if (content == null) {
            return;
        }

        launchOverlay = new ImageView(this);
        launchOverlay.setImageResource(R.drawable.splash_exact);
        launchOverlay.setScaleType(ImageView.ScaleType.FIT_CENTER);
        launchOverlay.setBackgroundColor(Color.BLACK);
        launchOverlay.setClickable(true);
        launchOverlay.setFocusable(true);

        content.addView(
                launchOverlay,
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );
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

    private void scheduleNextPoll() {
        launchHandler.removeCallbacks(readyPoll);
        launchHandler.postDelayed(readyPoll, READY_POLL_MS);
    }

    private void beginRevealSequence() {
        if (launchOverlayDismissed) {
            return;
        }
        launchOverlayDismissed = true;
        launchHandler.removeCallbacksAndMessages(null);

        restoreNormalSystemBars();

        WebView webView = launchWebView;
        if (webView == null) {
            webView = findWebView(getWindow().getDecorView());
            launchWebView = webView;
        }

        if (webView == null) {
            fadeSplashThenStartHero();
            return;
        }

        // Freeze the visible hero at frame zero while it is still fully covered.
        // Reveal only after the DOM replacement callback confirms completion.
        webView.evaluateJavascript(PREPARE_HERO_JS, ignored -> {
            View decor = getWindow().getDecorView();
            decor.postOnAnimation(this::fadeSplashThenStartHero);
        });
    }

    private void fadeSplashThenStartHero() {
        ImageView overlay = launchOverlay;
        if (overlay == null) {
            startHeroAnimation();
            return;
        }

        overlay.animate()
                .alpha(0f)
                .setDuration(120L)
                .withEndAction(() -> {
                    ViewGroup parent = (ViewGroup) overlay.getParent();
                    if (parent != null) {
                        parent.removeView(overlay);
                    }
                    launchOverlay = null;

                    // The page is now visible with the graph frozen at frame zero.
                    // Start the animation on the next display frame.
                    getWindow().getDecorView().postOnAnimation(this::startHeroAnimation);
                })
                .start();
    }

    private void startHeroAnimation() {
        WebView webView = launchWebView;
        if (webView == null) {
            webView = findWebView(getWindow().getDecorView());
            launchWebView = webView;
        }
        if (webView != null) {
            webView.evaluateJavascript(START_HERO_JS, null);
        }
    }

    private void restoreNormalSystemBars() {
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        window.setStatusBarColor(Color.rgb(7, 10, 15));
        window.setNavigationBarColor(Color.rgb(7, 10, 15));

        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
                && window.getInsetsController() != null) {
            window.getInsetsController().show(
                    WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars()
            );
        }
        window.getDecorView().requestApplyInsets();
    }

    @Override
    protected void onDestroy() {
        launchHandler.removeCallbacksAndMessages(null);
        launchOverlay = null;
        launchWebView = null;
        super.onDestroy();
    }
}
