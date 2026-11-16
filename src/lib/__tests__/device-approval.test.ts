import { describe, expect, it } from "vitest";
import type { DbTransaction } from "@/db";
import { approveDeviceRecord, KeyChangedError } from "../device-approval";
import { keyFingerprint } from "../key-fingerprint";

const key = Buffer.alloc(32, 1).toString("base64");
const other = Buffer.alloc(32, 2).toString("base64");
const now = new Date("2026-11-13T12:00:00Z");

function fakeTx(row: { publicKey: string | null } | undefined) {
  const updates: unknown[] = [];
  const tx = {
    select: () => ({ from: () => ({ where: () => ({ limit: () => (row ? [row] : []) }) }) }),
    update: () => ({ set: (values: unknown) => ({ where: () => updates.push(values) }) }),
  } as unknown as DbTransaction;
  return { tx, updates };
}

describe("key-bound approval", () => {
  it("approves the key the operator compared", async () => {
    const { tx, updates } = fakeTx({ publicKey: key });
    const result = await approveDeviceRecord(tx, "AABBCCDDEEFF", keyFingerprint(key), "t", now);
    expect(result.fingerprint).toBe(keyFingerprint(key));
    expect(updates).toEqual([{ status: "approved", token: "t", approvedAt: now }]);
  });

  it("refuses when the key changed after the operator looked", async () => {
    const { tx, updates } = fakeTx({ publicKey: other });
    await expect(
      approveDeviceRecord(tx, "AABBCCDDEEFF", keyFingerprint(key), "t", now)
    ).rejects.toBeInstanceOf(KeyChangedError);
    expect(updates).toEqual([]);
  });

  it("refuses an approval without a fingerprint for a device that has a key", async () => {
    const { tx, updates } = fakeTx({ publicKey: key });
    await expect(approveDeviceRecord(tx, "AABBCCDDEEFF", null, "t", now)).rejects.toThrow(
      "key_changed"
    );
    expect(updates).toEqual([]);
  });

  it("keeps legacy devices without a key approvable, and only without a fingerprint", async () => {
    const legacy = fakeTx({ publicKey: null });
    expect(
      (await approveDeviceRecord(legacy.tx, "AABBCCDDEEFF", null, "t", now)).fingerprint
    ).toBeNull();
    expect(legacy.updates).toHaveLength(1);
    const stale = fakeTx({ publicKey: null });
    await expect(
      approveDeviceRecord(stale.tx, "AABBCCDDEEFF", keyFingerprint(key), "t", now)
    ).rejects.toBeInstanceOf(KeyChangedError);
  });

  it("refuses unknown devices", async () => {
    const { tx } = fakeTx(undefined);
    await expect(approveDeviceRecord(tx, "AABBCCDDEEFF", null, "t", now)).rejects.toThrow(
      "device_not_found"
    );
  });
});

it("does not approve a malformed stored key through the keyless legacy path", async () => {
  const { tx, updates } = fakeTx({ publicKey: "invalid-key" });
  await expect(approveDeviceRecord(tx, "AABBCCDDEEFF", null, "test", now)).rejects.toThrow(
    "key_changed"
  );
  expect(updates).toEqual([]);
});
