# DriveDelta — open design discussions

Topics deferred from the post-first-drive fix batch (CP11–CP18, see `CLAUDE.md`). Not decided, not
implemented. Each one starts from measured evidence rather than opinion so the conversation can be
about the choice rather than about the facts.

Measurements below come from the real `drivedelta.db` pulled off the test device on 2026-08-21,
after CP14 removed the 12× segment duplication. The reference drive throughout is
**`d8a0f4ce…` — Gestosa → Home, 9 Aug, 173.1 km, 1 h 29 m 50 s**.

---

## 1. Segment granularity

> **Resolved in CP20 + CP22.** Option **A + B**, as suggested below: merge consecutive runs of the
> same road name, then enforce a **250 m floor**, folding anything shorter into its longer neighbour.
> The reference drive went **853 → 80 segments** averaging **2.2 km**, and the distance under-count
> is gone — segments now sum to the trip exactly, because they tile the drive and take their distance
> from the raw trace rather than from the snapped path. Both related defects below are fixed too:
> `roadKey` is now road name + start/end Google feature ids, and durations are measured. See
> CHECKPOINT 20/22 in `CLAUDE.md`. **C (cut on junctions) remains the eventual target** if splits
> become the centrepiece feature.

### The problem, measured

| | value |
|---|---|
| Trip | 173.1 km · 1:29:50 |
| Segments produced | **853** |
| Mean segment | **146 m · 6.2 s** |
| Segments under 200 m | **8,640 of 10,236 pre-dedup rows** (~84%) |
| Distinct road *names* in the drive | **141** |
| Segments per named road | **~6** |

So a 90-minute motorway drive is cut into 853 pieces averaging six seconds each. On a motorway
that is meaningless: "you were 0.4 s slower on this 150 m of the A1" is not a fact anyone can act
on, and it drowns the handful of comparisons that would matter.

Worth noting the totals are also off, independently of granularity: the 853 segments sum to
**124.1 km against a 173.1 km trip (~28% under)**. That is RDP thinning plus snapping shortening
the path, plus one-point groups being dropped outright. Whatever we do about granularity should fix
this too, or the Splits tab keeps under-reporting distance.

### Why it happens

`BuildSegmentsUseCase.kt:49-59` groups **consecutive snapped points that share a Roads API
`placeId`**, and that is the whole rule. There is **no minimum length, no minimum duration, and no
merging of adjacent same-road segments** — the only filter is `coords.size >= 2` (`:57`), which
silently drops one-point groups rather than merging them into a neighbour.

The root cause is a modelling mismatch: a Google `placeId` identifies a *road feature*, not a road.
A motorway arrives from the API pre-chopped into dozens of features, so grouping by `placeId`
faithfully reproduces Google's internal segmentation rather than anything a driver would recognise.

The top offenders on the reference drive, by row count before dedup:

| Road | rows | km |
|---|---|---|
| Autoestrada do Norte | 1,692 | 488.2 |
| IC3 | 1,032 | 169.4 |
| IC8 | 912 | 86.2 |
| N236-1 | 840 | 49.4 |
| Autoestrada da Beira Interior | 708 | 98.4 |

### Options

**A. Merge consecutive runs of the same `roadName`.** Measured on the reference drive: **853 → 242
segments**. One line of grouping change. Cheap, big win, and it matches how a driver narrates a
drive ("the A1 stretch, then the IC19"). Downside: a 55 km motorway run becomes one segment, which
is too coarse to be an interesting split — you lose the ability to see *where* on the A1 you gained.

**B. Minimum length/duration with merge-into-neighbour.** Set a floor (say 500 m or 30 s) and fold
anything shorter into the adjacent segment rather than dropping it. Fixes the distance under-count
as a side effect, since nothing is discarded. Combines naturally with A.

**C. Cut on junctions rather than on `placeId`.** Closest to how people actually think about a
route, and gives sensible splits on both a motorway and a town centre. Much more work: needs
junction detection we don't have today, probably from bearing changes plus road-name changes.

**D. Cap segments per drive.** Target a count (say 15–30) and choose boundaries to hit it. Keeps
the Splits tab readable on any drive, at the cost of segments not being comparable across drives of
different lengths — which would undermine the whole personal-best premise.

**Suggested direction:** A + B together (merge by consecutive road name, then enforce a floor and
merge rather than drop), as a first pass that is cheap and measurable, with C as the eventual
target if splits become the centrepiece feature. Worth deciding what a "good" segment count for a
173 km drive actually is before writing any code — that number drives everything else.

### Two related defects to fix in the same pass

Both undercut the split-vs-best premise, so it isn't worth reworking granularity without them:

1. **`roadKey` is coordinate-sensitive.** It is built as
   `"$roadName|$startLat4dp,$startLng4dp|$endLat4dp,$endLng4dp"` (`BuildSegmentsUseCase.kt:137-143`).
   4 dp is ~11 m, so the *same* stretch of road driven twice with slightly different GPS produces a
   *different* key and never compares against itself. This is very likely why so much of the Splits
   tab shows "★ PB / new best": every segment is its own first sighting. Any granularity rework must
   also make the key stable across drives — probably name + a coarser grid, or the `placeId` run.

2. **Segment durations are not measured, they are distributed.** `:66-74` splits the trip's total
   time across segments *in proportion to distance*. So a segment's time is really its share of the
   drive's average speed — meaning a fast stretch and a slow stretch of equal length get identical
   times, and the split comparison is measuring almost nothing. This happens because RDP thinning
   plus API-interpolated points strip timestamps. Fixing it means carrying timestamps through the
   snap (match snapped points back to their nearest raw point by index) rather than distributing.

   Honestly, **this is the more serious of the two** — granularity is a readability problem, but
   distributed durations mean the headline numbers are not real measurements.

### Tests that pin current behaviour

Any rework has to keep these green, or consciously update them:
`BuildSegmentsUseCaseTest` (grouping + the fallback path), `CompareAndMatchSegmentsTest` (pins
`roadKey` as the cross-trip alignment key and `routeHash` as the trip-matching key), and
`RouteSummaryUseCaseTest`.

---

## 2. Global "roads I have driven" map

A world map shading every road the user has ever driven, so the grid of covered roads builds up over
time.

### What already exists

Every segment carries a `roadKey` and the drive carries a `routeHash`, so there is already a
per-road identity to aggregate on, and **segments are synced to Firestore** — so a cross-device
aggregate is reachable without new plumbing.

### The blocker

**Route points are local-only by design** (`CLAUDE.md` F14: never pushed to Firestore, too large).
So the actual drawn geometry only exists on the device that recorded the drive. A global map built
from route points would silently be "roads driven *on this phone*", and would empty out on a
reinstall — the emulator already demonstrates this: trips restored from Firestore have zero route
points, which is why their Trip Detail map and replay are blank.

### Options

**A. Aggregate at the segment level.** Segments sync, and each has start/end coordinates — so a
straight line per segment is drawable everywhere. Cheap, works cross-device today. Downside: the
map shows chords, not real road geometry, so it will look crude on winding roads. Gets much better
if segments get longer and more meaningful (topic 1) — or much worse.

**B. Upload compressed route points to Firebase Storage.** Already on the post-MVP backlog. Gives
true geometry and would also fix replay on a new device. Costs storage and a new sync path.

**C. Store the snapped `placeId` set per drive and render from a road-geometry source.** The most
"correct" answer — you'd be shading actual roads rather than drawn traces — but needs a geometry
source we don't have and probably per-road API lookups, which the cost rule in `CLAUDE.md` fights.

### Things to settle before building

- **Rendering cost.** Thousands of polylines on a Google Map will not perform. Realistically needs
  tiling, a simplified overlay, or a heatmap-style raster rather than individual polylines.
- **What "driven" means.** Once per direction? Does a 200 m clip of a road count as having driven
  it? This decides whether the map feels rewarding or noisy.
- **What the map is for.** Completionism (a coverage score, roads-in-region %) is a different
  product from a memory map (where have I been). Worth picking one before designing.

**Suggested direction:** option A first — it works cross-device with what already syncs, and it is
the cheapest way to find out whether the feature is actually compelling before paying for B.

---

## 3. Smaller gaps found while fixing CP11–CP18

Not discussed with the user yet; each is small and self-contained.

- **Fuel logs cannot be edited or deleted.** `FuelLogDao` only has `insertOrReplace`, and
  `FirestoreDataSource` has `deletePlace` but no `deleteFuelLog`. A mistyped fill-up is permanent
  and there is no way to correct it from inside the app. Found the hard way — see the stray test row
  flagged in `PROGRESS.md`.
- **Deleting a trip may not survive a sync.** `TripRepositoryImpl.deleteTrip` hard-deletes from Room
  only, with the comment "Remote tombstoning is deferred (single-user POC)" — so the next `pullAll`
  could restore it. Unverified, but worth checking before the Play Store release.
- **The map renders in Google's default light style** on every screen, while `design/tokens.md` §2.1
  specifies a dark map (`mapBase #0A0B0D`, `mapRoads #15171B`) and the HUD's glass panel is designed
  to sit over it. Fixing it is a map-style JSON plus one line at each of the three `GoogleMap` call
  sites. Left alone deliberately — a light map is arguably more legible in daylight, so it's a
  product call rather than a bug.
- **The remote `segments` collection still holds the pre-CP14 duplicate documents** under their old
  numeric ids. Harmless now (pulls collapse them onto the derived ids) but it makes every pull
  heavier. Clearing the collection once from the Firebase console would fix it; the next push
  rewrites a clean set.
