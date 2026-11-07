# Vianshield

Vianshield is an Android firewall application shell being prepared for a future engine integration. The current build preserves the planned five-tab experience and selected supporting tools, but **it is not currently an operational firewall or VPN**.

The repository is maintained directly on GitHub. The active preparation branch is `cleanup/prep-work`; `main` is kept separate until an explicit review and merge decision.

## Current implementation status

| Area | Current state |
|---|---|
| Main dashboard | Present. The primary protection control reports that runtime integration is pending; no protection is active. |
| Firewall | Installed-app inventory and policy UI are present. App blocking and VPN/Direct routing enforcement and persistent app-rule storage are not integrated. |
| VPN profiles | WireGuard `.conf` parsing/validation, import preview, and in-session profile selection are present. Profiles are currently held in memory and do not establish a tunnel. |
| Tracker | Intentionally blank fifth tab. No tracker detection or TrackerControl runtime is included. |
| Settings | Log Keeper and diagnostics are available. Planned preferences and Backup/Restore remain unavailable. |
| DNS and Ad Filter | Navigation/UI shells only. DNS changes, filtering, domain/link whitelist editing and enforcement are not active. |
| Socket buffer / speed controls | Not implemented. |
| Runtime | No Android `VpnService`, TUN owner, packet engine, or active engine adapter is included in the current source tree. |

Do not interpret a visible screen, state model, parser, profile selection, log category, or dependency declaration as evidence that runtime enforcement exists.

## Project documentation

- [Current architecture and future runtime boundary](docs/ARCHITECTURE.md)
- [Approved product and architecture decisions](docs/DECISIONS.md)
- [Preparation checklist and acceptance gates](docs/CLEANUP_CHECKLIST.md)
- [Current architecture overview](BLUEPRINT.md)
- [Historical pre-cleanup blueprint snapshot](docs/archive/BLUEPRINT_PRE_ENGINE_NEUTRAL.md)
- [Historical implementation receipts](receipts/)

The current architecture documents describe the checked-in preparation-stage source. Historical receipts preserve earlier work and research; they are not current implementation instructions.

## Build and test

### Requirements

- Android Studio with Android SDK support.
- JDK 17.
- Android Gradle Plugin 9.1.1 and Kotlin 2.2.10 as declared in the version catalog.
- The GitHub Actions workflow currently installs Gradle 9.3.1.

The repository currently does not include the `gradlew` wrapper scripts. Use a compatible local Gradle installation or Android Studio's Gradle integration. The current Android source does not consume a Gemini API key; no `.env` secret setup is required for the present build.

### Commands

Run from the repository root:

```bash
gradle :app:testDebugUnitTest --stacktrace
gradle :app:assembleDebug --stacktrace
```

The debug APK is produced at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions runs unit tests and assembles/verifies the debug APK for pushes to `main` and `cleanup/**`, pull requests targeting `main`, and manual dispatch. CI build success does not establish physical-device stability or firewall functionality.

## Scope and safety

This repository is in a preparation stage. It does not currently provide traffic blocking, VPN tunnelling, DNS filtering, ad blocking, tracker detection, speed limiting, kill-switch enforcement, or backup/restore. Do not rely on it to protect network traffic.

WireGuard profile parsing validates configuration text; it does not connect a tunnel. Profile state is not yet stored in a durable profile repository. Settings persistence and backup formats are also pending.

See the [architecture document](docs/ARCHITECTURE.md) for current component responsibilities, data-retention behavior, limitations, and the proposed future runtime integration contract. Licence and privacy readiness are tracked separately in [Step 5](docs/CLEANUP_CHECKLIST.md#step-5--licensing-and-privacy-preparation).
