# TrackerControl Import Audit

Audit baseline: TrackerControl upstream commit `4a3d1939b3e7859540367e15d597d54cb2e6a118` (2026-11-01).

Source checkout: `third_party/tracker-control` Git submodule. This document describes the upstream structure at the pinned revision; it does not claim that these components are already linked into the Vianshield APK.

## 1. What TrackerControl actually contains

TrackerControl is a complete Android application, not a drop-in firewall library. Its implementation has four tightly connected parts:

1. Android VPN service and lifecycle: `eu.faircode.netguard.ServiceSinkhole`, plus `ApplicationEx`, service receivers/workers, VPN replacement/restart policy, foreground notification and Android manifest declarations.
2. Native packet engine: C sources under `app/src/main/jni/netguard/`, built as `libnetguard.so`. The JNI boundary exchanges packet, UID, routing, DNS and usage information with Java.
3. WireGuard transport: Rust project `wgbridge-rs/` (GotaTun-based), Java JNI wrappers under `net/kollnig/missioncontrol/wgbridge/`, and the Gradle script `app/gradle/wgbridge.gradle` which builds ABI-specific `libwgbridge.so` using Cargo/cargo-ndk and the Android NDK.
4. Tracker intelligence and presentation: tracker signatures/analysis, tracker and blocklist models, app/domain usage storage, country lookup, and Android/Compose screens.

## 2. Runtime components to adapt

### VPN service and packet processing

- `eu.faircode.netguard.ServiceSinkhole` owns the upstream `VpnService` and its TUN lifecycle.
- `app/src/main/jni/netguard/netguard.c`, `ip.c`, `tcp.c`, `udp.c`, `dns.c`, `policy.c` and supporting files implement the packet path and JNI.
- `DatabaseHelper`, `Rule`, `Allowed`, `Packet`, `ResourceRecord` and `Usage` provide the Java-side models/storage used by the packet path.

**Integration constraint:** Vianshield must retain one authoritative VPN service/TUN owner. Do not register or start TrackerControl's `ServiceSinkhole` beside `VianshieldVpnService`. The packet engine and service logic must be extracted/adapted behind Vianshield's existing lifecycle, or the existing service must be deliberately replaced after a reviewed migration. Two concurrent VPN service owners are not an acceptable integration.

### WireGuard

- Rust source: `wgbridge-rs/`, including the `tc-dns` crate.
- Build: `app/gradle/wgbridge.gradle` plus `app/CMakeLists.txt`.
- JNI wrapper: `Wgbridge`, `Tunnel`, `TunnelStats`, `Protector`, `Logger`, `DnsRecorder`.
- ABI list in upstream build: armeabi-v7a, arm64-v8a, x86, x86_64.

The existing Vianshield WireGuard profile parser is not equivalent to the upstream UAPI configuration, socketpair, tunnel, protector, DNS recorder and callback contract. A deliberate adapter and lifecycle/FD ownership review is required. Do not claim WireGuard is operational merely because the Rust library compiles.

### Tracker intelligence and UI

- Tracker identification: `TrackerAnalysisManager`, `TrackerAnalysisWorker`, `TrackerLibraryAnalyser`, `TrackerSignatureManager`, `SignatureTrie`.
- Tracker data: `Tracker`, `TrackerList`, `TrackerCategory`, `TrackerContact`, `TrackerBlocklist`, `TrackerFeedLogic`, `TrackerStatusLogic`.
- Per-app evidence: `DatabaseHelper`, `Usage`, DNS/resource records and host observations.
- Country attribution: `CountriesFragment` and GeoLite2 country database.
- UI reference: upstream app list/details, tracker, data and country views, including Compose screens and legacy fragment/activity integration.

Vianshield should adapt the data models and view logic into its own Compose navigation and theme rather than importing TrackerControl's launcher, activities, fragments, theme or duplicate app shell.

## 3. Native build and dependencies

Upstream currently uses:
- Android Gradle application module with compile SDK 37 and Java 17.
- CMake to build `libnetguard.so`.
- Rust/Cargo and `cargo-ndk` to build `libwgbridge.so`.
- Android NDK version pinned in `app/gradle/wgbridge.gradle`.
- Additional AndroidX, WorkManager, Compose, database, GeoIP, DEX analysis and image dependencies declared in upstream `app/build.gradle`.

Do not copy the upstream Gradle file wholesale. Vianshield has a different application ID, namespace, Compose UI, min/target SDK decisions, test setup, lifecycle, and CI pipeline. Extract the required dependencies and native tasks incrementally, keeping Vianshield's build as the root build.

## 4. Data and licensing audit still required

The upstream tree includes assets such as `trackers.json`, `hosts.txt`, `GeoLite2-Country.mmdb`, and several tracker/blocklist datasets. The repository's top-level GPL-3.0 license does not establish the license of every bundled dataset or third-party library. Before copying each data asset or generated database into Vianshield, record its origin, license, redistribution terms, update mechanism and attribution.

Also inventory the licenses for native dependencies (including GotaTun and Rust crates), Gradle dependencies, fonts, icons, images, GeoIP data and downloaded feeds. Keep the original notices for every copied component and mark modified source files.

## 5. Safe integration sequence

1. Finish source/dependency/license inventory for the pinned revision.
2. Extract the packet engine and its JNI contract into Vianshield without adding a second VPN service.
3. Add a Vianshield runtime adapter that owns TUN descriptor lifetime, protect callbacks, start/stop serialization, foreground service state and fail-closed errors.
4. Add and test WireGuard bridge build/runtime integration separately from packet engine integration.
5. Adapt tracker analysis, domain evidence, usage and country attribution to Vianshield's storage/logging and UI.
6. Connect the Tracker tab to real data only after evidence is being collected by the runtime.
7. Verify app blocking, direct routing, DNS filtering, WireGuard routing, reboot/restart, network changes, descriptor cleanup and Android versions on device.

Until these steps are complete, the Vianshield engine-neutral VPN shell remains intentionally unprotected and the Tracker tab remains a placeholder.
