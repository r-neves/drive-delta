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

- **Active checkpoint:** MVP build complete (CP0–CP10 code done) + hardening + a **design-drift sweep**
  bringing each screen to its high-fi mockup. Swept + verified so far: Dashboard, Trips (Recent +
  By-route), Car-edit, Place-edit, Route Summary, the **Tracking HUD**, **Auth**, **Trip Detail**, the
  **ride-moments sheets** (StopConfirm/Arrival/PreRide), and **Fuel Log** — plus the Cars-screen title
  fix. **The design-drift sweep is complete** across every mockup screen. Everything below is committed
  **and pushed** to `origin/main`.
- **Last completed (this session, 2026-07-27):**
  1. **Fixed broken Firestore sync** — `pullAll` was never called (restore-on-sign-in/new-device was
     dead) and segments were never pushed. Wired `SyncTrigger.requestInitialSync()` (push-then-pull)
     on sign-in + cold-start; push+pull now include segments; `pullAll` reads `Source.SERVER`.
     **Verified**: empty-cache server pull restored trips/places/cars/fuel + a new trip's segments.
     (The "only cars+fuel in console" report was a Firestore-console scroll/nav thing — data was there.)
  2. **Closed CP9/CP10 code deferrals** — permission permanently-denied dialog (settings deep-link);
     Dashboard weekly summary + personal bests; History by-vehicle filter chips. All verified on emulator.
  3. **✅ Built the Route Summary screen** (the REQUIRED-BEFORE-PUBLISH task) — route-level A→B
     analytics aggregating all rides on a route by `routeHash`. New `RouteSummaryUseCase` (+ model),
     `RouteSummaryViewModel`/`Screen`, nav route `route_summary/{tripId}`, reached from the **Trip
     Detail app-bar Insights icon** and from **Dashboard personal-best cards**. Header + green "Ride
     saved" banner + total-time card (vs-best ▾/▴ delta + Distance/Avg speed/Energy cost) + FASTER/
     SLOWER/PURPLE tiles + custom-Canvas **speed-vs-cost scatter** (this-drive/fastest-purple/
     cheapest-green markers, dashed quadratic U-curve). Reuses the segment-comparison baselines and
     the linked-FuelLog cost. 3 unit tests (`RouteSummaryUseCaseTest`), share intent, en+pt strings.
     **Verified on emulator** via an injected 6-ride synthetic route: 3/2/1 tiles + full scatter from
     the Insights entry (red ▴ delta), and green ▾ delta from the Dashboard PB-card entry (fastest
     ride). Synthetic rows cleaned up afterward.
- **Next up:** the app is functionally complete + polished; **everything remaining before a Play
  Store release is captured in the "Pre-release checklist" below.** The device-free code backlog is
  fully cleared: ~~soft-delete tombstone pruning~~, ~~History place-pair + date-range filters~~,
  ~~pt translations (full en/pt parity)~~, ~~a11y contentDescription audit~~, ~~AA contrast~~,
  ~~trip-delete undo~~, ~~in-app language picker~~ — all done. Live-split HUD delta stays **deferred
  by design** (needs a live Roads call, barred by the cost rule).
- **Note:** route points are **local-only by design** (never synced) → Trip Detail map/replay only
  work on the recording device (post-MVP: Firebase Storage upload). Firestore security rules reviewed
  and correct (per-user, not test mode). Emulator (`Medium_Phone`) was `pm clear`'d during sync
  diagnosis, so it's freshly re-synced from server; old test trips lack route points (expected).
- **Last updated by:** (machine / 2026-07-28)
- **Working branch:** `main`

---

## Pre-release checklist (Play Store)

Everything left before an internal Play Store release. Grouped by who can do it. **None are code
features** — the app is functionally complete; these are release-ops, signing, backend config,
store compliance, and on-device verification. Items marked **[YOU]** need a human (console access,
a real device, or credentials); **[ME]** can be done from Claude Code.

### A. Signing & build config
- [ ] **[YOU/ME]** Create an upload keystore (`keytool`), store it + passwords **outside git** (e.g.
      `keystore.properties`, gitignored). I can wire the `signingConfigs { release }` in
      `app/build.gradle.kts` once the keystore + a `keystore.properties` exist.
- [ ] **[ME]** Verify a **minified release build** compiles + runs, then flip `isMinifyEnabled = true`
      (currently `false`; ProGuard/R8 rules already staged in `proguard-rules.pro`). Smoke-test the
      minified APK (Firebase/Retrofit/Room/Hilt/kotlinx-serialization/Maps reflection paths).
- [ ] **[ME]** Bump `versionCode`/`versionName` for the release (currently `1` / `1.0.0`).
- [ ] **[YOU/ME]** Build the signed **AAB** (`:app:bundleRelease`).

### B. Firebase / Google Cloud (production)
- [ ] **[YOU]** Register the **release keystore's SHA-1** (and SHA-256) in the Firebase project so
      Google Sign-In works on the Play-signed build. **Play App Signing** re-signs the AAB, so also add
      the **App-signing key SHA-1** from Play Console → Setup → App integrity.
- [ ] **[YOU]** Restrict the Maps/Roads/Places **API key** to the release package + SHA-1 (currently
      dev keys in `local.properties`). Confirm billing is enabled and quotas are sane.
- [x] Firestore security rules published to the **named** DB `drivedelta-firestore` (per-user, not
      test mode) — reviewed + correct.
- [ ] **[YOU]** **Firebase Crashlytics** — add the Crashlytics Gradle plugin + enable in console
      (deferred through CP10). Recommended before a public release. (I can wire the Gradle side.)

### C. Play Console — listing & compliance
- [ ] **[YOU]** Create the app in Play Console; complete the **store listing** (title, short + full
      description — have pt-PT + en ready, app is bilingual), **screenshots** (phone), **feature
      graphic**, **app icon**.
- [ ] **[YOU]** **Privacy Policy URL** (required — app collects location + account/email via Firebase).
- [ ] **[YOU]** **Data safety form** — declare: precise location, email/account, app activity; stored
      in Firestore per-user; note location is used in the foreground service for trip recording.
- [ ] **[YOU]** **Background/foreground location declaration** — Play requires a justification +
      demo video for `ACCESS_BACKGROUND_LOCATION` + `foregroundServiceType=location`. Prepare the
      "why background location" rationale (keeps recording while using nav apps).
- [ ] **[YOU]** Content rating questionnaire, target audience, ads declaration (no ads), news/gov = no.
- [ ] **[YOU]** Upload the AAB to the **internal testing** track; add testers.

### D. On-device verification (needs a real phone — [YOU], I can script/assist)
- [ ] **Real drive**: HUD/tracking live, foreground-service notification, **ArrivalSheet 30 s
      auto-finish** at a real geofence (the 5-fix arrival debounce isn't drivable on the emulator).
- [ ] **Populated Compare**: two genuinely identical drives → same `routeHash` → the Compare chart +
      table render with data (only ever seen empty-state on the emulator).
- [ ] **Trip-delete undo** live tap-and-hold (Compose `onLongClick` isn't triggerable via adb
      `input swipe`; build-verified only).
- [ ] **Sign-out → sign-back-in** full Firestore restore round-trip on a real account.
- [ ] Permission chain on a fresh install (fine → background → notifications → battery), incl. the
      permanently-denied → settings deep-link path (only the happy path was exercised on the emulator).

### E. Known constraints to accept (or fix) before shipping
- [ ] **Route points are local-only by design** → Trip Detail **Map/Replay only work on the recording
      device**; a fresh install / other device shows "No route recorded". OK for MVP; fix is the
      post-MVP Firebase Storage upload. **Decide** whether this is acceptable for a public listing.
- [ ] **Live-split HUD delta** stays greyed out (deferred by the Roads-API cost rule) — split-vs-best
      is delivered post-ride in Trip Detail. Acceptable, but note it in the listing if it looks unfinished.

---

## Checkpoint status

| # | Checkpoint | Status | Where run | Notes |
|---|---|---|---|---|
| 0 | Design (Claude Design) | ✅ Done | You (design tools) | Committed + pushed `d199717` |
| 1 | Project Skeleton, Theme & Auth | ✅ Done | Local | Builds + auth verified on device |
| 2 | Room DB & Sync Skeleton | ✅ Done | Local | Place verified in Firestore; named DB "drivedelta-firestore" |
| 3 | Cars Feature (CRUD) | 🟡 In progress | Local | Verified on emulator (found+fixed a stale-undo bug); Firestore console sync check still pending |
| 4 | Places Feature (CRUD) | ✅ Done | Local | Verified on emulator with keys (map, radius circle, autocomplete, save real geodata). Firestore console sync check pending |
| 5 | Background GPS Tracking Service | ✅ Done | Local | Verified on emulator via `adb emu geo fix` playback (30 pts/10 interp/max gap 6 s) |
| 6 | Live Tracking Screen | ✅ Done | Local | Verified on emulator (GEOFENCE auto-finish + MANUAL stop); arrival unit-tested |
| 7 | Roads API & Segment Building | ✅ Done | Local | Real Roads API verified (15 named Lisbon segments); 9 unit tests |
| 8 | Trip Detail & Comparison | ✅ Done | Local | Emulator: Map/Splits/Replay + fuel prompt; compare logic unit-tested (18 tests) |
| 9 | History, Fuel Log & Dashboard | ✅ Done | Local | Emulator: History + Fuel Log verified; sign-out clear + pt i18n; live-split/stats deferred |
| 10 | Hardening & Play Store Prep | 🟡 Code done | Local | Backoff/cold-GPS/ProGuard/18 tests done; Crashlytics + keystore/AAB are release-ops |

Status legend: ⬜ Not started · 🟡 In progress · ✅ Done (committed + pushed)

---

## Decisions & deviations log

Record anything that differs from the plan, or decisions made mid-build that a future session
(or a different laptop) needs to know. Newest at top.

- `2026-07-28` — **Backlog #3: History filters + pt translations + a11y audit (NOT committed — awaiting go-ahead).**
  Three self-contained tasks, all verified on the emulator; build + 20 unit tests green.
  (1) **History place-pair + date-range filters.** The Trips filter icon now opens a **Filters
  `ModalBottomSheet`** (replacing the vehicle-only `DropdownMenu`) using M3 **`FilterChip`** rows —
  the same filter-chip language used in TripDetail/PreRide — with three sections: **Vehicle**
  (All + cars), **Route** (All + auto-derived origin→destination pairs from the data), and **Date**
  (Any time · Last 7 days · Last 30 days · This month · **Custom** → M3 `DateRangePicker` dialog).
  `TripsOverviewBuilder.build` gained `selectedPair: RoutePair?` + `dateRange: LongRange?` params
  and a `pairOptions` output; **design filters (route/date/search) narrow the display but delta/PB
  history is still computed over the full vehicle-filtered set** (so a drive's vs-previous delta
  references the real previous drive even when it's outside the window). Place-pair also filters the
  By-route list; date-range narrows Recent only (route trend is inherently all-time). `HistoryViewModel`
  folds the 4 filter inputs into one `Filters` flow to stay within `combine`'s 5-arg limit; filter
  icon tints primary + shows an "N active" content-desc when any of vehicle/route/date is set.
  `TripsOverviewBuilderTest` +2 cases (place-pair narrowing/pairOptions; date-range keeps delta base).
  **Verified on emulator**: Route "SyncTest → Test Place" narrowed 11→1 drive; "Last 7 days" narrowed
  11→4; Custom picker selected 26–28 Jul and the chip showed the formatted "26 Jul – 28 Jul"; Clear all
  reset. (2) **pt-PT translations finished** — swept `values/strings.xml` vs `values-pt`, translated the
  36 missing keys (perm dialogs, trip-detail replay/compare, tracking chips/notification, dashboard
  week/greeting, places/cars content-descs, compare, preride chip, …) + the new filter strings, and
  removed one stale pt-only key (`dashboard_recent_rides`, superseded by `_title`). **Now full en/pt
  key parity, no duplicates.** (3) **a11y contentDescription audit** — walked every screen's
  `Icon`/`IconButton`/`Image`. Found the app already well-labeled (all IconButtons + the swipe-delete
  backgrounds + auth logo + recenter/share/stop already described). The one real gap: `RecentTripCard`'s
  **fuel-type icon** (bolt/pump on Trips + Dashboard cards) conveyed the fuel type by icon+colour with
  **no adjacent text** → added `contentDescription = stringResource(fuelType.labelRes)` (reusing the
  existing `FuelTypeUi.labelRes`). All other `contentDescription = null` are genuinely decorative
  (icons beside their own text label, empty-state art, text-field leading icons, nav icons with
  always-visible labels) and left null.

- `2026-07-28` — **Small-polish sweep (4 items; NOT committed).** Build + 20 tests green; full en/pt parity.
  (1) **WCAG AA contrast** — `DdTextDim` bumped `#6B7178` → `#787F89` (~4.0:1 → ~5:1 on `#0A0B0D`);
  used for small dim captions. (`DdTextTertiary #7E858F`, used for nav labels, was already AA.)
  (2) **Text-field error states** — **already implemented** (stale TODO): `CarTextField` wires
  `isError` + `supportingText`, `CarEditViewModel` flips `nameError` on blank save, PlaceEdit name
  field the same. No change needed; verified in code.
  (3) **Undo for trip delete** — the History (Recent) long-press delete now shows a **"Ride deleted /
  Undo" snackbar** (matches the cars pattern). `TripRepository.restoreTrip(trip, segments, routePoints)`
  re-inserts all three from a snapshot captured before delete (`HistoryViewModel.deleteTrip` snapshots
  via getTrip/getSegments/getRoutePoints, emits `undoSignal`; `undoDelete()` restores). New
  `SnackbarHost` on the Trips screen. New string `history_deleted` (en/pt). **Trip Detail delete stays
  confirm-only** (navigating back, no snackbar surface). **Build-verified; not driven live** — Compose
  `onLongClick` isn't triggerable via adb `input swipe` (same gesture limitation noted elsewhere), but
  the restore reuses the exact mappers used by verified append/finish/start paths.
  (4) **In-app language picker** — the gear (Settings, reached from the Dashboard header) screen was
  retitled **"Settings"** and gained a top **LANGUAGE** section with a **Language** dropdown (System
  default / English / Português) using the framework **`LocaleManager.applicationLocales`** (API 33+;
  guarded — below 33 users change the device language, no AppCompat backport added). Existing
  energy/currency content kept below a new **CURRENCY** section label. New strings `settings_section_language`,
  `settings_language`, `settings_language_system`, `energy_section_prices` (en/pt). **Verified live on
  emulator (API 37)**: picking Português recreated the activity and relocalized the whole app
  ("Definições / IDIOMA / Português / MOEDA / ELETRICIDADE"); switched back to System default after.

- `2026-07-28` — **Two usability fixes: per-app language + discoverable trip delete (NOT committed).**
  (1) **OS per-app language picker enabled.** There was no in-app language switch AND no `localeConfig`,
  so the Android 13+ per-app Language screen (Settings → Apps → DriveDelta → Language) never appeared —
  the only way to get Portuguese was a device-wide language change. Fix: `android { androidResources {
  generateLocaleConfig = true } }` + `resourceConfigurations += listOf("en", "pt")` in
  `app/build.gradle.kts`, plus a required **`app/src/main/res/resources.properties`** with
  `unqualifiedResLocale=en-US` (build fails without it: "No resources.properties file found"). AGP now
  generates `_generated_res_locale_config.xml` (en-US, pt) and wires `android:localeConfig`.
  **Verified on emulator**: `cmd locale set-app-locales app.drivedelta --locales pt` was accepted and the
  whole app rendered in pt ("Boa tarde", "Iniciar viagem", "Custo combustível") without touching the
  device locale; reset back to system after. (No in-app dropdown — the OS picker is the surface.)
  (2) **Trip delete made discoverable.** Deleting a ride was long-press-only on Trips→Recent (nothing in
  Trip Detail). Added a red **"Delete ride"** item (trash icon) to the Trip Detail app-bar ⋮ `OverflowMenu`
  → confirm `AlertDialog` (reuses `history_delete_title/_message`) → `TripDetailViewModel.deleteTrip`
  (delegates to the same `tripRepository.deleteTrip` as History) → `onBack`. New string `trip_menu_delete`
  (en "Delete ride" / pt "Eliminar viagem"). **Verified on emulator**: ⋮ shows Route insights / Compare /
  Delete ride (red); tapping Delete → "Delete ride?" confirm dialog; Cancelled (didn't destroy the real
  restored trip). Full en/pt string parity maintained; build + 20 tests green.

- `2026-07-27` — **NEW FEATURE: Energy Logging (per-drive fuel/energy cost) + Energy Prices settings.**
  Built the "record energy used per drive" flow from 4 new mockups (`design/mockups/Energy Logging-*.png`,
  `settings-energy-prices.png`). **Data:** new `EnergyPricesEntity`/`EnergyPricesDao` (one row per user)
  + **Room v1→v2 migration** (`AppDatabase.MIGRATION_1_2`, wired in `DatabaseModule`; schema `2.json`
  exported; migration verified on the emulator's existing v1 DB — no crash). Synced like other entities:
  `EnergyPricesDto`, `FirestoreDataSource.pushEnergyPrices`/pull under `/users/{uid}/settings/energy_prices`,
  `RemoteSnapshot.energyPrices`, `SyncManager` push/pull (`SyncManagerTest` updated for the new DAO).
  Domain `EnergyPrices` (+ `ElectricTariff`) with the **user-specified defaults: petrol €2.00/L, diesel
  €1.96/L, LPG €0.96/L, electricity home €0.155781/kWh + public €0.79/kWh, currency EUR** (default tariff for
  new drives = Public); `EnergyPricesRepository` emits `default(userId)` until the user saves.
  **UI:** (1) **`ui/settings/EnergyPricesScreen`** (+VM) — currency dropdown, Home/Public electric tariffs
  (radio picks default-for-new-drives), Petrol/Diesel/LPG rows (tap → price dialog), Estimate/Ask toggles;
  auto-saves each edit. Reached via a **new gear icon on the Dashboard header** (`ENERGY_PRICES` outer-nav
  route; `onOpenSettings` threaded MainScreen→Dashboard). (2) **`ui/fuel/EnergyLogSheet`** (+`EnergyLogViewModel`,
  a `ModalBottomSheet`) **replaced the old full-screen `FuelLogScreen`/`FuelLogViewModel`** (deleted, along
  with the `FUEL_LOG_ROUTE` nav): kWh/litres adapt to the car, unit price from settings (electric tariff
  switchable per drive by tapping the rate row; liquid rate row opens Energy Prices), **live cost = amount ×
  price**, "Use estimate" from the car's avg consumption. Content is `verticalScroll` so Save is reachable.
  (3) **Trip Detail** gained a **Fuel cost header stat** (logged cost or "—"), a dashed **"Fuel not logged →
  Add"** banner, and a **4th "Cost" tab** with the speed-vs-cost scatter (incl. a "NO COST YET" pending
  marker for the focused unlogged drive). The sheet **auto-opens once per unlogged drive** when `askAfterEveryDrive`
  is on (gated by the existing `fuelPromptDismissed` trip flag); the "Add" banner opens it manually.
  New use case `GetTripCostChartUseCase` (this drive's cost + scatter, gap-filling other unlogged drives
  with estimates when `estimateWhenNotLogged` is on). (4) **Extracted a shared `ui/components/SpeedCostScatter`**
  (+`ScatterPoint`/`ScatterKind`) used by BOTH Trip Detail and Route Summary (RouteSummary refactored off its
  private chart — one implementation). (5) **Currency now follows the setting** on the Dashboard weekly card
  and Route Summary (both VMs read `EnergyPricesRepository`), not just the locale — fixes the "$72.00 vs €"
  mismatch. en+pt strings added. **Verified on emulator** (installed debug, migrated existing DB): Energy
  Prices screen matches the mockup with the exact default prices; opening an **unlogged** trip auto-opens the
  "Fuel used" sheet (VW Golf/Petrol, €2.00/L from settings, 5.2 L → **€10.40** live), shows the not-logged
  banner + "—" stat; a **logged** trip (linked €72 log) shows "Fuel cost €72.00", no banner, and a Cost-tab
  scatter with a green **THIS DRIVE** marker at (5 km/h, €72). Not visually captured: the literal Save-tap→
  banner-clears transition (blind adb tapping got flaky) — but the logged-trip state proves a saved log renders,
  and the save path reuses the verified `LogFuelUseCase`. **NOT committed/pushed** (awaiting user go-ahead).

- `2026-07-27` — **Design sweep FINAL: Fuel Log aligned to the Car-edit form language + Cars-screen
  title fixed.** The **Fuel Log** (F12) has no dedicated mockup, so it was rebuilt to match the form
  language established in `car-edit.png`: **uppercase field labels** (`LabeledField`) over **filled
  surface inputs** (`DdSurface` container, `DdOutline` border, `DdPrimary` focus, `radiusInput`) with
  **unit suffixes** (`L`, `kWh`, `km`), an **app-bar Save** action + a bold bottom **"Save fill-up"**
  primary button, and the post-save result moved into a **bordered summary card** (green efficiency
  text). The car selector + adaptive fuel/electric fields + auto-calc total were preserved. Also fixed
  the leftover **Cars-screen title "Vehicles" → "Cars"** (`cars_title`, en+pt) to match the bottom-nav
  label + mockups. New en+pt strings (`fuel_unit_l/_kwh/_km`, `fuel_save`). **Verified on emulator**:
  the Cars screen title now reads "Cars"; opening a trip's fuel prompt → Add fill-up → the redesigned
  Fuel Log renders (uppercase labels, filled fields with L/km suffixes, Save/Save-fill-up), and
  entering 40 L × 1.80 auto-calculated 72.00 and showed the bordered saved-summary card. **The
  design-drift sweep is now complete** across all mockup screens.
- `2026-07-27` — **Design sweep cont'd: Ride-moments sheets rebuilt to match the `ride-moments-*` mockups.**
  (1) **StopConfirmSheet** (`ride-moments-stop-confirm.png`) — added a **red stop badge** (circle with
  a red-tinted fill/border + red rounded-square glyph), a **save-to-history subtitle**, and wrapped the
  stats in a **bordered card with vertical dividers** showing `Elapsed / Distance / Avg` with small unit
  suffixes (km, km/h); Finish Ride stays red, Keep Going outlined. (2) **ArrivalSheet**
  (`ride-moments-auto-finish.png`) — replaced the plain countdown text with a **green countdown ring**
  (Canvas arc, "30"/"SEC" centre) + a **depleting green progress bar**, an "Auto-finishing this ride…"
  subtitle, and a **green** Finish Ride button (dark text). (3) **PreRideSheet** (`ride-moments-pre-ride.png`)
  — targeted polish: added the **ⓘ "Set a destination to auto-finish…" hint** line and a **▶ play icon**
  on the bold Start Ride button (the dropdown selectors were kept functional rather than rebuilt into
  the mockup's rich vehicle/place cards — a fuller pass is possible later). New en+pt strings
  (`tracking_stop_subtitle`, `_arrived_subtitle`, `_countdown_unit`, `_stat_elapsed`, `_stat_avg`,
  `preride_hint`). **Verified on emulator**: started a ride → the polished pre-ride sheet (hint + play
  button) → HUD → **STOP → the redesigned StopConfirmSheet** (red badge, subtitle, bordered
  Elapsed/Distance/Avg card, red/outlined buttons) rendered pixel-close to the mockup; Finish Ride
  completed cleanly. **ArrivalSheet not captured on-emulator** — the geofence 5-consecutive-fix arrival
  debounce is unreliable under `adb emu geo fix` (established CP6 limitation: fixes arrive irregularly
  and interpolated points don't count; held at the destination for minutes at "0.0 km left" without
  firing). It compiles, uses the same `ModalBottomSheet` path StopConfirm just rendered, and its logic
  is unit-tested (`DetectArrivalUseCaseTest`). Two dangling in-progress trips (one a resurrected
  HUD-test trip, one from force-stopping mid-finish) were finished + cleaned in Room afterward.
  The acquiring-GPS state (`ride-moments-acquiring-gps.png`) was already covered by the HUD sweep
  (SEARCHING amber pill + `--` placeholders).
- `2026-07-27` — **Design sweep cont'd: Trip Detail rebuilt to match `trip-detail.png`.** The screen
  was restructured to the mockup's shape: (1) **app bar** now shows the dynamic **"Origin → Destination"**
  route title (via the shared `routeTitle` helper; falls back to "Ride" when a trip has no linked
  places) with a **"Today · 18:24 · Car"** subtitle (`tripSubtitle` — relative day + `HH:mm` + car
  name), a **circular** back button, and the two actions (Route insights + Compare) folded into a
  single **⋮ overflow `DropdownMenu`** (matches the mockup's lone ⋮). (2) A **persistent 4-stat summary
  header** (Duration / km / avg km/h / **vs best** with a green ▾ or red ▴ delta) now sits between the
  app bar and the tabs, replacing the old in-Splits summary. (3) **Splits is the default tab** (was
  Map) with a blue underline + bold active label. (4) The **splits table** was redesigned: a
  `SEGMENT / TIME / Δ VS BEST` **column header**, rows with **no leading index**, road name + `X.X km`
  sub-line, a larger mono **TIME**, and a **`Δ VS BEST`** column showing `▾0.8`/`▴2.4` (seconds, one
  decimal) + a `best m:ss.d` caption; a **personal-best segment** now renders as a **purple band**
  (`DdPurpleRowBg` + border, purple text) with **`★ PB` / `new best`** instead of a delta. Hairline
  dividers between rows. The vs-best/vs-previous **filter chips are kept** (functional, per F10) though
  the mockup doesn't show them — placed compactly above the header. **Data:** `TripDetailViewModel`
  now injects `PlaceRepository` + `CarRepository` and resolves `originName`/`destName`/`carName` in
  init (added to the ui-state) — no change to `TripDetail`/`GetTripDetailUseCase`. **Verified on
  emulator**: a VW Golf drive opened to the redesigned Splits tab (header stats `0:47 / 0.3 / 23 /
  ▾0:00`, two purple `★ PB` segment bands with real Lisbon road names), the Map tab shows "No route
  recorded" (route points are local-only + these are Firestore-restored trips), and the ⋮ menu opens
  with Route insights + Compare. Tab switch + underline confirmed.
- `2026-07-27` — **Design sweep cont'd: Auth screen rebuilt to match `auth.png`.** Swapped the
  placeholder `Δ` glyph for a proper **apex mark** — a new `ic_apex_logo.xml` vector drawing two
  overlapping triangle strokes (brand blue `#5B8DEF` + success green `#37D67A`) inside the rounded
  surface tile. Layout re-anchored to the mockup: the brand block (logo/wordmark/tagline) sits in the
  upper-middle via weights, and the **"Continue with Google" button is pinned near the bottom** with a
  new `ic_google_g.xml` (official 4-colour Google G) + a **Terms/Privacy caption** below it (blue link
  spans on `DdSecondary`). Wordmark bumped to 38sp bold; button 64dp with a hairline border. Button
  copy changed from "Sign in with Google" → **"Continue with Google"** (+pt "Continuar com Google").
  **Gotcha fixed:** Android trims leading/trailing whitespace in string resources, so the caption
  words ran together ("ourTermsandPrivacy") — moved the spaces into the `buildAnnotatedString` and kept
  the string pieces whitespace-free. New strings `auth_terms_prefix/_terms/_terms_conj/_privacy/
  _logo_desc` (en+pt). **Verified on emulator** end-to-end: signed out → Auth screen renders per the
  mockup (apex tile, wordmark, tagline, bottom Google button, correctly-spaced caption) → "Continue
  with Google" → silent SSO → back to Dashboard; the sign-out cleared the local cache and the
  **initial Firestore pull restored all 9 trips / 3 cars / 3 places** (route_points were already 0, so
  nothing local-only was lost; a full DB backup was taken beforehand as a safety net regardless).
- `2026-07-27` — **Design sweep cont'd: Tracking HUD rebuilt to match `tracking-hud-{ahead,behind}.png`.**
  The signature screen was restructured from a top-anchored HUD + separate centered STOP button to the
  mockup's **bottom glass panel**. `HudOverlay` (`ui/tracking/components/`) is now: a **header row**
  with a pulsing **RECORDING** dot (amber **SEARCHING** while acquiring GPS) on the left + the current
  road name (uppercased, right-aligned, "—" placeholder) on the right; a **two-column main row** —
  the big speed readout + `KM/H` (left) beside the segment time, `AHEAD/BEHIND BEST` status label and
  a coloured **seconds delta** (`▾ −2.4s` / `▴ +1.9s`) (right); a divider; and a **footer row** with
  `ELAPSED`/`DISTANCE` stat blocks and the **STOP button moved inside the panel** (bordered red pill +
  square glyph per tokens.md §7). The segment status/best/delta only render once `bestSegmentMs` is
  known — live splits stay **deferred by design** (so the right column shows just the segment time in
  the running app; split-vs-best is delivered post-ride in Trip Detail). `TrackingScreen` moved the
  HUD to `BottomCenter` (12dp side margins), and the top overlay is now a mockup-style **"◆ X.X km
  left" destination chip** (left) + a **recenter button** (right, `MyLocation`, animates camera to the
  latest follow target). New en+pt strings (`tracking_recording`, `_searching`, `_unit_kmh`,
  `_label_elapsed/_distance`, `_ahead/_behind/_new_best`, `_best_caption`, `_km_left`, `_recenter`).
  `HudOverlay` gained an `onStop` param. **Verified on emulator** via a real Start Ride → live GPS
  track: SEARCHING/acquiring state (`--` km/h, `--:--`, amber dot) then RECORDING state (red dot,
  road "—", speed, segment `0:00.0`, `ELAPSED`/`DISTANCE`, in-panel STOP, recenter button) confirmed
  by screenshot + uiautomator node dump. The throwaway test trip was deleted from Room afterward.
  Notes: the pre-ride & stop-confirm `ModalBottomSheet`s are flaky to open on this emulator (need a
  clean single tap + a couple of tries); emulator `geo fix` carries no speed so the HUD speed reads 0.
  Remaining un-swept: **Auth, Trip Detail, ride-moments sheets, Fuel Log** (+ Cars-screen title still
  "Vehicles").
- `2026-07-27` — **Design sweep cont'd: Car-edit + Place-edit rebuilt to match their mockups.**
  **Car-edit** (`car-edit.png`): a live **preview card** (car tile + name + plate + fuel badge), uppercase
  section labels over filled fields, a custom **colored-icon fuel-type selector** (Petrol/Diesel/Hybrid/
  Electric/LPG, tinted by `badgeColor`), side-by-side Battery|Tank + Consumption fields with unit
  suffixes + an electric-only helper, a **default-toggle card** with subtitle, an app-bar **Save** +
  bottom **Save changes** button; titles now "Edit car"/"Add car". **Place-edit** (`place-edit.png`):
  restructured to a **map-hero** layout — the address **search field moved into the top bar** (next to
  back), the map fills the top ~300dp with the geofence circle + draggable marker, a **"Use my
  location" pill** overlaid on it, and a bottom panel (rounded top) with uppercase PLACE NAME / ICON /
  GEOFENCE RADIUS labels, **rounded-square emoji tiles** (blue selection), the radius slider and a
  **Save place** button. Both verified on the emulator (Place-edit with live map tiles). Remaining
  un-swept screens: **Auth, Trip Detail, Fuel Log, Tracking HUD** (+ the ride-moments sheets) — a
  further sweep pass would bring those to parity too. The Cars/Places *list* screens have no dedicated
  mockup; they already follow the card language (Cars-screen title still reads "Vehicles").
- `2026-07-27` — **Dashboard redesigned to match `dashboard.png`; shared `RecentTripCard`; nav label
  "Vehicles" → "Cars".** The old Dashboard (plain "Dashboard" title, Start-Ride FAB, simple text
  trip rows) was rebuilt: a date + time-of-day greeting header with an avatar (initial of
  `AuthRepository.currentUserName`, newly added — Firebase displayName or email local-part), a blue
  **Start Ride hero card**, a THIS WEEK totals card (locale currency, `h:mm` drive time), a **Recent
  drives** section (rich cards + "See all" → Trips tab) and **Personal bests** (→ Route Summary).
  The rich trip card was extracted to a shared `ui/components/RecentTripCard.kt` (+ `routeTitle`/
  `formatClock`) and both Trips-Recent and the Dashboard now use it — the Dashboard passes a
  **vs-best** delta (this drive − best of the route's *other* drives, so a record shows green ▾),
  Trips passes vs-previous. Bottom-nav tab renamed **Vehicles → Cars** (`nav_cars`) to match the
  mockup; the Cars *screen* title still reads "Vehicles" (left as-is — out of the pointed-at scope).
  Verified on emulator (injected multi-route data): greeting/avatar, hero card, weekly card, and
  green/red "vs best" recent cards all render per the mockup. **Design-drift sweep note:** only the
  Dashboard was rebuilt this pass (highest-value, clearly-drifted landing screen). Auth / Car-edit /
  Place-edit / Cars / Places / Fuel-log screens were NOT re-checked against their mockups — a
  follow-up sweep is recommended if full design parity is wanted.
- `2026-07-27` — **Trips screen redesigned to match `trips-recent.png` + `trips-by-route.png`.**
  The old History screen (month-grouped plain list + vehicle chips) was replaced by a two-tab
  **Trips** screen. Files: `ui/history/TripsOverview.kt` (pure builder + data classes, unit-tested),
  reworked `HistoryViewModel` (streams trips+cars+places → `TripsOverviewBuilder`), rewritten
  `HistoryScreen`. **Recent tab:** title + search + filter icons, a DRIVES/DISTANCE/NEW PBS stat
  card, day-grouped ("Today"/"Yesterday"/date) rich cards — time · fuel-type icon+car name (or a
  NEW PB pill), `Origin → Destination`, `duration · distance`, and a delta-vs-**previous-drive**
  (green ▾ / red ▴) or "★ best" for a PB (purple-highlighted card). **By-route tab:** caption +
  one card per route (grouped by `routeHash`) with best time, BEST/NEW PB label, a custom-Canvas
  **sparkline** of the last ≤12 drive times (faster = **down**), and a coloured trend label
  (green "trending faster" / purple "best ever" / red "trending slower"); tapping a route card
  opens the Route Summary. Semantics: "NEW PB"/"best ever" ⇔ the newest drive set the record;
  "faster/slower" trend compares the newest drive to the window start. Search filters by O/D/car
  text; the vehicle filter moved behind the filter icon (DropdownMenu). Nav tab renamed
  **History → Trips** with a route icon (`Icons.Outlined.Route`); the `history` route id + file
  names are unchanged (least churn). en + pt strings; `TripsOverviewBuilderTest` (4 tests).
  **Verified on emulator** (injected multi-route synthetic data): both tabs render per the mockups,
  incl. the NEW-PB purple card, fuel icons, and green/purple/red sparklines; synthetic data removed
  after. Note: the bottom-nav "Vehicles"/"Cars" label mismatch with the mockup was left as-is
  (out of the requested scope; the Cars screen/title still says "Vehicles").
- `2026-07-27` — **Route Summary screen — design decisions.** (1) **Keyed on `tripId`** (the drive
  in focus), not routeHash/place-pair — the mockup centres on one drive ("this drive"), and the
  group of comparable rides is derived internally by `routeHash` equality (blank hash → the drive
  stands alone). (2) **Totals use `Trip.durationMs`** (wall-clock, always present) for cross-ride
  comparison, not segment sums (some rides have no segments). `bestTotal` = min duration of the
  **other** rides (the standing record) so a genuine PB shows a green ▾ delta; if the drive is alone,
  delta is 0. (3) **Per-segment tiles are a strict partition** of this ride's segments: *purple* if
  it beats the all-time record for that road (new PB, using `bestSegmentDuration`-style min over
  OTHER rides), else *faster*/*slower* vs the **previous run**'s time on that road (same "previous
  run" baseline the Trip Detail splits already compute). So faster+slower+purple == segmentsTimed,
  and `newPersonalBests == purpleCount`. (4) **Scatter** = only rides with a linked FuelLog cost AND
  a computable avg speed; markers: this-drive (green, priority), fastest-by-time (purple ring),
  cheapest-by-cost (green ring); trend = least-squares **quadratic** drawn only when it's an upward
  U (`a > 0`) with ≥3 points. (5) **Entry points:** Trip Detail app-bar Insights icon (primary,
  matches mockup) + Dashboard PB cards (→ the route's fastest ride). (6) Emulator GPS can't produce
  a multi-ride same-`routeHash` route on demand, so the full screen was **verified by injecting a
  synthetic 6-ride route** into Room (push modified DB via `run-as`, per the CP5 note), then removed.
- `2026-07-27` — **Sync bug hunt (user report: "only cars + fuel_logs in Firestore").** Diagnosed on
  emulator by wiping local (sign-out) and observing what a pull restores. Findings + fixes:
  (1) **`SyncManager.pullAll()` was NEVER called** anywhere — restore-on-sign-in / new-device was
  fully broken (nothing ever came back). FIX: added `SyncWorker` pull mode (`KEY_PULL` input) +
  `SyncTrigger.requestInitialSync()` (own unique name `drivedelta_initial_sync`, KEEP, so the frequent
  write-triggered push can't REPLACE it); wired it on **sign-in** (`AuthViewModel`) and **cold start
  while signed in** (`DriveDeltaApplication.onCreate`). Verified: cold start restored 6 trips / 3 cars
  / 3 places / 1 fuel from the server. (2) **Segments were never pushed** (no `syncedAt`, absent from
  the push loop) AND **`pullAll` never inserted segments** either. FIX: push each pending trip's
  segments alongside the trip; pull inserts them grouped-by-trip (delete-then-insert only for trips
  the server returns segments for, so local-only pending segments survive a pull — pulled `SegmentDto`
  carries id=0 so Room re-autogenerates). (3) **trips + places WERE on the server all along** — the
  user's console view just didn't expand the `trips`/`places`/`segments` subcollections under
  `/users/{uid}/`; the pull proved they're there. **Order matters:** `SyncWorker` now pushes BEFORE it
  pulls so local pending isn't clobbered. **Caveat:** `route_points` remain local-only by design (plan)
  — they never sync, so Trip Detail map/replay only work on the recording device until the post-MVP
  Firebase Storage upload lands. (The diagnostic sign-out wiped this device's old test route points;
  restored here from a DB backup.)

- `2026-07-21` — **CP7 (Roads API & segments) built + verified with the LIVE Roads API.** Notes:
  (1) The `ROADS_API_KEY` in local.properties has **Roads API enabled + billing** — confirmed by a
  live `200` from `snapToRoads` on the emulator (my Lisbon test coords are on real streets). (2)
  **Road names come from the platform `Geocoder`** (`GeocoderRoadNameResolver`, no Geocoding API key),
  because `snapToRoads` returns only `placeId` — same key-free approach as CP4. `nearestRoads` not
  implemented (unneeded). (3) **Segment timing is distance-proportional**, NOT per-point: RDP thinning
  (ε 10 m, needed for Roads cost control) plus interpolated snapped points strip timestamps, so a
  short segment between two thinned points would collapse to 0 ms if timed from snapped points. Fix:
  distribute the trip's total time across segments by each segment's share of the snapped distance
  (positive, monotone, sums to total) — verified 15/15 segments non-zero. Consequence: all segments
  in a trip share the trip's avg speed (intra-trip speed variation not captured). **Post-MVP:** finer
  timing by projecting boundaries onto the raw trace. (4) `roadKey`/hash use `Locale.US` formatting so
  a comma-decimal locale (pt-PT!) can't corrupt the `lat,lng` key. (5) Snap+build run in the service
  **stop coroutine** (`runCatching`), not a separate use case from `StopTripUseCase` — the service
  owns post-ride processing. Roads/Room calls are main-safe so no explicit IO dispatch. (6) Emulator
  reusable-GPS caveat still applies (see CP6 note): feed real road coords for meaningful snapping.
- `2026-07-21` — **CP6 (Live Tracking) built + verified.** Notes for future sessions: (1) The
  screen binds the running service via a `ServiceConnection` in `TrackingViewModel`
  (`BIND_AUTO_CREATE`, unbind in `onCleared`) and mirrors `service.trackingState`; it accumulates a
  live polyline from each distinct `currentLocation` and a **camera target throttled to 3 s** so the
  map doesn't jitter. `tripEnded` (wasTracking && !isTracking) drives navigation back to the
  Dashboard. (2) Nav: added outer-graph `TRACKING` route (covers the bottom bar, like the editors);
  `MainScreen`/`DashboardScreen` gained an `onStartTracking` callback. (3) Pre-ride is its own
  `PreRideViewModel` + `PreRideSheet` (hiltViewModel-scoped to the sheet); it streams cars/places,
  suggests a nearby origin from `lastLocation()`, and calls `StartTripUseCase` then emits the new
  trip id to trigger navigation. Removed the temporary CP5 Dashboard test harness (Start Ride FAB
  replaces it). (4) Added `destinationName` + `distanceToDestinationMeters` to `TrackingState`
  (service fills them) for the HUD destination chip + ArrivalSheet title. (5) "I'm just passing" is a
  **local UI dismiss** flag reset when arrivalStatus returns to EN_ROUTE (leaving the radius), not a
  service-state change. (6) **Emulator can't reliably drive the 5-consecutive-fix arrival debounce**:
  `adb emu geo fix` reaches the fused provider only every ~3–28 s (irregular), so most points get
  interpolated and real inside-fixes are too sparse. It DID fire once (GEOFENCE auto-finish observed),
  but the reliable coverage is `DetectArrivalUseCaseTest` (mocks static `Location.distanceBetween`).
  Also: the emulator's `System.currentTimeMillis()` jumped after snapshot-restore/host-sleep, so
  observed elapsed/duration was bogus (9 h) — a clock artifact, not a code bug.
- `2026-07-20` — **CP5 verified via scripted emulator GPS (reusable for CP6/CP7).** No device GPS
  needed: boot the `Medium_Phone` AVD, `pm grant` the location/notification perms + `dumpsys deviceidle
  whitelist +app.drivedelta` so the CP5 permission chain short-circuits, then drive a moving track
  with a bash loop of `adb emu geo fix <lon> <lat>` (step ≥ ~10 m each tick to beat the 5 m
  `minUpdateDistance`; pause > 8 s to trigger gap interpolation). The emulator has **no `sqlite3`
  binary** — pull the DB to the host instead: `adb exec-out run-as app.drivedelta cat
  …/databases/drivedelta.db{,-wal,-shm}` then query with host sqlite (Room is WAL, so grab the -wal).
  Gotcha hit this run: a **blind center `input tap` hit the Dashboard "Sign out" button** → signed
  out. Re-sign-in worked because the emulator has a Google account (silent SSO); still, always tap by
  reading a fresh `uiautomator dump`'s node bounds (Compose collapses the tree, so match the *text*
  node's `bounds`, not the root).
- `2026-07-20` — **CP5 authored: GPS tracking foreground service + start/stop use cases.** Key
  decisions: (1) The **service owns all live recording state** (point buffer, running distance,
  elapsed, arrival status) and exposes `StateFlow<TrackingState>` via a bound `TrackingBinder` for
  CP6 to collect. (2) **`StartTripUseCase`/`StopTripUseCase` are thin** — Start mints the trip UUID,
  snapshots `lastLocation()` as start (0,0 if none; the service **backfills start coords on its first
  accepted fix**), persists the `TripEntity`, and calls `TrackingForegroundService.start(...)`; Stop
  just delivers a STOP intent and the service runs the whole finalise sequence (flush → stamp end
  fields → `finishTrip` → `requestSync`). Both use cases inject `@ApplicationContext` to start/stop
  the service (same pragmatic Context-in-use-case call as `SyncTrigger`). (3) **GPS-gap fill**: on a
  fix > 8 s late, interpolate between the last valid and new fix at ~2 s spacing (`isInterpolated=
  true`); if the gap > 30 s, drop a single interpolated midpoint marker instead. Uses `GeoUtils`
  `interpolate` + `bearingDegrees` (added this CP). Warm-up drops the first 10 s; accuracy filter
  drops `>25 m`. (4) **Timing**: monotonic `elapsedRealtime` for warm-up/gap logic, wall-clock epoch
  for stored point timestamps + duration. (5) **Route points are local-only** — `appendRoutePoints`
  never calls `requestSync` (Firestore stores trips/segments only, per plan). (6) **Post-ride Roads
  snap/segment build is a TODO hook** in `stopTracking` — lands in CP7. (7) Foreground start uses
  `ServiceCompat.startForeground(..., FOREGROUND_SERVICE_TYPE_LOCATION)` called first thing in the
  START branch to dodge the start-timeout ANR; `START_NOT_STICKY` (mid-trip process death out of
  scope). (8) **Permission chain** is a Compose helper `rememberStartTrackingPermissionFlow`
  (`ui/permissions/`), not imperative `MainActivity` code — the idiomatic Compose place given
  MainActivity is a pure `setContent` host. Sequence: fine → background (API 29+) → notifications
  (API 33+) → battery-optimisation exemption → start. Permanently-denied rationale + settings
  deep-link deferred to CP10. (9) A temporary **Start/Stop test-trip harness on the Dashboard**
  exercises the service pre-CP6; it polls the trip until `endTime` is stamped then shows a
  points/interpolated/km summary. Removed when the real Start Ride flow lands in CP6.
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

- ✅ **RESOLVED (2026-07-27) — Route Summary screen built + verified.** Route-level A→B analytics
  aggregating all rides on a route by `routeHash`. Files: `domain/model/RouteSummary.kt`
  (+ `RouteDrivePoint`), `domain/usecase/segment/RouteSummaryUseCase.kt`,
  `ui/routesummary/RouteSummary{ViewModel,Screen}.kt`; nav route `route_summary/{tripId}` wired in
  `AppNavGraph`/`NavDestinations`. Entry points: Trip Detail app-bar **Insights** icon and clickable
  **Dashboard personal-best cards** (`PersonalBest.bestTripId` = the route's fastest ride). Layout
  matches `design/mockups/trip-summary.png`: header + green "Ride saved" banner + total-time card
  (vs-best ▾/▴ delta + Distance/Avg speed/Energy cost) + FASTER/SLOWER/PURPLE tiles + custom-Canvas
  speed-vs-cost scatter (this-drive green + fastest purple-ring + cheapest green-ring + dashed
  quadratic **U-curve**). Share via `ACTION_SEND`. Currency symbol is locale-derived. 3 unit tests
  (`RouteSummaryUseCaseTest`); en + pt strings. **Design semantics chosen** (see decisions log):
  `bestTotal`/`deltaVsBest` compare THIS ride against the best of the OTHER rides (the standing
  record) so a genuine PB reads green ▾; per-segment classification is a clean partition — *purple*
  when this ride beats the all-time segment record (new PB), else *faster*/*slower* vs the previous
  run. Scatter dots are only rides with a linked FuelLog cost + computable avg speed.
  **Also done:** `trip-summary.png` added to the CLAUDE.md design-asset list (14 → 15 PNGs).
- ✅ **RESOLVED (2026-07-27) — soft-delete tombstone pruning.** `SyncManager.pushPending()` now, after
  pushing a pending car, checks `isDeleted`: a tombstone is pushed (so other devices pull it and hide
  the car) and then **hard-deleted from Room** via `CarDao.hardDelete(id)`, instead of being
  re-inserted with `syncedAt`. So hidden `isDeleted=1` rows no longer accumulate locally. The
  Firestore tombstone doc is intentionally kept (it's how other devices learn of the deletion; a
  single-user serverless app can't know when all devices have synced, so server-side pruning stays
  out of scope). Only cars have soft-delete tombstones — places use a hard delete (no `isDeleted`
  column), so nothing to prune there. Covered by `SyncManagerTest` (2 tests: tombstone → push +
  hardDelete, no re-insert; live car → push + `syncedAt` stamp, no delete).
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
