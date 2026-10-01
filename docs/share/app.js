// 공유 젤리: a month of jellies shared by two (or a few) people, on iPhone, Android or any browser.
import { firebaseConfig, googleSignIn, publicUrl } from './config.js';
import * as store from './store.js';
import { JellyBox, GOLDEN_FLAVOR, setTitleFace } from './box.js';
import { holidayOn } from './holidays.js';
import { titleTime } from './titletime.js';
import { cleanLook, lookName, lookSvg, LOOK_JOBS, sameLook } from './character.js';

// ---------------------------------------------------------------- look

const FLAVORS = [
  { name: '딸기', light: '#FFD6E0', base: '#FF9DB4', deep: '#EE4F78', ink: '#7A1F38' },
  { name: '복숭아', light: '#FFE3D1', base: '#FFB68F', deep: '#F07B3C', ink: '#7A3512' },
  { name: '레몬', light: '#FFF5C7', base: '#FFE276', deep: '#EDB20A', ink: '#6B5000' },
  { name: '청포도', light: '#E6F8CE', base: '#BCE791', deep: '#6DBB3C', ink: '#2F5A12' },
  { name: '민트', light: '#D3F6EA', base: '#93E4C9', deep: '#26B386', ink: '#0F5A43' },
  { name: '소다', light: '#D6EDFF', base: '#98CEFF', deep: '#3689F2', ink: '#0F3F7A' },
  { name: '블루베리', light: '#E0E4FF', base: '#ADB7FB', deep: '#5566E6', ink: '#232C7A' },
  { name: '포도', light: '#EDE0FF', base: '#C9A9F6', deep: '#8850DA', ink: '#42207A' },
  { name: '콜라', light: '#F2E1D5', base: '#D8AD91', deep: '#9C5D3A', ink: '#4F2A15' },
  { name: '우유', light: '#F4F6FA', base: '#D7DDE7', deep: '#7F8BA1', ink: '#3A4252' },
];
const MEMBER_COLORS = ['#FF6F93', '#3F74F0', '#26B386', '#F07B3C', '#8850DA', '#9C5D3A'];
// The jelly lettering, the same choices as the app's settings (FontChoice there).
const FONTS = [
  { id: 'NANUM_ROUND', name: '동글', family: '"NanumSquareRound", "Pretendard Variable", Pretendard, system-ui, sans-serif', weight: 800 },
  { id: 'JUA', name: '말랑', family: '"Jua", "Pretendard Variable", Pretendard, system-ui, sans-serif', weight: 400 },
  { id: 'CLEAN', name: '깔끔', family: '"Pretendard Variable", Pretendard, system-ui, sans-serif', weight: 700 },
  { id: 'ROUND', name: '통통', family: '"Bagel Fat One", "Pretendard Variable", Pretendard, system-ui, sans-serif', weight: 900 },
  { id: 'SYSTEM', name: '휴대폰 글꼴', family: 'system-ui, -apple-system, "Apple SD Gothic Neo", sans-serif', weight: 800 },
];
// Lengths: 10-minute steps up to two hours, then 30-minute steps up to twelve (as in the app).
const SHORT_MAX = 120;
const MAX_DURATION = 720;
const MAX_PINNED = 3;
const LENGTH_TICKS = [[30, '30분'], [60, '1시간'], [120, '2시간'], [360, '6시간'], [720, '12시간']];

// The hidden golden jelly, as in the app (GoldenJelly.kt): grab one jelly in the box and let it go
// 50 times without a break. Who finders are sent to is written here and in the app (GoldenDialog.kt).
const MAKER = '‘대 AI 시대의 딸깍 개발자’ 황창현';
const GOLDEN_GRABS = 50;
const GOLDEN_HINT = 25;
const GOLDEN_PATIENCE_MS = 3000;
const CODE_LETTERS = '23456789ABCDEFGHJKMNPQRSTVWXYZ';
const DAY_NAMES = ['월', '화', '수', '목', '금', '토', '일'];

// ---------------------------------------------------------------- state

const saved = {
  get(key) {
    try {
      return localStorage.getItem(`jellyShare.${key}`);
    } catch {
      return null;
    }
  },
  set(key, value) {
    try {
      if (value == null) localStorage.removeItem(`jellyShare.${key}`);
      else localStorage.setItem(`jellyShare.${key}`, value);
    } catch {
      // Private browsing: nothing is remembered, which only costs a re-join.
    }
  },
};

function readJson(text, fallback) {
  try {
    return text ? JSON.parse(text) : fallback;
  } catch {
    return fallback;
  }
}

const inApp = /JellyCalendarApp/.test(navigator.userAgent);
const isIos = /iPhone|iPad|iPod/.test(navigator.userAgent);
const standalone = window.matchMedia?.('(display-mode: standalone)').matches || navigator.standalone === true;
// Inside the Android app: the app's side of the page (see MainActivity.ShareBridge).
const bridge = window.JellyBridge || null;

const savedColor = Number.parseInt(saved.get('color') ?? '', 10);

/** The character of someone who has not picked one yet: the plain smiling face. */
const FIRST_LOOK = { job: 'face', v: 0 };

/** The character kept on this phone: null for a plain jelly ("민무늬"). */
function savedLook() {
  const raw = saved.get('look');
  if (raw == null) return FIRST_LOOK;
  if (raw === 'none') return null;
  return cleanLook(readJson(raw, null)) ?? FIRST_LOOK;
}

const state = {
  uid: null,
  myName: saved.get('name') || '',
  // My colour (a flavour index): my name's circle, and the colour my new jellies start with.
  myColor: savedColor >= 0 && savedColor < 10 ? savedColor : Math.floor(Math.random() * 10),
  // My jelly character ({ job, v }, or null for a plain jelly), the same in every calendar.
  myLook: savedLook(),
  spaceId: saved.get('space'),
  space: null,
  members: [],
  jellies: [],
  month: firstOfMonth(new Date()),
  selected: todayIso(),
  // The shared calendar as a month ('month') or as one day's box ('box').
  view: saved.get('view') === 'box' ? 'box' : 'month',
  // The same choice in the app's "모두" tab, kept apart (it starts as the box).
  allView: saved.get('allView') === 'month' ? 'month' : 'box',
  // Finished jellies of the day are folded away until asked for.
  showDone: false,
  // Set by the Android app: { mode: 'shared' } or { mode: 'all', date, personal, doubleTap, longPress }.
  host: null,
  sheet: null, // { kind: 'jelly' | 'new' | 'add' | 'invite' | 'menu' | 'name' | 'golden', ... }
  memos: [],
  online: navigator.onLine,
  font: null,
  // Jellies (box keys) that turned to gold here, and the grabs counted towards the next one.
  goldenKeys: new Set(readJson(saved.get('golden.keys'), [])),
  streak: { key: null, count: 0, at: 0 },
};
const subs = { space: null, members: null, jellies: null, jelly: null, memos: null };
let ready = false;
let screen = null; // what #app shows: 'welcome' | 'legacy' | 'month' | 'shared-box' | 'all'
let pendingCode = '';
// Paints the welcome page's colour and character again (after a character was picked on a sheet).
let welcomeRepaint = null;
let testMode = false;

// ---------------------------------------------------------------- dates

function pad(n) {
  return String(n).padStart(2, '0');
}

function isoDay(d) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

function todayIso() {
  return isoDay(new Date());
}

function parseDay(s) {
  const [y, m, d] = s.split('-').map(Number);
  return new Date(y, m - 1, d);
}

function firstOfMonth(d) {
  return new Date(d.getFullYear(), d.getMonth(), 1);
}

function addDays(d, n) {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);
}

/** Weeks start on Sunday, as Korean wall calendars do, unless the app is set to Monday. */
function sundayFirst() {
  return state.host ? state.host.sundayFirst !== false : true;
}

/** Six weeks covering the month. */
function gridDays(month) {
  const offset = sundayFirst() ? month.getDay() : (month.getDay() + 6) % 7;
  const start = addDays(month, -offset);
  return Array.from({ length: 42 }, (_, i) => addDays(start, i));
}

function weekdayNames() {
  return sundayFirst() ? ['일', ...DAY_NAMES.slice(0, 6)] : DAY_NAMES;
}

function dayName(d) {
  return DAY_NAMES[(d.getDay() + 6) % 7];
}

function dayTitle(iso) {
  const d = parseDay(iso);
  return `${d.getMonth() + 1}월 ${d.getDate()}일 ${dayName(d)}요일`;
}

function hm(min) {
  return `${pad(Math.floor(min / 60))}:${pad(min % 60)}`;
}

function durationText(min) {
  if (min < 60) return `${min}분`;
  if (min % 60 === 0) return `${min / 60}시간`;
  return `${Math.floor(min / 60)}시간 ${min % 60}분`;
}

function timeText(j) {
  if (j.start == null) return durationText(j.duration);
  return `${hm(j.start)}–${hm(Math.min(j.start + j.duration, 1440))} · ${durationText(j.duration)}`;
}

function agoText(ts) {
  if (!ts?.toMillis) return '방금';
  const s = Math.max(0, (Date.now() - ts.toMillis()) / 1000);
  if (s < 60) return '방금';
  if (s < 3600) return `${Math.floor(s / 60)}분 전`;
  if (s < 86400) return `${Math.floor(s / 3600)}시간 전`;
  const d = ts.toDate();
  return `${d.getMonth() + 1}월 ${d.getDate()}일`;
}

// ---------------------------------------------------------------- DOM helpers

function h(tag, attrs, ...children) {
  const el = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs || {})) {
    if (value == null || value === false) continue;
    if (key === 'class') el.className = value;
    else if (key === 'vars') for (const [k, v] of Object.entries(value)) el.style.setProperty(`--${k}`, v);
    else if (key === 'style') Object.assign(el.style, value);
    else if (key.startsWith('on')) el.addEventListener(key.slice(2).toLowerCase(), value);
    else if (value === true) el.setAttribute(key, '');
    else el.setAttribute(key, value);
  }
  for (const child of children.flat(Infinity)) {
    if (child == null || child === false) continue;
    el.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return el;
}

const GOLD = { name: '황금', light: '#FFF3C4', base: '#FFD24D', deep: '#D99A00', ink: '#5C3B00' };

function flavorVars(i) {
  const f = i === GOLDEN_FLAVOR ? GOLD : FLAVORS[((i % FLAVORS.length) + FLAVORS.length) % FLAVORS.length];
  return { light: f.light, base: f.base, deep: f.deep, ink: f.ink };
}

let toastTimer = 0;
function toast(text) {
  document.querySelector('.toast')?.remove();
  const el = h('div', { class: 'toast', role: 'status' }, text);
  document.body.append(el);
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.remove(), 2600);
}

let asking = null;

/**
 * Asks a yes-or-no question on the page itself. The app's web view shows no browser dialogs
 * (a confirm there quietly answers "no"), so every "are you sure?" goes through this instead.
 */
function askYesNo(message, { yes = '네', no = '아니요', danger = false } = {}) {
  asking?.(false);
  return new Promise((resolve) => {
    const scrim = h('div', { class: 'ask-scrim' });
    const done = (answer) => {
      if (asking !== done) return;
      asking = null;
      scrim.remove();
      resolve(answer);
    };
    asking = done;
    const noButton = h('button', { class: 'btn ghost', 'data-testid': 'ask-no', onClick: () => done(false) }, no);
    scrim.append(
      h('div', { class: 'ask', role: 'alertdialog', 'aria-modal': 'true', 'data-testid': 'ask', onClick: (e) => e.stopPropagation() },
        h('p', { class: 'ask-text' }, message),
        h('div', { class: 'ask-buttons' },
          noButton,
          h('button', { class: danger ? 'btn danger' : 'btn', 'data-testid': 'ask-yes', onClick: () => done(true) }, yes))));
    scrim.addEventListener('click', () => done(false));
    document.body.append(scrim);
    noButton.focus({ preventScroll: true });
  });
}

const appEl = () => document.getElementById('app');

// ---------------------------------------------------------------- members

// The people of the calendar on screen by default; "모두" passes another calendar's people.
function memberIndex(uid, members = state.members) {
  const i = members.findIndex((m) => m.uid === uid);
  return i < 0 ? members.length : i;
}

/** The colour someone picked, as a flavour index, or null when they have not picked one. */
function memberFlavor(uid, members = state.members) {
  const color = members.find((m) => m.uid === uid)?.color;
  return Number.isInteger(color) && color >= 0 && color < FLAVORS.length ? color : null;
}

function memberColor(uid, members = state.members) {
  const flavor = memberFlavor(uid, members);
  return flavor == null ? MEMBER_COLORS[memberIndex(uid, members) % MEMBER_COLORS.length] : FLAVORS[flavor].deep;
}

/** Letters on a member's colour: dark on the light lemon, white on the rest. */
function memberInk(uid, members = state.members) {
  return memberFlavor(uid, members) === 2 ? FLAVORS[2].ink : '#FFFFFF';
}

/** The name someone goes by in the calendar, also for oneself. */
function realName(uid, fallback, members = state.members) {
  return members.find((m) => m.uid === uid)?.name || fallback || '알 수 없음';
}

/** "나" for oneself, otherwise the person's name. */
function memberName(uid, fallback) {
  return uid === state.uid ? '나' : realName(uid, fallback);
}

/** "내가" for oneself, otherwise the bare name: "내가 올림", "창현 올림". */
function subject(uid, fallback, members = state.members) {
  return uid === state.uid ? '내가' : realName(uid, fallback, members);
}

/** The light colour of someone's jelly character. */
function memberBody(uid, members = state.members) {
  const flavor = memberFlavor(uid, members);
  return flavor == null ? MEMBER_COLORS[memberIndex(uid, members) % MEMBER_COLORS.length] : FLAVORS[flavor].base;
}

/** Someone's character, or null for a plain jelly or a profile from before characters. */
function memberLook(uid, members = state.members) {
  return cleanLook(members.find((m) => m.uid === uid)?.look);
}

function avatar(uid, fallback, small = false, members = state.members) {
  const name = realName(uid, fallback, members);
  const look = memberLook(uid, members);
  if (look) {
    return h('span', { class: `avatar look-avatar${small ? ' small' : ''}`, title: name, 'data-look': `${look.job}-${look.v}` },
      lookSvg(look, memberBody(uid, members), small ? 26 : 38));
  }
  return h(
    'span',
    { class: `avatar${small ? ' small' : ''}`, style: { background: memberColor(uid, members), color: memberInk(uid, members) }, title: name },
    Array.from(name || '?')[0],
  );
}

// ---------------------------------------------------------------- boot

async function boot() {
  const params = new URLSearchParams(location.search);
  const emulator = params.has('emu') && ['localhost', '127.0.0.1'].includes(location.hostname);
  testMode = emulator;
  const config = emulator
    ? { apiKey: 'demo-key', authDomain: 'localhost', projectId: 'demo-jelly', appId: 'demo-app' }
    : firebaseConfig;
  applyFont(saved.get('font'));
  readHost();
  if (inApp) reportViewport();
  if (!config) {
    appEl().replaceChildren(
      h('div', { class: 'center' },
        h('img', { class: 'logo', src: 'icons/icon-192.png', alt: '' }),
        h('div', { class: 'display', style: { fontSize: '22px', color: 'var(--text)' } }, '공유 젤리'),
        h('div', null, '공유 서버가 아직 연결되지 않았어요.'),
        h('div', null, '서버 설정이 끝나면 이 화면에서 바로 쓸 수 있어요.')),
    );
    return;
  }
  try {
    // Inside the app, Google sign-in goes through the app, so no pop-up helper frame is loaded.
    state.uid = await store.start(config, { emulator, popups: !inApp });
  } catch (e) {
    console.error(e);
    appEl().replaceChildren(
      h('div', { class: 'center' },
        h('div', { class: 'display', style: { fontSize: '20px', color: 'var(--text)' } }, '연결하지 못했어요'),
        h('div', null, '인터넷 연결을 확인한 뒤 다시 열어 주세요.'),
        h('button', { class: 'btn', onClick: () => location.reload() }, '다시 시도')),
    );
    return;
  }

  window.addEventListener('online', () => {
    state.online = true;
    render();
  });
  window.addEventListener('offline', () => {
    state.online = false;
    render();
  });
  window.addEventListener('popstate', () => {
    asking?.(false);
    if (!state.sheet) return;
    if (state.sheet.under && history.state?.sheet) showUnder();
    else hideSheet();
  });

  ready = true;
  pendingCode = codeFromLink();
  if (!state.uid) {
    // Nobody signed in on this phone yet: the welcome page asks for an account first.
    render();
    return;
  }
  const ids = savedSpaces();
  for (const id of ids) {
    const key = saved.get(`key.${id}`);
    if (key) await store.useSpaceKey(id, key).catch((e) => console.warn(e));
  }
  for (const id of ids) watchGroup(id);
  const current = ids.includes(state.spaceId) ? state.spaceId : ids[0];
  if (current) {
    // Already in a calendar: an invite link offers to join one more.
    const code = pendingCode;
    openSpace(current);
    if (code && !isAll()) openSheet({ kind: 'new-group', code });
  } else {
    render();
  }
}

/** "#c=ABCD2345" in the address, from an invite link. */
function codeFromLink() {
  const m = location.hash.match(/[#&]c=([0-9A-Za-z-]+)/);
  return m ? store.cleanCode(m[1]) : '';
}

// ---------------------------------------------------------------- inside the Android app

// The app tells the page what to show (the shared calendar, or everything on one day together with
// the phone's own jellies) and pokes it when that changes; the page then asks for the details.
window.jellyHost = {
  ...window.jellyHost,
  poke() {
    readHost();
    render();
  },
  /** The app signed in with Google and hands over the ID token. */
  async googleToken(token) {
    try {
      await store.signInWithGoogle(token);
      await afterGoogle(pendingCode);
    } catch (e) {
      console.error(e);
      toast(idError(e));
    }
  },
  googleFailed(reason) {
    if (reason !== 'cancelled') toast('구글 로그인을 하지 못했어요');
  },
};

function readHost() {
  let next = null;
  try {
    next = bridge?.hostState ? JSON.parse(bridge.hostState()) : null;
  } catch (e) {
    console.warn(e);
  }
  if (next && next.mode !== 'all') next = { ...next, mode: 'shared' };
  if (next?.mode === 'all' && !/^\d{4}-\d{2}-\d{2}$/.test(next.date || '')) next = { ...next, mode: 'shared' };
  const changedMode = (state.host?.mode || 'shared') !== (next?.mode || 'shared');
  const changedLook = !sameLook(state.host?.look, next?.look);
  state.host = next;
  if (next?.font) applyFont(next.font);
  // An app that keeps characters but has none yet takes the one picked here before.
  if (next && !('look' in next) && bridge?.setLook && !lookHandedOver) {
    lookHandedOver = true;
    bridge.setLook(JSON.stringify(state.myLook ?? PLAIN));
  }
  // A character picked in the app's settings goes into every calendar.
  if (changedLook) {
    for (const [id, g] of groups) keepLook(id, g.members.find((m) => m.uid === state.uid));
  }
  // A sheet from one tab should not stay open over the other one.
  if (changedMode && state.sheet) closeSheet();
}

function isAll() {
  return state.host?.mode === 'all';
}

/** Letters headings and jellies in the chosen face ([id] from FONTS; anything else: the first). */
function applyFont(id) {
  const font = FONTS.find((f) => f.id === id) || FONTS[0];
  if (state.font === font.id) return;
  state.font = font.id;
  const root = document.documentElement;
  root.style.setProperty('--display', font.family);
  root.style.setProperty('--display-weight', String(font.weight));
  root.dataset.font = font.id;
  setTitleFace(font.family, font.weight);
}

/** How the finishing gestures are set up in the app (both on in a browser). */
function gestures() {
  return {
    doubleTap: state.host?.doubleTap !== false,
    longPress: state.host?.longPress !== false,
    // Inside the app, pinned jellies sway only while "말랑말랑 숨쉬기" is on.
    sway: state.host?.wobble !== false,
  };
}

/** One line in the app's log, so a broken viewport inside the app's web view shows up in tests. */
function reportViewport() {
  requestAnimationFrame(() => {
    const probe = h('div', { style: { position: 'fixed', top: '0', left: '0', width: '1px', height: '100vh', visibility: 'hidden' } });
    document.body.append(probe);
    console.info(`jelly-share viewport ${innerWidth}x${innerHeight} vh100=${probe.getBoundingClientRect().height}`);
    probe.remove();
  });
}

// ---------------------------------------------------------------- welcome

// ---------------------------------------------------------------- accounts and the key vault

let vaultTimer = 0;

/** Writes this phone's calendar keys into the account's vault, a moment after they change. */
function syncVault() {
  const raw = saved.get('vault');
  const who = store.account();
  if (!raw || !who || who.kind === 'guest') return;
  clearTimeout(vaultTimer);
  vaultTimer = setTimeout(() => {
    const spaces = savedSpaces().map((id) => ({ id, key: saved.get(`key.${id}`) })).filter((x) => x.key);
    store.saveVault(raw, { spaces, profile: { name: state.myName, color: state.myColor, look: state.myLook } }, saved.get('vaultLock') || 'password')
      .catch((e) => console.warn('vault not saved', e));
  }, 300);
}

/** Opens the account's vault with [raw] and brings its calendars to this phone. */
async function restoreFromVault(raw, lock) {
  saved.set('vault', raw);
  saved.set('vaultLock', lock);
  let contents = null;
  try {
    contents = await store.readVault(raw);
  } catch (e) {
    console.warn(e);
    toast('열쇠 보관함을 열지 못했어요');
  }
  const kept = (Array.isArray(contents?.spaces) ? contents.spaces : [])
    .filter((x) => typeof x?.id === 'string' && typeof x?.key === 'string');
  for (const { id, key } of kept) {
    saved.set(`key.${id}`, key);
    await store.useSpaceKey(id, key).catch((e) => console.warn(e));
  }
  rememberSpaces([...new Set([...savedSpaces(), ...kept.map((x) => x.id)])]);
  const profile = contents?.profile;
  if (profile?.name) rememberProfile(profile.name, Number.isInteger(profile.color) ? profile.color : state.myColor);
  if (profile && 'look' in profile) rememberLook(profile.look);
  return kept.length;
}

/** Signed in (or a guest got an account): show the calendars, or the next welcome step. */
function enterAccount(code) {
  state.uid = store.uid();
  syncVault();
  const ids = savedSpaces();
  for (const id of ids) watchGroup(id);
  screen = null;
  if (ids.length) {
    openSpace(ids.includes(state.spaceId) ? state.spaceId : ids[0]);
    if (code && !isAll()) openSheet({ kind: 'new-group', code });
  } else {
    pendingCode = code || pendingCode;
    render();
  }
}

function idError(e) {
  const code = e?.code || '';
  if (code === 'auth/email-already-in-use' || code === 'auth/credential-already-in-use') return '이미 있는 아이디예요. 다른 아이디를 정하거나 로그인해 주세요';
  if (['auth/invalid-credential', 'auth/invalid-login-credentials', 'auth/wrong-password', 'auth/user-not-found'].includes(code)) return '아이디나 비밀번호가 맞지 않아요';
  if (code === 'auth/too-many-requests') return '여러 번 틀려서 잠시 막혔어요. 조금 뒤에 다시 해 주세요';
  if (code === 'auth/operation-not-allowed') return '아직 이 로그인 방법이 켜져 있지 않아요';
  if (code === 'auth/unauthorized-domain') return '이 주소에서는 아직 구글 로그인이 허용되지 않았어요. 아이디로 시작해 주세요';
  if (code === 'auth/popup-blocked') return '팝업이 막혔어요. 브라우저에서 팝업을 허용한 뒤 다시 눌러 주세요';
  if (code === 'auth/network-request-failed') return '인터넷 연결을 확인해 주세요';
  return '잠시 후 다시 해 주세요';
}

/** Google in a browser shows a pop-up; inside the Android app the app signs in and hands over a token. */
function canGoogle() {
  if (inApp) {
    try {
      return !!bridge?.googleAvailable?.();
    } catch {
      return false;
    }
  }
  return googleSignIn || testMode;
}

async function startGoogle(code) {
  pendingCode = code || pendingCode;
  if (inApp) {
    bridge.googleSignIn();
    return;
  }
  try {
    await store.signInWithGoogle();
    await afterGoogle(code);
  } catch (e) {
    console.error(e);
    if (e?.code !== 'auth/popup-closed-by-user' && e?.code !== 'auth/cancelled-popup-request') toast(idError(e));
  }
}

/** After Google: a vault locked with a vault password asks for it; otherwise carry on. */
async function afterGoogle(code) {
  closeSheet();
  const lock = await store.vaultLock().catch(() => null);
  if (lock === 'passphrase') {
    state.uid = store.uid();
    openSheet({ kind: 'vault-open', code });
    return;
  }
  saved.set('vaultLock', 'passphrase');
  enterAccount(code);
}

window.jellyHost = window.jellyHost || {};

/**
 * The fields of an account form: a login ID and a password, to make an account or sign in. [link]
 * is for a guest from before accounts (only making one, which keeps their calendars).
 */
function accountForm({ code = '', link = false } = {}) {
  let busy = false;
  const idBox = h('input', {
    class: 'input',
    style: { width: '100%' },
    placeholder: '아이디 (영문 소문자·숫자 4~20자)',
    maxlength: '20',
    autocapitalize: 'none',
    autocomplete: 'username',
    spellcheck: 'false',
    'data-testid': 'account-id',
  });
  const pwBox = h('input', {
    class: 'input',
    style: { width: '100%', marginTop: '8px' },
    type: 'password',
    placeholder: `비밀번호 (${store.MIN_PASSWORD}자 이상)`,
    autocomplete: link ? 'new-password' : 'current-password',
    'data-testid': 'account-password',
  });
  const read = () => {
    const id = store.cleanId(idBox.value);
    const pw = pwBox.value;
    if (!store.ID_PATTERN.test(id)) {
      toast('아이디는 영문 소문자나 숫자로 시작하는 4~20자로 정해 주세요 (. _ - 도 쓸 수 있어요)');
      idBox.focus();
      return null;
    }
    if (pw.length < store.MIN_PASSWORD) {
      toast(`비밀번호는 ${store.MIN_PASSWORD}자 이상으로 정해 주세요`);
      pwBox.focus();
      return null;
    }
    return { id, pw };
  };
  const run = async (button, work) => {
    const input = read();
    if (!input || busy) return;
    busy = true;
    const label = button.textContent;
    button.disabled = true;
    button.textContent = '확인하는 중…';
    try {
      await work(input);
    } catch (e) {
      console.error(e);
      toast(idError(e));
    } finally {
      busy = false;
      if (button.isConnected) {
        button.disabled = false;
        button.textContent = label;
      }
    }
  };
  const signUp = h('button', {
    class: 'btn',
    'data-testid': 'sign-up',
    onClick: (e) => run(e.currentTarget, async ({ id, pw }) => {
      const raw = await store.signUpWithId(id, pw);
      await restoreFromVault(raw, 'password');
      closeSheet();
      enterAccount(code);
      toast(link ? '계정을 만들었어요. 새 휴대폰에서도 로그인하면 달력이 열려요' : '계정을 만들었어요');
    }),
  }, link ? '계정 만들기' : '새로 만들기');
  const signIn = link ? null : h('button', {
    class: 'btn ghost',
    'data-testid': 'sign-in',
    onClick: (e) => run(e.currentTarget, async ({ id, pw }) => {
      const raw = await store.signInWithId(id, pw);
      const restored = await restoreFromVault(raw, 'password');
      enterAccount(code);
      if (restored) toast(`쓰던 공유 달력 ${restored}개를 열었어요`);
    }),
  }, '로그인');
  return h('div', { class: 'account-form' },
    canGoogle()
      ? [
          h('button', { class: 'btn block google-btn', 'data-testid': 'google', onClick: () => startGoogle(code) },
            h('span', { class: 'google-g', 'aria-hidden': 'true' }, 'G'), link ? '구글 계정 연결하기' : '구글로 시작하기'),
          h('div', { class: 'or' }, '또는 아이디로'),
        ]
      : null,
    idBox,
    pwBox,
    h('div', { class: 'row account-buttons' }, signUp, signIn),
    h('p', { class: 'account-note' }, '아이디는 로그인에만 쓰이고, 함께 쓰는 사람에게는 보이지 않아요. 달력에서 보이는 이름은 따로 정해요.'),
    h('p', { class: 'account-note' }, '비밀번호를 잊으면 되찾을 수 없어요. 그때는 함께 쓰는 사람에게 초대 코드를 다시 받으면 돼요.'),
  );
}

/** Makes a calendar named [groupName] with me in it under [profile], and shows it. */
async function createGroup(groupName, profile) {
  rememberProfile(profile.name, profile.color);
  const { spaceId, key } = await store.createSpace(groupName || '공유 젤리 달력', { ...profile, look: myLook() });
  saved.set(`key.${spaceId}`, key);
  openSpace(spaceId);
}

/** Joins the calendar of an invite code under [profile], and shows it. */
async function joinGroup(code, profile) {
  rememberProfile(profile.name, profile.color);
  const { spaceId, key } = await store.joinWithCode(code, { ...profile, look: myLook() });
  saved.set(`key.${spaceId}`, key);
  openSpace(spaceId);
}

function joinError(err) {
  return err.message === 'expired-invite'
    ? '기간이 지난 초대 코드예요. 새 코드를 받아 주세요'
    : '초대 코드를 찾지 못했어요. 코드를 다시 확인해 주세요';
}

function renderWelcome(code = '') {
  closeSubs();
  leaveBox();
  screen = 'welcome';
  document.body.classList.remove('fill', 'has-bar');
  // Nobody signed in, or someone from before accounts with no calendar left: an account first.
  const who = store.account();
  if (!who || (who.kind === 'guest' && !savedSpaces().length)) {
    appEl().replaceChildren(
      h('div', { class: 'welcome', 'data-testid': 'account-step' },
        h('img', { class: 'logo', src: 'icons/icon-192.png', alt: '' }),
        h('h1', null, '공유 젤리'),
        h('p', null, code ? '초대받은 달력에 들어가기 전에 내 계정부터 만들어요.' : '함께 보고 고치는 젤리 달력이에요. 먼저 내 계정을 만들거나 로그인해 주세요.'),
        accountForm({ code }),
        h('p', { class: 'privacy-note' }, PRIVACY_LINE, ' ', h('a', { href: 'privacy.html', class: 'link-btn', 'data-testid': 'privacy' }, '개인정보 안내')),
        installHint(),
      ),
    );
    return;
  }
  let busy = false;
  let color = state.myColor;
  const name = h('input', {
    class: 'input',
    placeholder: '내 이름 (함께 쓰는 사람에게 보여요)',
    maxlength: '20',
    value: state.myName,
    'data-testid': 'name',
  });
  const colors = h('div', { class: 'row' });
  const lookBox = h('div');
  const paintColors = () => {
    colors.replaceChildren(...colorSwatches(color, (i) => {
      color = i;
      paintColors();
    }));
    lookBox.replaceChildren(lookRow(color, () => openSheet({ kind: 'look', color })));
  };
  paintColors();
  welcomeRepaint = paintColors;
  const codeBox = h('input', {
    class: 'input code-input',
    placeholder: 'ABCDE-23456',
    maxlength: String(store.CODE_LENGTH + 1),
    autocapitalize: 'characters',
    autocomplete: 'off',
    value: code ? store.prettyCode(code) : '',
    'data-testid': 'code',
  });

  function needName() {
    const n = name.value.trim();
    if (!n) {
      toast('이름을 먼저 적어 주세요');
      name.focus();
      return null;
    }
    return n;
  }

  async function create() {
    const n = needName();
    if (!n || busy) return;
    busy = true;
    try {
      await createGroup('공유 젤리 달력', { name: n, color });
      toast('공유 달력을 만들었어요. 오른쪽 위 ⋯에서 함께 쓸 사람을 초대해 보세요');
    } catch (e) {
      console.error(e);
      toast('만들지 못했어요. 잠시 후 다시 해 주세요');
    } finally {
      busy = false;
    }
  }

  async function join(e) {
    const n = needName();
    if (!n || busy) return;
    const c = store.cleanCode(codeBox.value);
    if (c.length !== store.CODE_LENGTH) {
      toast(`초대 코드 ${store.CODE_LENGTH}자리를 넣어 주세요`);
      codeBox.focus();
      return;
    }
    busy = true;
    const button = e?.currentTarget;
    const label = button?.textContent;
    if (button) {
      button.disabled = true;
      button.textContent = '코드 확인하는 중…';
    }
    try {
      await joinGroup(c, { name: n, color });
      toast('공유 달력에 들어왔어요');
    } catch (err) {
      console.error(err);
      toast(joinError(err));
    } finally {
      busy = false;
      if (button?.isConnected) {
        button.disabled = false;
        button.textContent = label;
      }
    }
  }

  appEl().replaceChildren(
    h('div', { class: 'welcome' },
      h('img', { class: 'logo', src: 'icons/icon-192.png', alt: '' }),
      h('h1', null, '공유 젤리'),
      h('p', null, '함께 보고 고치는 젤리 달력이에요.'),
      h('div', { class: 'field-label' }, '내 이름'),
      name,
      h('div', { class: 'field-label' }, '내 색 (내 캐릭터의 색이자 새 젤리의 기본 색)'),
      colors,
      h('div', { class: 'field-label' }, '내 캐릭터'),
      lookBox,
      code
        ? [
            h('div', { class: 'field-label' }, '받은 초대 코드'),
            codeBox,
            h('button', { class: 'btn block', onClick: join, 'data-testid': 'join' }, '초대받은 달력에 들어가기'),
          ]
        : [
            h('button', { class: 'btn block', style: { marginTop: '10px' }, onClick: create, 'data-testid': 'create' }, '새 공유 달력 만들기'),
            h('div', { class: 'or' }, '초대를 받았다면'),
            codeBox,
            h('button', { class: 'btn ghost block', onClick: join, 'data-testid': 'join' }, '초대 코드로 들어가기'),
          ],
      signedInLine(),
      h('p', { class: 'privacy-note' }, PRIVACY_LINE),
      installHint(),
    ),
  );
}

function signedInLine() {
  const who = store.account();
  if (!who || who.kind === 'guest') return null;
  return h('p', { class: 'account-note', 'data-testid': 'signed-in' },
    who.kind === 'id' ? `아이디 ‘${who.id}’로 로그인했어요 · ` : `구글 ${who.email}로 로그인했어요 · `,
    h('button', { class: 'link-btn', onClick: signOutHere }, '로그아웃'));
}

const PRIVACY_LINE = '이름, 색, 캐릭터, 젤리, 메모는 달력에 들어온 사람의 기기에서만 열리도록 암호화되어 저장돼요. 서버를 운영하는 사람도 내용을 볼 수 없어요.';

/** The ten flavours to pick a colour from. */
function colorSwatches(current, onPick) {
  return FLAVORS.map((f, i) => h('button', {
    class: `swatch${current === i ? ' on' : ''}`,
    type: 'button',
    'aria-label': f.name,
    'aria-pressed': current === i ? 'true' : 'false',
    style: { background: f.base },
    onClick: () => onPick(i),
    'data-testid': `color-${i}`,
  }));
}

function rememberProfile(n, color) {
  state.myName = n;
  state.myColor = color;
  saved.set('name', n);
  saved.set('color', String(color));
}

/** Inside an app that keeps my character in its settings (newer versions hand it over). */
function appKeepsLook() {
  return inApp && !!state.host && 'look' in state.host;
}

/** My character: the one in the app's settings when the app keeps it, else the one picked here. */
function myLook() {
  return appKeepsLook() ? cleanLook(state.host.look) : state.myLook;
}

// How the app is told about a plain jelly (an unknown job is plain everywhere).
const PLAIN = { job: 'none', v: 0 };

function rememberLook(look) {
  state.myLook = cleanLook(look);
  saved.set('look', state.myLook ? JSON.stringify(state.myLook) : 'none');
}

/** I picked a character: it goes into every calendar I am in (inside the app, the app keeps it). */
function pickLook(look) {
  const clean = cleanLook(look);
  if (inApp && bridge?.setLook) {
    bridge.setLook(JSON.stringify(clean ?? PLAIN));
    return;
  }
  rememberLook(clean);
  syncVault();
  for (const [id, g] of groups) {
    const mine = g.members.find((m) => m.uid === state.uid);
    if (mine?.name && !sameLook(mine.look, clean)) {
      store.updateProfile(id, { name: mine.name, color: mine.color, look: clean }).catch((e) => console.warn(e));
    }
  }
}

// Calendars whose profile is being given my character right now.
const lookWrites = new Set();
// The character picked here was handed to the app once.
let lookHandedOver = false;

/**
 * Keeps my character the same in every calendar. Inside the app the app's choice wins. Here, a
 * profile from before characters gets this phone's, and the calendar on screen tells this phone
 * about one picked on another phone.
 */
function keepLook(id, mine) {
  if (!mine?.name || lookWrites.has(id)) return;
  if (!appKeepsLook() && mine.look !== undefined) {
    if (id === state.spaceId && !sameLook(mine.look, state.myLook)) rememberLook(mine.look);
    return;
  }
  const want = myLook();
  if (mine.look !== undefined && sameLook(mine.look, want)) return;
  lookWrites.add(id);
  store.updateProfile(id, { name: mine.name, color: mine.color, look: want })
    .catch((e) => console.warn(e))
    .finally(() => lookWrites.delete(id));
}

// ---------------------------------------------------------------- space

// ---------------------------------------------------------------- calendars ("공유 달력"), several per person

const MAX_GROUPS = 5;
/** spaceId → { id, space, members, jellies, unsubs }: every calendar this device is in, kept live. */
const groups = new Map();
// Calendars being left or deleted from this phone on purpose: their watchers keep quiet meanwhile.
const leaving = new Set();

function savedSpaces() {
  const list = readJson(saved.get('spaces'), null);
  if (Array.isArray(list)) return list.filter((x) => typeof x === 'string');
  // Saved before there could be more than one.
  const one = saved.get('space');
  return one ? [one] : [];
}

function rememberSpaces(ids) {
  saved.set('spaces', JSON.stringify(ids));
}

function groupUsable(g) {
  return !!g?.space && !g.space.legacy && !g.space.locked;
}

/** The calendars whose jellies "모두" shows: all of them; elsewhere the one on screen. */
function shownGroups() {
  if (isAll()) return [...groups.values()].filter(groupUsable);
  const g = groups.get(state.spaceId);
  return groupUsable(g) ? [g] : [];
}

function groupName(g) {
  return g?.space?.name || '공유 젤리 달력';
}

/** Keeps the calendar on screen in the plain state fields the rest of the page reads. */
function syncCurrent(id) {
  if (id !== state.spaceId) return;
  const g = groups.get(id);
  state.space = g?.space ?? null;
  state.members = g?.members ?? [];
  state.jellies = g?.jellies ?? [];
}

function watchGroup(id) {
  if (groups.has(id)) return groups.get(id);
  const g = { id, space: null, members: [], jellies: [], unsubs: [] };
  groups.set(id, g);
  const lost = (e) => {
    if (leaving.has(id)) return;
    console.warn(e);
    // Let go by the owner, or the calendar is gone: this device stops using it.
    if (e?.code === 'permission-denied') forgetSpace('이 공유 달력에 더 이상 들어갈 수 없어요', id);
  };
  g.unsubs.push(store.watchSpace(id, (space) => {
    if (leaving.has(id)) return;
    if (!space) return forgetSpace('공유 달력이 지워졌어요', id);
    if (space.locked) return forgetSpace('이 기기에 달력 열쇠가 없어요. 초대 코드를 다시 받아 주세요', id);
    g.space = space;
    syncCurrent(id);
    render();
  }, lost));
  g.unsubs.push(store.watchMembers(id, (members) => {
    g.members = members;
    syncCurrent(id);
    const mine = members.find((m) => m.uid === state.uid);
    if (id === state.spaceId && mine?.name && (mine.name !== state.myName || mine.color !== state.myColor)) {
      rememberProfile(mine.name, Number.isInteger(mine.color) ? mine.color : state.myColor);
    }
    keepLook(id, mine);
    render();
    if (id === state.spaceId) state.sheet?.repaint?.();
  }, lost));
  g.unsubs.push(store.watchJellies(id, (jellies) => {
    g.jellies = jellies;
    syncCurrent(id);
    render();
  }, (e) => console.warn(e)));
  return g;
}

function unwatchGroup(id) {
  const g = groups.get(id);
  if (!g) return;
  for (const off of g.unsubs) off?.();
  groups.delete(id);
}

function closeSubs() {
  for (const key of Object.keys(subs)) {
    subs[key]?.();
    subs[key] = null;
  }
}

/** Shows the calendar [id] (and keeps it in this device's list). */
function openSpace(id) {
  const ids = savedSpaces();
  if (!ids.includes(id)) {
    rememberSpaces([...ids, id]);
    syncVault();
  }
  if (state.spaceId !== id) {
    subs.jelly?.();
    subs.memos?.();
    subs.jelly = subs.memos = null;
  }
  state.spaceId = id;
  saved.set('space', id);
  // An invite code in the address is used up once we are in.
  if (location.hash) history.replaceState(history.state, '', location.pathname + location.search);
  pendingCode = '';
  watchGroup(id);
  syncCurrent(id);
  render();
}

/**
 * Leaves or deletes the calendar [id] on purpose and lets go of it here with [message], so the person
 * is not told "공유 달력이 지워졌어요" by its own watchers on the way out. Throws when [work] fails.
 */
async function goOut(id, work, message) {
  leaving.add(id);
  try {
    await work();
    forgetSpace(message, id);
  } finally {
    leaving.delete(id);
  }
}

/** This device stops using the calendar [id] (after leaving it, deleting it, or being let go). */
function forgetSpace(message, id = state.spaceId) {
  if (!id) return;
  unwatchGroup(id);
  saved.set(`key.${id}`, null);
  store.forgetSpaceKey(id);
  const rest = savedSpaces().filter((x) => x !== id);
  rememberSpaces(rest);
  syncVault();
  if (id === state.spaceId) {
    if (state.sheet) hideSheet();
    state.spaceId = null;
    state.space = null;
    state.members = [];
    state.jellies = [];
    saved.set('space', null);
    screen = null;
    if (rest[0]) openSpace(rest[0]);
    else render();
  } else {
    render();
  }
  if (message) toast(message);
}

/** Shows the month of an ISO day with that day selected. In the app's "모두" tab the app picks the day. */
function showDay(iso) {
  if (!isAll()) {
    const d = parseDay(iso);
    state.selected = iso;
    if (d.getMonth() !== state.month.getMonth() || d.getFullYear() !== state.month.getFullYear()) {
      state.month = firstOfMonth(d);
    }
  }
  render();
}

function shiftMonth(delta) {
  state.month = new Date(state.month.getFullYear(), state.month.getMonth() + delta, 1);
  const sel = parseDay(state.selected);
  if (sel.getMonth() !== state.month.getMonth() || sel.getFullYear() !== state.month.getFullYear()) {
    const now = new Date();
    const thisMonth = state.month.getMonth() === now.getMonth() && state.month.getFullYear() === now.getFullYear();
    state.selected = isoDay(thisMonth ? now : state.month);
  }
  render();
  toTop();
}

function goToday() {
  if (isAll()) {
    bridge?.showDay?.(todayIso());
    toTop();
    return;
  }
  state.month = firstOfMonth(new Date());
  state.selected = todayIso();
  render();
  toTop();
}

/**
 * Back to the top of the page, where the month starts: after "오늘" or turning the month while
 * scrolled down to the day's list, the page would otherwise stay down there.
 */
function toTop() {
  if (window.scrollY <= 0) return;
  const calm = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
  requestAnimationFrame(() => window.scrollTo({ top: 0, behavior: calm ? 'auto' : 'smooth' }));
}

/** "달력" or "상자": the shared tab and the app's "모두" tab each remember their own. */
function currentView() {
  return isAll() ? state.allView : state.view;
}

function setView(view) {
  if (currentView() === view) return;
  if (isAll()) {
    state.allView = view;
    saved.set('allView', view);
  } else {
    state.view = view;
    saved.set('view', view);
  }
  render();
}

/** The day the page is about: picked here, or the app's day in the "모두" tab. */
function selectedDay() {
  return isAll() ? state.host.date : state.selected;
}

/** The phone's own jellies the app handed over (the six weeks around its day). */
function hostPersonal() {
  return (Array.isArray(state.host?.personal) ? state.host.personal : []).filter((p) => p && p.id && p.title != null);
}

/** Everything on [iso] as one list: the phone's own jellies (in "모두") and the shared ones. */
function dayItems(iso) {
  const mine = isAll() ? hostPersonal().filter((p) => !p.date || p.date === iso).map((p) => ({
    kind: 'personal', id: p.id, title: p.title, flavor: p.flavor ?? 0, done: !!p.done, pinned: !!p.pinned,
    start: p.start ?? null, duration: p.duration || 30, p,
  })) : [];
  const shared = shownGroups().flatMap((g) => g.jellies.filter((j) => j.date === iso).map((j) => ({
    kind: 'shared', id: j.id, title: j.title, flavor: j.flavor, done: !!j.done, pinned: !!j.pinned,
    start: j.start ?? null, duration: j.duration, j, group: g,
  })));
  return [...mine, ...shared].sort((a, b) => (b.pinned - a.pinned) || (a.start ?? 2000) - (b.start ?? 2000));
}

function jelliesOn(iso) {
  return state.jellies
    .filter((j) => j.date === iso)
    .sort((a, b) => (a.start ?? 2000) - (b.start ?? 2000) || (a.createdAt?.toMillis?.() ?? Infinity) - (b.createdAt?.toMillis?.() ?? Infinity));
}

// ---------------------------------------------------------------- main view

/** Draws whatever the page should show now. Safe to call often: box views are updated in place. */
function render() {
  if (!ready) return;
  renderScreen();
  // An open day's sheet follows the changes too.
  if (state.sheet?.kind === 'day') buildDaySheet();
}

function renderScreen() {
  if (isAll()) {
    if (state.allView === 'month') renderMonth();
    else renderAll();
    return;
  }
  if (!state.spaceId) {
    if (screen !== 'welcome') renderWelcome(pendingCode);
    return;
  }
  if (state.space?.legacy) {
    if (screen !== 'legacy') renderLegacy();
    return;
  }
  if (state.view === 'box') renderSharedBox();
  else renderMonth();
}

/** True when the calendar is open and readable on this device. */
function usable() {
  return !!state.spaceId && !!state.space && !state.space.legacy && !state.space.locked;
}

function membersButton() {
  // Room for three circles; more people show as "+N", so the title keeps its line on small phones.
  const shown = state.members.slice(0, 3);
  const rest = state.members.length - shown.length;
  return h('button', { class: 'members', 'aria-label': '함께 쓰는 사람과 메뉴', onClick: () => openSheet({ kind: 'menu' }), 'data-testid': 'menu' },
    shown.map((m) => avatar(m.uid, m.name)),
    rest > 0 ? h('span', { class: 'avatar more-people' }, `+${rest}`) : null,
    h('span', { class: 'icon-btn', style: { width: '32px', fontSize: '20px' } }, '⋯'));
}

/** A calendar made before encryption: it can only be cleared away. */
function renderLegacy() {
  leaveBox();
  screen = 'legacy';
  document.body.classList.remove('fill');
  const mine = state.space.owner === state.uid;
  appEl().replaceChildren(
    h('div', { class: 'center', 'data-testid': 'legacy' },
      h('div', { class: 'display', style: { fontSize: '20px', color: 'var(--text)' } }, '공유 방식이 바뀌었어요'),
      h('div', null, '이제 공유 젤리는 달력에 들어온 사람의 기기에서만 열리도록 암호화돼요. 예전 방식으로 만든 이 달력은 더 쓸 수 없어요.'),
      h('button', {
        class: 'btn',
        'data-testid': 'clear-legacy',
        onClick: async (e) => {
          const button = e.currentTarget;
          const id = state.spaceId;
          const sure = await askYesNo(mine ? '예전 달력과 그 안의 젤리를 서버에서 지울까요?' : '예전 달력에서 나갈까요?', {
            yes: mine ? '지우기' : '나가기',
            danger: true,
          });
          if (!sure) return;
          button.disabled = true;
          const message = mine ? '예전 달력을 지웠어요. 새 달력을 만들어 다시 초대해 주세요' : '예전 달력에서 나왔어요';
          await goOut(id, () => (mine ? store.deleteSpace(id) : store.removeMember(id, state.uid)), message).catch((err) => {
            console.error(err);
            forgetSpace(message, id);
          });
        },
      }, mine ? '예전 달력 지우고 새로 시작' : '예전 달력에서 나가기')),
  );
}

function header({ title, sub, testid, prev, next, prevLabel, nextLabel, switcher = false }) {
  return h('div', { class: 'header' },
    h('button', { class: 'icon-btn', 'aria-label': prevLabel, onClick: prev }, '‹'),
    h('div', { class: 'head-text' },
      h('div', { class: 'month-title', 'data-testid': testid }, title),
      switcher
        ? h('button', { class: 'space-name space-switch', onClick: () => openSheet({ kind: 'groups' }), 'data-testid': 'groups' }, sub, ' ▾')
        : h('div', { class: 'space-name' }, sub)),
    h('button', { class: 'icon-btn', 'aria-label': nextLabel, onClick: next }, '›'),
    usable() ? membersButton() : null);
}

/**
 * The bar at the bottom of every screen, as in the app: back to today on the left, 달력 or 상자 in
 * the middle, and + on the right.
 */
function bottomBar() {
  const view = currentView();
  const tab = (v, label) => h('button', {
    class: `seg-btn${view === v ? ' on' : ''}`,
    role: 'tab',
    'aria-selected': view === v ? 'true' : 'false',
    onClick: () => setView(v),
    'data-testid': `view-${v}`,
  }, label);
  return h('nav', { class: 'bottom-bar', 'data-testid': 'bottom-bar' },
    h('button', { class: 'today-btn squish', onClick: goToday, 'data-testid': 'today', 'aria-label': '오늘로' },
      h('span', { class: 'today-leaf' }, String(new Date().getDate())),
      h('span', null, '오늘')),
    h('div', { class: 'seg', role: 'tablist' }, tab('month', '달력'), tab('box', '상자')),
    h('button', { class: 'jelly add-fab squish', vars: flavorVars(0), 'aria-label': '젤리 올리기', onClick: () => addJelly(), 'data-testid': 'add' }, '+'));
}

/** "+": a shared jelly on [date] (the day shown); in "모두", first the choice between mine and shared. */
function addJelly(date = selectedDay()) {
  if (!date) return;
  if (!isAll()) openSheet({ kind: 'new', date });
  else if (shownGroups().length) openSheet({ kind: 'add', date });
  else bridge?.createPersonal?.(date);
}

function offlineBanner() {
  return state.online ? null : h('div', { class: 'banner offline' }, '오프라인이에요. 고친 내용은 연결되면 자동으로 올라가요.');
}

function leaveBox() {
  if (!boxView) return;
  boxView.box.destroy();
  boxView = null;
  if (testMode) window.__jellyBox = null;
}

/** A month of jellies: the shared ones, and in the app's "모두" tab the phone's own as well. */
function renderMonth() {
  leaveBox();
  const all = isAll();
  screen = all ? 'all-month' : 'month';
  document.body.classList.remove('fill');
  document.body.classList.add('has-bar');
  const selected = selectedDay();
  const month = all ? firstOfMonth(parseDay(selected)) : state.month;
  const today = todayIso();
  // In "모두" the app owns the day: the page asks it to move.
  const pick = (iso) => {
    if (all) {
      bridge?.showDay?.(iso);
      return;
    }
    const d = parseDay(iso);
    state.selected = iso;
    if (d.getMonth() !== month.getMonth()) shiftMonth(d < month ? -1 : 1);
    else render();
  };
  const moveMonth = (delta) => {
    if (!all) {
      shiftMonth(delta);
      return;
    }
    const d = parseDay(selected);
    const target = new Date(d.getFullYear(), d.getMonth() + delta, 1);
    const last = new Date(target.getFullYear(), target.getMonth() + 1, 0).getDate();
    pick(isoDay(new Date(target.getFullYear(), target.getMonth(), Math.min(d.getDate(), last))));
  };

  const top = header({
    title: `${month.getFullYear()}년 ${month.getMonth() + 1}월`,
    sub: all ? '내 젤리와 공유 젤리' : groupName(groups.get(state.spaceId)),
    switcher: !all,
    testid: 'month',
    prev: () => moveMonth(-1),
    next: () => moveMonth(1),
    prevLabel: '이전 달',
    nextLabel: '다음 달',
  });

  const names = weekdayNames();
  const weekdays = h('div', { class: 'weekdays' },
    names.map((n) => h('div', { class: n === '토' ? 'sat' : n === '일' ? 'sun' : '' }, n)));

  const grid = h('div', { class: 'grid', 'data-testid': 'grid' },
    gridDays(month).map((d) => {
      const iso = isoDay(d);
      // Finished jellies leave the grid; the day below still lists them.
      const open = dayItems(iso).filter((it) => !it.done);
      const shown = open.slice(0, 3);
      const rest = open.length - shown.length;
      const holiday = holidayOn(iso);
      const tone = holiday || d.getDay() === 0 ? ' sun' : d.getDay() === 6 ? ' sat' : '';
      return h('button', {
        class: ['day', d.getMonth() !== month.getMonth() && 'other', iso === today && 'today', iso === selected && 'selected']
          .filter(Boolean).join(' '),
        'data-day': iso,
        onClick: () => {
          pick(iso);
          openSheet({ kind: 'day', date: iso });
        },
      },
        h('span', { class: 'day-top' },
          h('span', { class: `num${tone}` }, d.getDate()),
          rest > 0
            ? h('span', { class: 'more' }, `+${rest}`)
            : holiday ? h('span', { class: 'hol' }, holiday.short) : null),
        shown.map((it) => h('span', {
          class: `jelly mini${it.pinned ? ' pinned' : ''}${it.kind === 'personal' ? ' mine' : ''}`,
          vars: { ...flavorVars(it.flavor), who: it.kind === 'shared' ? memberColor(it.j.by, it.group.members) : 'transparent' },
        }, it.title)),
      );
    }));
  addSwipe(grid, moveMonth);

  appEl().replaceChildren(...[
    top,
    weekdays,
    grid,
    offlineBanner(),
    dayPanel(selected),
    installHint(),
    bottomBar(),
  ].filter(Boolean));
}

/**
 * The picked day under the month, or in its sheet ([inSheet]): its jellies to do, and the finished
 * ones folded away.
 */
function dayPanel(iso, inSheet = false) {
  const items = dayItems(iso);
  const open = items.filter((it) => !it.done);
  const done = items.filter((it) => it.done);
  const holiday = holidayOn(iso);
  const count = open.length || done.length
    ? `할 젤리 ${open.length}개${done.length ? ` · 다 먹은 젤리 ${done.length}개` : ''}`
    : '아직 비어 있어요';
  const toCard = (it, i) => (it.kind === 'shared' ? card(it.j, i, it.group) : personalCard(it.p, i));
  return h('div', { class: `day-panel${inSheet ? ' in-sheet' : ''}` },
    h('div', { class: 'day-head' },
      h('div', null,
        h('div', { class: 'day-title', 'data-testid': 'day-title' },
          dayTitle(iso),
          iso === todayIso() ? h('span', { class: 'tag today-tag' }, '오늘') : null,
          holiday ? h('span', { class: 'tag holiday-tag' }, holiday.name) : null),
        h('div', { class: 'day-count' }, count))),
    open.length
      ? h('div', { class: 'cards' }, open.map(toCard))
      : h('div', { class: 'empty' }, done.length
        ? '이 날 젤리를 다 먹었어요. 잘했어요!'
        : inSheet
          ? '이 날은 아직 말랑하게 비어 있어요. 아래 버튼으로 젤리를 올려 보세요.'
          : '이 날은 아직 말랑하게 비어 있어요. 아래 ＋ 로 젤리를 올려 보세요.'),
    done.length
      ? h('button', {
          class: 'done-fold',
          'data-testid': 'done-fold',
          'aria-expanded': String(state.showDone),
          onClick: () => {
            state.showDone = !state.showDone;
            render();
          },
        }, `다 먹은 젤리 ${done.length}개 ${state.showDone ? '▴' : '▾'}`)
      : null,
    done.length && state.showDone ? h('div', { class: 'cards done-cards' }, done.map(toCard)) : null,
  );
}

/** One of the phone's own jellies in the "모두" tab: the app opens it. */
function personalCard(p, i) {
  return h('button', {
    class: `jelly card squish${p.done ? ' done' : ''}`,
    vars: flavorVars(p.flavor ?? 0),
    style: { animationDelay: `${-(i * 0.7)}s` },
    onClick: () => bridge?.openPersonal?.(p.id),
    'data-testid': 'personal-card',
  },
    h('div', { class: 'title' }, `${p.pinned ? '📌 ' : ''}${p.done ? '✓ ' : ''}${p.title}`),
    h('div', { class: 'meta' },
      h('span', null, timeText({ start: p.start ?? null, duration: p.duration || 30 })),
      h('span', { class: 'badge' }, '내 젤리')));
}

function card(j, i, group = groups.get(state.spaceId)) {
  const members = group?.members ?? state.members;
  const edited = j.updatedBy && j.updatedBy !== j.by ? `${subject(j.updatedBy, j.updatedByName)} 고침` : null;
  return h('button', {
    class: `jelly card squish${j.done ? ' done' : ''}`,
    vars: flavorVars(j.flavor),
    style: { animationDelay: `${-(i * 0.7)}s` },
    onClick: () => openShared(j.id, group?.id),
    'data-testid': 'card',
  },
    h('div', { class: 'title' }, `${j.pinned ? '📌 ' : ''}${j.done ? '✓ ' : ''}${j.title}`),
    h('div', { class: 'meta' },
      h('span', null, timeText(j)),
      h('span', { class: 'badge' }, avatar(j.by, j.byName, true, members), `${subject(j.by, j.byName, members)} 올림`),
      isAll() && groups.size > 1 && group ? h('span', { class: 'badge' }, groupName(group)) : null,
      edited ? h('span', { class: 'badge' }, edited) : null,
      j.memoCount ? h('span', { class: 'badge', 'data-testid': 'memo-count' }, `메모 ${j.memoCount}`) : null,
      j.pending ? h('span', { class: 'pending-dot', title: '올리는 중' }) : null,
    ),
  );
}

function addSwipe(el, move = shiftMonth) {
  let x0 = null;
  let y0 = null;
  el.addEventListener('touchstart', (e) => {
    x0 = e.touches[0].clientX;
    y0 = e.touches[0].clientY;
  }, { passive: true });
  el.addEventListener('touchend', (e) => {
    if (x0 == null) return;
    const dx = e.changedTouches[0].clientX - x0;
    const dy = e.changedTouches[0].clientY - y0;
    x0 = null;
    if (Math.abs(dx) > 60 && Math.abs(dx) > Math.abs(dy) * 1.5) move(dx < 0 ? 1 : -1);
  });
}

function installHint() {
  if (inApp || standalone || !isIos || saved.get('hideInstall')) return null;
  const el = h('div', { class: 'banner' },
    h('div', null, '아이폰에서는 사파리 아래쪽 공유 버튼(네모에 화살표)을 누르고 ‘홈 화면에 추가’를 고르면 앱처럼 쓸 수 있어요.'),
    h('button', {
      class: 'x',
      'aria-label': '닫기',
      onClick: () => {
        saved.set('hideInstall', '1');
        el.remove();
      },
    }, '✕'));
  return el;
}

// ---------------------------------------------------------------- boxes

// The box screens fill the window and keep their box (and its falling jellies) between updates;
// only the text around it is drawn again.
let boxView = null;

function useBoxView(kind, onSwipe) {
  if (boxView?.kind === kind && screen === kind && boxView.frame.isConnected) return boxView;
  leaveBox();
  const inner = h('div', { class: 'box-inner', 'data-testid': 'box' });
  const frame = h('div', { class: 'box-frame' }, inner);
  const top = h('div', { class: 'box-top' });
  const note = h('div', { class: 'box-note' });
  const bar = h('div', { class: 'box-bar' });
  appEl().replaceChildren(top, note, frame, bar);
  document.body.classList.add('fill', 'has-bar');
  screen = kind;
  const box = new JellyBox(inner, { onOpen: openItem, onToggle: toggleItem, onSwipe, onGrab: grabItem });
  boxView = { kind, top, note, frame, bar, box };
  if (testMode) window.__jellyBox = box;
  return boxView;
}

function initial(name) {
  return Array.from(name || '?')[0];
}

function sharedItem(j, group = groups.get(state.spaceId)) {
  const members = group?.members ?? state.members;
  return {
    key: `s:${j.id}`,
    title: j.title,
    start: j.start,
    duration: j.duration,
    flavor: j.flavor,
    done: !!j.done,
    // Whoever put it up: their character on the jelly, or their first letter in a circle.
    look: memberLook(j.by, members),
    badge: memberLook(j.by, members) ? null : { text: initial(realName(j.by, j.byName, members)), color: memberColor(j.by, members), ink: memberInk(j.by, members) },
    gold: state.goldenKeys.has(`s:${j.id}`),
    pinned: !!j.pinned,
    ref: { kind: 'shared', id: j.id, spaceId: group?.id ?? state.spaceId },
  };
}

function personalItem(p) {
  return {
    key: `p:${p.id}`,
    title: p.title,
    start: p.start ?? null,
    duration: p.duration || 30,
    flavor: p.flavor ?? 0,
    done: !!p.done,
    badge: null,
    // My own jellies wear my character only if I asked for that in the app's settings.
    look: state.host?.lookOnMine ? myLook() : null,
    pinned: !!p.pinned,
    ref: { kind: 'personal', id: p.id },
  };
}

/** One more squeeze of [item] in the box: glitter from the 25th, gold on the 50th. */
function grabItem(item) {
  const box = boxView?.box;
  const now = Date.now();
  const st = state.streak;
  st.count = st.key === item.key && now - st.at <= GOLDEN_PATIENCE_MS ? st.count + 1 : 1;
  st.key = item.key;
  st.at = now;
  const level = st.count < GOLDEN_HINT ? 0 : Math.min(1, (st.count - GOLDEN_HINT + 1) / (GOLDEN_GRABS - GOLDEN_HINT));
  box?.setGlitter(item.key, level);
  if (st.count < GOLDEN_GRABS) return;
  st.key = null;
  st.count = 0;
  box?.setGlitter(null, 0);
  item.gold = true;
  state.goldenKeys.add(item.key);
  saved.set('golden.keys', JSON.stringify([...state.goldenKeys].slice(-30)));
  box?.burst(item.key);
  // Let it burst into gold before the news covers it.
  setTimeout(() => foundGolden(item), 1200);
}

function foundGolden(item) {
  if (bridge?.foundGolden) {
    // Inside the app, the app keeps one code per phone and shows the news.
    bridge.foundGolden(item.ref.kind === 'personal' ? item.ref.id : '');
    return;
  }
  if (readJson(saved.get('golden.find'), null)) {
    toast('✨ 또 황금 젤리! 황금 코드는 메뉴에서 다시 볼 수 있어요');
    return;
  }
  const bytes = crypto.getRandomValues(new Uint8Array(8));
  const chars = Array.from(bytes, (b) => CODE_LETTERS[b % CODE_LETTERS.length]).join('');
  saved.set('golden.find', JSON.stringify({ code: `GOLD-${chars.slice(0, 4)}-${chars.slice(4)}`, foundAt: Date.now() }));
  openSheet({ kind: 'golden' });
}

function foundAtText(ms) {
  const d = new Date(ms);
  const hour = d.getHours();
  const ampm = hour < 12 ? '오전' : '오후';
  return `${d.getFullYear()}년 ${d.getMonth() + 1}월 ${d.getDate()}일 ${ampm} ${hour % 12 || 12}:${pad(d.getMinutes())}`;
}

/** The surprise, and later the code again from the menu. */
function buildGoldenSheet() {
  const find = readJson(saved.get('golden.find'), null);
  if (!find) {
    closeSheet();
    return;
  }
  const when = foundAtText(find.foundAt);
  const text = `🏆 젤리 캘린더에서 숨겨진 황금 젤리를 찾았어요!\n황금 코드: ${find.code}\n찾은 때: ${when}`;
  sheetFrame(
    h('div', { class: 'golden', 'data-testid': 'golden' },
      h('div', { class: 'golden-jelly', 'aria-hidden': 'true' }, '🏆'),
      h('div', { class: 'golden-title' }, '숨겨진 황금 젤리를', h('br'), '찾았어요!'),
      h('p', { class: 'golden-sub' }, `젤리를 ${GOLDEN_GRABS}번이나 쉬지 않고 주물럭거린 끈기에 젤리가 황금으로 변했어요.`),
      h('p', { class: 'golden-ask' }, `이 화면을 캡처해서 제작자 ${MAKER}에게 보내 주세요. 작은 선물을 드려요!`),
      h('div', { class: 'golden-code' },
        h('div', { class: 'golden-code-label' }, '황금 코드'),
        h('div', { class: 'golden-code-value', 'data-testid': 'golden-code' }, find.code),
        h('div', { class: 'golden-code-label' }, `찾은 때 · ${when}`)),
      h('button', {
        class: 'btn block golden-btn',
        onClick: async () => {
          try {
            if (navigator.share) {
              await navigator.share({ text });
            } else {
              await navigator.clipboard.writeText(text);
              toast('황금 코드를 복사했어요. 제작자에게 붙여 넣어 보내 주세요');
            }
          } catch {
            // Closed the share sheet: nothing to do.
          }
        },
      }, '제작자에게 보내기'),
      h('button', { class: 'btn ghost block', onClick: closeSheet }, '닫기')),
  );
}

function openItem(item) {
  if (item.ref.kind === 'personal') bridge?.openPersonal?.(item.ref.id);
  else openShared(item.ref.id, item.ref.spaceId);
}

/** Opens a shared jelly; one from another calendar brings that calendar on screen first. */
function openShared(id, spaceId) {
  if (spaceId && spaceId !== state.spaceId) openSpace(spaceId);
  openSheet({ kind: 'jelly', id });
}

function toggleItem(item) {
  if (item.ref.kind === 'personal') {
    bridge?.togglePersonal?.(item.ref.id);
    return;
  }
  const spaceId = item.ref.spaceId || state.spaceId;
  const j = groups.get(spaceId)?.jellies.find((x) => x.id === item.ref.id);
  if (!j) return;
  store.updateJelly(spaceId, j.id, { done: !j.done }, state.myName).catch((e) => {
    console.error(e);
    toast('저장하지 못했어요');
  });
}

function renderSharedBox() {
  const v = useBoxView('shared-box', (dir) => showDay(isoDay(addDays(parseDay(state.selected), dir))));
  const list = jelliesOn(state.selected);
  const open = list.filter((j) => !j.done).length;
  const holiday = holidayOn(state.selected);
  v.top.replaceChildren(
    header({
      title: dayTitle(state.selected),
      sub: [
        state.selected === todayIso() ? '오늘' : null,
        holiday?.name,
        list.length ? `공유 젤리 ${list.length}개${open < list.length ? ` · 다 먹음 ${list.length - open}` : ''}` : '아직 비어 있어요',
      ].filter(Boolean).join(' · '),
      testid: 'day-head',
      prev: () => showDay(isoDay(addDays(parseDay(state.selected), -1))),
      next: () => showDay(isoDay(addDays(parseDay(state.selected), 1))),
      prevLabel: '전날',
      nextLabel: '다음 날',
    }),
    // replaceChildren() would print a null as the text "null".
    ...(savedSpaces().length > 1
      ? [h('button', { class: 'space-switch box-switch', onClick: () => openSheet({ kind: 'groups' }), 'data-testid': 'groups' },
          `${groupName(groups.get(state.spaceId))} ▾`)]
      : []),
  );
  v.note.replaceChildren(...[offlineBanner()].filter(Boolean));
  v.bar.replaceChildren(bottomBar());
  v.box.setOptions(gestures());
  v.box.setEmpty('이 날은 비어 있어요', '아래 ＋ 로 같이 할 일을 올려 보세요. 빈 곳을 옆으로 밀면 다른 날로 가요.');
  v.box.set(list.map(sharedItem));
}

/** The app's "모두" tab: the phone's own jellies of the day and the shared ones, in one box. */
function renderAll() {
  const host = state.host;
  const v = useBoxView('all', (dir) => bridge?.shiftDay?.(dir));
  // The app hands over six weeks of my jellies; the box holds the day's.
  const personal = hostPersonal().filter((p) => !p.date || p.date === host.date);
  const shown = shownGroups();
  const shared = shown.flatMap((g) => g.jellies.filter((j) => j.date === host.date).map((j) => ({ j, g })));
  const holiday = holidayOn(host.date);
  v.top.replaceChildren(
    header({
      title: dayTitle(host.date),
      sub: [host.date === todayIso() ? '오늘' : null, holiday?.name, '내 젤리와 공유 젤리'].filter(Boolean).join(' · '),
      testid: 'all-day',
      prev: () => bridge?.shiftDay?.(-1),
      next: () => bridge?.shiftDay?.(1),
      prevLabel: '전날',
      nextLabel: '다음 날',
    }),
    h('div', { class: 'legend', 'data-testid': 'legend' },
      h('span', { class: 'legend-item' }, h('span', { class: 'legend-mine' }), `내 젤리 ${personal.length}`),
      h('span', { class: 'legend-item' },
        state.members.length
          ? state.members.slice(0, 4).map((m) => avatar(m.uid, m.name, true))
          : h('span', { class: 'avatar small', style: { background: MEMBER_COLORS[1] } }, '공'),
        `공유 젤리 ${shared.length}${shown.length > 1 ? ` · 달력 ${shown.length}개` : ''}`),
      h('span', { class: 'legend-note' }, '젤리의 캐릭터나 동그라미 글자: 공유 젤리를 올린 사람')),
  );
  v.note.replaceChildren(...[
    shown.length
      ? null
      : h('div', { class: 'banner', 'data-testid': 'no-space' },
          h('div', null, state.space?.legacy
            ? '공유 방식이 바뀌어 예전 공유 달력은 열 수 없어요. ‘공유 젤리’ 탭에서 정리한 뒤 새 달력을 만들어 주세요.'
            : '아직 공유 달력에 들어가지 않았어요. ‘공유 젤리’ 탭에서 달력을 만들거나 초대 코드로 들어가면, 공유 젤리도 이 상자에 함께 떨어져요.'),
          bridge?.showShared ? h('button', { class: 'chip on', onClick: () => bridge.showShared() }, '열기') : null),
    offlineBanner(),
  ].filter(Boolean));
  v.bar.replaceChildren(bottomBar());
  v.box.setOptions(gestures());
  v.box.setEmpty('이 날은 비어 있어요', '아래 ＋ 로 내 젤리나 공유 젤리를 넣어 보세요. 빈 곳을 옆으로 밀면 다른 날로 가요.');
  v.box.set([...personal.map(personalItem), ...shared.map(({ j, g }) => sharedItem(j, g))]);
}

// ---------------------------------------------------------------- sheets

function openSheet(sheet) {
  if (state.sheet?.kind === 'day' && sheet.kind !== 'day') {
    // Opened from a day's sheet (a jelly, or a new one): going back returns to the day.
    sheet.under = state.sheet;
    history.pushState({ sheet: true }, '');
  } else if (!history.state?.sheet) {
    history.pushState({ sheet: true }, '');
  } else if (state.sheet?.under && !sheet.under) {
    sheet.under = state.sheet.under;
  }
  state.sheet = sheet;
  buildSheet();
}

function closeSheet() {
  if (history.state?.sheet) history.back();
  else hideSheet();
}

/** Stops the live updates of the sheet on screen. */
function dropSheetWatchers() {
  subs.memos?.();
  subs.memos = null;
  subs.jelly?.();
  subs.jelly = null;
  state.memos = [];
}

function hideSheet() {
  dropSheetWatchers();
  state.sheet = null;
  document.querySelector('.scrim')?.remove();
  document.querySelector('.sheet')?.remove();
  document.documentElement.classList.remove('sheet-open');
}

/** Back from a sheet opened on a day's sheet: that day's sheet again. */
function showUnder() {
  const under = state.sheet.under;
  dropSheetWatchers();
  state.sheet = under;
  buildSheet();
}

/**
 * A tapped day's jellies in a sheet, to open one or put up another: the list under the month is
 * often off screen. Kept up to date while it is open (see render).
 */
function buildDaySheet() {
  const iso = state.sheet.date;
  const content = [
    dayPanel(iso, true),
    h('button', { class: 'btn block day-add', type: 'button', onClick: () => addJelly(iso), 'data-testid': 'day-add' },
      isAll() ? '＋ 이 날에 젤리 넣기' : '＋ 이 날에 젤리 올리기'),
  ];
  const open = document.querySelector('.sheet[data-sheet="day"]');
  if (open && open.dataset.date === iso) {
    const top = open.scrollTop;
    open.replaceChildren(...sheetTop(), ...content);
    open.scrollTop = top;
    return;
  }
  const sheet = sheetFrame(...content);
  sheet.dataset.sheet = 'day';
  sheet.dataset.date = iso;
  sheet.setAttribute('data-testid', 'day-sheet');
}

/** The top of every sheet: ✕ to close it (it stays in reach while the sheet scrolls) and the handle. */
function sheetTop() {
  return [
    h('div', { class: 'sheet-top' },
      h('button', { class: 'sheet-close', type: 'button', 'aria-label': '닫기', onClick: closeSheet, 'data-testid': 'sheet-close' }, '✕')),
    h('div', { class: 'handle' }),
  ];
}

function sheetFrame(...children) {
  document.querySelector('.scrim')?.remove();
  document.querySelector('.sheet')?.remove();
  const scrim = h('div', { class: 'scrim', onClick: closeSheet });
  const sheet = h('div', { class: 'sheet', role: 'dialog', 'aria-modal': 'true' }, ...sheetTop(), ...children);
  document.body.append(scrim, sheet);
  // The page under a sheet stays still: no scrolling behind it, no pull-to-refresh.
  document.documentElement.classList.add('sheet-open');
  pullToClose(sheet, scrim);
  return sheet;
}

/**
 * A sheet follows a finger pulling it down, by its handle or from the top of what it shows, and
 * closes when let go far enough down or with a flick; otherwise it springs back. The pull is kept
 * from the browser, which would otherwise scroll the page or reload it (pull-to-refresh).
 */
function pullToClose(sheet, scrim) {
  let startY = null;
  let lastY = 0;
  let lastT = 0;
  let speed = 0;
  let pulling = false;
  sheet.addEventListener('touchstart', (e) => {
    startY = null;
    pulling = false;
    if (e.touches.length !== 1) return;
    const onHandle = e.target.closest('.handle, .sheet-top');
    // Text fields and the stretchy length keep their own gestures; scrolled content scrolls back first.
    if (!onHandle && (sheet.scrollTop > 0 || e.target.closest('input, textarea, select, [role="slider"]'))) return;
    startY = lastY = e.touches[0].clientY;
    lastT = e.timeStamp;
    speed = 0;
  }, { passive: true });
  sheet.addEventListener('touchmove', (e) => {
    if (startY == null || e.touches.length !== 1) return;
    const y = e.touches[0].clientY;
    const dy = y - startY;
    if (!pulling && dy < 0) {
      // Upwards: the content scrolls as usual.
      startY = null;
      return;
    }
    if (dy <= 0) return;
    if (e.cancelable) e.preventDefault();
    if (!pulling) {
      if (dy < 6) return;
      pulling = true;
      sheet.style.animation = 'none';
      sheet.style.transition = 'none';
      scrim.style.transition = 'none';
    }
    speed = (y - lastY) / Math.max(1, e.timeStamp - lastT);
    lastY = y;
    lastT = e.timeStamp;
    sheet.style.transform = `translateY(${dy}px)`;
    scrim.style.opacity = String(Math.max(0.15, 1 - dy / Math.max(1, sheet.offsetHeight)));
  }, { passive: false });
  const letGo = (cancelled) => {
    if (startY == null) return;
    const dy = Math.max(0, lastY - startY);
    const was = pulling;
    startY = null;
    pulling = false;
    if (!was) return;
    const mine = state.sheet;
    if (!cancelled && (dy > Math.min(140, sheet.offsetHeight * 0.3) || (speed > 0.5 && dy > 30))) {
      sheet.style.transition = 'transform 0.18s ease-in';
      sheet.style.transform = `translateY(${sheet.offsetHeight + 24}px)`;
      scrim.style.transition = 'opacity 0.18s ease-in';
      scrim.style.opacity = '0';
      setTimeout(() => {
        if (state.sheet === mine) closeSheet();
      }, 170);
    } else {
      sheet.style.transition = 'transform 0.25s cubic-bezier(0.2, 1.3, 0.4, 1)';
      sheet.style.transform = '';
      scrim.style.transition = 'opacity 0.2s';
      scrim.style.opacity = '';
    }
  };
  sheet.addEventListener('touchend', () => letGo(false));
  sheet.addEventListener('touchcancel', () => letGo(true));
  scrim.addEventListener('touchmove', (e) => {
    if (e.cancelable) e.preventDefault();
  }, { passive: false });
}

function buildSheet() {
  const s = state.sheet;
  if (!s) return;
  if (s.kind === 'jelly' || s.kind === 'new') buildJellySheet();
  else if (s.kind === 'day') buildDaySheet();
  else if (s.kind === 'add') buildAddSheet();
  else if (s.kind === 'invite') buildInviteSheet();
  else if (s.kind === 'menu') buildMenuSheet();
  else if (s.kind === 'name') buildNameSheet();
  else if (s.kind === 'look') buildLookSheet();
  else if (s.kind === 'golden') buildGoldenSheet();
  else if (s.kind === 'groups') buildGroupsSheet();
  else if (s.kind === 'new-group') buildNewGroupSheet();
  else if (s.kind === 'rename') buildRenameSheet();
  else if (s.kind === 'account') buildAccountSheet();
  else if (s.kind === 'vault-open') buildVaultOpenSheet();
  else if (s.kind === 'vault-set') buildVaultSetSheet();
  else if (s.kind === 'delete-account') buildDeleteAccountSheet();
}

/** Where a length sits on the stretch bar: the first half for up to two hours, the rest up to twelve. */
function lengthToFraction(minutes) {
  const m = Math.min(MAX_DURATION, Math.max(10, minutes));
  return m <= SHORT_MAX ? (0.5 * (m - 10)) / (SHORT_MAX - 10) : 0.5 + (0.5 * (m - SHORT_MAX)) / (MAX_DURATION - SHORT_MAX);
}

function fractionToLength(fraction) {
  const f = Math.min(1, Math.max(0, fraction));
  if (f <= 0.5) return Math.min(SHORT_MAX, Math.max(10, 10 + Math.round(((f / 0.5) * (SHORT_MAX - 10)) / 10) * 10));
  return Math.min(MAX_DURATION, Math.max(SHORT_MAX, SHORT_MAX + Math.round((((f - 0.5) / 0.5) * (MAX_DURATION - SHORT_MAX)) / 30) * 30));
}

/**
 * Pull the jelly to make it longer: it stretches under the finger and buzzes a little every step,
 * like a chewy sweet being pulled. [get] and [set] read and change the length; [onEnd] runs when let go.
 */
function stretchLength(get, set, onEnd) {
  const KNOB = 30;
  const text = h('span', { class: 'stretch-text' });
  const bar = h('div', { class: 'jelly stretch-bar' }, text, h('span', { class: 'stretch-knob', 'aria-hidden': 'true' }, '⇢'));
  const track = h('div', {
    class: 'stretch-track',
    role: 'slider',
    tabindex: '0',
    'aria-label': '길이',
    'aria-valuemin': '10',
    'aria-valuemax': String(MAX_DURATION),
    'data-testid': 'length',
  }, bar);
  const ticks = h('div', { class: 'stretch-ticks' }, LENGTH_TICKS.map(([m, label], i) => h('span', {
    'data-m': String(m),
    style: {
      left: `calc(${KNOB / 2}px + (100% - ${KNOB}px) * ${lengthToFraction(m)})`,
      transform: i === LENGTH_TICKS.length - 1 ? 'translateX(-85%)' : 'translateX(-50%)',
    },
  }, label)));
  const at = (clientX) => {
    const r = track.getBoundingClientRect();
    return fractionToLength((clientX - r.left - KNOB / 2) / Math.max(1, r.width - KNOB));
  };
  const apply = (m) => {
    if (m === get()) return;
    set(m);
    try {
      navigator.vibrate?.(4);
    } catch {
      // No buzz here.
    }
  };
  let dragging = false;
  track.addEventListener('pointerdown', (e) => {
    dragging = true;
    track.setPointerCapture?.(e.pointerId);
    apply(at(e.clientX));
  });
  track.addEventListener('pointermove', (e) => {
    if (dragging) apply(at(e.clientX));
  });
  const stop = () => {
    if (!dragging) return;
    dragging = false;
    onEnd?.();
  };
  track.addEventListener('pointerup', stop);
  track.addEventListener('pointercancel', stop);
  track.addEventListener('keydown', (e) => {
    const m = get();
    const step = m < SHORT_MAX || (m === SHORT_MAX && e.key === 'ArrowLeft') ? 10 : 30;
    if (e.key === 'ArrowRight') apply(Math.min(MAX_DURATION, m + step));
    else if (e.key === 'ArrowLeft') apply(Math.max(10, m - step));
    else return;
    e.preventDefault();
    onEnd?.();
  });
  const paint = (flavor) => {
    const m = get();
    for (const [k, v] of Object.entries(flavorVars(flavor))) bar.style.setProperty(`--${k}`, v);
    bar.style.width = `max(64px, calc(${KNOB}px + (100% - ${KNOB}px) * ${lengthToFraction(m)}))`;
    text.textContent = durationText(m);
    track.setAttribute('aria-valuenow', String(m));
    track.setAttribute('aria-valuetext', durationText(m));
    for (const t of ticks.children) t.classList.toggle('on', Number(t.dataset.m) === m);
  };
  return { el: h('div', { class: 'stretch' }, track, ticks), paint };
}

/**
 * "⏰ 시작 전 알람" inside the Android app: the app asks the clock app for an alarm a little before the
 * jelly starts. The request takes a time but no date: an alarm in the coming 24 hours is set in one
 * tap, and a later one is made in Samsung Clock's new-alarm screen, where the date is picked.
 */
function alarmRow(draft) {
  if (!bridge?.setAlarm) return null;
  const DAY_MS = 24 * 3600 * 1000;
  const canPick = typeof bridge.openAlarmEditor === 'function';
  let before = 10;
  let asked = null;
  // Samsung Clock was opened for this alarm (whether it was saved there is not known).
  let opened = null;
  const note = h('small', { class: 'toggle-note' });
  const chips = h('div', { class: 'row' });
  const button = h('button', { class: 'btn small-btn', type: 'button', 'data-testid': 'alarm' }, '알람 맞추기');
  const at = () => {
    if (!draft.date || draft.start == null) return null;
    const d = parseDay(draft.date);
    return new Date(d.getFullYear(), d.getMonth(), d.getDate(), 0, draft.start - before);
  };
  const label = () => {
    const title = (draft.title || '젤리').trim();
    return before === 0 ? `${title} 시작` : `${title} ${before}분 전`;
  };
  const clock = (t) => hm(t.getHours() * 60 + t.getMinutes());
  const paint = () => {
    const t = at();
    const ahead = t ? t.getTime() - Date.now() : null;
    const possible = ahead != null && ahead >= 0 && ahead < DAY_MS;
    const later = canPick && ahead != null && ahead >= DAY_MS;
    const done = asked != null && t && asked === t.getTime();
    chips.replaceChildren(...[[0, '정각'], [10, '10분 전'], [30, '30분 전']].map(([m, text]) => h('button', {
      class: `chip${before === m ? ' on' : ''}`,
      type: 'button',
      onClick: () => {
        before = m;
        paint();
      },
    }, text)));
    note.textContent = !t
      ? '날짜와 시간이 있는 젤리에 알람을 맞출 수 있어요.'
      : done
        ? `${clock(t)} 알람을 시계 앱에 부탁했어요. 띠링!`
        : possible
          ? `${clock(t)}에 울리도록 시계 앱에 알람을 맞춰요.`
          : later && opened === t.getTime()
            ? '삼성 시계에서 날짜를 골라 저장했다면 다 됐어요.'
            : later
              ? '하루 넘게 남은 알람은 삼성 시계에서 날짜를 골라 맞춰요.'
              : ahead < 0
                ? '이미 지난 시각이에요.'
                : '시계 앱은 날짜 없이 시각만 받아서, 24시간 안에 울릴 알람만 맞출 수 있어요.';
    button.disabled = !(possible || later) || done;
    button.textContent = done ? '✓ 맞췄어요' : later ? '날짜 골라 맞추기' : '알람 맞추기';
  };
  button.addEventListener('click', async () => {
    const t = at();
    if (!t) return;
    if (canPick && t.getTime() - Date.now() >= DAY_MS) {
      const go = await askYesNo(
        `알람 요청에는 날짜를 담을 수 없어서, 삼성 시계의 알람 추가 화면을 열어 드릴게요.\n\n`
          + `1. 시각이 ${clock(t)}인지 확인하고\n`
          + `2. 달력 아이콘을 눌러 ${t.getMonth() + 1}월 ${t.getDate()}일을 고른 다음\n`
          + '3. ‘저장’을 눌러 주세요.',
        { yes: '삼성 시계 열기', no: '취소' },
      );
      if (!go) return;
      bridge.openAlarmEditor(t.getHours(), t.getMinutes(), label());
      opened = t.getTime();
      paint();
      return;
    }
    bridge.setAlarm(t.getHours(), t.getMinutes(), label());
    asked = t.getTime();
    paint();
  });
  const el = h('div', { class: 'alarm-row' },
    h('div', { class: 'toggle-text' }, h('span', null, '⏰ 시작 전 알람'), note),
    h('div', { class: 'row alarm-controls' }, chips, button));
  return { el, paint };
}

/** How many jellies of [date] are pinned, leaving out [exceptId]. */
function pinsOn(date, exceptId) {
  return state.jellies.filter((j) => j.date === date && j.pinned && j.id !== exceptId).length;
}

/** A new jelly's time, as in the app: today a little after now (not before 8:00), another day 9:00. */
function defaultStart(iso) {
  if (iso !== todayIso()) return 9 * 60;
  const now = new Date();
  const soon = Math.max(now.getHours() * 60 + now.getMinutes() + 10, 8 * 60);
  return Math.min(Math.round(soon / 10) * 10, 23 * 60);
}

function buildJellySheet() {
  const s = state.sheet;
  const isNew = s.kind === 'new';
  const existing = isNew ? null : s.seed || state.jellies.find((x) => x.id === s.id);
  if (!isNew && !existing) return hideSheet();
  // The draft that is shown and edited; for an existing jelly, changes are saved as they happen.
  const draft = existing
    ? { ...existing }
    : (() => {
        const date = s.date || state.selected;
        return { title: '', date, start: defaultStart(date), duration: 60, flavor: state.myColor, done: false, note: '', pinned: false };
      })();

  const preview = h('div', { class: 'jelly card', style: { animation: 'none', marginBottom: '6px' } });
  const title = h('input', {
    class: 'title-input',
    placeholder: '무엇을 같이 할까요?',
    maxlength: '80',
    value: draft.title,
    'data-testid': 'title',
  });
  const date = h('input', { class: 'input', type: 'date', value: draft.date, 'data-testid': 'date' });
  const time = h('input', { class: 'input', type: 'time', value: draft.start == null ? '' : hm(draft.start), 'data-testid': 'time' });
  // An empty time field is a blank box on an iPhone: it says what it is for.
  const timeBox = h('label', { class: 'time-box' }, time, h('span', { class: 'time-empty', 'aria-hidden': 'true' }, '시간 정하기'));
  const noTime = h('button', { class: 'chip', type: 'button', 'data-testid': 'no-time' }, '시간 없음');
  // A time written in the title ("11시 미용실"): a new jelly takes it until its time is set by hand,
  // and an existing one offers it in one tap.
  const titleHint = h('div', { class: 'title-time', 'data-testid': 'title-time' });
  let timeTouched = false;
  let fromTitle = null;
  let startBeforeTitle = draft.start;
  const showTime = (minute) => {
    time.value = minute == null ? '' : hm(minute);
  };
  const length = stretchLength(() => draft.duration, (m) => save({ duration: m }), () => save({}, true));
  const colorRow = h('div', { class: 'row' });
  const doneSwitch = h('span', { class: 'switch' });
  const doneRow = h('button', { class: 'toggle', type: 'button', 'data-testid': 'done' }, h('span', null, '✓ 다 먹었어요'), doneSwitch);
  const pinSwitch = h('span', { class: 'switch' });
  const pinNote = h('small', { class: 'toggle-note' });
  const pinRow = h('button', { class: 'toggle', type: 'button', 'data-testid': 'pin' },
    h('span', { class: 'toggle-text' }, h('span', null, '📌 젤위로 고정'), pinNote), pinSwitch);
  const selfId = isNew ? null : s.id;
  const alarm = isNew ? null : alarmRow(draft);
  const note = h('textarea', { class: 'input', placeholder: '장소, 준비물 같은 설명 (선택)', maxlength: '1000' }, draft.note || '');
  const whoLine = h('div', { class: 'who-line' });

  let pendingSave = null;
  let pendingPatch = {};
  function save(patch, now = false) {
    Object.assign(draft, patch);
    paint();
    if (isNew) return;
    Object.assign(pendingPatch, patch);
    clearTimeout(pendingSave);
    const flush = () => {
      const p = pendingPatch;
      pendingPatch = {};
      if (Object.keys(p).length) {
        store.updateJelly(state.spaceId, s.id, p, state.myName).catch((e) => {
          console.error(e);
          toast('저장하지 못했어요');
        });
      }
    };
    if (now) flush();
    else pendingSave = setTimeout(flush, 600);
  }

  function paint() {
    preview.replaceChildren(
      h('div', { class: 'title' }, draft.done ? `✓ ${draft.title || '이름 없는 젤리'}` : draft.title || '이름 없는 젤리'),
      h('div', { class: 'meta' }, h('span', null, `${dayTitle(draft.date)} · ${timeText(draft)}`)),
    );
    for (const [k, v] of Object.entries(flavorVars(draft.flavor))) preview.style.setProperty(`--${k}`, v);
    preview.classList.toggle('done', !!draft.done);
    length.paint(draft.flavor);
    alarm?.paint();
    const full = !draft.pinned && pinsOn(draft.date, selfId) >= MAX_PINNED;
    pinSwitch.classList.toggle('on', !!draft.pinned);
    pinRow.classList.toggle('muted', full);
    pinNote.textContent = full
      ? '이 날은 벌써 3개가 고정돼 있어요. 하나를 풀면 고정할 수 있어요.'
      : '젤 중요한 젤리를 상자 맨 위에 꼭 붙여 둬요. 하루 3개까지예요.';
    colorRow.replaceChildren(...FLAVORS.map((f, i) => h('button', {
      class: `swatch${draft.flavor === i ? ' on' : ''}`,
      type: 'button',
      'aria-label': f.name,
      style: { background: f.base },
      onClick: () => save({ flavor: i }, true),
    })));
    noTime.classList.toggle('on', draft.start == null);
    timeBox.classList.toggle('blank', draft.start == null);
    doneSwitch.classList.toggle('on', !!draft.done);
    const said = isNew ? null : titleTime(draft.title);
    titleHint.replaceChildren(...[
      fromTitle != null
        ? h('small', { class: 'toggle-note' }, `시간을 제목의 ‘${fromTitle}’에 맞췄어요.`)
        : said && said.minute !== draft.start
          ? h('button', {
              class: 'chip',
              type: 'button',
              'data-testid': 'title-time-chip',
              onClick: () => {
                timeTouched = true;
                showTime(said.minute);
                save({ start: said.minute }, true);
              },
            }, `제목대로 ${hm(said.minute)}`)
          : null,
    ].filter(Boolean));
  }

  /** A new jelly's time follows a time written in its title, until the time is set by hand. */
  function followTitle() {
    if (!isNew || timeTouched) return;
    const said = titleTime(title.value);
    if (said) {
      if (fromTitle == null) startBeforeTitle = draft.start;
      fromTitle = said.text;
      showTime(said.minute);
      save({ start: said.minute });
    } else if (fromTitle != null) {
      fromTitle = null;
      showTime(startBeforeTitle);
      save({ start: startBeforeTitle });
    }
  }

  title.addEventListener('input', () => {
    const t = title.value.trim();
    if (t) {
      save({ title: t });
    } else {
      draft.title = '';
      paint();
    }
    followTitle();
  });
  title.addEventListener('blur', () => {
    if (!isNew && !title.value.trim()) title.value = draft.title = existing.title;
  });
  date.addEventListener('change', () => {
    if (!date.value) return;
    const patch = { date: date.value };
    if (draft.pinned && pinsOn(date.value, selfId) >= MAX_PINNED) {
      patch.pinned = false;
      toast('그날은 고정 자리가 꽉 차서 고정을 풀었어요');
    }
    save(patch, true);
    // Follow the jelly to its new day, so it stays in view.
    if (!isNew) showDay(date.value);
  });
  time.addEventListener('change', () => {
    timeTouched = true;
    fromTitle = null;
    if (!time.value) return save({ start: null }, true);
    const [hh, mm] = time.value.split(':').map(Number);
    save({ start: hh * 60 + mm }, true);
  });
  noTime.addEventListener('click', () => {
    timeTouched = true;
    fromTitle = null;
    time.value = '';
    save({ start: null }, true);
  });
  doneRow.addEventListener('click', () => save({ done: !draft.done }, true));
  pinRow.addEventListener('click', () => {
    if (!draft.pinned && pinsOn(draft.date, selfId) >= MAX_PINNED) {
      toast('이 날은 벌써 3개가 고정돼 있어요. 하나를 풀면 고정할 수 있어요');
      return;
    }
    save({ pinned: !draft.pinned }, true);
  });
  note.addEventListener('input', () => save({ note: note.value }));

  const children = [
    h('div', { class: 'sheet-kicker' }, `${isNew ? '새 젤리 빚기' : '젤리 다듬기'}${groups.size > 1 ? ` · ${groupName(groups.get(state.spaceId))}` : ''}`),
    preview,
    title,
    h('div', { class: 'field-label' }, '맛'),
    colorRow,
    h('div', { class: 'field-label' }, '길이 · 쭉 당기면 늘어나요'),
    length.el,
    h('div', { class: 'field-label' }, '언제'),
    h('div', { class: 'row' }, date, timeBox, noTime),
    titleHint,
    pinRow,
  ];

  if (isNew) {
    let busy = false;
    children.push(
      h('div', { class: 'field-label' }, '설명'),
      note,
      h('button', {
        class: 'btn block',
        style: { marginTop: '16px' },
        'data-testid': 'post',
        onClick: async () => {
          const t = title.value.trim();
          if (!t) {
            toast('무엇을 할지 적어 주세요');
            title.focus();
            return;
          }
          if (busy) return;
          busy = true;
          const fields = { ...draft, title: t };
          const id = store.addJelly(state.spaceId, fields, state.myName, (err) => {
            if (err) {
              console.error(err);
              toast('올리지 못했어요');
            }
          });
          const under = state.sheet?.under;
          showDay(fields.date);
          state.sheet = {
            kind: 'jelly',
            id,
            seed: { ...fields, id, by: state.uid, byName: state.myName, updatedBy: state.uid, memoCount: 0, pending: true },
            under,
          };
          buildSheet();
          toast(state.online ? '올렸어요. 함께 쓰는 사람 화면에도 바로 보여요' : '연결되면 바로 올라가요');
        },
      }, '젤리 올리기'),
    );
  } else {
    const memoList = h('div', { class: 'memos', 'data-testid': 'memos' });
    const memoInput = h('input', { class: 'input', placeholder: '메모 남기기', maxlength: '500', 'data-testid': 'memo-input' });
    const send = h('button', { class: 'btn', type: 'submit', 'data-testid': 'memo-send' }, '보내기');
    const memoForm = h('form', {
      class: 'memo-input',
      onSubmit: async (e) => {
        e.preventDefault();
        const text = memoInput.value.trim();
        if (!text) return;
        memoInput.value = '';
        try {
          await store.addMemo(state.spaceId, s.id, text, state.myName);
        } catch (err) {
          console.error(err);
          memoInput.value = text;
          toast('메모를 남기지 못했어요');
        }
      },
    }, memoInput, send);

    const paintMemos = () => {
      memoList.replaceChildren(
        ...(state.memos.length
          ? state.memos.map((m) => {
              const mine = m.by === state.uid;
              return h('div', { class: `memo${mine ? ' mine' : ''}` },
                avatar(m.by, m.byName, true),
                h('div', { class: 'bubble' },
                  h('div', { class: 'who' },
                    `${memberName(m.by, m.byName)} · ${agoText(m.at)}`,
                    mine
                      ? h('button', {
                          class: 'del',
                          type: 'button',
                          onClick: () => store.deleteMemo(state.spaceId, s.id, m.id).catch(() => toast('지우지 못했어요')),
                        }, '지우기')
                      : null),
                  m.text));
            })
          : [h('div', { class: 'more' }, '아직 메모가 없어요. 시간이나 장소를 바꾸자는 말도 여기에 남겨 보세요.')]),
      );
    };
    subs.memos?.();
    subs.memos = store.watchMemos(state.spaceId, s.id, (memos) => {
      state.memos = memos;
      paintMemos();
    }, (e) => console.warn(e));
    paintMemos();

    children.push(
      doneRow,
      alarm?.el,
      h('div', { class: 'field-label' }, '설명'),
      note,
      whoLine,
      h('div', { class: 'field-label' }, '메모'),
      memoList,
      memoForm,
      h('button', {
        class: 'btn danger block',
        style: { marginTop: '10px' },
        onClick: async () => {
          if (!(await askYesNo('이 젤리를 지울까요? 달린 메모도 함께 지워져요.', { yes: '지우기', danger: true }))) return;
          s.deleting = true;
          try {
            await store.deleteJelly(state.spaceId, s.id);
            closeSheet();
            toast('지웠어요');
          } catch (e) {
            s.deleting = false;
            console.error(e);
            toast('지우지 못했어요');
          }
        },
      }, '이 젤리 지우기'),
    );

    // Someone else's changes come in: update every field that is not being typed into.
    let latest = existing;
    s.repaint = () => {
      paintWho(latest);
      paintMemos();
    };
    s.sync = (j) => {
      latest = j;
      for (const key of ['date', 'start', 'duration', 'flavor', 'done', 'pinned']) if (!(key in pendingPatch)) draft[key] = j[key];
      if (document.activeElement !== title && !('title' in pendingPatch)) {
        draft.title = j.title;
        title.value = j.title;
      }
      if (document.activeElement !== note && !('note' in pendingPatch)) {
        draft.note = j.note;
        note.value = j.note || '';
      }
      date.value = draft.date;
      if (document.activeElement !== time) time.value = draft.start == null ? '' : hm(draft.start);
      paintWho(j);
      paint();
    };
    subs.jelly?.();
    subs.jelly = store.watchJelly(state.spaceId, s.id, (j) => {
      if (state.sheet !== s) return;
      if (j) {
        s.sync(j);
      } else if (!s.deleting) {
        closeSheet();
        toast('다른 사람이 이 젤리를 지웠어요');
      }
    }, (e) => console.warn(e));
    paintWho(existing);
  }

  function paintWho(j) {
    const parts = [`${subject(j.by, j.byName)} ${agoText(j.createdAt)} 올림`];
    if (j.updatedBy && (j.updatedBy !== j.by || j.updatedAt?.toMillis?.() - (j.createdAt?.toMillis?.() ?? 0) > 1000)) {
      parts.push(`${subject(j.updatedBy, j.updatedByName)} ${agoText(j.updatedAt)} 고침`);
    }
    whoLine.textContent = parts.join(' · ');
  }

  paint();
  sheetFrame(...children);
  if (isNew) setTimeout(() => title.focus(), 50);
}

async function buildInviteSheet() {
  const body = h('div', null, h('div', { class: 'center', style: { minHeight: '120px' } }, '초대 코드를 만드는 중…'));
  sheetFrame(h('div', { class: 'day-title' }, '함께 쓸 사람 초대하기'), body);
  try {
    const { code, id, expiresAt } = await store.makeInvite(state.spaceId);
    const pretty = store.prettyCode(code);
    // Inside the app the page is a local copy, so links always point to the public address.
    const base = testMode ? `${location.origin}${location.pathname}` : publicUrl;
    const link = `${base}#c=${code}`;
    const until = `${expiresAt.getMonth() + 1}월 ${expiresAt.getDate()}일`;
    const text = [
      '공유 젤리 달력에 초대해요.',
      `링크: ${link}`,
      `초대 코드: ${pretty} (${until}까지)`,
      '아이폰: 사파리에서 링크를 열고, 들어가기 전에 공유 버튼 → ‘홈 화면에 추가’를 누른 뒤 홈 화면의 아이콘으로 열어 이 코드를 넣어 주세요.',
      '갤럭시 앱: ‘공유 젤리’ 탭에서 이 코드를 넣어 주세요.',
    ].join('\n');
    body.replaceChildren(
      h('p', { style: { color: 'var(--text-sub)', fontSize: '14px' } },
        '아래 코드나 링크를 함께 쓸 사람에게 보내 주세요. 이 코드로 들어온 사람은 이 달력의 젤리를 보고, 고치고, 메모를 달 수 있어요. 코드를 아는 사람은 누구나 들어올 수 있으니 믿는 사람에게만 보내 주세요.'),
      h('div', { class: 'big-code', 'data-testid': 'invite-code' }, pretty),
      h('div', { class: 'more', style: { textAlign: 'center' } }, `${until}까지 여러 사람이 쓸 수 있어요`),
      h('div', { class: 'row', style: { marginTop: '16px', flexWrap: 'nowrap' } },
        h('button', { class: 'btn', style: { flex: '1' }, onClick: () => shareText(text) }, '보내기'),
        h('button', {
          class: 'btn ghost',
          style: { flex: '1' },
          onClick: () => copyText(text),
        }, '복사하기')),
      h('button', {
        class: 'btn danger block',
        style: { marginTop: '8px' },
        'data-testid': 'cancel-invite',
        onClick: async () => {
          try {
            await store.cancelInvite(id);
            closeSheet();
            toast('이 초대 코드는 이제 쓸 수 없어요');
          } catch (e) {
            console.error(e);
            toast('취소하지 못했어요');
          }
        },
      }, '이 코드 취소하기'),
    );
  } catch (e) {
    console.error(e);
    body.replaceChildren(h('div', { class: 'center', style: { minHeight: '120px' } }, '초대 코드를 만들지 못했어요. 인터넷 연결을 확인해 주세요.'));
  }
}

function shareText(text) {
  if (window.JellyBridge?.share) {
    window.JellyBridge.share(text);
  } else if (navigator.share) {
    navigator.share({ text }).catch(() => {});
  } else {
    copyText(text);
  }
}

function copyText(text) {
  navigator.clipboard?.writeText(text).then(() => toast('복사했어요'), () => toast('복사하지 못했어요'));
}

/**
 * "내 프로필": me as the others see me (character, colour, name), at the top of the menu that the
 * circles at the top right open. A tap changes them.
 */
function profileCard() {
  const me = state.members.find((m) => m.uid === state.uid);
  return h('button', { class: 'look-row profile-card', type: 'button', onClick: () => openReplace({ kind: 'name' }), 'data-testid': 'profile' },
    lookSvg(myLook(), memberBody(state.uid), 58),
    h('span', { class: 'look-row-text' },
      h('strong', null, me?.name || state.myName || '나'),
      h('small', null, '눌러서 캐릭터 · 색 · 이름 바꾸기')),
    h('span', { class: 'look-row-go' }, '›'));
}

function buildMenuSheet() {
  const owner = state.space?.owner;
  const iOwn = owner === state.uid;
  const people = h('div', { class: 'menu-list', style: { marginBottom: '14px' }, 'data-testid': 'members' });
  const paintPeople = () => people.replaceChildren(...state.members.map((m) => h('div', { class: 'row member-row' },
    avatar(m.uid, m.name),
    h('span', { class: 'member-name' }, m.uid === state.uid ? `${m.name || '나'} (나)` : m.name || '이름 없음'),
    m.uid === owner ? h('span', { class: 'chip owner-chip' }, '만든 사람') : null,
    iOwn && m.uid !== state.uid
      ? h('button', {
          class: 'chip remove-chip',
          'data-testid': 'remove',
          onClick: async () => {
            const sure = await askYesNo(`${m.name || '이 사람'}님을 이 달력에서 내보낼까요? 다시 들어오려면 새 초대 코드가 필요해요.`, {
              yes: '내보내기',
              danger: true,
            });
            if (!sure) return;
            try {
              await store.removeMember(state.spaceId, m.uid);
              toast(`${m.name || '그 사람'}님을 내보냈어요`);
            } catch (e) {
              console.error(e);
              toast('내보내지 못했어요');
            }
          },
        }, '내보내기')
      : null)));
  paintPeople();
  state.sheet.repaint = paintPeople;

  const alone = state.members.length <= 1;
  sheetFrame(
    h('div', { class: 'day-title', style: { marginBottom: '8px' } }, '내 프로필'),
    profileCard(),
    h('div', { class: 'day-title', style: { margin: '18px 0 8px' } }, '함께 쓰는 사람'),
    people,
    h('div', { class: 'menu-list' },
      h('button', { class: 'btn block', onClick: () => openReplace({ kind: 'invite' }), 'data-testid': 'invite' }, '함께 쓸 사람 초대하기'),
      h('button', { class: 'btn ghost block', onClick: () => openReplace({ kind: 'rename' }), 'data-testid': 'rename' }, '달력 이름 바꾸기'),
      h('button', { class: 'btn ghost block', onClick: () => openReplace({ kind: 'groups' }), 'data-testid': 'menu-groups' }, '다른 공유 달력 · 달력 더 만들기'),
      h('button', {
        class: 'btn danger block',
        'data-testid': 'leave',
        onClick: async (e) => {
          const button = e.currentTarget;
          const id = state.spaceId;
          const question = alone
            ? '마지막 한 사람이라 나가면 이 달력과 그 안의 젤리, 메모가 모두 지워져요. 나갈까요?'
            : `이 달력에서 나갈까요? 다시 들어오려면 초대 코드가 필요해요.${iOwn ? ' 달력은 가장 먼저 들어온 사람에게 넘어가요.' : ''}`;
          if (!(await askYesNo(question, { yes: '나가기', danger: true }))) return;
          button.disabled = true;
          try {
            await goOut(id, () => store.leave(id), alone ? '달력을 지우고 나왔어요' : '달력에서 나왔어요');
          } catch (err) {
            console.error(err);
            toast('나가지 못했어요. 인터넷 연결을 확인해 주세요');
            button.disabled = false;
          }
        },
      }, '이 달력에서 나가기'),
      iOwn && !alone
        ? h('button', {
            class: 'btn danger block',
            'data-testid': 'delete-space',
            onClick: async (e) => {
              const button = e.currentTarget;
              const id = state.spaceId;
              const sure = await askYesNo('이 달력과 그 안의 젤리, 메모를 모든 사람에게서 지울까요? 되돌릴 수 없어요.', {
                yes: '지우기',
                danger: true,
              });
              if (!sure) return;
              button.disabled = true;
              try {
                await goOut(id, () => store.deleteSpace(id), '달력을 지웠어요');
              } catch (err) {
                console.error(err);
                toast('지우지 못했어요. 인터넷 연결을 확인해 주세요');
                button.disabled = false;
              }
            },
          }, '달력 지우기 (모든 사람에게서)')
        : null),
    inApp ? null : fontPicker(),
    accountBox(),
    !inApp && saved.get('golden.find')
      ? h('button', { class: 'btn ghost block golden-again', onClick: () => openReplace({ kind: 'golden' }) }, '🏆 황금 젤리 코드 보기')
      : null,
    h('p', { class: 'privacy-note' }, PRIVACY_LINE),
  );
}

/** "글씨체": each choice written in its own face. Remembered on this phone only. */
function fontPicker() {
  const row = h('div', { class: 'font-row', 'data-testid': 'fonts' });
  const paint = () => row.replaceChildren(...FONTS.map((f) => h('button', {
    class: `chip font-chip${state.font === f.id ? ' on' : ''}`,
    style: { fontFamily: f.family, fontWeight: String(f.weight) },
    'aria-pressed': String(state.font === f.id),
    onClick: () => {
      applyFont(f.id);
      saved.set('font', f.id);
      paint();
    },
  }, f.name)));
  paint();
  return h('div', { class: 'font-picker' },
    h('div', { class: 'day-title', style: { margin: '18px 0 8px' } }, '글씨체'),
    row);
}

function openReplace(sheet) {
  if (state.sheet?.under && !sheet.under) sheet.under = state.sheet.under;
  state.sheet = sheet;
  buildSheet();
}

/** "+" in the app's "모두" tab: one of my own jellies, or a shared one. */
/** "공유 달력": the calendars this phone is in, one tap to switch, and room for more. */
function buildGroupsSheet() {
  const ids = savedSpaces();
  sheetFrame(
    h('div', { class: 'day-title', style: { marginBottom: '4px' } }, '공유 달력'),
    h('p', { class: 'sheet-sub' }, inApp
      ? '사람마다 따로 달력을 만들어 나눌 수 있어요. ‘모두’ 탭에는 전부 함께 보여요.'
      : '사람마다 따로 달력을 만들어 나눌 수 있어요.'),
    h('div', { class: 'menu-list', 'data-testid': 'group-list' }, ids.map((id) => {
      const g = groups.get(id);
      const current = id === state.spaceId;
      return h('button', {
        class: `group-row${current ? ' on' : ''}`,
        onClick: () => {
          closeSheet();
          if (!current) openSpace(id);
        },
      },
        h('span', { class: 'group-name' }, groupName(g)),
        h('span', { class: 'group-people' }, (g?.members ?? []).slice(0, 4).map((m) => avatar(m.uid, m.name, true, g.members))),
        current ? h('span', { class: 'chip on' }, '보는 중') : null);
    })),
    ids.length < MAX_GROUPS
      ? h('button', { class: 'btn block', style: { marginTop: '12px' }, onClick: () => openReplace({ kind: 'new-group' }), 'data-testid': 'new-group' },
          '+ 달력 더 만들기 · 초대 코드로 들어가기')
      : h('p', { class: 'sheet-sub' }, `공유 달력은 ${MAX_GROUPS}개까지 쓸 수 있어요.`),
  );
}

/** One more calendar: a new one with a name (e.g. "재훈·준헌"), or one from an invite code. */
function buildNewGroupSheet() {
  let busy = false;
  const groupNameBox = h('input', { class: 'input', style: { width: '100%' }, maxlength: '20', placeholder: '예: 재훈·준헌, 우리 둘', 'data-testid': 'group-name' });
  const myName = h('input', { class: 'input', style: { width: '100%' }, maxlength: '20', value: state.myName, 'data-testid': 'group-my-name' });
  const code = state.sheet.code || '';
  const codeBox = h('input', {
    class: 'input code-input',
    placeholder: 'ABCDE-23456',
    maxlength: String(store.CODE_LENGTH + 1),
    autocapitalize: 'characters',
    autocomplete: 'off',
    value: code ? store.prettyCode(code) : '',
    'data-testid': 'group-code',
  });
  const profile = () => {
    const n = myName.value.trim();
    if (!n) {
      toast('이 달력에서 쓸 내 이름을 적어 주세요');
      myName.focus();
      return null;
    }
    return { name: n, color: state.myColor };
  };
  const run = async (button, work, done) => {
    if (busy) return;
    busy = true;
    const label = button.textContent;
    button.disabled = true;
    try {
      await work();
      closeSheet();
      toast(done);
    } catch (err) {
      console.error(err);
      toast(err.message?.includes('invite') ? joinError(err) : '잠시 후 다시 해 주세요');
    } finally {
      busy = false;
      if (button.isConnected) {
        button.disabled = false;
        button.textContent = label;
      }
    }
  };
  sheetFrame(
    h('div', { class: 'day-title', style: { marginBottom: '4px' } }, code ? '초대받은 달력에 들어가기' : '달력 더 만들기'),
    h('div', { class: 'field-label' }, '이 달력에서 쓸 내 이름'),
    myName,
    code ? null : [
      h('div', { class: 'field-label' }, '새 달력 이름'),
      groupNameBox,
      h('button', {
        class: 'btn block',
        style: { marginTop: '12px' },
        'data-testid': 'group-create',
        onClick: (e) => {
          const who = profile();
          if (who) run(e.currentTarget, () => createGroup(groupNameBox.value.trim() || '공유 젤리 달력', who), '새 달력을 만들었어요. ⋯에서 함께 쓸 사람을 초대해 보세요');
        },
      }, '새 달력 만들기'),
      h('div', { class: 'or' }, '초대를 받았다면'),
    ],
    codeBox,
    h('button', {
      class: `btn ${code ? '' : 'ghost '}block`,
      'data-testid': 'group-join',
      onClick: (e) => {
        const who = profile();
        if (!who) return;
        const c = store.cleanCode(codeBox.value);
        if (c.length !== store.CODE_LENGTH) {
          toast(`초대 코드 ${store.CODE_LENGTH}자리를 넣어 주세요`);
          codeBox.focus();
          return;
        }
        run(e.currentTarget, () => joinGroup(c, who), '공유 달력에 들어왔어요');
      },
    }, '초대 코드로 들어가기'),
  );
}

/** "내 계정" in the menu: who is signed in, and what can be done about it. */
function accountBox() {
  const who = store.account();
  if (!who) return null;
  const line = who.kind === 'id'
    ? `아이디 ‘${who.id}’로 로그인했어요.`
    : who.kind === 'google'
      ? `구글 ${who.email}로 로그인했어요.${saved.get('vault') ? '' : ' 열쇠 비밀번호를 정해 두면 새 휴대폰에서도 달력이 바로 열려요.'}`
      : '계정 없이 쓰고 있어요. 계정을 만들어 두면 새 휴대폰에서도 이어서 쓸 수 있어요.';
  return h('div', { class: 'account-box', 'data-testid': 'account-box' },
    h('div', { class: 'day-title', style: { margin: '18px 0 6px' } }, '내 계정'),
    h('p', { class: 'account-note' }, line, ' ', h('a', { href: 'privacy.html', class: 'link-btn' }, '개인정보 안내')),
    h('div', { class: 'menu-list' },
      who.kind === 'guest'
        ? h('button', { class: 'btn block', onClick: () => openReplace({ kind: 'account' }), 'data-testid': 'make-account' }, '계정 만들기')
        : null,
      who.kind === 'google' && !saved.get('vault')
        ? h('button', { class: 'btn ghost block', onClick: () => openReplace({ kind: 'vault-set' }), 'data-testid': 'vault-set' }, '열쇠 비밀번호 정하기 (새 휴대폰용)')
        : null,
      who.kind !== 'guest'
        ? h('button', { class: 'btn ghost block', onClick: signOutHere, 'data-testid': 'sign-out' }, '로그아웃')
        : null,
      who.kind !== 'guest'
        ? h('button', { class: 'btn ghost block danger-text', onClick: () => openReplace({ kind: 'delete-account' }), 'data-testid': 'delete-account' }, '계정 지우기')
        : null));
}

/** Clears this phone of the calendars and their keys (they stay in the account's vault). */
function clearDevice() {
  for (const id of [...groups.keys()]) unwatchGroup(id);
  for (const id of savedSpaces()) saved.set(`key.${id}`, null);
  rememberSpaces([]);
  for (const key of ['space', 'vault', 'vaultLock', 'golden.keys']) saved.set(key, null);
  state.uid = null;
  state.spaceId = null;
  state.space = null;
  state.members = [];
  state.jellies = [];
  screen = null;
}

async function signOutHere() {
  const who = store.account();
  const warn = who?.kind === 'google' && !saved.get('vault')
    ? '로그아웃할까요? 열쇠 비밀번호를 정하지 않아서, 다시 로그인하면 함께 쓰는 사람에게 초대 코드를 받아야 달력이 열려요.'
    : '로그아웃할까요? 이 휴대폰에서 공유 달력이 닫혀요. 다시 로그인하면 그대로 열려요.';
  if (!(await askYesNo(warn, { yes: '로그아웃' }))) return;
  clearTimeout(vaultTimer);
  closeSheet();
  clearDevice();
  await store.signOutAccount().catch((e) => console.warn(e));
  render();
  toast('로그아웃했어요');
}

/** A guest from before accounts makes one; their calendars stay theirs. */
function buildAccountSheet() {
  sheetFrame(
    h('div', { class: 'day-title', style: { marginBottom: '4px' } }, '계정 만들기'),
    h('p', { class: 'sheet-sub' }, '지금 쓰는 공유 달력은 그대로 이어져요. 새 휴대폰에서는 로그인만 하면 열려요.'),
    accountForm({ link: true }),
  );
}

/** Google, on a new phone: the vault password opens the calendars kept in the vault. */
function buildVaultOpenSheet() {
  const code = state.sheet.code || '';
  const box = h('input', { class: 'input', style: { width: '100%' }, type: 'password', placeholder: '열쇠 비밀번호', 'data-testid': 'vault-pass' });
  sheetFrame(
    h('div', { class: 'day-title', style: { marginBottom: '4px' } }, '열쇠 비밀번호'),
    h('p', { class: 'sheet-sub' }, '예전에 정한 열쇠 비밀번호를 넣으면 쓰던 공유 달력이 이 휴대폰에서도 열려요.'),
    box,
    h('button', {
      class: 'btn block',
      style: { marginTop: '12px' },
      'data-testid': 'vault-open',
      onClick: async (e) => {
        const button = e.currentTarget;
        button.disabled = true;
        try {
          const raw = await store.googleVaultSecret(box.value);
          await store.readVault(raw);
          const restored = await restoreFromVault(raw, 'passphrase');
          closeSheet();
          enterAccount(code);
          toast(`쓰던 공유 달력 ${restored}개를 열었어요`);
        } catch (err) {
          console.warn(err);
          toast('열쇠 비밀번호가 맞지 않아요');
          button.disabled = false;
        }
      },
    }, '달력 열기'),
    h('button', {
      class: 'btn ghost block',
      style: { marginTop: '8px' },
      onClick: () => {
        saved.set('vaultLock', 'passphrase');
        closeSheet();
        enterAccount(code);
      },
    }, '나중에 할게요 (초대 코드로 들어가기)'),
  );
}

/** Google: a separate vault password, so a new phone can open the calendars without invites. */
function buildVaultSetSheet() {
  const first = h('input', { class: 'input', style: { width: '100%' }, type: 'password', placeholder: `열쇠 비밀번호 (${store.MIN_PASSWORD}자 이상)`, autocomplete: 'new-password', 'data-testid': 'vault-new' });
  const again = h('input', { class: 'input', style: { width: '100%', marginTop: '8px' }, type: 'password', placeholder: '한 번 더', autocomplete: 'new-password', 'data-testid': 'vault-again' });
  sheetFrame(
    h('div', { class: 'day-title', style: { marginBottom: '4px' } }, '열쇠 비밀번호 정하기'),
    h('p', { class: 'sheet-sub' }, '공유 달력의 열쇠를 이 비밀번호로 잠가 보관해요. 구글도, 서버를 운영하는 사람도 열 수 없어요. 잊으면 되찾을 수 없으니 잘 기억해 주세요.'),
    first,
    again,
    h('button', {
      class: 'btn block',
      style: { marginTop: '12px' },
      'data-testid': 'vault-save',
      onClick: async (e) => {
        if (first.value.length < store.MIN_PASSWORD) return toast(`${store.MIN_PASSWORD}자 이상으로 정해 주세요`);
        if (first.value !== again.value) return toast('두 번 넣은 비밀번호가 달라요');
        e.currentTarget.disabled = true;
        saved.set('vault', await store.googleVaultSecret(first.value));
        saved.set('vaultLock', 'passphrase');
        syncVault();
        closeSheet();
        toast('열쇠 비밀번호를 정했어요');
      },
    }, '정하기'),
  );
}

/** Deletes the account: leaves every calendar first (a calendar left empty goes with it). */
function buildDeleteAccountSheet() {
  const who = store.account();
  const pw = who?.kind === 'id'
    ? h('input', { class: 'input', style: { width: '100%' }, type: 'password', placeholder: '비밀번호', autocomplete: 'current-password', 'data-testid': 'delete-password' })
    : null;
  sheetFrame(
    h('div', { class: 'day-title', style: { marginBottom: '4px' } }, '계정 지우기'),
    h('p', { class: 'sheet-sub' }, '계정과 열쇠 보관함을 지우고, 들어가 있는 공유 달력에서 모두 나가요. 혼자 남아 있던 달력은 젤리와 메모까지 함께 지워져요. 되돌릴 수 없어요.'),
    pw,
    h('button', {
      class: 'btn danger block',
      style: { marginTop: '12px' },
      'data-testid': 'delete-account-confirm',
      onClick: async (e) => {
        const button = e.currentTarget;
        button.disabled = true;
        try {
          if (pw) await store.confirmPassword(pw.value);
          for (const id of savedSpaces()) {
            leaving.add(id);
            await store.leave(id).catch((err) => console.warn(err));
          }
          await store.deleteAccount();
        } catch (err) {
          console.error(err);
          toast(err?.code === 'auth/requires-recent-login' ? '보안을 위해 로그아웃했다가 다시 로그인한 뒤 지워 주세요' : idError(err));
          button.disabled = false;
          return;
        }
        clearTimeout(vaultTimer);
        closeSheet();
        clearDevice();
        render();
        toast('계정을 지웠어요');
      },
    }, '계정 지우기'),
  );
}

/** "달력 이름 바꾸기": everyone in the calendar sees the new name. */
function buildRenameSheet() {
  const box = h('input', { class: 'input', style: { width: '100%' }, maxlength: '20', value: groupName(groups.get(state.spaceId)), 'data-testid': 'rename-input' });
  sheetFrame(
    h('div', { class: 'day-title', style: { marginBottom: '8px' } }, '달력 이름 바꾸기'),
    box,
    h('button', {
      class: 'btn block',
      style: { marginTop: '12px' },
      'data-testid': 'rename-save',
      onClick: async (e) => {
        const name = box.value.trim();
        if (!name) return box.focus();
        e.currentTarget.disabled = true;
        try {
          await store.renameSpace(state.spaceId, name);
          closeSheet();
          toast('달력 이름을 바꿨어요');
        } catch (err) {
          console.error(err);
          toast('바꾸지 못했어요');
          e.currentTarget.disabled = false;
        }
      },
    }, '저장'),
  );
}

function buildAddSheet() {
  const date = state.sheet.date;
  sheetFrame(
    h('div', { class: 'day-title', style: { marginBottom: '6px' } }, dayTitle(date)),
    h('p', { style: { color: 'var(--text-sub)', fontSize: '14px', margin: '0 0 14px' } }, '어떤 젤리를 넣을까요?'),
    h('div', { class: 'menu-list' },
      h('button', {
        class: 'btn block',
        'data-testid': 'add-personal',
        onClick: () => {
          closeSheet();
          bridge?.createPersonal?.(date);
        },
      }, '내 젤리 만들기 (나만 봐요)'),
      shownGroups().map((g, i) => h('button', {
        class: 'btn ghost block',
        'data-testid': i === 0 ? 'add-shared' : `add-shared-${i}`,
        onClick: () => {
          openSpace(g.id);
          openReplace({ kind: 'new', date });
        },
      }, shownGroups().length > 1 ? `‘${groupName(g)}’에 올리기` : '공유 젤리 올리기 (함께 쓰는 사람도 봐요)'))),
  );
}

/** "내 캐릭터": my character as it looks in [color], tap to pick another. */
function lookRow(color, onOpen) {
  const look = myLook();
  return h('button', { class: 'look-row', type: 'button', onClick: onOpen, 'data-testid': 'look-open' },
    lookSvg(look, FLAVORS[color]?.base ?? MEMBER_COLORS[0], 58),
    h('span', { class: 'look-row-text' },
      h('strong', null, lookName(look)),
      h('small', null, '눌러서 바꾸기 · 직업마다 두 가지')),
    h('span', { class: 'look-row-go' }, '›'));
}

/** "내 캐릭터 고르기": the plain jelly, the plain face, and every job in two outfits. */
function buildLookSheet() {
  const s = state.sheet;
  const color = FLAVORS[s.color ?? state.myColor]?.base ?? MEMBER_COLORS[0];
  const current = myLook();
  const done = () => {
    if (s.back) openReplace(s.back);
    else {
      closeSheet();
      welcomeRepaint?.();
    }
  };
  const cell = (look, label, sub) => h('button', {
    class: `look-cell${sameLook(look, current) ? ' on' : ''}`,
    type: 'button',
    'data-testid': `look-${look ? `${look.job}-${look.v}` : 'none'}`,
    onClick: () => {
      pickLook(look);
      toast(`내 캐릭터: ${lookName(look)}`);
      done();
    },
  }, lookSvg(look, color, 66), h('span', { class: 'look-cell-name' }, label, sub ? h('small', null, sub) : null));
  const cells = [cell(null, '민무늬')];
  for (const job of LOOK_JOBS) for (const v of [0, 1]) cells.push(cell({ job: job.id, v }, job.name, String(v + 1)));
  sheetFrame(
    h('div', { class: 'day-title' }, '내 캐릭터 고르기'),
    h('p', { class: 'sheet-sub' }, bridge?.setLook
      ? '공유 달력에서 내가 올린 젤리와 내 동그라미가 이 모습이 돼요. 앱 설정의 ‘내 캐릭터’와 같아요.'
      : '공유 달력에서 내가 올린 젤리와 내 동그라미가 이 모습이 돼요. 직업마다 두 가지가 있어요.'),
    h('div', { class: 'look-grid', 'data-testid': 'look-grid' }, ...cells),
  );
}

function buildNameSheet() {
  let color = state.sheet.color ?? state.myColor;
  const name = h('input', { class: 'input', style: { width: '100%' }, maxlength: '20', value: state.sheet.name ?? state.myName, 'data-testid': 'profile-name' });
  const colors = h('div', { class: 'row' });
  const lookBox = h('div');
  const paintColors = () => {
    colors.replaceChildren(...colorSwatches(color, (i) => {
      color = i;
      paintColors();
    }));
    lookBox.replaceChildren(lookRow(color, () => openReplace({ kind: 'look', color, back: { kind: 'name', name: name.value, color } })));
  };
  paintColors();
  sheetFrame(
    h('div', { class: 'day-title' }, '내 프로필 바꾸기'),
    h('div', { class: 'field-label' }, '함께 쓰는 사람에게 보이는 이름'),
    name,
    h('div', { class: 'field-label' }, '내 색 (내 캐릭터의 색이자 새 젤리의 기본 색)'),
    colors,
    h('div', { class: 'field-label' }, '내 캐릭터 (모든 공유 달력에서 같아요)'),
    lookBox,
    h('button', {
      class: 'btn block',
      style: { marginTop: '14px' },
      'data-testid': 'profile-save',
      onClick: async () => {
        const n = name.value.trim();
        if (!n) return;
        rememberProfile(n, color);
        try {
          await store.updateProfile(state.spaceId, { name: n, color, look: myLook() });
          closeSheet();
          toast('프로필을 바꿨어요');
        } catch (e) {
          console.error(e);
          toast('바꾸지 못했어요');
        }
      },
    }, '저장'),
  );
}

// ---------------------------------------------------------------- service worker

if ('serviceWorker' in navigator && location.protocol === 'https:' && !inApp) {
  navigator.serviceWorker.register('sw.js').catch(() => {});
}

boot();
