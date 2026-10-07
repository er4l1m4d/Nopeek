// Shared rig: one browser, one page, one clock. Everything else reads from here.
import { chromium } from 'playwright-core';
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';
import path from 'node:path';
import fs from 'node:fs';

const require = createRequire(import.meta.url);

export const ROOT = path.resolve(import.meta.dirname, '..');
export const FPS = 60;
export const FILM = process.env.FILM || 'film2.html';   // v2 is the active film; FILM=film.html for v1
export const DURATION = Number(process.env.DUR || 72.0); // seconds; one source of truth
export const TOTAL_FRAMES = Math.round(FPS * DURATION);  // 4320 for v2

export function ffmpeg() {
  // ffmpeg-static ships the binary and exports its path.
  const p = require('ffmpeg-static');
  if (!p || !fs.existsSync(p)) throw new Error('ffmpeg-static binary missing');
  return p;
}

export function pageUrl(file) {
  return pathToFileURL(path.join(ROOT, file)).href;
}

export async function openFilm(file = FILM) {
  // --disable-gpu: Skia GPU raster varies 1 LSB run-to-run on fractional-AA
  // content (proven: t=21.98 differs across launches with GPU, identical with
  // CPU). CPU raster keeps seek(t) byte-deterministic across processes.
  const CPU = ['--force-color-profile=srgb', '--font-render-hinting=none', '--disable-lcd-text', '--disable-gpu'];
  const launch = async (opts) => chromium.launch(opts);
  let browser;
  try {
    browser = await launch({ channel: 'chrome', headless: true, args: CPU });
  } catch (e) {
    browser = await launch({ executablePath: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe', headless: true, args: CPU });
  }
  const page = await browser.newPage({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 1 });
  page.__errors = [];
  page.on('console', m => { if (m.type() === 'error') page.__errors.push('console: ' + m.text()); });
  page.on('pageerror', e => page.__errors.push('pageerror: ' + e.message));
  await page.goto(pageUrl(file), { waitUntil: 'load' });
  await page.waitForFunction(() => window.READY === true, null, { timeout: 30000 });
  const fonts = await page.evaluate(() => window.__fonts);
  return { browser, page, fonts };
}

// The single contract with the page: ask for a moment, get a frame.
export async function seekTo(page, t) {
  await page.evaluate((s) => window.seek(s), t);
}

export function ensureDir(p) { fs.mkdirSync(p, { recursive: true }); }
