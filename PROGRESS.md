# DriveDelta — Progress Log

> **This file is the single source of truth for "where are we?" across machines and sessions.**
> It lives in Git, so it travels between laptops. A Claude Code session's own memory does NOT
> travel — this file replaces it. Update it whenever a checkpoint or notable task completes,
> then commit + push. At the start of any session, read this file first.

**How to use:**
- At session start: read `CLAUDE.md` + this file → know the current state.
- When a task/checkpoint completes: tick it here, note anything worth remembering, commit + push.
- Rule: a checkpoint is not "done" until it's committed, pushed, AND marked done here.

---

## Current status

- **Active checkpoint:** Checkpoint 2 (Room DB & Sync Skeleton) — 🟡 code complete, needs device test
- **Last completed:** ✅ Checkpoint 1 (Skeleton, Theme & Auth) — verified on device.
- **CP2 written (not yet compiled/run here — no JDK on this box):** 6 Room entities + 6 DAOs +
  AppDatabase + DatabaseModule; 5 Firestore DTOs + FirestoreCodec + FirestoreDataSource +
  FirestoreModule; SyncManager + SyncWorker; Hilt WorkManager wiring (Application is now a
  `Configuration.Provider`, default WM initializer removed in manifest); Room schema export enabled.
  Cross-checked statically: DI provides all DAOs + Firestore; DTO↔entity fields align; `syncedAt`
  is never pushed. A temporary "Insert test place & sync" button sits on the Dashboard (remove in CP4).
- **Next up (you, in Android Studio):** Build → run → tap "Insert test place & sync" → confirm a doc
  appears at Firestore `/users/{uid}/places/`. Expect maybe a small first-compile fix. Then tick the
  CP2 boxes in CLAUDE.md and this file, and start Checkpoint 3 (Cars CRUD).
- **Last updated by:** (machine / 2026-07-11)
- **Working branch:** `main`

---

## Checkpoint status

| # | Checkpoint | Status | Where run | Notes |
|---|---|---|---|---|
| 0 | Design (Claude Design) | ✅ Done | You (design tools) | Committed + pushed `d199717` |
| 1 | Project Skeleton, Theme & Auth | ✅ Done | Local | Builds + auth verified on device |
| 2 | Room DB & Sync Skeleton | 🟡 Code complete | Local | Needs device test: place → Firestore |
| 3 | Cars Feature (CRUD) | ⬜ Not started | Web or Local | |
| 4 | Places Feature (CRUD) | ⬜ Not started | Local | Needs Maps/Places key |
| 5 | Background GPS Tracking Service | ⬜ Not started | Local | Needs device GPS |
| 6 | Live Tracking Screen | ⬜ Not started | Local | Needs device GPS |
| 7 | Roads API & Segment Building | ⬜ Not started | Local | Needs Roads API key |
| 8 | Trip Detail & Comparison | ⬜ Not started | Web or Local | |
| 9 | History, Fuel Log & Dashboard | ⬜ Not started | Local | |
| 10 | Hardening & Play Store Prep | ⬜ Not started | Local | |

Status legend: ⬜ Not started · 🟡 In progress · ✅ Done (committed + pushed)

---

## Decisions & deviations log

Record anything that differs from the plan, or decisions made mid-build that a future session
(or a different laptop) needs to know. Newest at top.

- `2026-07-10` — **Checkpoint 1 scaffold authored (not yet compiled).** Full Gradle setup (version
  catalog, wrapper 8.9, AGP 8.6.1, Kotlin 2.0.21, KSP), Hilt, dark Compose theme from `tokens.md`
  (Color/Type/Theme + `LocalDdTokens`/`LocalDdType`), Geist + Geist Mono vendored to `res/font/`,
  Firebase Google Sign-In (classic `GoogleSignInClient`), Auth + Dashboard screens, nav with
  auth-boundary back-stack clearing. **Dependency versions were bumped from the plan's mid-2024
  numbers to a coherent Kotlin-2.0.21 set** (e.g. Compose BOM 2024.09.03, Hilt 2.52, Firebase BOM
  33.4.0, nav 2.8.2) because the plan's originals have K2 rough edges and this box can't compile-test.
- `2026-07-10` — **Google Sign-In uses the classic `GoogleSignInClient`** (play-services-auth), not
  Credential Manager. It's deprecated but far lower-risk to wire correctly without a compiler, and it
  matches the plan's dependency. Revisit if targeting newer Identity APIs. Needs
  `R.string.default_web_client_id`, which the google-services plugin generates from
  `google-services.json` — so the app won't build until that file is added.
- `2026-07-10` — **Removed the `.debug` applicationIdSuffix.** The google-services plugin matches
  `google-services.json` package_name against the full applicationId; a suffix would fail the debug
  build unless `app.drivedelta.debug` were also registered in Firebase. POC uses one package.
- `2026-07-10` — **Mockup PNGs exported and render-verified.** 14 full-res PNGs (1176×2631) in
  `design/mockups/`: 6 core screens + 2 HUD delta states + 6 Ride Moments states. Renamed to
  kebab-case matching the `.html` basenames; the captioned trip-detail duplicate was dropped in
  favour of the clean crop. The renders confirm every extracted token (dark `#0A0B0D` canvas, apex
  logo, green/red/purple deltas) — the design app's own renderer resolves the `<sc-if>` branches
  that reading the HTML could not.
- `2026-07-10` — **Dark theme only for the POC.** All 8 screens are authored dark-first; light
  variants exist only for the HUD and pre-ride sheet. Build one dark `ColorScheme`, no
  `isSystemInDarkTheme()` branch. Light values stay recorded in `design/tokens.md` §2.2 for later.
- `2026-07-10` — **Fuel-badge colour collisions accepted.** Diesel `#5B8DEF` == `primary`, Electric
  `#37D67A` == `success`/`deltaFaster`. Binding consequence: fuel badges must always render their
  text label and never signal by colour alone.
- `2026-07-10` — **Implement from `design/tokens.md`, not the `.dc.html` mockups.** The mockups can't
  render offline (`support.js` needs React, which none of them load), and 6 of 8 are React-templated:
  the static markup holds every `<sc-if>` branch, and `hint-placeholder-val` is not the real default
  (`auth.html` hints `isClassic` while `data-props` says `"apex"`). Reading them yields wrong UI.
  Order: `tokens.md` → PNG → raw HTML only as a tiebreaker.
- `2026-07-10` — Design imported from Claude Design project `50aaa2d0-469b-4699-aba1-25ae18291f19`
  ("DriveDelta Live Tracking") via the `claude_design` MCP. Fonts are **Geist** + **Geist Mono**
  (both OFL), not yet vendored into `res/font/`.
- `2026-07-10` — Design includes a screen not in the plan: **Ride Moments**. Not a product screen —
  a reference board for the pre-ride / stop-confirm / arrival sheets, the acquiring-GPS state, and
  the 3 empty states. It covers F5, F6-A, F6-B and Checkpoint 10's cold-GPS requirement.

Example entries you might add later:
- `2026-07-15` — Swapped Vico for a custom Canvas chart on the compare screen; Vico beta had a
  rendering bug with horizontal bars. Compare screen no longer depends on Vico.
- `2026-07-14` — Chose `europe-west1` for Firestore region. Firestore rules deployed and verified
  in the Rules Playground.

---

## Known issues / TODO carryover

Things noticed but deferred — so they aren't lost between sessions.

- ✅ RESOLVED — CP1 now builds and runs in Android Studio. The one fix needed vs. the authored
  scaffold: `kotlin { jvmToolchain(17) }` demanded a JDK-17 toolchain that isn't installed →
  replaced with `compilerOptions { jvmTarget = JVM_17 }` (compiles on the JBR 21 that runs Gradle).
  Left the IDE nudges (Kotlin 2.3.10, AGP 8.7, Daemon toolchain) declined on purpose — version
  freeze during MVP; Kotlin/KSP/Compose move in lockstep.
- **WorkManager + Hilt not fully wired yet.** `hilt-work` is on the classpath but the Application
  does not yet implement `Configuration.Provider`; do that in Checkpoint 2 alongside `SyncWorker`
  (and remove the default `WorkManagerInitializer` in the manifest then).
- **Contrast below WCAG AA:** `#6B7178` on `#0A0B0D` is ~4.0:1 (AA needs 4.5:1). Used for dim
  captions and inactive bottom-nav labels at 10–11sp. Recommend lightening the nav labels to
  `#7E858F` (~5.2:1). See `design/tokens.md` §9.
- **No error state was designed for text fields.** Derive from `error #FF556A` (border + helper
  text), reusing the focused field's 1.5dp border / 4dp ring geometry.
- **Only the Electric fuel badge was drawn filled.** The other four colours come from
  segmented-control icon strokes; apply the same 12% bg / 30% border composite.
- **Geist + Geist Mono not yet vendored** into `res/font/` (Geist 400/500/600/700,
  Geist Mono 400/500/600).
- `design/mockups/support.js` (62 KB) is Claude Design's generated runtime. It is kept only so the
  `.dc.html` sources stay faithful to the export. It is not used by the app and never will be.

---

## Environment reminders (per machine)

Before building on a new laptop, confirm these are in place (they are NOT in Git):
- [ ] `google-services.json` in `app/` module directory
- [ ] `local.properties` with `MAPS_API_KEY`, `ROADS_API_KEY`, `PLACES_API_KEY`
- [ ] Debug keystore's SHA-1 registered in Firebase (each machine's debug keystore differs —
      either register every machine's SHA-1, or copy one `~/.android/debug.keystore` to all machines
      so they share the same SHA-1). **Recommended: copy the same debug.keystore to every machine**
      so Google Sign-In works everywhere without re-registering.
- [ ] Android Studio + JDK 17 + Claude Code installed and authenticated
