#!/usr/bin/env node
/**
 * Scrape story METADATA from monkeydd.com to build local test data.
 *
 * What this takes and what it deliberately does not:
 *   - Takes: title, slug, cover URL, genres, translation team, view/follow/
 *     favourite counters, progress status, and the chapter list (number + title).
 *   - Does NOT take chapter bodies. The source serves them through
 *     CSS-injected spans (an anti-scraping measure), and working around that
 *     to bulk-copy translated works is not something this script does. The
 *     seed generator writes placeholder chapter text locally instead, which is
 *     all the reader UI needs to be exercised.
 *
 * Output: db/seed-data/monkeydd-sample.json (gitignored, local only).
 * The generator (be/src/main/resources/db/seed/generate_seed_sql.js) turns
 * that JSON into the repeatable local seed migration.
 *
 * Usage:
 *   node scripts/scrape-monkeydd.mjs [--stories 100] [--chapters 3] [--delay 800]
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = path.resolve(__dirname, '..');
const OUTPUT_FILE = path.join(PROJECT_ROOT, 'db', 'seed-data', 'monkeydd-sample.json');

const BASE = 'https://monkeydd.com';
const LIST_PATH = '/truyen-moi.html';

function parseArgs(argv) {
  const options = { stories: 100, chapters: 3, delay: 800 };
  for (let i = 0; i < argv.length; i += 1) {
    const flag = argv[i];
    if (!flag.startsWith('--')) continue;
    const key = flag.slice(2);
    if (!(key in options)) throw new Error(`Unknown flag: ${flag}`);
    const value = Number(argv[i + 1]);
    if (!Number.isFinite(value) || value <= 0) {
      throw new Error(`Flag ${flag} needs a positive number.`);
    }
    options[key] = value;
    i += 1;
  }
  return options;
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/** One request at a time with a fixed delay, so the source is not hammered. */
async function fetchText(url, { retries = 3, delay = 800 } = {}) {
  for (let attempt = 1; attempt <= retries; attempt += 1) {
    try {
      const response = await fetch(url, {
        headers: {
          // Identifies the script rather than impersonating a browser.
          'User-Agent': 'GioiTruyen-local-seed/1.0 (local test data builder)',
          'Accept-Language': 'vi,en;q=0.8',
        },
        signal: AbortSignal.timeout(20000),
      });
      if (response.status === 404) return null;
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      return await response.text();
    } catch (error) {
      if (attempt === retries) throw new Error(`${url}: ${error.message}`);
      await sleep(delay * attempt * 2);
    }
  }
  return null;
}

const HTML_ENTITIES = {
  amp: '&', lt: '<', gt: '>', quot: '"', apos: "'", nbsp: ' ',
  ldquo: '“', rdquo: '”', hellip: '…', mdash: '—', ndash: '–',
};

function decodeEntities(text) {
  return text
    .replace(/&#(\d+);/g, (_, code) => String.fromCodePoint(Number(code)))
    .replace(/&#x([0-9a-f]+);/gi, (_, code) => String.fromCodePoint(parseInt(code, 16)))
    .replace(/&([a-z]+);/gi, (match, name) => HTML_ENTITIES[name.toLowerCase()] ?? match);
}

function stripTags(html) {
  return decodeEntities(html.replace(/<[^>]*>/g, ' ')).replace(/\s+/g, ' ').trim();
}

/** Turn a Vietnamese title into an ASCII slug matching the schema's slug style. */
function slugify(text) {
  return text
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 200);
}

/** Title case a SHOUTED title so the catalog cards do not read as all caps. */
function normaliseTitle(raw) {
  const text = stripTags(raw);
  const letters = text.replace(/[^\p{L}]/gu, '');
  const uppers = text.replace(/[^\p{Lu}]/gu, '').length;
  if (letters.length === 0 || uppers / letters.length < 0.8) return text;
  return text
    .toLocaleLowerCase('vi')
    .replace(/(^|[\s(“"'\-–—:])(\p{L})/gu, (_, prefix, char) => prefix + char.toLocaleUpperCase('vi'));
}

function parseCount(raw) {
  const digits = String(raw ?? '').replace(/[^\d]/g, '');
  return digits ? Number(digits) : 0;
}

/** Collect story detail URLs from the "truyện mới" listing, page by page. */
async function collectStoryUrls(limit, delay) {
  const urls = [];
  const seen = new Set();
  for (let page = 1; urls.length < limit && page <= 40; page += 1) {
    const url = page === 1 ? `${BASE}${LIST_PATH}` : `${BASE}${LIST_PATH}?page=${page}`;
    const html = await fetchText(url, { delay });
    if (!html) break;

    // Only the listing grid carries `story-item-title`; the sidebar rankings do
    // not, which keeps "top tuần/tháng" entries out of the sample.
    const cardPattern = /<a href="(https:\/\/monkeydd\.com\/[^"/]+\.html)">\s*<h3 class="card-title[^"]*story-item-title[^"]*">/g;
    let found = 0;
    for (const match of html.matchAll(cardPattern)) {
      found += 1;
      const storyUrl = match[1];
      if (seen.has(storyUrl)) continue;
      seen.add(storyUrl);
      urls.push(storyUrl);
      if (urls.length >= limit) break;
    }
    console.log(`  list page ${page}: +${found} cards (total ${urls.length}/${limit})`);
    if (found === 0) break;
    await sleep(delay);
  }
  return urls;
}

const PROGRESS_BY_LABEL = [
  [/đã đủ bộ|đã full|hoàn thành|full/i, 'COMPLETED'],
  [/tạm ngưng|tạm dừng|drop/i, 'PAUSED'],
];

function parseStoryPage(html, storyUrl) {
  const slugFromUrl = storyUrl.replace(/^.*\//, '').replace(/\.html$/, '');

  const titleMatch = html.match(/<h2 itemprop="name" class="card-title">([\s\S]*?)<\/h2>/);
  if (!titleMatch) return null;
  const title = normaliseTitle(titleMatch[1]);
  if (!title) return null;

  const coverMatch = html.match(/<img src="(https:\/\/cdn\.monkeydarchive\.com\/images\/story\/[^"]+)" class="img-fluid"/);

  const genres = [];
  const genreBlock = html.match(/<dt class="col-sm-3">Thể loại<\/dt>\s*<dd class="col-sm-9">([\s\S]*?)<\/dd>/);
  if (genreBlock) {
    for (const genre of genreBlock[1].matchAll(/href='https:\/\/monkeydd\.com\/the-loai\/([^']+)\.html'>([^<]*)</g)) {
      genres.push({ slug: genre[1], name: stripTags(genre[2]) });
    }
  }

  const teamMatch = html.match(/<dt class="col-sm-3">Team<\/dt>\s*<dd class="col-sm-9">\s*<a href="[^"]*\/nhom-dich\/(\d+)"[^>]*>\s*<b>([^<]*)<\/b>/);

  const readField = (label) => {
    const match = html.match(new RegExp(`<dt class="col-sm-3">${label}</dt>\\s*<dd class="col-sm-9">([\\s\\S]*?)</dd>`));
    return match ? stripTags(match[1]) : '';
  };

  const statusLabel = readField('Trạng thái');
  const progressStatus = PROGRESS_BY_LABEL.find(([pattern]) => pattern.test(statusLabel))?.[1] ?? 'ONGOING';

  // Chapter links appear newest-first in the listing; sort ascending and keep
  // only the numbered ones so chapter_number stays meaningful.
  const chapterBlock = html.match(/<div class="list-chapters">([\s\S]*?)<div class="tab-pane fade" id="listComments"/)
    ?? html.match(/<div class="list-chapters">([\s\S]*?)$/);
  const chapters = [];
  const chapterSeen = new Set();
  if (chapterBlock) {
    const linkPattern = /<a href="(https:\/\/monkeydd\.com\/[^"]+\/([^"/]+)\.html)">\s*([\s\S]*?)<\/a>/g;
    for (const link of chapterBlock[1].matchAll(linkPattern)) {
      const [, url, chapterSlug, rawLabel] = link;
      // Three slug styles are in use: "chuong-12", a bare "12", and
      // "the-gioi-1-chuong-12" for multi-arc stories.
      const numberMatch = chapterSlug.match(/chuong-(\d+)/) ?? chapterSlug.match(/^(\d+)$/);
      if (!numberMatch) continue;
      const number = Number(numberMatch[1]);
      // Multi-arc stories restart numbering per arc, but chapter_number is
      // UNIQUE per story - keep the first occurrence of each number.
      if (chapterSeen.has(number)) continue;
      chapterSeen.add(number);
      chapters.push({ number, slug: chapterSlug, title: stripTags(rawLabel), sourceUrl: url });
    }
  }
  chapters.sort((a, b) => a.number - b.number);

  return {
    sourceUrl: storyUrl,
    title,
    slug: slugify(title) || slugFromUrl,
    sourceSlug: slugFromUrl,
    coverUrl: coverMatch ? coverMatch[1] : null,
    genres,
    team: teamMatch ? { sourceId: teamMatch[1], name: stripTags(teamMatch[2]) } : null,
    viewCount: parseCount(readField('Lượt xem')),
    favoriteCount: parseCount(readField('Yêu thích')),
    followCount: parseCount(readField('Lượt theo dõi')),
    progressStatus,
    // Total published chapters, kept before the caller trims `chapters` down to
    // a sample. Without it a one-page story is indistinguishable from a long
    // one, and the catalog cannot tell which stories belong on the Zhihu shelf.
    chapterCount: chapters.length,
    chapters,
  };
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  console.log(`Scraping metadata for ${options.stories} stories (max ${options.chapters} chapters each).`);
  console.log('Chapter bodies are NOT downloaded - the generator writes local placeholder text.\n');

  console.log('Collecting story URLs...');
  const urls = await collectStoryUrls(options.stories, options.delay);
  if (urls.length === 0) throw new Error('No story URLs found - the listing markup may have changed.');

  const stories = [];
  const slugsTaken = new Set();
  for (const [index, url] of urls.entries()) {
    const html = await fetchText(url, { delay: options.delay });
    await sleep(options.delay);
    if (!html) {
      console.warn(`  [${index + 1}/${urls.length}] skipped (not found): ${url}`);
      continue;
    }
    const story = parseStoryPage(html, url);
    if (!story) {
      console.warn(`  [${index + 1}/${urls.length}] skipped (unparsable): ${url}`);
      continue;
    }
    // stories.slug is UNIQUE; keep the first occurrence and suffix later ones.
    let slug = story.slug;
    for (let suffix = 2; slugsTaken.has(slug); suffix += 1) slug = `${story.slug}-${suffix}`;
    slugsTaken.add(slug);
    story.slug = slug;
    story.chapters = story.chapters.slice(0, options.chapters);
    stories.push(story);
    console.log(`  [${index + 1}/${urls.length}] ${story.title} (${story.chapters.length} chapters, ${story.genres.length} genres)`);
  }

  if (stories.length === 0) throw new Error('Every story page failed to parse - aborting without writing output.');

  const payload = {
    source: `${BASE}${LIST_PATH}`,
    scrapedAt: new Date().toISOString(),
    note: 'Metadata only. Chapter bodies are generated locally by generate_seed_sql.js. Local development use.',
    requested: options,
    stories,
  };

  fs.mkdirSync(path.dirname(OUTPUT_FILE), { recursive: true });
  fs.writeFileSync(OUTPUT_FILE, JSON.stringify(payload, null, 2), 'utf8');

  const chapterTotal = stories.reduce((sum, story) => sum + story.chapters.length, 0);
  const genreTotal = new Set(stories.flatMap((story) => story.genres.map((genre) => genre.slug))).size;
  const teamTotal = new Set(stories.map((story) => story.team?.name).filter(Boolean)).size;
  console.log(`\nWrote ${stories.length} stories / ${chapterTotal} chapters / ${genreTotal} genres / ${teamTotal} teams`);
  console.log(`  -> ${OUTPUT_FILE}`);
  console.log('\nNext: node be/src/main/resources/db/seed/generate_seed_sql.js');
}

main().catch((error) => {
  console.error(`\nScrape failed: ${error.message}`);
  process.exit(1);
});
