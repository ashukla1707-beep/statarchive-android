# Stat Archive TWA Android project

This project wraps **https://stat-archive.lustats.workers.dev/** as a Trusted Web Activity.

## Android configuration

- Package: `com.statarchive.app`
- Min SDK: 24
- Target SDK: 36 (Android 16)
- Compile SDK: 36
- Android Browser Helper: 2.7.2
- Release signing key: `statarchive-release.jks`

## Before building

Upload the contents of the `website/` folder to the matching paths on your site:

- `/manifest.json`
- `/icons/icon-192.png`
- `/icons/icon-512.png`
- `/icons/icon-512-maskable.png`
- `/.well-known/assetlinks.json`

The `assetlinks.json` file is already generated for this project's release signing certificate.
Serve it as JSON over HTTPS with no login or redirect.

## Build in Android Studio

1. Install a current Android Studio with Android SDK 36.
2. Open this folder as an Android project.
3. Let Gradle sync and download dependencies.
4. Build > Generate Signed App Bundle / APK.
5. Choose APK for direct sharing or Android App Bundle (AAB) for Google Play.
6. Use `statarchive-release.jks`, alias `statarchive`, and the password in `SIGNING_INFO.txt`.

## Verification

After deploying `assetlinks.json`, opening the app should use a full-screen Trusted Web Activity instead of showing a Custom Tab address bar. If verification fails, first verify that this exact URL returns the JSON file:

`https://stat-archive.lustats.workers.dev/.well-known/assetlinks.json`

## Security

Do not publish or commit `statarchive-release.jks` or `SIGNING_INFO.txt` to a public repository.
