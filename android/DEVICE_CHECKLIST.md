# Panel acceptance checklist

Status: all four software phases verified locally and in the test emulator.
An explicitly approved backend rollout and live emulator enrollment were completed.
No physical panel was available; physical acceptance and a 24-hour hardware run
remain unverified. No APK has been publicly released.

## Verified locally

- [x] Server protocol fixture parses in Kotlin.
- [x] Server-produced encrypted token decrypts; tampering is rejected.
- [x] Android Keystore persists an encrypted identity across store recreation on the test emulator.
- [x] Pending, approval, 401, 204, 304, 422, HTTP failure and timeout behavior.
- [x] Room and asset cache, revalidation, assignment isolation and asset revocation.
- [x] Both layouts, free/soon/busy/offline, landscape and portrait in Compose tests.
- [x] Day/week navigation and 30-second idle return in Compose tests.
- [x] QR encode/decode and server-directed visibility.
- [x] Contrast, day boundaries, overlapping events and daylight-saving window.
- [x] Unmanaged kiosk guard, keep-screen-on and brightness/pixel-shift decisions.
- [x] Debug/release APK builds, JVM tests, lint and ktlint.

## Verified in the approved test environment

- [x] Backend image deployed after database/configuration backup; health checks passed.
- [x] Dedicated emulator client enrolled, approved and assigned existing room content.
- [x] Live TLS enrollment, encrypted token, first room response, conditional 304 and cache write.
- [x] Device Owner provisioning on the local test AVD; Lock Task blocks Home exit.
- [x] Reboot returns to the app in Lock Task mode.
- [x] Emulator DNS failure reproduced; a reachable DNS resolver restored HTTPS polling.

No live room screenshots or UI-tree exports were retained. Visual review used
synthetic fixtures. The test emulator remains provisioned as Device Owner.

## Physical panel and integrated service

Record observations and results here when authorized hardware and service access
are available. Until then these are unverified, not waived acceptance criteria.

- [ ] Confirm panel model, Android release and API level (minimum API 30).
- [ ] Install the approved APK on the designated test panel.
- [ ] Enroll with the actual Vellum service, approve and assign a room.
- [ ] Verify first display within 60 seconds of approval.
- [ ] Switch theme, branding and both layouts from the console; verify within 60 seconds.
- [ ] Compare colors, branding, touch targets and text at 1280×800 and 800×1280.
- [ ] Start/end a booking; verify correct state within 60 seconds, including hidden details.
- [ ] Scan the displayed QR code and verify its destination.
- [ ] Disconnect networking: retain state, display offline timestamp and continue clock; reconnect automatically.
- [ ] Navigate day/week and verify the idle return with real touch input.
- [ ] Provision the approved kiosk method; verify exit restrictions and restart after reboot.
- [ ] Check night brightness against actual opening-hour requirements.
- [ ] Run 24 hours on the physical panel; inspect memory trend, network recovery and display behavior.

## Release gate

- [ ] Complete the target-panel checklist above.
- [ ] Obtain explicit approval for production installation and any APK publication.
- [ ] Sign the release APK using an operator-managed key; keep signing credentials outside the repository.
