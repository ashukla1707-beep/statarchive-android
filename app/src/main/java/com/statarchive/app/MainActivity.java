package com.statarchive.app;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private WebView webView;

    private static final String SITE_HOST =
            "stat-archive.lustats.workers.dev";


    @SuppressLint({
            "SetJavaScriptEnabled",
            "JavascriptInterface"
    })
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        /* =====================================================
           ROOT CONTAINER
           ===================================================== */

        FrameLayout root = new FrameLayout(this);

        root.setBackgroundColor(
                Color.rgb(7, 10, 15)
        );


        /* =====================================================
           WEBVIEW
           ===================================================== */

        webView = new WebView(this);

        webView.setBackgroundColor(
                Color.rgb(7, 10, 15)
        );

        root.addView(
                webView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        setContentView(root);


        /* =====================================================
           ANDROID STATUS / NAVIGATION BAR SAFE AREA
           ===================================================== */

        ViewCompat.setOnApplyWindowInsetsListener(
                root,
                (view, windowInsets) -> {

                    Insets bars =
                            windowInsets.getInsets(
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


        /* =====================================================
           WEBVIEW SETTINGS
           ===================================================== */

        WebSettings settings =
                webView.getSettings();

        settings.setJavaScriptEnabled(true);

        settings.setDomStorageEnabled(true);

        /*
         * Let the website use its real responsive
         * phone layout.
         */
        settings.setUseWideViewPort(true);

        settings.setLoadWithOverviewMode(false);

        settings.setTextZoom(100);

        webView.setInitialScale(0);


        /* Disable browser-style zoom controls */

        settings.setSupportZoom(false);

        settings.setBuiltInZoomControls(false);

        settings.setDisplayZoomControls(false);


        /*
         * Keep the normal Android mobile
         * WebView user agent.
         */
        settings.setUserAgentString(
                settings.getUserAgentString()
        );


        /* =====================================================
           NATIVE ANDROID FILE BRIDGE

           JavaScript will call:

           AndroidBridge.openFile(...)
           AndroidBridge.shareFile(...)
           ===================================================== */

        webView.addJavascriptInterface(
                new AndroidFileBridge(),
                "AndroidBridge"
        );


        /* =====================================================
           WEBVIEW CLIENT
           ===================================================== */

        webView.setWebViewClient(
                new WebViewClient() {


                    /*
                     * Keep Stat Archive links inside the app.
                     *
                     * External links are handed to Android.
                     */
                    @Override
                    public boolean shouldOverrideUrlLoading(
                            WebView view,
                            WebResourceRequest request
                    ) {

                        Uri uri = request.getUrl();

                        String host = uri.getHost();

                        if (
                                host != null &&
                                (
                                        host.equals(SITE_HOST) ||
                                        host.endsWith("." + SITE_HOST)
                                )
                        ) {

                            return false;
                        }


                        try {

                            Intent intent =
                                    new Intent(
                                            Intent.ACTION_VIEW,
                                            uri
                                    );

                            startActivity(intent);

                        } catch (Exception ignored) {
                        }

                        return true;
                    }


                    @Override
                    public void onPageFinished(
                            WebView view,
                            String url
                    ) {

                        super.onPageFinished(
                                view,
                                url
                        );


                        /*
                         * 1. Mark WebView as installed-app mode
                         *    so Offline Library is visible.
                         *
                         * 2. Give the normal graph animation time
                         *    to run.
                         *
                         * 3. If Android WebView leaves it halfway,
                         *    force the bell curve into its completed
                         *    state.
                         */

                        view.evaluateJavascript(

                                "(function() {" +

                                "document.documentElement" +
                                ".classList.add(" +
                                "'stat-archive-pwa'" +
                                ");" +


                                "function finishCurve() {" +

                                "var c = document.querySelector(" +
                                "'.gaussian-curve'" +
                                ");" +

                                "if (!c) return;" +

                                "c.style.setProperty(" +
                                "'animation'," +
                                "'none'," +
                                "'important'" +
                                ");" +

                                "c.style.setProperty(" +
                                "'transition'," +
                                "'none'," +
                                "'important'" +
                                ");" +

                                "c.style.setProperty(" +
                                "'stroke-dashoffset'," +
                                "'0'," +
                                "'important'" +
                                ");" +

                                "c.style.setProperty(" +
                                "'opacity'," +
                                "'1'," +
                                "'important'" +
                                ");" +

                                "}" +


                                "setTimeout(" +
                                "finishCurve," +
                                "3800" +
                                ");" +

                                "setTimeout(" +
                                "finishCurve," +
                                "5000" +
                                ");" +

                                "})();",

                                null
                        );
                    }
                }
        );


        /* =====================================================
           ANDROID BACK BUTTON
           ===================================================== */

        getOnBackPressedDispatcher()
                .addCallback(
                        this,
                        new OnBackPressedCallback(true) {

                            @Override
                            public void handleOnBackPressed() {

                                if (
                                        webView != null &&
                                        webView.canGoBack()
                                ) {

                                    webView.goBack();

                                } else {

                                    finish();
                                }
                            }
                        }
                );


        /* =====================================================
           LOAD STAT ARCHIVE
           ===================================================== */

        webView.loadUrl(
                "https://stat-archive.lustats.workers.dev/"
        );
    }


    /* =========================================================
       ANDROID FILE BRIDGE
       ========================================================= */

    private class AndroidFileBridge {


        /*
         * OPEN OFFLINE FILE
         */
        @JavascriptInterface
        public void openFile(
                String base64Data,
                String filename,
                String mimeType
        ) {

            runOnUiThread(() -> {

                try {

                    File file =
                            createSharedFile(
                                    base64Data,
                                    filename
                            );

                    Uri uri =
                            FileProvider.getUriForFile(
                                    MainActivity.this,
                                    getPackageName()
                                            + ".fileprovider",
                                    file
                            );


                    String mime =
                            normalizeMime(
                                    mimeType
                            );


                    Intent intent =
                            new Intent(
                                    Intent.ACTION_VIEW
                            );

                    intent.setDataAndType(
                            uri,
                            mime
                    );

                    intent.addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );


                    try {

                        startActivity(intent);

                    } catch (
                            ActivityNotFoundException e
                    ) {

                        Toast.makeText(
                                MainActivity.this,
                                "No app is available to open this file.",
                                Toast.LENGTH_LONG
                        ).show();
                    }


                } catch (Exception e) {

                    Toast.makeText(
                            MainActivity.this,
                            "Couldn't open the offline file.",
                            Toast.LENGTH_LONG
                    ).show();
                }
            });
        }


        /*
         * SHARE OFFLINE FILE
         */
        @JavascriptInterface
        public void shareFile(
                String base64Data,
                String filename,
                String mimeType
        ) {

            runOnUiThread(() -> {

                try {

                    File file =
                            createSharedFile(
                                    base64Data,
                                    filename
                            );


                    Uri uri =
                            FileProvider.getUriForFile(
                                    MainActivity.this,
                                    getPackageName()
                                            + ".fileprovider",
                                    file
                            );


                    String mime =
                            normalizeMime(
                                    mimeType
                            );


                    Intent intent =
                            new Intent(
                                    Intent.ACTION_SEND
                            );

                    intent.setType(mime);

                    intent.putExtra(
                            Intent.EXTRA_STREAM,
                            uri
                    );

                    intent.addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );


                    Intent chooser =
                            Intent.createChooser(
                                    intent,
                                    "Share file"
                            );


                    startActivity(chooser);


                } catch (Exception e) {

                    Toast.makeText(
                            MainActivity.this,
                            "Couldn't share the offline file.",
                            Toast.LENGTH_LONG
                    ).show();
                }
            });
        }
    }


    /* =========================================================
       WRITE OFFLINE BLOB TO TEMPORARY ANDROID FILE
       ========================================================= */

    private File createSharedFile(
            String base64Data,
            String filename
    ) throws IOException {


        /*
         * Prevent unsafe file names such as:
         *
         * ../../something
         */

        String safeName =
                sanitizeFilename(
                        filename
                );


        File sharedDirectory =
                new File(
                        getCacheDir(),
                        "shared"
                );


        if (
                !sharedDirectory.exists() &&
                !sharedDirectory.mkdirs()
        ) {

            throw new IOException(
                    "Could not create shared directory."
            );
        }


        File file =
                new File(
                        sharedDirectory,
                        safeName
                );


        byte[] bytes =
                Base64.decode(
                        base64Data,
                        Base64.DEFAULT
                );


        try (
                FileOutputStream output =
                        new FileOutputStream(file)
        ) {

            output.write(bytes);

            output.flush();
        }


        return file;
    }


    /* =========================================================
       SAFE FILE NAME
       ========================================================= */

    private String sanitizeFilename(
            String filename
    ) {

        if (
                filename == null ||
                filename.trim().isEmpty()
        ) {

            return "statarchive-file";
        }


        String clean =
                filename.replaceAll(
                        "[\\\\/:*?\"<>|]",
                        "_"
                );


        clean =
                clean.replace(
                        "..",
                        "_"
                );


        return clean;
    }


    /* =========================================================
       MIME FALLBACK
       ========================================================= */

    private String normalizeMime(
            String mimeType
    ) {

        if (
                mimeType == null ||
                mimeType.trim().isEmpty()
        ) {

            return "application/octet-stream";
        }


        return mimeType;
    }


    /* =========================================================
       CLEAN UP WEBVIEW
       ========================================================= */

    @Override
    protected void onDestroy() {

        if (webView != null) {

            webView.removeJavascriptInterface(
                    "AndroidBridge"
            );

            webView.stopLoading();

            webView.destroy();

            webView = null;
        }

        super.onDestroy();
    }
}
