# TrackerControl Native Engine Adapter Plan

Baseline: TrackerControl commit `4a3d1939b3e7859540367e15d597d54cb2e6a118`.
Target: Vianshield branch `refactor/engine-neutral-purge`.

This plan originally described the extraction sequence. The current branch has implemented the native packet facade, service lifecycle and WireGuard bridge portions. Physical-device validation and tracker/domain intelligence remain outstanding.

## 1. Confirmed JNI boundary

The native packet engine resolves the following Java contract:

- `eu.faircode.netguard.ServiceSinkhole`: JNI init/start/run/stop/clear/stats/MTU, WireGuard socketpair/route controls and teardown.
- Model classes: `Packet`, `Allowed`, `ResourceRecord`, `Usage`.
- Callback methods: native exit/error, protect, packet log, DNS record, domain-block decision, UID attribution, address policy and usage accounting.

These names and signatures form a runtime ABI. Changes to the facade must be reviewed against the pinned C JNI source.

## 2. Vianshield ownership boundary — implemented

- `com.example.vpn.VianshieldVpnService` is the only Android `VpnService` and TUN owner.
- `eu.faircode.netguard.ServiceSinkhole` is a plain JNI compatibility facade, not a service.
- The native packet loop borrows the TUN FD. Vianshield stops and joins the loop before closing the FD.
- `WgEgress` optionally owns the GotaTun userspace tunnel behind the same TUN.
- Native start/stop/reconfigure work is serialized on a single lifecycle executor.

## 3. Native packet engine — integrated in the branch

CMake builds the pinned NetGuard packet-engine sources as `libnetguard.so` and links the Rust `libwgbridge.so` dependency required by the DNS C ABI.

The CMake configuration also creates pinned-source patched copies of `netguard.c` and `session.c`. A policy-refresh signal is raised when the native UID routing table is updated; the packet loop re-evaluates existing sessions under its session lock. CMake fails closed at build time if the expected pinned-source anchors are absent.

## 4. Packet policy — integrated, live behavior unverified on devices

- Known installed apps default to allowed firewall traffic unless blocked.
- BLOCK wins for all packages sharing a UID.
- Direct routing is selected only when every package sharing the UID explicitly selects Direct.
- Otherwise, traffic follows the default route (WireGuard when a profile is active; direct/local firewall path without a profile).
- The service uses Android Q+ connection-owner UID lookup; native NetGuard UID lookup handles older Android versions.
- Unknown app-range UIDs are denied. Unowned system UIDs below 2000 retain the upstream safe-system fallback.
- App rules persist in app-private preferences and are pushed into the native route/policy layer.

These are code-path descriptions, not device test results. Validate blocked connectivity, IPv4/IPv6, DNS, existing flows and Direct behavior on physical devices.

## 5. WireGuard bridge — integrated, live handshake unverified on devices

- Rust GotaTun bridge is built by the Vianshield Gradle/Cargo/NDK workflow.
- Pinned Java wrappers and the `WgEgress` adapter are included.
- WireGuard uses the same Vianshield TUN and native packet engine.
- The service marks WireGuard Connected only after a peer handshake is observed; a created native tunnel alone is not reported as connected.
- Network-change rebind/recovery is wired through the existing WgEgress monitor callbacks.

A successful Rust/native compile does not prove UDP sockets can handshake or carry app traffic on a device.

## 6. DNS and tracker integration status

- Android TUN DNS server selection is persisted and applies Profile Default, Cloudflare, Quad9, Google or AdGuard DNS.
- Changing the DNS choice reconfigures the active Vianshield TUN.
- `isDomainBlocked(String)` currently returns false. The local Ad/Tracker filter and saved domain allow-list do not yet enforce DNS blocking.
- DNS record and usage callbacks do not currently populate a tracker evidence store.
- The Tracker tab remains a placeholder.

## 7. Build and validation

- GitHub Actions runs JDK 17, Android SDK/NDK/CMake, pinned Rust toolchain, Gradle unit tests, dual-ARM debug APK assembly, APK verification and artifact upload.
- Check the workflow run attached to the exact branch HEAD before claiming that commit passed CI.
- Physical-device testing is still required for local firewall enforcement, WireGuard handshake/data, network handover, permission revoke, reboot, repeated start/stop and FD/thread cleanup.

## 8. Remaining sequence

1. Finish a green CI run for the current native policy-refresh and live-profile reconfiguration changes.
2. Run physical-device validation against the checklist in `BLUEPRINT.md`.
3. Implement local domain blocking with a documented DNS-filter syntax/parser, explicit source/license attribution, bounded update/cache behavior and allow-list precedence.
4. Add tracker evidence/usage/country attribution only after packet/DNS evidence is being collected with the agreed privacy boundaries.
5. Implement real socket-buffer/speed policy only after the user-facing semantics are defined and verified.
6. Integrate Sidebar Main/Heavy process ownership only after the standalone runtime passes.

