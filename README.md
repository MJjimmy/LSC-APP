# LSC Android app

Kotlin/XML Android client for the existing LSC PHP website.

## Source and backend

- Working application source: `master` (the repository default branch).
- Android application ID: `com.example.lsc_app` (the existing installed package, unchanged).
- Backend: `https://lsc.up.railway.app`.
- Google sign-in opens the system browser and returns through a state/verifier-bound, one-time session exchange. Google and email-provider credentials stay on the server.

## Build and test

Use the committed Gradle wrapper, Java 25 and the Android SDK platform/build tools required by the existing project (compile/target API 37).

```bash
bash ./gradlew :app:testDebugUnitTest :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions runs the same unit-test and APK-build tasks on an Ubuntu runner. Each successful run publishes `LSC-debug-apk` and `LSC-unit-test-results` artifacts. The workflow uses read-only repository permissions, pinned official actions, the runner's preinstalled Java/SDK and a checksum-verified Gradle wrapper.

## Current features

- Native Welcome to LSC with direct Create account, Sign in and Help actions.
- Existing PHP account, study-group, task, file, chat and profile/settings pages in the trusted WebView.
- System-browser Google sign-in with private pending state and verifier checks.
- Version 1.3 (versionCode 4): system document-picker support for photo/file inputs, including requested multiple selection. Only explicitly granted `content://` documents are accepted; no broad storage permission is requested.
- Authenticated, direct same-origin HTTPS downloads: files are staged privately, then saved to a location chosen with Android's document picker. Cookie values remain private and are never forwarded to redirects.
- Picker limits: up to 10 files and 100 MB combined; backend type and size validation still applies. Downloads reject redirects, HTML sign-in responses and files larger than 100 MB. Network and file reads run off the UI thread.
- Connection failure/retry handling and Android Back navigation.

## UI fix (version 1.4.2, versionCode 7; branch `ui-fix`)

- Local LSC adaptive/themed launcher icons and an OS launch screen without an artificial delay; static launch-window fallback before Android 12.
- Light-only native Welcome, workspace/error surfaces, dialogs, launch screen and trusted WebView. Night-qualified dark overrides and WebView algorithmic darkening are disabled, without injected CSS/JavaScript or new dependencies. Status/navigation bars use light surfaces and dark icons where supported; API 24/25 keep a fixed navy navigation-bar contrast fallback because dark navigation icons require API 26. Navy/copper remain brand accents.
- No native top loading line or reserved strip. Exact-document refresh retention, accessible error screen, Retry and Return to Welcome remain. Retry never replays a one-time Google exchange as a GET.
- No extra native top toolbar or duplicate bottom tabs. Existing website navigation and safe Android Back handling remain.
- Pure, tested URL/history/invitation policy. Back skips one-shot auth/logout/download pages and cross-account authentication history.
- Indeterminate picker/validation/download/save feedback, no invented upload percentages or server-success claims. Completed user-saved documents offer Open/Share using read-granted content URIs; no raw private file paths or broad storage permissions.
- Validated HTTPS `/invite.php?token=<64 lowercase hex characters>` entry and native long-press sharing of actual invitation anchors rendered on workspace pages. Backend recipient/expiry/CSRF checks remain authoritative.
- Existing Google repeated-tap/cancel-return fix retained: one pending browser handoff; an unfinished return clears it and reveals Welcome.

See [implementation boundaries and the physical-device checklist](docs/ANDROID-POLISH.md). The debug build is not proof of release-signature compatibility, verified App Links, push delivery or real-device acceptance.

## Coursework evidence boundary

A successful build and JVM tests do not prove every feature works on a physical phone. Registration/login, real Google return, settings persistence, collaboration features, invalid input and the narrated demonstration still need physical-device evidence. The new file picker, multipart uploads and authenticated downloads also need device testing. The project owner reports lecturer approval to use GitHub instead of Azure DevOps; keep that approval alongside the separately required team records and lecturer feedback.

Do not commit `local.properties`, signing keys, provider secrets, passwords or generated build files. Do not expose those values in screenshots or the demonstration video.
