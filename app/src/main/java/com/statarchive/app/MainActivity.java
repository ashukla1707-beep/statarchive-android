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
import java.io.OutputStream;


public class MainActivity extends AppCompatActivity {

    private WebView webView;

    private static final String SITE_HOST =
            "stat-archive.lustats.workers.dev";


    /* =========================================================
       SAVE TO FILES
       ========================================================= */

    private static final int SAVE_FILE_REQUEST = 9001;

    private String pendingSaveBase64 = null;


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

        FrameLayout root =
                new FrameLayout(this);

        root.setBackgroundColor(
                Color.rgb(7, 10, 15)
        );


        /* =====================================================
           WEBVIEW
           ===================================================== */

        webView =
                new WebView(this);

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
         * Let the website use its real
         * responsive phone layout.
         */
        settings.setUseWideViewPort(true);

        settings.setLoadWithOverviewMode(false);

        settings.setTextZoom(100);

        webView.setInitialScale(0);


        /*
         * Disable browser-style WebView zoom.
         *
         * PDF pinch zoom is handled by preview.js.
         */
        settings.setSupportZoom(false);

        settings.setBuiltInZoomControls(false);

        settings.setDisplayZoomControls(false);


        /*
         * Keep normal Android WebView
         * user agent.
         */
        settings.setUserAgentString(
                settings.getUserAgentString()
        );


        /* =====================================================
           NATIVE ANDROID FILE BRIDGE

           JavaScript can call:

           AndroidBridge.openFile(...)
           AndroidBridge.shareFile(...)
           AndroidBridge.saveFile(...)
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
                     * Keep Stat Archive links inside app.
                     * External links open through Android.
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
                                        host.endsWith(
                                                "." + SITE_HOST
                                        )
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
                     * Mark Android WebView as
                     * installed PWA/app mode.
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

           1. Close visible popup/modal
           2. Go back in WebView
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


                                final String js =
                                        "(function() {" +

                                        "try {" +

                                        "var ids = [" +

                                        "'previewOverlay'," +
                                        "'offlineLibraryOverlay'," +
                                        "'editEntryOverlay'," +
                                        "'overlay'," +
                                        "'contributorDisclaimerOverlay'," +
                                        "'loginOverlay'" +

                                        "];" +


                                        "for (" +
                                        "var i = 0;" +
                                        "i < ids.length;" +
                                        "i++" +
                                        ") {" +


                                        "var el =" +
                                        "document.getElementById(ids[i]);" +


                                        "if (!el) continue;" +


                                        "var style =" +
                                        "window.getComputedStyle(el);" +


                                        "if (" +

                                        "style.display !== 'none' && " +
                                        "style.visibility !== 'hidden'" +

                                        ") {" +


                                        "el.style.display = 'none';" +


                                        "document.body.classList.remove(" +
                                        "'no-scroll'" +
                                        ");" +


                                        /*
                                         * Clean PDF preview state.
                                         */
                                        "if (" +
                                        "ids[i] === 'previewOverlay'" +
                                        ") {" +

                                        "var card =" +
                                        "el.querySelector('.preview-card');" +

                                        "if (card) {" +

                                        "card.classList.remove(" +
                                        "'pdf-preview-active'" +
                                        ");" +

                                        "}" +

                                        "}" +


                                        "return true;" +

                                        "}" +

                                        "}" +


                                        "return false;" +


                                        "} catch (e) {" +

                                        "return false;" +

                                        "}" +


                                        "})();";


                                webView.evaluateJavascript(
                                        js,
                                        value -> {

                                            /*
                                             * JS true is returned as
                                             * string "true".
                                             */
                                            if (
                                                    "true".equals(value)
                                            ) {

                                                return;
                                            }


                                            if (
                                                    webView != null &&
                                                    webView.canGoBack()
                                            ) {

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


        /* =====================================================
           OPEN FILE
           ===================================================== */

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


        /* =====================================================
           SHARE FILE
           ===================================================== */

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


        /* =====================================================
           SAVE FILE TO ANDROID FILES

           Opens Android's native Save As picker.
           ===================================================== */

        @JavascriptInterface
        public void saveFile(
                String base64Data,
                String filename,
                String mimeType
        ) {

            runOnUiThread(() -> {

                try {

                    pendingSaveBase64 =
                            base64Data;


                    String mime =
                            normalizeMime(
                                    mimeType
                            );


                    Intent intent =
                            new Intent(
                                    Intent.ACTION_CREATE_DOCUMENT
                            );


                    intent.addCategory(
                            Intent.CATEGORY_OPENABLE
                    );


                    intent.setType(
                            mime
                    );


                    intent.putExtra(
                            Intent.EXTRA_TITLE,
                            sanitizeFilename(
                                    filename
                            )
                    );


                    startActivityForResult(
                            intent,
                            SAVE_FILE_REQUEST
                    );


                } catch (Exception e) {

                    pendingSaveBase64 =
                            null;


                    Toast.makeText(
                            MainActivity.this,
                            "Couldn't open the Android file picker.",
                            Toast.LENGTH_LONG
                    ).show();
                }
            });
        }
    }


    /* =========================================================
       RESULT FROM ANDROID SAVE-AS PICKER
       ========================================================= */

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );


        if (
                requestCode !=
                SAVE_FILE_REQUEST
        ) {

            return;
        }


        /*
         * User cancelled the picker.
         */
        if (
                resultCode != RESULT_OK ||
                data == null ||
                data.getData() == null ||
                pendingSaveBase64 == null
        ) {

            pendingSaveBase64 =
                    null;

            return;
        }


        Uri destinationUri =
                data.getData();


        try {

            byte[] bytes =
                    Base64.decode(
                            pendingSaveBase64,
                            Base64.DEFAULT
                    );


            try (
                    OutputStream output =
                            getContentResolver()
                                    .openOutputStream(
                                            destinationUri
                                    )
            ) {

                if (output == null) {

                    throw new IOException(
                            "Could not open destination."
                    );
                }


                output.write(
                        bytes
                );

                output.flush();
            }


            Toast.makeText(
                    this,
                    "File saved successfully.",
                    Toast.LENGTH_SHORT
            ).show();


        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Couldn't save the file.",
                    Toast.LENGTH_LONG
            ).show();


        } finally {

            pendingSaveBase64 =
                    null;
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
                        new FileOutputStream(
                                file
                        )
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
         * Keep normal file extensions such as
         * .pdf, .png, .docx, etc.
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

        pendingSaveBase64 =
                null;


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

            webView =
                    null;
        }


        super.onDestroy();
    }
}
