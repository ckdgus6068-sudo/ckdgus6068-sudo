// Jelly characters: a face and a job's outfit on a jelly (the shapes are in looks.js). Drawn on the
// box's canvas, and as small SVG pictures of the people in a calendar.
import { LOOKS } from './looks.js';

const JOBS = new Map(LOOKS.jobs.map((job) => [job.id, job]));

/** Every job with its two outfits, in the order a picker shows them (the plain face first). */
export const LOOK_JOBS = LOOKS.jobs.map((job) => ({ id: job.id, name: job.name }));

/** A look as kept in a profile, { job, v }, or null for a plain jelly. Anything else is null too. */
export function cleanLook(look) {
  if (!look || typeof look !== 'object' || !JOBS.has(look.job)) return null;
  return { job: look.job, v: look.v === 1 ? 1 : 0 };
}

export function sameLook(a, b) {
  const x = cleanLook(a);
  const y = cleanLook(b);
  return x === y || (x != null && y != null && x.job === y.job && x.v === y.v);
}

/** "경찰관" or, for the plain face, "얼굴". */
export function lookName(look) {
  const clean = cleanLook(look);
  if (!clean) return '민무늬';
  return clean.job === 'face' ? '얼굴' : JOBS.get(clean.job).name;
}

function mix(hex, other, t) {
  const a = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));
  const b = [1, 3, 5].map((i) => parseInt(other.slice(i, i + 2), 16));
  return `#${a.map((v, i) => Math.round(v + (b[i] - v) * t).toString(16).padStart(2, '0')).join('')}`;
}

function paletteOf(color) {
  return { body: color, shade: mix(color, '#000000', 0.28), light: mix(color, '#FFFFFF', 0.45) };
}

const resolve = (value, palette) => palette[value] ?? value;

/** The parts of a look by where they go: behind the jelly, its face, and the rest. */
function partsOf(look) {
  const clean = cleanLook(look);
  if (!clean) return null;
  const outfit = JOBS.get(clean.job).variants[clean.v];
  const face = LOOKS.faces[LOOKS.variantFaces[clean.v]];
  return {
    back: outfit.filter((p) => p.on === 'back'),
    face: [...face, ...outfit.filter((p) => p.on === 'face')],
    head: outfit.filter((p) => !p.on || p.on === 'head'),
    body: outfit.filter((p) => p.on === 'body'),
  };
}

// ---------------------------------------------------------------- canvas

const paths = new Map();
function path2d(d) {
  let p = paths.get(d);
  if (!p) {
    p = new Path2D(d);
    paths.set(d, p);
  }
  return p;
}

function paint(ctx, parts, palette) {
  for (const part of parts) {
    const p = path2d(part.d);
    ctx.globalAlpha = part.alpha ?? 1;
    if (part.fill) {
      ctx.fillStyle = resolve(part.fill, palette);
      ctx.fill(p);
    }
    if (part.stroke) {
      ctx.strokeStyle = resolve(part.stroke, palette);
      ctx.lineWidth = part.width ?? 4;
      ctx.lineCap = 'round';
      ctx.lineJoin = 'round';
      ctx.stroke(p);
    }
  }
  ctx.globalAlpha = 1;
}

/**
 * Draws what goes behind a jelly whose middle is (x, y) with radius r (a hood, say). Call before
 * drawing the jelly itself, and drawLookFront after.
 */
export function drawLookBack(ctx, look, x, y, r, color) {
  const parts = partsOf(look);
  if (!parts?.back.length) return;
  ctx.save();
  ctx.translate(x, y);
  ctx.scale(r / 100, r / 100);
  paint(ctx, parts.back, paletteOf(color));
  ctx.restore();
}

/**
 * Draws the face and the outfit on a jelly whose middle is (x, y) with radius r.
 * - face: false leaves the face out; faceY and faceScale place it (in the same units, 100 = r).
 * - head: false leaves out the hat and what is held up.
 * - body: false leaves out what is worn lower down (where a jelly's name goes in the box).
 * - hatLift: raises the hat a little, for a face that was moved up.
 */
export function drawLookFront(ctx, look, x, y, r, color, { face = true, faceY = 8, faceScale = 1, head = true, body = true, hatLift = 0 } = {}) {
  const parts = partsOf(look);
  if (!parts) return;
  const palette = paletteOf(color);
  ctx.save();
  ctx.translate(x, y);
  ctx.scale(r / 100, r / 100);
  if (body) paint(ctx, parts.body, palette);
  if (face) {
    ctx.save();
    ctx.translate(0, faceY);
    ctx.scale(faceScale, faceScale);
    paint(ctx, parts.face, palette);
    ctx.restore();
  }
  if (head) {
    ctx.translate(0, -hatLift);
    paint(ctx, parts.head, palette);
  }
  ctx.restore();
}

// ---------------------------------------------------------------- SVG

const SVG_NS = 'http://www.w3.org/2000/svg';
// The jelly fills the lower part; hats and the things held up get the room above and to the right.
const VIEW = { x: -122, y: -170, w: 252, h: 282 };

function svgParts(parts, palette, transform) {
  const g = document.createElementNS(SVG_NS, 'g');
  if (transform) g.setAttribute('transform', transform);
  for (const part of parts) {
    const p = document.createElementNS(SVG_NS, 'path');
    p.setAttribute('d', part.d);
    p.setAttribute('fill', part.fill ? resolve(part.fill, palette) : 'none');
    if (part.stroke) {
      p.setAttribute('stroke', resolve(part.stroke, palette));
      p.setAttribute('stroke-width', String(part.width ?? 4));
      p.setAttribute('stroke-linecap', 'round');
      p.setAttribute('stroke-linejoin', 'round');
    }
    if (part.alpha != null) p.setAttribute('opacity', String(part.alpha));
    g.append(p);
  }
  return g;
}

/**
 * A little picture of a jelly character in [color], [size] pixels tall. With no look it is a plain
 * jelly. Its width is 0.9 of its height.
 */
export function lookSvg(look, color, size = 32) {
  const svg = document.createElementNS(SVG_NS, 'svg');
  svg.setAttribute('viewBox', `${VIEW.x} ${VIEW.y} ${VIEW.w} ${VIEW.h}`);
  svg.setAttribute('width', String(Math.round((size * VIEW.w) / VIEW.h)));
  svg.setAttribute('height', String(size));
  svg.setAttribute('aria-hidden', 'true');
  svg.classList.add('look');
  const palette = paletteOf(color);
  const parts = partsOf(look);
  if (parts) svg.append(svgParts(parts.back, palette));
  const body = document.createElementNS(SVG_NS, 'circle');
  body.setAttribute('r', '100');
  body.setAttribute('fill', color);
  svg.append(body);
  // A soft shine on the jelly.
  const shine = document.createElementNS(SVG_NS, 'ellipse');
  shine.setAttribute('cx', '-42');
  shine.setAttribute('cy', '-52');
  shine.setAttribute('rx', '22');
  shine.setAttribute('ry', '13');
  shine.setAttribute('transform', 'rotate(-30 -42 -52)');
  shine.setAttribute('fill', '#FFFFFF');
  shine.setAttribute('opacity', '0.35');
  svg.append(shine);
  if (parts) {
    svg.append(svgParts(parts.body, palette));
    svg.append(svgParts(parts.face, palette, 'translate(0 8)'));
    svg.append(svgParts(parts.head, palette));
  }
  return svg;
}
