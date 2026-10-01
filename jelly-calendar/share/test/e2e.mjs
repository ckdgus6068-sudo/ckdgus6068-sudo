// Two people on one shared calendar, against the Auth and Firestore emulators:
//   npm run test:e2e
// One plays the Galaxy app (the page inside the app's web view), the other an iPhone in Safari.
// Screenshots go to test-results/. CHROME_PATH picks the browser binary.
import { createServer } from 'node:http';
import { mkdirSync, readFileSync, existsSync, statSync } from 'node:fs';
import { extname, join, normalize } from 'node:path';
import { chromium } from 'playwright-core';

const DOCS = new URL('../../../docs/', import.meta.url).pathname;
const OUT = new URL('../test-results/', import.meta.url).pathname;
mkdirSync(OUT, { recursive: true });

const TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.png': 'image/png',
  '.webmanifest': 'application/manifest+json',
  '.txt': 'text/plain; charset=utf-8',
};

const server = createServer((req, res) => {
  let path = normalize(decodeURIComponent(new URL(req.url, 'http://x').pathname)).replace(/^(\.\.[/\\])+/, '');
  let file = join(DOCS, path);
  if (existsSync(file) && statSync(file).isDirectory()) file = join(file, 'index.html');
  if (!existsSync(file)) {
    res.writeHead(404).end('not found');
    return;
  }
  res.writeHead(200, { 'content-type': TYPES[extname(file)] || 'application/octet-stream' });
  res.end(readFileSync(file));
});
await new Promise((r) => server.listen(5173, '127.0.0.1', r));
const BASE = 'http://127.0.0.1:5173/share/?emu=1';

const browser = await chromium.launch({
  executablePath: process.env.CHROME_PATH || '/opt/pw-browsers/chromium-1194/chrome-linux/chrome',
});

const galaxy = await browser.newContext({
  viewport: { width: 412, height: 915 },
  deviceScaleFactor: 2.625,
  isMobile: true,
  hasTouch: true,
  locale: 'ko-KR',
  reducedMotion: 'reduce',
  timezoneId: 'Asia/Seoul',
  userAgent:
    'Mozilla/5.0 (Linux; Android 14; SM-S921N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Mobile Safari/537.36 JellyCalendarApp/0.2',
});
const iphone = await browser.newContext({
  viewport: { width: 390, height: 844 },
  deviceScaleFactor: 3,
  isMobile: true,
  hasTouch: true,
  locale: 'ko-KR',
  reducedMotion: 'reduce',
  timezoneId: 'Asia/Seoul',
  userAgent:
    'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1',
});

const a = await galaxy.newPage();
const b = await iphone.newPage();
for (const [name, page] of [['galaxy', a], ['iphone', b]]) {
  page.on('pageerror', (e) => console.error(`[${name}] page error`, e));
  page.on('console', (m) => m.type() === 'error' && console.error(`[${name}]`, m.text()));
  page.on('dialog', (d) => d.accept());
  page.on('requestfailed', (r) => console.error(`[${name}] request failed ${r.url()} ${r.failure()?.errorText}`));
}

let failures = 0;
function check(ok, what) {
  console.log(`${ok ? 'ok  ' : 'FAIL'} ${what}`);
  if (!ok) failures++;
}
const shot = (page, name) => page.screenshot({ path: `${OUT}${name}.png`, fullPage: true });
const tid = (page, id) => page.locator(`[data-testid="${id}"]`);

try {
  // 1. Galaxy (창현) makes the shared calendar and an invite.
  await a.goto(BASE);
  await tid(a, 'name').fill('창현');
  await tid(a, 'create').click();
  await tid(a, 'month').waitFor();
  check((await tid(a, 'month').textContent()).includes('월'), 'galaxy: shared calendar created');
  await shot(a, '1-galaxy-empty');
  await tid(a, 'menu').click();
  await tid(a, 'invite').click();
  await tid(a, 'invite-code').waitFor();
  const code = (await tid(a, 'invite-code').textContent()).trim();
  check(/^[0-9A-Z]{5}-[0-9A-Z]{5}$/.test(code), `galaxy: invite code ${code}`);
  await shot(a, '2-galaxy-invite');
  await a.goBack();

  // 2. iPhone (지은) opens the invite link and joins.
  await b.goto(`${BASE}#c=${code.replace('-', '')}`);
  await tid(b, 'code').waitFor();
  check((await tid(b, 'code').inputValue()) === code, 'iphone: code filled in from the link');
  await shot(b, '3-iphone-welcome');
  await tid(b, 'name').fill('지은');
  await tid(b, 'join').click();
  await tid(b, 'month').waitFor();
  check(true, 'iphone: joined');

  // 3. Galaxy puts up a jelly on the 10th; the iPhone sees it without reloading.
  const month = await a.evaluate(() => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
  });
  const day = `${month}-10`;
  await a.locator(`[data-day="${day}"]`).click();
  await tid(a, 'add').click();
  await tid(a, 'title').fill('저녁 약속');
  await tid(a, 'time').fill('19:00');
  await tid(a, 'post').click();
  await tid(a, 'memos').waitFor();
  await a.goBack();
  await a.locator(`[data-day="${day}"]`).click();

  await b.locator(`[data-day="${day}"]`).click();
  await b.locator('[data-testid="card"]', { hasText: '저녁 약속' }).waitFor({ timeout: 10000 });
  check(true, 'iphone: sees the jelly the galaxy put up');
  check((await b.locator('[data-testid="card"]').first().textContent()).includes('창현 올림'), 'iphone: shows who put it up');

  // 4. iPhone changes the time and the title, and leaves a memo.
  await b.locator('[data-testid="card"]').first().click();
  await tid(b, 'title').fill('저녁 약속 (7시 반)');
  await tid(b, 'time').fill('19:30');
  await tid(b, 'time').dispatchEvent('change');
  await tid(b, 'memo-input').fill('역 앞 2번 출구에서 봐요');
  await tid(b, 'memo-send').click();
  await b.locator('.memo', { hasText: '2번 출구' }).waitFor();
  await b.waitForTimeout(1200);
  await shot(b, '4-iphone-sheet-memo');
  await b.goBack();

  // 5. Galaxy sees the edit and the memo right away.
  await a.locator('[data-testid="card"]', { hasText: '7시 반' }).waitFor({ timeout: 10000 });
  const cardText = await a.locator('[data-testid="card"]').first().textContent();
  check(cardText.includes('19:30'), 'galaxy: sees the new time');
  check(cardText.includes('지은 고침'), 'galaxy: sees who changed it');
  check(cardText.includes('메모 1'), 'galaxy: sees the memo count');
  await a.locator('[data-testid="card"]').first().click();
  await a.locator('.memo', { hasText: '2번 출구' }).waitFor({ timeout: 10000 });
  check(true, 'galaxy: reads the memo');
  await tid(a, 'memo-input').fill('좋아요! 7시 반에 봐요');
  await tid(a, 'memo-send').click();
  await a.locator('.memo', { hasText: '7시 반에 봐요' }).waitFor();
  await tid(a, 'done').click();
  await a.waitForTimeout(800);
  await shot(a, '5-galaxy-sheet');
  await a.goBack();

  // 6. A few more jellies so the month looks lived in.
  const more = [
    ['03', '영화 보기', '14:00', b],
    ['17', '부모님 생신', '', a],
    ['17', '케이크 찾기', '11:00', b],
    ['24', '주말 등산', '08:00', a],
  ];
  for (const [d, title, time, page] of more) {
    await page.locator(`[data-day="${month}-${d}"]`).click();
    await tid(page, 'add').click();
    await tid(page, 'title').fill(title);
    if (time) await tid(page, 'time').fill(time);
    if (title === '주말 등산') {
      // Pulled all the way out: a whole day's hike.
      const track = await tid(page, 'length').boundingBox();
      await page.mouse.click(track.x + track.width - 4, track.y + track.height / 2);
      check((await tid(page, 'length').getAttribute('aria-valuenow')) === '720', 'galaxy: the length stretches to 12 hours');
    }
    await tid(page, 'post').click();
    await tid(page, 'memos').waitFor();
    await page.goBack();
  }
  await b.locator(`[data-day="${day}"]`).click();
  // Finished jellies leave the grid and fold away under the day.
  await tid(b, 'done-fold').waitFor({ timeout: 10000 });
  check(!(await b.locator(`[data-day="${day}"] .mini`).count()), 'iphone: a finished jelly leaves the month grid');
  await tid(b, 'done-fold').click();
  await b.locator('[data-testid="card"].done').waitFor({ timeout: 10000 });
  check(true, 'iphone: sees it marked done');
  await b.waitForTimeout(800);
  await shot(b, '6-iphone-month');
  await a.locator(`[data-day="${month}-17"]`).click();
  await a.waitForTimeout(800);
  await shot(a, '7-galaxy-month');
  const cells = await b.locator(`[data-day="${month}-17"] .mini`).count();
  check(cells === 2, 'iphone: two jellies on the 17th in the month grid');
  const firstWeekday = await b.locator('.weekdays div').first().textContent();
  check(firstWeekday === '일', 'iphone: weeks start on Sunday');
  const { holidayOn } = await import(`${DOCS}share/holidays.js`);
  const holidayIso = Array.from({ length: 31 }, (_, i) => `${month}-${String(i + 1).padStart(2, '0')}`).find((iso) => holidayOn(iso));
  if (holidayIso) {
    const red = await b.locator(`[data-day="${holidayIso}"] .num.sun`).count();
    check(red === 1, `iphone: ${holidayOn(holidayIso).name} (${holidayIso}) is red in the grid`);
  }

  // The server holds no readable names, titles or memos: look at the raw documents in the emulator.
  const rawDocs = (collectionId) => fetch('http://127.0.0.1:8080/v1/projects/demo-jelly/databases/(default)/documents:runQuery', {
    method: 'POST',
    headers: { 'content-type': 'application/json', authorization: 'Bearer owner' },
    body: JSON.stringify({ structuredQuery: { from: [{ collectionId, allDescendants: true }] } }),
  }).then((r) => r.text());
  const raw = (await rawDocs('jellies')) + (await rawDocs('memos'));
  const members = (await rawDocs('members')) + (await rawDocs('spaces'));
  // Write times are kept in the clear (they look like 2026-10-01T05:52:11Z); the jellies' own dates are not.
  const leaks = ['저녁 약속', '부모님 생신', '2번 출구', '7시 반에 봐요', '창현', '지은', `${month}-10`, `${month}-17`, '19:30', '1170']
    .filter((t) => raw.includes(t) || members.includes(t));
  check(raw.includes('"title"') && leaks.length === 0, `server copy is sealed (plain text found: ${leaks.join(', ') || 'none'})`);

  // 7. The shared box: the 17th as a box of soft jellies.
  const box = (page) => page.evaluate(() => window.__jellyBox?.centers() ?? []);
  const settled = (page, count) => page.waitForFunction(
    (n) => window.__jellyBox?.resting && window.__jellyBox.centers().length === n,
    count,
    { timeout: 15000 },
  );
  await tid(a, 'view-box').click();
  await tid(a, 'day-head').waitFor();
  await settled(a, 2);
  check((await tid(a, 'day-head').textContent()).includes('17일'), 'galaxy box: opens on the selected day');
  await a.waitForTimeout(600);
  await shot(a, '8-galaxy-box');

  // A double tap finishes a jelly, and the other phone sees it at once.
  let blobs = await box(a);
  const cake = blobs.find((c) => c.title === '케이크 찾기');
  await a.mouse.dblclick(cake.x, cake.y);
  await b.waitForFunction((sel) => document.querySelectorAll(sel).length === 1, `[data-day="${month}-17"] .mini`, { timeout: 10000 });
  check(true, 'iphone: sees the jelly finished in the galaxy box (it leaves the grid)');

  // A tap opens the jelly with its memos.
  await settled(a, 2);
  blobs = await box(a);
  const birthday = blobs.find((c) => c.title === '부모님 생신');
  await a.mouse.click(birthday.x, birthday.y);
  await tid(a, 'memos').waitFor();
  check((await tid(a, 'title').inputValue()) === '부모님 생신', 'galaxy box: a tap opens the jelly');
  // Pinned: it sits at the top of the box, here and on the other phone.
  await tid(a, 'pin').click();
  await a.goBack();
  await a.waitForFunction(() => window.__jellyBox?.centers().some((c) => c.title === '부모님 생신' && c.fixed), null, { timeout: 10000 });
  check(true, 'galaxy box: a pinned jelly is held at the top');
  await b.locator(`[data-day="${month}-17"] .mini.pinned`).waitFor({ timeout: 10000 });
  check(true, 'iphone: sees the jelly pinned in the month grid');
  await shot(a, '8b-galaxy-box-pinned');

  // Swiping an empty spot turns the day.
  const area = await tid(a, 'box').boundingBox();
  await a.mouse.move(area.x + area.width - 110, area.y + 110);
  await a.mouse.down();
  await a.mouse.move(area.x + 30, area.y + 120, { steps: 8 });
  await a.mouse.up();
  await a.waitForFunction(() => document.querySelector('[data-testid="day-head"]')?.textContent.includes('18일'));
  check(true, 'galaxy box: a sideways swipe turns to the next day');

  // The iPhone's box of the 10th, and its menu sheet at full height.
  await tid(b, 'view-box').click();
  await settled(b, 1);
  await b.waitForTimeout(600);
  await shot(b, '9-iphone-box');
  await tid(b, 'menu').click();
  await tid(b, 'invite').waitFor();
  const sheet = await b.locator('.sheet').boundingBox();
  check(sheet.height > 250, `iphone: the menu sheet opens at full height (${Math.round(sheet.height)}px)`);
  await shot(b, '10-iphone-menu');

  // The lettering can be picked in a browser; it is remembered on the phone.
  const display = (page) => page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--display'));
  check((await display(b)).includes('NanumSquareRound'), 'iphone: rounded lettering by default');
  await tid(b, 'fonts').getByText('말랑', { exact: true }).click();
  check((await display(b)).includes('Jua') && (await b.evaluate(() => localStorage.getItem('jellyShare.font'))) === 'JUA',
    'iphone: picks another lettering and keeps it');
  await tid(b, 'fonts').getByText('동글', { exact: true }).click();
  await b.goBack();

  // The hidden golden jelly: grab the same jelly 50 times in a row.
  const grab = async (page, title, times, at) => {
    for (let i = 0; i < times; i++) {
      const c = (await box(page)).find((x) => x.title === title);
      await page.mouse.move(c.x, c.y);
      await page.mouse.down();
      await page.mouse.move(c.x + (i % 2 ? -24 : 24), c.y, { steps: 3 });
      await page.mouse.up();
      if (at && i + 1 === at.after) await at.then();
    }
  };
  const lone = (await box(b))[0].title;
  await grab(b, lone, 50, {
    after: 30,
    then: async () => check(await b.evaluate(() => window.__jellyBox.glitter.level > 0), 'iphone: the jelly glitters after 30 grabs'),
  });
  await tid(b, 'golden').waitFor({ timeout: 5000 });
  const golden = await tid(b, 'golden-code').textContent();
  check(/^GOLD-[2-9A-HJKMNP-TV-Z]{4}-[2-9A-HJKMNP-TV-Z]{4}$/.test(golden), `iphone: golden jelly found (${golden})`);
  check(await b.evaluate(() => [...window.__jellyBox.byKey.values()].some((it) => it.gold)), 'iphone: the jelly turned to gold');
  await shot(b, '10b-iphone-golden');
  await b.goBack();

  // 8. Inside the app's "모두" tab: the phone's own jellies and the shared ones in one box.
  const app = await galaxy.newPage();
  app.on('pageerror', (e) => console.error('[app] page error', e));
  app.on('console', (m) => m.type() === 'error' && console.error('[app]', m.text()));
  await app.addInitScript((date) => {
    window.__calls = [];
    window.__host = {
      mode: 'all',
      date,
      doubleTap: true,
      longPress: true,
      sundayFirst: true,
      personal: [
        { id: 'p1', title: '헬스', date, start: 19 * 60, duration: 60, flavor: 3, done: false },
        { id: 'p2', title: '보고서 작성', date, start: 14 * 60, duration: 90, flavor: 6, done: true },
      ],
    };
    const call = (name) => (...args) => {
      window.__calls.push([name, ...args]);
    };
    window.JellyBridge = {
      hostState: () => JSON.stringify(window.__host),
      share: call('share'),
      openPersonal: call('openPersonal'),
      togglePersonal: call('togglePersonal'),
      createPersonal: call('createPersonal'),
      shiftDay: call('shiftDay'),
      showShared: call('showShared'),
      showDay: call('showDay'),
      foundGolden: call('foundGolden'),
      setAlarm: call('setAlarm'),
    };
  }, `${month}-17`);
  const called = (page, name, arg) => page.waitForFunction(
    ([n, x]) => window.__calls.some((c) => c[0] === n && c[1] === x),
    [name, arg],
    { timeout: 5000 },
  );
  await app.goto(BASE);
  await tid(app, 'legend').waitFor();
  await settled(app, 4);
  const legend = await tid(app, 'legend').textContent();
  check(legend.includes('내 젤리 2') && legend.includes('공유 젤리 2'), 'all: my two jellies and two shared ones in one box');
  await app.waitForTimeout(600);
  await shot(app, '11-galaxy-all');

  blobs = await box(app);
  const gym = blobs.find((c) => c.title === '헬스');
  await app.mouse.click(gym.x, gym.y);
  await called(app, 'openPersonal', 'p1');
  check(true, 'all: a tap on my own jelly opens it in the app');

  await settled(app, 4);
  blobs = await box(app);
  const report = blobs.find((c) => c.title === '보고서 작성');
  await app.mouse.dblclick(report.x, report.y);
  await called(app, 'togglePersonal', 'p2');
  check(true, 'all: a double tap on my own jelly finishes it in the app');

  // Inside the app the app keeps the golden code: the page hands the find over.
  await settled(app, 4);
  await grab(app, '헬스', 50);
  await called(app, 'foundGolden', 'p1');
  check(!(await tid(app, 'golden').count()), 'all: a golden find inside the app is the app\'s to announce');

  // The page letters itself the way the app's settings say.
  await app.evaluate(() => {
    window.__host = { ...window.__host, font: 'ROUND' };
    window.jellyHost.poke();
  });
  check((await display(app)).includes('Bagel'), 'all: follows the lettering picked in the app');

  // "모두" as a month: my jellies and the shared ones, and a tapped day goes to the app.
  await tid(app, 'view-month').click();
  await tid(app, 'grid').waitFor();
  const mine17 = await app.locator(`[data-day="${month}-17"] .mini.mine`).count();
  const shared17 = await app.locator(`[data-day="${month}-17"] .mini:not(.mine)`).count();
  check(mine17 === 1 && shared17 >= 1, `all: the month shows my unfinished jelly and the shared ones (${mine17} + ${shared17})`);
  await shot(app, '11b-galaxy-all-month');
  await app.locator(`[data-day="${month}-24"]`).click();
  await called(app, 'showDay', `${month}-24`);
  check(true, 'all: a tapped day goes to the app');
  await tid(app, 'view-box').click();
  await settled(app, 4);

  await settled(app, 4);
  blobs = await box(app);
  const shared = blobs.find((c) => c.title === '부모님 생신');
  await app.mouse.click(shared.x, shared.y);
  await tid(app, 'memos').waitFor();
  check(true, 'all: a tap on a shared jelly opens it with its memos');
  check(await tid(app, 'alarm').isDisabled(), 'all: the clock alarm waits for a jelly with a time in the coming day');
  await app.goBack();

  const room = await tid(app, 'box').boundingBox();
  await app.mouse.move(room.x + 40, room.y + 110);
  await app.mouse.down();
  await app.mouse.move(room.x + room.width - 110, room.y + 120, { steps: 8 });
  await app.mouse.up();
  await called(app, 'shiftDay', -1);
  check(true, 'all: a sideways swipe asks the app for the day before');

  await app.evaluate((day) => {
    window.__host = { ...window.__host, date: day, personal: [] };
    window.jellyHost.poke();
  }, day);
  await settled(app, 1);
  check((await tid(app, 'legend').textContent()).includes('공유 젤리 1'), 'all: follows the app to another day');

  // "+" asks which kind of jelly; my own goes to the app's editor for the day now shown.
  await tid(app, 'add').click();
  await tid(app, 'add-shared').waitFor();
  await shot(app, '12-galaxy-all-add');
  await tid(app, 'add-personal').click();
  await called(app, 'createPersonal', day);
  check(true, 'all: + makes my own jelly in the app on the day shown');
} catch (e) {
  console.error(e);
  failures++;
  await shot(a, 'error-galaxy').catch(() => {});
  await shot(b, 'error-iphone').catch(() => {});
} finally {
  await browser.close();
  server.close();
}

console.log(failures ? `${failures} check(s) failed` : 'all checks passed');
process.exit(failures ? 1 : 0);
