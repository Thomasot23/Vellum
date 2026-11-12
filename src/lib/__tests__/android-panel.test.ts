import { helloRequestSchema } from "../validation";
import { describe, expect, it } from "vitest";
import { completeDisplayCaps, resolveDisplayCaps } from "../display";
import { resolveTheme, themeForDisplay } from "../theme";
describe("Android panel capabilities", () => {
  it("accepts a registry-backed model-only hello", () => {
    const hello = helloRequestSchema.parse({
      mac: "AABBCCDDEEFF",
      display: { model: "android-panel" },
    });
    expect(hello.display).toMatchObject({ width: 1280, height: 800, colorMode: "fullcolor" });
  });
  it("completes model-only enrollment and preserves full-color theme values", () => {
    const caps = completeDisplayCaps({ model: "android-panel" }, "android-panel");
    expect(caps).toMatchObject({
      width: 1280,
      height: 800,
      colorMode: "fullcolor",
      orientations: ["landscape", "portrait"],
    });
    const display = resolveDisplayCaps(caps);
    const theme = resolveTheme(7);
    expect(themeForDisplay(theme, display)).toEqual(theme);
    expect(resolveDisplayCaps(caps, "portrait")).toMatchObject({ width: 800, height: 1280 });
  });
});
