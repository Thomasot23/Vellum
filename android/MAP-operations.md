# Capability Map: Android panel operations

Status: approved. Stage 1 (`panel-telemetry`, `panel-enrollment-trust`,
`release-signing` tooling) implemented; later stages get their own specs.

Goal: bring provisioning, updates and device visibility of the Android panel to
the level the e-paper displays already have, reusing the existing server
mechanisms where they are device-agnostic (enrollment, vouchers, token-signed
commands, rollout logic, OTA reports, logs) and replacing the parts that assume
ESP32 firmware (image format, A/B rollback, eFuse posture).

| Module id                | Responsibility                                                                                                                                                                                                                                | Depends on                           |
| ------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------ |
| `panel-telemetry`        | Panel reports app version, Android release, security patch level, device-owner and debugging state with its polls; console shows them; warnings and errors uploaded through the existing log route                                            | —                                    |
| `panel-enrollment-trust` | Close the pre-approval key window: key fingerprint shown on the panel and in the console before approval; let voucher-enrolled panels register their key once, authenticated                                                                  | —                                    |
| `panel-provisioning`     | Zero-touch setup: server origin and a provisioning voucher handed to the app at setup (ADB script first, Android QR provisioning where the firmware supports it); no typing on the panel                                                      | `panel-enrollment-trust`             |
| `panel-hardening`        | Device-owner policies on production panels: debugging features off, safe boot and factory reset restricted, console-controlled maintenance switch; state reported via telemetry                                                               | `panel-telemetry`                    |
| `release-signing`        | One long-lived release signing key, stored outside the repository; signed release builds in CI; documented key custody and recovery                                                                                                           | —                                    |
| `panel-updates`          | App updates through Vellum: operator uploads a signed APK in the console, existing channels, pins and staged rollout decide which panels get it, signed download, verification, silent install as device owner, result reported as OTA events | `panel-telemetry`, `release-signing` |
| `panel-remote-config`    | Token-signed commands for Android: server migration and re-enrollment, acknowledged through the existing config-report state machine                                                                                                          | `panel-telemetry`                    |

Build order:

1. `panel-telemetry`, `panel-enrollment-trust`, `release-signing` (independent)
2. `panel-hardening`, `panel-provisioning`
3. `panel-updates`
4. `panel-remote-config` (optional, last)

## Decisions this map assumes

1. **APK source:** uploaded in the console and stored by Vellum, not published
   as a GitHub release of the public fork. A public APK is a publication and is
   kept out of scope.
2. **No rollback on the device:** Android does not offer A/B app rollback, and a
   downgrade needs uninstalling, which device-owner apps cannot do. Safety comes
   from signature and digest checks before install, a canary cohort first, and
   the existing failure blocklist and halt switch.
3. **One signing key for the life of the fleet:** Android only accepts updates
   signed with the installed app's key. Losing or changing it means a factory
   reset per panel. Key custody is an operator decision and is asked first.
4. **Vendor service stays out:** the panel vendor's local API (app installation,
   reboot) is not used for provisioning or updates; only for the status light.
5. **Server changes stay additive:** new routes and optional fields, no change
   to the e-paper protocol. Schema migrations are asked first; where possible the
   existing tables (`otaEvents`, `firmwareRollouts`, `assets`, `settings`) are
   reused.

## Out of scope

- MDM/EMM or managed Google Play (only the GMS-certified vendor model supports it).
- Hardware-rooted attestation (Play Integrity needs GMS; key attestation may be
  evaluated inside `panel-telemetry` but is not promised).
- Network controls for the vendor service port; they belong to the operator.
