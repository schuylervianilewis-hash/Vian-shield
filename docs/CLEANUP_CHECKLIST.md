# Vianshield — Cleanup and Preparation Checklist

**Working branch:** `cleanup/prep-work`  
**Documentation checkpoint:** 8 November 2026  
**Scope:** Prepare the Android application for a future runtime integration. This checklist does not authorize runtime implementation or a merge to `main`.

## Overall status

- Steps 0–2: recorded complete in the preparation tracker.
- Step 3: UI implementation has progressed; its acceptance gate remains open for physical-device review and any issues found.
- Step 4: groundwork only; settings/data separation and durable storage preparation remain open.
- Step 5: open.
- Step 6: documentation cleanup completed by the documentation-only package recorded below.
- Steps 7–8: open.

A checklist item is complete only when the stated work is present and its acceptance evidence is recorded. A successful build does not prove runtime or physical-device behavior.

## Step 0 — Verify source of truth

**Recorded status:** Complete.

- [x] Confirm the GitHub repository and inspect the source before cleanup.
- [x] Record the original main baseline and working branch in the preparation tracker.
- [x] Keep the cleanup work separate from the original main baseline.

## Step 1 — Protect the baseline

**Recorded status:** Complete.

- [x] Preserve the original main baseline using the dedicated backup branch recorded in Notion.
- [x] Create and use `cleanup/prep-work` for preparation.
- [x] Leave `main` and the separate refactor branch untouched.

## Step 2 — Remove the former engine implementation

**Recorded status:** Complete in the preparation tracker.

- [x] Remove the former engine runtime, native integration, active dependency, and engine-specific build pipeline from the cleanup branch.
- [x] Retain generic app functionality that remains useful.
- [x] Keep historical records as history rather than current implementation guidance.

## Step 3 — Clean and refine the five-tab UI

**Recorded status:** Implementation progressed; acceptance gate remains open.

- [x] Preserve Main, Firewall, VPN, Tracker, and Settings destinations.
- [x] Keep the Main protection status truthful while runtime is pending.
- [x] Keep unavailable controls from implying active enforcement.
- [x] Consolidate operational logs, crash reports, and diagnostics in Log Keeper.
- [x] Apply the committed compact layout refinements without changing the visual theme.
- [ ] Complete physical-device review of all five tabs and key navigation flows.
- [ ] Record and resolve any device-only crashes, layout problems, or misleading status found during review.

**Gate:** All five tabs are navigable and accurately represent current capabilities; physical-device review is recorded.

## Step 4 — Settings and data preparation

**Status:** Open. Earlier work made preference and Backup/Restore UI truthful, but did not implement a durable data layer.

- [ ] Inventory existing preference, app-policy, DNS, domain/link whitelist, and WireGuard profile state.
- [ ] Separate settings and policy models from Compose UI and engine-specific configuration.
- [ ] Remove obsolete engine-only preferences after confirming they are not product requirements.
- [ ] Preserve separate app blocking, VPN/Direct routing, and domain/link-only Ad Filter whitelist concepts.
- [ ] Prepare backup-compatible data structures without unnecessary migrations.
- [ ] Define the future Exclude LAN traffic setting contract.
- [ ] Reconcile placeholder ViewModel values with the visible UI before designing persistence.
- [ ] Verify any chosen storage with process-restart and migration tests.

**Gate:** Existing user configuration is not discarded or conflated with future engine configuration. No persistence is claimed until restart tests pass.

## Step 5 — GPL-3.0, third-party notices, and privacy preparation

**Status:** Open.

- [ ] Audit direct and transitive dependencies, native libraries, assets, tracker data sources, and copied/adapted code.
- [ ] Check licence compatibility before declaring the project compliant.
- [ ] Add GPL-3.0 project licence text after the audit confirms the project direction is suitable.
- [ ] Prepare `THIRD_PARTY_NOTICES.md` with upstream project, source, licence, and notice information.
- [ ] Create a dependency licence inventory and preserve required copyright/attribution notices.
- [ ] Record source provenance and modification obligations for any future TrackerControl-derived work.
- [ ] Prepare privacy documentation covering installed-app visibility, VPN/network handling, DNS, logs, retention, crash reports, and Android permissions.
- [ ] Record unresolved compatibility or privacy questions rather than guessing.

**Gate:** Licence and privacy preparation is documented with unresolved questions explicitly identified. Do not claim compliance without evidence.

## Step 6 — Documentation cleanup

**Status:** Completed in the documentation-only package dated 8 November 2026.

- [x] Reconcile the project blueprint with the current five-tab requirements and current source.
- [x] Document the current app structure and distinguish present components from absent runtime components.
- [x] Document the proposed future engine-neutral runtime boundary and label it as not implemented.
- [x] Record known limitations, transient state, logging behavior, and backup/profile persistence gaps.
- [x] Add this cleanup checklist and the approved decision record.
- [x] Preserve the previous engine-specific blueprint as a labelled historical snapshot.
- [x] Keep both chronological receipt files unchanged.
- [x] Replace the generated AI Studio starter README with current project/build guidance.

**Gate:** Current documentation describes the preparation-stage architecture and does not present future work as implemented.

## Step 7 — Build, test, and review

**Status:** Open for the complete preparation milestone.

- [ ] Run Gradle unit tests on the final preparation change set.
- [ ] Run the Android debug APK build and inspect CI results.
- [ ] Confirm no removed engine runtime/native binaries are present in the APK.
- [ ] Verify navigation and intended interactions across all five tabs.
- [ ] Verify WireGuard import, profile selection, and durable persistence when Step 4 storage exists; do not claim a connected tunnel.
- [ ] Verify settings persistence and backup/restore behavior when implemented.
- [ ] Install and launch on a physical Android device; check for crashes and force-closes.
- [ ] Review the full diff, dependency/licence inventory, and documentation.
- [ ] Obtain explicit approval before any merge to `main`.

**Gate:** Compilation alone is insufficient. Require UI checks, physical-device launch validation, and full change-set review.

## Step 8 — Prepare engine-independent application boundaries

**Status:** Open.

- [ ] Separate UI components from runtime-specific classes.
- [ ] Define neutral contracts for runtime start/stop, status, errors, events, policy, DNS/filter policy, and statistics as required by the product.
- [ ] Keep user preferences and firewall policy separate from engine configuration.
- [ ] Keep WireGuard profile parsing/storage/selection separate from the normal firewall runtime.
- [ ] Define Android VPN service and TUN descriptor ownership.
- [ ] Document how future TrackerControl work would connect through the neutral boundary without importing it during preparation.

**Gate:** UI and app-state code do not directly depend on a specific engine implementation.

## Explicit exclusions for this preparation stage

- No firewall enforcement or per-app traffic blocking.
- No VPN tunnel or packet-engine implementation.
- No DNS changing, DNS filtering, or ad blocking.
- No socket-buffer speed limiting.
- No kill-switch or Exclude LAN enforcement.
- No tracker detection or TrackerControl runtime.
- No Sidebar integration.
- No wholesale merge/cherry-pick of the separate refactor branch.

## Step 6 implementation record — 8 November 2026

**Starting source checkpoint:** `cleanup/prep-work` at `c4bd9bf8ff09404c7ffec5acc3f34fcdf7aa53ee`.

Documentation changes in this package:

- Replaced the generated AI Studio starter README with a current project overview, build instructions, and explicit capability limitations.
- Reconciled `BLUEPRINT.md` with the current engine-neutral preparation-stage architecture.
- Added `docs/ARCHITECTURE.md` with the current source responsibility map, state/persistence facts, runtime limitations, logging/crash-report behavior, and a clearly labelled future runtime contract.
- Added `docs/DECISIONS.md` to distinguish approved product decisions from current implementation status and deferred proposals.
- Added this `docs/CLEANUP_CHECKLIST.md` to make work-package status and acceptance gates explicit.
- Preserved the former engine-specific blueprint as `docs/archive/BLUEPRINT_PRE_ENGINE_NEUTRAL.md`.
- Left `receipts/RECEIPTS_001.md` and `receipts/RECEIPTS_002.md` unchanged.
- No Kotlin, Gradle, manifest, runtime, or product behavior was changed.

**Validation required:** Read back the updated files, verify the links and current branch head, and run the repository's unit-test/APK CI workflow. This documentation package does not replace Step 3 physical-device review or Step 7's broader acceptance gate.
