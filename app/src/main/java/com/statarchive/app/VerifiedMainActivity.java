package com.statarchive.app;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Final launch activity. SafeMainActivity owns WebView/bridge/Back safety;
 * this layer adds APK trust verification plus memory-safe streaming helpers.
 */
public class VerifiedMainActivity extends SafeMainActivity {

    private static final String APK_MIME = "application/vnd.android.package-archive";
    private static final String SITE_HOST = "stat-archive.lustats.workers.dev";
    private static final int STREAM_URL_SAVE_REQUEST = 19201;
    private static final int STREAM_BLOB_SAVE_REQUEST = 19202;

    private final Object transferLock = new Object();
    private final ExecutorService streamExecutor = Executors.newSingleThreadExecutor();

    private WebView verifiedWebView;
    private File activeTransferFile;
    private FileOutputStream activeTransferOutput;
    private String activeTransferName;
    private String activeTransferMime;
    private String activeTransferAction;

    private String pendingUrl;
    private String pendingUrlName;
    private String pendingUrlMime;
    private File pendingBlobSaveFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        verifiedWebView = findWebView(getWindow().getDecorView());
        if (verifiedWebView != null) {
            verifiedWebView.addJavascriptInterface(new AndroidStreamBridge(), "AndroidStreamBridge");
        }
    }

    private WebView findWebView(View view) {
        if (view instanceof WebView) return (WebView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                WebView found = findWebView(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private boolean isTrustedSiteUrl(String value) {
        if (value == null || value.trim().isEmpty()) return false;
        try {
            Uri uri = Uri.parse(value.trim());
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme())
                    && host != null
                    && (SITE_HOST.equalsIgnoreCase(host)
                    || host.toLowerCase(Locale.ROOT).endsWith("." + SITE_HOST));
        } catch (Exception ignored) {
            return false;
        }
    }

    private String safeFilename(String filename) {
        String value = filename == null ? "" : filename.trim();
        if (value.isEmpty()) value = "Stat Archive file.pdf";
        return value.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_").replace("..", "_");
    }

    private String safeMime(String mime, String filename) {
        String value = mime == null ? "" : mime.trim();
        if (value.isEmpty()) value = "application/octet-stream";
        if ((value.equalsIgnoreCase("application/octet-stream")
                || value.equalsIgnoreCase("binary/octet-stream"))
                && filename != null
                && filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            return "application/pdf";
        }
        return value;
    }

    private File newTransferFile(String filename) throws IOException {
        File dir = new File(getCacheDir(), "streamed-files");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Could not create transfer directory.");
        }
        return new File(dir, System.currentTimeMillis() + "-" + safeFilename(filename));
    }

    private void resetActiveTransfer(boolean deleteFile) {
        synchronized (transferLock) {
            if (activeTransferOutput != null) {
                try { activeTransferOutput.close(); } catch (Exception ignored) {}
            }
            activeTransferOutput = null;
            if (deleteFile && activeTransferFile != null) {
                try { activeTransferFile.delete(); } catch (Exception ignored) {}
            }
            activeTransferFile = null;
            activeTransferName = null;
            activeTransferMime = null;
            activeTransferAction = null;
        }
    }

    private void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        output.flush();
    }

    private void openTransferredFile(File file, String filename, String mime) {
        try {
            Uri uri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    file
            );
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, safeMime(mime, filename));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Open PDF with"));
        } catch (Exception error) {
            Toast.makeText(this, "Couldn't open the file.", Toast.LENGTH_LONG).show();
        }
    }

    private void shareTransferredFile(File file, String filename, String mime) {
        try {
            Uri uri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    file
            );
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(safeMime(mime, filename));
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share file"));
        } catch (Exception error) {
            Toast.makeText(this, "Couldn't share the file.", Toast.LENGTH_LONG).show();
        }
    }

    private void requestTransferredFileSave(File file, String filename, String mime) {
        try {
            pendingBlobSaveFile = file;
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(safeMime(mime, filename));
            intent.putExtra(Intent.EXTRA_TITLE, safeFilename(filename));
            startActivityForResult(intent, STREAM_BLOB_SAVE_REQUEST);
        } catch (Exception error) {
            pendingBlobSaveFile = null;
            try { file.delete(); } catch (Exception ignored) {}
            Toast.makeText(this, "Couldn't open the Android file picker.", Toast.LENGTH_LONG).show();
        }
    }

    private void streamUrlToDestination(String sourceUrl, Uri destination) {
        streamExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(sourceUrl).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(60000);
                connection.setInstanceFollowRedirects(true);
                connection.setUseCaches(false);
                try (
                        InputStream input = new BufferedInputStream(connection.getInputStream());
                        OutputStream output = getContentResolver().openOutputStream(destination)
                ) {
                    if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
                        throw new IOException("Download failed.");
                    }
                    if (output == null) throw new IOException("Could not open destination.");
                    copy(input, output);
                }
                runOnUiThread(() -> Toast.makeText(
                        this,
                        "File saved successfully.",
                        Toast.LENGTH_SHORT
                ).show());
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(
                        this,
                        "Couldn't save the file.",
                        Toast.LENGTH_LONG
                ).show());
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private class AndroidStreamBridge {
        @JavascriptInterface
        public boolean saveUrl(String url, String filename, String mimeType) {
            if (!isTrustedSiteUrl(url)) return false;
            final String name = safeFilename(filename);
            final String mime = safeMime(mimeType, name);
            runOnUiThread(() -> {
                try {
                    pendingUrl = url;
                    pendingUrlName = name;
                    pendingUrlMime = mime;
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType(mime);
                    intent.putExtra(Intent.EXTRA_TITLE, name);
                    startActivityForResult(intent, STREAM_URL_SAVE_REQUEST);
                } catch (Exception error) {
                    pendingUrl = null;
                    pendingUrlName = null;
                    pendingUrlMime = null;
                    Toast.makeText(
                            VerifiedMainActivity.this,
                            "Couldn't open the Android file picker.",
                            Toast.LENGTH_LONG
                    ).show();
                }
            });
            return true;
        }

        @JavascriptInterface
        public boolean beginBlobTransfer(String filename, String mimeType, String action) {
            String normalizedAction = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
            if (!(normalizedAction.equals("open")
                    || normalizedAction.equals("share")
                    || normalizedAction.equals("save"))) {
                return false;
            }
            synchronized (transferLock) {
                resetActiveTransfer(true);
                try {
                    activeTransferName = safeFilename(filename);
                    activeTransferMime = safeMime(mimeType, activeTransferName);
                    activeTransferAction = normalizedAction;
                    activeTransferFile = newTransferFile(activeTransferName);
                    activeTransferOutput = new FileOutputStream(activeTransferFile);
                    return true;
                } catch (Exception error) {
                    resetActiveTransfer(true);
                    return false;
                }
            }
        }

        @JavascriptInterface
        public boolean appendBlobChunk(String base64Chunk) {
            if (base64Chunk == null || base64Chunk.isEmpty()) return false;
            synchronized (transferLock) {
                if (activeTransferOutput == null || activeTransferFile == null) return false;
                try {
                    byte[] decoded = Base64.decode(base64Chunk, Base64.DEFAULT);
                    activeTransferOutput.write(decoded);
                    return true;
                } catch (Exception error) {
                    resetActiveTransfer(true);
                    return false;
                }
            }
        }

        @JavascriptInterface
        public boolean finishBlobTransfer() {
            final File file;
            final String filename;
            final String mime;
            final String action;
            synchronized (transferLock) {
                if (activeTransferOutput == null || activeTransferFile == null) return false;
                try {
                    activeTransferOutput.flush();
                    activeTransferOutput.close();
                } catch (Exception error) {
                    resetActiveTransfer(true);
                    return false;
                }
                file = activeTransferFile;
                filename = activeTransferName;
                mime = activeTransferMime;
                action = activeTransferAction;
                activeTransferOutput = null;
                activeTransferFile = null;
                activeTransferName = null;
                activeTransferMime = null;
                activeTransferAction = null;
            }

            runOnUiThread(() -> {
                if ("open".equals(action)) {
                    openTransferredFile(file, filename, mime);
                } else if ("share".equals(action)) {
                    shareTransferredFile(file, filename, mime);
                } else {
                    requestTransferredFileSave(file, filename, mime);
                }
            });
            return true;
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == STREAM_URL_SAVE_REQUEST) {
            String url = pendingUrl;
            pendingUrl = null;
            pendingUrlName = null;
            pendingUrlMime = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null && url != null) {
                streamUrlToDestination(url, data.getData());
            }
            return;
        }

        if (requestCode == STREAM_BLOB_SAVE_REQUEST) {
            File source = pendingBlobSaveFile;
            pendingBlobSaveFile = null;
            if (resultCode != RESULT_OK
                    || data == null
                    || data.getData() == null
                    || source == null
                    || !source.exists()) {
                if (source != null) source.delete();
                return;
            }
            Uri destination = data.getData();
            streamExecutor.execute(() -> {
                try (
                        InputStream input = new FileInputStream(source);
                        OutputStream output = getContentResolver().openOutputStream(destination)
                ) {
                    if (output == null) throw new IOException("Could not open destination.");
                    copy(input, output);
                    runOnUiThread(() -> Toast.makeText(
                            this,
                            "File saved successfully.",
                            Toast.LENGTH_SHORT
                    ).show());
                } catch (Exception error) {
                    runOnUiThread(() -> Toast.makeText(
                            this,
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
        resetActiveTransfer(true);
        streamExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void startActivity(Intent intent) {
        if (isApkInstallIntent(intent) && !isTrustedStatArchiveUpdate()) {
            Toast.makeText(
                    this,
                    "Update verification failed. The APK was not installed.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }
        super.startActivity(intent);
    }

    private boolean isApkInstallIntent(Intent intent) {
        if (intent == null) {
            return false;
        }
        return Intent.ACTION_VIEW.equals(intent.getAction())
                && APK_MIME.equalsIgnoreCase(intent.getType());
    }

    @SuppressWarnings("deprecation")
    private boolean isTrustedStatArchiveUpdate() {
        try {
            File apk = new File(getCacheDir(), "updates/stat-archive-update.apk");
            if (!apk.isFile() || apk.length() < 50_000) {
                return false;
            }

            PackageManager packageManager = getPackageManager();
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? PackageManager.GET_SIGNING_CERTIFICATES
                    : PackageManager.GET_SIGNATURES;

            PackageInfo candidate = packageManager.getPackageArchiveInfo(
                    apk.getAbsolutePath(),
                    flags
            );
            PackageInfo installed = packageManager.getPackageInfo(
                    getPackageName(),
                    flags
            );

            if (candidate == null
                    || installed == null
                    || !getPackageName().equals(candidate.packageName)) {
                return false;
            }

            Signature[] candidateSignatures = signaturesOf(candidate, false);
            Signature[] installedSignatures = signaturesOf(installed, true);

            if (candidateSignatures == null
                    || installedSignatures == null
                    || candidateSignatures.length == 0
                    || installedSignatures.length == 0) {
                return false;
            }

            for (Signature candidateSignature : candidateSignatures) {
                boolean matched = false;
                for (Signature installedSignature : installedSignatures) {
                    if (candidateSignature.equals(installedSignature)) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    return false;
                }
            }

            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private Signature[] signaturesOf(PackageInfo info, boolean includeHistory) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (info.signingInfo == null) {
                return new Signature[0];
            }
            if (includeHistory && !info.signingInfo.hasMultipleSigners()) {
                Signature[] history = info.signingInfo.getSigningCertificateHistory();
                if (history != null && history.length > 0) {
                    return history;
                }
            }
            Signature[] current = info.signingInfo.getApkContentsSigners();
            return current == null ? new Signature[0] : current;
        }
        return info.signatures == null ? new Signature[0] : info.signatures;
    }
}
