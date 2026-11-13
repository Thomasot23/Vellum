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

## Status light (planned)

- [Vendor LED control guide, 2020](https://www.syscomtec.com/_produktbereich/source/Artikel/Artikel%207977/PRODVX%20APPC-10SLB%20&%20IPPC-10SLB,%20APPC-10XPL%20LED%20Control%20SDK.pdf): serial-port sample project for first-generation panels; APPC-10XPL strips switch together (all off, all on, red, green, blue). Sample code and its licence not reviewed.
- [Vendor Android version table](https://support.prodvx.com/support/solutions/articles/48001247034-available-android-versions-on-prodvx-devices): current 10.1" models ship with Android 12 or 13.
- [Vendor API quick start guide v1.2](https://www.syscomtec.com/_produktbereich/source/Artikel/Artikel%209046/PRODVX%20API%20Quick%20Start%20Guide%20v1.2.pdf): device-local HTTP service on port 3535 with `setAllLeds`, `setAllLedsOff` and `setLeds`; token from the vendor portal; names Android 8.1 to 12.
- [Vendor API announcement](https://www.prodvx.com/blog/simplified-development-with-prodvx-api): REST service giving unsigned apps access to the LED controller; pre-installed on new R23 models since Q2 2024.
- Not yet confirmed on a device: Android 13 support, accepted colours on side-strip models, header name for the token. Nothing is implemented yet.
