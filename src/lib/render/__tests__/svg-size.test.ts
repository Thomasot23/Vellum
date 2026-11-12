// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 Fabian Schmieder. All rights reserved.
import { describe, it, expect } from "vitest";
import { loadImage } from "@napi-rs/canvas";
import { withIntrinsicSize } from "../svg-size";

const viewBoxOnly = (w: number, h: number) =>
  Buffer.from(
    `<?xml version="1.0"?>\n<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${w} ${h}"><rect width="${w}" height="${h}"/></svg>`
  );

describe("withIntrinsicSize", () => {
  it("gives a viewBox-only SVG the proportions of its viewBox", async () => {
    const img = await loadImage(withIntrinsicSize(viewBoxOnly(85.04, 58.11)));
    expect(img.width / img.height).toBeCloseTo(85.04 / 58.11, 2);
    expect(Math.max(img.width, img.height)).toBe(1200);
  });

  it("keeps a tall mark tall", async () => {
    const img = await loadImage(withIntrinsicSize(viewBoxOnly(141.73, 283.46), 600));
    expect(img.height).toBe(600);
    expect(img.width).toBe(300);
  });

  it("leaves an SVG that already has a size alone", () => {
    const buf = Buffer.from(
      '<svg xmlns="http://www.w3.org/2000/svg" width="10" height="20" viewBox="0 0 1 2"/>'
    );
    expect(withIntrinsicSize(buf)).toBe(buf);
  });

  it("leaves anything that is not an SVG alone", () => {
    const png = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
    expect(withIntrinsicSize(png)).toBe(png);
  });

  it("leaves an SVG without a usable viewBox alone", () => {
    const buf = Buffer.from('<svg xmlns="http://www.w3.org/2000/svg"><rect/></svg>');
    expect(withIntrinsicSize(buf)).toBe(buf);
  });
});
