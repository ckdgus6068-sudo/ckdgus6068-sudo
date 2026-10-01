// 상자: one day as a box of soft jellies, with the look and feel of the Android app's box view.
// Each jelly's size follows its length; they fall in order of their start time and squash against
// each other until the box is packed. This is a canvas port of the app's SoftBodyWorld
// (position-based dynamics: a ring of points per jelly held by edge springs and an area
// constraint) and of the gestures of its JellyBoxBoard.

/** Loud, flat colours, one per flavour, the same as in the app's box. */
const BOX_COLORS = ['#FF3DA5', '#FF8A3D', '#FFE14D', '#8CF04F', '#63F0C4', '#43B4FF', '#3F6BF2', '#B05CFF', '#D8F55A', '#F03C5A'];
const INK = '#1A1320';
// The app's constants are in pixels of a 2.625x phone screen; here everything is in CSS pixels.
const REF_DPR = 2.625;
const GRAVITY = 0.35;
const REST_ENERGY = 0.004 / (REF_DPR * REF_DPR);
// A packed pile never stops trembling completely, so the box also rests once no jelly has moved
// more than QUIET_DRIFT px over two windows of QUIET_FRAMES frames, and at the latest after
// MAX_FRAMES frames without a touch or a change.
const QUIET_FRAMES = 30;
const QUIET_DRIFT = 0.8;
const MAX_FRAMES = 900;
const STEP_MS = 1000 / 60;
const SLOP = 8;
const SWIPE = 72;
const LONG_PRESS_MS = 500;
const DOUBLE_TAP_MS = 300;
const BODY_FONT = '"Pretendard Variable", Pretendard, -apple-system, BlinkMacSystemFont, system-ui, sans-serif';

// The jelly lettering. The page picks it (app.js: FONTS) and every open box follows.
let titleFace = { family: '"NanumSquareRound", "Pretendard Variable", Pretendard, system-ui, sans-serif', weight: 800 };
const liveBoxes = new Set();

/** Letters the jellies of every box in [family] at [weight]. */
export function setTitleFace(family, weight) {
  if (titleFace.family === family && titleFace.weight === weight) return;
  titleFace = { family, weight };
  for (const box of liveBoxes) box.restyle();
}

function titleFont(size) {
  return `${titleFace.weight} ${size}px ${titleFace.family}`;
}

function clamp(v, lo, hi) {
  return Math.min(hi, Math.max(lo, v));
}

// The hidden golden jelly (app.js: golden; GoldenJelly.kt in the app) has a flavour of its own.
export const GOLDEN_FLAVOR = 10;
const GOLD = '#FFC52A';
const GOLD_SHEEN = ['#FFF3B0', '#FFC52A', '#E09A00'];
const BURST_MS = 1100;

export function boxColor(flavor) {
  if (flavor === GOLDEN_FLAVOR) return GOLD;
  const n = BOX_COLORS.length;
  return BOX_COLORS[((flavor % n) + n) % n];
}

/** A finished jelly's colour: its box colour, a third of the way to black. */
function doneColor(flavor) {
  const hex = boxColor(flavor);
  const c = [1, 3, 5].map((i) => Math.round(parseInt(hex.slice(i, i + 2), 16) * 0.65));
  return `rgb(${c[0]}, ${c[1]}, ${c[2]})`;
}

function pad2(n) {
  return String(n).padStart(2, '0');
}

function hm(min) {
  return `${pad2(Math.floor(min / 60))}:${pad2(min % 60)}`;
}

function durationText(min) {
  if (min < 60) return `${min}분`;
  if (min % 60 === 0) return `${min / 60}시간`;
  return `${Math.floor(min / 60)}시간 ${min % 60}분`;
}

// ---------------------------------------------------------------- physics

/** One soft jelly: a ring of points with edge springs and an area ("pressure") constraint. */
class Blob {
  constructor(key, cx, cy, area, fixed = false) {
    this.key = key;
    // A pinned jelly (젤위로 고정): held in place at the top; the others bump into it but cannot move it.
    this.fixed = fixed;
    // Still falling in from above the pinned row: it slips behind the pinned jellies until it is below them.
    this.passing = !fixed;
    this.targetArea = area;
    const n = clamp(Math.floor(12 + (Math.sqrt(area) * REF_DPR) / 9), 14, 28);
    this.n = n;
    this.x = new Float64Array(n);
    this.y = new Float64Array(n);
    this.px = new Float64Array(n);
    this.py = new Float64Array(n);
    this.gx = new Float64Array(n);
    this.gy = new Float64Array(n);
    this.minX = 0;
    this.minY = 0;
    this.maxX = 0;
    this.maxY = 0;
    const r = Math.sqrt(area / Math.PI);
    for (let i = 0; i < n; i++) {
      const a = (2 * Math.PI * i) / n;
      this.x[i] = this.px[i] = cx + r * Math.cos(a);
      this.y[i] = this.py[i] = cy + r * Math.sin(a);
    }
    this.setRest(r);
  }

  setRest(r) {
    this.restEdge = (2 * Math.PI * r) / this.n;
    this.restSkip = 2 * r * Math.sin((2 * Math.PI) / this.n);
  }

  resize(area) {
    this.targetArea = area;
    this.setRest(Math.sqrt(area / Math.PI));
  }

  centroidX() {
    let s = 0;
    for (let i = 0; i < this.n; i++) s += this.x[i];
    return s / this.n;
  }

  centroidY() {
    let s = 0;
    for (let i = 0; i < this.n; i++) s += this.y[i];
    return s / this.n;
  }

  area() {
    const { x, y, n } = this;
    let a = 0;
    for (let i = 0; i < n; i++) {
      const j = i + 1 === n ? 0 : i + 1;
      a += x[i] * y[j] - x[j] * y[i];
    }
    return a / 2;
  }

  updateBounds() {
    const { x, y, n } = this;
    let minX = x[0];
    let maxX = x[0];
    let minY = y[0];
    let maxY = y[0];
    for (let i = 1; i < n; i++) {
      if (x[i] < minX) minX = x[i];
      if (x[i] > maxX) maxX = x[i];
      if (y[i] < minY) minY = y[i];
      if (y[i] > maxY) maxY = y[i];
    }
    this.minX = minX;
    this.maxX = maxX;
    this.minY = minY;
    this.maxY = maxY;
  }

  contains(qx, qy) {
    if (qx < this.minX || qx > this.maxX || qy < this.minY || qy > this.maxY) return false;
    const { x, y, n } = this;
    let inside = false;
    for (let i = 0, j = n - 1; i < n; j = i++) {
      if ((y[i] > qy) !== (y[j] > qy) && qx < ((x[j] - x[i]) * (qy - y[i])) / (y[j] - y[i]) + x[i]) inside = !inside;
    }
    return inside;
  }

  integrate(gravity, damping) {
    if (this.fixed) return;
    const { x, y, px, py, n } = this;
    for (let i = 0; i < n; i++) {
      const vx = (x[i] - px[i]) * damping;
      const vy = (y[i] - py[i]) * damping;
      px[i] = x[i];
      py[i] = y[i];
      x[i] += vx;
      y[i] += vy + gravity;
    }
  }

  solveShape() {
    if (this.fixed) return;
    this.distance(1, this.restEdge, 0.9);
    this.distance(2, this.restSkip, 0.15);
    // Area constraint: push the ring out (or in) along its normals.
    const { x, y, gx, gy, n } = this;
    const a = this.area();
    let sum = 0;
    for (let i = 0; i < n; i++) {
      const prev = i === 0 ? n - 1 : i - 1;
      const next = i + 1 === n ? 0 : i + 1;
      gx[i] = (y[next] - y[prev]) / 2;
      gy[i] = (x[prev] - x[next]) / 2;
      sum += gx[i] * gx[i] + gy[i] * gy[i];
    }
    if (sum < 1e-6) return;
    const lambda = ((this.targetArea - a) / sum) * 0.9;
    for (let i = 0; i < n; i++) {
      x[i] += lambda * gx[i];
      y[i] += lambda * gy[i];
    }
  }

  distance(step, rest, k) {
    const { x, y, n } = this;
    for (let i = 0; i < n; i++) {
      const j = (i + step) % n;
      const dx = x[j] - x[i];
      const dy = y[j] - y[i];
      const d = Math.sqrt(dx * dx + dy * dy);
      if (d < 1e-4) continue;
      const c = ((d - rest) / d) * 0.5 * k;
      x[i] += dx * c;
      y[i] += dy * c;
      x[j] -= dx * c;
      y[j] -= dy * c;
    }
  }

  kinetic() {
    const { x, y, px, py, n } = this;
    let e = 0;
    for (let i = 0; i < n; i++) {
      const vx = x[i] - px[i];
      const vy = y[i] - py[i];
      e += vx * vx + vy * vy;
    }
    return e / n;
  }

  push(dx, dy) {
    for (let i = 0; i < this.n; i++) {
      this.x[i] += dx;
      this.y[i] += dy;
    }
  }

  /** Puts a pinned jelly back into its place as a round shape, at rest. */
  placeAt(cx, cy) {
    const r = Math.sqrt(this.targetArea / Math.PI);
    for (let i = 0; i < this.n; i++) {
      const a = (2 * Math.PI * i) / this.n;
      this.x[i] = this.px[i] = cx + r * Math.cos(a);
      this.y[i] = this.py[i] = cy + r * Math.sin(a);
    }
  }

  kick(strength) {
    if (this.fixed) return;
    const cx = this.centroidX();
    const cy = this.centroidY();
    for (let i = 0; i < this.n; i++) {
      this.px[i] -= (this.x[i] - cx) * strength;
      this.py[i] -= (this.y[i] - cy) * strength;
    }
  }
}

/** A box of soft jellies falling under gravity and pressing against each other. */
class World {
  constructor() {
    this.blobs = new Map();
    this.width = 1;
    this.height = 1;
    this.pad = 3;
    this.restFrames = 0;
    this.frames = 0;
    this.quiet = 0;
    this.snapshot = null;
    this.grabKey = null;
    this.grabX = 0;
    this.grabY = 0;
    this.grabOffsetX = 0;
    this.grabOffsetY = 0;
    // Lower edge of the pinned row: jellies falling in pass the pinned ones until they are below it.
    this.pinnedBottom = 0;
  }

  resize(w, h, pad) {
    this.width = w;
    this.height = h;
    this.pad = pad;
  }

  get isResting() {
    return this.grabKey == null && (this.restFrames > 45 || this.quiet >= 2 || this.frames > MAX_FRAMES);
  }

  wake() {
    this.restFrames = 0;
    this.frames = 0;
    this.quiet = 0;
    this.snapshot = null;
  }

  grab(blob, x, y) {
    this.grabKey = blob.key;
    this.grabOffsetX = x - blob.centroidX();
    this.grabOffsetY = y - blob.centroidY();
    this.grabX = x;
    this.grabY = y;
    this.wake();
  }

  release() {
    this.grabKey = null;
    this.wake();
  }

  add(key, area, x, y, fixed = false) {
    this.blobs.set(key, new Blob(key, x, y, area, fixed));
    this.wake();
  }

  remove(key) {
    if (this.blobs.delete(key)) this.wake();
  }

  step(gravity) {
    const blobs = [...this.blobs.values()];
    for (const b of blobs) b.integrate(gravity, 0.985);
    for (const b of blobs) {
      if (b.passing) {
        b.updateBounds();
        if (b.minY > this.pinnedBottom) b.passing = false;
      }
    }
    for (let it = 0; it < 8; it++) {
      for (const b of blobs) {
        b.solveShape();
        if (b.key === this.grabKey) {
          const cx = b.centroidX();
          const cy = b.centroidY();
          b.push((this.grabX - this.grabOffsetX - cx) * 0.18, (this.grabY - this.grabOffsetY - cy) * 0.18);
        }
      }
      for (const b of blobs) b.updateBounds();
      this.collide(blobs);
      this.walls(blobs);
    }
    let energy = 0;
    for (const b of blobs) energy = Math.max(energy, b.kinetic());
    this.restFrames = energy < REST_ENERGY ? this.restFrames + 1 : 0;
    this.frames++;
    if (this.frames % QUIET_FRAMES === 0) {
      const snapshot = new Map(blobs.map((b) => [b.key, [b.centroidX(), b.centroidY()]]));
      if (this.snapshot) {
        let drift = 0;
        for (const [key, [x, y]] of snapshot) {
          const before = this.snapshot.get(key);
          drift = Math.max(drift, before ? Math.hypot(x - before[0], y - before[1]) : Infinity);
        }
        this.quiet = drift < QUIET_DRIFT ? this.quiet + 1 : 0;
      }
      this.snapshot = snapshot;
    }
  }

  walls(blobs) {
    const left = this.pad;
    const right = this.width - this.pad;
    const bottom = this.height - this.pad;
    const top = -this.height * 3;
    for (const b of blobs) {
      const { x, y, px, py } = b;
      for (let i = 0; i < b.n; i++) {
        if (x[i] < left) {
          x[i] = left;
          py[i] = y[i] + (py[i] - y[i]) * 0.6;
        }
        if (x[i] > right) {
          x[i] = right;
          py[i] = y[i] + (py[i] - y[i]) * 0.6;
        }
        if (y[i] > bottom) {
          y[i] = bottom;
          px[i] = x[i] + (px[i] - x[i]) * 0.5;
        }
        if (y[i] < top) y[i] = top;
      }
    }
  }

  collide(blobs) {
    for (const a of blobs) {
      for (const b of blobs) {
        if (a === b) continue;
        if (a.fixed && b.fixed) continue;
        // A jelly still falling in slips behind the pinned ones.
        if ((a.fixed && b.passing) || (b.fixed && a.passing)) continue;
        if (a.maxX < b.minX || a.minX > b.maxX || a.maxY < b.minY || a.minY > b.maxY) continue;
        for (let i = 0; i < a.n; i++) {
          const qx = a.x[i];
          const qy = a.y[i];
          if (!b.contains(qx, qy)) continue;
          // Push the point to the nearest edge of b, and the edge back a little.
          let best = Infinity;
          let bj = 0;
          let bt = 0;
          let bx = 0;
          let by = 0;
          for (let j = 0; j < b.n; j++) {
            const k = j + 1 === b.n ? 0 : j + 1;
            const ex = b.x[k] - b.x[j];
            const ey = b.y[k] - b.y[j];
            const len2 = ex * ex + ey * ey;
            const t = len2 < 1e-6 ? 0 : clamp(((qx - b.x[j]) * ex + (qy - b.y[j]) * ey) / len2, 0, 1);
            const cx = b.x[j] + ex * t;
            const cy = b.y[j] + ey * t;
            const d = (cx - qx) * (cx - qx) + (cy - qy) * (cy - qy);
            if (d < best) {
              best = d;
              bj = j;
              bt = t;
              bx = cx;
              by = cy;
            }
          }
          const dx = bx - qx;
          const dy = by - qy;
          // A pinned jelly does not give way: the other one takes the whole push.
          const share = b.fixed ? 1 : a.fixed ? 0 : 0.5;
          const back = 1 - share;
          a.x[i] += dx * share;
          a.y[i] += dy * share;
          const k = bj + 1 === b.n ? 0 : bj + 1;
          b.x[bj] -= dx * back * (1 - bt);
          b.y[bj] -= dy * back * (1 - bt);
          b.x[k] -= dx * back * bt;
          b.y[k] -= dy * back * bt;
        }
      }
    }
  }

  /** The jelly under a finger; pinned ones first, since they are drawn on top. */
  blobAt(qx, qy) {
    const all = [...this.blobs.values()];
    const list = [...all.filter((b) => b.fixed), ...all.filter((b) => !b.fixed).reverse()];
    for (const b of list) {
      b.updateBounds();
      if (b.contains(qx, qy)) return b;
    }
    return null;
  }
}

// ---------------------------------------------------------------- gold

function blobBounds(blob) {
  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;
  for (let i = 0; i < blob.n; i++) {
    minX = Math.min(minX, blob.x[i]);
    maxX = Math.max(maxX, blob.x[i]);
    minY = Math.min(minY, blob.y[i]);
    maxY = Math.max(maxY, blob.y[i]);
  }
  return { minX, minY, maxX, maxY };
}

/** A little push pin stuck into the top of a pinned jelly. */
function drawPin(ctx, x, tipY, size) {
  const headY = tipY - size * 0.15;
  ctx.strokeStyle = '#B9B4C2';
  ctx.lineWidth = size * 0.22;
  ctx.lineCap = 'round';
  ctx.beginPath();
  ctx.moveTo(x, headY);
  ctx.lineTo(x, tipY + size * 0.9);
  ctx.stroke();
  const dot = (r, color, dx = 0, dy = 0) => {
    ctx.beginPath();
    ctx.arc(x + dx, headY + dy, r, 0, Math.PI * 2);
    ctx.fillStyle = color;
    ctx.fill();
  };
  dot(size * 0.72, '#FFFFFF');
  dot(size * 0.56, '#FF3D6E');
  dot(size * 0.18, 'rgba(255, 255, 255, 0.7)', -size * 0.2, -size * 0.2);
}

function drawStar(ctx, x, y, r, color) {
  ctx.beginPath();
  ctx.moveTo(x, y - r);
  ctx.quadraticCurveTo(x, y, x + r, y);
  ctx.quadraticCurveTo(x, y, x, y + r);
  ctx.quadraticCurveTo(x, y, x - r, y);
  ctx.quadraticCurveTo(x, y, x, y - r);
  ctx.fillStyle = color;
  ctx.fill();
}

/** Little four-pointed stars twinkling on a jelly's edge; [strength] from 0 to 1. */
function drawSparkles(ctx, blob, strength, count, now) {
  const b = blobBounds(blob);
  const cx = blob.centroidX();
  const cy = blob.centroidY();
  for (let i = 0; i < count; i++) {
    const angle = i * 2.39996 + 0.6;
    const twinkle = 0.55 + 0.45 * Math.sin(now / 140 + i * 1.7);
    const r = (3.5 + 3 * twinkle) * (0.6 + 0.4 * strength);
    const x = cx + Math.cos(angle) * (b.maxX - b.minX) * 0.42;
    const y = cy + Math.sin(angle) * (b.maxY - b.minY) * 0.42;
    drawStar(ctx, x, y, r, `rgba(255, 255, 255, ${((0.35 + 0.65 * twinkle) * strength).toFixed(3)})`);
  }
}

/** Gold dust flying out when a jelly turns to gold; [t] from 0 to 1. */
function drawBurst(ctx, blob, t) {
  const b = blobBounds(blob);
  const cx = blob.centroidX();
  const cy = blob.centroidY();
  const reach = Math.max(b.maxX - b.minX, b.maxY - b.minY) * (0.4 + 0.9 * t);
  const fade = 1 - t;
  ctx.beginPath();
  ctx.arc(cx, cy, reach * 0.7, 0, Math.PI * 2);
  ctx.fillStyle = `rgba(255, 243, 176, ${(0.45 * fade).toFixed(3)})`;
  ctx.fill();
  for (let i = 0; i < 14; i++) {
    const angle = (i * 2 * Math.PI) / 14 + 0.2;
    const color = i % 2 === 0 ? `rgba(255, 197, 42, ${fade.toFixed(3)})` : `rgba(255, 255, 255, ${fade.toFixed(3)})`;
    drawStar(ctx, cx + Math.cos(angle) * reach, cy + Math.sin(angle) * reach, 5 + 4 * fade, color);
  }
}

// ---------------------------------------------------------------- labels

/**
 * Breaks Korean text between words only (like word-break: keep-all). A word wider than the line is
 * split by letters, which callers treat as "does not fit" until the font is as small as it goes.
 */
function wrapText(ctx, text, maxWidth, maxLines) {
  const words = text.trim().split(/\s+/).filter(Boolean);
  const lines = [];
  let line = '';
  let split = false;
  for (const word of words) {
    const candidate = line ? `${line} ${word}` : word;
    if (ctx.measureText(candidate).width <= maxWidth) {
      line = candidate;
      continue;
    }
    if (line) lines.push(line);
    line = '';
    if (ctx.measureText(word).width <= maxWidth) {
      line = word;
      continue;
    }
    split = true;
    let part = '';
    for (const ch of Array.from(word)) {
      if (!part || ctx.measureText(part + ch).width <= maxWidth) {
        part += ch;
      } else {
        lines.push(part);
        part = ch;
      }
    }
    line = part;
  }
  if (line) lines.push(line);
  if (lines.length <= maxLines) return { lines, overflow: false, split };
  const kept = lines.slice(0, maxLines);
  let last = Array.from(kept[maxLines - 1]);
  while (last.length && ctx.measureText(`${last.join('')}…`).width > maxWidth) last.pop();
  kept[maxLines - 1] = `${last.join('')}…`;
  return { lines: kept, overflow: true, split };
}

/**
 * Lays out a jelly's name as large as its size allows (two lines at most), with its time and
 * length below when there is room, and who put it up for shared jellies.
 */
function layoutLabel(ctx, item, d) {
  const maxWidth = Math.max(1, d * 0.74);
  const maxHeight = d * 0.62;
  const title = item.title?.trim() || '이름 없는 젤리';
  let size = clamp(d * 0.17, 13, 30);
  let wrapped;
  for (;;) {
    ctx.font = titleFont(size);
    wrapped = wrapText(ctx, title, maxWidth, 2);
    const height = wrapped.lines.length * size * 1.12;
    if ((!wrapped.overflow && !wrapped.split && height <= maxHeight * 0.72) || size <= 10) break;
    size = Math.max(10, size * 0.88);
  }
  const lineHeight = size * 1.12;
  const titleHeight = wrapped.lines.length * lineHeight;

  const subSize = Math.max(9.5, size * 0.52);
  const badgeR = item.badge ? Math.max(6.5, subSize * 0.72) : 0;
  const subHeight = Math.max(subSize * 1.35, badgeR * 2 + 2);
  ctx.font = `600 ${subSize}px ${BODY_FONT}`;
  const candidates = [];
  if (item.start != null) candidates.push(`${hm(item.start)} · ${durationText(item.duration)}`);
  candidates.push(durationText(item.duration));
  if (item.badge) candidates.push('');
  let sub = null;
  for (const text of candidates) {
    const width = (text ? ctx.measureText(text).width : 0) + (item.badge ? badgeR * 2 + (text ? 4 : 0) : 0);
    if (width <= maxWidth && titleHeight + subHeight <= maxHeight) {
      sub = { text, width, size: subSize, height: subHeight };
      break;
    }
  }
  const used = titleHeight + (sub ? sub.height : 0);
  let check = item.done ? Math.min(size * 0.95, Math.max(0, maxHeight - used)) : 0;
  if (check < 8) check = 0;
  return { lines: wrapped.lines, size, lineHeight, sub, badgeR, check };
}

function drawCheck(ctx, cx, cy, size) {
  ctx.beginPath();
  ctx.moveTo(cx - size * 0.34, cy + size * 0.02);
  ctx.lineTo(cx - size * 0.1, cy + size * 0.26);
  ctx.lineTo(cx + size * 0.36, cy - size * 0.24);
  ctx.strokeStyle = '#FFFFFF';
  ctx.lineWidth = size * 0.17;
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';
  ctx.stroke();
}

/** Smooth closed curve through the blob's ring of points. */
function tracePath(ctx, b) {
  const { x, y, n } = b;
  ctx.beginPath();
  ctx.moveTo((x[0] + x[n - 1]) / 2, (y[0] + y[n - 1]) / 2);
  for (let i = 0; i < n; i++) {
    const j = i + 1 === n ? 0 : i + 1;
    ctx.quadraticCurveTo(x[i], y[i], (x[i] + x[j]) / 2, (y[i] + y[j]) / 2);
  }
  ctx.closePath();
}

// ---------------------------------------------------------------- the box

/**
 * A day's box inside [host]. Items: { key, title, start (minutes or null), duration, flavor, done,
 * badge: { text, color } or null }, plus anything the callbacks need.
 *
 * Tap: onOpen. Double tap, or press and hold then let go: onToggle (each can be switched off).
 * Drag: shake the jelly inside the box. A sideways swipe on an empty spot: onSwipe(+1 or -1).
 */
export class JellyBox {
  constructor(host, options = {}) {
    this.host = host;
    this.options = { doubleTap: true, longPress: true, onOpen() {}, onToggle() {}, onSwipe() {}, ...options };
    this.items = [];
    this.byKey = new Map();
    this.world = new World();
    this.labels = new Map();
    this.fontEpoch = 0;
    this.glitter = { key: null, level: 0 };
    this.burstKey = null;
    this.burstAt = 0;
    this.w = 0;
    this.h = 0;
    this.dpr = 1;
    this.raf = 0;
    this.last = 0;
    this.acc = 0;
    this.gesture = null;
    this.pendingTap = null;
    this.reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;

    this.canvas = document.createElement('canvas');
    this.canvas.className = 'box-canvas';
    this.empty = document.createElement('div');
    this.empty.className = 'box-empty';
    host.append(this.canvas, this.empty);
    this.ctx = this.canvas.getContext('2d');

    this.frame = this.frame.bind(this);
    this.onDown = this.onDown.bind(this);
    this.onMove = this.onMove.bind(this);
    this.onUp = this.onUp.bind(this);
    this.onCancel = this.onCancel.bind(this);
    this.canvas.addEventListener('pointerdown', this.onDown);
    this.canvas.addEventListener('pointermove', this.onMove);
    this.canvas.addEventListener('pointerup', this.onUp);
    this.canvas.addEventListener('pointercancel', this.onCancel);
    this.canvas.addEventListener('contextmenu', (e) => e.preventDefault());

    this.resizer = new ResizeObserver(() => this.measure());
    this.resizer.observe(host);
    this.measure();
    document.fonts?.ready?.then(() => this.relabel());
    liveBoxes.add(this);
  }

  /** The lettering changed: fetch the new face for the labels and lay them out again. */
  restyle() {
    this.fontText = null;
    this.loadFonts(this.items);
    this.relabel();
  }

  setOptions(options) {
    Object.assign(this.options, options);
  }

  /** Shows these jellies. New ones fall in from above, removed ones vanish, the rest stay put. */
  set(items) {
    this.items = items;
    this.byKey = new Map(items.map((it) => [it.key, it]));
    this.paintEmpty();
    this.loadFonts(items);
    this.sync();
    this.draw();
  }

  setEmpty(title, text) {
    this.emptyTitle = title;
    this.emptyText = text;
    this.paintEmpty();
  }

  destroy() {
    liveBoxes.delete(this);
    cancelAnimationFrame(this.raf);
    this.raf = 0;
    clearTimeout(this.gesture?.holdTimer);
    clearTimeout(this.pendingTap?.timer);
    this.resizer.disconnect();
    this.canvas.remove();
    this.empty.remove();
    this.destroyed = true;
  }

  /** Where each jelly is on screen, for tests. */
  centers() {
    const r = this.canvas.getBoundingClientRect();
    return [...this.world.blobs.values()].map((b) => ({
      key: b.key,
      title: this.byKey.get(b.key)?.title,
      fixed: b.fixed,
      x: r.left + b.centroidX(),
      y: r.top + b.centroidY(),
    }));
  }

  get resting() {
    return this.world.isResting && !this.raf;
  }

  paintEmpty() {
    const show = this.items.length === 0;
    this.empty.hidden = !show;
    if (!show) return;
    const title = document.createElement('div');
    title.className = 'box-empty-title';
    title.textContent = this.emptyTitle || '이 날은 비어 있어요';
    const text = document.createElement('div');
    text.textContent = this.emptyText || '';
    this.empty.replaceChildren(title, text);
  }

  /** Canvas text does not wait for web fonts: load the ones the labels need, then lay out again. */
  loadFonts(items) {
    if (!document.fonts?.load) return;
    const text = `${items.map((it) => `${it.title}${it.badge?.text ?? ''}`).join('')}0123456789:·시간분`;
    if (text === this.fontText) return;
    this.fontText = text;
    Promise.all([
      document.fonts.load(titleFont(20), text),
      document.fonts.load(`600 12px ${BODY_FONT}`, text),
      document.fonts.load(`700 12px ${BODY_FONT}`, text),
    ]).then(() => this.relabel(), () => {});
  }

  relabel() {
    if (this.destroyed) return;
    this.fontEpoch++;
    this.draw();
  }

  measure() {
    const r = this.host.getBoundingClientRect();
    const w = Math.round(r.width);
    const h = Math.round(r.height);
    const dpr = window.devicePixelRatio || 1;
    if (w === this.w && h === this.h && dpr === this.dpr) return;
    this.w = w;
    this.h = h;
    this.dpr = dpr;
    this.canvas.width = Math.max(1, Math.round(w * dpr));
    this.canvas.height = Math.max(1, Math.round(h * dpr));
    this.world.resize(w, h, 3);
    this.sync();
    this.draw();
  }

  /** Keeps the world in step with the items: sizes follow lengths, spawn order follows start times. */
  sync() {
    const { w, h } = this;
    if (w < 20 || h < 20) return;
    const pad = this.world.pad;
    // Up to three pinned jellies sit in a row at the top; the others fall in behind them.
    const pinned = this.items.filter((it) => it.pinned).sort((a, b) => (a.start ?? 0) - (b.start ?? 0)).slice(0, 3);
    const pinnedKeys = new Set(pinned.map((it) => it.key));
    const ordered = this.items.filter((it) => !pinnedKeys.has(it.key)).sort((a, b) => (a.start ?? 0) - (b.start ?? 0));
    const gap = 6;
    const pinD = pinned.length ? Math.min((w / pinned.length) * 0.8, h * 0.24, w * 0.42) : 0;
    const pinBottom = pinned.length ? pad + gap + pinD + gap : 0;
    this.world.pinnedBottom = pinBottom;
    const total = w * (h - pinBottom);
    // A very long jelly still has to fit across the box.
    const most = 0.7 * w * w;
    const wanted = new Map(ordered.map((it) => [it.key, (Math.max(it.duration, 10) / 540) * 0.8 * total]));
    let sum = 0;
    for (const v of wanted.values()) sum += v;
    const scale = sum > 0.82 * total ? (0.82 * total) / sum : 1;
    let changed = false;
    for (const [key, blob] of [...this.world.blobs]) {
      // Gone, or pinned or let go since: laid down again.
      if ((!wanted.has(key) && !pinnedKeys.has(key)) || blob.fixed !== pinnedKeys.has(key)) {
        this.world.remove(key);
        this.labels.delete(key);
        changed = true;
      }
    }
    pinned.forEach((it, index) => {
      const cx = (w * (index + 0.5)) / pinned.length;
      const cy = pad + gap + pinD / 2;
      const area = (Math.PI * pinD * pinD) / 4;
      const blob = this.world.blobs.get(it.key);
      if (!blob) {
        this.world.add(it.key, area, cx, cy, true);
        changed = true;
      } else {
        if (Math.abs(blob.targetArea - area) > 1) blob.resize(area);
        blob.placeAt(cx, cy);
      }
    });
    let spawnY = -16;
    ordered.forEach((it, index) => {
      const area = Math.min(wanted.get(it.key) * scale, most);
      const blob = this.world.blobs.get(it.key);
      if (!blob) {
        const r = Math.sqrt(area / Math.PI);
        spawnY -= r * 2.1;
        const x = clamp(w * (0.3 + (0.4 * ((index * 37) % 10)) / 10), r + pad, Math.max(r + pad, w - r - pad));
        this.world.add(it.key, area, x, spawnY + r);
        changed = true;
      } else if (Math.abs(blob.targetArea - area) > 1) {
        blob.resize(area);
        changed = true;
      }
    });
    if (changed) this.wake();
  }

  wake() {
    this.world.wake();
    if (!this.raf && !this.destroyed) {
      this.last = 0;
      this.raf = requestAnimationFrame(this.frame);
    }
  }

  frame(t) {
    this.raf = 0;
    if (this.destroyed) return;
    if (this.reduced && this.world.grabKey == null) {
      // No falling and wobbling: settle at once and show the packed box.
      for (let i = 0; i < 1500 && !this.world.isResting; i++) this.world.step(GRAVITY);
    } else {
      const dt = this.last ? Math.min(t - this.last, 100) : STEP_MS;
      this.last = t;
      this.acc += dt;
      let steps = 0;
      while (this.acc >= STEP_MS && steps < 4) {
        this.world.step(GRAVITY);
        this.acc -= STEP_MS;
        steps++;
      }
      if (steps === 4) this.acc = 0;
    }
    this.draw();
    if (!this.world.isResting || this.bursting()) this.raf = requestAnimationFrame(this.frame);
  }

  /** How much the jelly [key] glitters, from 0 to 1: a hint that it is about to turn to gold. */
  setGlitter(key, level) {
    this.glitter = { key, level };
    this.draw();
  }

  /** Gold dust bursting out of the jelly [key] as it turns to gold. */
  burst(key) {
    this.burstKey = key;
    this.burstAt = performance.now();
    this.world.blobs.get(key)?.kick(0.35);
    this.buzz();
    this.wake();
  }

  bursting() {
    return this.burstKey != null && performance.now() - this.burstAt < BURST_MS;
  }

  draw() {
    const { ctx, dpr } = this;
    if (!ctx || this.destroyed) return;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, this.w, this.h);
    // Pinned jellies are drawn last, on top of the ones falling in behind them.
    const blobs = [...this.world.blobs.values()].sort((a, b) => a.fixed - b.fixed);
    for (const blob of blobs) {
      const item = this.byKey.get(blob.key);
      if (!item) continue;
      const gold = item.gold || item.flavor === GOLDEN_FLAVOR;
      const flavor = gold ? GOLDEN_FLAVOR : item.flavor;
      tracePath(ctx, blob);
      ctx.fillStyle = item.done ? doneColor(flavor) : boxColor(flavor);
      ctx.fill();
      if (gold && !item.done) {
        const b = blobBounds(blob);
        const sheen = ctx.createLinearGradient(b.minX, b.minY, b.maxX, b.maxY);
        GOLD_SHEEN.forEach((c, i) => sheen.addColorStop(i / (GOLD_SHEEN.length - 1), c));
        ctx.fillStyle = sheen;
        ctx.fill();
      }
      if (item.done) {
        ctx.strokeStyle = 'rgba(255, 255, 255, 0.85)';
        ctx.lineWidth = 2.5;
        ctx.stroke();
      }
      if (blob.key === this.world.grabKey) {
        ctx.strokeStyle = '#FFFFFF';
        ctx.lineWidth = 3;
        ctx.stroke();
      }
      // Sized by the jelly's resting size, so that squashing does not lay the text out again.
      const d = Math.round((2 * Math.sqrt(blob.targetArea / Math.PI)) / 2) * 2;
      const key = [item.title, item.start, item.duration, item.done, item.badge?.text, d, this.fontEpoch].join('|');
      let label = this.labels.get(blob.key);
      if (!label || label.key !== key) {
        label = layoutLabel(ctx, item, d);
        label.key = key;
        this.labels.set(blob.key, label);
      }
      this.drawLabel(label, item, blob.centroidX(), blob.centroidY());
      const now = performance.now();
      if (gold) drawSparkles(ctx, blob, 1, 4, now);
      if (this.glitter.key === blob.key && this.glitter.level > 0) {
        drawSparkles(ctx, blob, this.glitter.level, 2 + Math.round(this.glitter.level * 4), now);
      }
      if (this.burstKey === blob.key && this.bursting()) drawBurst(ctx, blob, (now - this.burstAt) / BURST_MS);
      if (blob.fixed) drawPin(ctx, blob.centroidX(), blobBounds(blob).minY, 9);
    }
  }

  drawLabel(label, item, cx, cy) {
    const { ctx } = this;
    const gap = label.check > 0 ? label.check * 0.15 : 0;
    const total = label.check + gap + label.lines.length * label.lineHeight + (label.sub ? label.sub.height : 0);
    let y = cy - total / 2;
    if (label.check > 0) {
      drawCheck(ctx, cx, y + label.check / 2, label.check);
      y += label.check + gap;
    }
    ctx.textBaseline = 'middle';
    ctx.textAlign = 'center';
    ctx.font = titleFont(label.size);
    ctx.fillStyle = item.done ? '#FFFFFF' : INK;
    for (const line of label.lines) {
      ctx.fillText(line, cx, y + label.lineHeight / 2);
      y += label.lineHeight;
    }
    const sub = label.sub;
    if (!sub) return;
    const mid = y + sub.height / 2;
    let x = cx - sub.width / 2;
    if (item.badge) {
      const r = label.badgeR;
      ctx.beginPath();
      ctx.arc(x + r, mid, r, 0, Math.PI * 2);
      ctx.fillStyle = item.badge.color;
      ctx.fill();
      ctx.strokeStyle = 'rgba(255, 255, 255, 0.92)';
      ctx.lineWidth = 1.5;
      ctx.stroke();
      ctx.fillStyle = '#FFFFFF';
      ctx.font = `700 ${r * 1.1}px ${BODY_FONT}`;
      ctx.fillText(item.badge.text, x + r, mid + 0.5);
      x += r * 2 + 4;
    }
    if (sub.text) {
      ctx.textAlign = 'left';
      ctx.font = `600 ${sub.size}px ${BODY_FONT}`;
      ctx.fillStyle = item.done ? 'rgba(255, 255, 255, 0.8)' : 'rgba(26, 19, 32, 0.78)';
      ctx.fillText(sub.text, x, mid);
    }
  }

  // ---------------------------------------------------------------- gestures

  local(e) {
    const r = this.canvas.getBoundingClientRect();
    return { x: e.clientX - r.left, y: e.clientY - r.top };
  }

  buzz() {
    try {
      navigator.vibrate?.(12);
    } catch {
      // Not allowed here: no buzz then.
    }
  }

  onDown(e) {
    if (this.gesture) return;
    const p = this.local(e);
    const blob = this.world.blobAt(p.x, p.y);
    this.canvas.setPointerCapture?.(e.pointerId);
    const g = { id: e.pointerId, x0: p.x, y0: p.y, key: blob?.key ?? null, moved: false, held: false, second: false };
    this.gesture = g;
    if (!blob) return;
    if (this.pendingTap && this.pendingTap.key === blob.key) {
      clearTimeout(this.pendingTap.timer);
      this.pendingTap = null;
      g.second = true;
    } else if (this.pendingTap) {
      // A tap on another jelly: the first one opens now.
      this.flushTap();
    }
    if (!this.reduced) {
      blob.kick(-0.03);
      this.wake();
    }
    g.holdTimer = setTimeout(() => {
      if (this.gesture !== g || g.moved) return;
      g.held = true;
      this.buzz();
      const b = this.world.blobs.get(g.key);
      if (b && !b.fixed) this.world.grab(b, g.x0, g.y0);
      this.wake();
    }, LONG_PRESS_MS);
  }

  onMove(e) {
    const g = this.gesture;
    if (!g || e.pointerId !== g.id || g.key == null) return;
    const p = this.local(e);
    if (!g.moved && Math.hypot(p.x - g.x0, p.y - g.y0) > SLOP) {
      g.moved = true;
      clearTimeout(g.holdTimer);
      const b = this.world.blobs.get(g.key);
      if (b && !b.fixed && this.world.grabKey !== g.key) this.world.grab(b, p.x, p.y);
    }
    if (this.world.grabKey === g.key) {
      this.world.grabX = p.x;
      this.world.grabY = p.y;
      this.wake();
    }
  }

  onUp(e) {
    const g = this.gesture;
    if (!g || e.pointerId !== g.id) return;
    this.gesture = null;
    clearTimeout(g.holdTimer);
    const p = this.local(e);
    if (g.key == null) {
      const dx = p.x - g.x0;
      const dy = p.y - g.y0;
      if (Math.abs(dx) > SWIPE && Math.abs(dx) > Math.abs(dy) * 1.5) this.options.onSwipe(dx < 0 ? 1 : -1);
      return;
    }
    if (this.world.grabKey != null) {
      this.world.release();
      this.wake();
    }
    if (g.moved) {
      // Squeezed and let go: one step towards the golden jelly (a pinned one cannot be squeezed).
      const grabbed = this.byKey.get(g.key);
      if (grabbed && !this.world.blobs.get(g.key)?.fixed) this.options.onGrab?.(grabbed);
      return;
    }
    const item = this.byKey.get(g.key);
    if (!item) return;
    if (g.held) {
      if (this.options.longPress) this.finish(item);
      return;
    }
    if (g.second) {
      this.finish(item);
      return;
    }
    if (!this.options.doubleTap) {
      this.options.onOpen(item);
      return;
    }
    // Wait a moment: a second tap finishes the jelly instead of opening it.
    this.pendingTap = { key: g.key, timer: setTimeout(() => this.flushTap(), DOUBLE_TAP_MS) };
  }

  onCancel(e) {
    const g = this.gesture;
    if (!g || e.pointerId !== g.id) return;
    this.gesture = null;
    clearTimeout(g.holdTimer);
    if (this.world.grabKey != null) this.world.release();
    this.wake();
  }

  flushTap() {
    const tap = this.pendingTap;
    if (!tap) return;
    clearTimeout(tap.timer);
    this.pendingTap = null;
    const item = this.byKey.get(tap.key);
    if (item) this.options.onOpen(item);
  }

  finish(item) {
    this.buzz();
    if (!this.reduced) {
      this.world.blobs.get(item.key)?.kick(0.12);
      this.wake();
    }
    this.options.onToggle(item);
  }
}
