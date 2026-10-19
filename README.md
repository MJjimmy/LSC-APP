# LSC Android app

Kotlin/XML Android client for the existing LSC PHP website. It uses a native welcome screen and a restricted WebView; it does not use Jetpack Compose or replace the PHP backend.

## Source and backend

- Working application source: `master` (the repository default branch).
- Android application ID: `com.example.lsc_app`.
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

## Coursework evidence boundary

A successful build and JVM tests do not prove every feature works on a physical phone. Registration/login, real Google return, settings persistence, collaboration features, invalid input and the narrated demonstration still need physical-device evidence. The new file picker, multipart uploads and authenticated downloads also need device testing. The project owner reports lecturer approval to use GitHub instead of Azure DevOps; keep that approval alongside the separately required team records and lecturer feedback.

Do not commit `local.properties`, signing keys, provider secrets, passwords or generated build files. Do not expose those values in screenshots or the demonstration video.
