// SPDX-License-Identifier: AGPL-3.0-or-later
/**
 * The shape of a native panel's platform report. Kept free of server imports so
 * the console can parse a stored snapshot in the browser too.
 */

import { z } from "zod";

const label = (max: number) =>
  z
    .string()
    .min(1)
    .max(max)
    .regex(/^[\x20-\x7e]+$/);

export const panelInfoSchema = z.object({
  appVersion: label(32),
  appVersionCode: z.number().int().min(1).max(2_100_000_000),
  androidRelease: label(16),
  sdkInt: z.number().int().min(21).max(99),
  securityPatch: z.iso.date().nullable(),
  manufacturer: label(64),
  model: label(64),
  buildType: z.string().regex(/^[a-z]{1,16}$/),
  buildTags: z.string().regex(/^[a-z0-9,_-]{0,64}$/),
  deviceOwner: z.boolean(),
  lockTask: z.boolean(),
  adbEnabled: z.boolean(),
  developerOptions: z.boolean(),
  networkType: z.enum(["ethernet", "wifi", "other", "none"]),
});

export type PanelInfo = z.infer<typeof panelInfoSchema>;

const storedPanelInfoSchema = z.object({ info: panelInfoSchema, receivedAt: z.iso.datetime() });

/** A stored snapshot as the database returns it, or null when it is unusable. */
export function parseStoredPanelInfo(value: unknown): { info: PanelInfo; receivedAt: Date } | null {
  const parsed = storedPanelInfoSchema.safeParse(value);
  return parsed.success
    ? { info: parsed.data.info, receivedAt: new Date(parsed.data.receivedAt) }
    : null;
}
