import { beforeEach, describe, expect, it, vi } from "vitest";

const state = vi.hoisted(() => ({ rows: [] as unknown[] }));
vi.mock("@/db", () => ({
  db: {
    select: () => ({ from: () => ({ where: () => ({ limit: () => state.rows }) }) }),
  },
  withDbRead: async (fn: () => unknown) => fn(),
}));

import type { DbTransaction } from "@/db";
import {
  deleteStatusLightRecord,
  openStatusLightToken,
  readStatusLightRecord,
  replaceStatusLightToken,
  sealStatusLightToken,
  statusLightKey,
  statusLightTokenSchema,
} from "../status-light";

beforeEach(() => {
  state.rows = [];
});

describe("status-light secret", () => {
  it("round-trips a token without storing it in the clear", () => {
    const record = sealStatusLightToken("synthetic-vendor-token");
    expect(JSON.stringify(record)).not.toContain("synthetic-vendor-token");
    expect(record.revision).toMatch(/^[0-9a-f]{16}$/);
    expect(openStatusLightToken(record)).toBe("synthetic-vendor-token");
  });

  it("gives every stored value its own revision, even for the same token", () => {
    const first = sealStatusLightToken("same-token");
    const second = sealStatusLightToken("same-token");
    expect(first.revision).not.toBe(second.revision);
  });

  it("rejects tokens that could not travel in a header", () => {
    for (const bad of ["", "has space", "line\nbreak", "ümlaut", "x".repeat(513)]) {
      expect(statusLightTokenSchema.safeParse(bad).success).toBe(false);
      expect(() => sealStatusLightToken(bad)).toThrow();
    }
  });

  it("rejects a record that was tampered with", () => {
    const record = sealStatusLightToken("synthetic-vendor-token");
    const bytes = Buffer.from(record.encrypted, "base64");
    bytes.writeUInt8(bytes.readUInt8(bytes.length - 1) ^ 1, bytes.length - 1);
    expect(() =>
      openStatusLightToken({ ...record, encrypted: bytes.toString("base64") })
    ).toThrow();
  });

  it("keys entries by device", () => {
    expect(statusLightKey("AABBCCDDEEFF")).toBe("device.statusLight.AABBCCDDEEFF");
  });

  it("reads a stored record and treats missing or malformed entries as absent", async () => {
    expect(await readStatusLightRecord("AABBCCDDEEFF")).toBeNull();
    state.rows = [{ value: { encrypted: "x", revision: "not-a-revision" } }];
    expect(await readStatusLightRecord("AABBCCDDEEFF")).toBeNull();
    const record = sealStatusLightToken("synthetic-vendor-token");
    state.rows = [{ value: record }];
    expect(await readStatusLightRecord("AABBCCDDEEFF")).toEqual(record);
  });

  it("writes, replaces and removes a device's entry only for a known device", async () => {
    const calls: string[] = [];
    let deviceRows: unknown[] = [{ mac: "AABBCCDDEEFF" }];
    const tx = {
      select: () => ({ from: () => ({ where: () => ({ limit: () => deviceRows }) }) }),
      insert: () => ({
        values: (row: { key: string; value: unknown }) => {
          calls.push(`insert ${row.key}`);
          expect(JSON.stringify(row.value)).not.toContain("synthetic-vendor-token");
          return { onConflictDoUpdate: () => calls.push("upsert") };
        },
      }),
      delete: () => ({ where: () => calls.push("delete") }),
    } as unknown as DbTransaction;
    await replaceStatusLightToken(
      tx,
      "AABBCCDDEEFF",
      sealStatusLightToken("synthetic-vendor-token")
    );
    await replaceStatusLightToken(tx, "AABBCCDDEEFF", null);
    await deleteStatusLightRecord(tx, "AABBCCDDEEFF");
    expect(calls).toEqual(["insert device.statusLight.AABBCCDDEEFF", "upsert", "delete", "delete"]);
    deviceRows = [];
    await expect(replaceStatusLightToken(tx, "AABBCCDDEEFF", null)).rejects.toThrow(
      "device_not_found"
    );
    expect(calls).toHaveLength(4);
  });
});
