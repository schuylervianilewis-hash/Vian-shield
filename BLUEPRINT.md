# Vianshield — Standalone Preparation Blueprint

## 1. Current Direction

Vianshield is being prepared as a standalone Android firewall/VPN application first. Once its TrackerControl-based runtime and user-facing features are validated on a real device, the proven feature will be integrated into Sidebar.

The final Sidebar architecture is planned around:
- Sidebar Main process owning the VPN service, tunnel lifecycle, enforcement runtime and authoritative protection state.
- Sidebar Heavy process hosting the Vianshield settings UI and tracker presentation.
- Existing Sidebar Binder IPC/recovery infrastructure connecting the two processes.
- One Android VPN service/TUN owner.

The standalone stage is a validation vehicle. It is not a commitment to retain a separate published app or its current package identity.

## 2. Selected Runtime Direction

- Remove the existing Sing-box integration.
- Use TrackerControl as the selected networking/firewall foundation, subject to source audit and deliberate extraction.
- Preserve Vianshield's own UI, theme, feature presentation and existing logging experience.
- Do not run Sing-box and TrackerControl as competing VPN engines.
- Keep engine-specific implementation details behind a Vianshield runtime boundary.
- Do not claim protection is active until the selected runtime and TUN are confirmed running.
- Until the new runtime is integrated, VPN start requests fail closed and report that protection is unavailable.

TrackerControl is an Android application rather than a ready-made Android library. Its services, resources, native code, storage, preferences and runtime dependencies require an incremental extraction/adaptation plan.

## 3. Current Standalone Preparation Phase

### Completed in the engine-neutral purge branch
- [x] Create a working branch from the inspected main baseline.
- [x] Remove Sing-box adapter, engine contract and storage manager.
- [x] Remove Sing-box JSON generation from the WireGuard profile parser.
- [x] Preserve WireGuard parsing, validation, profile redaction and import support.
- [x] Remove the synthetic default WireGuard test profile from normal app state.
- [x] Refactor Android VpnService shell to fail closed while no engine is installed.
- [x] Remove Sing-box-specific UI, diagnostics and log-export claims.
- [x] Replace the native Sing-box CI build with the existing Gradle test/APK pipeline.
- [x] Remove obsolete Sing-box-specific tests and retain parser/logging/ViewModel tests.

### TrackerControl import bootstrap
- [x] Confirm GPL-3.0 licensing and user approval for GPL-3.0 distribution.
- [x] Pin upstream TrackerControl source as a Git submodule for reproducible source audit.
- [x] Preserve upstream GPL-3.0 license text and add third-party attribution notes.
- [x] Configure CI checkout to initialize the pinned submodule.
- [ ] Audit native build, service, packet loop, data/storage, tracker datasets and all nested third-party licenses.
- [ ] Extract and adapt the runtime behind Vianshield's existing single VPN service; do not compile a second VPN service.

### Remaining verification
- [ ] Run the GitHub Actions unit-test and APK workflow on this branch.
- [ ] Fix compilation/test issues revealed by CI.
- [ ] Review the diff for stale engine references and unintended feature removal.
- [ ] Verify APK artifact and both configured ARM ABIs.
- [ ] Confirm on a physical Android device that the app truthfully remains unprotected until a runtime is integrated.
- [ ] Audit current TrackerControl source and licensing before extraction.

## 4. Preserve Existing Vianshield Work

Keep and adapt:
- The Vianshield visual system and five primary tabs: Main, Firewall, VPN, Tracker and Settings. Tracker currently remains a placeholder.
- WireGuard .conf parsing, validation, redacted profile previews and import support.
- Android VPN permission/lifecycle shell and foreground notification infrastructure.
- Operational Log Catcher, Log Keeper, Crash Reporter and Log Exporter.
- Existing tests for parser, logging, crash reporting, log export and UI/ViewModel behavior.
- Existing Gradle/Android build configuration and dual-ARM ABI filters unless TrackerControl native requirements prove a change necessary.

Do not treat UI placeholders as implemented enforcement. The existing firewall rules, DNS/ad-filter settings and speed presets currently include UI/state-only behavior and must be connected to real runtime enforcement in later phases.

Do not include speed throttling in the initial TrackerControl import.

## 5. Feature Integration Plan

1. TrackerControl source audit: enumerate exact firewall rules, app inventory, tracker lists, company attribution, categories, DNS behavior, country details, logging, WireGuard bridge, native dependencies and license obligations.
2. Runtime extraction: adapt the smallest maintainable set of TrackerControl components behind the Vianshield runtime boundary. Preserve a single VpnService/TUN owner.
3. Firewall and routing: connect per-app Block / Direct / WireGuard routing to actual enforcement. Direct traffic must still pass through the local firewall; no Android VPN bypass for Direct.
4. Tracker experience: add the Tracker section and source-supported app detail views for Trackers, Data and Country.
5. Logging: connect TrackerControl runtime events to the existing Log Catcher and Log Keeper without creating competing collectors/stores.
6. Validation: test blocking, routing, DNS/tracker detection, WireGuard, fail-closed behavior, recovery, network transitions, reboot and resource use on a real device.
7. Sidebar integration: move runtime ownership into Sidebar Main and the Vianshield UI into Sidebar Heavy using existing IPC/recovery patterns.
8. Later extension: assess per-app upload/download speed limits after the firewall/tracker baseline is proven.

## 6. APK Build Pipeline

The standalone repository's GitHub Actions workflow remains the build path:
- Set up JDK 17 and Gradle.
- Run :app:testDebugUnitTest.
- Run :app:assembleDebug.
- Stage and verify a non-empty APK outside Gradle-managed output directories.
- Upload the debug APK artifact.

The old Sing-box Go/gomobile/NDK/libbox compilation and AAR verification steps are removed. The Gradle ABI filters remain dual-ARM (armeabi-v7a and arm64-v8a). When TrackerControl's native components are introduced, add only their actual required native build steps and verify the resulting APK ABI contents.

## 7. Important Product Constraints

- No false Connected/Protected state.
- No silent fallback from a failed WireGuard route to Direct.
- One VPN service/TUN owner.
- Main process is authoritative after Sidebar integration; Heavy process must not own VPN lifecycle.
- Heavy UI process exit must not stop VPN protection.
- No telemetry; local processing by default.
- Keep cryptographic keys and credentials redacted in logs/exports.
- Domain allowlist exceptions apply to ad filtering only; firewall block rules still take precedence.
- Speed throttling is deferred.
- Historical receipts documenting the former Sing-box implementation are retained as history, not as current architecture.

## 8. Current Branch

Working branch: refactor/engine-neutral-purge

Main remains the baseline until the purge branch passes CI, review and the user approves merging.