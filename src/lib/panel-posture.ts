// SPDX-License-Identifier: AGPL-3.0-or-later
/**
 * How far an operator can trust a native panel's platform, from what it reports.
 *
 * The counterpart of `security-posture.ts` for e-paper firmware. It judges only
 * what the panel says about itself, so it can reveal a weak setup but never prove
 * a strong one: a compromised panel can report anything. Pure, so the console can
 * evaluate it in the browser as well.
 */

import type { PanelInfo } from "@/lib/panel-info-schema";

export type PanelPostureLevel = "ok" | "warning" | "critical";

export type PanelPostureReason =
  | "notDeviceOwner"
  | "testKeys"
  | "nonUserBuild"
  | "adbEnabled"
  | "developerOptions"
  | "lockTaskOff"
  | "patchUnknown"
  | "patchOld"
  | "androidOld"
  | "stale";

export interface PanelPosture {
  level: PanelPostureLevel;
  reasons: PanelPostureReason[];
}

/** Older than this, a security patch level is a warning. */
export const PATCH_MAX_AGE_DAYS = 180;
/** Android 13. The app runs from Android 11, but without a warning only from here. */
export const MIN_QUIET_SDK = 33;
/** A panel that polls but has not reported for this long hides its current state. */
export const STALE_AFTER_MS = 60 * 60 * 1000;

const CRITICAL: ReadonlySet<PanelPostureReason> = new Set([
  "notDeviceOwner",
  "testKeys",
  "nonUserBuild",
]);

export function assessPanelPosture(
  info: PanelInfo,
  receivedAt: Date,
  lastSeen: Date | null,
  now: Date
): PanelPosture {
  const reasons: PanelPostureReason[] = [];
  if (!info.deviceOwner) reasons.push("notDeviceOwner");
  if (info.buildTags.split(",").includes("test-keys")) reasons.push("testKeys");
  if (info.buildType !== "user") reasons.push("nonUserBuild");
  if (info.adbEnabled) reasons.push("adbEnabled");
  if (info.developerOptions) reasons.push("developerOptions");
  if (!info.lockTask) reasons.push("lockTaskOff");
  if (info.securityPatch === null) {
    reasons.push("patchUnknown");
  } else {
    // Whole calendar days: a patch level names a date, not an instant.
    const ageDays = Math.floor(
      (now.getTime() - new Date(`${info.securityPatch}T00:00:00Z`).getTime()) / 86_400_000
    );
    if (ageDays > PATCH_MAX_AGE_DAYS) reasons.push("patchOld");
  }
  if (info.sdkInt < MIN_QUIET_SDK) reasons.push("androidOld");
  if (lastSeen && lastSeen.getTime() - receivedAt.getTime() > STALE_AFTER_MS) {
    reasons.push("stale");
  }
  const level: PanelPostureLevel = reasons.some((r) => CRITICAL.has(r))
    ? "critical"
    : reasons.length > 0
      ? "warning"
      : "ok";
  return { level, reasons };
}
