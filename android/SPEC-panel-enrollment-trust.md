# Spec: panel-enrollment-trust

Module of `MAP-operations.md`. Status: approved and implemented (T22–T25); the app's voucher path follows in `panel-provisioning`.

## Objective

Approval binds a device token to the X25519 key the device registered. Until
approval, any caller who knows a device ID may replace that key with its own
(`handleHello`, unauthenticated). The Android panel shows its device ID on
screen while it waits, so someone at or near the panel can register their own
key with that ID right before an operator approves it, and receive the device
token and the status-light token. Voucher-enrolled devices, the other way in,
never register a key at all, so nothing sealed can be delivered to them.

This module makes the operator approve a specific key, not just an ID, and lets
voucher-enrolled panels register their key once, authenticated.

User stories:

1. As an operator approving a panel, I compare a short key fingerprint shown on
   the panel with the one in the console, and approval only succeeds for that
   exact key.
2. As an operator I see when a pending device's key changed since it first
   appeared, and treat that as a warning.
3. As a panel enrolled by voucher, I register my key once with my token, so the
   server can seal secrets to me.

## Design

### Fingerprint

- `fingerprint = SHA-256(raw 32-byte X25519 public key)`, first 10 bytes,
  rendered as five groups of four uppercase hex digits: `3F2A-91C0-7B1E-04D9-AA51`.
- Same function on server (`src/lib/key-fingerprint.ts`) and app; a shared
  fixture checks both produce the same string.
- Shown on the panel's waiting screen under the device ID, in a large monospace
  face. Shown in the console's device table and approval dialog for pending
  devices that have a key.

### Approval bound to the key

- `approveDevice(mac, expectedFingerprint?)`:
  - If the device has a key, `expectedFingerprint` is required and must match
    the current key inside the same serializable transaction; otherwise the
    action fails with `key_changed` and nothing is approved.
  - If the device has no key (legacy firmware), approval works as today and the
    console says so explicitly.
- The console's approve button opens a confirmation showing the fingerprint and
  the instruction to compare it with the panel. The fingerprint is passed back
  from what the operator saw, so a key swapped after the page loaded makes the
  approval fail instead of succeed.
- The audit entry records the approved fingerprint.
- E-paper firmware is unchanged. Its operators get the same confirmation; the
  e-paper display does not show a fingerprint, so for them it records which key
  was approved and detects swaps between viewing and clicking.

### Key changes before approval

- `handleHello` already allows a pending device to replace its key. It now also
  records the first-seen fingerprint and a change counter in the settings store
  (`device.keyHistory.<MAC>`), removed on approval and on device deletion.
- The console marks a pending device whose key changed ("key changed since
  first contact") and shows both fingerprints.
- The hello response does not change; nothing is revealed to the caller.

### Key registration for voucher-enrolled devices

- `POST /api/v1/ink/key` with `{mac, publicKey}` and `x-device-token`.
- Accepted once: only if the device is approved and has no key. Otherwise 409,
  never an overwrite. Audited as `device.key.register` with the fingerprint.
- The app calls it right after its first authenticated poll when it enrolled by
  voucher (used by `panel-provisioning`).

## Tech Stack

As in the existing server and app; no new dependencies.

## Commands

```sh
pnpm typecheck && pnpm lint && pnpm format:check && pnpm i18n:check
pnpm vitest run src/lib/__tests__/key-fingerprint.test.ts src/lib/auth src/app/api/v1/ink/key
pnpm test:coverage
cd android && ./gradlew testDebugUnitTest lintDebug ktlintCheck assembleDebug
```

## Project Structure

```
src/lib/key-fingerprint.ts            → fingerprint
src/lib/auth/index.ts                 → key history in handleHello
src/app/admin/actions.ts              → approveDevice with expected fingerprint
src/app/admin/devices/                → confirmation dialog, table column, warning
src/app/api/v1/ink/key/route.ts       → one-time key registration
android/app/.../enroll/Fingerprint.kt → fingerprint
android/app/.../ui/EnrollmentScreen.kt → display
android/app/src/test/resources/key-fingerprint.json → shared fixture
```

## Code Style

As in the neighbouring files: audited serializable transactions for state
changes, constant-time comparisons for secrets (a fingerprint is not secret; a
plain comparison is fine), zod at every boundary, no new `any`.

## Testing Strategy

- Server unit: fingerprint against the shared fixture; approval succeeds with
  the matching fingerprint, fails with `key_changed` after a swap, works without
  a key for legacy devices; key history recorded on swap, cleared on approval
  and deletion; key registration once, 409 on second call or for a device that
  already has a key, 401 without token.
- App unit: fingerprint against the fixture; waiting screen shows it.
- Manual on the test emulator: approve with the shown fingerprint; a scripted
  key swap before clicking makes the approval fail.

## Boundaries

- Always: additive and backwards compatible for e-paper firmware; audit every
  approval and key registration; strings in five languages.
- Ask first: changing the e-paper firmware; a schema migration.
- Never: let an unauthenticated call change the key of an approved device;
  overwrite a registered key through the new route.

## Success Criteria

1. A pending panel shows the same fingerprint as the console.
2. A key replaced between opening the page and clicking approve makes the
   approval fail and leaves the device pending.
3. The console flags a pending device whose key changed since first contact.
4. A voucher-enrolled panel can register its key exactly once, after which the
   status-light endpoint serves it.
5. Existing e-paper enrollment keeps working without firmware changes.

## Open Questions

1. Should approval of a device without a key (legacy firmware) stay possible,
   or be refused once all displays run firmware that sends a key?
2. Should the approval dialog require typing the last group of the fingerprint
   instead of a confirm click?
