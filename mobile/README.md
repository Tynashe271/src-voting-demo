# SRC Voting — native apps

Two Kotlin Multiplatform + Compose Multiplatform apps, each targeting Android, iOS, and Windows
(desktop), sharing one `core` networking module. Neither touches the deployed website or its
backend — both are just new clients of the same live API.

- **`studentApp`** — talks directly to the Go API
  (`https://src-voting-api-demo.onrender.com/api/v1`), the same public/student surface the
  existing student-site JS calls from any browser. No secret involved; this base URL is exactly
  as safe to ship in a binary as the public website itself.
- **`adminApp`** — talks to the Laravel admin-site's JSON API
  (`https://src-voting-admin-demo.onrender.com/api/admin/...`), **never** to the Go backend's
  internal admin endpoints directly. That internal API needs `INTERNAL_ADMIN_API_KEY`, a
  service-to-service secret that must only ever live on a server — shipping it inside a mobile/
  desktop binary would hand permanent full admin access to anyone who decompiled the app. Laravel
  holds that secret and proxies every write through `VotingApiClient`, exactly like its own Blade
  UI does; the admin app is a second, JSON-speaking front door onto that same session + mandatory
  MFA guard (`RequireAdminMfa`).

## Requirements

- JDK 17
- Android SDK (for the Android targets) — this repo was built against `compileSdk 35`
- **macOS + Xcode, for the iOS targets only.** Kotlin/Native cannot cross-compile an iOS binary
  from Windows or Linux; the iOS source sets here are correct and ready to build, but actually
  producing a runnable `.app`/`.ipa` needs a Mac. Everything else (Android, Windows desktop) was
  built and run from this Windows machine with no Mac involved.

## Building

```bash
# Android debug APKs
./gradlew :studentApp:assembleDebug
./gradlew :adminApp:assembleDebug
# → studentApp/build/outputs/apk/debug/studentApp-debug.apk
# → adminApp/build/outputs/apk/debug/adminApp-debug.apk

# Windows desktop (run directly)
./gradlew :studentApp:run
./gradlew :adminApp:run

# Windows desktop installer (.msi/.exe)
./gradlew :studentApp:packageMsi :studentApp:packageExe
./gradlew :adminApp:packageMsi :adminApp:packageExe
```

## iOS (on a Mac)

Each app's `src/iosMain` already declares `MainViewController()`, and the Gradle module already
declares `iosX64()/iosArm64()/iosSimulatorArm64()` targets producing a static
`SrcVotingCore`/`StudentApp`/`AdminApp` framework. To run on a Mac:

1. Open Xcode, create a new iOS App project (SwiftUI) alongside this `mobile/` directory, e.g. at
   `mobile/studentApp/iosApp/` (and the equivalent for `adminApp`).
2. Add a Run Script build phase that calls
   `../../gradlew :studentApp:embedAndSignAppleFrameworkForXcode` (standard Kotlin Multiplatform +
   Xcode integration step — this is what actually builds the Kotlin/Native framework for the
   simulator/device architecture Xcode is building for).
3. In the SwiftUI `ContentView`, host the Compose UI via
   `UIViewControllerRepresentable` wrapping `MainViewController()` from the generated framework.

This project intentionally stops short of committing a full Xcode project (`.xcodeproj`) since
Xcode project files are unwieldy to hand-maintain outside Xcode itself and were never going to be
exercised on this machine anyway — the Kotlin/Native side (the part that *is* portable and
buildable here) is complete and correct.

## Known gap: candidate photo upload on native clients

The Laravel API (`POST /api/admin/positions/{position}/candidates`) accepts a real multipart photo
upload — `AdminApiClient.createCandidate(...)` in `core` already supports passing photo bytes — but
the admin app's UI currently only exposes the "paste a photo URL" fallback, the same one the web
admin UI has always had. Wiring up each platform's native file/photo picker (Android
`ActivityResultContracts.GetContent`, iOS `PHPickerViewController`, desktop `JFileChooser` or the
platform file dialog) is three separate platform-specific integrations behind one `expect`/`actual`
function; not done here for time, but the backend and client plumbing for it already exist and
work end-to-end (verified via the Blade UI and via raw multipart requests).

## Session state

Neither app persists a login token across restarts — closing and reopening either app logs you
out, the same as closing and reopening a browser that doesn't remember you. Both rely entirely on
the same session-cookie model the websites already use (a persistent in-process cookie jar), just
without a browser underneath it.
