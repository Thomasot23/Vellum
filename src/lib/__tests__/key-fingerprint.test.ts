import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { keyFingerprint } from "../key-fingerprint";

const fixture = JSON.parse(
  readFileSync("android/app/src/test/resources/key-fingerprint.json", "utf8")
) as { publicKey: string; fingerprint: string };

describe("key fingerprint", () => {
  it("matches the fixture shared with the Android app", () => {
    // RFC 7748 section 6.1 public test key; never a device key.
    expect(keyFingerprint(fixture.publicKey)).toBe(fixture.fingerprint);
    expect(fixture.fingerprint).toMatch(/^([0-9A-F]{4}-){4}[0-9A-F]{4}$/);
  });

  it("refuses anything that is not a 32-byte key", () => {
    for (const bad of [null, undefined, "", "AAAA", fixture.publicKey.slice(1), "!".repeat(44)]) {
      expect(keyFingerprint(bad)).toBeNull();
    }
  });

  it("changes when the key changes", () => {
    const other = Buffer.alloc(32, 7).toString("base64");
    expect(keyFingerprint(other)).not.toBe(fixture.fingerprint);
  });
});
