# Capability Map: Android live room display

Status: approved (2026-11-16); local follow-on implementation authorized (2026-11-16). This extends
the approved `SPEC.md` display scope. `MAP-operations.md` and its unfinished
tasks remain in place; provisioning and updates are not re-specified here.

Goal: keep Android rendering native and interactive, reproduce the agreed
`status-panel` and `day-band` designs, and separate local time-driven changes
from changes arriving from the calendar service.

## Assumptions for review

- The reviewed mockups guide the layout correction, with operator-provided
  branding and theme, day/week navigation and the existing 30-second idle return.
  Landscape 1280 × 800 and portrait 800 × 1280 both need synthetic visual checks.
- No bitmap room-frame endpoint or WebView replaces the native Compose UI.
- Existing token authentication, server-side privacy filtering and asset
  ownership checks remain authoritative. An empty visible event list never
  proves availability.
- Conditional polling around 15 seconds is a starting point for external
  changes, not a measured end-to-end guarantee. SSE is a later option only if
  measured requirements justify it and source updates can arrive promptly.
- This map does not authorize server rollout, publication, production device
  changes, release-key creation, emulator wipe or pushing existing work.

| Module id               | Responsibility                                                                                                                                                                                                  | Depends on                                 |
| ----------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------ |
| `room-boundary-labels`  | Show an unambiguous day/date when a free or busy boundary falls on another local day; consistent semantics for the server renderer and Android, including timezone/year changes                                 | —                                          |
| `panel-live-state`      | A privacy-preserving validity/transition contract; locally timed room status, QR eligibility and status-light decisions from one state; trustworthy time anchoring, source freshness and explicit unknown state | —                                          |
| `panel-data-refresh`    | Conditional polling, bounded retries/backoff, reconnect recovery, matching source-cache policy, NAT-safe rate budgets and presence cadence; avoid repeated unchanged asset work                                 | `panel-live-state`                         |
| `panel-layout-fidelity` | Native status panel, day band, opening-hours axis, labelled bookings, brand placement and responsive navigation; verify free/busy/later-day/offline/unknown in both orientations                                | `room-boundary-labels`, `panel-live-state` |

Build order: `room-boundary-labels` → `panel-live-state` →
`panel-data-refresh` → `panel-layout-fidelity`.

Before extending rollout or provisioning, correct the approval/key-write race
in the existing `panel-enrollment-trust` module. The approved spec already
requires that an unauthenticated hello cannot change an approved key. A late
hello UPDATE currently checks identity only, after an earlier pending read;
approval must stay bound to the verified key under concurrent operations.

## Contract boundaries to specify after map approval

- `panel-live-state` owns the meaning of available, occupied and unknown, the
  source-data coverage/age, and the lifetime of privacy-safe status evidence.
  Hidden event details or times must not be leaked merely to obtain local
  transitions. Old servers and 304 responses need an explicit safe fallback.
- `panel-data-refresh` consumes that evidence and supplies new snapshots; it
  must not turn a successful HTTP 304 into proof that the calendar itself was
  just fetched. Source age, server contact and panel time are distinct facts.
- `panel-layout-fidelity` consumes the same live status as QR and LEDs. It owns
  placement and interaction, not a separate availability calculation.
- `room-boundary-labels` owns local-date semantics, not state selection. Both
  renderers use matching fixtures without organisation-specific test data.

Each module follows a reviewed spec, then plan/tasks, then small TDD increments.
Regression coverage includes hidden bookings, overlapping bookings, midnight,
DST, wrong wall clock, 304, stale/offline data, rapid reassignment and recovery.
Visual acceptance compares the actual native UI with synthetic references;
the HTML mockup alone is not implementation acceptance.

## Follow-on operations

Keep the approved `MAP-operations.md` order: signing custody and enrollment
trust corrections → `panel-provisioning` and `panel-hardening` →
`panel-updates` → `panel-remote-config`. The current audit recommends ADB-assisted
setup as the first supported path; Android QR provisioning requires separate
app entry points and validation on the target OEM firmware.
