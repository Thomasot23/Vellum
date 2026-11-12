// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 Fabian Schmieder. All rights reserved.
import { describe, it, expect } from "vitest";
import { themeForDisplay, type Theme } from "../theme";
import { DISPLAY_REGISTRY } from "../display";

const BRAND: Theme = {
  name: "Brand",
  headerBg: "#575250",
  headerText: "#FFFFFF",
  freeBadge: "#FFD200",
  busyBadge: "#575250",
  badgeText: "#FFFFFF",
  background: "#FFFFFF",
  eventBg: "#575250",
  slotText: "#FFFFFF",
  slotSecondary: "#575250",
  footerText: "#000000",
};

describe("themeForDisplay", () => {
  it("keeps a full-colour display's theme exactly as written", () => {
    const d1001 = DISPLAY_REGISTRY.d1001;
    expect(themeForDisplay(BRAND, d1001)).toEqual(BRAND);
  });

  it("still snaps a panel with fixed inks to what it can print", () => {
    const e1002 = DISPLAY_REGISTRY.e1002;
    const snapped = themeForDisplay(BRAND, e1002);
    expect(snapped.freeBadge).toBe("#FFFF00");
    expect(snapped.busyBadge).toBe("#000000");
  });

  it("still snaps a greyscale panel by lightness", () => {
    const e1003 = DISPLAY_REGISTRY.e1003;
    const snapped = themeForDisplay(BRAND, e1003);
    expect(snapped.freeBadge).toBe("#DDDDDD");
    expect(snapped.busyBadge).toBe("#555555");
  });
});
