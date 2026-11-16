# Spec: release-signing

Module of `MAP-operations.md`. Status: approved; tooling implemented (T26). Real key (T27) and emulator re-provisioning (T28) wait for decisions.

## Objective

Android installs an update only if it is signed with the same key as the
installed app. The panel app runs as device owner and cannot be uninstalled, so
a lost or changed signing key forces a factory reset of every panel. Today the
release build is unsigned and no key exists.

This module establishes one long-lived release key before the first production
panel, a reproducible way to sign and verify releases, and the custody rules
that keep the key available and secret.

## Design

### Key

- One upload-free, self-managed signing key: RSA 4096 in a PKCS#12 keystore,
  validity 30 years, neutral subject (`CN=Vellum Panel`), no personal or
  organisational data in the certificate.
- APK Signature Schemes v2 and v3 (v3 keeps the option of planned key rotation
  through a signed lineage, which still needs the old key).
- The certificate's SHA-256 digest is public information. It is recorded in
  `android/release-certificate.txt` so that the server (updates module) and
  operators can check an uploaded APK's signer.

### Custody (recommended option A)

- **A. Local, operator-held (recommended):** keystore file on the release
  machine, encrypted at rest; keystore password in the macOS Keychain, never in
  files, shell history or the repository; at least one offline backup of
  keystore and password in separate places (for example an encrypted medium in
  a safe and a password manager entry). Signing runs locally through a script.
- **B. CI-held:** keystore and password as GitHub Actions secrets of the fork.
  Rejected for now: the fork is public, and a workflow change could exfiltrate
  the key; it also spreads custody to the hosting provider.

### Signing workflow

- `android/scripts/sign-release.sh`:
  1. builds `assembleRelease` (unsigned),
  2. reads the keystore path from an environment variable and the password from
     the Keychain item `vellum-panel-release` via `security find-generic-password`,
  3. signs with `zipalign` and `apksigner sign --v2-signing-enabled true
--v3-signing-enabled true`,
  4. verifies with `apksigner verify --print-certs` and fails unless the
     certificate digest matches `release-certificate.txt`,
  5. writes `VellumPanel-<version>.apk` and its `.sha256` next to each other.
- The password is passed to `apksigner` through an environment variable for the
  single call (`--ks-pass env:`), never on the command line.
- Version policy: `versionCode` strictly increasing; derived from
  `versionName` (`major * 10000 + minor * 100 + patch`); the build fails if a
  release would not increase it.

### Debug and test devices

- Debug builds keep the debug key. The test emulator, provisioned with the
  debug build as device owner, must be re-provisioned (wiped) once to move to
  release-signed builds. No production panel ever runs a debug-signed build.

## Tech Stack

Android build tools 36 (`zipalign`, `apksigner`), JDK 17 `keytool`, macOS
`security`. No new dependencies.

## Commands

```sh
keytool -genkeypair -storetype PKCS12 -keyalg RSA -keysize 4096 -validity 10950 \
  -alias vellum-panel -dname "CN=Vellum Panel" -keystore "$VELLUM_PANEL_KEYSTORE"
security add-generic-password -s vellum-panel-release -a release -w
android/scripts/sign-release.sh
apksigner verify --print-certs VellumPanel-0.2.0.apk
```

## Project Structure

```
android/scripts/sign-release.sh     → signing and verification
android/release-certificate.txt     → public certificate digest
android/RELEASING.md                → custody, backup, recovery, checklist
android/app/build.gradle.kts        → version policy check
```

## Code Style

Shell with `set -euo pipefail`, no secret in arguments or output, every step
verified before the next. Example:

```sh
digest=$(apksigner verify --print-certs "$apk" | sed -n 's/.*SHA-256 digest: //p')
[ "$digest" = "$(cat android/release-certificate.txt)" ] || { echo "wrong signer" >&2; exit 1; }
```

## Testing Strategy

- Script tested with a throwaway keystore in a temporary directory (never the
  real key): signs, verifies, rejects a wrong certificate, refuses a
  non-increasing `versionCode`, prints no password.
- Manual once: install a signed build on a freshly wiped test emulator, then a
  second signed build with a higher `versionCode` as an update; a debug-signed
  build is rejected as an update.

## Boundaries

- Always: key and password outside the repository; verify every signed APK
  against the recorded certificate; keep the backup current.
- Ask first: creating the real key; choosing its storage places; publishing any
  signed APK; wiping the test emulator.
- Never: commit a keystore, password or signed release APK; put the password on
  a command line or in logs; sign with a different key once panels are in
  production.

## Success Criteria

1. A real key exists with documented custody and a tested offline backup.
2. `sign-release.sh` produces a signed, verified APK and its digest without
   printing secrets.
3. A signed update installs over a signed build on the test emulator; a build
   with a different key is rejected.
4. `RELEASING.md` explains recovery and what happens if the key is lost.

## Open Questions

1. Custody: option A as recommended? Which machine signs releases, and where
   do the two backups live?
2. Should a second person hold a sealed copy (four-eyes), given that losing the
   key means a factory reset per panel?
