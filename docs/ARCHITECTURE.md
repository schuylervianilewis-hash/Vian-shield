# Vianshield Architecture and Runtime Boundary

**Status:** Current source description plus explicitly labelled future contract  
**Source of truth:** The checked-in `cleanup/prep-work` branch  
**Last source review:** 8 November 2026  
**Important:** The future contract below is a preparation target, not implemented runtime code.

## 1. Current product shape

Vianshield is an Android application shell for a future firewall runtime. Its five persistent top-level destinations are:

| Tab | Current responsibility |
|---|---|
| Main | Primary protection control and dashboard shortcuts. The control reports runtime pending; no protection is active. |
| Firewall | Queries Android's package manager for the applications visible to this app and presents planned per-app controls. Policy enforcement and durable app-rule storage are absent. |
| VPN | WireGuard `.conf` import, parsing/validation, profile preview, profile details, and in-session selection. No tunnel is established. |
| Tracker | Intentionally blank destination. No tracker data, detection, or TrackerControl runtime is present. |
| Settings | Log Keeper/diagnostics, unavailable preference placeholders, and unavailable Backup/Restore. |

The Main dashboard has additional destinations for Speed Preset, DNS Changer, Ad Filter, domain/link whitelist, and Blocked Domains. They are UI shells. Their presence does not mean that traffic is shaped, DNS is changed, domains are filtered, or blocked-domain events are collected.

## 2. Current source layout

| Source | Responsibility in this build | Boundary / limitation |
|---|---|---|
| `MainActivity.kt` | Compose app entry point, persistent bottom navigation, and destination selection. | Navigation is held by the shared ViewModel; no separate navigation module is present. |
| `ui/MainScreen.kt` | Main dashboard and truthful runtime-pending presentation. | No service is started by the primary control. |
| `ui/FirewallScreen.kt` | Queries PackageManager and displays the visible app list and planned controls. | The list is subject to Android package-visibility rules. Policy controls are unavailable; no app rules are enforced or persisted. |
| `ui/ProfilesScreen.kt` | File selection, profile import preview, profile details, raw configuration inspection/editing, and selection. | Profile list and active configuration are held in memory. Selection does not connect a tunnel. |
| `ui/TrackerScreen.kt` | Blank Tracker tab shell. | No tracker implementation or sample data. |
| `ui/SettingsScreen.kt` | Log Keeper entry, logging controls, unavailable preferences, and Backup/Restore status. | General preferences and custom backup/restore are not implemented. |
| `ui/DashboardScreens.kt` | Speed, DNS, Ad Filter, whitelist, and Blocked Domains destinations. | These destinations explain unavailable functionality; they do not implement network policy. |
| `ui/DiagnosticViewModel.kt` | Shared navigation, diagnostic filters, transient profile state, and placeholder control state. | It currently combines responsibilities that should later be separated into focused state holders/repositories. |
| `config/WireGuardConfigParser.kt` | Parses and validates WireGuard configuration text into a profile model. | Parsing is not tunnel setup or connectivity validation. |
| `vpn/VpnState.kt` | Defines neutral VPN lifecycle state values. | The current ViewModel starts at `RuntimePending`; no runtime currently drives these states. |
| `logging/OperationalLogCatcher.kt` | Records app events, redacts selected secret patterns, bounds memory/disk logs, and exports formatted event text. | It records only events explicitly sent by app code; it is not packet capture or a network monitor. |
| `logging/CrashReporter.kt` | Persists JVM uncaught-exception reports and delegates to Android's prior default handler. | JVM uncaught-exception handling does not capture native fatal signals. |
| `logging/LogExporter.kt` | Exports operational logs and crash reports. | Export does not imply that firewall/runtime telemetry exists. |
| `VianshieldApplication.kt` | Initializes the operational logger and crash reporter. | No VPN service or engine is initialized here. |

### Shared state and persistence

`DiagnosticViewModel` currently owns `MutableStateFlow` values for navigation, diagnostic filters, the active raw WireGuard configuration, imported-profile preview, saved-profile list, speed preset, DNS provider, Ad Filter enabled state, and the domain allow-list. These are process/session state, not a durable settings model.

Specific consequences:

- Imported WireGuard profiles are added to an in-memory list. They are not saved to a Room database, DataStore, or another profile repository.
- The current profile selection is also in memory. A process restart does not restore the user's imported profile from a durable profile store.
- Some placeholder values and mutation methods remain in the ViewModel, but the corresponding dashboard controls are unavailable and no network behavior is attached to them.
- The ViewModel's initial allow-list contains placeholder entries, while the current whitelist screen reports no saved entries. Step 4 must reconcile this discrepancy before a real whitelist store is designed.
- The project declares Room dependencies, but the current source tree contains no Room database, entity, or DAO. DataStore is present in the version catalog but its app dependency is commented out.
- The Settings Backup/Restore UI is explicitly unavailable. Android backup configuration files are still present as templates; the app's actual backup inclusion/exclusion policy has not been finalized.

Do not describe profiles, settings, policies, or whitelist entries as persistently saved until a repository and restart tests prove that behavior.

## 3. Runtime status: not integrated

The current source tree contains no Android `VpnService` implementation, no TUN interface establishment/owner, no active packet-engine adapter, and no runtime service declaration in the app manifest.

The `VpnState` type includes states such as `Stopped`, `Starting`, `Connected`, `Stopping`, and `Error`, but type definitions alone do not provide a running service. The current app starts in `RuntimePending`, and the Main control records that integration is pending without starting a VPN.

Therefore, this build does not provide:

- Per-app blocking or VPN/Direct routing enforcement.
- A VPN tunnel or traffic forwarding.
- DNS provider switching or DNS leak protection.
- Ad/domain filtering or domain/link whitelist enforcement.
- Blocked-domain event collection.
- Socket-buffer tuning or speed limiting.
- Kill-switch enforcement, boot startup, or LAN exclusion.
- Tracker detection or analysis.

The app manifest currently requests `INTERNET`, `ACCESS_NETWORK_STATE`, and `POST_NOTIFICATIONS`. These permissions do not create a VPN runtime.

## 4. Logging and crash-report behavior

### Operational events

The current `OperationalLogCatcher` implementation:

- Starts with its logging switch enabled in process memory; that switch is not a persisted user preference.
- Retains at most 500 event objects in its in-memory flow.
- Appends event lines under the app's internal `filesDir`.
- Uses an active and one rotated log segment, with a configured total disk ceiling of 3 MiB (1.5 MiB per segment).
- Redacts selected named secret fields and WireGuard-like base64 key patterns before recording.
- Records only messages explicitly sent by application code. It does not inspect packets or collect browser history. Event messages can still contain application metadata.

The disk files are written and rotated, but the current UI event flow is not rehydrated from those files on process startup. Do not promise that the on-screen event list survives process death.

### Crash reports

`CrashReporter` writes JVM uncaught-exception reports under `filesDir/crash_reports`, retains at most three report files, and delegates the exception to the previous default handler. Reports can include the exception and stack trace, thread details, app version, Android version, device manufacturer/model, and recent operational event lines. Selected secret patterns are redacted.

The source declares a byte-budget constant for crash reports, but the current pruning logic enforces the file-count limit rather than that byte budget. Native fatal signals are not captured by the JVM uncaught-exception handler.

Privacy claims and disclosure requirements are tracked in Step 5; this document is not a privacy policy.

## 5. Proposed engine-neutral runtime integration contract

**This section describes the intended boundary for a later package. It is not implemented in the current source.** The exact Kotlin interfaces and data schemas must be reviewed when Step 8 is undertaken.

### Application/domain layer

Own product-level concepts that do not depend on a particular packet engine:

- Firewall policy: per-app block decision and routing choice (VPN or Direct), kept as distinct policy dimensions.
- DNS/filter policy: upstream selection, filter enablement, and a separate domain/link allow-list.
- WireGuard profile model and profile selection, independent of the normal firewall runtime.
- Neutral runtime state, typed errors, operational events, and statistics.
- User preferences and backup-compatible data models.

UI and ViewModels should consume these neutral models and invoke application-level use cases. They should not generate engine-specific JSON or call engine SDK classes.

### Android platform layer

A future Android VPN service should own Android-specific responsibilities:

- VPN permission preparation and service lifecycle.
- Foreground-service and notification requirements.
- TUN interface creation and Android descriptor hand-off.
- Network changes, process/service teardown, and OS-facing error reporting.

TUN descriptor ownership and close/transfer rules must be explicit. Exactly one layer must own each descriptor at a time; startup rollback and shutdown ordering must prevent leaks, double-close, and descriptor reuse races.

### Runtime adapter layer

A future engine adapter should sit behind a neutral runtime boundary and translate product-level configuration/policy into the selected engine's configuration. It should expose typed lifecycle state, errors, events, and supported statistics without leaking engine-specific types into Compose UI or persisted user settings.

The adapter must define:

- Validated configuration hand-off and startup result.
- Start/stop serialization, cancellation, startup timeout, and rollback behavior.
- Single-owner TUN descriptor transfer and deterministic cleanup.
- Runtime state/event delivery and error classification.
- Capability reporting where an engine cannot support a product requirement directly.

### Persistence layer

A future repository layer should own durable user data independently of engine configuration:

- General preferences.
- Per-app firewall policies.
- DNS choices and domain/link whitelist entries.
- WireGuard profile records and active-profile selection.
- Versioned backup/restore representation.

The chosen storage technology and schema belong to Step 4. Avoid unnecessary migration and do not discard existing user configuration if durable user data is introduced later.

### Policy invariants to preserve

1. App blocking is not the same as choosing VPN or Direct routing.
2. The Ad Filter allow-list is separate from per-app firewall rules and accepts domain/link entries only.
3. No per-app Ad Filter exemption control is part of the approved product behavior.
4. WireGuard profile management remains independent of whether the firewall runtime is running.
5. A disconnected, failed, or pending runtime must never be displayed as active protection.
6. Runtime logs and diagnostics must reflect observed events, not fabricated engine activity.

## 6. Known preparation gaps

- Settings, policy, whitelist, and profile data are not separated into durable repositories.
- The ViewModel is a shared state holder with mixed responsibilities.
- Profile import UI currently uses “saved” wording even though the list is in memory only; this should be corrected alongside Step 4 storage work.
- The whitelist ViewModel placeholder values and the empty whitelist UI state are inconsistent.
- The crash report byte-budget constant is not enforced by current pruning logic.
- The Android backup rules remain templates; Backup/Restore is unavailable.
- Physical-device UI review remains an acceptance task. CI success alone is not device validation.
- Licence compatibility and privacy disclosures have not yet been audited; see Step 5.
- `metadata.json` still contains a generated `majorCapabilities` entry for a server-side Gemini API. No corresponding Gemini API use was found in the current Android source. The descriptive text was corrected, but the capability field is left for a separate metadata-format review rather than changing an unverified generated schema.

## 7. Related records

- [Project overview](../README.md)
- [Current architecture blueprint](../BLUEPRINT.md)
- [Approved decisions](DECISIONS.md)
- [Preparation checklist](CLEANUP_CHECKLIST.md)
- [Historical pre-engine-neutral blueprint](archive/BLUEPRINT_PRE_ENGINE_NEUTRAL.md)
- [Historical implementation receipts](../receipts/)
