import { describe, expect, it } from "vitest";
import type { PanelInfo } from "../panel-info";
import { assessPanelPosture } from "../panel-posture";

const info: PanelInfo = {
  appVersion: "0.2.0",
  appVersionCode: 2,
  androidRelease: "13",
  sdkInt: 33,
  securityPatch: "2026-10-05",
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
const now = new Date("2026-11-13T12:00:00Z");

function assess(change: Partial<PanelInfo>, receivedAt = now, lastSeen: Date | null = now) {
  return assessPanelPosture({ ...info, ...change }, receivedAt, lastSeen, now);
}

describe("panel posture", () => {
  it("is ok for a managed, patched release build", () => {
    expect(assess({})).toEqual({ level: "ok", reasons: [] });
  });

  it("is critical for unmanaged, test-keys or non-user builds", () => {
    expect(assess({ deviceOwner: false })).toEqual({
      level: "critical",
      reasons: ["notDeviceOwner"],
    });
    expect(assess({ buildTags: "dev-keys,test-keys" }).reasons).toEqual(["testKeys"]);
    expect(assess({ buildType: "userdebug" })).toEqual({
      level: "critical",
      reasons: ["nonUserBuild"],
    });
  });

  it("warns about debugging, lock task, old Android and unknown patch level", () => {
    expect(
      assess({
        adbEnabled: true,
        developerOptions: true,
        lockTask: false,
        sdkInt: 31,
        securityPatch: null,
      })
    ).toEqual({
      level: "warning",
      reasons: ["adbEnabled", "developerOptions", "lockTaskOff", "patchUnknown", "androidOld"],
    });
  });

  it("warns once the patch level is older than 180 days", () => {
    // 2026-05-17 is exactly 180 days before the reference time's date.
    expect(assess({ securityPatch: "2026-05-17" }).level).toBe("ok");
    expect(assess({ securityPatch: "2026-05-16" }).reasons).toEqual(["patchOld"]);
  });

  it("warns when a polling panel has not reported for more than an hour", () => {
    const reported = new Date(now.getTime() - 60 * 60 * 1000);
    expect(assess({}, reported, now).level).toBe("ok");
    expect(assess({}, new Date(reported.getTime() - 1), now).reasons).toEqual(["stale"]);
    expect(assess({}, new Date(reported.getTime() - 1), null).level).toBe("ok");
  });
});
