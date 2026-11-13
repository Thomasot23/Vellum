# Tasks: Vellum Room Display for Android

## Phase 1: Server contract

- [x] **T1 `room-state` endpoint** (M)
  - Acceptance: `GET /api/v1/ink/room-state?mac=…` with `x-device-token`
    returns the version-1 JSON contract in `../SPEC.md` and the shared fixture;
    401 without valid token; ETag + 304 for unchanged content.
  - Verify: `pnpm vitest run src/app/api/v1/ink/room-state`, `pnpm typecheck`
  - Files: `src/app/api/v1/ink/room-state/route.ts`, `src/lib/room-state.ts`, schema + tests
- [x] **T2 Display registry `android-panel`** (S)
  - Acceptance: hello with model `android-panel` is accepted and completed from the
    registry (1280×800, fullcolor, both orientations); themes not snapped.
  - Verify: display/registry tests
  - Files: `src/lib/display.ts`, tests
- [x] **T3 Device asset endpoint** (S)
  - Acceptance: `GET /api/v1/ink/asset/{id}` serves an asset only if the device's
    content references it (wordmark/mark), else 404; correct content type; cache headers.
  - Verify: vitest for auth, ownership, 404
  - Files: `src/app/api/v1/ink/asset/[id]/route.ts`, tests

### Checkpoint 1

Validation: 1,130 tests passed (2 pre-existing skips), typecheck, lint, formatting,
i18n check and production build passed. The build used the repository's artificial
CI environment values; no runtime credentials or live services were used.

- [x] Server suite, lint, typecheck green; contract fixture `android/app/src/test/resources/room-state.json` exported from a test

## Phase 2: Foundation

- [x] **T4 Android project skeleton** (M)
  - Acceptance: Gradle KTS + version catalog, Compose app shows a placeholder at
    1280×800, ktlint and lint configured, unit test runs.
  - Verify: `./gradlew assembleDebug testDebugUnitTest lintDebug ktlintCheck`
- [x] **T5 Token crypto** (S)
  - Acceptance: X25519 key pair, HKDF-SHA256 (`vellum-token-v1`), AES-256-GCM
    decrypt of a server-produced fixture; private key encrypted with Keystore.
  - Verify: JVM unit test against fixture from `encryptForDevice`
- [x] **T6 Enroll flow** (M)
  - Acceptance: first-run screen asks for server URL; generates identity; hello
    loop shows "waiting for approval" with the device ID; stores token on approval.
  - Verify: unit tests with a fake server; manual run against test server
- [x] **T7 API client and cache** (M)
  - Acceptance: polls `room-state` every 60 s with ETag; keeps last state on disk;
    exposes `StateFlow<Snapshot>` with `lastSuccess` timestamp; downloads assets on change.
  - Verify: unit tests with MockWebServer (200, 304, 401 → re-enroll, timeout)

### Checkpoint 2 (continued by user instruction on 2026-11-13)

- [x] Automated local HTTP enrollment, server-token interoperability and parsed room state verified without logging secrets.
- [x] Live enrollment, approval, assignment, room retrieval and conditional revalidation verified on the explicitly approved test emulator and backend.

## Phase 3: Layouts

- [x] **T8 Domain and theme** (S): `momentOf`, list selection, `readableOn`, theme → Compose colours; ported tests from `room-status.test.ts`.
- [x] **T9 Status-panel layout** (M): Compose screen matching the server layout incl. tinted wordmark/mark, QR, offline notice; UI tests free/soon/busy/offline at 1280×800 and 800×1280.
- [x] **T10 Day-band layout** (M): chip, band with lanes and now-line, QR; same UI tests.
- [x] **T11 Day list, week view, idle return** (M): touch navigation, 30 s auto-return; UI tests.

### Checkpoint 3

- [x] Both layouts render from the contract fixture; all tests green; emulator screenshots reviewed

## Phase 4: Operation

- [x] **T12 Kiosk, boot, night** (M): boot receiver, keep-screen-on, lock-task when device owner, dimming outside opening hours, burn-in pixel shift.
- [x] **T13 Device checklist and docs** (S): README in `android/`, enroll/approve/test checklist for the APPC-10S, release APK build instructions.

### Checkpoint 4

All software tasks T1–T13 are implemented. Debug/release builds, JVM/Compose
checks, Android Keystore instrumentation and emulator layout review are complete.
The approved backend rollout, live emulator enrollment, managed kiosk and reboot
checks are complete. The user requested local/emulator checks because no physical
panel is available. Physical acceptance and a 24-hour hardware run remain
unverified; see `../DEVICE_CHECKLIST.md`.

- [ ] Spec success criteria 1–6 checked on a real panel
