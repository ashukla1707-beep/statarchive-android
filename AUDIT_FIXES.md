# Android audit fixes - 27 September 2026

Addresses findings 2, 7 and 9 from the audit; coordinated website changes are required.

- Removes registration of addJavascriptInterface bridges. WebMessageListener permits only the exact HTTPS Stat Archive origin and main frame. Navigation uses the same exact origin/port policy. Unsupported WebView implementations fail closed.
- Saved passcodes are not exposed through the new dispatch allowlist. Android autofill remains enabled; users can also enter credentials manually.
- Stream transfers are bounded, reject overlapping active transfers, and report save completion only after a successful write and output close. Picker cancellation, startup failure, write failure, and activity destruction reject pending saves.
- Native scanner callbacks verify the current trusted URL before injecting results.
- Gradle reconstructs and SHA-256-verifies the approved splash before resource processing. Launcher icons were regenerated from the approved asset. Clean local builds no longer depend on CI-only splash reconstruction.
- Prepares versionCode 33 / versionName 1.5.26; no release APK is committed or published by this branch.

## Validation

`gradle testDebugUnitTest lintDebug assembleDebug` is added as a pull-request/push check without signing secrets. BridgeOrigin unit tests cover HTTPS, default/explicit port 443, subdomains, lookalike hosts, alternate ports, user-info, malformed URLs and non-web schemes. The approved splash bytes were reconstructed locally and their expected SHA-256 was verified.

## Release order

Deploy the paired website native-bridge.js adapter before releasing this APK. Run real-device tests for scanner capture/gallery, save/cancel/write failure, share/open, back navigation, WebView updates, and upgrade installation with the production signing certificate. Publish a signed APK and then update website version.json. This branch does not automatically publish or merge a release.

CI also exposed a pre-existing splash Back-handler lint error. It now uses the lifecycle-aware Back dispatcher and cancels deferred launching on destruction. Locale-independent filename handling, flexible splash orientation, icon declarations, and density-independent splash resource placement were corrected while reviewing lint diagnostics.
