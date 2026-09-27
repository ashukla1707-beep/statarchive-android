# Stat Archive Android

Android wrapper for **https://stat-archive.lustats.workers.dev/**.

The current app is a native Android **WebView** wrapper. It is not a Trusted Web Activity and it does not use Android Browser Helper.

## Android configuration

- Package: `com.statarchive.app`
- Min SDK: 24
- Target SDK: 36
- Compile SDK: 36
- Java: 17
- Android Gradle Plugin: 8.9.1
- Gradle used by CI: 8.11.1

`LaunchReadyActivity` is the launcher and extends `VerifiedMainActivity`, preserving the ready-aware splash and frame-zero hero handoff. That class extends `SafeMainActivity`, which in turn extends the established `MainActivity`. The two thin wrappers add Android-specific safety fixes while retaining the existing WebView behavior.

## Build locally

1. Install Android Studio with Android SDK 36 and Java 17.
2. Open this repository as the Android project.
3. Let Gradle sync and download dependencies. The `generateApprovedSplash` pre-build task reconstructs and SHA-256-verifies the approved splash automatically; no CI-only setup is needed.
4. For a release build, provide the signing values expected by `app/build.gradle`:
   - `KEYSTORE_FILE`
   - `KEYSTORE_PASSWORD`
   - `KEY_ALIAS`
   - `KEY_PASSWORD`
5. Run `gradle assembleRelease bundleRelease` or use Android Studio's signed APK/AAB build flow.

Do **not** commit a release keystore, passwords, signing-info text files, or other signing secrets to this public repository.

## GitHub Actions build

`.github/workflows/build-apk.yml`:

- reconstructs and verifies the approved splash asset;
- generates launcher icons from that exact splash;
- builds the signed release APK and AAB;
- uploads both as workflow artifacts;
- on `main` only, publishes the APK to `downloads/stat-archive.apk` for the in-app updater.

Required repository secrets are:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

The publishing commit message derives the version from `app/build.gradle`; it is not hard-coded.

## Website relationship

The Android app loads the deployed Stat Archive website directly from:

`https://stat-archive.lustats.workers.dev/`

The canonical website/PWA source is maintained in the separate `stat-archive` repository. There is no duplicate `website/` payload in this Android repository.

## File and scanner bridge

Website code uses the asynchronous `StatArchiveNative` WebMessageListener transport. Only the exact Stat Archive HTTPS origin, port 443, and main frame are accepted. No addJavascriptInterface bridge is exposed. Update the website native-bridge.js adapter before releasing this APK. Unsupported WebView versions receive no native bridge; update Android System WebView to enable native features.

Saved passcodes are no longer exposed to page JavaScript. Use Android autofill or enter the passcode. File-save promises resolve only after Android confirms the destination was written and closed; cancellation rejects without counting a download.

## Update verification

`VerifiedMainActivity` performs a final check before Android's package installer is opened. A downloaded update is accepted only when:

- it parses as an Android package;
- its package name is `com.statarchive.app`; and
- every current signer on the candidate APK matches a signer trusted by the installed app (including signing-certificate history where supported).

This is intentionally a last-mile check in addition to Android's own signature enforcement.

## Security

- Keep the release keystore and all signing credentials private.
- Do not add broad WebView navigation exceptions for untrusted hosts.
- Keep native messaging restricted to the exact Stat Archive origin and the main frame.
- Release APKs must continue to be signed with the same trusted application signing key so Android can verify updates.

