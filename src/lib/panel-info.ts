// SPDX-License-Identifier: AGPL-3.0-or-later
/**
 * What a native panel reports about its own platform.
 *
 * E-paper displays describe themselves through firmware headers; an Android panel
 * runs an app on a vendor's Android build, so the facts that matter are different:
 * which app version, which Android release and patch level, and whether the panel
 * is locked down (device owner, lock task, debugging off). The panel sends them as
 * one header on its authenticated room poll; only the latest snapshot is kept.
 *
 * Stored in the settings key-value table, one entry per device, so this adds no
 * schema migration. Nothing in the database ties the entry to its device:
 * `deleteDevice` removes it explicitly.
 *
 * Deliberately no hardware identifiers, network names or addresses: the panel is
 * already identified by its Vellum device ID.
 */

import { eq } from "drizzle-orm";
import { db, withDbRead, withDbWrite, type DbTransaction } from "@/db";
import { settings } from "@/db/schema";
import { panelInfoSchema, parseStoredPanelInfo, type PanelInfo } from "@/lib/panel-info-schema";

export { panelInfoSchema, parseStoredPanelInfo, type PanelInfo };
export const PANEL_INFO_HEADER = "X-Panel-Info";

/** 2 KiB of JSON, base64url-encoded. */
const MAX_HEADER_LENGTH = 2_800;
const KEY_PREFIX = "device.panelInfo.";

export function panelInfoKey(mac: string): string {
  return `${KEY_PREFIX}${mac}`;
}

/** The header's content, or null when it is absent or anything about it is wrong. */
export function parsePanelInfo(header: string | null): PanelInfo | null {
  if (!header || header.length > MAX_HEADER_LENGTH || !/^[A-Za-z0-9_-]+$/.test(header)) {
    return null;
  }
  try {
    const json: unknown = JSON.parse(Buffer.from(header, "base64url").toString("utf-8"));
    const parsed = panelInfoSchema.safeParse(json);
    return parsed.success ? parsed.data : null;
  } catch {
    return null;
  }
}

export async function writePanelInfo(mac: string, info: PanelInfo, now: Date): Promise<void> {
  const value = { info, receivedAt: now.toISOString() };
  await withDbWrite(
    () =>
      db
        .insert(settings)
        .values({ key: panelInfoKey(mac), value, updatedAt: now })
        .onConflictDoUpdate({ target: settings.key, set: { value, updatedAt: now } }),
    "panel-info-write"
  );
}

export async function readPanelInfo(
  mac: string
): Promise<{ info: PanelInfo; receivedAt: Date } | null> {
  const [row] = await withDbRead(
    () =>
      db
        .select({ value: settings.value })
        .from(settings)
        .where(eq(settings.key, panelInfoKey(mac)))
        .limit(1),
    "panel-info-read"
  );
  return parseStoredPanelInfo(row?.value);
}

export async function deletePanelInfo(tx: DbTransaction, mac: string): Promise<void> {
  await tx.delete(settings).where(eq(settings.key, panelInfoKey(mac)));
}
