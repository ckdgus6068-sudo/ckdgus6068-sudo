// Bundles the Firebase SDK for the web app: node build.mjs
import { build } from 'esbuild';
import { readFileSync } from 'node:fs';

const version = JSON.parse(readFileSync(new URL('./node_modules/firebase/package.json', import.meta.url))).version;
await build({
  entryPoints: [new URL('./firebase-entry.js', import.meta.url).pathname],
  outfile: new URL('../../docs/share/vendor/firebase.js', import.meta.url).pathname,
  bundle: true,
  format: 'esm',
  minify: true,
  target: ['safari15', 'chrome100'],
  legalComments: 'eof',
  banner: { js: `// Firebase JS SDK ${version} (Apache-2.0), bundled by jelly-calendar/share/build.mjs` },
});
console.log('bundled firebase', version);
