# Spec: room-boundary-labels

Module of [`MAP-live-display.md`](MAP-live-display.md), approved 2026-11-16.
Status: local implementation authorized by the follow-on instruction on
2026-11-16 to carry out the remaining points. Deployment and publication remain
separate operator decisions.

## Objective

A room display must identify the local day of a known availability boundary.
An isolated clock such as "until 13:00" must not imply today when the next
booking starts tomorrow or later. The same rule applies to the end of an
overnight booking. Both server-drawn status layouts and the native Android
status layouts follow this contract.

## Assumptions for review

- The room timezone, not the panel/host timezone, determines the local day.
- A same-day boundary keeps the compact clock-only form. A different local day
  adds an abbreviated weekday and compact numeric date. A different local year
  also includes the four-digit calendar year.
- Clock labels use 24-hour `HH:mm` in this module. Existing translated prefixes
  and suffixes remain authoritative; German continues to append " Uhr".
- English, German, French, Italian and Spanish remain supported, with English
  as the fallback. Locale-appropriate weekday/date punctuation may differ
  between platforms; omission of the local day/month/year may not.
- This module formats a boundary the current data contract already permits.
  It neither infers availability nor discloses extra hidden booking times.

## Behavior

Inputs to the pure formatter are a boundary instant, the instant being
displayed, the room timezone and the locale. The result contains the boundary
date/time, without the surrounding free/busy word or translated "until" prefix.
The existing labels compose those words and suffixes around it.

1. Convert both instants to the room timezone before comparing local dates.
2. Same local date: show the boundary clock. Different date in the same local
   year: abbreviated weekday, day and month, then clock. Different year:
   additionally show the boundary's four-digit calendar year, not ISO week-year.
3. Format actual instants across DST gaps/overlaps; never calculate a day by
   dividing elapsed milliseconds by 24 hours or compare UTC date substrings.
4. For the repeated hour at an autumn clock change, a future boundary can have
   an earlier-looking local clock. In that specific same-day case, append the
   boundary's numeric UTC offset so it is not mistaken for a past clock.
5. Without a known boundary, preserve the existing privacy/unavailable/empty
   state. Do not invent an end time, fetch extra bookings or show a date merely
   because the room has an authoritative busy flag.
6. Use the same formatter in "until" labels and any next-booking heading that
   identifies the same boundary. Booking rows and the daytime axis are outside
   this formatting change and retain their existing time conventions.
7. Reevaluate the date qualifier when the displayed local day changes, including
   from a cached Android snapshot. Server-clock anchoring and source-data validity
   belong to `panel-live-state`; this module accepts the supplied display time.

German examples below include the surrounding prefix/suffix. They are examples
of labels, not room data or booking fixtures for a real deployment.

| Display time in room        | Boundary in room            | Expected label                 |
| --------------------------- | --------------------------- | ------------------------------ |
| Mon 2026-11-16 12:10        | Mon 2026-11-16 13:00        | bis 13:00 Uhr                  |
| Mon 2026-11-16 12:10        | Tue 2026-11-17 13:00        | bis Di., 17.11., 13:00 Uhr     |
| Mon 2026-11-16 23:50        | Tue 2026-11-17 01:30        | bis Di., 17.11., 01:30 Uhr     |
| Thu 2026-12-31 22:00        | Fri 2027-01-01 09:00        | bis Fr., 01.01.2027, 09:00 Uhr |
| Sun 2026-10-25 02:50 +02:00 | Sun 2026-10-25 02:10 +01:00 | bis 02:10 Uhr (UTC+01:00)      |

For non-German locales, adapt weekday and numeric-date ordering to the locale;
keep the same date-qualification and offset rules. Unsupported locale labels
use English rather than mixing an English state prefix with an unrelated
language's weekday. The existing page-header `dateFormat` remains unchanged;
these deliberately compact boundary labels do not reuse its long-date format.

## Tech stack

Use the checked-in dependencies only: TypeScript 6.0.3, date-fns 4.4.0 and
@date-fns/tz 1.5.0 on the server; Kotlin 2.3.10, JDK 17 `java.time` and the
existing Compose stack on Android. Vitest 5.0.5, JUnit 4.13.2 and Robolectric
4.16 provide the existing test infrastructure. No dependency or schema change.

## Commands

Run from the repository root unless the command starts with `cd android`.
Focused test paths below are the intended new test files, not a claim that
they already exist.

```sh
pnpm exec vitest run src/lib/content/renderers/__tests__/room-boundary-labels.test.ts src/lib/content/renderers/__tests__/room-status.test.ts
pnpm typecheck && pnpm lint && pnpm format:check && pnpm i18n:check
pnpm test:coverage
pnpm build
cd android && ./gradlew testDebugUnitTest --tests org.vellum.panel.BoundaryLabelsTest --tests org.vellum.panel.RoomScreenTest
cd android && ./gradlew testDebugUnitTest ktlintCheck lintDebug assembleDebug assembleRelease
```

Server builds use the synthetic CI values from `.github/workflows/required-ci.yml`,
not a live configuration. Emulator/instrumentation checks use only the approved
test AVD and synthetic room data. They do not authorize a reset or live capture.

## Project structure

Intended locations, to be finalized in the module's implementation plan:

```text
src/lib/content/renderers/room-boundary-labels.ts
  pure server boundary formatter
src/lib/content/renderers/room-status.ts
  use it in status-panel/day-band state and next-booking labels
src/lib/content/renderers/__tests__/room-boundary-labels.test.ts
  date/zone/locale cases and shared fixture validation
android/app/src/main/java/org/vellum/panel/domain/BoundaryLabels.kt
  pure Android equivalent using java.time
android/app/src/main/java/org/vellum/panel/ui/RoomScreen.kt
  use it for the native boundary labels
android/app/src/test/java/org/vellum/panel/BoundaryLabelsTest.kt
  matching JVM date/zone cases
android/app/src/test/resources/room-boundary-labels.json
  synthetic cross-platform input and semantic expectation fixture
```

Reuse the existing server recording-surface checks and Android `RoomScreenTest`
for integration and essential-text visibility. Do not introduce organisation
names, proprietary assets or actual bookings into code or fixtures.

## Code style

Use a pure function with explicit time/zone/locale inputs, not an internal
clock read. Keep semantic decisions separate from drawing and translation.
Use the existing Prettier/ESLint and ktlint conventions. For example:

```kotlin
val zone = ZoneId.of(timezone)
val today = now.atZone(zone).toLocalDate()
val target = boundary.atZone(zone)
val needsDate = target.toLocalDate() != today
```

Do not copy Java date patterns blindly into date-fns: their pattern languages
and year semantics differ. Treat invalid input according to the existing
validated state boundary; never silently use the machine's timezone.

## Testing strategy

- RED/GREEN unit tests for today/tomorrow/later dates, different year, local
  midnight, equal UTC dates with different local dates, and different UTC dates
  on the same local date. Include leap day and both DST transitions.
- A shared neutral fixture fixes the instants, room zone, supported language,
  local date/year/clock components, and whether a date/year/offset is required.
  Exact German examples above are checked; platform punctuation differences
  for other locales are acceptable only if all required components are intact.
- Server integration: both layouts, free and overnight busy, include the
  expected full label in the recording surface; hidden/unavailable states do
  not gain extra information. Essential dates must not be clipped or ellipsized.
- Android integration: both layouts and orientations show the label, update
  the qualifier at local midnight without a network request, and retain
  unavailable/offline behavior. No calculation based on visible events alone
  changes whether the room is free.
- Full existing suites remain green; no test deletions, new skips or reduced
  coverage thresholds. Visual checks use synthetic references at the supported
  panel sizes and record any unresolved hardware-only limits.

## Boundaries

- Always: compare local calendar dates, preserve privacy and translated words,
  use pure tested formatters, keep fixtures neutral, retain existing work.
- Ask first: new dependencies, database migrations, new endpoints, CI changes,
  production installation, publication, keys, emulator reset or server rollout.
- Never: derive availability from a privacy-filtered empty list, fabricate a
  boundary, expose hidden data, log credentials, or make a server-generated
  bitmap the Android room UI.

Out of scope: local occupancy transitions, new validity/synchronization fields,
faster polling, SSE, layout redesign, device setup and OTA. Those belong to the
other modules. The follow-on instruction authorizes local planning and implementation;
production actions still require their own approval.

## Success criteria

1. Both status layouts on server and Android show the correct boundary date when
   it differs from the displayed local day, and include a different local year.
2. Known overnight end times and next-booking headings follow the same rule.
3. A local-midnight transition updates Android wording using the supplied clock;
   no extra fetch or additional booking information is needed for formatting.
4. Supported locales preserve all required components, and essential labels
   remain readable in both orientations without losing date/year/clock.
5. Shared-fixture, integration and existing regression checks pass; no new
   dependency, API field, endpoint, migration or live configuration is needed.

## Open questions for specification review

No missing fact blocks the proposed specification. Review the compact
weekday/date form, leading-zero `HH:mm`, and the rare repeated-hour UTC-offset
suffix before implementation. All three are proposed presentation choices.

## Sources

- Existing `room-status.ts`, `room-booking-text.ts`, Android `RoomScreen.kt`,
  `RoomState.kt` and checked-in dependency versions.
- [JDK 17 ZonedDateTime: instants, zones and DST](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/ZonedDateTime.html)
- [JDK 17 DateTimeFormatter: locale, zone and pattern semantics](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/format/DateTimeFormatter.html)
