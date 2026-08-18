/**
 * Generate the local development seed migration.
 *
 * Input : db/seed-data/monkeydd-sample.json  (written by scripts/scrape-monkeydd.mjs)
 * Output: be/src/main/resources/db/seed/R__local_seed_data.sql
 *
 * Two properties this file has to keep, because breaking either one is what
 * made the previous seed drift out of sync with the database:
 *
 *  1. REPEATABLE, not versioned. The output is `R__...sql`, so Flyway re-runs
 *     it whenever its checksum changes. The old `V2__seed_data.sql` was a
 *     versioned migration: once applied, regenerating it could never reach the
 *     database, and the changed checksum failed validation on the next boot.
 *  2. DETERMINISTIC. Every value is derived from the input JSON through a
 *     seeded PRNG - never Math.random() or a wall clock. Identical input must
 *     produce a byte-identical file, otherwise the checksum changes on every
 *     run and Flyway re-applies the seed forever.
 *
 * The seed is only on the classpath location list of the "local" profile
 * (see application-local.yml), so a production database never sees it.
 *
 * Usage: node be/src/main/resources/db/seed/generate_seed_sql.js
 */

const fs = require('fs');
const path = require('path');

const SEED_DIR = __dirname;
const PROJECT_ROOT = path.resolve(SEED_DIR, '..', '..', '..', '..', '..', '..');
const INPUT_FILE = path.join(PROJECT_ROOT, 'db', 'seed-data', 'monkeydd-sample.json');
const OUTPUT_FILE = path.join(SEED_DIR, 'R__local_seed_data.sql');

// Fixed timestamp anchor. A wall clock here would change the checksum on every
// run; all relative dates hang off this instead.
const NOW_LITERAL = "TIMESTAMP('2026-08-17 09:00:00')";

// Reserved id ranges. The seed wipes exactly these prefixes before inserting,
// so a regenerated seed never leaves stale rows behind.
const ID = {
  user: (n) => `00000000-0000-0000-0000-${String(n).padStart(12, '0')}`,
  authAccount: (n) => `01000000-0000-0000-0000-${String(n).padStart(12, '0')}`,
  team: (n) => `03000000-0000-0000-0000-${String(n).padStart(12, '0')}`,
  genre: (n) => `10000000-0000-0000-0000-${String(n).padStart(12, '0')}`,
  story: (n) => `11000000-0000-0000-0000-${String(n).padStart(12, '0')}`,
  chapter: (storyIndex, chapterIndex) =>
    `13000000-0000-0000-${String(storyIndex).padStart(4, '0')}-${String(chapterIndex).padStart(12, '0')}`,
};
const PREFIX = {
  team: '03000000-0000-0000-0000-',
  genre: '10000000-0000-0000-0000-',
  story: '11000000-0000-0000-0000-',
  chapter: '13000000-0000-0000-',
};

/** bcrypt hash of "Admin@123" - the demo password for every seeded account. */
const DEMO_PASSWORD_HASH = '$2a$10$DJ7.CxIRm.97hv9g1JnLi.pV7pvYJEOMNt4hA7n.OW74suEpuJ7Im';

const USERS = [
  { n: 1, email: 'admin@gioitruyen.com', username: 'admin', name: 'Quản trị Giới Truyện', role: 'ADMIN' },
  { n: 2, email: 'maianh@gioitruyen.com', username: 'maianh', name: 'Mai Anh Dịch Truyện', role: 'READER' },
  { n: 3, email: 'linhchi@gioitruyen.com', username: 'linhchi', name: 'Linh Chi Biên Tập', role: 'READER' },
];
const ADMIN_ID = ID.user(1);
const UPLOADER_ID = ID.user(2);

// ---------------------------------------------------------------------------
// Deterministic helpers
// ---------------------------------------------------------------------------

/** FNV-1a, used to derive a stable per-story PRNG seed from its slug. */
function hashString(text) {
  let hash = 0x811c9dc5;
  for (let i = 0; i < text.length; i += 1) {
    hash ^= text.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193) >>> 0;
  }
  return hash >>> 0;
}

/** mulberry32 - small, stable PRNG so the same slug always yields the same numbers. */
function makeRandom(seed) {
  let state = seed >>> 0;
  return function random() {
    state = (state + 0x6d2b79f5) >>> 0;
    let t = state;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const randomInt = (random, min, max) => min + Math.floor(random() * (max - min + 1));
const pick = (random, list) => list[Math.floor(random() * list.length)];

/** Escape for a single-quoted MySQL string literal. */
function sql(value) {
  if (value === null || value === undefined) return 'NULL';
  return `'${String(value).replace(/\\/g, '\\\\').replace(/'/g, "''")}'`;
}

function slugify(text) {
  return String(text)
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

const truncate = (text, max) => (text.length <= max ? text : `${text.slice(0, max - 1).trimEnd()}…`);

// ---------------------------------------------------------------------------
// Chapter bodies
//
// Placeholder prose written here, not taken from the source site. It only has
// to be long enough and varied enough to exercise the reader UI: paragraph
// rendering, scroll position, reading progress and the paywall boundary.
// ---------------------------------------------------------------------------

const OPENINGS = [
  'Trời vừa hửng sáng khi tin tức truyền đến sân trước',
  'Cơn mưa cuối hạ gõ lên mái hiên suốt cả canh giờ',
  'Ngọn đèn trong thư phòng vẫn chưa tắt',
  'Tiếng bước chân ngoài hành lang dừng lại trước cửa',
  'Chiếc xe ngựa dừng ở đầu con phố nhỏ',
  'Buổi chiều hôm ấy gió lặng đến lạ thường',
];
const MIDDLES = [
  'Nàng đặt chén trà xuống, chậm rãi cân nhắc từng chữ trước khi lên tiếng.',
  'Câu trả lời đến muộn hơn dự tính, và vì thế lại càng khó nghe.',
  'Không ai trong phòng dám nhìn thẳng vào mắt người vừa bước vào.',
  'Mọi lời giải thích đều trở nên vô nghĩa khi sự thật đã nằm trên bàn.',
  'Có những chuyện chỉ cần nhắc một lần là đủ để thay đổi tất cả.',
  'Bàn tay siết chặt vạt áo, nhưng giọng nói vẫn giữ được vẻ bình thản.',
  'Người ngoài cuộc chỉ thấy phần nổi, còn phần chìm thì không ai muốn kể.',
  'Ván cờ đã đi đến nước phải quyết, không còn đường lùi cho bất kỳ ai.',
];
const CLOSINGS = [
  'Đến khi cánh cửa khép lại, nàng mới nhận ra mình đã nín thở từ lâu.',
  'Câu chuyện dừng ở đó, nhưng ai cũng biết nó chưa hề kết thúc.',
  'Ngày mai trời có thể quang, cũng có thể mưa thêm một trận nữa.',
  'Chỉ còn lại tiếng gió, và một lời hứa chưa kịp nói thành tiếng.',
];

function buildChapterBody(random, title, chapterNumber, paragraphCount) {
  const paragraphs = [];
  paragraphs.push(`${pick(random, OPENINGS)}. Chương ${chapterNumber} của <em>${title}</em> mở ra từ chính khoảnh khắc đó.`);
  for (let i = 0; i < paragraphCount; i += 1) {
    const sentences = [];
    for (let j = 0; j < randomInt(random, 3, 5); j += 1) sentences.push(pick(random, MIDDLES));
    paragraphs.push(sentences.join(' '));
  }
  paragraphs.push(pick(random, CLOSINGS));
  return paragraphs.map((text) => `<p>${text}</p>`).join('\n');
}

// ---------------------------------------------------------------------------
// Build
// ---------------------------------------------------------------------------

function loadInput() {
  if (!fs.existsSync(INPUT_FILE)) {
    throw new Error(
      `Missing ${path.relative(PROJECT_ROOT, INPUT_FILE)}.\n` +
      'Run the scraper first:  npm run seed:scrape'
    );
  }
  const payload = JSON.parse(fs.readFileSync(INPUT_FILE, 'utf8'));
  if (!Array.isArray(payload.stories) || payload.stories.length === 0) {
    throw new Error('Input JSON contains no stories.');
  }
  return payload;
}

/** Collect the genre and team dimension tables out of the scraped stories. */
function buildDimensions(stories) {
  const genres = new Map();
  const teams = new Map();

  for (const story of stories) {
    for (const genre of story.genres ?? []) {
      const slug = slugify(genre.slug || genre.name);
      if (!slug || genres.has(slug)) continue;
      genres.set(slug, { id: ID.genre(genres.size + 1), slug, name: genre.name || slug });
    }
    const teamName = story.team?.name?.trim();
    if (teamName && !teams.has(teamName)) {
      teams.set(teamName, { id: ID.team(teams.size + 1), name: teamName, slug: slugify(teamName) });
    }
  }

  // Every story needs a team_id (NOT NULL), so guarantee at least one.
  if (teams.size === 0) {
    teams.set('Nhà Dịch Giới Truyện', {
      id: ID.team(1), name: 'Nhà Dịch Giới Truyện', slug: 'nha-dich-gioi-truyen',
    });
  }

  // Team slugs are unique in the schema; de-duplicate after slugifying.
  const slugsTaken = new Set();
  for (const team of teams.values()) {
    let slug = team.slug || 'nhom-dich';
    for (let suffix = 2; slugsTaken.has(slug); suffix += 1) slug = `${team.slug}-${suffix}`;
    slugsTaken.add(slug);
    team.slug = slug;
  }

  return { genres, teams };
}

function build() {
  const payload = loadInput();
  const { genres, teams } = buildDimensions(payload.stories);
  const teamList = [...teams.values()];

  const storyRows = [];
  const storyGenreRows = [];
  const storyTagRows = [];
  const chapterRows = [];
  const counts = { oneshot: 0, paidChapters: 0 };

  payload.stories.forEach((story, index) => {
    const storyIndex = index + 1;
    const storyId = ID.story(storyIndex);
    const random = makeRandom(hashString(story.slug));

    const team = story.team?.name && teams.has(story.team.name)
      ? teams.get(story.team.name)
      : teamList[index % teamList.length];

    let chapters = (story.chapters ?? []).slice();

    // Zhihu-style one-page reads feed a dedicated catalog shelf. Two signals
    // mark them: a story that really has a single chapter, and the "Đoản Văn"
    // (short-form) genre the source tags them with. `chapters` here is only a
    // 3-chapter sample, so its length says nothing - chapterCount is the real
    // total and is why the scraper records it separately.
    const isOneshot = story.chapterCount === 1
      || (story.genres ?? []).some((genre) => slugify(genre.slug || genre.name) === 'doan-van');
    const storyFormat = isOneshot ? 'ONESHOT' : 'SERIAL';
    if (isOneshot) {
      counts.oneshot += 1;
      // A one-page story with three chapters would contradict its own format.
      chapters = chapters.slice(0, 1);
    }

    // Spread the editorial types so every catalog shelf has rows to show.
    const storyType = storyIndex % 11 === 0 ? 'ORIGINAL' : storyIndex % 7 === 0 ? 'EXCLUSIVE' : 'TEXT';

    // A few non-published rows so admin moderation screens are not empty.
    const status = storyIndex % 23 === 0 ? 'PENDING_REVIEW' : storyIndex % 29 === 0 ? 'DRAFT' : 'PUBLISHED';
    const published = status === 'PUBLISHED';

    const publishedDaysAgo = randomInt(random, 1, 120);
    const lastChapterMinutesAgo = randomInt(random, 5, 4320);
    const publishedAt = published ? `${NOW_LITERAL} - INTERVAL ${publishedDaysAgo} DAY` : 'NULL';
    const lastChapterAt = published && chapters.length > 0
      ? `${NOW_LITERAL} - INTERVAL ${lastChapterMinutesAgo} MINUTE`
      : 'NULL';

    const storyGenres = (story.genres ?? [])
      .map((genre) => genres.get(slugify(genre.slug || genre.name)))
      .filter(Boolean);
    const genreNames = storyGenres.map((genre) => genre.name);
    const shortDescription = truncate(
      genreNames.length > 0
        ? `Truyện ${genreNames.slice(0, 3).join(' / ').toLowerCase()} do ${team.name} thực hiện.`
        : `Truyện do ${team.name} thực hiện.`,
      500,
    );
    const description = truncate(
      `<p><strong>${story.title}</strong> là bản dịch của ${team.name}.</p>` +
      (genreNames.length > 0 ? `<p>Thể loại: ${genreNames.join(', ')}.</p>` : '') +
      '<p>Đây là dữ liệu mẫu dùng cho môi trường phát triển cục bộ.</p>',
      60000,
    );

    storyRows.push([
      sql(storyId), sql(team.id), sql(UPLOADER_ID),
      sql(truncate(story.title, 255)), sql(truncate(story.slug, 280)),
      sql(truncate(story.title, 255)), sql(truncate(story.team?.name ?? 'Chưa xác định', 255)),
      sql(shortDescription), sql(description),
      sql(story.coverUrl), sql(story.coverUrl),
      "'TEXT'", sql(storyFormat), sql(storyType), sql(status),
      sql(story.progressStatus ?? 'ONGOING'), "'13+'",
      publishedAt, lastChapterAt,
      story.viewCount ?? 0, story.followCount ?? 0, story.favoriteCount ?? 0,
      randomInt(random, 0, 80),
      `${NOW_LITERAL} - INTERVAL ${publishedDaysAgo} DAY`, NOW_LITERAL,
    ]);

    for (const genre of storyGenres) {
      storyGenreRows.push(`(${sql(storyId)}, ${sql(genre.id)})`);
    }

    // story_tags exists so tag links resolve; reuse genre labels as tags.
    for (const genre of storyGenres.slice(0, 4)) {
      storyTagRows.push(`(${sql(storyId)}, ${sql(genre.slug)}, ${sql(genre.name)}, ${NOW_LITERAL})`);
    }

    chapters.forEach((chapter, chapterIdx) => {
      const chapterNumber = Number(chapter.number) || chapterIdx + 1;
      // Last chapter of a multi-chapter story is paid, so the paywall and the
      // unlock flow both have something to act on.
      const isPaid = chapters.length > 1 && chapterIdx === chapters.length - 1;
      if (isPaid) counts.paidChapters += 1;
      const chapterTitle = truncate(chapter.title?.trim() || `Chương ${chapterNumber}`, 255);
      const chapterSlug = truncate(`${story.slug}-chuong-${chapterNumber}`, 280);

      chapterRows.push([
        sql(ID.chapter(storyIndex, chapterIdx + 1)), sql(storyId),
        chapterNumber.toFixed(2), sql(chapterTitle), sql(chapterSlug),
        sql(buildChapterBody(random, story.title, chapterNumber, randomInt(random, 6, 12))),
        sql(truncate(`Nội dung mẫu cho ${chapterTitle}.`, 500)),
        isPaid ? "'PAID'" : "'FREE'", isPaid ? 20 : 0,
        published ? "'PUBLISHED'" : "'DRAFT'",
        published ? `${NOW_LITERAL} - INTERVAL ${publishedDaysAgo} DAY` : 'NULL',
        sql(UPLOADER_ID),
        `${NOW_LITERAL} - INTERVAL ${publishedDaysAgo} DAY`, NOW_LITERAL,
      ]);
    });
  });

  return { payload, genres, teams: teamList, storyRows, storyGenreRows, storyTagRows, chapterRows, counts };
}

// ---------------------------------------------------------------------------
// Render SQL
// ---------------------------------------------------------------------------

const values = (row) => `    (${row.join(', ')})`;

function render(model) {
  const { payload, genres, teams, storyRows, storyGenreRows, storyTagRows, chapterRows } = model;
  const out = [];

  out.push('-- ===========================================================================');
  out.push('-- Gioitruyen - LOCAL DEVELOPMENT SEED  (repeatable migration)');
  out.push('--');
  out.push('-- GENERATED FILE - do not edit by hand.');
  out.push('--   source : be/src/main/resources/db/seed/generate_seed_sql.js');
  out.push('--   input  : db/seed-data/monkeydd-sample.json');
  out.push('--   rebuild: npm run seed:refresh');
  out.push('--');
  out.push('-- Repeatable (R__) on purpose: Flyway re-applies it whenever the generated');
  out.push('-- content changes, which is what keeps the local database matching this file.');
  out.push('-- It runs only under the "local" profile (application-local.yml adds');
  out.push('-- classpath:db/seed); production loads classpath:db/migration only.');
  out.push('--');
  out.push(`-- Stories: ${storyRows.length}  Chapters: ${chapterRows.length}  Genres: ${genres.size}  Teams: ${teams.length}`);
  out.push(`-- Story metadata scraped from ${payload.source} at ${payload.scrapedAt}`);
  out.push('-- Chapter bodies are generated placeholder text, not source content.');
  out.push('-- Demo password for every seeded account: Admin@123');
  out.push('-- ===========================================================================');
  out.push('');
  out.push('-- Production guard. ${seedProfile} is defined only by application-local.yml,');
  out.push('-- and Flyway aborts a migration whose placeholder has no value. If this file');
  out.push('-- is ever loaded outside the "local" profile it fails here, before writing');
  out.push('-- a single row.');
  out.push("SET @seed_profile = '${seedProfile}';");
  out.push('');
  out.push(`SET @now = ${NOW_LITERAL};`);
  out.push('');

  out.push('-- ---------------------------------------------------------------------------');
  out.push('-- Clear the reserved seed id ranges first, so a regenerated seed cannot');
  out.push('-- leave rows from a previous generation behind. `chapters` and');
  out.push('-- `story_genres` carry no real foreign key to `stories` (MySQL ignores the');
  out.push('-- inline column-level REFERENCES in V1), so they must be deleted explicitly.');
  out.push('-- Nothing outside these prefixes is touched.');
  out.push('-- ---------------------------------------------------------------------------');
  out.push(`DELETE FROM \`chapters\` WHERE \`id\` LIKE '${PREFIX.chapter}%' OR \`story_id\` LIKE '${PREFIX.story}%';`);
  out.push(`DELETE FROM \`story_genres\` WHERE \`story_id\` LIKE '${PREFIX.story}%';`);
  out.push(`DELETE FROM \`story_tags\` WHERE \`story_id\` LIKE '${PREFIX.story}%';`);
  out.push(`DELETE FROM \`stories\` WHERE \`id\` LIKE '${PREFIX.story}%';`);
  out.push(`DELETE FROM \`genres\` WHERE \`id\` LIKE '${PREFIX.genre}%';`);
  out.push('');

  out.push('-- Seeded accounts are upserted rather than deleted: wallets, quests and');
  out.push('-- reading history hang off them and are worth keeping between refreshes.');
  out.push('INSERT INTO `users` (`id`, `email`, `username`, `password_hash`, `display_name`, `avatar_url`, `role`, `status`, `email_verified_at`, `last_login_at`, `created_at`, `updated_at`) VALUES');
  out.push(USERS.map((user) => values([
    sql(ID.user(user.n)), sql(user.email), sql(user.username), sql(DEMO_PASSWORD_HASH),
    sql(user.name), sql(`/assets/avatars/${user.username}.png`), sql(user.role), "'ACTIVE'",
    '@now - INTERVAL 60 DAY', '@now - INTERVAL 1 HOUR', '@now - INTERVAL 60 DAY', '@now',
  ])).join(',\n'));
  out.push('ON DUPLICATE KEY UPDATE `display_name` = VALUES(`display_name`), `password_hash` = VALUES(`password_hash`), `role` = VALUES(`role`), `status` = VALUES(`status`), `updated_at` = VALUES(`updated_at`);');
  out.push('');

  out.push('INSERT INTO `auth_accounts` (`id`, `user_id`, `provider`, `provider_account_id`, `created_at`) VALUES');
  out.push(USERS.map((user) => values([
    sql(ID.authAccount(user.n)), sql(ID.user(user.n)), "'LOCAL'", sql(user.email), '@now - INTERVAL 60 DAY',
  ])).join(',\n'));
  out.push('ON DUPLICATE KEY UPDATE `user_id` = VALUES(`user_id`);');
  out.push('');

  out.push('INSERT INTO `teams` (`id`, `name`, `slug`, `avatar_url`, `cover_url`, `description`, `status`, `created_by`, `created_at`, `updated_at`) VALUES');
  out.push(teams.map((team) => values([
    sql(team.id), sql(truncate(team.name, 255)), sql(truncate(team.slug, 280)),
    sql(`/assets/teams/${team.slug}.png`), sql(`/assets/teams/${team.slug}-cover.png`),
    sql(`Nhóm dịch ${team.name}. Dữ liệu mẫu cho môi trường cục bộ.`),
    "'ACTIVE'", sql(ADMIN_ID), '@now - INTERVAL 40 DAY', '@now',
  ])).join(',\n'));
  out.push('ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `slug` = VALUES(`slug`), `status` = VALUES(`status`), `updated_at` = VALUES(`updated_at`);');
  out.push('');

  out.push('INSERT INTO `genres` (`id`, `name`, `slug`, `description`, `icon_url`, `is_active`, `created_at`, `updated_at`) VALUES');
  out.push([...genres.values()].map((genre) => values([
    sql(genre.id), sql(truncate(genre.name, 255)), sql(truncate(genre.slug, 280)),
    sql(`Truyện thuộc thể loại ${genre.name}.`), sql(`/assets/genres/${genre.slug}.svg`),
    'TRUE', '@now - INTERVAL 40 DAY', '@now',
  ])).join(',\n'));
  out.push('ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `is_active` = VALUES(`is_active`), `updated_at` = VALUES(`updated_at`);');
  out.push('');

  out.push('INSERT INTO `stories` (`id`, `team_id`, `created_by`, `title`, `slug`, `original_title`, `original_author`, `short_description`, `description`, `cover_url`, `banner_url`, `content_type`, `story_format`, `story_type`, `status`, `progress_status`, `age_rating`, `published_at`, `last_chapter_at`, `view_count_cache`, `follow_count_cache`, `favorite_count_cache`, `recommendation_gem_cache`, `created_at`, `updated_at`) VALUES');
  out.push(storyRows.map(values).join(',\n'));
  out.push(';');
  out.push('');

  if (storyGenreRows.length > 0) {
    out.push('INSERT INTO `story_genres` (`story_id`, `genre_id`) VALUES');
    out.push(storyGenreRows.map((row) => `    ${row}`).join(',\n'));
    out.push(';');
    out.push('');
  }

  if (storyTagRows.length > 0) {
    out.push('INSERT INTO `story_tags` (`story_id`, `slug`, `label`, `created_at`) VALUES');
    out.push(storyTagRows.map((row) => `    ${row}`).join(',\n'));
    out.push('ON DUPLICATE KEY UPDATE `label` = VALUES(`label`);');
    out.push('');
  }

  out.push('INSERT INTO `chapters` (`id`, `story_id`, `chapter_number`, `title`, `slug`, `content`, `short_description`, `access_type`, `coin_price`, `status`, `published_at`, `created_by`, `created_at`, `updated_at`) VALUES');
  out.push(chapterRows.map(values).join(',\n'));
  out.push(';');
  out.push('');

  return out.join('\n');
}

function main() {
  const model = build();
  const content = render(model);
  fs.writeFileSync(OUTPUT_FILE, content, 'utf8');

  const legacy = path.join(SEED_DIR, 'V2__seed_data.sql');
  if (fs.existsSync(legacy)) {
    fs.rmSync(legacy);
    console.log('Removed stale versioned seed V2__seed_data.sql (replaced by R__local_seed_data.sql).');
  }

  console.log(`Wrote ${path.relative(PROJECT_ROOT, OUTPUT_FILE)} (${content.length} bytes)`);
  console.log(`  stories        : ${model.storyRows.length}`);
  console.log(`  chapters       : ${model.chapterRows.length} (${model.counts.paidChapters} paid)`);
  console.log(`  one-shot       : ${model.counts.oneshot}`);
  console.log(`  genres / teams : ${model.genres.size} / ${model.teams.length}`);
  console.log(`  story_genres   : ${model.storyGenreRows.length}`);
  console.log('\nRestart the backend with the "local" profile; Flyway re-applies the repeatable seed.');
}

main();
