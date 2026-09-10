package com.statarchive.app;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.widget.Toast;

import java.io.File;

/**
 * Final launch activity. SafeMainActivity owns WebView/bridge/Back safety;
 * this layer adds a last-mile trust check for the in-app APK installer.
 */
public class VerifiedMainActivity extends SafeMainActivity {

    private static final String APK_MIME = "application/vnd.android.package-archive";

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

            /* Every signer on the candidate APK must be trusted by the
               currently installed application. This also supports a valid
               signing-certificate history after an Android key rotation. */
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
