# Vianshield — Approved Decisions and Preparation Record

**Status:** Decision record for the current preparation stage  
**Last reconciled:** 8 November 2026  
**Repository branch:** `cleanup/prep-work`

This record separates approved product decisions from observed implementation status and future proposals. A decision being documented does not mean its feature is implemented.

## D-001 — Product identity and preparation scope

**Decision:** Vianshield is an Android firewall product. The current milestone prepares the application shell for a future runtime integration; it does not import or implement a packet engine.

**Rationale:** The app structure, product requirements, and user configuration should remain independent of the eventual engine choice.

**Current status:** The current source is a UI/configuration-parsing/logging shell. No active firewall or VPN runtime is present.

## D-002 — Preserve five top-level tabs

**Decision:** Keep the five persistent tabs and their responsibilities:

1. Main
2. Firewall
3. VPN
4. Tracker
5. Settings

**Rationale:** The five-tab product structure is part of the agreed application design.

**Current status:** All five navigation destinations are present. Tracker is intentionally blank until its separate integration work is approved.

## D-003 — Truthful protection status

**Decision:** Keep the Main protection control visible, but show runtime integration as pending until a real runtime is integrated and validated. Never claim protection is active while no runtime is running.

**Current status:** The ViewModel initializes to `RuntimePending`; the control records the pending state and does not start a VPN service.

## D-004 — Separate firewall policy dimensions

**Decision:** Preserve the distinction between:
- Blocking an app.
- Routing an app through VPN.
- Routing an app directly.

These are separate policy choices and must not be collapsed into one setting.

**Current status:** The Firewall UI presents planned controls, but rule persistence and enforcement are not implemented.

## D-005 — Separate domain/link whitelist

**Decision:** Ad Filter whitelist is managed separately from the installed-app firewall list and accepts domain/link entries only. Do not add a per-app Ad Filter exemption.

**Current status:** Separate Ad Filter and whitelist destinations exist. Entry management, persistence, filtering, and enforcement are unavailable.

## D-006 — WireGuard profile management remains independent

**Decision:** Keep WireGuard `.conf` import, parsing/validation, multiple-profile management, and profile selection independent from the normal firewall runtime. A WireGuard profile is not required for the engine-neutral firewall shell to exist.

**Current status:** Parsing and in-session profile management are present. Profiles are not durably persisted and no tunnel is established.

## D-007 — TrackerControl work is deferred

**Decision:** Preserve the Tracker destination as an intentionally blank tab shell during this preparation stage. Do not fabricate tracker data or import TrackerControl runtime/detection in this stage.

**Current status:** The Tracker screen is blank.

## D-008 — Settings and backup must be truthful

**Decision:** Keep planned settings visible where required, but unavailable controls must not change state or imply functionality. Backup/Restore must not claim to export or import data until implemented.

**Current status:** Planned preference rows and Backup/Restore are visibly unavailable. No durable preference repository or custom backup/restore flow is present.

## D-009 — Log Keeper consolidates operational logs and diagnostics

**Decision:** Use Log Keeper as the consolidated destination for operational logs, crash reports, and runtime/protection diagnostics rather than maintaining a separate Diagnostics destination.

**Current status:** Log Keeper contains these UI sections. Because runtime is absent, it cannot report real VPN-engine or TUN activity.

## D-010 — Project licence direction

**Decision:** GPL-3.0 is the intended project licence direction, subject to a direct/transitive dependency, asset, source-provenance, and combination-licence audit.

**Current status:** This is not a declaration of licence compliance. Step 5 remains open until the audit and notices are completed.

## D-011 — Visual refinement without product restructuring

**Decision:** Modest layout, spacing, hierarchy, and usability refinements are permitted. Preserve the five tabs and their responsibilities. Preserve the existing visual identity and colour theme.

**Current status:** Compact layout refinements have been committed on the preparation branch. Physical-device review remains outstanding.

## D-012 — Branch and review discipline

**Decision:** Continue preparation on `cleanup/prep-work`. Keep `main` untouched until the full change set has been reviewed and the user explicitly approves a merge. Do not wholesale merge the separate refactor branch.

**Current status:** The documentation package is being committed only to `cleanup/prep-work`. No merge is part of Step 6.

## D-013 — Historical research is not current implementation guidance

**Decision:** Preserve historical implementation receipts and the earlier engine-specific blueprint, but label them as historical. Current architecture and approved decisions must be documented separately.

**Rationale:** Earlier research and implementation records remain useful for provenance and future engine selection, but they must not imply that removed runtime components remain in the current source tree.

**Current status:** `receipts/` remains unchanged. The former blueprint is preserved in `docs/archive/BLUEPRINT_PRE_ENGINE_NEUTRAL.md`; current guidance is in `BLUEPRINT.md` and `docs/ARCHITECTURE.md`.

## Deferred proposals

The future runtime boundary in `docs/ARCHITECTURE.md` is a preparation proposal, not an approved engine-specific design or implemented interface. Exact interfaces, storage schema, and runtime ownership are deferred to Steps 4 and 8 and must be reviewed against the selected engine when that work begins.
