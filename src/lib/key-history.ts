// SPDX-License-Identifier: AGPL-3.0-or-later
/**
 * Remembers that a pending device's key changed before approval.
 *
 * `/hello` must let a device replace its key until it is approved (a reset device
 * needs that), but a change is also exactly what an impersonator causes. The
 * console shows it, so an operator approving a device whose key moved knows to
 * compare the fingerprint with particular care. Best effort and append-only in
 * spirit: it never blocks enrollment and never reveals anything to the caller.
 *
 * Kept in the settings key-value table, one entry per device, cleared on approval
 * and deletion; no schema migration.
 */

import { eq } from "drizzle-orm";
import { db, withDbTransaction, type DbTransaction } from "@/db";
import { devices, settings } from "@/db/schema";
import { keyFingerprint } from "@/lib/key-fingerprint";
import { parseKeyHistory, type KeyHistory } from "@/lib/key-history-schema";

export function keyHistoryKey(mac: string): string {
  return `device.keyHistory.${mac}`;
}

export async function recordKeyChange(
  mac: string,
  previousKey: string | null,
  now: Date
): Promise<void> {
  await withDbTransaction(
    () =>
      db.transaction(async (tx) => {
        // Serialize with approval/deletion so late evidence cannot recreate cleanup.
        const [device] = await tx
          .select({ status: devices.status })
          .from(devices)
          .where(eq(devices.mac, mac))
          .limit(1)
          .for("update");
        if (device?.status !== "pending") return;
        const [row] = await tx
          .select({ value: settings.value })
          .from(settings)
          .where(eq(settings.key, keyHistoryKey(mac)))
          .limit(1);
        const existing = parseKeyHistory(row?.value);
        const value: KeyHistory = {
          firstFingerprint: existing ? existing.firstFingerprint : keyFingerprint(previousKey),
          changes: (existing?.changes ?? 0) + 1,
          lastChangedAt: now.toISOString(),
        };
        await tx
          .insert(settings)
          .values({ key: keyHistoryKey(mac), value, updatedAt: now })
          .onConflictDoUpdate({ target: settings.key, set: { value, updatedAt: now } });
      }),
    "key-history-record"
  );
}

export async function deleteKeyHistory(tx: DbTransaction, mac: string): Promise<void> {
  await tx.delete(settings).where(eq(settings.key, keyHistoryKey(mac)));
}
