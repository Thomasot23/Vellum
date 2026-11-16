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

## Phase 5: Status light (software done, device check open)

See "Planned after v1: status light" in `../SPEC.md`. T15, T15a, T15b and T16 are
implemented and tested without hardware; T14 and T17 need a physical panel.

- [ ] **T14 Confirm the LED interface** (S): documented interface found (local
      HTTP service, see `../SPEC.md` and `../SOURCES.md`). Remaining: confirm it
      on the delivered panel (Android 13, colours, token header) and answer open
      questions 5–8.
- [x] **T15 `StatusLight` abstraction** (S): interface, no-op default, mapping
      room state → signal (free, busy, off when dimmed, unknown or stale); JVM
      tests for the mapping incl. offline and night.
- [x] **T15a Server: status-light secret** (M): write-only per-device field in
      the console, encrypted at rest, audited without the value; optional revision
      marker in `room-state`; device-authenticated endpoint returning the
      `encryptForDevice` envelope; vitest for auth, ownership, absence and
      rotation and cleanup on device deletion. Storage: settings store, no
      migration.
- [x] **T15b App: receive and store the secret** (S): fetch on marker change,
      decrypt, Keystore-wrap, clear on removal; JVM tests with the shared envelope
      fixture.
- [x] **T16 Vendor adapter** (M): loopback HTTP adapter behind capability
      detection; cleartext limited to `127.0.0.1`; token stored encrypted and
      never logged; failures never affect the display; local test-server tests.
- [ ] **T17 On-device check** (S): extend `../DEVICE_CHECKLIST.md` (free, busy,
      offline, night, reboot; default device key replaced, port 3535 restricted)
      and run it on the panel.

## Phase 6: Operations, stage 1 (T18–T26 done; T27, T28 and live checks open)

Specs: `../SPEC-panel-telemetry.md`, `../SPEC-panel-enrollment-trust.md`,
`../SPEC-release-signing.md`. Server checks for every server task:
`pnpm typecheck && pnpm lint && pnpm format:check && pnpm i18n:check && pnpm test:coverage`.
Android checks for every app task:
`./gradlew testDebugUnitTest lintDebug ktlintCheck assembleDebug assembleRelease`.

- [x] **T18 Panel-info intake** (S): zod schema and base64url parser for
      `X-Panel-Info`, latest snapshot in the settings store after token check,
      removal with the device; `room-state` keeps answering if storage fails.
      Verify: `pnpm vitest run src/lib/__tests__/panel-info.test.ts src/app/api/v1/ink/room-state`.
      Files: `src/lib/panel-info.ts`, its test, `room-state/route.ts` + test, `src/app/admin/actions.ts`.
- [x] **T19 Posture and console card** (M): `assessPanelPosture` with the spec's
      rules and date boundaries; "Platform" card on the device page and a table
      badge for android panels; strings in five languages.
      Verify: posture unit tests; `pnpm build` with CI values.
      Files: `src/lib/panel-posture.ts` + test, `devices/[mac]/page.tsx`, `detail.tsx`, `device-table.tsx`, `src/i18n/messages/*.json`.
- [x] **T20 App collection and send policy** (M): `PanelInfo` from public APIs,
      header on the first poll, on change and every 15 minutes; never fails a poll.
      Verify: JVM tests with fakes and MockWebServer; instrumentation reports
      device owner and lock task on the test emulator.
      Files: `data/PanelInfo.kt`, `data/PanelClient.kt`, `data/PanelRepository.kt`, `MainActivity.kt`, tests.
- [x] **T21 App log ring and upload** (M): bounded ring of coded entries,
      redaction rules, upload via `/api/v1/ink/logs` with persisted `seq`, at most
      every 5 minutes. Verify: JVM tests for bounds, redaction, sequence, retry.
      Files: `data/PanelLog.kt`, `data/PanelClient.kt`, `data/PanelRepository.kt`, tests.

### Checkpoint 6a

- [ ] Test emulator page shows platform data within one poll; ADB on turns the
      assessment to warning; logs appear in the diagnostics card; all checks green.

- [x] **T22 Shared key fingerprint** (S): server and Kotlin implementation, shared
      fixture, shown on the panel's waiting screen.
      Verify: fixture tests on both sides; Compose test for the waiting screen.
      Files: `src/lib/key-fingerprint.ts` + test, `enroll/Fingerprint.kt`, `ui/EnrollmentScreen.kt`, fixture.
- [x] **T23 Key-bound approval** (M): `approveDevice(mac, expectedFingerprint)`
      in a serializable audited transaction, `key_changed` on mismatch, legacy
      path without key; confirmation dialog and fingerprint column.
      Verify: action tests through the library function; manual swap test.
      Files: `src/lib/auth` or `src/lib/device-approval.ts` + test, `actions.ts`, `device-table.tsx`, i18n.
- [x] **T24 Key history before approval** (S): first-seen fingerprint and change
      counter in the settings store from `handleHello`, cleared on approval and
      deletion; console warning. Verify: unit tests for swap, approval, deletion.
      Files: `src/lib/auth/index.ts`, `src/lib/key-history.ts` + test, `device-table.tsx`, i18n.
- [x] **T25 One-time key registration** (S): `POST /api/v1/ink/key` for approved
      devices without a key; 409 otherwise; audited. App call follows in
      `panel-provisioning`. Verify: route tests (once, twice, wrong state, no token).
      Files: `src/app/api/v1/ink/key/route.ts` + test, `src/lib/validation.ts`.

### Checkpoint 6b

- [ ] Panel and console show the same fingerprint; a swapped key fails approval;
      e-paper enrollment unchanged; all checks green.

- [x] **T26 Signing tooling and version policy** (M): `sign-release.sh`,
      certificate check, `versionCode` rule, `RELEASING.md`; tested only with a
      throwaway keystore in a temporary directory.
      Verify: script test run signs, verifies, rejects a wrong signer and a
      non-increasing version, prints no password.
      Files: `scripts/sign-release.sh`, `RELEASING.md`, `app/build.gradle.kts`, `release-certificate.txt` (placeholder).
- [ ] **T27 Real release key** (S, operator): only after the custody decision;
      create key, store password in the Keychain, make and test both backups,
      record the certificate digest.
- [ ] **T28 Re-provision the test emulator** (S): only after approval; wipe,
      install a signed build as device owner, verify a signed update installs and
      a differently signed build is rejected.

### Checkpoint 6c

- [ ] Signed release verified against the recorded certificate; update path on
      the emulator proven; custody documented.

## Phase 7: local live-display corrections

- [x] **T29 Enrollment race regression:** pending-only atomic key write, result-aware history, invalid-key rejection; focused/full auth regressions.
- [x] **T30 Boundary labels:** shared neutral fixture, pure formatters and both renderers; day/year/zone/DST integration checks.
- [x] **T31 Live-state contract:** complete-vs-hidden coverage, source age/expiry, clock headers, privacy and ETag tests.
- [x] **T32 Native effective status:** local transitions, unknown state, QR/LED consistency and monotonic clock tests.
- [x] **T33 Refresh behavior:** native 15-second source cache/polls, backoff/Retry-After, asset reuse and rate budgets.
- [x] **T34 Layout fidelity:** native status-panel/day-band in both orientations, labelled expanded timeline, navigation retained.
- [ ] **T35 Integrated acceptance:** complete local gates, synthetic screenshots, external review, provisioning proposal and central handoff.

No item above waives existing live, release-key, production-installation or
emulator-reset approvals.

T34: native implementations and synthetic landscape/portrait captures complete; physical-panel acceptance remains separate.
T35: local unit/build/UI gates passed; External review response and real PostgreSQL concurrency acceptance remain open. No deployment.
