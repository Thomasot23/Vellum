# Implementation Plan: Vellum Room Display for Android

Spec: [`../SPEC.md`](../SPEC.md) (approved). Task list: [`todo.md`](todo.md).

## Overview

Two server endpoints in the fork plus a Kotlin/Compose app that enrolls like an
e-paper display, polls a JSON room model and draws the status-panel and day-band
layouts natively on a 1280 × 800 touch panel.

## Architecture Decisions

- **Contract first.** The `room-state` JSON is defined once (zod schema on the
  server, `@Serializable` DTOs in the app) and both sides test against the same
  recorded fixture. Everything after task 2 depends on it.
- **Reuse, don't fork, server logic.** `room-state` calls `loadRoomModel` and the
  device's resolved theme exactly like `/render`; it only skips drawing.
- **Crypto is the riskiest part, so it comes first.** X25519 + HKDF-SHA256
  (`info = "vellum-token-v1"`) + AES-256-GCM (tag appended) must match
  `src/lib/crypto.ts`; a fixture produced by the server proves interop before any
  UI exists.
- **Pure domain layer.** `momentOf`, list selection and `readableOn` are plain
  Kotlin, ported from `room-status.ts`, tested on the JVM.
- **Assumptions for the open questions** (change anytime): Android 11+
  (`minSdk 30`); kiosk via lock-task when the app is device owner, otherwise
  screen-on + boot start only; night = dimmed, not off; open face (Inter).

## Dependency Graph

```
T1 room-state endpoint ─┬─ T3 asset endpoint
T2 registry entry ──────┤
                        └─ T4 Android skeleton ─ T5 crypto ─ T6 enroll flow
                                                   │
                           T7 API client + cache ──┘
                                 │
                           T8 domain + theme ─ T9 status-panel ─ T10 day-band
                                                                    │
                                              T11 day/week + idle return
                                                                    │
                                              T12 kiosk, boot, night dimming
                                                                    │
                                              T13 device checklist + docs
```

## Phases

1. **Server contract** (T1–T3): endpoints and registry, vitest green.
2. **Foundation** (T4–T7): app builds, enrolls against a local or test server,
   receives and caches a room state. Checkpoint with the user before UI.
3. **Layouts** (T8–T11): both layouts and navigation, Compose UI tests.
4. **Operation** (T12–T13): kiosk, boot, offline, night; on-device checklist.
5. **Status light** (T14–T17, planned): mirror the room state on panel LEDs.
   Starts only after the vendor interface is confirmed.
6. **Operations, stage 1** (T18–T28): modules `panel-telemetry`,
   `panel-enrollment-trust` and `release-signing` from `../MAP-operations.md`;
   specs `../SPEC-panel-telemetry.md`, `../SPEC-panel-enrollment-trust.md`,
   `../SPEC-release-signing.md`. Stage 2 (hardening, provisioning), stage 3
   (updates) and stage 4 (remote configuration) get their own specs later.

## Phase 6 plan: operations, stage 1

Order: telemetry first (later modules read its data), enrollment trust second
(it changes approval, which touches e-paper operators too), signing tooling
third; the real key and the emulator wipe wait for explicit decisions.

```
T18 panel-info intake ──► T19 posture + console ──┐
T20 app collection (needs T18 contract) ──────────┤──► Checkpoint 6a
T21 app log upload (independent) ─────────────────┘
T22 shared fingerprint + panel display ──► T23 key-bound approval ──┐
                                           T24 key history ─────────┤──► Checkpoint 6b
                                           T25 one-time key route ──┘
T26 signing script + version policy ──► T27 real key (decision) ──► T28 emulator re-provision (approval) ──► Checkpoint 6c
```

Decisions taken with the approved specs: patch-age warning at 180 days;
Android 13 as the no-warning minimum; approval without a key stays possible
for legacy firmware, clearly labelled; a confirm click with the fingerprint
shown, no typing.

## Risks and Mitigations

| Risk                                             | Impact | Mitigation                                                                                       |
| ------------------------------------------------ | ------ | ------------------------------------------------------------------------------------------------ |
| X25519/HKDF/GCM mismatch with the server         | High   | Fixture from `encryptForDevice` decrypted in a JVM unit test (T5) before enroll UI               |
| APPC-10S Android version below 11                | Med    | Ask/verify early; `minSdk` can drop to 26 with Tink                                              |
| Kiosk needs device-owner provisioning            | Med    | App works without it; document one-time `adb dpm set-device-owner`                               |
| No physical panel in CI                          | Med    | Compose tests at 1280×800 on the JVM (Robolectric) and an emulator image; manual checklist (T13) |
| Fork drifts from upstream                        | Low    | Server changes kept to two endpoints + one registry entry                                        |
| Sandbox cannot download Gradle/Maven             | Med    | Builds run locally; first build needs network for Gradle wrapper and dependencies                |
| Per-device entries grow the settings store       | Low    | Keys are namespaced per device and removed with the device; reads are by primary key             |
| Key-bound approval changes e-paper operator flow | Med    | Only a confirmation step is added; legacy devices without a key keep working                     |
| Signing key lost or exposed                      | High   | Real key only after custody decision (T27); offline backups; never in CI or the public repo      |
| Emulator still debug-signed as device owner      | Low    | Wipe and re-provision once, after approval (T28)                                                 |

## Open Questions

- Android version on the panels (affects `minSdk`).
- Device-owner kiosk or ProDVX kiosk setting.
- Status light (phase 5, planned): LED interface of the delivered panel
  generation, colour for "soon", behaviour while stale. See `../SPEC.md`.
- Phase 6: where releases are signed, where the two key backups live, and
  whether a second person holds a sealed copy (`../SPEC-release-signing.md`).

## Phase 7: live display corrections (authorized locally, 2026-11-16)

Specs: MAP-live-display and its four module specs. The follow-on user instruction
authorizes local specify/plan/test/implement work without repeating each phase
prompt; production boundaries are unchanged.

Order: close the existing enrollment-trust regression first; boundary labels;
privacy-safe live state and clocks; refresh/cache/rate budgets; native layouts;
integrated synthetic validation and updated provisioning proposal. Preserve open
T27/T28 and physical/live acceptance rather than marking them done.

Each slice starts with a failing regression, implements only its contract, then
runs focused checks. Final gates cover server and Android, leak/neutrality/diff
review and an additional external review of credential-free code. No push or
rollout is implied.
