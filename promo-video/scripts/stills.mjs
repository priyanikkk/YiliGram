// Renders a still of every scene (vertical and horizontal) into out/stills for a visual check.
import {execFileSync} from 'node:child_process';
import {mkdirSync} from 'node:fs';

const fps = 30;
const scenes = {intro: 7, expressive: 10, customization: 16, plugins: 15, details: 8, push: 6, openSource: 6, download: 7, disclaimer: 3};
// Seconds inside each scene to look at (after the entrances settle).
const at = {intro: [1.2, 5.5], expressive: [1.5, 7.5], customization: [4.5, 12.5], plugins: [6, 11], details: [2.2, 6.5], push: [4], openSource: [4], download: [5], disclaimer: [2]};
mkdirSync('out/stills', {recursive: true});
const only = process.argv[2];
let start = 0;
for (const [k, len] of Object.entries(scenes)) {
  for (const s of at[k]) {
    const frame = Math.round((start + s) * fps);
    for (const comp of ['YiliGramVertical', 'YiliGramHorizontal']) {
      if (only && !comp.toLowerCase().includes(only)) continue;
      const out = `out/stills/${comp === 'YiliGramVertical' ? 'v' : 'h'}-${k}-${s}.png`;
      execFileSync('npx', ['remotion', 'still', comp, out, `--frame=${frame}`, '--log=error'], {stdio: 'inherit'});
    }
  }
  start += len;
}
