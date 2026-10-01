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
  check(/^[0-9A-Z]{4}-[0-9A-Z]{4}$/.test(code), `galaxy: invite code ${code}`);
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
    await tid(page, 'post').click();
    await tid(page, 'memos').waitFor();
    await page.goBack();
  }
  await b.locator(`[data-day="${day}"]`).click();
  await b.locator('[data-testid="card"].done').waitFor({ timeout: 10000 });
  check(true, 'iphone: sees it marked done');
  await b.waitForTimeout(800);
  await shot(b, '6-iphone-month');
  await a.locator(`[data-day="${month}-17"]`).click();
  await a.waitForTimeout(800);
  await shot(a, '7-galaxy-month');
  const cells = await b.locator(`[data-day="${month}-17"] .mini`).count();
  check(cells === 2, 'iphone: two jellies on the 17th in the month grid');
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
