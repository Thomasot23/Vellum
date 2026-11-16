import type { Pool } from "pg";
import { beforeEach, describe, expect, it, vi } from "vitest";

const state = vi.hoisted(() => ({ query: vi.fn() }));
vi.mock("@/db", async () => {
  const { drizzle } = await import("drizzle-orm/node-postgres");
  return {
    db: drizzle({ client: { query: state.query } as unknown as Pool }),
    withDbRead: async (fn: () => unknown) => fn(),
    withDbWrite: async (fn: () => unknown) => fn(),
    withDbTransaction: async (fn: () => unknown) => fn(),
  };
});
vi.mock("@/lib/key-history", () => ({ recordKeyChange: vi.fn() }));

import { drizzleDeviceRepo } from "../index";

const mac = "AABBCCDDEEFF";
const key = Buffer.alloc(32, 4).toString("base64");

beforeEach(() => {
  state.query.mockReset();
  state.query.mockResolvedValue({ rows: [], rowCount: 0 });
});

describe("pending-key write authorization", () => {
  it("checks pending status in the actual UPDATE, not a prior read", async () => {
    await drizzleDeviceRepo.updatePublicKey(mac, key);
    const [config, parameters] = state.query.mock.calls[0] as [{ text: string }, unknown[]];
    expect(config.text).toContain('"devices"."status" = $3');
    expect(parameters).toEqual([key, mac, "pending"]);
    expect(config.text).toContain('returning "mac"');
  });

  it("reports a lost approval race or deleted device without a successful write", async () => {
    expect(await drizzleDeviceRepo.updatePublicKey(mac, key)).toBe(false);
  });

  it("reports a key write only when the UPDATE returns the pending row", async () => {
    state.query.mockResolvedValue({ rows: [[mac]], rowCount: 1 });
    expect(await drizzleDeviceRepo.updatePublicKey(mac, key)).toBe(true);
  });
});
