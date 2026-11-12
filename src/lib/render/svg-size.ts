// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 Fabian Schmieder. All rights reserved.
/**
 * Give an SVG that only has a `viewBox` an intrinsic size.
 *
 * Logo files exported from design tools commonly carry `viewBox` and nothing else.
 * Decoded without `width` and `height`, such a file has no proportions the canvas
 * can read, so a renderer that scales it by its aspect ratio guesses, and a wide
 * wordmark comes out squeezed. Adding the two attributes from the `viewBox` fixes
 * the proportions without touching a single path.
 *
 * The size is set so the longer side is `longSide` pixels: large enough that the
 * rasterised logo stays crisp when it is scaled down onto a 10-inch panel.
 *
 * Anything that is not such an SVG is returned unchanged, byte for byte.
 */

const ROOT = /<svg\b[^>]*>/i;

export function withIntrinsicSize(buf: Buffer, longSide = 1200): Buffer {
  const head = buf.subarray(0, 4096).toString("utf8");
  const match = ROOT.exec(head);
  if (!match) return buf;
  const tag = match[0];
  if (/\swidth\s*=/.test(tag) && /\sheight\s*=/.test(tag)) return buf;
  const vb =
    /\sviewBox\s*=\s*["']\s*([-\d.eE]+)[\s,]+([-\d.eE]+)[\s,]+([\d.eE]+)[\s,]+([\d.eE]+)\s*["']/i.exec(
      tag
    );
  if (!vb) return buf;
  const w = Number(vb[3]);
  const h = Number(vb[4]);
  if (!(w > 0 && h > 0)) return buf;
  const scale = longSide / Math.max(w, h);
  const sized = tag
    .replace(/\s(width|height)\s*=\s*["'][^"']*["']/gi, "")
    .replace(/^<svg\b/i, `<svg width="${Math.round(w * scale)}" height="${Math.round(h * scale)}"`);
  const text = buf.toString("utf8");
  return Buffer.from(text.replace(tag, sized), "utf8");
}
