// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 Fabian Schmieder. All rights reserved.
/**
 * Two status-first layouts for the room-booking renderer.
 *
 *  - `status-panel`: a solid panel carries the state from across a corridor,
 *    the day's list sits beside it (below it in portrait).
 *  - `day-band`:     the state is a block under the room name, and a band at
 *    the bottom shows the whole day, so free stretches can be seen at a glance.
 *
 * Both are built for panels without touch: nothing here is a control. The
 * booking QR code is the way in, and it follows the instance's QR setting.
 *
 * Branding is DATA, never code. An operator may set a title and subtitle (the
 * wordmark, set in the render face) and point `brandMarkAssetId` at an uploaded
 * SVG or PNG. The mark is drawn as a silhouette, tinted with a theme colour, so
 * one upload serves every panel and every state, and a greyscale panel gets a
 * grey the palette can print rather than a dithered photograph of a colour.
 *
 * Colour comes only from the theme. Both layouts read the same ten tokens as the
 * timeline: `freeBadge` and `busyBadge` are the two state grounds, `eventBg` the
 * future bookings in the band, `slotSecondary` the quiet text. Text on any filled
 * ground goes through `readableOn`, so a theme cannot make it disappear.
 *
 * Geometry is in units of `u = shortSide / 480`, the same rule as the timeline,
 * so the 800x480 panels and the 1872x1404 one share proportions.
 */

import { createCanvas, type Canvas, type Image, type SKRSContext2D } from "@napi-rs/canvas";
import { format } from "date-fns";
import { TZDate } from "@date-fns/tz";
import QRCode from "qrcode";
import { contrastRatio, readableOn, type Theme } from "@/lib/theme";
import type { DisplayEvent } from "@/lib/types";
import { ensureRenderFonts } from "@/lib/render/fonts";
import { canvasSurface, type SurfaceFactory } from "@/lib/render/surface";
import { shouldShowBookingQr, type BookingQrVisibility } from "./booking-qr";
import {
  DATE_LOCALES,
  STATUS_TEXT,
  UPDATED_TEXT,
  type StatusText,
  bookingLabel,
  stateWord,
} from "./room-booking-text";

/** How close the next booking must be before a free room announces it. */
const SOON_MS = 15 * 60_000;
/** Below this contrast a mark would vanish into its ground and gets the ink instead. */
const MARK_MIN_CONTRAST = 1.25;

export interface StatusFrameSpec {
  events: DisplayEvent[];
  /**
   * The state as the provider reported it, before privacy filtering. "Hide All"
   * leaves `events` empty for a fully booked room, so the list alone cannot say
   * whether the room is taken.
   */
  isRoomFree?: boolean | undefined;
  roomName: string;
  roomSubtitle?: string | undefined;
  brandTitle?: string | undefined;
  brandSubtitle?: string | undefined;
  brandMark?: Image | null | undefined;
  timezone: string;
  now: Date;
  theme: Theme;
  width: number;
  height: number;
  colorMode?: string | undefined;
  locale?: string | undefined;
  dateFormat?: string | undefined;
  bookingQr?: { url: string; visibility: BookingQrVisibility; isRoomFree: boolean } | undefined;
  surface?: SurfaceFactory | undefined;
}

/* ── Shared pieces ───────────────────────────────────────────────── */

interface Pen {
  ctx: SKRSContext2D;
  ff: string;
  u: number;
}

type Weight = "normal" | "500" | "bold";

function font(p: Pen, size: number, weight: Weight): string {
  return `${weight === "normal" ? "" : `${weight} `}${Math.round(size)}px ${p.ff}`;
}

function widthOf(p: Pen, s: string, size: number, weight: Weight): number {
  p.ctx.font = font(p, size, weight);
  return p.ctx.measureText(s).width;
}

/**
 * The largest size between `max` and `min` at which `s` fits `maxW`.
 *
 * Shrinking is the first answer to a long string and condensing the last: canvas
 * condenses silently, and a string condensed past a fifth stops reading from the
 * door while still counting as drawn.
 */
function fitSize(p: Pen, s: string, maxW: number, max: number, min: number, w: Weight): number {
  let size = max;
  while (size > min && widthOf(p, s, size, w) > maxW) size -= Math.max(1, size * 0.04);
  return Math.max(size, min);
}

/** Cut `s` to `maxW` with an ellipsis. For strings nobody is owed in full. */
function clip(p: Pen, s: string, size: number, w: Weight, maxW: number): string {
  if (widthOf(p, s, size, w) <= maxW) return s;
  let cut = s;
  while (cut.length > 1 && widthOf(p, `${cut}…`, size, w) > maxW) cut = cut.slice(0, -1);
  return `${cut.trimEnd()}…`;
}

function say(
  p: Pen,
  s: string,
  x: number,
  y: number,
  size: number,
  w: Weight,
  color: string,
  align: "left" | "right" | "center" = "left",
  maxW?: number
): void {
  p.ctx.font = font(p, size, w);
  p.ctx.fillStyle = color;
  p.ctx.textAlign = align;
  if (maxW === undefined) p.ctx.fillText(s, x, y);
  else p.ctx.fillText(s, x, y, maxW);
  p.ctx.textAlign = "left";
}

function clock(d: Date, tz: string): string {
  return format(new TZDate(d, tz), "H:mm");
}

interface Moment {
  current: DisplayEvent | null;
  next: DisplayEvent | null;
  soon: boolean;
  sorted: DisplayEvent[];
}

function moment(events: DisplayEvent[], now: Date): Moment {
  const t = now.getTime();
  const sorted = [...events].sort((a, b) => a.startTime.getTime() - b.startTime.getTime());
  const current = sorted.find((e) => e.startTime.getTime() <= t && e.endTime.getTime() > t) ?? null;
  const next = sorted.find((e) => e.startTime.getTime() > t) ?? null;
  const soon = !current && next !== null && next.startTime.getTime() - t <= SOON_MS;
  return { current, next, soon, sorted };
}

/** The mark as a silhouette in one colour, at the size asked for. */
function tinted(mark: Image, color: string, w: number, h: number): Canvas {
  /* A scratch canvas, deliberately NOT from the surface factory: it is an
   * intermediate bitmap, not a frame, and a recording surface must see exactly
   * one frame per render. */
  const c = createCanvas(Math.max(1, Math.round(w)), Math.max(1, Math.round(h)));
  const x = c.getContext("2d");
  x.drawImage(mark, 0, 0, c.width, c.height);
  x.globalCompositeOperation = "source-in";
  x.fillStyle = color;
  x.fillRect(0, 0, c.width, c.height);
  return c;
}

function markAspect(mark: Image): number {
  return mark.height > 0 ? mark.width / mark.height : 0.5;
}

function markColor(preferred: string, ground: string, fallback: string): string {
  return contrastRatio(preferred, ground) >= MARK_MIN_CONTRAST ? preferred : fallback;
}

/** Wordmark: title lines in bold, subtitle lines regular. Returns the bottom edge. */
function drawBrand(p: Pen, spec: StatusFrameSpec, x: number, top: number, color: string): number {
  const titles = (spec.brandTitle ?? "").split("\n").filter((l) => l.trim());
  const subs = (spec.brandSubtitle ?? "").split("\n").filter((l) => l.trim());
  if (titles.length === 0 && subs.length === 0) return top;
  const u = p.u;
  let y = top;
  for (const line of titles) {
    y += 10.5 * u;
    say(p, line.toLocaleUpperCase(), x, y, 9 * u, "bold", color);
  }
  if (subs.length) y += 3 * u;
  for (const line of subs) {
    y += 8 * u;
    say(p, line.toLocaleUpperCase(), x, y, 6.6 * u, "normal", color);
  }
  return y + 2 * u;
}

/** Date and "updated" stamp, right-aligned. Returns the block's width. */
function drawStamp(p: Pen, spec: StatusFrameSpec, right: number, top: number): number {
  const locale = spec.locale ?? "en";
  const T = spec.theme;
  const u = p.u;
  const dateStr = format(new TZDate(spec.now, spec.timezone), spec.dateFormat ?? "PPP", {
    locale: DATE_LOCALES[locale] ?? DATE_LOCALES.en,
  });
  const text = STATUS_TEXT[locale] ?? STATUS_TEXT.en;
  const stamp = `${UPDATED_TEXT[locale] ?? UPDATED_TEXT.en} ${clock(spec.now, spec.timezone)}${text.clockSuffix}`;
  say(p, dateStr, right, top + 10 * u, 10 * u, "500", T.footerText, "right");
  say(p, stamp, right, top + 23 * u, 9 * u, "normal", T.slotSecondary, "right");
  return Math.max(widthOf(p, dateStr, 10 * u, "500"), widthOf(p, stamp, 9 * u, "normal"));
}

/** The QR code with its label beside it. Returns nothing; the caller reserved the room. */
function drawQr(
  p: Pen,
  spec: StatusFrameSpec,
  x: number,
  y: number,
  size: number,
  labelBeside: boolean
): void {
  const qr = spec.bookingQr;
  if (!qr) return;
  const T = spec.theme;
  const u = p.u;
  const code = QRCode.create(qr.url, { errorCorrectionLevel: "M" });
  const quiet = 2;
  const modules = code.modules.size + quiet * 2;
  const cell = Math.max(1, Math.floor(size / modules));
  const side = cell * modules;
  p.ctx.fillStyle = "#FFFFFF";
  p.ctx.fillRect(x, y, side, side);
  p.ctx.fillStyle = "#000000";
  for (let r = 0; r < code.modules.size; r++)
    for (let c = 0; c < code.modules.size; c++)
      if (code.modules.get(r, c))
        p.ctx.fillRect(x + (c + quiet) * cell, y + (r + quiet) * cell, cell, cell);
  if (!labelBeside) return;
  const locale = spec.locale ?? "en";
  const lx = x + side + 12 * u;
  say(p, bookingLabel(locale), lx, y + side / 2 - 2 * u, 14 * u, "bold", T.footerText);
  say(
    p,
    (STATUS_TEXT[locale] ?? STATUS_TEXT.en).scanHint,
    lx,
    y + side / 2 + 13 * u,
    9.5 * u,
    "normal",
    T.slotSecondary
  );
}

/** "until 11:30", or the end of the list, or nothing when the booking is hidden. */
function stateLine(mo: Moment, busy: boolean, text: StatusText, tz: string): string {
  if (busy)
    return mo.current ? `${text.until} ${clock(mo.current.endTime, tz)}${text.clockSuffix}` : "";
  if (mo.next) return `${text.until} ${clock(mo.next.startTime, tz)}${text.clockSuffix}`;
  return text.noneLeft;
}

/**
 * A thin outline around a state ground that the panel cannot tell from the page.
 *
 * On a two-colour panel the free ground and the background are both white, so the
 * block that carries the state would vanish and leave a word floating. Stroked,
 * not filled, so the outline never counts as the ground under any text.
 */
function outlineIfFaint(
  p: Pen,
  ground: string,
  T: Theme,
  x: number,
  y: number,
  w: number,
  h: number
): void {
  if (contrastRatio(ground, T.background) >= MARK_MIN_CONTRAST) return;
  const lw = Math.max(2, Math.round(1.5 * p.u));
  p.ctx.strokeStyle = readableOn(T.background, T.footerText);
  p.ctx.lineWidth = lw;
  p.ctx.strokeRect(x + lw / 2, y + lw / 2, w - lw, h - lw);
}

function qrVisible(spec: StatusFrameSpec): boolean {
  const qr = spec.bookingQr;
  return !!qr && shouldShowBookingQr(qr.visibility, qr.isRoomFree, qr.url);
}

function openCanvas(spec: StatusFrameSpec): { canvas: Canvas; p: Pen } {
  const { canvas, ctx } = (spec.surface ?? canvasSurface)(spec.width, spec.height);
  ctx.imageSmoothingEnabled = (spec.colorMode ?? "indexed") !== "indexed";
  const u = Math.min(spec.width, spec.height) / 480;
  ctx.fillStyle = spec.theme.background;
  ctx.fillRect(0, 0, spec.width, spec.height);
  return { canvas, p: { ctx, ff: ensureRenderFonts(), u } };
}

/* ── Layout A: status panel ──────────────────────────────────────── */

export function renderStatusPanel(spec: StatusFrameSpec): Canvas {
  const { canvas, p } = openCanvas(spec);
  const { ctx, u } = p;
  const T = spec.theme;
  const W = spec.width;
  const H = spec.height;
  const locale = spec.locale ?? "en";
  const text = STATUS_TEXT[locale] ?? STATUS_TEXT.en;
  const tz = spec.timezone;
  const m = Math.round(27 * u);
  const mo = moment(spec.events, spec.now);
  const busy = spec.isRoomFree === undefined ? mo.current !== null : !spec.isRoomFree;
  const portrait = H > W;

  /* The panel. */
  const panel = portrait
    ? { x: 0, y: 0, w: W, h: Math.round(H * 0.42) }
    : { x: 0, y: 0, w: Math.round(W * 0.4), h: H };
  const ground = busy ? T.busyBadge : T.freeBadge;
  const ink = readableOn(ground, busy ? T.badgeText : T.footerText);
  ctx.fillStyle = ground;
  ctx.fillRect(panel.x, panel.y, panel.w, panel.h);
  outlineIfFaint(p, ground, T, panel.x, panel.y, panel.w, panel.h);

  const px = panel.x + m;
  const pw = panel.w - 2 * m;
  let y = drawBrand(p, spec, px, panel.y + m - 4 * u, ink);
  y = Math.max(y + 22 * u, panel.y + panel.h * (portrait ? 0.16 : 0.22));

  const word = stateWord(locale, busy);
  const wordSize = fitSize(p, word, pw, 68 * u, 28 * u, "bold");
  y += wordSize * 0.78;
  say(p, word, px, y, wordSize, "bold", ink);

  const lineSize = 17 * u;
  const line = stateLine(mo, busy, text, tz);
  if (line) {
    const lineFit = fitSize(p, line, pw, lineSize, 10 * u, "normal");
    /* Clear the descenders of the state word ("g" in "Belegt") before the line. */
    y += wordSize * 0.24 + lineFit;
    say(p, line, px, y, lineFit, "normal", ink, "left", pw);
  }

  const featured = busy ? mo.current : mo.soon ? mo.next : null;
  if (featured) {
    if (!busy) {
      y += 12 * u;
      ctx.fillStyle = ink;
      ctx.fillRect(px, y, pw, Math.max(1, Math.round(u)));
      y += 15 * u;
      const head = `${text.next} · ${clock(featured.startTime, tz)}${text.clockSuffix}`;
      say(p, head, px, y, 11 * u, "bold", ink, "left", pw);
      y += 2 * u;
    }
    y += 17 * u;
    const subjSize = 13 * u;
    say(p, clip(p, featured.displaySubject, subjSize, "bold", pw), px, y, subjSize, "bold", ink);
    if (featured.organizer) {
      const orgSize = fitSize(p, featured.organizer, pw, 11 * u, 8 * u, "normal");
      y += 4 * u + orgSize;
      say(p, featured.organizer, px, y, orgSize, "normal", ink, "left", pw);
    }
  }

  /* The mark sits in the panel's free corner, whole, never under the text. */
  if (spec.brandMark) {
    const aspect = markAspect(spec.brandMark);
    const floor = panel.y + panel.h - m;
    let mh = Math.min(panel.h * 0.36, floor - (y + 14 * u));
    let mw = mh * aspect;
    if (mw > pw * 0.6) {
      mw = pw * 0.6;
      mh = mw / aspect;
    }
    if (mh >= panel.h * 0.14) {
      const color = markColor(busy ? T.freeBadge : T.background, ground, ink);
      ctx.drawImage(tinted(spec.brandMark, color, mw, mh), panel.x + panel.w - m - mw, floor - mh);
    }
  }

  /* The content column. */
  const box = portrait
    ? { x: m, y: panel.h + m, r: W - m, b: H - m }
    : { x: panel.w + m, y: m, r: W - m, b: H - m };
  const cw = box.r - box.x;
  const stampW = drawStamp(p, spec, box.r, box.y - 2 * u);
  const nameMax = cw - stampW - 16 * u;
  const nameSize = fitSize(p, spec.roomName, nameMax, 22 * u, 12 * u, "bold");
  let cy = box.y + nameSize * 0.85;
  say(p, spec.roomName, box.x, cy, nameSize, "bold", T.footerText, "left", nameMax);
  if (spec.roomSubtitle) {
    cy += 15 * u;
    say(
      p,
      clip(p, spec.roomSubtitle, 9.5 * u, "normal", cw),
      box.x,
      cy,
      9.5 * u,
      "normal",
      T.slotSecondary
    );
  }

  const showQr = qrVisible(spec);
  const qrSize = Math.max(Math.round(84 * u), 120);
  const listBottom = showQr ? box.b - qrSize - 12 * u : box.b;
  if (showQr) drawQr(p, spec, box.x, box.b - qrSize, qrSize, true);

  cy = Math.max(cy + 22 * u, box.y + (portrait ? 48 : 92) * u);
  say(p, text.today.toLocaleUpperCase(), box.x, cy, 9 * u, "bold", T.slotSecondary);
  cy += 7 * u;

  const rowH = 40 * u;
  const maxRows = Math.max(0, Math.floor((listBottom - cy) / rowH));
  const t = spec.now.getTime();
  const todayKey = format(new TZDate(spec.now, tz), "yyyy-MM-dd");
  const today = mo.sorted.filter(
    (e) => e === mo.current || format(new TZDate(e.startTime, tz), "yyyy-MM-dd") === todayKey
  );
  const ahead = today.filter((e) => e.endTime.getTime() > t);
  const behind = today.filter((e) => e.endTime.getTime() <= t);
  const shown = [
    ...behind.slice(Math.max(0, behind.length - Math.max(0, maxRows - ahead.length))),
    ...ahead,
  ].slice(0, maxRows);

  if (ahead.length === 0 && maxRows > 0) {
    ctx.fillStyle = T.slotSecondary;
    ctx.fillRect(box.x, cy, cw, Math.max(1, Math.round(u)));
    say(p, text.noneLeft, box.x, cy + 22 * u, 12 * u, "normal", T.slotSecondary, "left", cw);
  }

  const timeW = 82 * u;
  for (const e of ahead.length === 0 ? [] : shown) {
    const isNow = e === mo.current;
    const rowGround = isNow ? T.busyBadge : T.background;
    const primary = isNow ? readableOn(rowGround, T.badgeText) : T.footerText;
    const secondary = isNow ? primary : T.slotSecondary;
    if (isNow) {
      ctx.fillStyle = rowGround;
      ctx.fillRect(box.x - 8 * u, cy, cw + 16 * u, rowH);
    } else {
      ctx.fillStyle = T.slotSecondary;
      ctx.fillRect(box.x, cy, cw, Math.max(1, Math.round(u * 0.6)));
    }
    const span = `${clock(e.startTime, tz)} – ${clock(e.endTime, tz)}`;
    const spanSize = fitSize(p, span, timeW - 6 * u, 11 * u, 8 * u, "normal");
    say(p, span, box.x, cy + 17 * u, spanSize, "normal", secondary);
    const tx = box.x + timeW;
    const tw = cw - timeW;
    say(
      p,
      clip(p, e.displaySubject, 12.5 * u, "bold", tw),
      tx,
      cy + 17 * u,
      12.5 * u,
      "bold",
      primary
    );
    if (e.organizer) {
      const orgSize = fitSize(p, e.organizer, tw, 9.5 * u, 7.5 * u, "normal");
      say(p, e.organizer, tx, cy + 31 * u, orgSize, "normal", secondary, "left", tw);
    }
    cy += rowH;
  }

  return canvas;
}

/* ── Layout B: day band ──────────────────────────────────────────── */

function localMinutes(d: Date, tz: string): number {
  const z = new TZDate(d, tz);
  return z.getHours() * 60 + z.getMinutes();
}

export function renderDayBand(spec: StatusFrameSpec): Canvas {
  const { canvas, p } = openCanvas(spec);
  const { ctx, u } = p;
  const T = spec.theme;
  const W = spec.width;
  const H = spec.height;
  const locale = spec.locale ?? "en";
  const text = STATUS_TEXT[locale] ?? STATUS_TEXT.en;
  const tz = spec.timezone;
  const m = Math.round(27 * u);
  const mo = moment(spec.events, spec.now);
  const busy = spec.isRoomFree === undefined ? mo.current !== null : !spec.isRoomFree;

  const bandH = Math.round(58 * u);
  const labelH = Math.round(15 * u);
  const bandTop = H - m - labelH - bandH;

  /* The mark: two thirds of the height where it fits, always whole. */
  let contentR = W - m;
  if (spec.brandMark) {
    const aspect = markAspect(spec.brandMark);
    let mh = Math.min((H * 2) / 3, bandTop - m - 18 * u);
    let mw = mh * aspect;
    if (mw > W * 0.3) {
      mw = W * 0.3;
      mh = mw / aspect;
    }
    if (mh >= H * 0.2) {
      const color = markColor(T.freeBadge, T.background, T.footerText);
      const mx = W - m - mw;
      ctx.drawImage(tinted(spec.brandMark, color, mw, mh), mx, m);
      contentR = mx - 24 * u;
    }
  }
  const cw = contentR - m;

  const brandBottom = drawBrand(p, spec, m, m - 4 * u, T.footerText);
  const stampW = drawStamp(p, spec, contentR, m - 2 * u);

  let y = Math.max(brandBottom + 30 * u, m + 70 * u);
  const nameMax = brandBottom > m ? cw : cw - stampW - 16 * u;
  const nameSize = fitSize(p, spec.roomName, nameMax, 22 * u, 12 * u, "bold");
  say(p, spec.roomName, m, y, nameSize, "bold", T.footerText, "left", nameMax);
  if (spec.roomSubtitle) {
    y += 14 * u;
    say(
      p,
      clip(p, spec.roomSubtitle, 9.5 * u, "normal", cw),
      m,
      y,
      9.5 * u,
      "normal",
      T.slotSecondary
    );
  }

  /* QR beside the state, on the right of the content column. */
  const showQr = qrVisible(spec);
  const chipTop = y + 14 * u;
  const qrSize = showQr
    ? Math.min(Math.max(Math.round(84 * u), 120), Math.round(bandTop - 16 * u - chipTop))
    : 0;
  const textR = showQr ? contentR - qrSize - 16 * u : contentR;
  if (showQr && qrSize >= 80) drawQr(p, spec, contentR - qrSize, chipTop, qrSize, false);

  /* The state block. */
  const word = stateWord(locale, busy);
  const chipGround = busy ? T.busyBadge : T.freeBadge;
  const chipInk = readableOn(chipGround, busy ? T.badgeText : T.footerText);
  const wordSize = fitSize(p, word, textR - m - 24 * u, 34 * u, 18 * u, "bold");
  const chipH = Math.round(wordSize * 1.4);
  const chipW = widthOf(p, word, wordSize, "bold") + 24 * u;
  ctx.fillStyle = chipGround;
  ctx.fillRect(m, chipTop, chipW, chipH);
  outlineIfFaint(p, chipGround, T, m, chipTop, chipW, chipH);
  say(p, word, m + 12 * u, chipTop + chipH / 2 + wordSize * 0.36, wordSize, "bold", chipInk);

  const line = stateLine(mo, busy, text, tz);
  const lineSize = 16 * u;
  const besideX = m + chipW + 14 * u;
  let sy = chipTop + chipH;
  if (!line) {
    /* Busy, but the policy hides the booking: the block says all there is. */
  } else if (widthOf(p, line, lineSize, "normal") <= textR - besideX) {
    say(p, line, besideX, chipTop + chipH / 2 + lineSize * 0.36, lineSize, "normal", T.footerText);
  } else {
    sy += 6 * u + lineSize;
    say(
      p,
      line,
      m,
      sy,
      fitSize(p, line, textR - m, lineSize, 10 * u, "normal"),
      "normal",
      T.footerText,
      "left",
      textR - m
    );
  }

  const featured = busy ? mo.current : mo.next;
  if (featured) {
    const tw = textR - m;
    const head = busy
      ? featured.displaySubject
      : `${text.next} · ${clock(featured.startTime, tz)}${text.clockSuffix}: ${featured.displaySubject}`;
    sy += 22 * u;
    say(p, clip(p, head, 13 * u, "bold", tw), m, sy, 13 * u, "bold", T.footerText);
    if (featured.organizer) {
      const orgSize = fitSize(p, featured.organizer, tw, 11 * u, 8 * u, "normal");
      sy += 5 * u + orgSize;
      say(p, featured.organizer, m, sy, orgSize, "normal", T.slotSecondary, "left", tw);
    }
  }

  /* The band. */
  const nowMin = localMinutes(spec.now, tz);
  const todayKey = format(new TZDate(spec.now, tz), "yyyy-MM-dd");
  const dayEvents = mo.sorted.filter(
    (e) =>
      format(new TZDate(e.startTime, tz), "yyyy-MM-dd") === todayKey ||
      format(new TZDate(e.endTime, tz), "yyyy-MM-dd") === todayKey
  );
  const span = (e: DisplayEvent) => {
    const sameStart = format(new TZDate(e.startTime, tz), "yyyy-MM-dd") === todayKey;
    const sameEnd = format(new TZDate(e.endTime, tz), "yyyy-MM-dd") === todayKey;
    return [
      sameStart ? localMinutes(e.startTime, tz) : 0,
      sameEnd ? localMinutes(e.endTime, tz) : 24 * 60,
    ] as const;
  };
  let from = 7 * 60;
  let to = 20 * 60;
  for (const e of dayEvents) {
    const [a, b] = span(e);
    from = Math.min(from, Math.floor(a / 60) * 60);
    to = Math.max(to, Math.ceil(b / 60) * 60);
  }
  if (nowMin < from) from = Math.floor(nowMin / 60) * 60;
  if (nowMin > to) to = Math.min(24 * 60, Math.ceil(nowMin / 60) * 60);
  const x0 = m;
  const bw = W - 2 * m;
  const xOf = (min: number) => x0 + ((min - from) / Math.max(60, to - from)) * bw;

  const hair = Math.max(1, Math.round(u * 0.6));
  ctx.fillStyle = T.slotSecondary;
  ctx.fillRect(x0, bandTop, bw, hair);
  ctx.fillRect(x0, bandTop + bandH - hair, bw, hair);

  /* Lanes for overlapping bookings. */
  const lanes: number[] = [];
  const placed = dayEvents.map((e) => {
    const [a, b] = span(e);
    let lane = lanes.findIndex((end) => end <= a);
    if (lane === -1) {
      lane = lanes.length;
      lanes.push(b);
    } else lanes[lane] = b;
    return { e, a, b, lane };
  });
  const laneH = (bandH - 2 * hair) / Math.max(1, lanes.length);
  for (const { e, a, b, lane } of placed) {
    const past = b <= nowMin;
    const isNow = e === mo.current;
    let fill = isNow ? T.busyBadge : past ? T.freeBadge : T.eventBg;
    if (past && contrastRatio(fill, T.background) < MARK_MIN_CONTRAST) fill = T.eventBg;
    const bx = xOf(a);
    const bwid = Math.max(2, xOf(b) - xOf(a) - 2 * u);
    const by = bandTop + hair + lane * laneH;
    ctx.fillStyle = fill;
    ctx.fillRect(bx, by, bwid, laneH - u);
    const ink = readableOn(fill, T.slotText);
    if (bwid >= 30 * u && laneH >= 16 * u) {
      const label = clock(e.startTime, tz);
      say(p, label, bx + 4 * u, by + 11 * u, 8 * u, "bold", ink);
      if (laneH >= 30 * u)
        say(
          p,
          clip(p, e.displaySubject, 8.5 * u, "normal", bwid - 8 * u),
          bx + 4 * u,
          by + 23 * u,
          8.5 * u,
          "normal",
          ink
        );
    }
  }

  /* Every second hour, on even hours, so the labels read the same on every day. */
  for (let h = Math.ceil(from / 60); h * 60 <= to; h++) {
    if (h % 2 !== 0) continue;
    const x = xOf(h * 60);
    ctx.fillStyle = T.slotSecondary;
    ctx.fillRect(x, bandTop + bandH, hair, 4 * u);
    const align = h * 60 === from ? "left" : h * 60 === to ? "right" : "center";
    say(p, `${h % 24}:00`, x, bandTop + bandH + 14 * u, 8 * u, "normal", T.slotSecondary, align);
  }

  /* Now: a stroked line and a dot. Stroked, not filled, so that it never counts
   * as the ground under a booking's label. */
  if (nowMin >= from && nowMin <= to) {
    const nx = xOf(nowMin);
    ctx.strokeStyle = T.footerText;
    ctx.lineWidth = Math.max(2, 1.5 * u);
    ctx.beginPath();
    ctx.moveTo(nx, bandTop - 6 * u);
    ctx.lineTo(nx, bandTop + bandH + 3 * u);
    ctx.stroke();
    ctx.beginPath();
    ctx.arc(nx, bandTop - 8 * u, 5 * u, 0, Math.PI * 2);
    ctx.fillStyle = T.freeBadge;
    ctx.fill();
    ctx.lineWidth = Math.max(1, u);
    ctx.stroke();
  }

  return canvas;
}
