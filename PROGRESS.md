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

- **Active checkpoint:** Checkpoint 5 (Background GPS tracking service) — not started.
- **Last completed:** ✅ Checkpoint 4 (Places CRUD) — verified on emulator WITH API keys: map render,
  live radius Circle, Places autocomplete + select (marker/camera/address), Save persists real
  geodata. Also verified crash-safe with no keys. Firestore console sync check still pending.
- **Next up:** Checkpoint 5 — `LocationProvider` (FusedLocation callbackFlow), extend `GeoUtils`
  (bearing + interpolation), `TrackingForegroundService` (F4), `DetectArrivalUseCase`, Start/Stop
  trip use cases, `TripRepository`, and the sequential runtime permission flow in `MainActivity`.
- **Note:** the two leftover "Test Place / Debug insert" rows (from the removed CP2 sync-test button)
  plus a test "Rossio" place created during CP4 verification are in Room/Firestore — deletable via
  the Places UI. Harmless.
- **Last updated by:** (machine / 2026-07-20)
- **Working branch:** `main`

---

## Checkpoint status

| # | Checkpoint | Status | Where run | Notes |
|---|---|---|---|---|
| 0 | Design (Claude Design) | ✅ Done | You (design tools) | Committed + pushed `d199717` |
| 1 | Project Skeleton, Theme & Auth | ✅ Done | Local | Builds + auth verified on device |
| 2 | Room DB & Sync Skeleton | ✅ Done | Local | Place verified in Firestore; named DB "drivedelta-firestore" |
| 3 | Cars Feature (CRUD) | 🟡 In progress | Local | Verified on emulator (found+fixed a stale-undo bug); Firestore console sync check still pending |
| 4 | Places Feature (CRUD) | ✅ Done | Local | Verified on emulator with keys (map, radius circle, autocomplete, save real geodata). Firestore console sync check pending |
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

- `2026-07-20` — **Added on-demand sync (`SyncTrigger`) — writes now reach Firestore in seconds,
  not up to 15 min.** Diagnosing "my edit isn't in Firestore" revealed the app only synced via the
  15-min periodic worker (no push-on-save), so fresh writes sat unsynced. Fix: `core/sync/SyncTrigger`
  enqueues a unique one-time `SyncWorker` (`ExistingWorkPolicy.REPLACE`, `NetworkType.CONNECTED`);
  `CarRepositoryImpl.saveCar/deleteCar` and `PlaceRepositoryImpl.savePlace` call `requestSync()` after
  the local write. NOT `setExpedited` — expedited on API < 31 (minSdk 26) requires a foreground
  notification via `getForegroundInfo`, unwanted per edit; a plain one-time request runs promptly
  while foreground. Place hard-delete doesn't call it (no pending row; `firestore.deletePlace` handles
  remote directly). Periodic worker stays as the offline/missed backstop. Verified on emulator: a
  radius edit synced in ~10 s (was ~150 s). Future repos (trips, fuel logs) should call `requestSync()` too.
- `2026-07-20` — **CP4 reverse-geocoding uses the platform `android.location.Geocoder`, not the
  Geocoding API + Retrofit** the plan named. Rationale: the Geocoder needs no extra API key or key
  wiring, keeps CP4 to just Maps + Places keys, and is enough for filling the address label on marker
  drag / use-my-location. Autocomplete result selection fills the address directly (no geocode). The
  Retrofit Geocoding path can be revisited if the platform geocoder proves unreliable in the field.
- `2026-07-20` — **Places delete is a hard delete (no soft-delete/undo), per F3.** `deletePlace`
  removes the Room row (source of truth) then best-effort deletes the Firestore doc
  (`FirestoreDataSource.deletePlace`); offline the local delete still sticks and the doc lingers. A
  swipe stages the delete and an AlertDialog confirms it — unlike cars (swipe = immediate soft-delete
  + undo snackbar). PlaceEntity has no `isDeleted` column, so the deferred tombstone-pruning TODO
  does not apply to places; instead the open risk is a deleted place resurrecting on a fresh-device
  pull if the offline Firestore delete never ran. Acceptable for the single-user POC.
- `2026-07-20` — **App must not crash when Maps/Places keys are absent (verified).** `PlaceEditScreen`
  guards the `PlacesClient` on `Places.isInitialized()` (search field disables with a helper note),
  and `GoogleMap` renders a blank Google tile without a `MAPS_API_KEY` rather than crashing. Emulator
  run confirmed the editor opens and the full non-map CRUD works with no key present.
- `2026-07-20` — **Bug found + fixed via emulator run: undo-delete restored stale car data.**
  `SwipeToDismissBox`'s `confirmValueChange` lambda is captured once at the item's first composition
  and NOT refreshed on recomposition, so it closed over the original `Car`. Editing a car (e.g.
  toggling default) then swiping it passed the pre-edit object to `deleteCar`, and undo re-saved the
  stale version — the restored car lost its default flag. Fix: `CarsViewModel.deleteCar(carId)` now
  reads the undo snapshot fresh from `cars.value` by id instead of trusting the object the composable
  passes. Re-verified on emulator: default survives swipe+undo. (The delete itself was always fine —
  it only used the immutable id.)
- `2026-07-20` — **CP3 acceptance test run on an emulator (`Medium_Phone`), not just compiled.**
  The debug build carried a persisted Firebase session from CP1/CP2, so the app opened straight to
  Dashboard — no interactive Google sign-in needed — which let the whole Cars CRUD flow be driven via
  adb. All UI states verified from screenshots (empty state, add Petrol + Electric with conditional
  fields, fuel-badge colours, default chip + single-default enforcement, swipe-delete + undo).
- `2026-07-20` — **CP3 nav is two-level.** Outer graph = `AUTH` / `MAIN` / `car_edit?carId={carId}`;
  `MAIN` is a `MainScreen` shell holding a bottom `NavigationBar` over its own inner NavHost of tab
  routes (`dashboard`, `cars`). Full-screen editors (car edit) live in the OUTER graph so they cover
  the bar. `MainScreen`'s Scaffold uses `contentWindowInsets = WindowInsets(0)` so the inner NavHost
  is padded only by the bar height and each tab owns its top inset (no double inset). Start
  destination moved from `DASHBOARD` to `MAIN`.
- `2026-07-20` — **Car userId is stamped by the repository, not the UI.** `CarRepositoryImpl.saveCar`
  overwrites `Car.userId` with `authRepository.currentUserId` (ignoring whatever the ViewModel set to
  `""`), so per-user isolation lives in one place. Signed-out reads emit empty; writes are no-ops.
  "Only one default" is enforced by `CarDao.clearDefaultExcept` after an upsert with `isDefault`.
- `2026-07-20` — **Swipe-delete = optimistic soft-delete + undo restore.** Swiping calls
  `softDelete` (isDeleted=1, syncedAt=NULL) → the `getByUser` Flow drops it instantly; the snackbar's
  Undo re-saves the captured `Car` (re-insert un-deletes). Single-slot undo (newest wins) — fine for POC.
- `2026-07-11` — **Firestore uses a NAMED database, `drivedelta-firestore`, not `(default)`.**
  `FirestoreModule` binds `FirebaseFirestore.getInstance("drivedelta-firestore")`; the KTX
  `Firebase.firestore` accessor targets `(default)` and threw `NOT_FOUND` until this was fixed.
  Security rules are per-database — publish the `request.auth.uid == userId` rules to
  `drivedelta-firestore` specifically, not `(default)`. CP2 sync verified end-to-end after this.
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

- **Soft-delete tombstones are never pruned (deferred from CP3).** Swipe-delete does
  `CarDao.softDelete` (isDeleted=true, syncedAt=NULL) and the worker pushes the `isDeleted:true`
  doc to Firestore — this is correct and intended: the tombstone propagates the deletion to other
  devices (pull re-inserts it into Room, `getByUser` filters `isDeleted=0` so it stays hidden). What
  the plan's F2 flow also calls for but CP3 does NOT yet implement is the final step — **hard-delete
  the Room row once the deletion has synced.** Consequence today: deleted cars linger forever as
  hidden `isDeleted=true` rows in both Room and Firestore (verified: Tesla M3 sits in Firestore with
  isDeleted=true). Harmless for the POC but unbounded growth. Do this with the sync-completion logic
  when the sync layer (thin since CP2) is fleshed out — a `hardDeleteSyncedTombstones()` pass after a
  successful push, applied to cars/places (and any other soft-deleted entity). Not a CP3 bolt-on.
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
