#!/usr/bin/env bash
# Exercises sign-release.sh with a throwaway keystore in a temporary directory.
# Never touches the real release key, the Keychain or the tracked history files.
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
unsigned=${1:-$root/app/build/outputs/apk/release/app-release-unsigned.apk}
[ -f "$unsigned" ] || { echo "build the unsigned release first: ./gradlew assembleRelease" >&2; exit 1; }
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
pass=$(openssl rand -hex 24)

keytool -genkeypair -storetype PKCS12 -keyalg RSA -keysize 2048 -validity 1 \
  -alias vellum-panel -dname "CN=Throwaway Test" -keystore "$tmp/test.p12" \
  -storepass "$pass" -keypass "$pass" >/dev/null 2>&1
digest=$(keytool -list -v -keystore "$tmp/test.p12" -storepass "$pass" -alias vellum-panel |
  sed -n 's/.*SHA256: //p' | tr -d ':' | tr 'A-F' 'a-f')
printf '%s\n' "$digest" >"$tmp/cert.txt"
printf '# test history\n' >"$tmp/history.txt"

run() {
  VELLUM_PANEL_KEYSTORE=$tmp/test.p12 VELLUM_PANEL_KS_PASS=$pass \
    VELLUM_PANEL_CERT_FILE=$tmp/cert.txt VELLUM_PANEL_HISTORY_FILE=$tmp/history.txt \
    VELLUM_PANEL_OUT=$tmp/out "$root/scripts/sign-release.sh" "$unsigned"
}
fail() { echo "FAIL: $*" >&2; exit 1; }

out=$(run 2>&1) || fail "signing with the matching certificate: $out"
grep -q "$pass" <<<"$out" && fail "password printed"
ls "$tmp/out"/VellumPanel-*.apk >/dev/null || fail "no signed APK"
(cd "$tmp/out" && shasum -a 256 -c ./*.sha256 >/dev/null) || fail "checksum file wrong"
echo "ok: signs, verifies and records"

out=$(run 2>&1) && fail "same versionCode accepted twice"
grep -q "not higher than the last release" <<<"$out" || fail "unexpected refusal: $out"
grep -q "$pass" <<<"$out" && fail "password printed on refusal"
echo "ok: refuses a versionCode that was released before"

printf '# test history\n' >"$tmp/history.txt"
printf '%064d\n' 0 >"$tmp/cert.txt"
out=$(run 2>&1) && fail "wrong certificate accepted"
grep -q "unexpected certificate" <<<"$out" || fail "unexpected refusal: $out"
echo "ok: refuses an APK signed by another certificate"

printf 'UNSET\n' >"$tmp/cert.txt"
out=$(run 2>&1) && fail "unset certificate accepted"
grep -q "record the release certificate digest" <<<"$out" || fail "unexpected refusal: $out"
echo "ok: refuses to sign before a certificate is recorded"
echo "all sign-release checks passed"
