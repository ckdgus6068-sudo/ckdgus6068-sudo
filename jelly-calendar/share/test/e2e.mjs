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
  page.on('dialog', (d) => noBrowserDialog(d));
  page.on('requestfailed', (r) => console.error(`[${name}] request failed ${r.url()} ${r.failure()?.errorText}`));
}

let failures = 0;
function check(ok, what) {
  console.log(`${ok ? 'ok  ' : 'FAIL'} ${what}`);
  if (!ok) failures++;
}

// Times written in titles: the same sentences as the app's TitleTimeTest.
{
  const { titleTime } = await import(`${DOCS}share/titletime.js`);
  const cases = JSON.parse(readFileSync(new URL('./titletime-cases.json', import.meta.url), 'utf8'));
  const wrong = cases.filter(([title, minute, text]) => {
    const said = titleTime(title);
    return minute == null ? said != null : !said || said.minute !== minute || said.text !== text;
  });
  check(wrong.length === 0, `titles: ${cases.length - wrong.length}/${cases.length} times read as written${wrong.length ? ` (wrong: ${wrong.map((c) => c[0]).join(', ')})` : ''}`);
}

// Inside the app a browser dialog quietly answers "no", so the page must ask on the page itself.
function noBrowserDialog(dialog) {
  check(false, `asks on the page, not with a browser dialog: ${dialog.message()}`);
  dialog.dismiss().catch(() => {});
}
const shot = (page, name) => page.screenshot({ path: `${OUT}${name}.png`, fullPage: true });
const tid = (page, id) => page.locator(`[data-testid="${id}"]`);
// A tapped day opens its sheet at once; the cards and the fold are looked for inside it.
const daySheet = (page) => tid(page, 'day-sheet');
const sheetCards = (page, hasText) => daySheet(page).locator('[data-testid="card"]', hasText ? { hasText } : undefined);
const openDay = async (page, iso) => {
  await page.locator(`[data-day="${iso}"]`).click();
  await daySheet(page).waitFor();
};
// A finger pulling the open sheet down by its handle, with real touch events.
const pullDown = async (page, distance, steps = 10) => {
  const handle = await page.locator('.sheet .handle').boundingBox();
  const x = handle.x + handle.width / 2;
  const y = handle.y + handle.height / 2;
  const cdp = await page.context().newCDPSession(page);
  await cdp.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x, y }] });
  for (let i = 1; i <= steps; i++) {
    await cdp.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ x, y: y + (distance * i) / steps }] });
  }
  await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
  await cdp.detach();
};
// Back to the month: a jelly opened on a day's sheet goes back to the day first, then the month.
const backToMonth = async (page) => {
  for (let i = 0; i < 4 && (await page.evaluate(() => !!history.state?.sheet)); i++) await page.goBack();
  await page.locator('.sheet').waitFor({ state: 'detached' });
};

try {
  // 1. Galaxy (창현) makes an account, the shared calendar and an invite.
  await a.goto(BASE);
  await tid(a, 'account-step').waitFor();
  await tid(a, 'account-id').fill('changhyun');
  await tid(a, 'account-password').fill('jelly-pass-1');
  await tid(a, 'sign-up').click();
  await tid(a, 'name').waitFor();
  check((await tid(a, 'signed-in').textContent()).includes('changhyun'), 'galaxy: makes an account with a login ID');
  await tid(a, 'name').fill('창현');
  check((await tid(a, 'look-open').textContent()).includes('얼굴'), 'galaxy: starts with the plain smiling face');
  await tid(a, 'look-open').click();
  await tid(a, 'look-grid').waitFor();
  check((await tid(a, 'look-grid').locator('.look-cell').count()) === 43, 'galaxy: 43 looks to pick from (plain, face x2, 20 jobs x2)');
  await shot(a, '1a-galaxy-look-picker');
  await tid(a, 'look-police-0').click();
  await tid(a, 'look-grid').waitFor({ state: 'detached' });
  check((await tid(a, 'look-open').textContent()).includes('경찰관'), 'galaxy: picks the police character');
  await tid(a, 'create').click();
  await tid(a, 'month').waitFor();
  check((await tid(a, 'month').textContent()).includes('월'), 'galaxy: shared calendar created');
  await a.locator('.look-avatar[data-look="police-0"]').first().waitFor({ timeout: 10000 });
  check(true, 'galaxy: shows itself as the police character');
  await shot(a, '1-galaxy-empty');
  await tid(a, 'menu').click();
  await tid(a, 'invite').click();
  await tid(a, 'invite-code').waitFor();
  const code = (await tid(a, 'invite-code').textContent()).trim();
  check(/^[0-9A-Z]{5}-[0-9A-Z]{5}$/.test(code), `galaxy: invite code ${code}`);
  await shot(a, '2-galaxy-invite');
  await a.goBack();

  // 2. iPhone (지은) opens the invite link, makes an account and joins.
  await b.goto(`${BASE}#c=${code.replace('-', '')}`);
  await tid(b, 'account-step').waitFor();
  await tid(b, 'account-id').fill('jieun.22');
  await tid(b, 'account-password').fill('jelly-pass-2');
  await tid(b, 'sign-up').click();
  await tid(b, 'code').waitFor();
  check((await tid(b, 'code').inputValue()) === code, 'iphone: code filled in from the link');
  await shot(b, '3-iphone-welcome');
  await tid(b, 'name').fill('지은');
  await tid(b, 'look-open').click();
  await tid(b, 'look-judge-1').click();
  await tid(b, 'look-grid').waitFor({ state: 'detached' });
  await tid(b, 'join').click();
  await tid(b, 'month').waitFor();
  check(true, 'iphone: joined');
  await b.locator('.look-avatar[data-look="police-0"]').first().waitFor({ timeout: 10000 });
  await a.locator('.look-avatar[data-look="judge-1"]').first().waitFor({ timeout: 10000 });
  check(true, 'each phone sees the other one\'s character');

  // 3. Galaxy puts up a jelly on the 10th; the iPhone sees it without reloading.
  const month = await a.evaluate(() => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
  });
  const day = `${month}-10`;
  await openDay(a, day);
  check((await daySheet(a).textContent()).includes('비어 있어요'), 'galaxy: a tapped day opens its jellies at once (none yet)');
  await tid(a, 'day-add').click();
  const startsAt = await tid(a, 'time').inputValue();
  check(/^\d\d:\d\d$/.test(startsAt), `galaxy: a new jelly starts with a time, as in the app (${startsAt})`);
  // A time written in the title becomes the time, and goes away with it.
  await tid(a, 'title').fill('11시 미용실');
  check(
    (await tid(a, 'time').inputValue()) === '11:00' && (await tid(a, 'title-time').textContent()).includes('11시'),
    'galaxy: "11시 미용실" sets the new jelly\'s time to 11:00',
  );
  await tid(a, 'title').fill('저녁 약속');
  check((await tid(a, 'time').inputValue()) === startsAt, 'galaxy: without a time in the title, the time goes back');
  await tid(a, 'title').fill('저녁 약속');
  await tid(a, 'time').fill('19:00');
  await tid(a, 'post').click();
  await tid(a, 'memos').waitFor();
  await a.goBack();
  await sheetCards(a, '저녁 약속').waitFor();
  check(true, 'galaxy: back from the new jelly, its day lists it');
  await shot(a, '3-galaxy-day-sheet');

  await openDay(b, day);
  await sheetCards(b, '저녁 약속').waitFor({ timeout: 10000 });
  check(true, 'iphone: sees the jelly the galaxy put up');
  check((await sheetCards(b).first().textContent()).includes('창현 올림'), 'iphone: shows who put it up');

  // 4. iPhone changes the time and the title, and leaves a memo.
  await sheetCards(b).first().click();
  await tid(b, 'title').fill('저녁 약속 (7시 반)');
  // An existing jelly is not moved on its own: the title's time is offered in one tap.
  await tid(b, 'title-time-chip').waitFor();
  check((await tid(b, 'title-time-chip').textContent()).includes('19:30'), 'iphone: a renamed jelly offers the time in its title (제목대로 19:30)');
  await tid(b, 'title-time-chip').click();
  check((await tid(b, 'time').inputValue()) === '19:30' && !(await tid(b, 'title-time-chip').count()), 'iphone: one tap sets it');
  await tid(b, 'memo-input').fill('역 앞 2번 출구에서 봐요');
  await tid(b, 'memo-send').click();
  await b.locator('.memo', { hasText: '2번 출구' }).waitFor();
  await b.waitForTimeout(1200);
  await shot(b, '4-iphone-sheet-memo');
  // Every sheet has ✕; the jelly's goes back to its day.
  await tid(b, 'sheet-close').click();
  await sheetCards(b, '7시 반').waitFor();
  check(true, 'iphone: ✕ closes the jelly and its day is there again');
  // Pulled down by the handle: a little springs back, far enough closes, and the page is not reloaded.
  await b.evaluate(() => {
    window.__stayed = true;
  });
  await pullDown(b, 24);
  await b.waitForTimeout(500);
  check(
    (await daySheet(b).count()) === 1 && (await daySheet(b).evaluate((el) => el.style.transform)) === '',
    'iphone: a short pull on the handle springs the sheet back',
  );
  await pullDown(b, 320);
  await daySheet(b).waitFor({ state: 'detached', timeout: 5000 });
  check(
    await b.evaluate(() => window.__stayed === true && !history.state?.sheet && !document.documentElement.classList.contains('sheet-open')),
    'iphone: pulled down by its handle, the sheet closes, and the page stays (no reload)',
  );

  // 5. Galaxy sees the edit and the memo right away, on the day's sheet still open.
  await sheetCards(a, '7시 반').waitFor({ timeout: 10000 });
  const cardText = await sheetCards(a).first().textContent();
  check(cardText.includes('19:30'), 'galaxy: sees the new time');
  check(cardText.includes('지은 고침'), 'galaxy: sees who changed it');
  check(cardText.includes('메모 1'), 'galaxy: sees the memo count');
  await sheetCards(a).first().click();
  await a.locator('.memo', { hasText: '2번 출구' }).waitFor({ timeout: 10000 });
  check(true, 'galaxy: reads the memo');
  await tid(a, 'memo-input').fill('좋아요! 7시 반에 봐요');
  await tid(a, 'memo-send').click();
  await a.locator('.memo', { hasText: '7시 반에 봐요' }).waitFor();
  await tid(a, 'done').click();
  await a.waitForTimeout(800);
  await shot(a, '5-galaxy-sheet');
  await backToMonth(a);

  // 6. A few more jellies so the month looks lived in.
  const more = [
    ['03', '영화 보기', '14:00', b],
    ['17', '부모님 생신', '', a],
    ['17', '케이크 찾기', '11:00', b],
    ['24', '주말 등산', '08:00', a],
  ];
  for (const [d, title, time, page] of more) {
    await openDay(page, `${month}-${d}`);
    await tid(page, 'day-add').click();
    await tid(page, 'title').fill(title);
    if (time) await tid(page, 'time').fill(time);
    else await tid(page, 'no-time').click();
    if (title === '주말 등산') {
      // Pulled all the way out: a whole day, on into the next morning.
      const track = await tid(page, 'length').boundingBox();
      await page.mouse.click(track.x + track.width - 4, track.y + track.height / 2);
      check((await tid(page, 'length').getAttribute('aria-valuenow')) === '1440', 'galaxy: the length stretches to 24 hours');
    }
    await tid(page, 'post').click();
    await tid(page, 'memos').waitFor();
    if (title === '주말 등산') {
      const meta = await page.locator('.sheet .card .meta').first().textContent();
      check(meta.includes('08:00–다음 날 08:00'), `galaxy: a jelly past midnight says when it ends (${meta.trim()})`);
    }
    if (!time) {
      check(await page.locator('.time-box.blank .time-empty').isVisible(), 'galaxy: a jelly without a time says "시간 정하기" in its empty time field');
      await shot(page, '6a-galaxy-no-time');
    }
    await backToMonth(page);
  }
  await openDay(b, day);
  // Finished jellies leave the grid and fold away under the day.
  await daySheet(b).locator('[data-testid="done-fold"]').waitFor({ timeout: 10000 });
  check(!(await b.locator(`[data-day="${day}"] .mini`).count()), 'iphone: a finished jelly leaves the month grid');
  await daySheet(b).locator('[data-testid="done-fold"]').click();
  await daySheet(b).locator('[data-testid="card"].done').waitFor({ timeout: 10000 });
  check(true, 'iphone: sees it marked done');
  await b.waitForTimeout(800);
  await shot(b, '6-iphone-day-done');
  await backToMonth(b);
  await shot(b, '6b-iphone-month');
  await openDay(a, `${month}-17`);
  check((await sheetCards(a).count()) === 2, 'galaxy: the 17th\'s sheet lists both of its jellies');
  await a.waitForTimeout(800);
  await shot(a, '7-galaxy-day-17');
  await backToMonth(a);
  await shot(a, '7b-galaxy-month');
  const cells = await b.locator(`[data-day="${month}-17"] .mini`).count();
  check(cells === 2, 'iphone: two jellies on the 17th in the month grid');
  const firstWeekday = await b.locator('.weekdays div').first().textContent();
  check(firstWeekday === '일', 'iphone: weeks start on Sunday');

  // Scrolled down to the day's list in another month, "오늘" brings back the top of this month.
  const fullSize = b.viewportSize();
  await b.setViewportSize({ width: fullSize.width, height: 520 });
  await b.getByRole('button', { name: '다음 달' }).first().click();
  // Turning the month glides back to its top; let that finish before scrolling down.
  await b.waitForFunction(() => window.scrollY === 0, null, { timeout: 5000 }).catch(() => {});
  await b.waitForTimeout(500);
  await b.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));
  await b.waitForFunction(() => window.scrollY > 0, null, { timeout: 3000 }).catch(() => {});
  const scrolledTo = await b.evaluate(() => window.scrollY);
  await tid(b, 'today').click();
  await b.waitForFunction(() => window.scrollY === 0, null, { timeout: 5000 }).catch(() => {});
  const thisMonth = (await tid(b, 'month').textContent()).trim();
  check(
    scrolledTo > 0 && (await b.evaluate(() => window.scrollY)) === 0 && thisMonth.includes(`${Number(month.slice(5))}월`),
    `iphone: "오늘" from another month, scrolled down (${scrolledTo}px), goes back to the top of ${thisMonth}`,
  );
  await b.setViewportSize(fullSize);
  // The steps below look at the 10th again.
  await openDay(b, `${month}-10`);
  await backToMonth(b);
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
  const worn = await a.evaluate(() => [...window.__jellyBox.byKey.values()].map((it) => `${it.title}:${it.look ? `${it.look.job}-${it.look.v}` : 'none'}`).sort());
  check(
    worn.includes('부모님 생신:police-0') && worn.includes('케이크 찾기:judge-1'),
    `galaxy box: each jelly wears the character of who put it up (${worn.join(', ')})`,
  );
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
  check(await a.evaluate(() => !document.body.innerText.split('\n').some((line) => line.trim() === 'null')), 'galaxy box: no stray "null" above the box');
  // Pulled down by a finger, a pinned jelly stretches and springs back to its pin.
  await settled(a, 2);
  const hung = (await box(a)).find((c) => c.title === '부모님 생신');
  await a.mouse.move(hung.x, hung.y);
  await a.mouse.down();
  await a.mouse.move(hung.x + 10, hung.y + 140, { steps: 10 });
  const pulled = await a.evaluate(() => {
    const blob = [...window.__jellyBox.world.blobs.values()].find((b) => b.fixed);
    blob.updateBounds();
    return blob.maxY - blob.minY;
  });
  await a.mouse.up();
  await settled(a, 2);
  const back = (await box(a)).find((c) => c.title === '부모님 생신');
  const round = await a.evaluate(() => {
    const blob = [...window.__jellyBox.world.blobs.values()].find((b) => b.fixed);
    blob.updateBounds();
    return { w: blob.maxX - blob.minX, h: blob.maxY - blob.minY };
  });
  check(
    back.fixed && Math.hypot(back.x - hung.x, back.y - hung.y) < 3 && pulled > round.h * 1.15 && Math.abs(round.w - round.h) < 3,
    `galaxy box: a pinned jelly stretches (${Math.round(pulled)}px tall) and springs back to its pin (${Math.round(round.w)}x${Math.round(round.h)})`,
  );
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
  // "내 프로필" at the top of the menu: the character and its colour change from there.
  check((await tid(b, 'profile').textContent()).includes('지은'), 'iphone: the menu starts with my profile');
  const bodyOf = '[data-testid="menu"] .avatar[title="지은"] svg circle';
  const oldBody = await a.locator(bodyOf).first().getAttribute('fill');
  await tid(b, 'profile').click();
  await tid(b, 'profile-name').waitFor();
  const onColor = await b.locator('.sheet .swatch.on').getAttribute('data-testid');
  await tid(b, onColor === 'color-7' ? 'color-2' : 'color-7').click();
  await tid(b, 'profile-save').click();
  await b.locator('.sheet').waitFor({ state: 'detached' });
  const recoloured = await a.waitForFunction(
    ([sel, old]) => document.querySelector(sel)?.getAttribute('fill') !== old,
    [bodyOf, oldBody],
    { timeout: 10000 },
  ).then(() => true, () => false);
  check(recoloured, `iphone: a new colour from my profile reaches the other phone (was ${oldBody})`);

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

  // 7b. A second calendar on the Galaxy ("재훈·준헌"): each calendar shows only its own jellies.
  await tid(a, 'view-month').click();
  await tid(a, 'menu').click();
  await tid(a, 'menu-groups').click();
  await tid(a, 'new-group').click();
  await tid(a, 'group-name').fill('재훈·준헌');
  await tid(a, 'group-create').click();
  await a.waitForFunction(() => document.querySelector('[data-testid="groups"]')?.textContent.includes('재훈·준헌'));
  check(true, 'galaxy: makes a second shared calendar');
  await openDay(a, `${month}-17`);
  await tid(a, 'day-add').click();
  await tid(a, 'title').fill('축구');
  await tid(a, 'time').fill('18:00');
  await tid(a, 'post').click();
  await tid(a, 'memos').waitFor();
  await backToMonth(a);
  const only = await a.waitForFunction(
    (sel) => document.querySelectorAll(sel).length === 1 && document.querySelector(sel).textContent.includes('축구'),
    `[data-day="${month}-17"] .mini`,
    { timeout: 10000 },
  ).then(() => true, () => false);
  const cellText = await a.locator(`[data-day="${month}-17"]`).textContent();
  check(only, `galaxy: the new calendar holds only its own jellies (${cellText.trim()})`);
  await tid(a, 'groups').click();
  await tid(a, 'group-list').waitFor();
  check((await tid(a, 'group-list').locator('.group-row').count()) === 2, 'galaxy: both calendars are listed');
  await shot(a, '10c-galaxy-groups');
  await tid(a, 'group-list').locator('.group-row').first().click();
  await a.waitForFunction(() => document.querySelector('[data-testid="groups"]')?.textContent.includes('공유 젤리 달력'));
  check((await a.locator(`[data-day="${month}-17"] .mini`).count()) === 1, 'galaxy: back on the first calendar');

  // 7c. A new phone: signing in with the ID brings both calendars back, without an invite.
  const phone = await browser.newContext({
    viewport: { width: 390, height: 844 },
    deviceScaleFactor: 3,
    isMobile: true,
    hasTouch: true,
    locale: 'ko-KR',
    reducedMotion: 'reduce',
    timezoneId: 'Asia/Seoul',
  });
  const c = await phone.newPage();
  c.on('pageerror', (e) => console.error('[new phone] page error', e));
  c.on('dialog', (d) => noBrowserDialog(d));
  await c.goto(BASE);
  await tid(c, 'account-id').fill('changhyun');
  await tid(c, 'account-password').fill('wrong-password');
  await tid(c, 'sign-in').click();
  await c.locator('.toast', { hasText: '아이디나 비밀번호가 맞지 않아요' }).waitFor({ timeout: 10000 });
  check(true, 'new phone: a wrong password is turned away');
  await tid(c, 'account-password').fill('jelly-pass-1');
  await tid(c, 'sign-in').click();
  await tid(c, 'month').waitFor({ timeout: 15000 });
  await c.locator(`[data-day="${month}-17"] .mini`).first().waitFor({ timeout: 15000 });
  check(true, 'new phone: signing in opens the calendar with its jellies');
  await tid(c, 'groups').click();
  check((await tid(c, 'group-list').locator('.group-row').count()) === 2, 'new phone: both calendars came back from the vault');
  await shot(c, '10d-new-phone-groups');
  await c.goBack();
  await tid(c, 'menu').click();
  await tid(c, 'sign-out').click();
  await tid(c, 'ask').waitFor();
  await shot(c, '10e-sign-out-question');
  await tid(c, 'ask-no').click();
  await tid(c, 'ask').waitFor({ state: 'detached' });
  check(await tid(c, 'sign-out').isVisible(), 'new phone: saying no to signing out keeps you signed in');
  await tid(c, 'sign-out').click();
  await tid(c, 'ask-yes').click();
  await tid(c, 'account-step').waitFor();
  check(await c.evaluate(() => !Object.keys(localStorage).some((k) => k.startsWith('jellyShare.key.'))), 'new phone: signing out leaves no calendar keys behind');
  await phone.close();

  // The vault on the server is sealed too.
  const vaults = await rawDocs('users');
  check(vaults.includes('"vault"') && !vaults.includes('공유 젤리 달력') && !vaults.includes('재훈'), 'the key vault on the server is sealed');

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
      openAlarmEditor: call('openAlarmEditor'),
    };
  }, `${month}-17`);
  const called = (page, name, arg) => page.waitForFunction(
    ([n, x]) => window.__calls.some((c) => c[0] === n && c[1] === x),
    [name, arg],
    { timeout: 5000 },
  );
  await app.goto(BASE);
  await tid(app, 'legend').waitFor();
  await settled(app, 5);
  const legend = await tid(app, 'legend').textContent();
  check(legend.includes('내 젤리 2') && legend.includes('공유 젤리 3') && legend.includes('달력 2개'),
    `all: my two jellies and the shared ones of both calendars in one box (${legend.trim()})`);
  await app.waitForTimeout(600);
  await shot(app, '11-galaxy-all');

  blobs = await box(app);
  const gym = blobs.find((c) => c.title === '헬스');
  await app.mouse.click(gym.x, gym.y);
  await called(app, 'openPersonal', 'p1');
  check(true, 'all: a tap on my own jelly opens it in the app');

  await settled(app, 5);
  blobs = await box(app);
  const report = blobs.find((c) => c.title === '보고서 작성');
  await app.mouse.dblclick(report.x, report.y);
  await called(app, 'togglePersonal', 'p2');
  check(true, 'all: a double tap on my own jelly finishes it in the app');

  // Inside the app the app keeps the golden code: the page hands the find over.
  await settled(app, 5);
  await grab(app, '헬스', 50);
  await called(app, 'foundGolden', 'p1');
  check(!(await tid(app, 'golden').count()), 'all: a golden find inside the app is the app\'s to announce');

  // The page letters itself the way the app's settings say.
  await app.evaluate(() => {
    window.__host = { ...window.__host, font: 'ROUND' };
    window.jellyHost.poke();
  });
  check((await display(app)).includes('Bagel'), 'all: follows the lettering picked in the app');

  // The character picked in the app's settings: my own jellies wear it when the app says so, and
  // the page puts it into my profile in every calendar.
  await app.evaluate(() => {
    window.__host = { ...window.__host, look: { job: 'chef', v: 1 }, lookOnMine: true };
    window.jellyHost.poke();
  });
  await app.waitForFunction(
    () => [...window.__jellyBox.byKey.values()].some((it) => it.ref?.kind === 'personal' && it.look?.job === 'chef' && it.look?.v === 1),
    null,
    { timeout: 5000 },
  );
  check(true, 'all: my own jellies wear the character picked in the app');
  await a.locator('.look-avatar[data-look="chef-1"]').first().waitFor({ timeout: 10000 });
  check(true, 'all: the app\'s character reaches my profile, and the other pages see it');
  await shot(app, '11c-galaxy-all-looks');

  // "모두" as a month: my jellies and the shared ones, and a tapped day goes to the app.
  await tid(app, 'view-month').click();
  await tid(app, 'grid').waitFor();
  const mine17 = await app.locator(`[data-day="${month}-17"] .mini.mine`).count();
  const shared17 = await app.locator(`[data-day="${month}-17"] .mini:not(.mine)`).count();
  check(mine17 === 1 && shared17 === 2, `all: the month shows my unfinished jelly and both calendars' (${mine17} + ${shared17})`);
  await shot(app, '11b-galaxy-all-month');
  await app.locator(`[data-day="${month}-24"]`).click();
  await called(app, 'showDay', `${month}-24`);
  check(true, 'all: a tapped day goes to the app');
  await sheetCards(app, '주말 등산').waitFor();
  check((await tid(app, 'day-add').textContent()).includes('넣기'), 'all: and its jellies come up at once, with a button to add one');
  await backToMonth(app);
  await tid(app, 'view-box').click();
  await settled(app, 5);

  await settled(app, 5);
  blobs = await box(app);
  const shared = blobs.find((c) => c.title === '부모님 생신');
  await app.mouse.click(shared.x, shared.y);
  await tid(app, 'memos').waitFor();
  check(true, 'all: a tap on a shared jelly opens it with its memos');
  check(await tid(app, 'alarm').isDisabled(), 'all: the clock alarm waits for a jelly with a time');
  await app.goBack();
  await settled(app, 5);
  blobs = await box(app);
  const football = blobs.find((c) => c.title === '축구');
  await app.mouse.click(football.x, football.y);
  await tid(app, 'memos').waitFor();
  check((await app.locator('.sheet-kicker').textContent()).includes('재훈·준헌'), 'all: a jelly of the other calendar opens in its calendar');
  // Its alarm (10 minutes before 18:00): one tap within a day, else Samsung Clock, where the date is picked.
  const kickoff = await app.evaluate((m) => new Date(`${m}-17T17:50:00`).getTime() - Date.now(), month);
  const alarmText = (await tid(app, 'alarm').textContent()).trim();
  if (kickoff >= 24 * 3600 * 1000) {
    check(alarmText === '날짜 골라 맞추기' && !(await tid(app, 'alarm').isDisabled()),
      `all: an alarm more than a day ahead is made by picking its date (${alarmText})`);
    await tid(app, 'alarm').click();
    await tid(app, 'ask').waitFor();
    check((await tid(app, 'ask').textContent()).includes('17일을 고른'), 'all: it first says how to pick the date in Samsung Clock');
    await shot(app, '11c-galaxy-alarm-date');
    await tid(app, 'ask-yes').click();
    await app.waitForFunction(
      () => window.__calls.some((c) => c[0] === 'openAlarmEditor' && c[1] === 17 && c[2] === 50 && c[3] === '축구 10분 전'),
      null,
      { timeout: 5000 },
    );
    check(!(await app.evaluate(() => window.__calls.some((c) => c[0] === 'setAlarm'))),
      'all: then Samsung Clock\'s new-alarm screen opens with the time filled in, and no alarm is set for the coming day');
  } else if (kickoff >= 0) {
    check(alarmText === '알람 맞추기' && !(await tid(app, 'alarm').isDisabled()), `all: an alarm within a day is set in one tap (${alarmText})`);
  } else {
    check(await tid(app, 'alarm').isDisabled(), 'all: an alarm already past cannot be set');
  }
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

  // 9. Google inside the app: the app hands over an ID token (a stand-in the emulator accepts). A
  // vault password set on one phone opens the calendar on the next one.
  const googlePhone = async () => {
    const context = await browser.newContext({
      viewport: { width: 412, height: 915 },
      isMobile: true,
      hasTouch: true,
      locale: 'ko-KR',
      reducedMotion: 'reduce',
      timezoneId: 'Asia/Seoul',
      userAgent: 'Mozilla/5.0 (Linux; Android 14; SM-S921N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Mobile Safari/537.36 JellyCalendarApp/0.2',
    });
    const page = await context.newPage();
    page.on('pageerror', (e) => console.error('[google phone] page error', e));
    page.on('dialog', (d) => noBrowserDialog(d));
    await page.addInitScript(() => {
      const token = JSON.stringify({ sub: 'google-user-1', email: 'jelly.friend@example.com', email_verified: true });
      window.JellyBridge = {
        hostState: () => JSON.stringify({ mode: 'shared' }),
        googleAvailable: () => true,
        googleSignIn: () => setTimeout(() => window.jellyHost.googleToken(token), 50),
      };
    });
    await page.goto(BASE);
    return { context, page };
  };
  const g1 = await googlePhone();
  await tid(g1.page, 'google').click();
  await tid(g1.page, 'signed-in').waitFor({ timeout: 15000 });
  check((await tid(g1.page, 'signed-in').textContent()).includes('jelly.friend@example.com'), 'google: signs in inside the app with the app\'s token');
  await tid(g1.page, 'name').fill('구글친구');
  await tid(g1.page, 'create').click();
  await tid(g1.page, 'month').waitFor();
  await tid(g1.page, 'menu').click();
  await tid(g1.page, 'vault-set').click();
  await tid(g1.page, 'vault-new').fill('vault-pass-9');
  await tid(g1.page, 'vault-again').fill('vault-pass-9');
  await tid(g1.page, 'vault-save').click();
  await g1.page.locator('.toast', { hasText: '열쇠 비밀번호를 정했어요' }).waitFor();
  await g1.page.waitForTimeout(1500);
  check(true, 'google: sets a vault password');
  await g1.context.close();

  const g2 = await googlePhone();
  await tid(g2.page, 'google').click();
  await tid(g2.page, 'vault-pass').waitFor({ timeout: 15000 });
  await tid(g2.page, 'vault-pass').fill('wrong-vault');
  await tid(g2.page, 'vault-open').click();
  await g2.page.locator('.toast', { hasText: '열쇠 비밀번호가 맞지 않아요' }).waitFor();
  await tid(g2.page, 'vault-pass').fill('vault-pass-9');
  await tid(g2.page, 'vault-open').click();
  await tid(g2.page, 'month').waitFor({ timeout: 15000 });
  check(true, 'google: the vault password opens the calendar on a new phone');
  await tid(g2.page, 'menu').click();
  await tid(g2.page, 'leave').click();
  await tid(g2.page, 'ask-yes').click();
  await g2.page.locator('.toast', { hasText: '달력을 지우고 나왔어요' }).waitFor({ timeout: 15000 });
  check(true, 'google: leaving asks on the page, and the last one out takes the calendar along');
  await g2.context.close();
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
