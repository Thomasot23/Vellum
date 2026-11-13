// SPDX-License-Identifier: AGPL-3.0-or-later
/**
 * The per-device secret a native panel needs to drive its status light.
 *
 * Some wall panels expose their LEDs through a vendor service on the device that
 * wants a token. That token is an operator-managed secret: it is entered once in
 * the console, never shown again, and handed only to the enrolled device it
 * belongs to.
 *
 * Stored in the settings key-value table, one entry per device, so that this adds
 * no schema migration. The price is that nothing in the database ties the entry
 * to its device: `deleteDevice` removes it explicitly.
 */

import { createHash } from "node:crypto";
import { eq } from "drizzle-orm";
import { z } from "zod";
import { db, withDbRead, type DbTransaction } from "@/db";
import { devices, settings } from "@/db/schema";
import { decryptCredentials, encryptCredentials } from "@/lib/encryption";

/** Tells a panel that the server knows of no secret for it. */
export const STATUS_LIGHT_NONE = "none";
export const STATUS_LIGHT_REVISION_HEADER = "X-Status-Light-Revision";

const KEY_PREFIX = "device.statusLight.";

/** Printable, without whitespace: it travels in an HTTP header on the panel. */
export const statusLightTokenSchema = z
  .string()
  .min(1)
  .max(512)
  .regex(/^[\x21-\x7e]+$/);

const recordSchema = z.object({
  encrypted: z.string().min(1),
  revision: z.string().regex(/^[0-9a-f]{16}$/),
});

export type StatusLightRecord = z.infer<typeof recordSchema>;

export function statusLightKey(mac: string): string {
  return `${KEY_PREFIX}${mac}`;
}

/**
 * Encrypt a token for storage. The revision identifies this stored value without
 * revealing anything about the token: it is derived from the ciphertext, whose
 * nonce is random, so re-entering the same token yields a new revision.
 */
export function sealStatusLightToken(token: string): StatusLightRecord {
  const encrypted = encryptCredentials({ token: statusLightTokenSchema.parse(token) });
  const revision = createHash("sha256").update(encrypted).digest("hex").slice(0, 16);
  return { encrypted, revision };
}

export function openStatusLightToken(record: StatusLightRecord): string {
  const opened = z
    .object({ token: statusLightTokenSchema })
    .parse(decryptCredentials(record.encrypted));
  return opened.token;
}

/** The stored record, or null when the device has none or the entry is unusable. */
export async function readStatusLightRecord(mac: string): Promise<StatusLightRecord | null> {
  const [row] = await withDbRead(
    () =>
      db
        .select({ value: settings.value })
        .from(settings)
        .where(eq(settings.key, statusLightKey(mac)))
        .limit(1),
    "status-light-read"
  );
  const parsed = recordSchema.safeParse(row?.value);
  return parsed.success ? parsed.data : null;
}

export async function writeStatusLightRecord(
  tx: DbTransaction,
  mac: string,
  record: StatusLightRecord
): Promise<void> {
  const now = new Date();
  await tx
    .insert(settings)
    .values({ key: statusLightKey(mac), value: record, updatedAt: now })
    .onConflictDoUpdate({ target: settings.key, set: { value: record, updatedAt: now } });
}

export async function deleteStatusLightRecord(tx: DbTransaction, mac: string): Promise<void> {
  await tx.delete(settings).where(eq(settings.key, statusLightKey(mac)));
}

/**
 * Store or remove a device's secret inside the caller's audited transaction.
 * Refuses unknown devices, so no entry can outlive or precede its device.
 */
export async function replaceStatusLightToken(
  tx: DbTransaction,
  mac: string,
  record: StatusLightRecord | null
): Promise<void> {
  const existing = await tx
    .select({ mac: devices.mac })
    .from(devices)
    .where(eq(devices.mac, mac))
    .limit(1);
  if (existing.length === 0) throw new Error("device_not_found");
  if (record) await writeStatusLightRecord(tx, mac, record);
  else await deleteStatusLightRecord(tx, mac);
}
