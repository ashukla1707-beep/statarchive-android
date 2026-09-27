package com.statarchive.app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY_MS = 900L;
    private final Handler launchHandler = new Handler(Looper.getMainLooper());
    private final Runnable launchArchive = () -> {
        if (isFinishing() || isDestroyed()) return;
        startActivity(new Intent(SplashActivity.this, VerifiedMainActivity.class));
        overridePendingTransition(0, 0);
        finish();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(Color.BLACK);
        window.setNavigationBarColor(Color.BLACK);
        window.setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        );

        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        ImageView splashImage = new ImageView(this);
        splashImage.setImageResource(R.drawable.splash_exact);
        splashImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        splashImage.setAdjustViewBounds(false);
        splashImage.setBackgroundColor(Color.BLACK);

        root.addView(
                splashImage,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );

        setContentView(root);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                // Ignore Back during the short launch transition.
            }
        });
        launchHandler.postDelayed(launchArchive, SPLASH_DELAY_MS);
    }

    @Override
    protected void onDestroy() {
        launchHandler.removeCallbacks(launchArchive);
        super.onDestroy();
    }
}
