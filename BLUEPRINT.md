# Vianshield — Current Architecture Blueprint

**Document status:** Current preparation-stage overview  
**Repository:** `schuylervianilewis-hash/Vian-shield`  
**Working branch:** `cleanup/prep-work`  
**Last reconciled with source:** 8 November 2026  
**Scope:** Engine-neutral application preparation; no firewall/VPN runtime implementation.

## Product purpose

Vianshield is an Android firewall product being prepared around a five-tab application shell. The current source is a UI, configuration-parsing, logging, and diagnostics foundation. It must not be described as an active firewall or VPN.

## Five-tab structure

1. **Main** — primary protection control with an explicit runtime-pending state; dashboard destinations for planned controls.
2. **Firewall** — Android-visible installed-app inventory and the planned per-app policy presentation. Blocking and VPN/Direct routing are not enforced.
3. **VPN** — WireGuard configuration import, validation, profile presentation and in-session selection. No tunnel is established.
4. **Tracker** — intentionally blank tab; no tracker data or detection is present.
5. **Settings** — Log Keeper, diagnostics, unavailable preference placeholders, and unavailable Backup/Restore.

The Main dashboard also links to Speed Preset, DNS Changer, Ad Filter, domain/link whitelist, and Blocked Domains UI destinations. These are shells/placeholders, not active network features.

## Current architecture

- `MainActivity` owns top-level Compose navigation.
- Compose screens are under `app/src/main/java/com/example/ui/`.
- `DiagnosticViewModel` currently centralizes navigation, diagnostic filters, WireGuard profile state, and several placeholder control states. This is not yet a separated settings or policy data layer.
- `WireGuardConfigParser` parses and validates WireGuard configuration text without starting a tunnel.
- `VpnState` provides state types, but the current ViewModel starts at `RuntimePending`; no VPN service or engine updates it.
- `VianshieldApplication` initializes operational logging and crash reporting.
- `OperationalLogCatcher`, `CrashReporter`, and `LogExporter` provide application-event logging, crash-report retention, and export.
- No runtime adapter, Android `VpnService`, TUN interface owner, firewall policy repository, settings repository, or durable WireGuard profile repository is present in the current tree.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the detailed responsibility map, persistence facts, limitations, and proposed future runtime contract.

## Approved boundaries

- Keep the five top-level tabs and their responsibilities.
- Keep app blocking separate from VPN/Direct routing.
- Keep the Ad Filter whitelist separate from per-app firewall policy; it accepts domain/link entries only. Do not add per-app Ad Filter exemptions.
- Keep WireGuard profile management independent from the normal firewall runtime.
- Keep engine-specific configuration out of UI and product-level settings models when a runtime is later introduced.
- Keep all runtime and protection status truthful. Pending means no protection is active.
- Do not import TrackerControl, a packet engine, or Sidebar integration as part of the current preparation stage.

## Preparation sequence

The current tracked work packages and acceptance gates are in [docs/CLEANUP_CHECKLIST.md](docs/CLEANUP_CHECKLIST.md). Approved decisions and rationale are in [docs/DECISIONS.md](docs/DECISIONS.md).

## Historical record

Earlier engine-specific implementation research and chronological receipts are retained in [docs/archive/BLUEPRINT_PRE_ENGINE_NEUTRAL.md](docs/archive/BLUEPRINT_PRE_ENGINE_NEUTRAL.md) and [receipts/](receipts/). Those files describe what was investigated or implemented at earlier points; they are not a declaration that those components remain in the current app.
