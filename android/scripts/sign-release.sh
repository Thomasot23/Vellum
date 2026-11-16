#!/usr/bin/env bash
# Sign and verify a release of the Vellum panel app. See ../RELEASING.md.
#
# The keystore never enters the repository. The password comes from the macOS
# Keychain item named by VELLUM_PANEL_KEYCHAIN_ITEM and reaches apksigner only
# through the environment of that one call; it is never printed or passed as an
# argument. VELLUM_PANEL_KS_PASS may be preset instead, for the script's own test.
set -euo pipefail

die() { echo "sign-release: $*" >&2; exit 1; }

root=$(cd "$(dirname "$0")/.." && pwd)
: "${VELLUM_PANEL_KEYSTORE:?set VELLUM_PANEL_KEYSTORE to the release keystore path}"
alias_name=${VELLUM_PANEL_KEY_ALIAS:-vellum-panel}
keychain_item=${VELLUM_PANEL_KEYCHAIN_ITEM:-vellum-panel-release}
cert_file=${VELLUM_PANEL_CERT_FILE:-$root/release-certificate.txt}
history_file=${VELLUM_PANEL_HISTORY_FILE:-$root/release-history.txt}
out_dir=${VELLUM_PANEL_OUT:-$root/release}
tools=${ANDROID_BUILD_TOOLS:-${ANDROID_HOME:-$HOME/Library/Android/sdk}/build-tools/36.0.0}

[ -f "$VELLUM_PANEL_KEYSTORE" ] || die "keystore not found"
expected=$(grep -v '^#' "$cert_file" | tr -d '[:space:]' | tr 'A-F' 'a-f' | tr -d ':')
[[ "$expected" =~ ^[0-9a-f]{64}$ ]] || die "record the release certificate digest in $cert_file first"

if [ $# -ge 1 ]; then
  unsigned=$1
else
  (cd "$root" && ./gradlew -q assembleRelease)
  unsigned=$root/app/build/outputs/apk/release/app-release-unsigned.apk
fi
[ -f "$unsigned" ] || die "unsigned APK not found"

# sed reads to the end: an early-closing reader would kill aapt2 with SIGPIPE under pipefail.
badging=$("$tools/aapt2" dump badging "$unsigned" | sed -n '/^package:/p')
code=$(sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p" <<<"$badging")
name=$(sed -n "s/.*versionName='\([^']*\)'.*/\1/p" <<<"$badging")
[ -n "$code" ] && [ -n "$name" ] || die "cannot read the version from the APK"
last=$({ grep -v '^#' "$history_file" 2>/dev/null || true; } | awk 'NF {print $1}' | sort -n | tail -1)
if [ -n "$last" ] && [ "$code" -le "$last" ]; then
  die "versionCode $code is not higher than the last release ($last); raise versionName"
fi

if [ -z "${VELLUM_PANEL_KS_PASS:-}" ]; then
  VELLUM_PANEL_KS_PASS=$(security find-generic-password -s "$keychain_item" -w 2>/dev/null) ||
    die "Keychain item $keychain_item not found"
fi
export VELLUM_PANEL_KS_PASS

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
"$tools/zipalign" -p -f 4 "$unsigned" "$work/aligned.apk"
"$tools/apksigner" sign \
  --ks "$VELLUM_PANEL_KEYSTORE" --ks-key-alias "$alias_name" \
  --ks-pass env:VELLUM_PANEL_KS_PASS --key-pass env:VELLUM_PANEL_KS_PASS \
  --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true \
  --out "$work/signed.apk" "$work/aligned.apk" >/dev/null
unset VELLUM_PANEL_KS_PASS

verify=$("$tools/apksigner" verify --min-sdk-version 30 --print-certs "$work/signed.apk")
digest=$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' <<<"$verify" | tr -d '[:space:]')
[ "$digest" = "$expected" ] || die "signed with an unexpected certificate ($digest)"

mkdir -p "$out_dir"
apk="$out_dir/VellumPanel-$name.apk"
cp "$work/signed.apk" "$apk"
sum=$(shasum -a 256 "$apk" | awk '{print $1}')
printf '%s  %s\n' "$sum" "$(basename "$apk")" >"$apk.sha256"
printf '%s %s %s\n' "$code" "$name" "$sum" >>"$history_file"
echo "Signed $(basename "$apk") (versionCode $code), certificate verified, sha256 $sum"
