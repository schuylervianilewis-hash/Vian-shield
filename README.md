# Vianshield

Vianshield is an Android firewall/VPN project being prepared as a standalone app before its proven networking feature is integrated into Sidebar.

## Current development status

The engine-neutral purge is in progress on the branch:

`refactor/engine-neutral-purge`

The previous Sing-box integration has been removed from this branch. The Android VPN service shell deliberately fails closed until the selected TrackerControl-based runtime is integrated. It must not report protection as active while no VPN engine is running.

The current standalone app retains the Vianshield Compose UI, WireGuard profile parsing/import, operational logging, Log Keeper, crash reporting and the Gradle APK build pipeline. The five primary tabs are Main, Firewall, VPN, Tracker and Settings; Tracker is currently a placeholder.

## Build locally

Prerequisites:
- Android Studio with a compatible Android SDK.
- JDK 17.
- Android Gradle Plugin / Gradle versions declared by the project.

Open the repository in Android Studio, sync Gradle, then run:

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

The debug APK is generated under:

`app/build/outputs/apk/debug/app-debug.apk`

The GitHub Actions workflow also stages and uploads a debug APK artifact. The app is configured for ARMv7 and ARM64.

## Architecture and roadmap

See [BLUEPRINT.md](BLUEPRINT.md) for the current architecture, TrackerControl integration sequence, Sidebar Main/Heavy process plan, validation requirements and build pipeline.

## Important

TrackerControl source is pinned as a Git submodule under `third_party/tracker-control` for audited extraction. The submodule is not yet linked into the APK runtime.

The current purge branch is a preparation/cleanup build, not a functioning firewall VPN. VPN start requests report that the engine is unavailable. Real traffic enforcement will be restored only after the selected runtime is integrated and verified.
