# Vianshield

Vianshield is an Android firewall/VPN being developed as a standalone app first. Sidebar integration is planned only after the standalone runtime has passed device-level validation.

## Current development status

The active implementation branch is:

`refactor/engine-neutral-purge`

The branch now contains a TrackerControl/NetGuard-derived native packet engine (`libnetguard.so`), the GotaTun WireGuard bridge (`libwgbridge.so`), a Vianshield-owned `VpnService`/TUN lifecycle, app-level Block/VPN/Direct policy, encrypted WireGuard profile storage and persisted firewall settings.

The native engine is integrated behind **one** Android VPN service: `VianshieldVpnService` owns the TUN and lifecycle. `eu.faircode.netguard.ServiceSinkhole` is only a plain JNI compatibility facade; it is not registered or started as a second Android service.

### Implemented in this branch

- Local firewall mode can start without a WireGuard profile.
- WireGuard mode uses the same TUN and native packet engine, with GotaTun as the optional remote egress.
- Known installed apps default to the normal firewall path. Block rules are evaluated before routing. Direct routing is supported through the native per-UID route table.
- Shared Android UIDs are treated conservatively: Block wins; Direct is used only when every package sharing the UID explicitly selects Direct.
- Unknown app-range UIDs fail closed. Unowned system UIDs below 2000 retain the upstream safe-system fallback.
- App inventory uses Android PackageManager and actual app icons. User and system app views are separate.
- WireGuard profiles are encrypted with AES-GCM using an Android Keystore key. The Keystore-bound profile blob is excluded from backup/device transfer because its key is not transferable.
- DNS provider selection and other firewall settings are persisted. Changing the DNS provider restarts the active TUN so Android receives the selected resolver list.
- Firewall rule changes update the native per-UID route table and request a re-check of already-open sessions.
- Packet destinations and DNS names are deliberately not written to Vianshield operational logs.

### Not active yet

- The local Ad/Tracker Filter master switch and domain allow-list are saved, but the local domain-blocking callback is still a no-op. Selecting AdGuard DNS uses that external resolver; it is not the same as the local filter.
- Tracker analysis, domain evidence storage, app usage attribution and country attribution are not connected. The Tracker tab remains a placeholder.
- Per-app speed exceptions and screen-aware rules are stored for the future enforcement layer but do not yet affect packet handling. Global speed preset selection is stored; it does not currently cap bandwidth.
- Physical Android device validation has not been completed. Native compilation and an APK build are not proof that traffic is correctly protected on a device.

## Build

Requirements: Android Studio/SDK, JDK 17, the Android NDK and CMake, plus the pinned Rust toolchain and `cargo-ndk` used by the workflow.

Typical local commands:

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

The debug APK is generated under:

`app/build/outputs/apk/debug/app-debug.apk`

The configured standalone ABIs are ARMv7 (`armeabi-v7a`) and ARM64 (`arm64-v8a`). GitHub Actions builds/tests the APK and uploads it as an artifact. Check the workflow run for the exact commit before treating that commit as build-verified.

## Architecture and validation

See [BLUEPRINT.md](BLUEPRINT.md) for the current ownership model, packet policy, Sidebar integration boundary and validation checklist.

See [the native adapter plan](docs/TRACKERCONTROL_NATIVE_ADAPTER_PLAN.md) and [the upstream import audit](docs/TRACKERCONTROL_IMPORT_AUDIT.md) for implementation and source/licensing details.

## Licensing

The TrackerControl-derived portions are distributed under GPL-3.0. The source import audit still tracks third-party library and dataset notices that must be checked individually before distribution.
