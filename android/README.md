# Vellum Panel for Android

A native, device-authenticated Vellum room display for Android 11 and newer.
The first target is a 1280 × 800 PoE touch panel. Room content, branding, theme,
locale and layout remain Vellum data. No organisation branding is bundled.

## Build and verify

Use JDK 17 and Android SDK platform `android-37.0` with Build Tools `36.0.0`.
The checked-in Gradle 9.4.1 wrapper verifies the official distribution checksum.
Dependencies are pinned in `gradle/libs.versions.toml`.

```sh
./gradlew assembleDebug assembleRelease
./gradlew testDebugUnitTest lintDebug ktlintCheck
./gradlew connectedDebugAndroidTest  # authorized test devices only
```

Set `JAVA_HOME` and `ANDROID_HOME` for your local installation, or configure an
ignored `local.properties` containing `sdk.dir`. Do not commit local SDK paths,
signing keys, device tokens or private keys. `assembleRelease` creates an unsigned
APK; release signing and publication are separate operator-controlled steps.
The debug APK is under `app/build/outputs/apk/debug/app-debug.apk`.

The Android CI workflow builds and runs JVM/Compose tests, lint and ktlint, and
checks the token fixture and language resources against the server implementation.
Android Keystore is additionally exercised by `DeviceKeystoreTest` on a test
emulator/device. The JVM tests use real local HTTP via MockWebServer.

## Enroll

1. Install the APK on an approved test panel and open **Vellum Panel**.
2. Enter the server's HTTPS origin, without a path, query, password or token.
3. Approve the displayed device ID in Vellum and assign room-booking content.
4. Choose a theme and either `status-panel` or `day-band` in Vellum.

The app generates a locally administered identifier because Android does not
expose a reliable hardware MAC. A stable X25519 key pair is enrolled through
`/api/v1/ink/hello`. Only the encrypted-token response is accepted. The token and
private scalar are stored together as AES-GCM ciphertext under a non-exportable
Android Keystore key; app backup and device transfer are excluded. Secrets are
never logged. TLS verification is always enabled; redirects receive no credentials.
A deployment using a private CA must provision a trusted system CA on the panel.

Pending enrollment retries every five seconds. An approved panel polls the room
JSON every 60 seconds, uses ETag revalidation, and caches the last successful
snapshot. A 304 refreshes the contact time. A 401 drops the token and room cache
and returns to enrollment. A 204 clears a previous room assignment; a 422 explains
that the assigned content is not a room. Network failures retain the last room
with a visible connection timestamp. A provider outage has its own unavailable
state and never claims a room is free.

Brand images are downloaded only through the device-authenticated asset route,
revalidated and pruned after reassignment. Cache data is bound to the server and
device identifier. No provider credentials reach the app. Clear app storage in
Android settings to enroll against another server; this deliberately creates a
new identity that needs approval again.

## Display behavior

Both native layouts support landscape and portrait. Original `timeline` and
`stacked` content uses the status-panel presentation in this client. The clock
continues locally; the server's configured room timezone controls all date and
time presentation. The JSON endpoint supplies seven local calendar days starting
today, including overnight events and daylight-saving transitions; e-paper
rendering retains its original window. The native endpoint caps provider caching
at 60 seconds. Day/week navigation returns home after 30 seconds without touch.
Week counts refer to **visible** bookings, respecting the room privacy policy.

The app uses the bundled Inter font under its SIL Open Font License, includes
booking QR codes, and tints operator-supplied brand images like the server.
QR visibility obeys the server response. Core room labels for English, German,
French, Italian and Spanish are checked against the server's string table.

The foreground activity keeps the screen on and hides system bars. Brightness
uses the current room timezone: full brightness from 08:00 through 17:59, 15%
otherwise. The content shifts a few pixels each minute. These default hours live
in `PanelPolicy`; no additional administrative scheduling API is introduced.

## Managed kiosk and reboot

Lock-task is enabled only when the app is Device Owner. Unmanaged devices remain
usable without entering screen pinning. Android background-launch restrictions
mean automatic restart after boot requires Device Owner or selecting the app as
the device's Home launcher. Confirm the device's management method before rollout.

On an explicitly authorized, freshly provisioned test device with no accounts:

```sh
adb -s SERIAL install app/build/outputs/apk/debug/app-debug.apk
adb -s SERIAL shell dpm set-device-owner org.vellum.panel/.kiosk.PanelAdmin
adb -s SERIAL shell am start -n org.vellum.panel/.MainActivity
```

Device-owner provisioning changes device management and can require factory
resetting an already provisioned panel. Do not run it on production hardware
without approval. The boot receiver starts the app for Device Owner installations.
Alternatively provision the app as the default Home launcher using your existing
management system. Test reboot and recovery on the target firmware.

## Synthetic preview

Debug builds contain an offline `DemoActivity` using only neutral fixture data:

```sh
adb -s SERIAL shell am start -S -n org.vellum.panel/.DemoActivity --es layout status-panel
adb -s SERIAL shell am start -S -n org.vellum.panel/.DemoActivity --es layout day-band
```

This activity, fixture and entry point are absent from release builds. It cannot
configure, enroll or expose an actual device identity. Production APK installation
and APK publication need separate approval.

See [DEVICE_CHECKLIST.md](DEVICE_CHECKLIST.md) for the remaining hardware
acceptance and [SOURCES.md](SOURCES.md) for implementation references.
