# Spec: panel-data-refresh

Module of `MAP-live-display.md`, depends on `panel-live-state`. Status: local
implementation authorized by follow-on instruction on 2026-11-16.

## Objective and contract

Use conditional JSON polls around 15 seconds, with bounded jitter, serialized
requests, Retry-After and exponential failure backoff capped at five minutes.
Pending enrollment uses a slower budget-compatible cadence. An activity restart
requests immediately; no second parallel polling loop or network on each UI tick.
Cap native calendar cache at 15 seconds. Track actual source fetch time on cache
hits too; do not label a cached answer newly fetched. A stricter caller must not
reuse a cache entry older than its own TTL. E-paper defaults stay unchanged.

Send live freshness separately on 304 so unchanged bookings need not be resent.
Do not hash clock/freshness renewal alone into the content ETag. Device endpoints
keep a pre-authentication ingress ceiling and a post-authentication per-device
budget so normal shared-address panels do not compete for one low room-poll quota.
No rate-limit removal or trust in an unauthenticated device ID for authorization.

Avoid branding decoding in the native JSON model and avoid fetching unchanged
assets on every poll. Revalidate retained assets periodically (at most five
minutes) and immediately on binding/assignment changes; keep revocation safe.
Presence advertises the actual native cadence. Freshness remains bounded even
when a 304 refreshes contact time.

## Structure

Server: calendar source, room model/route, room-state ETag, authenticated device
lookup and rate-limit helpers. Android: repository/client polling policy,
activity loop and asset cache.

## Testing and acceptance

Prove TTL cap after an older shared cache entry, 200/304 freshness renewal,
Retry-After and backoff bounds, one poll in flight, legacy/pending behavior,
asset reuse/revalidation/revocation and authenticated rate isolation. No claimed
end-to-end latency guarantee beyond measured source/provider behavior.

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
