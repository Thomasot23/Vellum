# Spec: Vellum Room Display for Android

Status: approved · Branch: `feat/room-status-layouts` (fork)

## Objective

A native Android app that turns a wall-mounted Android touch panel (first target:
ProDVX APPC-10S, 10.1", 1280 × 800, RK3568, PoE) into a Vellum room display.
Vellum stays the single place where rooms, calendars, designs and logos are
managed; the app is a managed client like the existing e-paper displays, but
draws natively, runs continuously and reacts to touch.

Users:

- **People in the corridor** read from a distance whether the room is free, until
  when, and what is next.
- **People at the door** tap to see the day or the week and scan a QR code to book.
- **Operators** enroll, approve, assign content and design in the Vellum console,
  exactly as for e-paper displays. Nothing is configured on the device beyond the
  server address.

### User stories (v1)

1. As an operator I enter the Vellum server URL once on a new panel; the panel then
   appears as _pending_ in the console, I approve it, and it starts showing its room.
2. As a passer-by I see the state (free/busy) as a large coloured area, the time it
   lasts, and the next booking, laid out as the **status-panel** or **day-band**
   layout chosen for the content in Vellum.
3. As someone at the door I can open a day list and a week view by touch and return
   to the start screen; after 30 s without touch the app returns by itself.
4. As someone who wants the room I scan the booking QR code shown on screen.
5. As an operator I change the design (theme, wordmark, mark) or the layout in the
   console and the panel shows it within one minute, without touching the device.
6. When the server cannot be reached the panel keeps showing the last known state
   with a visible "no connection since …" notice and keeps the clock running.

Out of scope for v1: booking or releasing the room from the panel (needs write
access to the booking provider), check-in, NFC/card readers, OTA updates of the
app through Vellum, LED frame control.

## Architecture

```
Vellum server (fork)                         Android app
──────────────────────                       ─────────────────────────────
POST /api/v1/ink/hello      ◄── enroll ────  mac, X25519 public key, caps
  (existing)                 ── token ───►   AES-GCM token, decrypted locally
GET  /api/v1/ink/room-state ◄── poll 60 s ─  x-device-token
  (NEW: JSON model)          ── JSON ────►   room, events, state, theme, layout,
                                             brand, bookingUrl, locale, tz
GET  /api/v1/ink/asset/{id} ◄── on change ─  wordmark / mark (SVG or PNG), cached
  (NEW: device-authenticated)
```

Server additions (in this fork, generic, no organisation data):

- **`/api/v1/ink/room-state`**: reuses `loadRoomModel` and the device's resolved
  theme; returns JSON instead of pixels. Same token check as `/render`
  (normalized MAC query plus `x-device-token`). Includes an ETag so unchanged polls cost nothing.
- **`/api/v1/ink/asset/{id}`**: serves only assets referenced by the device's own
  content instance, authenticated by device token.
- **Display registry entry** `android-panel`: 1280 × 800, `fullcolor`, landscape and
  portrait, so enrollment and the console treat the panel like any other model.
- Full-colour themes are not snapped to a palette (already fixed in `945beb3e`).

Device identity: Android hides the hardware MAC, so the app generates a random,
locally administered 48-bit identifier once and keeps it in app storage. The
X25519 private key lives in app storage encrypted with an Android Keystore key.

## Tech Stack

- Kotlin 2.x, Jetpack Compose (Material 3 used only for primitives; all colours
  come from the Vellum theme), Kotlin coroutines + Flow.
- Ktor client or OkHttp + kotlinx.serialization for the API.
- Tink or BouncyCastle for X25519 + HKDF-SHA256 + AES-GCM (matching
  `src/lib/crypto.ts`).
- Coil (SVG decoder) for wordmark and mark, drawn tinted like the server renderer.
- Gradle Kotlin DSL with a version catalog; JDK 17; `minSdk 30` (Android 11),
  `targetSdk` current stable.
- Font: the open render face shipped with Vellum (Inter) by default; an operator
  may later supply a licensed house face through the server. No proprietary font in
  the repository.

## Commands

```
cd android
./gradlew assembleDebug          # build APK → app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # JVM unit tests
./gradlew lintDebug              # Android lint
./gradlew ktlintCheck            # formatting (ktlint)
./gradlew installDebug           # install on a connected panel (adb)
```

Server side stays as before: `pnpm test`, `pnpm lint`, `pnpm typecheck`.

## Project Structure

```
android/
  SPEC.md                 → this document
  settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml
  app/src/main/java/.../
    enroll/               → identity, key pair, hello, token decryption
    data/                 → API client, DTOs, repository, cache of last state
    domain/               → RoomState, Moment (current/next/soon), pure logic
    ui/status/            → status-panel layout
    ui/band/              → day-band layout
    ui/week/              → week view, day list
    ui/theme/             → Vellum theme → Compose colours, readableOn()
    kiosk/                → lock-task mode, screen-on, boot start, brightness
  app/src/test/           → unit tests (domain, crypto, mapping)
  app/src/androidTest/    → Compose UI tests
src/app/api/v1/ink/room-state/  → new endpoint (server)
src/app/api/v1/ink/asset/       → new endpoint (server)
```

## Code Style

Kotlin official style, enforced by ktlint. Pure functions for everything that
decides what to show, so it can be tested without a device:

```kotlin
/** What the room is doing at [now]. Mirrors `moment()` in room-status.ts. */
fun momentOf(events: List<RoomEvent>, now: Instant, isRoomFree: Boolean?): Moment {
    val sorted = events.sortedBy { it.start }
    val current = sorted.firstOrNull { it.start <= now && it.end > now }
    val next = sorted.firstOrNull { it.start > now }
    val busy = isRoomFree?.not() ?: (current != null)
    val soon = !busy && next != null && Duration.between(now, next.start) <= SOON
    return Moment(current, next, busy, soon)
}
```

State labels and other texts come from the same words the server uses
(`room-booking-text.ts`), duplicated once into Android string resources with a
unit test that compares both tables.

## Testing Strategy

- **Unit (JVM):** moment/state logic, list selection, contrast choice
  (`readableOn`), theme mapping, token decryption against a fixture produced by the
  server's `encryptForDevice`, JSON parsing of a recorded `room-state` response.
- **Compose UI tests:** both layouts in free / soon / busy / offline; text never
  clipped at 1280 × 800 and 800 × 1280; exactly one state word on screen.
- **Server:** vitest for `room-state` and `asset` (auth, ownership, ETag), in the
  existing suite.
- **On device:** manual check on the APPC-10S against a checklist (enroll, approve,
  layout switch, design switch, offline, 30 s return, reboot → app starts).

## Boundaries

- **Always:** keep organisation names, logos, colours and URLs out of the
  repository (they are Vellum data); neutral git identity; run unit tests, lint and
  ktlint before every commit; keep the existing device protocol unchanged.
- **Ask first:** new server endpoints beyond the two above, database migrations,
  new third-party dependencies beyond those listed, any write access to the booking
  provider, installing the app on a production panel, publishing an APK.
- **Never:** store the device token or private key unencrypted; log tokens; ship a
  proprietary font or logo in the repo; disable TLS verification; mention the
  assistant in commits or files.

## Success Criteria

1. A fresh APPC-10S with only the server URL entered shows up as pending, is
   approved in the console, and shows its room within 60 s of approval.
2. Both layouts render on 1280 × 800 with the design colours exactly as configured
   (no palette snapping) and match the server's e-paper rendering in structure.
3. State changes (booking starts/ends) appear within 60 s; the clock is always
   current to the minute.
4. With the server unreachable the last state stays visible with a timestamped
   notice; recovery is automatic.
5. The app survives a reboot (starts by itself in kiosk mode) and a 24 h run
   without memory growth or screen burn-in protection issues (pixel shift or dim at
   night).
6. All unit and UI tests pass in CI; server suite stays green.

## Open Questions

1. Which Android version runs on your APPC-10S units? (`minSdk 30` assumes
   Android 11 or newer.)
2. Should the app be locked into kiosk mode by the app itself (device-owner
   provisioning via adb once per panel), or does ProDVX's own kiosk setting suffice?
3. Night behaviour: screen off outside opening hours, or dimmed?
4. Do you want the house face (Unit) on the panel once the licence is clear, or
   stay with the open face?

## Server API contract (version 1)

Both GET endpoints require `?mac=AABBCCDDEEFF` and `x-device-token`. MACs with
colons are also accepted. The token is never placed in a URL. Authentication and
rate limiting happen before content lookup or conditional cache handling. Device
assignments inherit content, theme and timezone from the site, as with `/render`.

`GET /api/v1/ink/room-state` returns the bare JSON object validated by
`src/lib/room-state.ts`. The exact version-1 response is recorded in
[`app/src/test/resources/room-state.json`](app/src/test/resources/room-state.json).
Its fields are `version`, `room {name, subtitle}`, `locale`, `timezone`,
`dateFormat`, `layout`, `state {isRoomFree, providerUnavailable, nextEventStart}`,
`events [{start, end, subject, organizer, isPrivate}]`, `theme`,
`brand {titleLines, subtitleLines, wordmarkAssetId, markAssetId}`,
`booking {url, visibility, showQr}`, and `serverTime`. Timestamps are UTC ISO-8601.
Events have already passed the room privacy policy. An empty list does not mean a
room is free: use `state.isRoomFree`. `providerUnavailable` must display an outage,
not a confident busy/free claim. Credentials and provider configuration are absent.

Poll every 60 seconds. Responses use `Cache-Control: private, no-cache` and a weak
ETag that excludes `serverTime`. Send it as `If-None-Match`; a 304 keeps the cached
state. Keep the client clock running, including after a 304. Other statuses:
400 invalid MAC, 401 invalid token, 404 missing device or assigned content,
204 approved device without assigned content, 422 assigned content is not a room,
429 rate limit, 500 model/theme failure. On 204 clear any previously assigned room;
on 401 return to enrollment. Never interpret an error as an empty calendar.

`GET /api/v1/ink/asset/{id}` uses the same authentication and returns bytes with
the stored MIME type, a content-based ETag, and private revalidation. Only the
current room content's wordmark and mark may be retrieved; missing/unreferenced
assets and non-room content return 404. Ownership is checked again before a 304,
so a previously cached asset grants no continuing access after reassignment.

To intentionally update the contract fixture from the route test:

```sh
UPDATE_ROOM_STATE_FIXTURE=1 pnpm vitest run src/app/api/v1/ink/room-state
```

## Implemented native calendar window

The JSON route requests seven calendar days starting at midnight in the resolved
room timezone. Day boundaries follow daylight saving; provider caching is capped
at 60 seconds for this route. Existing e-paper rendering keeps its rolling
16-hour query. The native week view shows these seven days and labels counts as
visible bookings because privacy policies may hide details. The intermediate UI checkpoint is waived for implementation. Production installation/publication and physical acceptance remain
separate gates.
