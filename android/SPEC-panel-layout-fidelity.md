# Spec: panel-layout-fidelity

Module of `MAP-live-display.md`, depends on `room-boundary-labels` and
`panel-live-state`. Status: local implementation authorized by follow-on
instruction on 2026-11-16.

## Objective and contract

Reproduce the agreed status-panel and day-band compositions natively in Compose,
not by downloading server room-frame bitmaps. Theme and branding remain data.
Status-panel: continuous left status ground about 40% in landscape, wordmark
above the large status, large mark in the free lower corner; room/location/date,
today list with organizers and booking QR on the right. Portrait stacks the
status ground above the agenda. No unrelated colored top bar.
Day-band: wordmark top left, date/update top, room/location and state chip below,
current/next booking detail, large mark on the right, labelled event blocks and
now marker at the bottom. Like the server, use the default 07:00–20:00 display
range expanded for early/late/overnight events and the current time; this is a
display range, not a fabricated opening-hours configuration.

Keep touch day/week navigation, 30-second idle return, offline indication and
QR booking. Status/QR/LED decisions come from the shared live domain state.
Use proportional responsive dimensions with readable text, safe wrapping and
independent scrollable event lists; never shrink essential dates away.

## Structure

Android `ui/RoomScreen.kt` and focused home-layout components; pure timeline
geometry in domain code; existing resources, Inter and operator-supplied assets.

## Testing and acceptance

Both orientations, both layouts, free/busy/later-day/unavailable/offline/unknown,
long room and event names, missing branding and overlapping/overnight events.
Actual native screenshots use synthetic fixtures and are compared with the
agreed structural references. HTML mockups are not evidence of implementation.
Physical color/touch/PoE and the 24-hour hardware run remain separate acceptance.

## Stack, commands and style

Inherit the checked-in versions, commands, project conventions and boundaries
from `SPEC.md` and `SPEC-room-boundary-labels.md`. No dependencies or migrations.
Server gates: `pnpm typecheck && pnpm lint && pnpm format:check && pnpm i18n:check && pnpm test:coverage`; production build uses synthetic CI values.
Android gates: `cd android && ./gradlew testDebugUnitTest ktlintCheck lintDebug assembleDebug assembleRelease`.
Use pure domain functions with explicit clocks and neutral shared fixtures.

## Boundaries

Always: privacy before rendering, tokens only through existing authenticated
paths, retain existing work, English neutral repository content. Ask first:
server rollout, publication, keys, emulator wipe, production installation, new
endpoints/dependencies/migrations/CI changes. Never disable TLS or log secrets.
