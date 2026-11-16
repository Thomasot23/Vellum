// SPDX-License-Identifier: AGPL-3.0-or-later
/** Shape of a pending device's key history; free of server imports for the console. */

import { z } from "zod";

export const keyHistorySchema = z.object({
  firstFingerprint: z.string().nullable(),
  changes: z.number().int().min(1),
  lastChangedAt: z.iso.datetime(),
});

export type KeyHistory = z.infer<typeof keyHistorySchema>;

export function parseKeyHistory(value: unknown): KeyHistory | null {
  const parsed = keyHistorySchema.safeParse(value);
  return parsed.success ? parsed.data : null;
}
