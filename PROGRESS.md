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

- **Active checkpoint:** Checkpoint 0 (Design) — not started
- **Last completed:** none yet
- **Next up:** Complete design in Pencil/Claude Design, fill in Design System tokens in CLAUDE.md
- **Last updated by:** (machine / date)
- **Working branch:** (e.g. `main` or `checkpoint-N`)

---

## Checkpoint status

| # | Checkpoint | Status | Where run | Notes |
|---|---|---|---|---|
| 0 | Design (Pencil / Claude Design) | ⬜ Not started | You (design tools) | Fill tokens + export 6 mockups |
| 1 | Project Skeleton, Theme & Auth | ⬜ Not started | Local | Needs google-services.json |
| 2 | Room DB & Sync Skeleton | ⬜ Not started | Web or Local | |
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

- _(none yet)_

Example entries you might add later:
- `2026-07-15` — Swapped Vico for a custom Canvas chart on the compare screen; Vico beta had a
  rendering bug with horizontal bars. Compare screen no longer depends on Vico.
- `2026-07-14` — Chose `europe-west1` for Firestore region. Firestore rules deployed and verified
  in the Rules Playground.

---

## Known issues / TODO carryover

Things noticed but deferred — so they aren't lost between sessions.

- _(none yet)_

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
