import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { expect, it } from "vitest";
import {
  BADGE_TEXT,
  STATUS_TEXT,
  UPDATED_TEXT,
  bookingLabel,
} from "../content/renderers/room-booking-text";

it("keeps Android string resources aligned with server room labels", () => {
  for (const locale of ["en", "de", "fr", "it", "es"]) {
    const texts = {
      state_free: BADGE_TEXT[locale]!.free,
      state_busy: BADGE_TEXT[locale]!.busy,
      updated: UPDATED_TEXT[locale]!,
      book: bookingLabel(locale),
      ...STATUS_TEXT[locale]!,
    };
    const dir = `android/app/src/main/res/values${locale === "en" ? "" : `-${locale}`}`;
    if (process.env.UPDATE_ANDROID_WORDS === "1") {
      mkdirSync(dir, { recursive: true });
      const strings = Object.entries(texts)
        .map(
          ([key, value]) =>
            `    <string name="${key}">${JSON.stringify(value).replaceAll("&", "&amp;").replaceAll("<", "&lt;")}</string>`
        )
        .join("\n");
      writeFileSync(`${dir}/strings.xml`, `<resources>\n${strings}\n</resources>\n`);
    }
    const xml = readFileSync(`${dir}/strings.xml`, "utf8");
    for (const [key, value] of Object.entries(texts)) {
      expect(xml).toContain(
        `<string name="${key}">${JSON.stringify(value).replaceAll("&", "&amp;").replaceAll("<", "&lt;")}</string>`
      );
    }
  }
});
