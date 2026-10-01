// 공유 젤리: a month of jellies shared by two (or a few) people, on iPhone, Android or any browser.
import { firebaseConfig, publicUrl } from './config.js';
import * as store from './store.js';
import { JellyBox, GOLDEN_FLAVOR, setTitleFace } from './box.js';
import { holidayOn } from './holidays.js';

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
const MAKER = '황OO';
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
const state = {
  uid: null,
  myName: saved.get('name') || '',
  // My colour (a flavour index): my name's circle, and the colour my new jellies start with.
  myColor: savedColor >= 0 && savedColor < 10 ? savedColor : Math.floor(Math.random() * 10),
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

const appEl = () => document.getElementById('app');

// ---------------------------------------------------------------- members

function memberIndex(uid) {
  const i = state.members.findIndex((m) => m.uid === uid);
  return i < 0 ? state.members.length : i;
}

/** The colour someone picked, as a flavour index, or null when they have not picked one. */
function memberFlavor(uid) {
  const color = state.members.find((m) => m.uid === uid)?.color;
  return Number.isInteger(color) && color >= 0 && color < FLAVORS.length ? color : null;
}

function memberColor(uid) {
  const flavor = memberFlavor(uid);
  return flavor == null ? MEMBER_COLORS[memberIndex(uid) % MEMBER_COLORS.length] : FLAVORS[flavor].deep;
}

/** Letters on a member's colour: dark on the light lemon, white on the rest. */
function memberInk(uid) {
  return memberFlavor(uid) === 2 ? FLAVORS[2].ink : '#FFFFFF';
}

/** The name someone goes by in the calendar, also for oneself. */
function realName(uid, fallback) {
  return state.members.find((m) => m.uid === uid)?.name || fallback || '알 수 없음';
}

/** "나" for oneself, otherwise the person's name. */
function memberName(uid, fallback) {
  return uid === state.uid ? '나' : realName(uid, fallback);
}

/** "내가" for oneself, otherwise the bare name: "내가 올림", "창현 올림". */
function subject(uid, fallback) {
  return uid === state.uid ? '내가' : realName(uid, fallback);
}

function avatar(uid, fallback, small = false) {
  const name = realName(uid, fallback);
  return h(
    'span',
    { class: `avatar${small ? ' small' : ''}`, style: { background: memberColor(uid), color: memberInk(uid) }, title: name },
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
    state.uid = await store.start(config, { emulator });
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
    if (state.sheet) hideSheet();
  });

  ready = true;
  pendingCode = codeFromLink();
  if (state.spaceId) {
    const key = saved.get(`key.${state.spaceId}`);
    if (key) await store.useSpaceKey(state.spaceId, key).catch((e) => console.warn(e));
    openSpace(state.spaceId);
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
  poke() {
    readHost();
    render();
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
  state.host = next;
  if (next?.font) applyFont(next.font);
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
  return { doubleTap: state.host?.doubleTap !== false, longPress: state.host?.longPress !== false };
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

function renderWelcome(code = '') {
  closeSubs();
  leaveBox();
  screen = 'welcome';
  document.body.classList.remove('fill');
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
  const paintColors = () => colors.replaceChildren(...colorSwatches(color, (i) => {
    color = i;
    paintColors();
  }));
  paintColors();
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
      rememberProfile(n, color);
      const { spaceId, key } = await store.createSpace('공유 젤리 달력', { name: n, color });
      saved.set(`key.${spaceId}`, key);
      openSpace(spaceId);
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
      rememberProfile(n, color);
      const { spaceId, key } = await store.joinWithCode(c, { name: n, color });
      saved.set(`key.${spaceId}`, key);
      openSpace(spaceId);
      toast('공유 달력에 들어왔어요');
    } catch (err) {
      console.error(err);
      toast(err.message === 'expired-invite' ? '기간이 지난 초대 코드예요. 새 코드를 받아 주세요' : '초대 코드를 찾지 못했어요. 코드를 다시 확인해 주세요');
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
      h('div', { class: 'field-label' }, '내 색 (내 이름 동그라미와 새 젤리의 기본 색)'),
      colors,
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
      h('p', { class: 'privacy-note' }, PRIVACY_LINE),
      installHint(),
    ),
  );
}

const PRIVACY_LINE = '이름, 색, 젤리, 메모는 달력에 들어온 사람의 기기에서만 열리도록 암호화되어 저장돼요. 서버를 운영하는 사람도 내용을 볼 수 없어요.';

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

// ---------------------------------------------------------------- space

function closeSubs() {
  for (const key of Object.keys(subs)) {
    subs[key]?.();
    subs[key] = null;
  }
}

function openSpace(id) {
  closeSubs();
  state.spaceId = id;
  saved.set('space', id);
  // An invite code in the address is used up once we are in.
  if (location.hash) history.replaceState(history.state, '', location.pathname + location.search);
  pendingCode = '';

  const lost = (e) => {
    console.warn(e);
    // Let go by the owner, or the calendar is gone: start over.
    if (e?.code === 'permission-denied') forgetSpace('이 공유 달력에 더 이상 들어갈 수 없어요');
  };
  subs.space = store.watchSpace(id, (space) => {
    if (!space) return forgetSpace('공유 달력이 지워졌어요');
    if (space.locked) return forgetSpace('이 기기에 달력 열쇠가 없어요. 초대 코드를 다시 받아 주세요');
    state.space = space;
    render();
  }, lost);
  subs.members = store.watchMembers(id, (members) => {
    state.members = members;
    const mine = members.find((m) => m.uid === state.uid);
    if (mine?.name && (mine.name !== state.myName || mine.color !== state.myColor)) {
      rememberProfile(mine.name, Number.isInteger(mine.color) ? mine.color : state.myColor);
    }
    render();
    state.sheet?.repaint?.();
  }, lost);
  subs.jellies = store.watchJellies(id, (jellies) => {
    state.jellies = jellies;
    render();
  }, (e) => console.warn(e));
  render();
}

/** This device stops using the calendar (after leaving it, deleting it, or being let go). */
function forgetSpace(message) {
  const id = state.spaceId;
  closeSubs();
  if (state.sheet) hideSheet();
  if (id) {
    saved.set(`key.${id}`, null);
    store.forgetSpaceKey(id);
  }
  saved.set('space', null);
  state.spaceId = null;
  state.space = null;
  state.members = [];
  state.jellies = [];
  screen = null;
  render();
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
}

function goToday() {
  if (isAll()) {
    bridge?.showDay?.(todayIso());
    return;
  }
  state.month = firstOfMonth(new Date());
  state.selected = todayIso();
  render();
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
  const shared = usable() ? jelliesOn(iso).map((j) => ({
    kind: 'shared', id: j.id, title: j.title, flavor: j.flavor, done: !!j.done, pinned: !!j.pinned,
    start: j.start ?? null, duration: j.duration, j,
  })) : [];
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
          if (!confirm(mine ? '예전 달력과 그 안의 젤리를 서버에서 지울까요?' : '예전 달력에서 나갈까요?')) return;
          e.currentTarget.disabled = true;
          try {
            if (mine) await store.deleteSpace(state.spaceId);
            else await store.removeMember(state.spaceId, state.uid);
          } catch (err) {
            console.error(err);
          }
          forgetSpace(mine ? '예전 달력을 지웠어요. 새 달력을 만들어 다시 초대해 주세요' : '예전 달력에서 나왔어요');
        },
      }, mine ? '예전 달력 지우고 새로 시작' : '예전 달력에서 나가기')),
  );
}

function header({ title, sub, testid, prev, next, prevLabel, nextLabel }) {
  return h('div', { class: 'header' },
    h('button', { class: 'icon-btn', 'aria-label': prevLabel, onClick: prev }, '‹'),
    h('div', { class: 'head-text' },
      h('div', { class: 'month-title', 'data-testid': testid }, title),
      h('div', { class: 'space-name' }, sub)),
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
    h('button', { class: 'jelly add-fab squish', vars: flavorVars(0), 'aria-label': '젤리 올리기', onClick: addJelly, 'data-testid': 'add' }, '+'));
}

/** "+": a shared jelly on the day shown; in "모두", first the choice between mine and shared. */
function addJelly() {
  const date = selectedDay();
  if (!date) return;
  if (!isAll()) openSheet({ kind: 'new', date });
  else if (usable()) openSheet({ kind: 'add', date });
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
    sub: all ? '내 젤리와 공유 젤리' : state.space?.name || '공유 젤리 달력',
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
        onClick: () => pick(iso),
      },
        h('span', { class: 'day-top' },
          h('span', { class: `num${tone}` }, d.getDate()),
          rest > 0
            ? h('span', { class: 'more' }, `+${rest}`)
            : holiday ? h('span', { class: 'hol' }, holiday.short) : null),
        shown.map((it) => h('span', {
          class: `jelly mini${it.pinned ? ' pinned' : ''}${it.kind === 'personal' ? ' mine' : ''}`,
          vars: { ...flavorVars(it.flavor), who: it.kind === 'shared' ? memberColor(it.j.by) : 'transparent' },
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

/** The picked day under the month: its jellies to do, and the finished ones folded away. */
function dayPanel(iso) {
  const items = dayItems(iso);
  const open = items.filter((it) => !it.done);
  const done = items.filter((it) => it.done);
  const holiday = holidayOn(iso);
  const count = open.length || done.length
    ? `할 젤리 ${open.length}개${done.length ? ` · 다 먹은 젤리 ${done.length}개` : ''}`
    : '아직 비어 있어요';
  const toCard = (it, i) => (it.kind === 'shared' ? card(it.j, i) : personalCard(it.p, i));
  return h('div', { class: 'day-panel' },
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

function card(j, i) {
  const edited = j.updatedBy && j.updatedBy !== j.by ? `${subject(j.updatedBy, j.updatedByName)} 고침` : null;
  return h('button', {
    class: `jelly card squish${j.done ? ' done' : ''}`,
    vars: flavorVars(j.flavor),
    style: { animationDelay: `${-(i * 0.7)}s` },
    onClick: () => openSheet({ kind: 'jelly', id: j.id }),
    'data-testid': 'card',
  },
    h('div', { class: 'title' }, `${j.pinned ? '📌 ' : ''}${j.done ? '✓ ' : ''}${j.title}`),
    h('div', { class: 'meta' },
      h('span', null, timeText(j)),
      h('span', { class: 'badge' }, avatar(j.by, j.byName, true), `${subject(j.by, j.byName)} 올림`),
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

function sharedItem(j) {
  return {
    key: `s:${j.id}`,
    title: j.title,
    start: j.start,
    duration: j.duration,
    flavor: j.flavor,
    done: !!j.done,
    badge: { text: initial(realName(j.by, j.byName)), color: memberColor(j.by), ink: memberInk(j.by) },
    gold: state.goldenKeys.has(`s:${j.id}`),
    pinned: !!j.pinned,
    ref: { kind: 'shared', id: j.id },
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
  else openSheet({ kind: 'jelly', id: item.ref.id });
}

function toggleItem(item) {
  if (item.ref.kind === 'personal') {
    bridge?.togglePersonal?.(item.ref.id);
    return;
  }
  const j = state.jellies.find((x) => x.id === item.ref.id);
  if (!j) return;
  store.updateJelly(state.spaceId, j.id, { done: !j.done }, state.myName).catch((e) => {
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
  const shared = usable() ? jelliesOn(host.date) : [];
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
        `공유 젤리 ${shared.length}`),
      h('span', { class: 'legend-note' }, '동그라미 글자: 공유 젤리를 올린 사람')),
  );
  v.note.replaceChildren(...[
    usable()
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
  v.box.set([...personal.map(personalItem), ...shared.map(sharedItem)]);
}

// ---------------------------------------------------------------- sheets

function openSheet(sheet) {
  state.sheet = sheet;
  if (!history.state?.sheet) history.pushState({ sheet: true }, '');
  buildSheet();
}

function closeSheet() {
  if (history.state?.sheet) history.back();
  else hideSheet();
}

function hideSheet() {
  subs.memos?.();
  subs.memos = null;
  subs.jelly?.();
  subs.jelly = null;
  state.sheet = null;
  state.memos = [];
  document.querySelector('.scrim')?.remove();
  document.querySelector('.sheet')?.remove();
}

function sheetFrame(...children) {
  document.querySelector('.scrim')?.remove();
  document.querySelector('.sheet')?.remove();
  const scrim = h('div', { class: 'scrim', onClick: closeSheet });
  const sheet = h('div', { class: 'sheet', role: 'dialog', 'aria-modal': 'true' }, h('div', { class: 'handle' }), ...children);
  document.body.append(scrim, sheet);
  return sheet;
}

function buildSheet() {
  const s = state.sheet;
  if (!s) return;
  if (s.kind === 'jelly' || s.kind === 'new') buildJellySheet();
  else if (s.kind === 'add') buildAddSheet();
  else if (s.kind === 'invite') buildInviteSheet();
  else if (s.kind === 'menu') buildMenuSheet();
  else if (s.kind === 'name') buildNameSheet();
  else if (s.kind === 'golden') buildGoldenSheet();
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

/** How many jellies of [date] are pinned, leaving out [exceptId]. */
function pinsOn(date, exceptId) {
  return state.jellies.filter((j) => j.date === date && j.pinned && j.id !== exceptId).length;
}

function buildJellySheet() {
  const s = state.sheet;
  const isNew = s.kind === 'new';
  const existing = isNew ? null : s.seed || state.jellies.find((x) => x.id === s.id);
  if (!isNew && !existing) return hideSheet();
  // The draft that is shown and edited; for an existing jelly, changes are saved as they happen.
  const draft = existing
    ? { ...existing }
    : { title: '', date: s.date || state.selected, start: null, duration: 60, flavor: state.myColor, done: false, note: '', pinned: false };

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
  const noTime = h('button', { class: 'chip', type: 'button' }, '시간 없음');
  const length = stretchLength(() => draft.duration, (m) => save({ duration: m }), () => save({}, true));
  const colorRow = h('div', { class: 'row' });
  const doneSwitch = h('span', { class: 'switch' });
  const doneRow = h('button', { class: 'toggle', type: 'button', 'data-testid': 'done' }, h('span', null, '✓ 다 먹었어요'), doneSwitch);
  const pinSwitch = h('span', { class: 'switch' });
  const pinNote = h('small', { class: 'toggle-note' });
  const pinRow = h('button', { class: 'toggle', type: 'button', 'data-testid': 'pin' },
    h('span', { class: 'toggle-text' }, h('span', null, '📌 젤위로 고정'), pinNote), pinSwitch);
  const selfId = isNew ? null : s.id;
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
    doneSwitch.classList.toggle('on', !!draft.done);
  }

  title.addEventListener('input', () => {
    const t = title.value.trim();
    if (t) {
      save({ title: t });
    } else {
      draft.title = '';
      paint();
    }
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
    if (!time.value) return save({ start: null }, true);
    const [hh, mm] = time.value.split(':').map(Number);
    save({ start: hh * 60 + mm }, true);
  });
  noTime.addEventListener('click', () => {
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
    h('div', { class: 'sheet-kicker' }, isNew ? '새 젤리 빚기' : '젤리 다듬기'),
    preview,
    title,
    h('div', { class: 'field-label' }, '맛'),
    colorRow,
    h('div', { class: 'field-label' }, '길이 · 쭉 당기면 늘어나요'),
    length.el,
    h('div', { class: 'field-label' }, '언제'),
    h('div', { class: 'row' }, date, time, noTime),
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
          showDay(fields.date);
          state.sheet = {
            kind: 'jelly',
            id,
            seed: { ...fields, id, by: state.uid, byName: state.myName, updatedBy: state.uid, memoCount: 0, pending: true },
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
          if (!confirm('이 젤리를 지울까요? 달린 메모도 함께 지워져요.')) return;
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
            if (!confirm(`${m.name || '이 사람'}님을 이 달력에서 내보낼까요? 다시 들어오려면 새 초대 코드가 필요해요.`)) return;
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
    h('div', { class: 'day-title', style: { marginBottom: '8px' } }, '함께 쓰는 사람'),
    people,
    h('div', { class: 'menu-list' },
      h('button', { class: 'btn block', onClick: () => openReplace({ kind: 'invite' }), 'data-testid': 'invite' }, '함께 쓸 사람 초대하기'),
      h('button', { class: 'btn ghost block', onClick: () => openReplace({ kind: 'name' }), 'data-testid': 'profile' }, '내 이름과 색 바꾸기'),
      h('button', {
        class: 'btn danger block',
        'data-testid': 'leave',
        onClick: async (e) => {
          const ask = alone
            ? '마지막 한 사람이라 나가면 이 달력과 그 안의 젤리, 메모가 모두 지워져요. 나갈까요?'
            : `이 달력에서 나갈까요? 다시 들어오려면 초대 코드가 필요해요.${iOwn ? ' 달력은 가장 먼저 들어온 사람에게 넘어가요.' : ''}`;
          if (!confirm(ask)) return;
          e.currentTarget.disabled = true;
          try {
            await store.leave(state.spaceId);
          } catch (err) {
            console.error(err);
            toast('나가지 못했어요. 인터넷 연결을 확인해 주세요');
            e.currentTarget.disabled = false;
            return;
          }
          forgetSpace(alone ? '달력을 지우고 나왔어요' : '달력에서 나왔어요');
        },
      }, '이 달력에서 나가기'),
      iOwn && !alone
        ? h('button', {
            class: 'btn danger block',
            'data-testid': 'delete-space',
            onClick: async (e) => {
              if (!confirm('이 달력과 그 안의 젤리, 메모를 모든 사람에게서 지울까요? 되돌릴 수 없어요.')) return;
              e.currentTarget.disabled = true;
              try {
                await store.deleteSpace(state.spaceId);
              } catch (err) {
                console.error(err);
                toast('지우지 못했어요. 인터넷 연결을 확인해 주세요');
                e.currentTarget.disabled = false;
                return;
              }
              forgetSpace('달력을 지웠어요');
            },
          }, '달력 지우기 (모든 사람에게서)')
        : null),
    inApp ? null : fontPicker(),
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
  state.sheet = sheet;
  buildSheet();
}

/** "+" in the app's "모두" tab: one of my own jellies, or a shared one. */
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
      h('button', {
        class: 'btn ghost block',
        'data-testid': 'add-shared',
        onClick: () => openReplace({ kind: 'new', date }),
      }, '공유 젤리 올리기 (함께 쓰는 사람도 봐요)')),
  );
}

function buildNameSheet() {
  let color = state.myColor;
  const name = h('input', { class: 'input', style: { width: '100%' }, maxlength: '20', value: state.myName, 'data-testid': 'profile-name' });
  const colors = h('div', { class: 'row' });
  const paintColors = () => colors.replaceChildren(...colorSwatches(color, (i) => {
    color = i;
    paintColors();
  }));
  paintColors();
  sheetFrame(
    h('div', { class: 'day-title' }, '내 이름과 색 바꾸기'),
    h('div', { class: 'field-label' }, '함께 쓰는 사람에게 보이는 이름'),
    name,
    h('div', { class: 'field-label' }, '내 색 (내 이름 동그라미와 새 젤리의 기본 색)'),
    colors,
    h('button', {
      class: 'btn block',
      style: { marginTop: '14px' },
      'data-testid': 'profile-save',
      onClick: async () => {
        const n = name.value.trim();
        if (!n) return;
        rememberProfile(n, color);
        try {
          await store.updateProfile(state.spaceId, { name: n, color });
          closeSheet();
          toast('이름과 색을 바꿨어요');
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
