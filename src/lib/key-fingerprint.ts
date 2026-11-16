// SPDX-License-Identifier: AGPL-3.0-or-later
/**
 * A short, comparable name for a device's X25519 handshake key.
 *
 * Approval binds the device token to this key, so the operator should approve
 * the key the device really holds. The fingerprint is what a person compares:
 * the panel shows it while waiting, the console shows it next to the approve
 * button. SHA-256 of the raw 32-byte key, first 10 bytes, five groups of four hex
 * digits. 80 bits are plenty for a human comparison of one device at a time; the
 * key itself is not secret.
 *
 * The Android app implements the same function; both test against
 * `android/app/src/test/resources/key-fingerprint.json`.
 */

import { createHash } from "node:crypto";

/** The fingerprint, or null when the value is not a 32-byte base64 key. */
export function keyFingerprint(publicKeyBase64: string | null | undefined): string | null {
  if (!publicKeyBase64 || !/^[A-Za-z0-9+/]{43}=$/.test(publicKeyBase64)) return null;
  const raw = Buffer.from(publicKeyBase64, "base64");
  if (raw.length !== 32) return null;
  const hex = createHash("sha256").update(raw).digest().subarray(0, 10).toString("hex");
  return hex.toUpperCase().replace(/(.{4})(?!$)/g, "$1-");
}
