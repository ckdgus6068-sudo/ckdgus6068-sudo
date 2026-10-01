// A time of day written in a jelly's title, the way people write it in Korean: "11시 미용실",
// "오후 3시 반 회의", "저녁 7시 30분 영화", "19:30 영화". The app reads titles with the same rules
// (TitleTime.kt), and both are tested with the same sentences.
//
// - The first time in the title counts. "1시간" is a length, not a time.
// - 오전/오후, 아침/점심/낮/저녁/밤/새벽 right before the hour say which half of the day it is.
// - Without them, 1–6시 is the afternoon and 7–11시 the morning, unless the title says otherwise
//   somewhere else (저녁 약속 7시 → 19:00, 5시 기상 → 05:00).
// (No lookbehind in these patterns: older iPhones cannot read it.)

const PART = '(오전|오후|아침|점심|낮|저녁|밤|새벽)';
const KOREAN = new RegExp(`(^|[^\\d])(?:${PART}\\s*)?(\\d{1,2})\\s*시(?!간)(?:\\s*(반)|\\s*(\\d{1,2})\\s*분)?`);
const CLOCK = new RegExp(`(^|[^\\d:])(?:${PART}\\s*)?(\\d{1,2}):([0-5]\\d)(?![\\d:])`);
const AM_HINTS = ['오전', '아침', '새벽', '기상'];
const PM_HINTS = ['오후', '저녁', '밤'];

/** The hour (0–23) meant by [h] o'clock with [part] before it, in [title]; null when unclear. */
function hourOf(part, h, title) {
  if (h >= 24) return null;
  if (part === '오전' || part === '아침' || part === '새벽') return h === 12 ? 0 : h;
  if (part === '오후') return h < 12 ? h + 12 : h;
  if (part === '저녁' || part === '밤') {
    if (h === 12) return null;
    return h < 12 ? h + 12 : h;
  }
  if (part === '점심' || part === '낮') return h >= 1 && h <= 6 ? h + 12 : h;
  if (h === 0 || h >= 12) return h;
  if (AM_HINTS.some((w) => title.includes(w))) return h;
  if (PM_HINTS.some((w) => title.includes(w))) return h + 12;
  return h <= 6 ? h + 12 : h;
}

/**
 * The first time of day in [title] as { minute, text }: minutes after midnight, and the words it
 * was read from ("오후 3시 반"). Null when the title has none.
 */
export function titleTime(title) {
  if (!title) return null;
  const k = KOREAN.exec(title);
  const c = CLOCK.exec(title);
  const at = (m) => (m ? m.index + m[1].length : Infinity);
  if (!k && !c) return null;
  let hour;
  let minute;
  let m;
  if (at(k) <= at(c)) {
    m = k;
    hour = hourOf(k[2], Number(k[3]), title);
    minute = k[4] ? 30 : k[5] != null ? Number(k[5]) : 0;
  } else {
    m = c;
    hour = hourOf(c[2], Number(c[3]), title);
    minute = Number(c[4]);
  }
  if (hour == null || minute > 59) return null;
  return { minute: hour * 60 + minute, text: m[0].slice(m[1].length).trim() };
}
