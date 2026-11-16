// SPDX-License-Identifier: AGPL-3.0-or-later
/**
 * Approval bound to the key the operator compared.
 *
 * Before approval, `/hello` lets anyone who knows a device ID register a key
 * under it; that is how the device's own key gets captured. The token issued on
 * approval is only ever delivered sealed to the key registered at that moment, so
 * the operator must approve that key, not just the ID: the console shows its
 * fingerprint, the panel shows the same fingerprint, and the approval carries the
 * fingerprint the operator saw. If the key changed in between, nothing is approved.
 *
 * Devices whose firmware never registered a key (legacy) can still be approved
 * without one; the console says so.
 */

import { eq } from "drizzle-orm";
import type { DbTransaction } from "@/db";
import { devices } from "@/db/schema";
import { keyFingerprint } from "@/lib/key-fingerprint";

export class KeyChangedError extends Error {
  constructor() {
    super("key_changed");
  }
}

/** Approves inside the caller's (serializable, audited) transaction. */
export async function approveDeviceRecord(
  tx: DbTransaction,
  mac: string,
  expectedFingerprint: string | null,
  token: string,
  now: Date
): Promise<{ fingerprint: string | null }> {
  const [device] = await tx
    .select({ publicKey: devices.publicKey })
    .from(devices)
    .where(eq(devices.mac, mac))
    .limit(1);
  if (!device) throw new Error("device_not_found");
  const fingerprint = keyFingerprint(device.publicKey);
  if ((device.publicKey !== null && fingerprint === null) || fingerprint !== expectedFingerprint)
    throw new KeyChangedError();
  await tx
    .update(devices)
    .set({ status: "approved", token, approvedAt: now })
    .where(eq(devices.mac, mac));
  return { fingerprint };
}
