package com.statarchive.app;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;


public class MainActivity extends AppCompatActivity {

    private WebView webView;


    /* =========================================================
       STAT ARCHIVE WEBSITE
       ========================================================= */

    private static final String SITE_HOST =
            "stat-archive.lustats.workers.dev";


    /* =========================================================
       APP UPDATE SYSTEM

       version.json must be available at:

       https://stat-archive.lustats.workers.dev/version.json
       ========================================================= */

    private static final String UPDATE_INFO_URL =
            "https://stat-archive.lustats.workers.dev/version.json";


    private boolean updateDialogShown = false;

    private File pendingUpdateApk = null;


    /* =========================================================
       SAVE TO FILES
       ========================================================= */

    private static final int SAVE_FILE_REQUEST = 9001;

    private String pendingSaveBase64 = null;


    /* =========================================================
       SECURE SAVED PASSCODES

       Four independent credential slots are stored:
       - MSc + Contributor
       - MSc + Admin
       - BSc + Contributor
       - BSc + Admin

       Passwords are encrypted with an AES key that lives in
       Android Keystore. SharedPreferences stores ciphertext only.
       ========================================================= */

    private static final String CREDENTIAL_PREFS =
            "stat_archive_secure_credentials";

    private static final String CREDENTIAL_KEY_ALIAS =
            "stat_archive_passcode_key_v1";



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


        /* =====================================================
           ANDROID AUTOFILL / PASSWORD MANAGER
           ===================================================== */

        if (
                Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.O
        ) {

            webView.setImportantForAutofill(
                    View.IMPORTANT_FOR_AUTOFILL_YES
            );
        }


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


        settings.setJavaScriptEnabled(
                true
        );


        settings.setDomStorageEnabled(
                true
        );


        settings.setUseWideViewPort(
                true
        );


        settings.setLoadWithOverviewMode(
                false
        );


        settings.setTextZoom(
                100
        );


        webView.setInitialScale(
                0
        );


        /*
         * Disable normal WebView zoom.
         *
         * PDF pinch zoom is handled
         * by the website.
         */
        settings.setSupportZoom(
                false
        );


        settings.setBuiltInZoomControls(
                false
        );


        settings.setDisplayZoomControls(
                false
        );


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

                    @Override
                    public boolean shouldOverrideUrlLoading(
                            WebView view,
                            WebResourceRequest request
                    ) {

                        Uri uri =
                                request.getUrl();


                        String host =
                                uri.getHost();


                        /*
                         * Keep Stat Archive itself
                         * inside the app.
                         */
                        if (
                                host != null &&
                                (
                                        host.equals(
                                                SITE_HOST
                                        ) ||
                                        host.endsWith(
                                                "." + SITE_HOST
                                        )
                                )
                        ) {

                            return false;
                        }


                        /*
                         * Open external links
                         * using Android.
                         */
                        try {

                            Intent intent =
                                    new Intent(
                                            Intent.ACTION_VIEW,
                                            uri
                                    );


                            startActivity(
                                    intent
                            );


                        } catch (
                                Exception ignored
                        ) {
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
                         * Tell website that this
                         * is the installed app.
                         */
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
           JAVASCRIPT CONFIRM DIALOG

           Required for Admin / Contributor Delete.
           ===================================================== */

        webView.setWebChromeClient(
                new WebChromeClient() {

                    @Override
                    public boolean onJsConfirm(
                            WebView view,
                            String url,
                            String message,
                            JsResult result
                    ) {

                        AlertDialog dialog =
                                new AlertDialog.Builder(
                                        MainActivity.this
                                )

                                        .setTitle(
                                                "Stat Archive"
                                        )

                                        .setMessage(
                                                message
                                        )

                                        .setPositiveButton(
                                                "Delete",
                                                (d, which) ->
                                                        result.confirm()
                                        )

                                        .setNegativeButton(
                                                "Cancel",
                                                (d, which) ->
                                                        result.cancel()
                                        )

                                        .setOnCancelListener(
                                                d ->
                                                        result.cancel()
                                        )

                                        .create();


                        dialog.show();


                        dialog
                                .getButton(
                                        AlertDialog.BUTTON_POSITIVE
                                )
                                .setAllCaps(
                                        false
                                );


                        dialog
                                .getButton(
                                        AlertDialog.BUTTON_NEGATIVE
                                )
                                .setAllCaps(
                                        false
                                );


                        return true;
                    }
                }
        );


        /* =====================================================
           ANDROID BACK BUTTON

           1. Close popup/modal
           2. Go back in WebView
           3. Exit app
           ===================================================== */

        getOnBackPressedDispatcher()
                .addCallback(
                        this,
                        new OnBackPressedCallback(
                                true
                        ) {

                            @Override
                            public void handleOnBackPressed() {

                                if (
                                        webView == null
                                ) {

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

                                            if (
                                                    "true".equals(
                                                            value
                                                    )
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


        /* =====================================================
           CHECK FOR NEW APK VERSION

           Runs in background.
           Failure does NOT affect normal app usage.
           ===================================================== */

        checkForAppUpdate();
    }


    /* =========================================================
       APP UPDATE CHECKER
       ========================================================= */

    private void checkForAppUpdate() {

        new Thread(
                () -> {

                    HttpURLConnection connection =
                            null;


                    try {

                        URL url =
                                new URL(
                                        UPDATE_INFO_URL
                                );


                        connection =
                                (HttpURLConnection)
                                        url.openConnection();


                        connection.setRequestMethod(
                                "GET"
                        );


                        connection.setConnectTimeout(
                                8000
                        );


                        connection.setReadTimeout(
                                8000
                        );


                        connection.setUseCaches(
                                false
                        );


                        connection.setRequestProperty(
                                "Cache-Control",
                                "no-cache"
                        );


                        int responseCode =
                                connection.getResponseCode();


                        if (
                                responseCode < 200 ||
                                responseCode >= 300
                        ) {

                            return;
                        }


                        String jsonText =
                                readText(
                                        connection.getInputStream()
                                );


                        JSONObject json =
                                new JSONObject(
                                        jsonText
                                );


                        long latestVersionCode =
                                json.optLong(
                                        "versionCode",
                                        0
                                );


                        String latestVersionName =
                                json.optString(
                                        "versionName",
                                        ""
                                );


                        String apkUrl =
                                json.optString(
                                        "apkUrl",
                                        ""
                                );


                        String message =
                                json.optString(
                                        "message",
                                        "A new version of Stat Archive is available."
                                );


                        long installedVersionCode =
                                getInstalledVersionCode();


                        if (
                                latestVersionCode >
                                        installedVersionCode &&
                                !apkUrl.trim().isEmpty()
                        ) {

                            runOnUiThread(
                                    () ->
                                            showUpdateDialog(
                                                    latestVersionName,
                                                    message,
                                                    apkUrl
                                            )
                            );
                        }


                    } catch (
                            Exception ignored
                    ) {

                        /*
                         * Update checking must never
                         * prevent the app from opening.
                         */


                    } finally {

                        if (
                                connection != null
                        ) {

                            connection.disconnect();
                        }
                    }
                }
        ).start();
    }


    /* =========================================================
       GET INSTALLED VERSION CODE
       ========================================================= */

    private long getInstalledVersionCode()
            throws Exception {

        android.content.pm.PackageInfo info =
                getPackageManager()
                        .getPackageInfo(
                                getPackageName(),
                                0
                        );


        if (
                Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.P
        ) {

            return info.getLongVersionCode();
        }


        return info.versionCode;
    }


    /* =========================================================
       SHOW UPDATE AVAILABLE DIALOG
       ========================================================= */

    private void showUpdateDialog(
            String versionName,
            String message,
            String apkUrl
    ) {

        if (
                isFinishing() ||
                isDestroyed() ||
                updateDialogShown
        ) {

            return;
        }


        updateDialogShown =
                true;


        StringBuilder text =
                new StringBuilder();


        if (
                message != null &&
                !message.trim().isEmpty()
        ) {

            text.append(
                    message.trim()
            );
        } else {

            text.append(
                    "A new version of Stat Archive is available."
            );
        }


        if (
                versionName != null &&
                !versionName.trim().isEmpty()
        ) {

            text.append(
                    "\n\nNew version: "
            );

            text.append(
                    versionName.trim()
            );
        }


        AlertDialog dialog =
                new AlertDialog.Builder(
                        this
                )

                        .setTitle(
                                "Update available"
                        )

                        .setMessage(
                                text.toString()
                        )

                        .setPositiveButton(
                                "Update",
                                (d, which) ->
                                        downloadAppUpdate(
                                                apkUrl
                                        )
                        )

                        .setNegativeButton(
                                "Later",
                                (d, which) -> {
                                }
                        )

                        .setOnDismissListener(
                                d ->
                                        updateDialogShown =
                                                false
                        )

                        .create();


        dialog.show();


        dialog
                .getButton(
                        AlertDialog.BUTTON_POSITIVE
                )
                .setAllCaps(
                        false
                );


        dialog
                .getButton(
                        AlertDialog.BUTTON_NEGATIVE
                )
                .setAllCaps(
                        false
                );
    }


    /* =========================================================
       DOWNLOAD NEW APK
       ========================================================= */

    private void downloadAppUpdate(
            String apkUrl
    ) {

        if (
                apkUrl == null ||
                apkUrl.trim().isEmpty()
        ) {

            Toast.makeText(
                    this,
                    "Update download address is missing.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }


        /*
         * Only allow HTTPS update downloads.
         */
        if (
                !apkUrl
                        .toLowerCase()
                        .startsWith(
                                "https://"
                        )
        ) {

            Toast.makeText(
                    this,
                    "Invalid update download address.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }


        Toast.makeText(
                this,
                "Downloading update…",
                Toast.LENGTH_SHORT
        ).show();


        new Thread(
                () -> {

                    HttpURLConnection connection =
                            null;


                    File temporaryFile =
                            null;


                    try {

                        URL url =
                                new URL(
                                        apkUrl
                                );


                        connection =
                                (HttpURLConnection)
                                        url.openConnection();


                        connection.setRequestMethod(
                                "GET"
                        );


                        connection.setConnectTimeout(
                                15000
                        );


                        connection.setReadTimeout(
                                30000
                        );


                        connection.setInstanceFollowRedirects(
                                true
                        );


                        int responseCode =
                                connection.getResponseCode();


                        if (
                                responseCode < 200 ||
                                responseCode >= 300
                        ) {

                            throw new IOException(
                                    "APK download failed."
                            );
                        }


                        File updatesDirectory =
                                new File(
                                        getCacheDir(),
                                        "updates"
                                );


                        if (
                                !updatesDirectory.exists() &&
                                !updatesDirectory.mkdirs()
                        ) {

                            throw new IOException(
                                    "Couldn't create update directory."
                            );
                        }


                        temporaryFile =
                                new File(
                                        updatesDirectory,
                                        "stat-archive-update.download"
                                );


                        File finalApk =
                                new File(
                                        updatesDirectory,
                                        "stat-archive-update.apk"
                                );


                        if (
                                temporaryFile.exists()
                        ) {

                            temporaryFile.delete();
                        }


                        if (
                                finalApk.exists()
                        ) {

                            finalApk.delete();
                        }


                        try (
                                InputStream input =
                                        new BufferedInputStream(
                                                connection.getInputStream()
                                        );

                                FileOutputStream output =
                                        new FileOutputStream(
                                                temporaryFile
                                        )
                        ) {

                            byte[] buffer =
                                    new byte[8192];


                            int count;


                            while (
                                    (count =
                                            input.read(
                                                    buffer
                                            )) != -1
                            ) {

                                output.write(
                                        buffer,
                                        0,
                                        count
                                );
                            }


                            output.flush();
                        }


                        /*
                         * Reject obviously invalid
                         * tiny downloads.
                         */
                        if (
                                temporaryFile.length() <
                                        50_000
                        ) {

                            throw new IOException(
                                    "Downloaded update is invalid."
                            );
                        }


                        if (
                                !temporaryFile.renameTo(
                                        finalApk
                                )
                        ) {

                            copyFile(
                                    temporaryFile,
                                    finalApk
                            );


                            temporaryFile.delete();
                        }


                        pendingUpdateApk =
                                finalApk;


                        runOnUiThread(
                                () ->
                                        beginUpdateInstallation(
                                                finalApk
                                        )
                        );


                    } catch (
                            Exception e
                    ) {

                        if (
                                temporaryFile != null &&
                                temporaryFile.exists()
                        ) {

                            temporaryFile.delete();
                        }


                        runOnUiThread(
                                () ->
                                        Toast.makeText(
                                                MainActivity.this,
                                                "Couldn't download the update.",
                                                Toast.LENGTH_LONG
                                        ).show()
                        );


                    } finally {

                        if (
                                connection != null
                        ) {

                            connection.disconnect();
                        }
                    }
                }
        ).start();
    }


    /* =========================================================
       INSTALL DOWNLOADED APK
       ========================================================= */

    private void beginUpdateInstallation(
            File apkFile
    ) {

        if (
                apkFile == null ||
                !apkFile.exists()
        ) {

            Toast.makeText(
                    this,
                    "Update file could not be found.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }


        pendingUpdateApk =
                apkFile;


        /*
         * Android 8+ requires the user to allow
         * this app to install unknown apps.
         */
        if (
                Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.O &&
                !getPackageManager()
                        .canRequestPackageInstalls()
        ) {

            new AlertDialog.Builder(
                    this
            )

                    .setTitle(
                            "Allow app updates"
                    )

                    .setMessage(
                            "Android needs permission for Stat Archive to install its downloaded update. Enable \"Allow from this source\", then return to Stat Archive."
                    )

                    .setPositiveButton(
                            "Open settings",
                            (d, which) -> {

                                try {

                                    Intent intent =
                                            new Intent(
                                                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES
                                            );


                                    intent.setData(
                                            Uri.parse(
                                                    "package:" +
                                                            getPackageName()
                                            )
                                    );


                                    startActivity(
                                            intent
                                    );


                                } catch (
                                        Exception e
                                ) {

                                    Toast.makeText(
                                            MainActivity.this,
                                            "Couldn't open installation settings.",
                                            Toast.LENGTH_LONG
                                    ).show();
                                }
                            }
                    )

                    .setNegativeButton(
                            "Cancel",
                            null
                    )

                    .show();


            return;
        }


        installDownloadedApk(
                apkFile
        );
    }


    /* =========================================================
       OPEN ANDROID PACKAGE INSTALLER
       ========================================================= */

    private void installDownloadedApk(
            File apkFile
    ) {

        try {

            Uri apkUri =
                    FileProvider.getUriForFile(
                            this,
                            getPackageName()
                                    + ".fileprovider",
                            apkFile
                    );


            Intent intent =
                    new Intent(
                            Intent.ACTION_VIEW
                    );


            intent.setDataAndType(
                    apkUri,
                    "application/vnd.android.package-archive"
            );


            intent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );


            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
            );


            startActivity(
                    intent
            );


        } catch (
                Exception e
        ) {

            Toast.makeText(
                    this,
                    "Couldn't start the Android update installer.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }


    /* =========================================================
       AFTER RETURNING FROM INSTALL-PERMISSION SETTINGS
       ========================================================= */

    @Override
    protected void onResume() {

        super.onResume();


        if (
                pendingUpdateApk == null ||
                !pendingUpdateApk.exists()
        ) {

            return;
        }


        if (
                Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.O
        ) {

            if (
                    !getPackageManager()
                            .canRequestPackageInstalls()
            ) {

                return;
            }
        }


        File apk =
                pendingUpdateApk;


        pendingUpdateApk =
                null;


        installDownloadedApk(
                apk
        );
    }


    /* =========================================================
       READ VERSION.JSON
       ========================================================= */

    private String readText(
            InputStream inputStream
    ) throws IOException {

        StringBuilder builder =
                new StringBuilder();


        try (
                InputStream input =
                        new BufferedInputStream(
                                inputStream
                        )
        ) {

            byte[] buffer =
                    new byte[4096];


            int count;


            while (
                    (count =
                            input.read(
                                    buffer
                            )) != -1
            ) {

                builder.append(
                        new String(
                                buffer,
                                0,
                                count,
                                java.nio.charset.StandardCharsets.UTF_8
                        )
                );
            }
        }


        return builder.toString();
    }


    /* =========================================================
       COPY APK IF RENAME FAILS
       ========================================================= */

    private void copyFile(
            File source,
            File destination
    ) throws IOException {

        try (
                InputStream input =
                        new java.io.FileInputStream(
                                source
                        );

                OutputStream output =
                        new FileOutputStream(
                                destination
                        )
        ) {

            byte[] buffer =
                    new byte[8192];


            int count;


            while (
                    (count =
                            input.read(
                                    buffer
                            )) != -1
            ) {

                output.write(
                        buffer,
                        0,
                        count
                );
            }


            output.flush();
        }
    }


    /* =========================================================
       SECURE CREDENTIAL HELPERS
       ========================================================= */

    private String credentialSlot(
            String level,
            String role
    ) {

        String normalizedLevel =
                "bsc".equalsIgnoreCase(
                        level == null
                                ? ""
                                : level.trim()
                )
                        ? "bsc"
                        : "msc";

        String normalizedRole =
                "admin".equalsIgnoreCase(
                        role == null
                                ? ""
                                : role.trim()
                )
                        ? "admin"
                        : "contributor";

        return normalizedLevel
                + "_"
                + normalizedRole;
    }


    private SecretKey getOrCreateCredentialKey()
            throws Exception {

        KeyStore keyStore =
                KeyStore.getInstance(
                        "AndroidKeyStore"
                );

        keyStore.load(null);

        if (
                keyStore.containsAlias(
                        CREDENTIAL_KEY_ALIAS
                )
        ) {

            java.security.Key existingKey =
                    keyStore.getKey(
                            CREDENTIAL_KEY_ALIAS,
                            null
                    );

            if (
                    existingKey instanceof SecretKey
            ) {

                return (SecretKey) existingKey;
            }
        }


        KeyGenerator generator =
                KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES,
                        "AndroidKeyStore"
                );

        KeyGenParameterSpec specification =
                new KeyGenParameterSpec.Builder(
                        CREDENTIAL_KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT |
                                KeyProperties.PURPOSE_DECRYPT
                )
                        .setBlockModes(
                                KeyProperties.BLOCK_MODE_GCM
                        )
                        .setEncryptionPaddings(
                                KeyProperties.ENCRYPTION_PADDING_NONE
                        )
                        .setKeySize(
                                256
                        )
                        .build();

        generator.init(
                specification
        );

        return generator.generateKey();
    }


    /* =========================================================
       ANDROID FILE BRIDGE
       ========================================================= */

    private class AndroidFileBridge {


        /* =====================================================
           SECURE PASSCODE STORAGE
           ===================================================== */

        @JavascriptInterface
        public String getSavedPasscode(
                String level,
                String role
        ) {

            String slot =
                    credentialSlot(
                            level,
                            role
                    );

            SharedPreferences prefs =
                    getSharedPreferences(
                            CREDENTIAL_PREFS,
                            MODE_PRIVATE
                    );

            String ivBase64 =
                    prefs.getString(
                            slot + "_iv",
                            null
                    );

            String encryptedBase64 =
                    prefs.getString(
                            slot + "_data",
                            null
                    );

            if (
                    ivBase64 == null ||
                    encryptedBase64 == null
            ) {

                return "";
            }


            try {

                SecretKey key =
                        getOrCreateCredentialKey();

                Cipher cipher =
                        Cipher.getInstance(
                                "AES/GCM/NoPadding"
                        );

                byte[] iv =
                        Base64.decode(
                                ivBase64,
                                Base64.NO_WRAP
                        );

                byte[] encrypted =
                        Base64.decode(
                                encryptedBase64,
                                Base64.NO_WRAP
                        );

                GCMParameterSpec spec =
                        new GCMParameterSpec(
                                128,
                                iv
                        );

                cipher.init(
                        Cipher.DECRYPT_MODE,
                        key,
                        spec
                );

                byte[] plain =
                        cipher.doFinal(
                                encrypted
                        );

                return new String(
                        plain,
                        StandardCharsets.UTF_8
                );


            } catch (Exception e) {

                /*
                 * If the Android Keystore key was invalidated
                 * or the saved value became corrupt, remove only
                 * this credential slot instead of crashing login.
                 */
                prefs.edit()
                        .remove(slot + "_iv")
                        .remove(slot + "_data")
                        .apply();

                return "";
            }
        }


        @JavascriptInterface
        public void savePasscode(
                String level,
                String role,
                String passcode
        ) {

            if (
                    passcode == null ||
                    passcode.isEmpty()
            ) {

                return;
            }


            try {

                String slot =
                        credentialSlot(
                                level,
                                role
                        );

                SecretKey key =
                        getOrCreateCredentialKey();

                Cipher cipher =
                        Cipher.getInstance(
                                "AES/GCM/NoPadding"
                        );

                cipher.init(
                        Cipher.ENCRYPT_MODE,
                        key
                );

                byte[] encrypted =
                        cipher.doFinal(
                                passcode.getBytes(
                                        StandardCharsets.UTF_8
                                )
                        );

                byte[] iv =
                        cipher.getIV();

                SharedPreferences prefs =
                        getSharedPreferences(
                                CREDENTIAL_PREFS,
                                MODE_PRIVATE
                        );

                prefs.edit()
                        .putString(
                                slot + "_iv",
                                Base64.encodeToString(
                                        iv,
                                        Base64.NO_WRAP
                                )
                        )
                        .putString(
                                slot + "_data",
                                Base64.encodeToString(
                                        encrypted,
                                        Base64.NO_WRAP
                                )
                        )
                        .apply();


            } catch (Exception ignored) {

                /*
                 * Saving a credential must never interrupt
                 * a successful Stat Archive login.
                 */
            }
        }


        @JavascriptInterface
        public void clearSavedPasscode(
                String level,
                String role
        ) {

            String slot =
                    credentialSlot(
                            level,
                            role
                    );

            getSharedPreferences(
                    CREDENTIAL_PREFS,
                    MODE_PRIVATE
            )
                    .edit()
                    .remove(slot + "_iv")
                    .remove(slot + "_data")
                    .apply();
        }


        /* =====================================================
           OPEN FILE
           ===================================================== */

        @JavascriptInterface
        public void openFile(
                String base64Data,
                String filename,
                String mimeType
        ) {

            runOnUiThread(
                    () -> {

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

                                startActivity(
                                        intent
                                );


                            } catch (
                                    ActivityNotFoundException e
                            ) {

                                Toast.makeText(
                                        MainActivity.this,
                                        "No app is available to open this file.",
                                        Toast.LENGTH_LONG
                                ).show();
                            }


                        } catch (
                                Exception e
                        ) {

                            Toast.makeText(
                                    MainActivity.this,
                                    "Couldn't open the file.",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    }
            );
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

            runOnUiThread(
                    () -> {

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


                        } catch (
                                Exception e
                        ) {

                            Toast.makeText(
                                    MainActivity.this,
                                    "Couldn't share the file.",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    }
            );
        }


        /* =====================================================
           SAVE FILE TO ANDROID FILES
           ===================================================== */

        @JavascriptInterface
        public void saveFile(
                String base64Data,
                String filename,
                String mimeType
        ) {

            runOnUiThread(
                    () -> {

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


                        } catch (
                                Exception e
                        ) {

                            pendingSaveBase64 =
                                    null;


                            Toast.makeText(
                                    MainActivity.this,
                                    "Couldn't open the Android file picker.",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    }
            );
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

                if (
                        output == null
                ) {

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


        } catch (
                Exception e
        ) {

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
       CREATE TEMP SHARED FILE
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


        if (
                webView != null
        ) {

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
