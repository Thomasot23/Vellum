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

## Risks and Mitigations

| Risk                                     | Impact | Mitigation                                                                                       |
| ---------------------------------------- | ------ | ------------------------------------------------------------------------------------------------ |
| X25519/HKDF/GCM mismatch with the server | High   | Fixture from `encryptForDevice` decrypted in a JVM unit test (T5) before enroll UI               |
| APPC-10S Android version below 11        | Med    | Ask/verify early; `minSdk` can drop to 26 with Tink                                              |
| Kiosk needs device-owner provisioning    | Med    | App works without it; document one-time `adb dpm set-device-owner`                               |
| No physical panel in CI                  | Med    | Compose tests at 1280×800 on the JVM (Robolectric) and an emulator image; manual checklist (T13) |
| Fork drifts from upstream                | Low    | Server changes kept to two endpoints + one registry entry                                        |
| Sandbox cannot download Gradle/Maven     | Med    | Builds run locally; first build needs network for Gradle wrapper and dependencies                |

## Open Questions

- Android version on the panels (affects `minSdk`).
- Device-owner kiosk or ProDVX kiosk setting.
