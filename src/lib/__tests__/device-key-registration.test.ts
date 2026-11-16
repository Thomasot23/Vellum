import { beforeEach, describe, expect, it, vi } from "vitest";

const state = vi.hoisted(() => ({
  device: undefined as { status: string; publicKey: string | null } | undefined,
  updated: 1,
  updates: [] as unknown[],
  audits: [] as unknown[],
  isolation: undefined as unknown,
}));
vi.mock("@/db", () => ({
  db: {
    transaction: async (fn: (tx: unknown) => unknown, options: unknown) => {
      state.isolation = options;
      return fn({
        select: () => ({
          from: () => ({ where: () => ({ limit: () => (state.device ? [state.device] : []) }) }),
        }),
        update: () => ({
          set: (values: unknown) => ({
            where: () => ({
              returning: () => {
                state.updates.push(values);
                return Array.from({ length: state.updated }, () => ({ mac: "AABBCCDDEEFF" }));
              },
            }),
          }),
        }),
        insert: () => ({ values: (row: unknown) => state.audits.push(row) }),
      });
    },
  },
  withDbTransaction: async (fn: () => unknown) => fn(),
}));

import { keyFingerprint } from "../key-fingerprint";
import { registerDeviceKey } from "../device-key-registration";

const key = Buffer.alloc(32, 4).toString("base64");
const other = Buffer.alloc(32, 5).toString("base64");

beforeEach(() => {
  state.device = { status: "approved", publicKey: null };
  state.updated = 1;
  state.updates = [];
  state.audits = [];
});

describe("one-time device key registration", () => {
  it("registers a key for an approved device without one, and audits it", async () => {
    expect(await registerDeviceKey("AABBCCDDEEFF", key)).toBe("registered");
    expect(state.updates).toEqual([{ publicKey: key }]);
    expect(state.audits).toEqual([
      {
        actorType: "device",
        actorId: "AABBCCDDEEFF",
        action: "device.key.register",
        targetType: "device",
        targetId: "AABBCCDDEEFF",
        metadata: { fingerprint: keyFingerprint(key) },
      },
    ]);
    expect(state.isolation).toEqual({ isolationLevel: "serializable" });
  });

  it("never replaces a registered key", async () => {
    state.device = { status: "approved", publicKey: other };
    expect(await registerDeviceKey("AABBCCDDEEFF", key)).toBe("conflict");
    state.device = { status: "approved", publicKey: key };
    expect(await registerDeviceKey("AABBCCDDEEFF", key)).toBe("unchanged");
    expect(state.updates).toEqual([]);
    expect(state.audits).toEqual([]);
  });

  it("refuses unknown, pending devices, malformed keys and lost races", async () => {
    state.device = undefined;
    expect(await registerDeviceKey("AABBCCDDEEFF", key)).toBe("conflict");
    state.device = { status: "pending", publicKey: null };
    expect(await registerDeviceKey("AABBCCDDEEFF", key)).toBe("conflict");
    state.device = { status: "approved", publicKey: null };
    expect(await registerDeviceKey("AABBCCDDEEFF", "AAAA")).toBe("conflict");
    state.updated = 0;
    expect(await registerDeviceKey("AABBCCDDEEFF", key)).toBe("conflict");
    expect(state.audits).toEqual([]);
  });
});
