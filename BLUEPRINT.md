# Vianshield Architecture Blueprint

Status: development branch `refactor/engine-neutral-purge`

This document records the current standalone architecture and the boundary for a later Sidebar integration. It distinguishes implemented behavior from planned work.

## 1. Product boundary

Vianshield is a lightweight Android firewall with optional WireGuard transport. Local app policy and remote VPN routing are separate decisions:

1. The firewall decides whether an app/UID may pass.
2. If permitted, the routing layer decides whether traffic goes directly to the physical network or through WireGuard.

A WireGuard profile is optional for local firewall-only operation.

## 2. Runtime ownership

`VianshieldVpnService` is the sole Android `VpnService` and sole owner of the Android TUN descriptor.

```text
Main / Firewall / VPN / Settings UI
                |
       DiagnosticViewModel
                |
       VpnLifecycleController
                |
       VianshieldVpnService
          |             |
          |             +---- optional WgEgress (GotaTun / libwgbridge.so)
          |
          +---- ServiceSinkhole JNI facade
                         |
                  libnetguard.so
                         |
                  TUN packet loop
```

Important ownership rules:

- `ServiceSinkhole` is a plain Java JNI facade, not an Android Service.
- The TUN descriptor remains owned by `VianshieldVpnService`; the native loop borrows its FD and is stopped/joined before the descriptor is closed.
- WireGuard is an optional userspace egress behind the same TUN. It does not establish a second Android VPN.
- Lifecycle work is serialized on a dedicated single-thread executor.
- Startup failures and native packet-loop failures transition the UI to an error/stopped state; Connected is not inferred from VPN permission alone.
- Local firewall mode starts the native packet loop without WireGuard. WireGuard mode starts the same packet loop plus the GotaTun tunnel.

## 3. App policy and routing

The native packet engine asks the Vianshield callback for the UID's firewall decision.

Current policy:

| Condition | Decision |
|---|---|
| Vianshield's own UID | Allow self |
| UID is unavailable/invalid | Deny (unknown UID) |
| No packages resolve and UID is below 2000 | Allow as an unowned Android system UID |
| No packages resolve and UID is in app range | Deny |
| Any package sharing the UID is Block | Block |
| Every package sharing the UID explicitly selects Direct | Allow and route Direct |
| Otherwise | Allow through the firewall's default route |

Direct UIDs are supplied to the native route table. With WireGuard configured, default routing is through WireGuard and Direct UIDs bypass that tunnel. Without WireGuard, allowed traffic follows the local/direct path through the native engine.

A native policy-refresh signal causes existing TCP/UDP sessions to be rechecked after per-app rules change. This is important because changing a rule must not leave an older connection operating under a stale allow decision.

### App inventory

The Firewall screen loads installed applications and icons through Android PackageManager. The manifest includes `QUERY_ALL_PACKAGES` for the user-facing inventory. User apps and system apps are shown as mutually exclusive views.

### Rule persistence

BLOCK/VPN/DIRECT plus the per-app speed-exception and screen-aware values are stored in app-private preferences. BLOCK and routing modes are consumed by the current packet policy. Speed-exception and screen-aware values are saved but not yet enforced.

## 4. WireGuard profiles and DNS

WireGuard profiles are encrypted as an AES-GCM blob, with a non-exportable AES key held by Android Keystore. The encrypted preferences file is excluded from Android cloud backup and device transfer because the key cannot be restored independently.

Import, edit, selection and reset persist first, then update the ViewModel. When the VPN is running, profile changes request a reconfiguration of the existing Vianshield service/TUN.

DNS provider choices are persisted in `FirewallSettingsRepository`. The selected resolver list is passed to Android's VPN Builder. A provider change while the VPN is active restarts the same runtime so the TUN DNS list changes. The current resolver choices include profile default, Cloudflare, Quad9, Google and AdGuard DNS.

## 5. Logging and privacy

- Operational lifecycle/configuration failures are logged through OperationalLogCatcher.
- Per-packet destination addresses/ports and DNS names are not persisted by the Vianshield packet callbacks.
- DNS/resource records and per-app usage are not currently persisted into a Tracker evidence store.
- Log Catcher is the operational logger/storage component. Log Keeper is the UI that displays, exports and clears those logs.
- Crash reporting and log export remain separate from packet-policy enforcement.

## 6. Current feature status

| Feature | Current state |
|---|---|
| Native firewall packet loop | Integrated behind VianshieldVpnService; device validation pending |
| Firewall-only mode | Implemented in service path; device validation pending |
| WireGuard egress | GotaTun bridge integrated; handshake/runtime device validation pending |
| Per-app Block | Implemented in callback policy; native policy refresh added; device validation pending |
| Per-app VPN/Direct | Native per-UID route table integrated; device validation pending |
| DNS resolver selection | Persisted and applied through TUN DNS server list |
| Local Ad/Tracker domain filtering | Not active; domain-block callback currently returns false |
| Domain allow-list | Persisted only; not yet connected to local DNS blocking |
| Tracker intelligence / usage / country attribution | Not active; Tracker tab placeholder |
| Global speed preset | Persisted only; no bandwidth cap applied |
| Per-app speed exceptions | Persisted only; not enforced |
| Screen-aware rule | Persisted only; not enforced |

Do not describe a persisted preference as active traffic enforcement.

## 7. Sidebar integration boundary

Standalone Vianshield must be validated first. The planned Sidebar split is:

- Sidebar Main/lightweight process owns and maintains the firewall runtime.
- Sidebar Heavy owns the firewall UI/settings page.
- Heavy closing must not stop the VPN or destroy the runtime.
- Settings must communicate with the Main-owned runtime through an explicit process-safe IPC/state boundary; Heavy must not create a second TUN or second VPN service.

This Sidebar architecture is a target design, not implemented in the current standalone branch.

## 8. Validation checklist before release / Sidebar integration

Run on physical Android devices and supported Android versions:

- [ ] Local firewall starts without a WireGuard profile.
- [ ] Blocked app loses IPv4, IPv6, TCP, UDP and DNS access, including an already-open connection when the rule is changed.
- [ ] Allowed app retains internet access in firewall-only mode.
- [ ] Direct app bypasses WireGuard only when all packages sharing its UID are Direct.
- [ ] VPN-mode app is carried through WireGuard when a profile is active.
- [ ] Split-tunnel AllowedIPs behavior matches the imported profile.
- [ ] DNS resolver choice changes the TUN resolver list and traffic reaches the intended server.
- [ ] WireGuard endpoint hostname resolution and tunnel handshake work.
- [ ] Wi-Fi/mobile changes recover without leaking ordinary traffic.
- [ ] VPN revoke, app force-stop, process recreation and device reboot cleanly recover/stop.
- [ ] Repeated start/stop/reconfigure does not leak FDs, threads or native contexts.
- [ ] Android versions below and above API 29 attribute UIDs correctly.
- [ ] Battery/background behavior and foreground notification are correct.
- [ ] Build/CI and APK install/launch are verified separately from live traffic behavior.

## 9. Licensing / dependency audit

TrackerControl-derived code is GPL-3.0. The pinned upstream source is a reference for the packet engine, JNI and WireGuard adapter. Keep upstream notices in modified/copied files and continue auditing all nested Rust crates, native dependencies, Gradle libraries, fonts/icons/images, tracker datasets, hostlists and GeoIP data individually.
