# Agent Progress

Last updated: 2026-07-30
Status: Paused by user request

## Completed

- Local commands run the reader app, admin app, and backend independently.
- Client navigation is standardized to Home, Stories, Categories, Rankings,
  Audio, and Publishing Teams.
- Story detail uses a responsive 60/40 chapter and recommendation layout.
- Story comments fill the complete primary column.
- Ranking page has a centered title and the revenue board is named `Thánh Bảng`.
- Audio uses speaker icons and has a browser speech player with playback,
  chapter selection, speed controls, and automatic next-chapter playback.
- `/teams` uses the shared public site header and navigation.
- Development seed data is inserted idempotently into MySQL.
- The original demo story `Bếp Lửa Cuối Ngõ` has 9 connected chapters; the live
  API reports 774-828 words per chapter.
- Reader app typecheck, lint, production build, and route smoke checks pass.
- Backend compilation passes and the local API responds on port 8080.
- Admin sidebar is persistent across dashboard routes and only shows concise
  tab labels without descriptions.
- Admin routes now replace only the right content area and show an immediate
  loading skeleton while data is loading.
- Admin API requests have a 4-second timeout instead of hanging indefinitely.
- Admin typecheck, lint, and production build pass.
- Admin development server responds on port 3001; all dashboard routes returned
  HTTP 200 during warm-up checks.

## Paused Work

- Continue profiling admin API and server-render latency. Warm route timings
  still vary because pages use uncached database requests.
- Decide a short cache and post-CRUD invalidation strategy so navigation stays
  fast without displaying stale administration data.

## Next Session Checklist

1. Fix admin navigation latency.
   - Profile each backend admin endpoint and identify slow database queries.
   - Add a short server cache for list/overview reads.
   - Invalidate affected cache entries after every successful CRUD mutation.
   - Keep the persistent sidebar and immediate route loading state.
   - Target repeated route responses below 500 ms locally.
2. Complete admin CRUD verification.
   - Test create, update, and delete flows for stories, categories, teams, users,
     and cash flow against seeded database records.
   - Verify right-side drawer forms, validation, errors, loading, and refresh.
   - Verify team approval and rejection workflow.
3. Finish backend verification.
   - Run the complete backend test suite.
   - Review indexes and query plans for admin list and dashboard endpoints.
   - Confirm authorization for every admin mutation and reader-only route.
4. Finish reader workflow checks.
   - Test login and registration, team registration, wallet top-up, donate,
     chapter unlock, bookshelf save, ad booking, and revenue split end to end.
   - Confirm every displayed counter is loaded from the database.
5. Finish browser-level UI verification.
   - Check desktop, tablet, and mobile layouts for horizontal overflow.
   - Verify all navbar routes, story detail, reader, rankings, audio, teams,
     profile dropdown, and admin dashboard.
   - Test Vietnamese speech playback and automatic next chapter in Chrome.
6. Final quality gate.
   - Run reader/admin production builds, frontend lint and typecheck, backend
     tests, and route/API smoke checks.
   - Update this progress file with actual results and remaining blockers.

## Verification Remaining

- Run the complete backend test suite after the seed-content update.
- Confirm Vietnamese speech voice output interactively in Chrome; voice quality
  depends on voices installed by the operating system/browser.
- Re-run admin navigation timing after the cache/invalidation change.

## Local Runtime

- Reader: `http://127.0.0.1:3000`
- Admin: `http://127.0.0.1:3001`
- Backend API: `http://127.0.0.1:8080/api/v1`
- Full demo story: `http://127.0.0.1:3000/truyen/bep-lua-cuoi-ngon`
- Audio demo: `http://127.0.0.1:3000/audio/bep-lua-cuoi-ngon`
