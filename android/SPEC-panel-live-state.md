# Spec: panel-live-state

Module of `MAP-live-display.md`. Status: authorized for local implementation by
the follow-on instruction on 2026-11-16.

## Objective and contract

Android draws a live native surface from data. Known time transitions happen
locally without a poll; hidden details must not be reconstructed from an empty
visible list. Add optional version-1 `live` metadata: `completeEvents`,
`evaluatedAt`, `sourceFetchedAt`, `validUntil`, `windowStart`, `windowEnd`.
Complete event coverage is true only for policies retaining all event times.
For complete coverage the app derives occupancy locally over the supplied
window; for Hide All it uses the short-lived authoritative boolean only.
No hidden occupancy interval or new hidden boundary is sent.

Expiry is the minimum of coverage end and source fetch time plus 180 seconds;
for incomplete coverage also cap at evaluation time plus 30 seconds. Expired,
future-dated or unavailable evidence means unknown, not free. Old servers without
live metadata use the authoritative boolean for at most thirty seconds from
the payload server time; contact-only 304s cannot renew this evidence. Display, QR and LEDs
consume one effective state. Unknown/unavailable means no QR and LEDs off.

Server clock is refreshed on 200 and 304 through an authenticated response
header and advanced on Android using monotonic elapsed time anchored before the request
so response latency cannot extend freshness deadlines. Server contact
and source age remain distinct. On restart cached content can be displayed but
new live evidence needs a new clock anchor before an asserted status.

## Structure

Server: `src/lib/room-state.ts`, `src/lib/calendar/source.ts`,
`src/lib/content/renderers/room-booking.ts`, `src/app/api/v1/ink/room-state/route.ts`.
Android: `data/RoomState.kt`, `data/PanelClient.kt`, `data/PanelRepository.kt`,
`domain/RoomLogic.kt`, `domain/StatusLight.kt`, `MainActivity.kt`, `ui/RoomScreen.kt`.

## Testing and acceptance

Prove booking start/end without network, overlap, hidden busy with empty list,
stale source despite 304, expiry/coverage edges, bad/future metadata, clock jump
and restart, source unavailable and backward compatibility. Server metadata must
not add hidden event times. All consumers agree for the same snapshot and time.

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
