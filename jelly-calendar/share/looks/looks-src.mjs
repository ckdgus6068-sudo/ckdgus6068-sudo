// The jelly characters: a face, and for each job two outfits (a hat or something held up, and
// something worn on the body). Drawn in units of the jelly's radius: the body is a circle of
// radius 100 around (0, 0), y grows downwards, so the top of the head is at y = -100.
//
// `node looks/build-looks.mjs` turns this into docs/share/looks.js (the web) and LookBook.kt
// (the app), so both draw the very same shapes.
//
// A part is { d, fill?, stroke?, width?, alpha?, on? }:
// - d: SVG path data (absolute M, L, Q, C and Z only).
// - fill / stroke: a colour, or 'body' / 'shade' / 'light' for the jelly's own colour.
// - on: where it goes. 'face': moves and shrinks with the face. 'head' (the default): on and
//   above the head, always drawn. 'body': worn lower down; only drawn where there is room
//   (not behind a jelly's name in the box). 'back': behind the jelly.

const f = (n) => String(Math.round(n * 10) / 10);

class P {
  constructor(cmds = []) {
    this.cmds = cmds;
  }

  M(x, y) {
    this.cmds.push(['M', [x, y]]);
    return this;
  }

  L(x, y) {
    this.cmds.push(['L', [x, y]]);
    return this;
  }

  Q(x1, y1, x, y) {
    this.cmds.push(['Q', [x1, y1], [x, y]]);
    return this;
  }

  C(x1, y1, x2, y2, x, y) {
    this.cmds.push(['C', [x1, y1], [x2, y2], [x, y]]);
    return this;
  }

  Z() {
    this.cmds.push(['Z']);
    return this;
  }

  map(fn) {
    return new P(this.cmds.map(([c, ...pts]) => [c, ...pts.map(fn)]));
  }

  rotate(deg, cx = 0, cy = 0) {
    const a = (deg * Math.PI) / 180;
    const cos = Math.cos(a);
    const sin = Math.sin(a);
    return this.map(([x, y]) => [cx + (x - cx) * cos - (y - cy) * sin, cy + (x - cx) * sin + (y - cy) * cos]);
  }

  move(dx, dy) {
    return this.map(([x, y]) => [x + dx, y + dy]);
  }

  mirror() {
    return this.map(([x, y]) => [-x, y]);
  }

  plus(other) {
    return new P([...this.cmds, ...other.cmds]);
  }

  toString() {
    return this.cmds.map(([c, ...pts]) => (pts.length ? `${c} ${pts.map(([x, y]) => `${f(x)} ${f(y)}`).join(' ')}` : c)).join(' ');
  }
}

const K = 0.5523;
function ellipse(cx, cy, rx, ry = rx) {
  return new P()
    .M(cx + rx, cy)
    .C(cx + rx, cy + K * ry, cx + K * rx, cy + ry, cx, cy + ry)
    .C(cx - K * rx, cy + ry, cx - rx, cy + K * ry, cx - rx, cy)
    .C(cx - rx, cy - K * ry, cx - K * rx, cy - ry, cx, cy - ry)
    .C(cx + K * rx, cy - ry, cx + rx, cy - K * ry, cx + rx, cy)
    .Z();
}
const circle = (cx, cy, r) => ellipse(cx, cy, r, r);

function rrect(x, y, w, h, r = 0) {
  r = Math.min(r, w / 2, h / 2);
  return new P()
    .M(x + r, y)
    .L(x + w - r, y)
    .Q(x + w, y, x + w, y + r)
    .L(x + w, y + h - r)
    .Q(x + w, y + h, x + w - r, y + h)
    .L(x + r, y + h)
    .Q(x, y + h, x, y + h - r)
    .L(x, y + r)
    .Q(x, y, x + r, y)
    .Z();
}

function poly(...pts) {
  const p = new P().M(...pts[0]);
  for (const pt of pts.slice(1)) p.L(...pt);
  return p.Z();
}

function line(...pts) {
  const p = new P().M(...pts[0]);
  for (const pt of pts.slice(1)) p.L(...pt);
  return p;
}

function star(cx, cy, r, inner = 0.45, points = 5) {
  const pts = [];
  for (let i = 0; i < points * 2; i++) {
    const a = -Math.PI / 2 + (i * Math.PI) / points;
    const rr = i % 2 ? r * inner : r;
    pts.push([cx + rr * Math.cos(a), cy + rr * Math.sin(a)]);
  }
  return poly(...pts);
}

/** A dome sitting on the head: from (-w, base) over the top to (w, base). */
function dome(w, base, top) {
  return new P().M(-w, base).C(-w, top, w, top, w, base).Z();
}

/** A dome whose lower edge bows down a little in the middle, like a cap pulled on. */
function cap(w, base, top, sag = 8) {
  return new P().M(-w, base).C(-w, top, w, top, w, base).Q(0, base + sag, -w, base).Z();
}

const fill = (d, color, extra = {}) => ({ d: d.toString(), fill: color, ...extra });

/**
 * A hat as it is worn: drawn at the size it looks right on its own, then made [scale] times as big
 * around the top of the head and pulled down by [drop], so it sits on the head instead of above it.
 */
function worn(parts, scale = 1.24, drop = 22) {
  return parts.map((p) => ({
    ...p,
    d: new P(parseD(p.d)).map(([x, y]) => [x * scale, -100 + (y + 100) * scale + drop]).toString(),
    ...(p.width != null ? { width: Math.round(p.width * scale * 10) / 10 } : {}),
  }));
}
const stroke = (d, color, width, extra = {}) => ({ d: d.toString(), stroke: color, width, ...extra });

const INK = '#2B1D2F';
const WHITE = '#FFFFFF';
const GOLD = '#F2C14E';
const GOLD_DEEP = '#D99A00';
const NAVY = '#26386B';
const NAVY_DEEP = '#1A2750';
const RED = '#E5484D';
const RED_DEEP = '#B8323A';
const STEEL = '#C8CDD3';
const DARK = '#2E2E36';
const WOOD = '#B07A45';
const WOOD_DEEP = '#7A5230';

// ---------------------------------------------------------------- faces

// Around the face's own middle; the eyes sit a little above it.
const BLUSH = [fill(ellipse(-46, 10, 11, 6.5), '#FF6F93', { alpha: 0.35 }), fill(ellipse(46, 10, 11, 6.5), '#FF6F93', { alpha: 0.35 })];

const FACES = {
  // Round shiny eyes and a small smile.
  dot: [
    ...BLUSH,
    fill(ellipse(-27, -6, 8, 10.5), INK),
    fill(ellipse(27, -6, 8, 10.5), INK),
    fill(circle(-29.5, -10, 3.2), WHITE),
    fill(circle(24.5, -10, 3.2), WHITE),
    stroke(new P().M(-11, 10).Q(0, 21, 11, 10), INK, 5),
  ],
  // Eyes shut with joy and a wide-open mouth.
  happy: [
    ...BLUSH,
    stroke(new P().M(-38, -2).Q(-27, -17, -16, -2), INK, 5.5),
    stroke(new P().M(16, -2).Q(27, -17, 38, -2), INK, 5.5),
    fill(new P().M(-13, 7).Q(0, 9, 13, 7).Q(11, 26, 0, 26).Q(-11, 26, -13, 7).Z(), INK),
    fill(ellipse(0, 20, 6.5, 4.2), '#FF7A93'),
  ],
};

// ---------------------------------------------------------------- outfits

/** Two lapels of a dark suit with a white shirt and a tie, worn on the body. */
function suit(jacket, tie) {
  return [
    fill(poly([-24, 40], [24, 40], [0, 92]), WHITE, { on: 'body' }),
    fill(poly([-7, 44], [7, 44], [10, 74], [0, 88], [-10, 74]), tie, { on: 'body' }),
    fill(new P().M(-84, 50).Q(-56, 34, -24, 40).L(0, 92).L(-36, 99).Q(-70, 88, -84, 50).Z(), jacket, { on: 'body' }),
    fill(new P().M(-84, 50).Q(-56, 34, -24, 40).L(0, 92).L(-36, 99).Q(-70, 88, -84, 50).Z().mirror(), jacket, { on: 'body' }),
  ];
}

const tie = (color) => [
  fill(poly([-9, 38], [9, 38], [5, 48], [-5, 48]), color, { on: 'body' }),
  fill(poly([-5, 47], [5, 47], [11, 80], [0, 92], [-11, 80]), color, { on: 'body' }),
];

const stethoscope = [
  stroke(new P().M(-36, 34).Q(-40, 84, 0, 84).Q(36, 84, 34, 54), '#4A4F5C', 6, { on: 'body' }),
  fill(circle(34, 50, 10), '#9AA0A6', { on: 'body' }),
  fill(circle(34, 50, 5), '#E9EDF1', { on: 'body' }),
];

const whiteCoat = [
  fill(new P().M(-80, 56).Q(-50, 32, -22, 40).L(-6, 100).L(-40, 98).Q(-70, 86, -80, 56).Z(), '#F7F9FB', { on: 'body' }),
  fill(new P().M(-80, 56).Q(-50, 32, -22, 40).L(-6, 100).L(-40, 98).Q(-70, 86, -80, 56).Z().mirror(), '#F7F9FB', { on: 'body' }),
];

const apron = (color) => [
  stroke(new P().M(-30, 50).Q(-30, 24, -46, 6), color, 6, { on: 'body' }),
  stroke(new P().M(30, 50).Q(30, 24, 46, 6), color, 6, { on: 'body' }),
  fill(new P().M(-42, 50).L(42, 50).L(52, 98).Q(0, 106, -52, 98).Z(), color, { on: 'body' }),
  fill(rrect(-16, 66, 32, 20, 5), '#FFFFFF', { on: 'body', alpha: 0.25 }),
];

// Things held up at the top right of the jelly.
const scales = [
  fill(rrect(73, -146, 6, 56, 3), GOLD_DEEP),
  fill(rrect(46, -142, 60, 7, 3.5), GOLD),
  stroke(line([50, -138], [42, -116]), GOLD_DEEP, 2.5),
  stroke(line([50, -138], [58, -116]), GOLD_DEEP, 2.5),
  stroke(line([102, -138], [94, -116]), GOLD_DEEP, 2.5),
  stroke(line([102, -138], [110, -116]), GOLD_DEEP, 2.5),
  fill(new P().M(38, -117).Q(50, -100, 62, -117).Z(), GOLD),
  fill(new P().M(90, -117).Q(102, -100, 114, -117).Z(), GOLD),
  fill(rrect(60, -94, 32, 8, 4), GOLD_DEEP),
  fill(circle(76, -147, 5), GOLD),
];

const gavel = [
  fill(rrect(72, -126, 9, 54, 4.5).rotate(-35, 76, -100), WOOD),
  fill(rrect(52, -150, 48, 24, 7).rotate(-35, 76, -138), WOOD_DEEP),
  fill(rrect(50, -146, 8, 16, 3).rotate(-35, 76, -138), GOLD),
  fill(rrect(94, -146, 8, 16, 3).rotate(-35, 76, -138), GOLD),
];

function sunflower(cx, cy, r) {
  const parts = [];
  for (let i = 0; i < 12; i++) {
    parts.push(fill(ellipse(cx, cy - r * 0.62, r * 0.28, r * 0.42).rotate(i * 30, cx, cy), GOLD));
  }
  parts.push(fill(circle(cx, cy, r * 0.44), GOLD_DEEP));
  parts.push(stroke(line([cx - r * 0.22, cy - r * 0.04], [cx + r * 0.22, cy - r * 0.04]), '#FFF3C4', r * 0.07));
  parts.push(stroke(line([cx, cy - r * 0.22], [cx, cy + r * 0.2]), '#FFF3C4', r * 0.07));
  return parts;
}

const briefcase = [
  stroke(new P().M(66, -126).Q(66, -142, 77, -142).Q(88, -142, 88, -126), '#4A3220', 6),
  fill(rrect(48, -128, 58, 40, 8), '#6B4A2B'),
  fill(rrect(48, -112, 58, 5, 2), '#4A3220'),
  fill(rrect(72, -116, 10, 12, 2.5), GOLD),
];

const lawBook = [
  fill(rrect(50, -148, 44, 56, 6).rotate(10, 72, -120), '#7A2E2E'),
  fill(rrect(88, -144, 7, 48, 2).rotate(10, 72, -120), '#F4EEDD'),
  fill(rrect(56, -138, 30, 6, 2).rotate(10, 72, -120), GOLD),
  ...sunflower(70, -114, 13).map((p) => ({ ...p, d: new P(parseD(p.d)).rotate(10, 72, -120).toString() })),
];

const icedCoffee = [
  fill(rrect(79, -164, 5, 30, 2.5), '#2BB3A3'),
  fill(poly([54, -138], [94, -138], [89, -90], [59, -90]), '#E8F4FF'),
  fill(poly([56, -124], [92, -124], [89, -92], [59, -92]), '#5A3A22'),
  fill(rrect(63, -120, 10, 10, 2.5), '#CFE8FF', { alpha: 0.9 }),
  fill(rrect(76, -113, 10, 10, 2.5), '#CFE8FF', { alpha: 0.9 }),
  fill(rrect(50, -142, 48, 7, 3.5), WHITE),
];

const fryingPan = [
  fill(rrect(94, -124, 40, 8, 4).rotate(-22, 94, -120), DARK),
  fill(circle(78, -116, 24), DARK),
  fill(circle(78, -116, 18), '#45454F'),
  fill(ellipse(74, -119, 10, 8.5), WHITE),
  fill(circle(75, -120, 4.5), '#FFC93C'),
];

const wrench = [
  fill(rrect(72, -136, 11, 54, 5.5).rotate(35, 77, -110), '#9AA0A6'),
  fill(circle(92, -132, 13), '#9AA0A6'),
  fill(rrect(88, -150, 9, 18, 2).rotate(35, 92, -132), '#5C6370'),
];

const parcel = [
  fill(rrect(50, -146, 50, 44, 4), '#C99A63'),
  fill(rrect(71, -146, 8, 44, 1), '#E8D3B0'),
  fill(rrect(56, -126, 12, 9, 2), WHITE),
  stroke(line([50, -146], [56, -152], [106, -152], [100, -146]), '#B0814E', 3),
];

const steeringWheel = [
  stroke(circle(80, -114, 26), DARK, 7),
  stroke(line([55, -114], [105, -114]), DARK, 6),
  stroke(line([80, -114], [80, -89]), DARK, 6),
  fill(circle(80, -114, 8), '#4A4F5C'),
];

const laptop = [
  fill(rrect(52, -146, 52, 36, 5), DARK),
  fill(rrect(57, -141, 42, 26, 2), '#33456B'),
  stroke(line([70, -134], [65, -128], [70, -122]), '#7EE787', 3),
  stroke(line([86, -134], [91, -128], [86, -122]), '#7EE787', 3),
  stroke(line([81, -136], [75, -120]), '#7EE787', 3),
  fill(poly([44, -110], [112, -110], [105, -101], [51, -101]), '#9AA0A6'),
];

const brush = [
  fill(rrect(78, -132, 8, 58, 4).rotate(32, 82, -103), WOOD),
  fill(rrect(77, -142, 10, 12, 2).rotate(32, 82, -103), STEEL),
  fill(ellipse(82, -152, 7, 13).rotate(32, 82, -103), '#FF6F93'),
];

const palette = [
  fill(new P().M(52, -120).C(50, -146, 104, -150, 110, -122).C(114, -100, 96, -94, 86, -100).C(80, -104, 74, -98, 74, -92).C(74, -86, 52, -90, 52, -120).Z(), '#F2D7A6'),
  fill(circle(68, -114, 5), '#E8C27A'),
  fill(circle(72, -131, 6), RED),
  fill(circle(88, -136, 6), '#3B82F6'),
  fill(circle(101, -126, 6), '#FFC93C'),
  fill(circle(99, -110, 6), '#5CC15C'),
];

function scissorsAt(x, y, angle) {
  const place = (d) => d.rotate(angle).move(x, y);
  return [
    fill(place(poly([-2, 2], [-9, -46], [-3, -48], [5, 0]).rotate(14)), STEEL),
    fill(place(poly([2, 2], [9, -46], [3, -48], [-5, 0]).rotate(-14)), '#AEB4BC'),
    stroke(place(circle(-12, 16, 9)), RED, 5),
    stroke(place(circle(12, 16, 9)), RED, 5),
    stroke(place(line([-5, 2], [-8, 8])), RED, 5),
    stroke(place(line([5, 2], [8, 8])), RED, 5),
    fill(place(circle(0, 0, 3.5)), '#5C6370'),
  ];
}
const scissors = scissorsAt(84, -112, 24);

const dryer = [
  fill(rrect(66, -118, 15, 32, 6), '#FF8FB1'),
  fill(rrect(54, -140, 48, 26, 13), '#FF8FB1'),
  fill(rrect(97, -136, 15, 18, 4), '#E86F95'),
  fill(circle(66, -127, 6), '#FFD1E0'),
  stroke(line([117, -134], [129, -139]), '#7CC4FF', 3),
  stroke(line([118, -127], [131, -127]), '#7CC4FF', 3),
  stroke(line([117, -120], [129, -115]), '#7CC4FF', 3),
];

const latte = [
  stroke(new P().M(70, -142).Q(64, -150, 70, -158), STEEL, 3),
  stroke(new P().M(82, -142).Q(76, -150, 82, -158), STEEL, 3),
  stroke(new P().M(98, -122).Q(112, -120, 106, -106).Q(102, -100, 94, -103), WHITE, 6),
  fill(new P().M(52, -126).L(100, -126).L(94, -98).Q(76, -90, 58, -98).Z(), WHITE),
  fill(ellipse(76, -126, 24, 5.5), '#C08552'),
  fill(new P().M(76, -122).C(70, -126, 70, -131, 74, -130).Q(76, -129, 76, -127).Q(76, -129, 78, -130).C(82, -131, 82, -126, 76, -122).Z(), '#F6E7D2'),
];

const bean = [
  fill(ellipse(80, -120, 17, 23).rotate(30, 80, -120), '#6B3F22'),
  stroke(new P().M(76, -140).Q(88, -122, 80, -100).rotate(30, 80, -120), '#3E2412', 4),
];

const flask = [
  fill(new P().M(72, -148).L(86, -148).L(86, -130).L(102, -102).Q(105, -94, 96, -94).L(62, -94).Q(53, -94, 56, -102).L(72, -130).Z(), '#E8F6FF'),
  fill(new P().M(63, -110).L(95, -110).L(102, -102).Q(105, -94, 96, -94).L(62, -94).Q(53, -94, 56, -102).Z(), '#7CE0A3'),
  stroke(new P().M(72, -148).L(86, -148).L(86, -130).L(102, -102).Q(105, -94, 96, -94).L(62, -94).Q(53, -94, 56, -102).L(72, -130).Z(), '#9AA0A6', 2.5),
  fill(circle(74, -103, 3), WHITE),
  fill(circle(84, -116, 2.5), '#7CE0A3'),
  fill(circle(80, -124, 2), '#7CE0A3'),
];

const testTube = [
  fill(rrect(72, -152, 16, 58, 8).rotate(18, 80, -123), '#E8F6FF'),
  fill(rrect(72, -122, 16, 28, 8).rotate(18, 80, -123), '#B784F5'),
  stroke(rrect(72, -152, 16, 58, 8).rotate(18, 80, -123), '#9AA0A6', 2.5),
  fill(rrect(69, -155, 22, 6, 3).rotate(18, 80, -123), '#9AA0A6'),
];

const pointer = [
  stroke(line([58, -70], [112, -146]), WOOD, 6),
  fill(circle(112, -146, 5.5), RED),
];

const blackboard = [
  fill(rrect(46, -150, 62, 44, 6), WOOD),
  fill(rrect(51, -145, 52, 34, 3), '#2F6B4F'),
  stroke(line([58, -134], [76, -134]), WHITE, 3),
  stroke(line([58, -124], [94, -124]), WHITE, 3),
  fill(rrect(82, -104, 18, 7, 3.5), WHITE),
];

const sprout = [
  stroke(new P().M(0, -100).Q(3, -116, 0, -128), '#3E9B4F', 5),
  fill(ellipse(-11, -131, 11, 6.5).rotate(-25, -11, -131), '#5CC15C'),
  fill(ellipse(12, -135, 12, 7).rotate(20, 12, -135), '#5CC15C'),
];

function glasses(color = '#3A2E3F') {
  return [
    stroke(circle(-27, -6, 16), color, 4, { on: 'face' }),
    stroke(circle(27, -6, 16), color, 4, { on: 'face' }),
    stroke(new P().M(-11, -9).Q(0, -15, 11, -9), color, 4, { on: 'face' }),
  ];
}

// "TAXI" in little strokes on a roof light.
const taxi = [
  fill(rrect(-38, -112, 76, 12, 5), DARK),
  fill(rrect(-32, -142, 64, 32, 9), '#FFD43B'),
  stroke(rrect(-32, -142, 64, 32, 9), DARK, 3),
  stroke(line([-23, -133], [-11, -133]), DARK, 3.4),
  stroke(line([-17, -133], [-17, -119]), DARK, 3.4),
  stroke(line([-9, -119], [-4, -133], [1, -119]), DARK, 3.4),
  stroke(line([-7, -124], [-1, -124]), DARK, 3),
  stroke(line([5, -133], [14, -119]), DARK, 3.4),
  stroke(line([14, -133], [5, -119]), DARK, 3.4),
  stroke(line([20, -133], [20, -119]), DARK, 3.4),
];

/** 태극: red over blue with the S between them. */
function taegeuk(cx, cy, r) {
  const k = K * r;
  return [
    fill(circle(cx, cy, r), '#2F6FD8'),
    fill(new P()
      .M(cx - r, cy)
      .C(cx - r, cy - k, cx - k, cy - r, cx, cy - r)
      .C(cx + k, cy - r, cx + r, cy - k, cx + r, cy)
      .C(cx + r, cy + r * 0.55, cx, cy + r * 0.55, cx, cy)
      .C(cx, cy - r * 0.55, cx - r, cy - r * 0.55, cx - r, cy)
      .Z(), '#E5484D'),
  ];
}

/** A 무궁화 with a 태극 in the middle, as on Korean police caps (made simple). */
function mugunghwa(cx, cy, r) {
  const parts = [];
  for (let i = 0; i < 5; i++) parts.push(fill(ellipse(cx, cy - r * 0.56, r * 0.4, r * 0.52).rotate(i * 72, cx, cy), GOLD));
  parts.push(fill(circle(cx, cy, r * 0.46), WHITE));
  parts.push(...taegeuk(cx, cy, r * 0.36));
  return parts;
}

/** The navy 근무모: a cap with a short brim and the emblem at the front. */
function policeCap() {
  return [
    fill(cap(64, -92, -138, 4), '#22305A'),
    fill(new P().M(-60, -92).Q(0, -72, 60, -92).Q(0, -84, -60, -92).Z(), '#141C36'),
    stroke(new P().M(-62, -95).Q(0, -103, 62, -95), '#3A4B80', 3),
    ...mugunghwa(0, -115, 15),
  ];
}

// The fluorescent yellow vest over a navy shirt, with a shining band.
const policeVest = [
  fill(poly([-24, 40], [24, 40], [0, 70]), '#22305A', { on: 'body' }),
  fill(new P().M(-86, 48).Q(-52, 30, -24, 40).L(-8, 100).Q(-62, 94, -86, 48).Z(), '#D9F23A', { on: 'body' }),
  fill(new P().M(-86, 48).Q(-52, 30, -24, 40).L(-8, 100).Q(-62, 94, -86, 48).Z().mirror(), '#D9F23A', { on: 'body' }),
  fill(rrect(-82, 72, 66, 8, 4), '#E9EEF2', { on: 'body' }),
  fill(rrect(16, 72, 66, 8, 4), '#E9EEF2', { on: 'body' }),
  ...mugunghwa(-44, 56, 7).map((p) => ({ ...p, on: 'body' })),
];

// 경광봉: the glowing traffic baton.
const lightBaton = [
  fill(rrect(82, -160, 14, 60, 7).rotate(28, 89, -118), '#FF6A3D'),
  fill(rrect(86, -154, 6, 48, 3).rotate(28, 89, -118), '#FFD0A8'),
  fill(rrect(81, -102, 16, 26, 5).rotate(28, 89, -118), DARK),
];

// ---------------------------------------------------------------- jobs

const JOBS = [
  {
    id: 'police',
    name: '경찰관',
    variants: [
      // 근무모 with the 무궁화 emblem, and the fluorescent vest of the patrol.
      [...worn(policeCap()), ...policeVest],
      // The same cap and vest, and a light baton held up.
      [...lightBaton, ...worn(policeCap()), ...policeVest],
    ],
  },
  {
    id: 'fire',
    name: '소방관',
    variants: [
      // A red helmet with a gold shield at the front.
      [
        ...worn([
          fill(ellipse(0, -84, 88, 14), RED_DEEP),
          fill(dome(70, -86, -144), RED),
          fill(rrect(-7, -136, 14, 46, 7), '#C93B43'),
          fill(new P().M(-19, -130).L(19, -130).L(17, -98).Q(0, -90, -17, -98).Z(), GOLD),
          fill(star(0, -114, 8), GOLD_DEEP),
        ], 1.2, 18),
        fill(rrect(-80, 62, 160, 9, 4), '#E9EEF2', { on: 'body' }),
        fill(rrect(-74, 74, 148, 9, 4), '#FFC93C', { on: 'body' }),
      ],
      // A yellow rescue helmet with a shining band.
      [
        ...worn([
          fill(ellipse(0, -86, 82, 11), '#E0A100'),
          fill(dome(68, -88, -142), '#FFC93C'),
          fill(rrect(-58, -110, 116, 10, 5), '#EEF2F6'),
          fill(rrect(-6, -138, 12, 26, 6), '#E0A100'),
        ], 1.2, 18),
        fill(rrect(-80, 62, 160, 9, 4), '#E9EEF2', { on: 'body' }),
        fill(rrect(-74, 74, 148, 9, 4), RED, { on: 'body' }),
      ],
    ],
  },
  {
    id: 'doctor',
    name: '의사',
    variants: [
      // A head mirror on a band, and a stethoscope.
      [
        stroke(new P().M(-90, -46).Q(0, -112, 90, -46), '#3A3A46', 7),
        fill(circle(0, -98, 22), '#DADDE2'),
        fill(circle(0, -98, 15), '#F5F7F9'),
        fill(circle(0, -98, 5), INK),
        ...whiteCoat,
        ...stethoscope,
      ],
      // A surgical cap, and a stethoscope.
      [
        fill(cap(76, -76, -132, 10), '#2BB3A3'),
        stroke(new P().M(-72, -84).Q(0, -72, 72, -84), '#1E8C80', 4),
        fill(ellipse(80, -88, 9, 6).rotate(30, 80, -88), '#1E8C80'),
        ...whiteCoat,
        ...stethoscope,
      ],
    ],
  },
  {
    id: 'nurse',
    name: '간호사',
    variants: [
      // The folded white nurse cap with a red cross.
      [
        ...worn([
          fill(new P().M(-48, -88).L(-38, -128).Q(0, -138, 38, -128).L(48, -88).Q(0, -98, -48, -88).Z(), WHITE),
          stroke(new P().M(-48, -88).L(-38, -128).Q(0, -138, 38, -128).L(48, -88).Q(0, -98, -48, -88).Z(), '#E3E3EA', 2.5),
          fill(rrect(-5, -126, 10, 26, 2), RED),
          fill(rrect(-13, -118, 26, 10, 2), RED),
        ]),
        fill(rrect(30, 62, 34, 14, 4), WHITE, { on: 'body' }),
        fill(rrect(34, 67, 18, 4, 2), '#FF9EBB', { on: 'body' }),
      ],
      // A pink scrub cap with a little cross pin.
      [
        fill(cap(74, -78, -132, 10), '#FF9EBB'),
        stroke(new P().M(-70, -86).Q(0, -74, 70, -86), '#E07A9B', 4),
        fill(circle(-34, -106, 11), WHITE),
        fill(rrect(-36.5, -114, 5, 16, 1.5), RED),
        fill(rrect(-42, -108.5, 16, 5, 1.5), RED),
        fill(rrect(30, 62, 34, 14, 4), WHITE, { on: 'body' }),
        fill(rrect(34, 67, 18, 4, 2), '#2BB3A3', { on: 'body' }),
      ],
    ],
  },
  {
    id: 'judge',
    name: '판사',
    variants: [
      // The scales of justice held up, and a black robe.
      [
        ...scales,
        fill(new P().M(-86, 46).Q(-44, 26, 0, 50).Q(44, 26, 86, 46).Q(70, 92, 0, 104).Q(-70, 92, -86, 46).Z(), '#26222B', { on: 'body' }),
        fill(poly([-14, 50], [14, 50], [0, 70]), WHITE, { on: 'body' }),
      ],
      // A gavel, and a black robe.
      [
        ...gavel,
        fill(new P().M(-86, 46).Q(-44, 26, 0, 50).Q(44, 26, 86, 46).Q(70, 92, 0, 104).Q(-70, 92, -86, 46).Z(), '#26222B', { on: 'body' }),
        fill(poly([-14, 50], [14, 50], [0, 70]), WHITE, { on: 'body' }),
      ],
    ],
  },
  {
    id: 'lawyer',
    name: '변호사',
    variants: [
      // The gold sunflower badge pinned on, and a suit.
      [...sunflower(-56, -96, 24), ...suit(NAVY, RED)],
      // The book of laws held up, and a suit.
      [...lawBook, ...suit('#3A3A46', '#3B82F6')],
    ],
  },
  {
    id: 'teacher',
    name: '교사',
    variants: [
      // Round glasses and a pointer.
      [...glasses(), ...pointer],
      // A little blackboard and chalk.
      [...blackboard],
    ],
  },
  {
    id: 'office',
    name: '회사원',
    variants: [
      // A briefcase, and a tie.
      [...briefcase, ...tie('#3B5BA9')],
      // An iced americano, a tie and an ID card on a lanyard.
      [
        ...icedCoffee,
        ...tie(RED),
        stroke(new P().M(-34, 36).Q(-26, 66, -20, 70), '#3B82F6', 3.5, { on: 'body' }),
        fill(rrect(-34, 68, 24, 30, 4), WHITE, { on: 'body' }),
        fill(rrect(-30, 74, 16, 9, 2), '#BFD7FF', { on: 'body' }),
      ],
    ],
  },
  {
    id: 'chef',
    name: '요리사',
    variants: [
      // The tall white toque and a red neckerchief.
      [
        ...worn([
          fill(circle(-30, -140, 26), WHITE),
          fill(circle(30, -140, 26), WHITE),
          fill(circle(0, -152, 30), WHITE),
          fill(rrect(-46, -126, 92, 36, 9), WHITE),
          stroke(new P().M(-44, -104).L(44, -104), '#E6E6EE', 2.5),
          stroke(new P().M(-14, -124).L(-14, -96), '#E6E6EE', 2.5),
          stroke(new P().M(14, -124).L(14, -96), '#E6E6EE', 2.5),
        ], 1.08, 24),
        fill(new P().M(-28, 44).Q(0, 58, 28, 44).L(10, 76).L(0, 68).L(-10, 76).Z(), RED, { on: 'body' }),
      ],
      // A red bandana and a frying pan with an egg.
      [
        fill(cap(78, -74, -134, 10), RED),
        fill(circle(-34, -100, 4.5), WHITE),
        fill(circle(-6, -114, 4.5), WHITE),
        fill(circle(24, -104, 4.5), WHITE),
        fill(circle(-52, -84, 4), WHITE),
        fill(circle(52, -86, 4), WHITE),
        fill(poly([-78, -78], [-98, -66], [-90, -86]), RED_DEEP),
        ...fryingPan,
        fill(new P().M(-28, 44).Q(0, 58, 28, 44).L(10, 76).L(0, 68).L(-10, 76).Z(), WHITE, { on: 'body' }),
      ],
    ],
  },
  {
    id: 'soldier',
    name: '군인',
    variants: [
      // A camouflage helmet.
      [
        fill(cap(76, -78, -144, 4), '#5B7A3A'),
        fill(ellipse(-32, -112, 15, 9).rotate(-10, -32, -112), '#3E5428'),
        fill(ellipse(22, -124, 13, 8).rotate(15, 22, -124), '#7E9B4E'),
        fill(ellipse(44, -98, 14, 8), '#3E5428'),
        fill(ellipse(-54, -90, 11, 6), '#7E9B4E'),
        fill(ellipse(4, -98, 10, 6), '#7E9B4E'),
        stroke(new P().M(-76, -78).Q(0, -82, 76, -78), '#3E5428', 5),
        stroke(new P().M(-28, 34).Q(0, 62, 28, 34), '#9AA0A6', 2.5, { on: 'body' }),
        fill(rrect(-8, 56, 16, 22, 4), STEEL, { on: 'body' }),
      ],
      // A camouflage patrol cap.
      [
        ...worn([
          fill(poly([-62, -90], [-58, -130], [58, -130], [62, -90]), '#6B8A45'),
          fill(ellipse(-28, -114, 13, 8), '#4A6430'),
          fill(ellipse(26, -120, 12, 7), '#89A65A'),
          fill(ellipse(34, -100, 12, 7), '#4A6430'),
          fill(ellipse(-44, -98, 9, 6), '#89A65A'),
          fill(new P().M(-62, -90).Q(0, -70, 62, -90).Q(0, -82, -62, -90).Z(), '#3E5428'),
        ]),
        stroke(new P().M(-28, 34).Q(0, 62, 28, 34), '#9AA0A6', 2.5, { on: 'body' }),
        fill(rrect(-8, 56, 16, 22, 4), STEEL, { on: 'body' }),
      ],
    ],
  },
  {
    id: 'farmer',
    name: '농부',
    variants: [
      // A straw hat with a red band, and dungarees.
      [
        ...worn([
          fill(ellipse(0, -86, 106, 20), '#E8C27A'),
          fill(dome(52, -88, -140), '#EFCF8B'),
          fill(rrect(-51, -104, 102, 13, 4), RED),
          stroke(new P().M(-96, -84).Q(0, -70, 96, -84), '#C9A15A', 2.5),
          stroke(new P().M(-34, -122).Q(0, -132, 34, -122), '#C9A15A', 2.5),
        ], 1.06, 16),
        fill(rrect(-36, 58, 72, 44, 12), '#3B6FD0', { on: 'body' }),
        stroke(line([-30, 62], [-40, 30]), '#3B6FD0', 7, { on: 'body' }),
        stroke(line([30, 62], [40, 30]), '#3B6FD0', 7, { on: 'body' }),
        fill(circle(-28, 64, 4), GOLD, { on: 'body' }),
        fill(circle(28, 64, 4), GOLD, { on: 'body' }),
      ],
      // A towel tied round the head, and a sprout.
      [
        ...sprout,
        fill(new P().M(-90, -54).Q(0, -122, 90, -54).L(88, -40).Q(0, -106, -88, -40).Z(), WHITE),
        stroke(new P().M(-90, -54).Q(0, -122, 90, -54).L(88, -40).Q(0, -106, -88, -40).Z(), '#D9DEE6', 2),
        fill(ellipse(96, -52, 10, 7).rotate(-30, 96, -52), WHITE),
        fill(ellipse(98, -38, 9, 6).rotate(30, 98, -38), WHITE),
        fill(rrect(-36, 58, 72, 44, 12), '#4C8A4C', { on: 'body' }),
        stroke(line([-30, 62], [-40, 30]), '#4C8A4C', 7, { on: 'body' }),
        stroke(line([30, 62], [40, 30]), '#4C8A4C', 7, { on: 'body' }),
      ],
    ],
  },
  {
    id: 'builder',
    name: '건설 기술자',
    variants: [
      // A yellow hard hat and a safety vest.
      [
        ...worn([
          fill(rrect(-82, -92, 164, 13, 6.5), '#E8A800'),
          fill(dome(66, -88, -142), '#FFC93C'),
          fill(rrect(-7, -136, 14, 46, 7), '#F0B400'),
        ], 1.2, 20),
        fill(new P().M(-86, 48).Q(-52, 30, -24, 40).L(-30, 100).Q(-66, 92, -86, 48).Z(), '#FF8A3D', { on: 'body' }),
        fill(new P().M(-86, 48).Q(-52, 30, -24, 40).L(-30, 100).Q(-66, 92, -86, 48).Z().mirror(), '#FF8A3D', { on: 'body' }),
        fill(rrect(-80, 70, 52, 7, 3), '#E9EEF2', { on: 'body' }),
        fill(rrect(28, 70, 52, 7, 3), '#E9EEF2', { on: 'body' }),
      ],
      // A white hard hat and a wrench.
      [
        ...wrench,
        ...worn([
          fill(rrect(-82, -92, 164, 13, 6.5), '#DCE1E7'),
          fill(dome(66, -88, -142), '#FAFBFC'),
          stroke(dome(66, -88, -142), '#D3D9E0', 2.5),
          fill(rrect(-7, -136, 14, 46, 7), '#E3E7EC'),
        ], 1.2, 20),
        fill(new P().M(-86, 48).Q(-52, 30, -24, 40).L(-30, 100).Q(-66, 92, -86, 48).Z(), '#9BE15D', { on: 'body' }),
        fill(new P().M(-86, 48).Q(-52, 30, -24, 40).L(-30, 100).Q(-66, 92, -86, 48).Z().mirror(), '#9BE15D', { on: 'body' }),
        fill(rrect(-80, 70, 52, 7, 3), '#E9EEF2', { on: 'body' }),
        fill(rrect(28, 70, 52, 7, 3), '#E9EEF2', { on: 'body' }),
      ],
    ],
  },
  {
    id: 'courier',
    name: '택배 기사',
    variants: [
      // A cap and a parcel.
      [
        ...parcel,
        ...worn([
          fill(ellipse(-6, -91, 72, 9), '#5C4229'),
          fill(cap(62, -94, -136, 4), '#7A5A3A'),
          fill(rrect(-14, -124, 28, 12, 4), '#F2C14E'),
        ]),
      ],
      // A rider's helmet with goggles.
      [
        ...worn([
          fill(dome(70, -84, -142), '#3B82F6'),
          fill(rrect(-7, -138, 14, 50, 7), WHITE),
          stroke(line([-70, -100], [-36, -100]), DARK, 5),
          stroke(line([36, -100], [70, -100]), DARK, 5),
          fill(circle(-22, -102, 14), '#BFE6FF'),
          fill(circle(22, -102, 14), '#BFE6FF'),
          stroke(circle(-22, -102, 14), DARK, 4),
          stroke(circle(22, -102, 14), DARK, 4),
        ], 1.2, 18),
      ],
    ],
  },
  {
    id: 'driver',
    name: '운전기사',
    variants: [
      // A driver's cap and the steering wheel.
      [
        ...steeringWheel,
        ...worn([
          fill(new P().M(-64, -90).C(-62, -132, 62, -136, 66, -92).Q(0, -100, -64, -90).Z(), '#4A4F5C'),
          fill(new P().M(-48, -92).Q(0, -74, 52, -94).Q(0, -86, -48, -92).Z(), '#2E3036'),
          fill(rrect(-10, -118, 20, 10, 3), STEEL),
        ]),
      ],
      // A taxi light on top.
      [...worn(taxi, 1, 10)],
    ],
  },
  {
    id: 'developer',
    name: '개발자',
    variants: [
      // Headphones.
      [
        stroke(new P().M(-86, -58).C(-90, -148, 90, -148, 86, -58), DARK, 10),
        fill(rrect(-108, -78, 26, 44, 11), DARK),
        fill(rrect(82, -78, 26, 44, 11), DARK),
        fill(rrect(-100, -72, 12, 32, 6), '#6E6E86'),
        fill(rrect(88, -72, 12, 32, 6), '#6E6E86'),
      ],
      // A hood behind the head and a laptop.
      [
        fill(new P().M(-104, 10).C(-128, -132, 128, -132, 104, 10).Q(96, -20, 70, -40).Q(0, -122, -70, -40).Q(-96, -20, -104, 10).Z(), '#7C8597', { on: 'back' }),
        ...laptop,
        stroke(line([-18, 44], [-22, 78]), '#C9CED8', 4, { on: 'body' }),
        stroke(line([18, 44], [22, 78]), '#C9CED8', 4, { on: 'body' }),
      ],
    ],
  },
  {
    id: 'designer',
    name: '디자이너',
    variants: [
      // A beret and a brush.
      [
        ...brush,
        ...worn([
          fill(new P().M(-72, -86).C(-90, -114, -42, -146, 8, -142).C(60, -138, 88, -114, 70, -86).Q(0, -100, -72, -86).Z(), '#8E6CF0'),
          fill(rrect(4, -154, 7, 15, 3.5), '#6E4FD0'),
        ], 1.2, 20),
      ],
      // A palette, and a scarf.
      [
        ...palette,
        fill(new P().M(-60, 40).Q(0, 70, 60, 40).L(56, 54).Q(0, 84, -56, 54).Z(), '#FF8A3D', { on: 'body' }),
        fill(rrect(26, 52, 14, 34, 6), '#FF8A3D', { on: 'body' }),
      ],
    ],
  },
  {
    id: 'stylist',
    name: '미용사',
    variants: [
      // Scissors, and a comb in the pocket.
      [...scissors, fill(rrect(-58, 54, 36, 12, 3), DARK, { on: 'body' }), stroke(line([-54, 66], [-54, 74], [-50, 66], [-46, 74], [-42, 66], [-38, 74], [-34, 66], [-30, 74], [-26, 66]), DARK, 2.5, { on: 'body' })],
      // A hair dryer.
      [...dryer, ...apron('#3A3A46')],
    ],
  },
  {
    id: 'barista',
    name: '바리스타',
    variants: [
      // A latte with a heart, a flat cap and an apron.
      [
        ...latte,
        ...worn([
          fill(new P().M(-64, -90).C(-62, -128, 62, -130, 66, -92).Q(0, -100, -64, -90).Z(), '#8B5A2B'),
          fill(new P().M(-44, -92).Q(0, -78, 50, -94).Q(0, -86, -44, -92).Z(), '#6B4220'),
        ]),
        ...apron('#2F6B4F'),
      ],
      // A coffee bean, a visor and an apron.
      [
        ...bean,
        ...worn([
          fill(rrect(-72, -104, 144, 15, 7.5), '#2F6B4F'),
          fill(new P().M(-48, -92).Q(0, -68, 48, -92).Q(0, -80, -48, -92).Z(), '#23513C'),
        ]),
        ...apron('#6B4220'),
      ],
    ],
  },
  {
    id: 'scientist',
    name: '연구원',
    variants: [
      // Goggles on the forehead and a flask.
      [
        ...flask,
        stroke(new P().M(-94, -58).Q(0, -118, 94, -58), DARK, 6),
        fill(rrect(-44, -106, 38, 26, 10), '#9FE3D6'),
        fill(rrect(6, -106, 38, 26, 10), '#9FE3D6'),
        stroke(rrect(-44, -106, 38, 26, 10), DARK, 4),
        stroke(rrect(6, -106, 38, 26, 10), DARK, 4),
        ...whiteCoat,
      ],
      // Round glasses and a test tube.
      [...glasses('#4A4F5C'), ...testTube, ...whiteCoat],
    ],
  },
  {
    id: 'student',
    name: '학생',
    variants: [
      // A mortarboard with a gold tassel.
      [
        ...worn([
          fill(new P().M(-42, -104).L(42, -104).L(42, -88).Q(0, -80, -42, -88).Z(), '#2B2B33'),
          fill(poly([-70, -114], [0, -138], [70, -114], [0, -92]), '#34343E'),
          stroke(line([0, -115], [56, -106], [58, -84]), GOLD, 3.5),
          fill(rrect(53, -86, 10, 16, 4), GOLD),
          fill(circle(0, -115, 4.5), GOLD),
        ]),
      ],
      // The yellow safety hat, and backpack straps.
      [
        ...worn([
          fill(ellipse(0, -86, 84, 12), '#F5B700'),
          fill(dome(66, -88, -140), '#FFD43B'),
          fill(circle(0, -112, 10), WHITE),
          fill(star(0, -112, 7), '#5CC15C'),
        ], 1.2, 18),
        stroke(line([-40, 36], [-46, 100]), '#E5484D', 9, { on: 'body' }),
        stroke(line([40, 36], [46, 100]), '#E5484D', 9, { on: 'body' }),
      ],
    ],
  },
];

/** Parses path data written by P back into commands (for the few parts transformed afterwards). */
function parseD(d) {
  const tokens = d.split(' ');
  const cmds = [];
  let i = 0;
  const sizes = { M: 1, L: 1, Q: 2, C: 3, Z: 0 };
  while (i < tokens.length) {
    const c = tokens[i++];
    const pts = [];
    for (let k = 0; k < sizes[c]; k++) {
      pts.push([Number(tokens[i]), Number(tokens[i + 1])]);
      i += 2;
    }
    cmds.push([c, ...pts]);
  }
  return cmds;
}

export const LOOKS = {
  faces: FACES,
  // The plain face comes first: no outfit, two faces.
  jobs: [{ id: 'face', name: '얼굴만', variants: [[], []] }, ...JOBS],
  // Variant 0 smiles with round eyes, variant 1 beams with its eyes shut.
  variantFaces: ['dot', 'happy'],
};
