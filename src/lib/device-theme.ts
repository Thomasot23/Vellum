// SPDX-License-Identifier: AGPL-3.0-or-later
/**
 * The theme a device is drawn with: its assigned theme or the database default,
 * with the built-in theme used if that selection is unavailable or invalid.
 * The selected theme is then fitted to the panel.
 *
 * The same resolution `/render` performs inline, for the routes that hand a
 * display data instead of pixels.
 */

import { eq } from "drizzle-orm";
import { db, withDbRead } from "@/db";
import { themes } from "@/db/schema";
import type { ResolvedDisplay } from "@/lib/display";
import { parseTheme, resolveTheme, themeForDisplay, type Theme } from "@/lib/theme";

export async function resolveDeviceTheme(
  themeId: string | null | undefined,
  display: ResolvedDisplay
): Promise<Theme> {
  const [row] = await withDbRead(
    () =>
      themeId
        ? db.select().from(themes).where(eq(themes.id, themeId)).limit(1)
        : db.select().from(themes).where(eq(themes.isDefault, true)).limit(1),
    "device-theme-resolve"
  );
  const theme = parseTheme(row?.config) ?? resolveTheme(display.colorCount);
  return themeForDisplay(theme, display);
}
