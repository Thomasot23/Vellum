import { beforeEach, describe, expect, it, vi } from "vitest";

const state = vi.hoisted(() => ({
  rows: [] as unknown[],
  written: [] as unknown[],
  deviceStatus: "pending" as string | null,
}));
vi.mock("@/db", async () => {
  const { devices } = await import("@/db/schema");
  const tx = {
    select: () => ({
      from: (table: unknown) => ({
        where: () => ({
          limit: () => {
            const rows =
              table === devices
                ? state.deviceStatus
                  ? [{ status: state.deviceStatus }]
                  : []
                : state.rows;
            return Object.assign(Promise.resolve(rows), { for: () => Promise.resolve(rows) });
          },
        }),
      }),
    }),
    insert: () => ({
      values: (row: unknown) => {
        state.written.push(row);
        return { onConflictDoUpdate: () => undefined };
      },
    }),
  };
  return {
    db: { ...tx, transaction: async (fn: (tx: unknown) => unknown) => fn(tx) },
    withDbRead: async (fn: () => unknown) => fn(),
    withDbWrite: async (fn: () => unknown) => fn(),
    withDbTransaction: async (fn: () => unknown) => fn(),
  };
});

import type { DbTransaction } from "@/db";
import { keyFingerprint } from "../key-fingerprint";
import { parseKeyHistory } from "../key-history-schema";
import { deleteKeyHistory, keyHistoryKey, recordKeyChange } from "../key-history";

const first = Buffer.alloc(32, 1).toString("base64");
const now = new Date("2026-11-13T12:00:00Z");

beforeEach(() => {
  state.rows = [];
  state.written = [];
  state.deviceStatus = "pending";
});

describe("key history", () => {
  it("records the first key's fingerprint on the first change", async () => {
    await recordKeyChange("AABBCCDDEEFF", first, now);
    expect(state.written).toEqual([
      {
        key: "device.keyHistory.AABBCCDDEEFF",
        value: {
          firstFingerprint: keyFingerprint(first),
          changes: 1,
          lastChangedAt: now.toISOString(),
        },
        updatedAt: now,
      },
    ]);
  });

  it("keeps the original fingerprint and counts further changes", async () => {
    state.rows = [
      {
        value: {
          firstFingerprint: "AAAA-BBBB-CCCC-DDDD-EEEE",
          changes: 2,
          lastChangedAt: "2026-11-12T00:00:00.000Z",
        },
      },
    ];
    await recordKeyChange("AABBCCDDEEFF", Buffer.alloc(32, 9).toString("base64"), now);
    expect(state.written[0]).toMatchObject({
      value: { firstFingerprint: "AAAA-BBBB-CCCC-DDDD-EEEE", changes: 3 },
    });
  });

  it("parses only well-formed entries and deletes inside a transaction", async () => {
    expect(parseKeyHistory({ changes: 0 })).toBeNull();
    expect(parseKeyHistory(null)).toBeNull();
    const deleted: unknown[] = [];
    const tx = { delete: () => ({ where: (w: unknown) => deleted.push(w) }) };
    await deleteKeyHistory(tx as unknown as DbTransaction, "AABBCCDDEEFF");
    expect(deleted).toHaveLength(1);
    expect(keyHistoryKey("AABBCCDDEEFF")).toBe("device.keyHistory.AABBCCDDEEFF");
  });
});

it("does not resurrect history after approval or device deletion", async () => {
  for (const status of ["approved", "rejected", null]) {
    state.deviceStatus = status;
    await recordKeyChange("AABBCCDDEEFF", first, now);
    expect(state.written).toEqual([]);
  }
});
