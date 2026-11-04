# TrackerControl Import Audit

Audit baseline: TrackerControl upstream commit `4a3d1939b3e7859540367e15d597d54cb2e6a118`.

Source checkout: `third_party/tracker-control` Git submodule. This document separates the upstream source inventory from the Vianshield implementation status.

## 1. Upstream source inventory

TrackerControl is a complete Android application, not a drop-in firewall library. The pinned source contains:

1. Android VPN service/lifecycle: `eu.faircode.netguard.ServiceSinkhole`, plus application/service receivers/workers, restart policy, foreground notification and manifest declarations.
2. Native packet engine: C sources under `app/src/main/jni/netguard/`, built as `libnetguard.so`.
3. WireGuard transport: Rust `wgbridge-rs/` (GotaTun-based), Java JNI wrappers under `net/kollnig/missioncontrol/wgbridge/`, and native CMake/Gradle integration.
4. Tracker intelligence and presentation: signature/analysis components, tracker and blocklist models, app/domain usage storage, country lookup and Android/Compose screens.

The upstream source includes dependencies and datasets whose licensing must be evaluated individually. The root GPL-3.0 license does not prove that every nested asset is GPL-compatible.

## 2. Vianshield integration boundary

Vianshield retains `com.example.vpn.VianshieldVpnService` as the sole Android VPN and TUN owner. It does **not** start the upstream `ServiceSinkhole` Android service.

The class name `eu.faircode.netguard.ServiceSinkhole` is retained only as a plain-Java JNI compatibility facade. It owns a native context and packet worker, but not an Android service or the TUN descriptor.

The current runtime structure is:

```text
VianshieldVpnService
  ├── Android TUN / foreground lifecycle / network callback
  ├── ServiceSinkhole (plain JNI facade)
  │     └── libnetguard.so (pinned TrackerControl C packet engine)
  └── WgEgress (optional)
        └── Wgbridge / libwgbridge.so (GotaTun Rust tunnel)
```

## 3. Components now present in Vianshield branch

- JNI facade and upstream model classes: `Packet`, `Allowed`, `ResourceRecord`, `Usage`.
- NetGuard C packet engine built through Vianshield CMake.
- Pinned-source CMake patch for refreshing established sessions after native route/policy changes.
- Per-app policy callbacks, UID attribution, Direct UID route table and shared-UID handling.
- GotaTun bridge JNI wrappers, adapted `WgEgress`, config serialization and connectivity monitor.
- Encrypted WireGuard profile storage backed by Android Keystore.
- Vianshield-owned TUN, foreground notification, native packet worker, optional WireGuard egress and serialized teardown/reconfigure path.

The branch includes these code paths; device-level correctness remains unverified until tested on physical Android devices.

## 4. Upstream modules not yet adapted

Tracker analysis and presentation are still outstanding. Relevant upstream sources include:

- `TrackerAnalysisManager`, `TrackerAnalysisWorker`, `TrackerLibraryAnalyser`, `TrackerSignatureManager`, `SignatureTrie`.
- `Tracker`, `TrackerList`, `TrackerCategory`, `TrackerContact`, `TrackerBlocklist`, `TrackerFeedLogic`, `TrackerStatusLogic`.
- Per-app/domain evidence storage through `DatabaseHelper`, `Usage`, DNS/resource records and host observations.
- GeoLite2 country data and related attribution UI.
- Upstream Android launcher/activity/fragment screens, which should not be imported as a second app shell.

Vianshield's Tracker tab is still a placeholder. The JNI usage/DNS callbacks are not yet connected to a tracker evidence store.

## 5. DNS filtering status

The native packet engine exposes a domain decision callback. Vianshield currently returns `false` from `isDomainBlocked(String)`, so local domain blocking is not active.

The DNS provider selector changes the DNS server list installed on the Android TUN. Selecting AdGuard DNS uses that external resolver; it must not be described as the local Vianshield Ad/Tracker filter.

The local Ad/Tracker master switch and domain allow-list are persisted settings only. A future implementation must choose a DNS blocklist source, honor its license, parse its actual supported syntax (not assume every entry is a hosts-file line), define allow-list precedence, cap memory/storage, refresh safely and provide attribution/update behavior.

## 6. Native and Gradle build inventory

Vianshield builds the C packet-engine sources from the pinned submodule through `app/CMakeLists.txt`. Rust/Cargo and `cargo-ndk` build the ABI-specific GotaTun `libwgbridge.so`, then CMake links the JNI packet engine against it for the DNS C ABI.

The current Vianshield application is configured for ARMv7 and ARM64. It does not copy TrackerControl's application ID, manifest, launcher, theme or full Gradle configuration.

## 7. Licensing work still required

- TrackerControl-derived code is GPL-3.0 and the repository includes a top-level GPL-3.0 license and third-party notices.
- Keep upstream copyright/license headers in copied source; mark adapted files.
- Audit the Rust crate dependencies, native dependencies, AndroidX/Gradle dependencies, fonts, icons, images, hostlists, tracker feeds, signatures and GeoIP data separately.
- Record source, license, redistribution conditions, attribution and update mechanism before bundling any data asset/feed.

## 8. Required validation

On physical devices and supported Android versions:

- Blocked app cannot use IPv4, IPv6, TCP, UDP or DNS; already-open sessions are terminated after Block is applied.
- Allowed app retains internet access in local firewall-only mode.
- Direct/VPN routing works for shared UIDs and respects WireGuard AllowedIPs.
- DNS provider selection and local DNS filtering have distinct verified behavior.
- WireGuard handshake and app traffic work; network handover recovers without leakage.
- VPN revoke, process stop, reboot, repeated start/stop/reconfigure and descriptor cleanup work.
- Foreground service, battery/background behavior and Android version-specific UID attribution are verified.

A green compile/APK build is necessary but not sufficient evidence of runtime protection.
