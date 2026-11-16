# Releasing the panel app

Android installs an update only when it is signed with the same key as the
installed app. The panel app runs as device owner and cannot be uninstalled, so
**a lost or replaced signing key means a factory reset of every panel.** Treat
the key like the master key of the fleet.

## Versions

`versionName` in `app/build.gradle.kts` is the only number edited by hand
(`major.minor.patch`, minor and patch below 100). `versionCode` is derived from
it as `major * 10000 + minor * 100 + patch`. `scripts/sign-release.sh` refuses a
`versionCode` that is not higher than every entry in `release-history.txt`.

## One-time setup (operator)

Do this once, before the first production panel, on the machine that will sign
releases. The commands prompt for the password; never put it on a command line.

```sh
export VELLUM_PANEL_KEYSTORE="$HOME/Secure/vellum-panel-release.p12"
keytool -genkeypair -storetype PKCS12 -keyalg RSA -keysize 4096 -validity 10950 \
  -alias vellum-panel -dname "CN=Vellum Panel" -keystore "$VELLUM_PANEL_KEYSTORE"
security add-generic-password -s vellum-panel-release -a release -w
keytool -list -v -keystore "$VELLUM_PANEL_KEYSTORE" -alias vellum-panel | grep SHA256
```

- Use the same password for keystore and key, and store it only in the Keychain
  item `vellum-panel-release` and in the backups below.
- Write the certificate's SHA-256 digest (colons optional) into
  `release-certificate.txt` and commit that file. The digest is public; it lets
  the server and operators check who signed an APK.
- Keep the certificate free of personal or organisational data.

### Backups

Keep at least two backups of keystore **and** password, in different places, for
example an encrypted medium in a safe and an entry in the organisation's password
manager. Test a restore once: copy the backup to a temporary location and sign a
test build with it. Record where the backups are outside this repository.

## Signing a release

```sh
export VELLUM_PANEL_KEYSTORE="$HOME/Secure/vellum-panel-release.p12"
scripts/sign-release.sh
```

The script builds the unsigned release, aligns it, signs it with APK Signature
Schemes v2 and v3, verifies it for Android 11 and newer, checks the signer
against `release-certificate.txt`, and writes `release/VellumPanel-<version>.apk`
with its `.sha256`. It appends the version and checksum to `release-history.txt`;
commit that file with the release. Signed APKs are never committed (`/release/`
is ignored) and are not published; they are distributed through Vellum.

Verify a file someone hands you:

```sh
apksigner verify --print-certs VellumPanel-0.2.0.apk
shasum -a 256 -c VellumPanel-0.2.0.apk.sha256
```

## Testing the tooling

`scripts/test-sign-release.sh` runs the script against a throwaway keystore in a
temporary directory: it must sign and verify, refuse a repeated `versionCode`,
refuse another certificate and refuse to sign before a certificate is recorded,
without printing the password. It never touches the real key or the Keychain.

## Debug builds and test devices

Debug builds keep the debug key. A test device provisioned with a debug build as
device owner cannot be updated to a release build; wipe and re-provision it once.
No production panel ever runs a debug build.

## If the key is lost or exposed

- **Lost, backups intact:** restore from a backup, verify with a test signature.
- **Lost, no backup:** no further update is possible for installed panels. Each
  panel must be factory-reset and provisioned with a newly signed app.
- **Exposed:** someone could sign updates the panels would accept, but only
  deliver them through a channel that installs apps (ADB, the vendor service,
  Vellum). Restrict those, then plan a rotation: APK Signature Scheme v3 lets a
  new key take over through a lineage signed with the old key, so panels move
  without a reset. That needs the old key, so it cannot replace backups.
