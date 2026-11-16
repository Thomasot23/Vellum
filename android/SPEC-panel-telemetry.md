# Spec: panel-telemetry

Module of `MAP-operations.md`. Status: approved and implemented (T18–T21); live check needs a server rollout.

## Objective

Operators cannot see what runs on an Android panel: the console shows when it
last polled, nothing else. E-paper displays report firmware version, power,
radio and security evidence with every request, upload warning logs, and the
console judges their security posture. This module gives Android panels the
same visibility, as the basis for hardening and updates.

User stories:

1. As an operator I open a panel's page and see its app version, Android
   release, security patch level, model and whether it runs managed (device
   owner, lock task) and with debugging off.
2. As an operator I see a clear warning when a panel is unmanaged, has
   debugging enabled, runs a debug or test-keys build, or has a security patch
   older than 180 days.
3. As an operator I read the panel's recent warnings and errors in the existing
   diagnostics card, without anyone touching the device.
4. As a maintainer of the updates module I can rely on the server knowing each
   panel's installed `versionCode`.

## Design

### What the panel reports

Collected by the app from public Android APIs only; no permissions beyond the
existing ones, no device identifiers beyond the Vellum device ID.

| Field                            | Source                                   | Example                |
| -------------------------------- | ---------------------------------------- | ---------------------- |
| `appVersion`, `appVersionCode`   | `PackageInfo`                            | `0.2.0`, `2`           |
| `androidRelease`, `sdkInt`       | `Build.VERSION`                          | `13`, `33`             |
| `securityPatch`                  | `Build.VERSION.SECURITY_PATCH`           | `2025-10-05`           |
| `manufacturer`, `model`          | `Build`                                  | vendor model string    |
| `buildType`, `buildTags`         | `Build.TYPE`, `Build.TAGS`               | `user`, `release-keys` |
| `deviceOwner`, `lockTask`        | `DevicePolicyManager`, `ActivityManager` | `true`, `true`         |
| `adbEnabled`, `developerOptions` | `Settings.Global`                        | `false`, `false`       |
| `networkType`                    | `ConnectivityManager`                    | `ethernet`, `wifi`     |

### Transport

- One header on the existing authenticated `room-state` poll:
  `X-Panel-Info: <base64url(JSON)>`, at most 2 KiB, validated by a zod schema
  with bounded strings and enums. Unknown fields are ignored.
- Sent on the first poll after start and then only when a value changed or 15
  minutes have passed, so a fleet of panels polling every minute does not write
  1,440 rows per panel and day.
- The firmware headers (`X-Firmware-Ver` etc.) are not reused for Android
  meanings; e-paper telemetry stays as it is.

### Storage (no schema migration)

- The latest snapshot per device in the settings key-value store,
  `device.panelInfo.<MAC>`, with `receivedAt`. No history; the update module
  records version changes through OTA events.
- Removed together with the device, like the status-light entry.
- Written only after the device token is verified; a malformed header is
  dropped and logged without its content.

### Assessment

A pure function `assessPanelPosture(info, now)` in `src/lib/panel-posture.ts`
returns `ok | warning | critical` plus reasons:

- critical: `deviceOwner` false, `buildTags` contains `test-keys`,
  `buildType` not `user`
- warning: `adbEnabled` or `developerOptions` true, `lockTask` false,
  `securityPatch` older than 180 days, `sdkInt` below 33, snapshot older than
  1 hour while the panel is otherwise polling

### Console

- Device page, android-panel only: a "Platform" card with the fields above and
  the assessment, in the style of the existing security assessment for e-paper.
- Device table: one compact badge for warning or critical.
- Strings in all five console languages.

### Logs

- The app keeps a bounded in-memory ring (200 entries) of its own warnings and
  errors: enrollment failures, poll failures by class, status-light failures,
  later update failures. Entries carry a code and a short neutral message, never
  tokens, keys, URLs with query strings, room names or booking content.
- Uploaded through the existing `POST /api/v1/ink/logs` with an increasing
  `seq` persisted on the panel, at most once per 5 minutes and only when new
  entries exist. The existing retention (30 days, 50 batches) applies.

## Tech Stack

Server: Next.js route handlers, zod, Drizzle, vitest (existing). App: Kotlin,
OkHttp, kotlinx.serialization, JUnit, Robolectric, MockWebServer (existing). No
new dependencies.

## Commands

```sh
pnpm typecheck && pnpm lint && pnpm format:check && pnpm i18n:check
pnpm vitest run src/lib/__tests__/panel-posture.test.ts src/app/api/v1/ink/room-state
pnpm test:coverage
cd android && ./gradlew testDebugUnitTest lintDebug ktlintCheck assembleDebug assembleRelease
cd android && ./gradlew connectedDebugAndroidTest   # test emulator only
```

## Project Structure

```
src/lib/panel-info.ts           → schema, header parsing, settings storage
src/lib/panel-posture.ts        → assessment
src/app/api/v1/ink/room-state/  → header intake after authentication
src/app/admin/devices/[mac]/    → platform card
src/app/admin/devices/          → table badge
android/app/.../data/PanelInfo.kt      → collection
android/app/.../data/PanelLog.kt       → ring buffer and upload
```

## Code Style

Follow the neighbouring files: small pure functions with zod at the boundary,
`withDbRead`/`withDbWrite` for every query, Kotlin data classes with
`@Serializable`, no logging of secrets. Example of the intended parser shape:

```ts
export function parsePanelInfo(header: string | null): PanelInfo | null {
  if (!header || header.length > 2_800) return null;
  const parsed = panelInfoSchema.safeParse(decodeBase64UrlJson(header));
  return parsed.success ? parsed.data : null;
}
```

## Testing Strategy

- Server unit: header parsing (valid, oversized, malformed, unknown fields),
  posture rules incl. date boundaries, storage write only after authentication,
  removal with the device, no content of a malformed header in logs.
- Server route: `room-state` stores the snapshot on 200, 304 and 204 and still
  answers when storage fails.
- App unit: collection mapping from fakes, send policy (first, change, 15
  minutes), log ring bounds and redaction, upload sequence and retry.
- Instrumentation on the test emulator: real `PanelInfo` collection reports
  device owner and lock task correctly.

## Boundaries

- Always: additive only; e-paper telemetry unchanged; no identifiers beyond the
  Vellum device ID; strings in five languages; tests before commit.
- Ask first: a schema migration (if history turns out to be needed); any field
  that identifies a person or network beyond the type.
- Never: send serial numbers, IMEI, hardware MACs, SSIDs, IP addresses, tokens
  or room content; log the header content.

## Success Criteria

1. The test emulator's page shows app version, Android release, patch level,
   device owner and lock task within one poll of start.
2. Enabling ADB or developer options on the emulator turns the assessment to
   warning within 15 minutes.
3. A malformed or oversized header never fails a room poll and is not stored.
4. Panel warnings appear in the diagnostics card; no secret or room content in
   any uploaded line (asserted by tests).
5. Server suite, coverage gate and Android checks stay green.

## Open Questions

1. Is 180 days the right patch-age threshold, or should it follow an internal
   IT policy?
2. Is Android 13 (`sdkInt` 33) the right minimum for "no warning"? The app
   itself supports Android 11.
