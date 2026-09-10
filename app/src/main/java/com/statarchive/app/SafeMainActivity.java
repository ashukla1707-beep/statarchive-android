package com.statarchive.app;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Base64InputStream;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Safety wrapper around the established MainActivity.
 *
 * MainActivity remains the compatibility baseline. This class adds only the
 * Android-specific protections that are difficult to express in the website:
 * - Preview is closed through window.closePreview() so pdf.js/fetch cleanup runs;
 * - large bridge file/scanner work is kept off the Android UI thread;
 * - bridge calls are enabled only while the top-level WebView is on the trusted
 *   Stat Archive HTTPS origin.
 */
public class SafeMainActivity extends MainActivity {

    private static final int SAFE_SAVE_FILE_REQUEST = 19101;
    private static final int SAFE_SCANNER_CAMERA_REQUEST = 19102;
    private static final int SAFE_SCANNER_GALLERY_REQUEST = 19103;

    private static final String SITE_HOST = "stat-archive.lustats.workers.dev";
    private static final String CREDENTIAL_PREFS = "stat_archive_secure_credentials";
    private static final String CREDENTIAL_KEY_ALIAS = "stat_archive_passcode_key_v1";
    private static final long MAX_NATIVE_SCANNER_IMAGE_BYTES = 32L * 1024L * 1024L;

    private static final String CLOSE_PREVIEW_IF_OPEN_JS =
            "(function(){try{" +
            "var el=document.getElementById('previewOverlay');" +
            "if(!el)return false;" +
            "var s=window.getComputedStyle(el);" +
            "if(s.display==='none'||s.visibility==='hidden')return false;" +
            "if(typeof window.closePreview==='function'){window.closePreview();return true;}" +
            "return false;" +
            "}catch(e){return false;}})();";

    private final ExecutorService bridgeExecutor = Executors.newSingleThreadExecutor();

    private OnBackPressedCallback cleanupAwareBackCallback;
    private WebView safeWebView;
    private volatile boolean trustedTopLevelPage;
    private File pendingScannerCameraFile;
    private File pendingSafeSaveFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        safeWebView = findWebView(getWindow().getDecorView());
        if (safeWebView != null) {
            // WebView URL access is UI-thread-only. Cache trust on this thread
            // and let JavascriptInterface methods read only the volatile flag.
            trustedTopLevelPage = isTrustedUrl(safeWebView.getUrl());

            safeWebView.removeJavascriptInterface("AndroidBridge");
            safeWebView.addJavascriptInterface(new SafeAndroidBridge(), "AndroidBridge");
            safeWebView.setWebViewClient(createSafeWebViewClient());
        }

        cleanupAwareBackCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                WebView webView = safeWebView != null
                        ? safeWebView
                        : findWebView(getWindow().getDecorView());

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

        // Added after MainActivity's callback, so this one runs first.
        getOnBackPressedDispatcher().addCallback(this, cleanupAwareBackCallback);
    }

    private WebViewClient createSafeWebViewClient() {
        return new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                trustedTopLevelPage = isTrustedUrl(url);
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (isTrustedUri(uri)) {
                    return false;
                }

                // Keep untrusted top-level content outside the WebView. The
                // currently loaded trusted page remains active, so do not alter
                // trustedTopLevelPage here unless a navigation actually begins.
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {
                    // No external handler: still block the untrusted navigation.
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                trustedTopLevelPage = isTrustedUrl(url);
                super.onPageFinished(view, url);
                if (trustedTopLevelPage) {
                    view.evaluateJavascript(
                            "document.documentElement.classList.add('stat-archive-pwa');",
                            null
                    );
                }
            }
        };
    }

    private boolean isTrustedUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        try {
            return isTrustedUri(Uri.parse(url));
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isTrustedUri(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        String host = uri.getHost();
        return host != null
                && (SITE_HOST.equalsIgnoreCase(host)
                || host.toLowerCase().endsWith("." + SITE_HOST.toLowerCase()));
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

    private boolean trustedBridgeCall() {
        // JavascriptInterface callbacks execute on WebView's bridge thread.
        // Never call WebView.getUrl() here; only read UI-thread-maintained state.
        return trustedTopLevelPage && safeWebView != null && !isFinishing() && !isDestroyed();
    }

    private void runBridgeIo(Runnable task) {
        if (task == null || bridgeExecutor.isShutdown()) {
            return;
        }
        try {
            bridgeExecutor.execute(task);
        } catch (RuntimeException ignored) {
            // Activity teardown can race with a late JavascriptInterface call.
        }
    }

    private String credentialSlot(String level, String role) {
        String normalizedLevel = "bsc".equalsIgnoreCase(level == null ? "" : level.trim())
                ? "bsc"
                : "msc";
        String normalizedRole = "admin".equalsIgnoreCase(role == null ? "" : role.trim())
                ? "admin"
                : "contributor";
        return normalizedLevel + "_" + normalizedRole;
    }

    private synchronized SecretKey getOrCreateCredentialKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        if (keyStore.containsAlias(CREDENTIAL_KEY_ALIAS)) {
            java.security.Key existingKey = keyStore.getKey(CREDENTIAL_KEY_ALIAS, null);
            if (existingKey instanceof SecretKey) {
                return (SecretKey) existingKey;
            }
        }

        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
        );
        KeyGenParameterSpec specification = new KeyGenParameterSpec.Builder(
                CREDENTIAL_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build();

        generator.init(specification);
        return generator.generateKey();
    }

    private String fileProviderAuthority() {
        return getPackageName() + ".fileprovider";
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return "statarchive-file";
        }
        return filename
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replace("..", "_");
    }

    private String normalizeMime(String mimeType) {
        return mimeType == null || mimeType.trim().isEmpty()
                ? "application/octet-stream"
                : mimeType.trim();
    }

    private File writeBase64ToSharedFile(String base64Data, String filename) throws IOException {
        if (base64Data == null) {
            throw new IOException("File data is missing.");
        }

        File sharedDirectory = new File(getCacheDir(), "shared");
        if (!sharedDirectory.exists() && !sharedDirectory.mkdirs()) {
            throw new IOException("Could not create shared directory.");
        }

        File file = new File(sharedDirectory, sanitizeFilename(filename));
        byte[] encoded = base64Data.getBytes(StandardCharsets.US_ASCII);

        try (
                InputStream raw = new ByteArrayInputStream(encoded);
                InputStream decoded = new Base64InputStream(raw, Base64.DEFAULT);
                FileOutputStream output = new FileOutputStream(file)
        ) {
            copyStream(decoded, output);
        }
        return file;
    }

    private void copyStream(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        output.flush();
    }

    private void copyFile(File source, Uri destination) throws IOException {
        try (
                InputStream input = new FileInputStream(source);
                OutputStream output = getContentResolver().openOutputStream(destination)
        ) {
            if (output == null) {
                throw new IOException("Could not open destination.");
            }
            copyStream(input, output);
        }
    }

    private byte[] readUriWithLimit(Uri uri) throws IOException {
        try (
                InputStream raw = getContentResolver().openInputStream(uri);
                InputStream input = raw == null ? null : new BufferedInputStream(raw);
                ByteArrayOutputStream output = new ByteArrayOutputStream()
        ) {
            if (input == null) {
                throw new IOException("Could not open selected image.");
            }

            byte[] buffer = new byte[8192];
            long total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_NATIVE_SCANNER_IMAGE_BYTES) {
                    throw new IOException("Selected image is too large.");
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private void notifyScannerBatchDone() {
        runOnUiThread(() -> {
            WebView webView = safeWebView;
            if (webView != null) {
                webView.evaluateJavascript(
                        "window.statArchiveScannerNativeBatchDone && " +
                                "window.statArchiveScannerNativeBatchDone();",
                        null
                );
            }
        });
    }

    private void sendScannerImagesToWeb(ArrayList<Uri> uris, int index) {
        if (uris == null || index >= uris.size()) {
            notifyScannerBatchDone();
            return;
        }

        final Uri uri = uris.get(index);
        runBridgeIo(() -> {
            try {
                byte[] bytes = readUriWithLimit(uri);
                String mime = getContentResolver().getType(uri);
                if (mime == null || !mime.startsWith("image/")) {
                    mime = "image/jpeg";
                }

                String dataUrl = "data:" + mime + ";base64,"
                        + Base64.encodeToString(bytes, Base64.NO_WRAP);
                String filename = "page_" + (index + 1)
                        + (mime.contains("png") ? ".png" : ".jpg");
                String js = "window.statArchiveScannerNativeReceive && "
                        + "window.statArchiveScannerNativeReceive("
                        + JSONObject.quote(dataUrl) + ","
                        + JSONObject.quote(filename) + ");";

                runOnUiThread(() -> {
                    WebView webView = safeWebView;
                    if (webView == null || !trustedTopLevelPage) {
                        return;
                    }
                    webView.evaluateJavascript(
                            js,
                            value -> sendScannerImagesToWeb(uris, index + 1)
                    );
                });
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(
                        SafeMainActivity.this,
                        "A selected image could not be read or was too large.",
                        Toast.LENGTH_LONG
                ).show());
                sendScannerImagesToWeb(uris, index + 1);
            }
        });
    }

    private class SafeAndroidBridge {
        @JavascriptInterface
        public void scannerTakePhoto() {
            if (!trustedBridgeCall()) return;

            runOnUiThread(() -> {
                try {
                    File scannerDirectory = new File(getCacheDir(), "scanner");
                    if (!scannerDirectory.exists() && !scannerDirectory.mkdirs()) {
                        throw new IOException("Could not create scanner directory.");
                    }

                    pendingScannerCameraFile = File.createTempFile(
                            "page_", ".jpg", scannerDirectory
                    );
                    Uri outputUri = FileProvider.getUriForFile(
                            SafeMainActivity.this,
                            fileProviderAuthority(),
                            pendingScannerCameraFile
                    );

                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, outputUri);
                    intent.addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    );
                    startActivityForResult(intent, SAFE_SCANNER_CAMERA_REQUEST);
                } catch (Exception error) {
                    pendingScannerCameraFile = null;
                    Toast.makeText(
                            SafeMainActivity.this,
                            "Couldn't open the camera.",
                            Toast.LENGTH_LONG
                    ).show();
                    notifyScannerBatchDone();
                }
            });
        }

        @JavascriptInterface
        public void scannerChoosePhotos() {
            if (!trustedBridgeCall()) return;

            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("image/*");
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    startActivityForResult(intent, SAFE_SCANNER_GALLERY_REQUEST);
                } catch (Exception error) {
                    Toast.makeText(
                            SafeMainActivity.this,
                            "Couldn't open the photo picker.",
                            Toast.LENGTH_LONG
                    ).show();
                    notifyScannerBatchDone();
                }
            });
        }

        @JavascriptInterface
        public String getSavedPasscode(String level, String role) {
            if (!trustedBridgeCall()) return "";

            String slot = credentialSlot(level, role);
            SharedPreferences prefs = getSharedPreferences(CREDENTIAL_PREFS, MODE_PRIVATE);
            String ivBase64 = prefs.getString(slot + "_iv", null);
            String encryptedBase64 = prefs.getString(slot + "_data", null);
            if (ivBase64 == null || encryptedBase64 == null) return "";

            try {
                SecretKey key = getOrCreateCredentialKey();
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                byte[] iv = Base64.decode(ivBase64, Base64.NO_WRAP);
                byte[] encrypted = Base64.decode(encryptedBase64, Base64.NO_WRAP);
                cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
                return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
            } catch (Exception error) {
                prefs.edit()
                        .remove(slot + "_iv")
                        .remove(slot + "_data")
                        .apply();
                return "";
            }
        }

        @JavascriptInterface
        public void savePasscode(String level, String role, String passcode) {
            if (!trustedBridgeCall() || passcode == null || passcode.isEmpty()) return;

            try {
                String slot = credentialSlot(level, role);
                SecretKey key = getOrCreateCredentialKey();
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, key);
                byte[] encrypted = cipher.doFinal(passcode.getBytes(StandardCharsets.UTF_8));

                getSharedPreferences(CREDENTIAL_PREFS, MODE_PRIVATE)
                        .edit()
                        .putString(
                                slot + "_iv",
                                Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)
                        )
                        .putString(
                                slot + "_data",
                                Base64.encodeToString(encrypted, Base64.NO_WRAP)
                        )
                        .apply();
            } catch (Exception ignored) {
                // Credential persistence must never interrupt a successful login.
            }
        }

        @JavascriptInterface
        public void clearSavedPasscode(String level, String role) {
            if (!trustedBridgeCall()) return;

            String slot = credentialSlot(level, role);
            getSharedPreferences(CREDENTIAL_PREFS, MODE_PRIVATE)
                    .edit()
                    .remove(slot + "_iv")
                    .remove(slot + "_data")
                    .apply();
        }

        @JavascriptInterface
        public void openFile(String base64Data, String filename, String mimeType) {
            if (!trustedBridgeCall()) return;

            runBridgeIo(() -> {
                try {
                    File file = writeBase64ToSharedFile(base64Data, filename);
                    Uri uri = FileProvider.getUriForFile(
                            SafeMainActivity.this, fileProviderAuthority(), file
                    );
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(uri, normalizeMime(mimeType));
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    runOnUiThread(() -> {
                        try {
                            startActivity(intent);
                        } catch (ActivityNotFoundException error) {
                            Toast.makeText(
                                    SafeMainActivity.this,
                                    "No app is available to open this file.",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> Toast.makeText(
                            SafeMainActivity.this,
                            "Couldn't open the file.",
                            Toast.LENGTH_LONG
                    ).show());
                }
            });
        }

        @JavascriptInterface
        public void shareFile(String base64Data, String filename, String mimeType) {
            if (!trustedBridgeCall()) return;

            runBridgeIo(() -> {
                try {
                    File file = writeBase64ToSharedFile(base64Data, filename);
                    Uri uri = FileProvider.getUriForFile(
                            SafeMainActivity.this, fileProviderAuthority(), file
                    );
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType(normalizeMime(mimeType));
                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    runOnUiThread(() -> {
                        try {
                            startActivity(Intent.createChooser(intent, "Share file"));
                        } catch (Exception error) {
                            Toast.makeText(
                                    SafeMainActivity.this,
                                    "Couldn't share the file.",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> Toast.makeText(
                            SafeMainActivity.this,
                            "Couldn't share the file.",
                            Toast.LENGTH_LONG
                    ).show());
                }
            });
        }

        @JavascriptInterface
        public void saveFile(String base64Data, String filename, String mimeType) {
            if (!trustedBridgeCall()) return;

            runBridgeIo(() -> {
                try {
                    File file = writeBase64ToSharedFile(base64Data, filename);
                    runOnUiThread(() -> {
                        try {
                            pendingSafeSaveFile = file;
                            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                            intent.setType(normalizeMime(mimeType));
                            intent.putExtra(Intent.EXTRA_TITLE, sanitizeFilename(filename));
                            startActivityForResult(intent, SAFE_SAVE_FILE_REQUEST);
                        } catch (Exception error) {
                            pendingSafeSaveFile = null;
                            file.delete();
                            Toast.makeText(
                                    SafeMainActivity.this,
                                    "Couldn't open the Android file picker.",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> Toast.makeText(
                            SafeMainActivity.this,
                            "Couldn't prepare that file.",
                            Toast.LENGTH_LONG
                    ).show());
                }
            });
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SAFE_SCANNER_CAMERA_REQUEST) {
            ArrayList<Uri> uris = new ArrayList<>();
            if (resultCode == RESULT_OK
                    && pendingScannerCameraFile != null
                    && pendingScannerCameraFile.exists()) {
                uris.add(FileProvider.getUriForFile(
                        this, fileProviderAuthority(), pendingScannerCameraFile
                ));
            }
            pendingScannerCameraFile = null;
            sendScannerImagesToWeb(uris, 0);
            return;
        }

        if (requestCode == SAFE_SCANNER_GALLERY_REQUEST) {
            ArrayList<Uri> uris = new ArrayList<>();
            if (resultCode == RESULT_OK && data != null) {
                ClipData clipData = data.getClipData();
                if (clipData != null) {
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        Uri uri = clipData.getItemAt(i).getUri();
                        if (uri != null) uris.add(uri);
                    }
                } else if (data.getData() != null) {
                    uris.add(data.getData());
                }
            }
            sendScannerImagesToWeb(uris, 0);
            return;
        }

        if (requestCode == SAFE_SAVE_FILE_REQUEST) {
            File source = pendingSafeSaveFile;
            pendingSafeSaveFile = null;

            if (resultCode != RESULT_OK
                    || data == null
                    || data.getData() == null
                    || source == null
                    || !source.exists()) {
                if (source != null) source.delete();
                return;
            }

            Uri destination = data.getData();
            runBridgeIo(() -> {
                try {
                    copyFile(source, destination);
                    runOnUiThread(() -> Toast.makeText(
                            SafeMainActivity.this,
                            "File saved successfully.",
                            Toast.LENGTH_SHORT
                    ).show());
                } catch (Exception error) {
                    runOnUiThread(() -> Toast.makeText(
                            SafeMainActivity.this,
                            "Couldn't save the file.",
                            Toast.LENGTH_LONG
                    ).show());
                } finally {
                    source.delete();
                }
            });
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onDestroy() {
        trustedTopLevelPage = false;
        bridgeExecutor.shutdownNow();
        if (pendingSafeSaveFile != null) pendingSafeSaveFile.delete();
        pendingSafeSaveFile = null;
        pendingScannerCameraFile = null;
        safeWebView = null;
        super.onDestroy();
    }
}
