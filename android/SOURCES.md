# Implementation references

- [AGP 9.2 compatibility](https://developer.android.com/build/releases/agp-9-2-0-release-notes): API 37, Gradle 9.4.1 and JDK 17.
- [Built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin): AGP supplies Kotlin; no legacy Android Kotlin plugin is applied.
- [Compose compiler](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler): versioned compiler plugin and Compose BOM.
- [Android Keystore](https://developer.android.com/privacy-and-security/keystore) and [AES-GCM key specification](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec): non-exportable wrapping key and provider-generated IVs.
- [Lock task mode](https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode): Device Owner allowlisting before starting lock task.
- [Background activity starts](https://developer.android.com/guide/components/activities/background-starts): boot behavior requires managed-device or Home-launcher provisioning.
- [Backup exclusions](https://developer.android.com/identity/data/autobackup): explicit cloud and device-transfer exclusions, in addition to no-backup storage.
- [Gradle wrapper verification](https://docs.gradle.org/current/userguide/gradle_wrapper.html#sec:verification): distribution SHA-256 pinned in wrapper properties.
- [RFC 7748, section 6.1](https://www.rfc-editor.org/rfc/rfc7748#section-6.1): the interoperability test uses a published scalar, never a real device private key.
- [ZXing Core](https://github.com/zxing/zxing): QR encoding, with decode-roundtrip verification.

The encryption wire contract is the server's `src/lib/crypto.ts`; a generated
public test envelope is checked independently by Node and Kotlin. The room JSON
contract is `src/lib/room-state.ts`; both runtimes parse the same fixture. The
large-text contrast threshold matches `src/lib/theme.ts` (3:1); no palette
snapping is applied to native full-color themes.
