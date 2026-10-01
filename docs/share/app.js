// 공유 젤리: a month of jellies shared by two (or a few) people, on iPhone, Android or any browser.
import { firebaseConfig } from './config.js';
import * as store from './store.js';

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
const DURATIONS = [30, 60, 90, 120, 180, 240];
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

const inApp = /JellyCalendarApp/.test(navigator.userAgent);
const isIos = /iPhone|iPad|iPod/.test(navigator.userAgent);
const standalone = window.matchMedia?.('(display-mode: standalone)').matches || navigator.standalone === true;

const today = isoDay(new Date());
const state = {
  uid: null,
  myName: saved.get('name') || '',
  spaceId: saved.get('space'),
  space: null,
  members: [],
  jellies: [],
  month: firstOfMonth(new Date()),
  selected: today,
  sheet: null, // { kind: 'jelly' | 'new' | 'invite' | 'menu' | 'name', ... }
  memos: [],
  online: navigator.onLine,
};
const subs = { space: null, members: null, jellies: null, jelly: null, memos: null };

// ---------------------------------------------------------------- dates

function pad(n) {
  return String(n).padStart(2, '0');
}

function isoDay(d) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
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

/** Six Monday-to-Sunday weeks covering the month. */
function gridDays(month) {
  const offset = (month.getDay() + 6) % 7;
  const start = addDays(month, -offset);
  return Array.from({ length: 42 }, (_, i) => addDays(start, i));
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

function flavorVars(i) {
  const f = FLAVORS[((i % FLAVORS.length) + FLAVORS.length) % FLAVORS.length];
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

function memberColor(uid) {
  return MEMBER_COLORS[memberIndex(uid) % MEMBER_COLORS.length];
}

/** The name someone goes by in the space, also for oneself. */
function realName(uid, fallback) {
  return state.members.find((m) => m.uid === uid)?.name || fallback || '상대';
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
    { class: `avatar${small ? ' small' : ''}`, style: { background: memberColor(uid) }, title: name },
    Array.from(name || '?')[0],
  );
}

// ---------------------------------------------------------------- boot

async function boot() {
  const params = new URLSearchParams(location.search);
  const emulator = params.has('emu') && ['localhost', '127.0.0.1'].includes(location.hostname);
  const config = emulator
    ? { apiKey: 'demo-key', authDomain: 'localhost', projectId: 'demo-jelly', appId: 'demo-app' }
    : firebaseConfig;
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
    renderMain();
  });
  window.addEventListener('offline', () => {
    state.online = false;
    renderMain();
  });
  window.addEventListener('popstate', () => {
    if (state.sheet) hideSheet();
  });

  const code = codeFromLink();
  if (state.spaceId) openSpace(state.spaceId);
  else renderWelcome(code);
}

/** "#c=ABCD2345" in the address, from an invite link. */
function codeFromLink() {
  const m = location.hash.match(/[#&]c=([0-9A-Za-z-]+)/);
  return m ? store.cleanCode(m[1]) : '';
}

// ---------------------------------------------------------------- welcome

function renderWelcome(code = '') {
  closeSubs();
  let busy = false;
  const name = h('input', {
    class: 'input',
    placeholder: '내 이름 (상대에게 보여요)',
    maxlength: '20',
    value: state.myName,
    'data-testid': 'name',
  });
  const codeBox = h('input', {
    class: 'input code-input',
    placeholder: 'ABCD-2345',
    maxlength: '9',
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
      rememberName(n);
      const id = await store.createSpace('공유 젤리 달력', n);
      openSpace(id);
      toast('공유 달력을 만들었어요. 메뉴에서 상대를 초대해 보세요');
    } catch (e) {
      console.error(e);
      toast('만들지 못했어요. 잠시 후 다시 해 주세요');
    } finally {
      busy = false;
    }
  }

  async function join() {
    const n = needName();
    if (!n || busy) return;
    const c = store.cleanCode(codeBox.value);
    if (c.length !== 8) {
      toast('초대 코드 8자리를 넣어 주세요');
      codeBox.focus();
      return;
    }
    busy = true;
    try {
      rememberName(n);
      const id = await store.joinWithCode(c, n);
      openSpace(id);
      toast('공유 달력에 들어왔어요');
    } catch (e) {
      console.error(e);
      toast(e.message === 'expired-invite' ? '기간이 지난 초대 코드예요. 새 코드를 받아 주세요' : '초대 코드를 찾지 못했어요');
    } finally {
      busy = false;
    }
  }

  appEl().replaceChildren(
    h('div', { class: 'welcome' },
      h('img', { class: 'logo', src: 'icons/icon-192.png', alt: '' }),
      h('h1', null, '공유 젤리'),
      h('p', null, '둘이 함께 보고 고치는 한 달 젤리 달력이에요.'),
      h('div', { class: 'field-label' }, '내 이름'),
      name,
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
      installHint(),
    ),
  );
}

function rememberName(n) {
  state.myName = n;
  saved.set('name', n);
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

  const lost = (e) => {
    console.warn(e);
    if (e?.code === 'permission-denied') {
      // Left the space, or the space is gone: start over.
      saved.set('space', null);
      state.spaceId = null;
      renderWelcome();
    }
  };
  subs.space = store.watchSpace(id, (space) => {
    state.space = space;
    renderMain();
  }, lost);
  subs.members = store.watchMembers(id, (members) => {
    state.members = members;
    const mine = members.find((m) => m.uid === state.uid);
    if (mine && mine.name !== state.myName) rememberName(mine.name);
    renderMain();
    state.sheet?.repaint?.();
  }, lost);
  watchMonth();
  renderMain();
}

function watchMonth() {
  subs.jellies?.();
  const days = gridDays(state.month);
  subs.jellies = store.watchJellies(state.spaceId, isoDay(days[0]), isoDay(days[41]), (jellies) => {
    state.jellies = jellies;
    renderMain();
  }, (e) => console.warn(e));
}

/** Shows the month of an ISO day with that day selected. */
function showDay(iso) {
  const d = parseDay(iso);
  state.selected = iso;
  if (d.getMonth() !== state.month.getMonth() || d.getFullYear() !== state.month.getFullYear()) {
    state.month = firstOfMonth(d);
    state.jellies = [];
    watchMonth();
  }
  renderMain();
}

function shiftMonth(delta) {
  state.month = new Date(state.month.getFullYear(), state.month.getMonth() + delta, 1);
  const sel = parseDay(state.selected);
  if (sel.getMonth() !== state.month.getMonth() || sel.getFullYear() !== state.month.getFullYear()) {
    state.selected = isoDay(state.month.getMonth() === new Date().getMonth() && state.month.getFullYear() === new Date().getFullYear() ? new Date() : state.month);
  }
  state.jellies = [];
  watchMonth();
  renderMain();
}

function jelliesOn(iso) {
  return state.jellies
    .filter((j) => j.date === iso)
    .sort((a, b) => (a.start ?? 2000) - (b.start ?? 2000) || (a.createdAt?.toMillis?.() ?? Infinity) - (b.createdAt?.toMillis?.() ?? Infinity));
}

// ---------------------------------------------------------------- main view

function renderMain() {
  if (!state.spaceId) return;
  const month = state.month;
  const title = `${month.getFullYear()}년 ${month.getMonth() + 1}월`;

  const header = h('div', { class: 'header' },
    h('button', { class: 'icon-btn', 'aria-label': '이전 달', onClick: () => shiftMonth(-1) }, '‹'),
    h('div', null,
      h('div', { class: 'month-title', 'data-testid': 'month' }, title),
      h('div', { class: 'space-name' }, state.space?.name || '공유 젤리 달력')),
    h('button', { class: 'icon-btn', 'aria-label': '다음 달', onClick: () => shiftMonth(1) }, '›'),
    h('button', {
      class: 'chip',
      onClick: () => {
        state.month = firstOfMonth(new Date());
        state.selected = today;
        watchMonth();
        renderMain();
      },
    }, '오늘'),
    h('button', { class: 'members', 'aria-label': '함께 쓰는 사람과 메뉴', onClick: () => openSheet({ kind: 'menu' }), 'data-testid': 'menu' },
      state.members.map((m) => avatar(m.uid, m.name)),
      h('span', { class: 'icon-btn', style: { width: '32px', fontSize: '20px' } }, '⋯')),
  );

  const weekdays = h('div', { class: 'weekdays' },
    DAY_NAMES.map((n, i) => h('div', { class: i === 5 ? 'sat' : i === 6 ? 'sun' : '' }, n)));

  const grid = h('div', { class: 'grid', 'data-testid': 'grid' },
    gridDays(month).map((d) => {
      const iso = isoDay(d);
      const list = jelliesOn(iso);
      const weekday = (d.getDay() + 6) % 7;
      const shown = list.slice(0, 2);
      return h('button', {
        class: ['day', d.getMonth() !== month.getMonth() && 'other', iso === today && 'today', iso === state.selected && 'selected']
          .filter(Boolean).join(' '),
        'data-day': iso,
        onClick: () => {
          state.selected = iso;
          if (d.getMonth() !== month.getMonth()) shiftMonth(d < month ? -1 : 1);
          else renderMain();
        },
      },
        h('span', { class: `num${weekday === 5 ? ' sat' : weekday === 6 ? ' sun' : ''}` }, d.getDate()),
        shown.map((j) => h('span', {
          class: `jelly mini${j.done ? ' done' : ''}`,
          vars: { ...flavorVars(j.flavor), who: memberColor(j.by) },
        }, j.title)),
        list.length > shown.length ? h('span', { class: 'more' }, `+${list.length - shown.length}`) : null,
      );
    }));
  addSwipe(grid);

  const list = jelliesOn(state.selected);
  const panel = h('div', { class: 'day-panel' },
    h('div', { class: 'day-head' },
      h('div', null,
        h('div', { class: 'day-title', 'data-testid': 'day-title' }, dayTitle(state.selected)),
        h('div', { class: 'day-count' }, list.length ? `공유 젤리 ${list.length}개` : '아직 비어 있어요')),
      h('button', {
        class: 'jelly add-btn squish',
        vars: flavorVars(0),
        onClick: () => openSheet({ kind: 'new' }),
        'data-testid': 'add',
      }, '+ 올리기')),
    list.length
      ? h('div', { class: 'cards' }, list.map((j, i) => card(j, i)))
      : h('div', { class: 'empty' }, '이 날에 같이 할 일을 ‘+ 올리기’로 올려 보세요. 올린 젤리는 상대 화면에도 바로 나타나요.'),
  );

  appEl().replaceChildren(...[
    header,
    weekdays,
    grid,
    state.online ? null : h('div', { class: 'banner offline' }, '오프라인이에요. 고친 내용은 연결되면 자동으로 올라가요.'),
    panel,
    installHint(),
  ].filter(Boolean));
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
    h('div', { class: 'title' }, j.done ? `✓ ${j.title}` : j.title),
    h('div', { class: 'meta' },
      h('span', null, timeText(j)),
      h('span', { class: 'badge' }, avatar(j.by, j.byName, true), `${subject(j.by, j.byName)} 올림`),
      edited ? h('span', { class: 'badge' }, edited) : null,
      j.memoCount ? h('span', { class: 'badge', 'data-testid': 'memo-count' }, `메모 ${j.memoCount}`) : null,
      j.pending ? h('span', { class: 'pending-dot', title: '올리는 중' }) : null,
    ),
  );
}

function addSwipe(el) {
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
    if (Math.abs(dx) > 60 && Math.abs(dx) > Math.abs(dy) * 1.5) shiftMonth(dx < 0 ? 1 : -1);
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
  else if (s.kind === 'invite') buildInviteSheet();
  else if (s.kind === 'menu') buildMenuSheet();
  else if (s.kind === 'name') buildNameSheet();
}

function buildJellySheet() {
  const s = state.sheet;
  const isNew = s.kind === 'new';
  const existing = isNew ? null : s.seed || state.jellies.find((x) => x.id === s.id);
  if (!isNew && !existing) return hideSheet();
  // The draft that is shown and edited; for an existing jelly, changes are saved as they happen.
  const draft = existing
    ? { ...existing }
    : { title: '', date: state.selected, start: null, duration: 60, flavor: state.jellies.length % FLAVORS.length, done: false, note: '' };

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
  const durationRow = h('div', { class: 'row' });
  const colorRow = h('div', { class: 'row' });
  const doneSwitch = h('span', { class: 'switch' });
  const doneRow = h('button', { class: 'toggle', type: 'button', 'data-testid': 'done' }, h('span', null, '다 했어요'), doneSwitch);
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
    durationRow.replaceChildren(...DURATIONS.map((m) => h('button', {
      class: `chip${draft.duration === m ? ' on' : ''}`,
      type: 'button',
      onClick: () => save({ duration: m }, true),
    }, durationText(m))));
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
    save({ date: date.value }, true);
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
  note.addEventListener('input', () => save({ note: note.value }));

  const children = [
    preview,
    title,
    h('div', { class: 'field-label' }, '날짜와 시간'),
    h('div', { class: 'row' }, date, time, noTime),
    h('div', { class: 'field-label' }, '길이'),
    durationRow,
    h('div', { class: 'field-label' }, '색'),
    colorRow,
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
          toast(state.online ? '올렸어요. 상대 화면에도 바로 보여요' : '연결되면 바로 올라가요');
        },
      }, '올리기'),
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
      for (const key of ['date', 'start', 'duration', 'flavor', 'done']) if (!(key in pendingPatch)) draft[key] = j[key];
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
        toast('상대가 이 젤리를 지웠어요');
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
  sheetFrame(h('div', { class: 'day-title' }, '상대 초대하기'), body);
  try {
    const { code, expiresAt } = await store.makeInvite(state.spaceId);
    const pretty = store.prettyCode(code);
    const link = `${location.origin}${location.pathname}#c=${code}`;
    const text = [
      '공유 젤리 달력에 초대해요.',
      `링크: ${link}`,
      `초대 코드: ${pretty}`,
      '아이폰은 사파리에서 링크를 연 뒤 공유 버튼 → ‘홈 화면에 추가’를 누르면 앱처럼 쓸 수 있어요. 홈 화면 아이콘으로 처음 열 때 이 코드를 넣어 주세요.',
    ].join('\n');
    body.replaceChildren(
      h('p', { style: { color: 'var(--text-sub)', fontSize: '14px' } },
        '아래 코드나 링크를 상대에게 보내 주세요. 이 코드로 들어온 사람은 이 달력의 젤리를 보고, 고치고, 메모를 달 수 있어요.'),
      h('div', { class: 'big-code', 'data-testid': 'invite-code' }, pretty),
      h('div', { class: 'more', style: { textAlign: 'center' } },
        `${expiresAt.getMonth() + 1}월 ${expiresAt.getDate()}일까지 쓸 수 있어요`),
      h('div', { class: 'row', style: { marginTop: '16px', flexWrap: 'nowrap' } },
        h('button', { class: 'btn', style: { flex: '1' }, onClick: () => shareText(text) }, '보내기'),
        h('button', {
          class: 'btn ghost',
          style: { flex: '1' },
          onClick: () => copyText(text),
        }, '복사하기')),
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
  sheetFrame(
    h('div', { class: 'day-title', style: { marginBottom: '8px' } }, '함께 쓰는 사람'),
    h('div', { class: 'menu-list', style: { marginBottom: '14px' } },
      state.members.map((m) => h('div', { class: 'row' },
        avatar(m.uid, m.name),
        h('span', null, m.uid === state.uid ? `${m.name} (나)` : m.name)))),
    h('div', { class: 'menu-list' },
      h('button', { class: 'btn block', onClick: () => openReplace({ kind: 'invite' }), 'data-testid': 'invite' }, '상대 초대하기'),
      h('button', { class: 'btn ghost block', onClick: () => openReplace({ kind: 'name' }) }, '내 이름 바꾸기'),
      h('button', {
        class: 'btn danger block',
        onClick: async () => {
          if (!confirm('이 공유 달력에서 나갈까요? 다시 들어오려면 초대 코드가 필요해요.')) return;
          try {
            await store.leave(state.spaceId);
          } catch (e) {
            console.error(e);
          }
          closeSheet();
          saved.set('space', null);
          state.spaceId = null;
          renderWelcome();
        },
      }, '이 달력에서 나가기')),
  );
}

function openReplace(sheet) {
  state.sheet = sheet;
  buildSheet();
}

function buildNameSheet() {
  const name = h('input', { class: 'input', style: { width: '100%' }, maxlength: '20', value: state.myName });
  sheetFrame(
    h('div', { class: 'day-title' }, '내 이름 바꾸기'),
    h('div', { class: 'field-label' }, '상대에게 보이는 이름'),
    name,
    h('button', {
      class: 'btn block',
      style: { marginTop: '14px' },
      onClick: async () => {
        const n = name.value.trim();
        if (!n) return;
        rememberName(n);
        try {
          await store.rename(state.spaceId, n);
          closeSheet();
          toast('이름을 바꿨어요');
        } catch (e) {
          console.error(e);
          toast('바꾸지 못했어요');
        }
      },
    }, '저장'),
  );
  setTimeout(() => name.focus(), 50);
}

// ---------------------------------------------------------------- service worker

if ('serviceWorker' in navigator && location.protocol === 'https:') {
  navigator.serviceWorker.register('sw.js').catch(() => {});
}

boot();
