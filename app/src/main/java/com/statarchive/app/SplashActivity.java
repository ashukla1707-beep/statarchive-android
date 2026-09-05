package com.statarchive.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY_MS = 900L;

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

        StatArchiveSplashView splash = new StatArchiveSplashView(this);
        root.addView(
                splash,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );

        setContentView(root);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            overridePendingTransition(0, 0);
            finish();
        }, SPLASH_DELAY_MS);
    }

    @Override
    public void onBackPressed() {
        // Ignore Back while the launch splash is visible.
    }

    /**
     * Resolution-independent recreation of the supplied Stat Archive splash.
     * The outer cyan circle/frame is intentionally omitted. Drawing directly on
     * Canvas keeps the logo crisp at every Android density and avoids raster
     * resampling artifacts on high-resolution phones.
     */
    private static final class StatArchiveSplashView extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
        private final Typeface medium = Typeface.create("sans-serif", Typeface.NORMAL);

        StatArchiveSplashView(Context context) {
            super(context);
            setBackgroundColor(Color.BLACK);
            // BlurMaskFilter glow needs software rendering, but this view exists
            // for only 900 ms and remains lightweight.
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            final float w = getWidth();
            final float h = getHeight();
            if (w <= 0 || h <= 0) return;

            // Work in a 1080 x 1920 portrait design space and scale uniformly.
            final float scale = Math.min(w / 1080f, h / 1920f);
            final float ox = (w - 1080f * scale) / 2f;
            final float oy = (h - 1920f * scale) / 2f;

            canvas.save();
            canvas.translate(ox, oy);
            canvas.scale(scale, scale);

            final int cyan = Color.rgb(0, 229, 239);
            final int cyanDeep = Color.rgb(0, 145, 160);
            final int white = Color.rgb(250, 252, 255);
            final int pageGray = Color.rgb(214, 218, 222);
            final int darkGray = Color.rgb(105, 112, 118);

            // Subtle logo glow without an enclosing border/circle.
            glowPaint.setStyle(Paint.Style.FILL);
            glowPaint.setColor(Color.argb(110, 0, 225, 240));
            glowPaint.setMaskFilter(new BlurMaskFilter(28f, BlurMaskFilter.Blur.NORMAL));
            canvas.drawOval(215, 720, 865, 1015, glowPaint);
            glowPaint.clearShadowLayer();
            glowPaint.setMaskFilter(null);

            // Rising cyan bars.
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(
                    0, 510, 0, 920,
                    Color.rgb(0, 240, 245),
                    Color.rgb(0, 126, 142),
                    Shader.TileMode.CLAMP
            ));
            canvas.drawRect(387, 705, 445, 905, paint);
            canvas.drawRect(465, 650, 523, 905, paint);
            canvas.drawRect(543, 588, 601, 905, paint);
            canvas.drawRect(621, 520, 679, 905, paint);
            canvas.drawRect(699, 440, 757, 905, paint);
            paint.setShader(null);

            // Upward trend line and data points.
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(11f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(white);
            path.reset();
            path.moveTo(305, 730);
            path.cubicTo(350, 675, 392, 645, 432, 625);
            path.cubicTo(480, 600, 520, 575, 560, 545);
            path.cubicTo(614, 505, 652, 455, 700, 390);
            canvas.drawPath(path, paint);

            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(390, 650, 16, paint);
            canvas.drawCircle(470, 605, 16, paint);
            canvas.drawCircle(560, 545, 16, paint);
            canvas.drawCircle(650, 455, 16, paint);

            // Arrow head.
            path.reset();
            path.moveTo(683, 365);
            path.lineTo(753, 335);
            path.lineTo(738, 405);
            path.close();
            canvas.drawPath(path, paint);

            // Open book - back/dark page layer.
            paint.setColor(darkGray);
            path.reset();
            path.moveTo(175, 885);
            path.cubicTo(305, 842, 430, 855, 540, 955);
            path.cubicTo(650, 855, 775, 842, 905, 885);
            path.lineTo(842, 1015);
            path.cubicTo(720, 985, 625, 1005, 540, 1080);
            path.cubicTo(455, 1005, 360, 985, 238, 1015);
            path.close();
            canvas.drawPath(path, paint);

            // Main white pages.
            paint.setColor(white);
            path.reset();
            path.moveTo(195, 845);
            path.cubicTo(318, 805, 438, 820, 540, 925);
            path.lineTo(540, 1038);
            path.cubicTo(440, 944, 350, 914, 205, 948);
            path.lineTo(155, 920);
            path.close();
            canvas.drawPath(path, paint);

            path.reset();
            path.moveTo(885, 845);
            path.cubicTo(762, 805, 642, 820, 540, 925);
            path.lineTo(540, 1038);
            path.cubicTo(640, 944, 730, 914, 875, 948);
            path.lineTo(925, 920);
            path.close();
            canvas.drawPath(path, paint);

            // Page layers for depth.
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(5f);
            paint.setColor(pageGray);
            for (int i = 0; i < 5; i++) {
                float y = 960 + i * 17;
                path.reset();
                path.moveTo(225, y);
                path.cubicTo(330, y - 23, 430, y - 5, 525, y + 62);
                canvas.drawPath(path, paint);
                path.reset();
                path.moveTo(855, y);
                path.cubicTo(750, y - 23, 650, y - 5, 555, y + 62);
                canvas.drawPath(path, paint);
            }

            // Center crease.
            paint.setColor(Color.rgb(58, 63, 68));
            paint.setStrokeWidth(7f);
            canvas.drawLine(540, 923, 540, 1042, paint);

            // Cyan bookmark.
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(cyan);
            path.reset();
            path.moveTo(730, 900);
            path.lineTo(777, 900);
            path.lineTo(777, 1028);
            path.lineTo(753, 1007);
            path.lineTo(730, 1028);
            path.close();
            canvas.drawPath(path, paint);

            // STAT ARCHIVE wordmark.
            paint.setTypeface(bold);
            paint.setTextSize(117f);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(
                    95, 1115, 430, 1235,
                    Color.rgb(0, 242, 244),
                    Color.rgb(0, 176, 197),
                    Shader.TileMode.CLAMP
            ));
            canvas.drawText("STAT", 86, 1235, paint);
            paint.setShader(null);
            paint.setColor(white);
            canvas.drawText("ARCHIVE", 438, 1235, paint);

            // Tagline.
            paint.setTypeface(medium);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(45f);
            paint.setLetterSpacing(0.22f);
            paint.setColor(cyan);
            canvas.drawText("LEARN • ANALYZE • GROW", 540, 1320, paint);
            paint.setLetterSpacing(0f);

            canvas.restore();
        }
    }
}
