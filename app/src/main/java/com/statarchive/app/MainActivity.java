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


        /*
         * Disable browser-style WebView zoom.
         *
         * PDF pinch zoom is handled by preview.js,
         * so we do not need Android WebView page zoom.
         */
        settings.setSupportZoom(false);

        settings.setBuiltInZoomControls(false);

        settings.setDisplayZoomControls(false);


        /*
         * Keep the normal Android mobile WebView
         * user agent.
         */
        settings.setUserAgentString(
                settings.getUserAgentString()
        );


        /* =====================================================
           NATIVE ANDROID FILE BRIDGE

           JavaScript calls:

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

                        Uri uri =
                                request.getUrl();

                        String host =
                                uri.getHost();


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


                    /*
                     * Mark Android WebView as installed-app mode.
                     *
                     * IMPORTANT:
                     * The Gaussian curve animation is now handled
                     * entirely by hero-animation.js.
                     *
                     * Do not force stroke-dashoffset or animation
                     * state from Android anymore.
                     */
                    @Override
                    public void onPageFinished(
                            WebView view,
                            String url
                    ) {

                        super.onPageFinished(
                                view,
                                url
                        );


                        view.evaluateJavascript(
                                "(function() {" +
                                "document.documentElement.classList.add('stat-archive-pwa');" +
                                "})();",
                                null
                        );
                    }
                }
        );


      /* =====================================================
   ANDROID BACK BUTTON

   Priority:
   1. Close any open Stat Archive popup/modal
   2. Go back in WebView history
   3. Exit app
   ===================================================== */

getOnBackPressedDispatcher()
        .addCallback(
                this,
                new OnBackPressedCallback(true) {

                    @Override
                    public void handleOnBackPressed() {

                        if (webView == null) {
                            finish();
                            return;
                        }

                        webView.evaluateJavascript(
                                "(function() {" +

                                "var closed = false;" +

                                /*
                                 * PDF / image preview
                                 */
                                "var preview = document.getElementById('previewOverlay');" +
                                "if (preview && getComputedStyle(preview).display !== 'none') {" +
                                    "if (typeof closePreview === 'function') {" +
                                        "closePreview();" +
                                    "} else {" +
                                        "preview.style.display = 'none';" +
                                        "document.body.classList.remove('no-scroll');" +
                                    "}" +
                                    "return true;" +
                                "}" +

                                /*
                                 * Offline Library
                                 */
                                "var offline = document.getElementById('offlineLibraryOverlay');" +
                                "if (offline && getComputedStyle(offline).display !== 'none') {" +
                                    "if (typeof closeOfflineLibrary === 'function') {" +
                                        "closeOfflineLibrary();" +
                                    "} else {" +
                                        "offline.style.display = 'none';" +
                                        "document.body.classList.remove('no-scroll');" +
                                    "}" +
                                    "return true;" +
                                "}" +

                                /*
                                 * Edit Entry
                                 */
                                "var edit = document.getElementById('editEntryOverlay');" +
                                "if (edit && getComputedStyle(edit).display !== 'none') {" +
                                    "if (typeof closeEditEntry === 'function') {" +
                                        "closeEditEntry();" +
                                    "} else {" +
                                        "edit.style.display = 'none';" +
                                        "document.body.classList.remove('no-scroll');" +
                                    "}" +
                                    "return true;" +
                                "}" +

                                /*
                                 * Upload / Add Entry
                                 */
                                "var upload = document.getElementById('overlay');" +
                                "if (upload && getComputedStyle(upload).display !== 'none') {" +
                                    "if (typeof closeAndResetUploadForm === 'function') {" +
                                        "closeAndResetUploadForm();" +
                                    "} else {" +
                                        "upload.style.display = 'none';" +
                                        "document.body.classList.remove('no-scroll');" +
                                    "}" +
                                    "return true;" +
                                "}" +

                                /*
                                 * Contributor disclaimer
                                 */
                                "var disclaimer = document.getElementById('contributorDisclaimerOverlay');" +
                                "if (disclaimer && getComputedStyle(disclaimer).display !== 'none') {" +
                                    "disclaimer.style.display = 'none';" +
                                    "document.body.classList.remove('no-scroll');" +
                                    "return true;" +
                                "}" +

                                /*
                                 * Sign In
                                 */
                                "var login = document.getElementById('loginOverlay');" +
                                "if (login && getComputedStyle(login).display !== 'none') {" +
                                    "if (typeof clearLoginModalState === 'function') {" +
                                        "clearLoginModalState();" +
                                    "}" +
                                    "login.style.display = 'none';" +
                                    "document.body.classList.remove('no-scroll');" +
                                    "return true;" +
                                "}" +

                                /*
                                 * Nothing was open.
                                 */
                                "return false;" +

                                "})();",

                                value -> {

                                    boolean popupClosed =
                                            "true".equals(value);

                                    if (popupClosed) {
                                        return;
                                    }

                                    if (webView.canGoBack()) {
                                        webView.goBack();
                                    } else {
                                        finish();
                                    }
                                }
                        );
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
         * OPEN FILE
         *
         * Used by:
         * - Offline Library
         * - Open PDF button in preview.js
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
                            "Couldn't open the file.",
                            Toast.LENGTH_LONG
                    ).show();
                }
            });
        }


        /*
         * SHARE FILE
         *
         * Used by Offline Library share.
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


                    intent.setType(
                            mime
                    );


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


                    startActivity(
                            chooser
                    );


                } catch (Exception e) {

                    Toast.makeText(
                            MainActivity.this,
                            "Couldn't share the file.",
                            Toast.LENGTH_LONG
                    ).show();
                }
            });
        }
    }


    /* =========================================================
       WRITE BASE64 FILE TO TEMPORARY ANDROID CACHE
       ========================================================= */

    private File createSharedFile(
            String base64Data,
            String filename
    ) throws IOException {


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

            output.write(
                    bytes
            );

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


        /*
         * Android viewers work more reliably when
         * the file keeps its extension.
         *
         * The replacement above preserves dots except "..",
         * so normal .pdf/.png/etc extensions remain intact.
         */

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


        return mimeType.trim();
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

            webView.loadUrl(
                    "about:blank"
            );

            webView.clearHistory();

            webView.removeAllViews();

            webView.destroy();

            webView = null;
        }


        super.onDestroy();
    }
}
