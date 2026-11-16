import { readFileSync } from "node:fs";
import { beforeEach, describe, expect, it, vi } from "vitest";

const state = vi.hoisted(() => ({
  rows: [] as unknown[],
  written: [] as { key: string; value: unknown }[],
}));
vi.mock("@/db", () => ({
  db: {
    select: () => ({ from: () => ({ where: () => ({ limit: () => state.rows }) }) }),
    insert: () => ({
      values: (row: { key: string; value: unknown }) => {
        state.written.push(row);
        return { onConflictDoUpdate: () => undefined };
      },
    }),
  },
  withDbRead: async (fn: () => unknown) => fn(),
  withDbWrite: async (fn: () => unknown) => fn(),
}));

import type { DbTransaction } from "@/db";
import {
  deletePanelInfo,
  panelInfoKey,
  parsePanelInfo,
  readPanelInfo,
  writePanelInfo,
  type PanelInfo,
} from "../panel-info";

const info: PanelInfo = {
  appVersion: "0.2.0",
  appVersionCode: 2,
  androidRelease: "13",
  sdkInt: 33,
  securityPatch: "2025-10-05",
  manufacturer: "Vendor",
  model: "Panel 10",
  buildType: "user",
  buildTags: "release-keys",
  deviceOwner: true,
  lockTask: true,
  adbEnabled: false,
  developerOptions: false,
  networkType: "ethernet",
};

function encode(value: unknown): string {
  return Buffer.from(JSON.stringify(value)).toString("base64url");
}

beforeEach(() => {
  state.rows = [];
  state.written = [];
});

describe("panel info", () => {
  it("accepts the Android app's shared contract fixture", () => {
    const fixture: unknown = JSON.parse(
      readFileSync("android/app/src/test/resources/panel-info.json", "utf8")
    );
    expect(parsePanelInfo(encode(fixture))).toEqual(fixture);
  });

  it("parses a valid header and drops unknown fields", () => {
    expect(parsePanelInfo(encode({ ...info, serial: "should-not-survive" }))).toEqual(info);
  });

  it("accepts a missing patch level", () => {
    expect(parsePanelInfo(encode({ ...info, securityPatch: null }))?.securityPatch).toBeNull();
  });

  it("rejects absent, oversized, malformed and out-of-range headers", () => {
    expect(parsePanelInfo(null)).toBeNull();
    expect(parsePanelInfo("")).toBeNull();
    expect(parsePanelInfo("x".repeat(2_801))).toBeNull();
    expect(parsePanelInfo("not base64 json")).toBeNull();
    expect(parsePanelInfo(Buffer.from("{").toString("base64url"))).toBeNull();
    for (const bad of [
      { ...info, sdkInt: 7 },
      { ...info, securityPatch: "last week" },
      { ...info, networkType: "carrier-pigeon" },
      { ...info, model: "x".repeat(65) },
      { ...info, buildTags: "release keys; drop table" },
      { ...info, deviceOwner: "yes" },
    ]) {
      expect(parsePanelInfo(encode(bad))).toBeNull();
    }
  });

  it("stores the latest snapshot per device with its arrival time", async () => {
    const now = new Date("2026-11-13T12:00:00Z");
    await writePanelInfo("AABBCCDDEEFF", info, now);
    expect(state.written).toEqual([
      {
        key: "device.panelInfo.AABBCCDDEEFF",
        value: { info, receivedAt: now.toISOString() },
        updatedAt: now,
      },
    ]);
  });

  it("reads a stored snapshot and ignores unusable entries", async () => {
    expect(await readPanelInfo("AABBCCDDEEFF")).toBeNull();
    state.rows = [
      { value: { info: { ...info, sdkInt: "x" }, receivedAt: "2026-11-13T12:00:00Z" } },
    ];
    expect(await readPanelInfo("AABBCCDDEEFF")).toBeNull();
    state.rows = [{ value: { info, receivedAt: "2026-11-13T12:00:00.000Z" } }];
    expect(await readPanelInfo("AABBCCDDEEFF")).toEqual({
      info,
      receivedAt: new Date("2026-11-13T12:00:00Z"),
    });
  });

  it("deletes the entry inside the caller's transaction", async () => {
    const deleted: unknown[] = [];
    const tx = { delete: () => ({ where: (w: unknown) => deleted.push(w) }) };
    await deletePanelInfo(tx as unknown as DbTransaction, "AABBCCDDEEFF");
    expect(deleted).toHaveLength(1);
    expect(panelInfoKey("AABBCCDDEEFF")).toBe("device.panelInfo.AABBCCDDEEFF");
  });
});
