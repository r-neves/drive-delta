# DriveDelta — "roads I have driven" coverage map

> **Status: investigation and proposal. No feature code was written.** The only code produced was a
> throwaway measurement harness (`app/src/debug/.../MapStressActivity.kt`), used to get the
> rendering numbers in §10 and deleted afterwards — see the appendix for how to recreate it.
>
> This supersedes `DISCUSSION.md` §2, which is left in place as the record of the earlier thinking.
> Three of its assumptions turn out to be wrong when measured; §1 says which.
>
> Everything below is anchored on numbers I measured on 2026-08-23 against the real account
> database and the real reference drive, or on figures with a cited source. Estimates are labelled
> as estimates. The appendix says how to reproduce each one.

**Decisions taken as given** (from the brief, not relitigated here): a *coverage* map, not a trace
map; optimised for Portugal but must not break elsewhere; **memory first**, light completion
second, with a score that must never imply 100% is reachable; offline nice-to-have; discovery and
sharing explicitly out of scope.

---

## 1. Three measurements that change the shape of the problem

### 1.1 `roadKey` does not identify a road. It identifies a drive.

The brief calls the existing `roadKey` "a stable per-road identity … enough to record WHICH roads
were driven". Measured against the real account, it is not.

| | |
|---|---|
| Segments in the account | **210** |
| Distinct `roadKey` values | **209** |
| Distinct `roadName` values | 143 |
| Road names appearing on more than one trip | **18 of 143** |

**One** `roadKey` in the entire account is shared by two segments. Everything else is its own first
sighting. A driven-roads set keyed on `roadKey` would today report "209 roads driven" for 285.7 km
of segments — which is just the segment count with the serial numbers filed off.

The clearest case is Circular Regional Exterior de Lisboa (CREL), which the user has driven twice —
northbound on trip `574706bd`, southbound on `5fbdb38e`:

```
574706bd  idx  9   802 m   38.7146,-9.2662 → 38.7176,-9.2696
574706bd  idx 19  2043 m   38.7625,-9.2698 → 38.7763,-9.2509
...  9 segments,  32.2 km of CREL
5fbdb38e  idx  4  5803 m   38.9031,-9.0568 → 38.8962,-9.1165
5fbdb38e  idx  6  2233 m   38.8940,-9.1215 → 38.8767,-9.1329
...  9 segments,  same road,  zero shared keys
```

Same tarmac, 18 segments, 18 distinct keys, no overlap at all. Two reasons, both structural:

1. **`roadKey` is `roadName|startPlaceId|endPlaceId`** (CP20). The start/end place ids are wherever
   *this* drive's segment boundaries happened to land, which depends on which `placeId` run the
   Roads API returned for this drive's GPS. Boundaries move between drives, so keys move with them.
   The segment lengths above make it plain: 802 m / 2,043 m one way, 5,803 m / 2,233 m the other.
2. **Opposite carriageways are different Google features anyway**, so a road driven in reverse can
   never share a key even if the boundaries were stable.

CP20 fixed a *worse* version of this (coordinates at 11 m precision) and made keys stable enough to
compare a segment against *itself on the same route*, via `routeHash`. That is a different and
easier problem than "is this stretch of tarmac one I have been on before", which is what a coverage
map needs.

> **Consequence.** Do not build the driven set on `roadKey`. Whatever identity a coverage map uses
> has to come from either geometry (§4, Proposal A) or an external enumerable network (§5 and §6,
> Proposals B and C). This is the single most important finding here, because the brief's stated
> crux assumed the opposite.

### 1.2 Route points are not too large to sync. They are stored in a wasteful shape.

`CLAUDE.md` F14 says route points are never pushed to Firestore because they are "too large", and
`DISCUSSION.md` §2 calls this "the blocker" — the thing that would make the map silently mean
"roads driven on this phone". Measured, the premise does not hold. Same geometry, same drive
(`d8a0f4ce`, Gestosa → Home, 173.1 km, 5,159 raw fixes), four encodings:

| Representation | Bytes | Per km | vs Room rows |
|---|---:|---:|---:|
| Room `route_points` rows (today) | **507,300** | 2,930 | 1× |
| Encoded polyline, full fidelity, 1e5 precision | **16,663** | 96 | **30× smaller** |
| …gzipped | 7,151 | 41 | 71× |
| Encoded polyline, RDP ε = 10 m (511 points) | **2,100** | 12 | **242× smaller** |
| …gzipped | 1,532 | 9 | 331× |

The whole account — all 17 trips, 9,791 route points, 318.8 km — is about **30 KB** as encoded
polylines. Firestore's document limit is 1 MiB. At full fidelity that is one 10,900 km drive per
document; at ε = 10 m it is 87,000 km per document. There is no volume problem at any realistic
scale; there was an encoding problem.

Measured Room cost, for contrast: **98.3 bytes per `route_points` row** (from `dbstat`), against
312 bytes per `segments` row. At a plausible 15,000 km/year and the measured 29.8 fixes/km, raw
route points grow by **~450,000 rows ≈ 44 MB per year** on the device. *That* is the number worth
worrying about, and it is a local-storage problem, not a sync one.

> **Consequence.** The blocker dissolves. A per-trip encoded polyline is small enough to sync,
> which makes the coverage map account-wide rather than phone-wide, survives a reinstall, and as a
> side effect fixes the long-standing "Trip Detail map and replay are blank on a restored device"
> gap and retires the *"Route point sync to Firebase Storage"* backlog item (Storage is not needed —
> it fits in Firestore).

### 1.3 You do not have to render the denominator.

`DISCUSSION.md` §2 lists rendering cost as the thing to settle, and it is real (§10: a Google Map
dies at ~6,500 `Polyline` objects). But the framing assumed the map must draw the road network with
driven roads highlighted against it.

It does not. **Google Maps already draws every road.** A coverage map only has to draw *your*
geometry on top of the basemap that is already there. The denominator is needed for the *number*
("112 of 340 roads in Lisboa"), not for the *picture*. That removes the hardest rendering problem
from every proposal below, and it is why Proposal A needs no reference network at all.

---

## 2. Prior art: drivingroads.io

Studied on 2026-08-23 by using the site: home page, `/map`, `/terms`, `/robots.txt`, and the
network traffic the map itself makes.

**What it treats as a "road".** A curated, scored, discrete *route* — not every way in a network.
Its own figures: **"2.4M+ road segments analyzed"** ingested, **38 countries mapped**, and the live
map's filter panel reports a working set of **201,988 roads** (143,225 of them once the default
"min length 2 km" filter applies). So roughly **2.4 M analysed segments distil to 202 K product
roads** — a ~12:1 curation ratio, and about 5,300 roads per country. Each carries a length, a
hairpin count, an elevation profile, a "Thrill Score", and a class (Mountain Pass / Valley Dip /
Climb-Descent / Ridge-Flat).

**Where the geometry comes from.** Not stated in the terms, but the map is Mapbox GL JS on the
`mapbox/dark-v11` style and carries **"© Mapbox © OpenStreetMap"** attribution; the pipeline
description says they "pulled millions of road geometries" and paired them with **NASA SRTM
3-arc-second** elevation. That is an OSM + SRTM derivation. Their Terms §10 claims IP only over "the
software, design, branding, the Driving Roads name, and our original rating methodology and Thrill
Score" — i.e. over the *scores*, which is consistent with the underlying geometry being OSM.

**How it scores.** Purely geometric, and it says so as a positioning line: *"No reviews. No forum
posts. No opinions."* Sinuosity, hairpins detected by angular-change threshold, 3D elevation
profile, then classification into experience types. Nothing crowd-sourced, nothing subjective.

**How it renders at scale.** Not by handing 202 K geometries to the client. The map is a WebGL
vector renderer (Mapbox GL JS) over a **Supabase/PostGIS** backend: the only data call the page
makes is `POST https://auth.drivingroads.io/rest/v1/rpc/get_filter_bounds` — a PostgREST RPC. The
client sends the viewport and the filter state, the server answers with counts and the geometry for
that viewport. At continent zoom the panel reads "0 of 0 roads" and nothing is drawn; the on-screen
hint is literally *"Zoom in and tap any glowing road"*. **Zoom-gated, viewport-bounded,
server-side spatial query, GPU vector rendering.** That is the architecture, and it is the same
answer §10 arrives at independently.

**The logbook — the part that matters most for us.** "Mark roads as driven" is **one manual tap**,
with an optional date, note and rating. It is not derived from a GPS trace at all. Progress is
reported as **counts and collections, never as a percentage of all roads**:

> 47 roads driven · 1,284 km logged · 9 regions · **23 of the top 100 European roads**

plus badges for "regions covered, named routes completed, and the rare, iconic roads ticked off one
by one". Free tier can mark roads driven; Pro ($10/mo) plots them on a personal map and unlocks the
badges. The marketing line for the feature is *"Watch a map of the world slowly fill in beneath
your wheels."*

> This is exactly the shape the brief asks for, arrived at by someone else: a **curated
> denominator** and a **collection-based score**. Nobody can drive all the roads, so they never
> offer a number that says you should. "23 of the top 100" is a target a person can plausibly move.

**API / export / terms — this is a hard no.** There is no public API. `robots.txt` allows crawling
the site but `Disallow: /api/`. The Terms §8 forbid, in order: scraping, harvesting, bulk
downloading, systematic extraction, automated access, redistribution, commercial exploitation, and
reverse-engineering the rating methodology.

> **Take the product model. Do not take a single byte of their data.** Their curated set is their
> business. If DriveDelta wants a curated denominator it has to build or licence its own — which is
> feasible, because the *inputs* (OSM geometry, SRTM elevation) are open and the scoring is
> geometric maths anyone can implement.

---

## 3. The three things `DISCUSSION.md` §2 said to settle — now answered

**"Rendering cost."** Answered in §10 with measured numbers. Short version: the wall is `Polyline`
*object count*, not vertex count — ~6,500 objects on a 192 MB heap, regardless of how few points
each holds. Within a budget of ≤1,000 objects and ≤150 K vertices, a Google Map is fine, and that
budget holds years of driving. Beyond it, tiles.

**"What 'driven' means."** Proposed, for decision:

- **Direction-agnostic.** Driving the A1 south does not leave the northbound carriageway grey. The
  opposite is technically purer and emotionally wrong for a memory map. (drivingroads.io is also
  direction-agnostic: one tap marks the road.)
- **A road counts once you have covered ≥ 50% of its length, or ≥ 2 km of it, whichever comes
  first.** The second clause is what stops a 40 km road being un-tickable on a normal commute.
- **A contiguous run of at least 250 m counts as coverage**, reusing `MIN_SEGMENT_METERS` from
  CP22 — below that you are measuring where a GPS fix landed, not where the car went. Clipping the
  corner of a roundabout should not tick a road.
- These thresholds only exist in the proposals that have a denominator (B and C). Proposal A has
  no notion of "a road" at all — geometry is either drawn or not.

**"What the map is for."** Settled by the brief: memory first. §8 turns that into a score design.

---

## 4. Proposal A — trace coverage (recommended first)

**The map is the union of every road you have actually been on, drawn over the normal map.** No
reference network, no denominator, no OSM, no new API spend, no licensing. Purely a memory map.

### Where the geometry comes from — it already exists and is being thrown away

`SnapRouteToRoadsUseCase` already sends every completed drive to the Roads API with
`interpolate=true`, and gets back a dense, road-centreline path: measured on the reference drive,
**4,864 snapped points at 18.8 m median spacing** (p90 66 m). That is not a GPS trace, it *is* road
geometry — Google has already done the map-matching that Proposal B would otherwise have to do. Today it is
used to cut segments and then discarded.

Persisting it costs, measured on the reference drive: **540 points / 2,232 bytes at ε = 10 m**.

### Storage

**Room — new table, migration v3 → v4** (`AppDatabase` is at version 3 with `MIGRATION_1_2` and
`MIGRATION_2_3`; this follows the same pattern):

```
trip_traces
  tripId      TEXT PRIMARY KEY   -- FK to trips.id
  encoded     TEXT               -- Google encoded polyline, precision 1e5, RDP ε = 10 m
  pointCount  INTEGER
  source      TEXT               -- "SNAPPED" | "RAW"  (RAW when the Roads API failed)
  builtAt     INTEGER
  syncedAt    INTEGER NULL
```

One row per trip, ~2 KB. The whole account today would be ~35 KB. A decade of heavy driving is
single-digit MB.

**Firestore — `/users/{uid}/traces/{tripId}`**, one document per trip, `{ encoded, pointCount,
source, builtAt }`.

Why this document model and not the alternatives the brief asks about:

- **One doc per road** — rejected, and this is the one to be careful about. It is not the write
  *rate* that kills it (Firestore's ~1 write/s limit is per document, and these would be different
  documents); it is that a long drive would fan out into hundreds of documents, `pullAll` already
  fetches every segment document on every cold start, and the CP14 duplication bug and the CP23
  stale-document bug were both caused by many small remote documents whose identity had to be
  derived correctly. Fewer, larger, write-once documents are the lesson from those two checkpoints.
- **One doc for everything** — rejected: it grows without bound toward the 1 MiB cap, and every
  finished drive is a read-modify-write of the whole thing, which is the one access pattern the
  per-document write limit actually punishes.
- **Geohash/tile-keyed documents** — the obvious middle, and *unnecessary here*. Tile-keying exists
  to make spatial queries and merges cheap. A trace document is **written once and never updated**,
  so there is nothing to merge remotely: the aggregate is a pure function of the traces and can be
  rebuilt locally in milliseconds. Tile keys would buy a partial-region pull we do not need at
  30 KB/account. Revisit only if a user's trace set passes ~5 MB, i.e. tens of thousands of drives.
- **Embedding the polyline in the existing trip document** — tempting, but `pullAll` fetches all
  trips on every cold start, so it would put the whole geometry on the cold-start path. Keep it in
  its own collection and pull it lazily, the first time the coverage map is opened.

**Deletion.** A deleted trip must delete its trace document too. This is the exact bug shape of
CP23 (trips) and CP14/CP23 (segments) — `FirestoreDataSource.deleteTrip` already deletes a trip's
segments; it needs one more line, or the trace comes back on the next pull.

### Rendering

Decode the traces, draw **one `Polyline` per trip** over the dark map style added in CP24. Measured
against the §10 budget:

| | measured / derived |
|---|---|
| Points per km of trace at ε = 10 m | **3.0** (511 pts / 173.1 km, motorway-heavy) — plan on 10 for urban |
| Objects | one per trip |
| 1,000 trips averaging 50 km (50,000 km driven) | ~150 K–500 K vertices in **1,000 objects** |
| Measured cost of 1,000 objects × 500 points | **+154.9 MB heap, 55.8 fps** — survives, but at the edge |
| Measured cost of 1,000 objects × 100 points | **+50.9 MB, 37.7 fps** — comfortable |

So the honest budget is: **fine to a few hundred drives; needs work beyond that.** Two levers, both
cheap, in this order: (1) draw only the traces intersecting the current viewport, which is what
drivingroads.io does and what makes zoom-out cheap rather than expensive; (2) simplify harder as
you zoom out — ε = 50 m halves the point count again (measured: 208 points for the reference
drive, 1,004 bytes).

Overlapping passes need no special handling: same colour, same width, full alpha, so a road driven
five times looks identical to one driven once. Opposite carriageways of a motorway will show as two
parallel lines, ~20–30 m apart. That is honest and, at coverage-map zoom levels, invisible.

### What this proposal is and is not

| | |
|---|---|
| ✅ | Memory map, which is the stated primary purpose |
| ✅ | Works everywhere on earth with no per-region data |
| ✅ | Works offline; nothing new is fetched at render time |
| ✅ | No new API spend — reuses the one Roads API call per drive that already happens |
| ✅ | No OSM, therefore no ODbL obligations at all |
| ✅ | Free side effect: restores Trip Detail's map on a reinstalled/second device |
| ❌ | **No number.** No "roads driven", no percentage, no completion of any kind |
| ⚠️ | Existing drives have no stored snapped path, and 11 of 17 have no route points either. §9.6, §9.7 |

**Effort:** small. A Room table + migration, an encoder/decoder (~40 lines, no dependency), one
write in `PostRideWorker`'s existing path, one Firestore collection, one screen. Days, not weeks.

---

## 5. Proposal B — roads ticked, against a curated denominator (recommended second)

Proposal A plus a **finite, named, curated list of roads** to tick off. This is the drivingroads.io
model, and it is the one that answers the user's objection directly: a denominator nobody could
finish is the wrong denominator, so pick one a person could plausibly move.

### The denominator, and why it is smaller than it sounds

Measured for Portugal (ohsome API over an OSM snapshot, corroborated against HOT/HDX to within
1.2%; encoded-polyline byte costs measured over 2,254 km of real sampled geometry with real RDP and
real polyline encoding):

| Tier | km | Points @ ε=10 m | Encoded polyline | gzipped | Stitched by name+ref |
|---|---:|---:|---:|---:|---:|
| motorway + trunk | 10,530 | 84,940 | **0.5 MB** | 0.3 MB | — |
| motorway…secondary | 38,537 | 403,841 | **2.3 MB** | 1.5 MB | ~1.5 MB |
| motorway…tertiary | 69,751 | 715,981 | **3.9 MB** | 2.7 MB | **2.6 MB** |
| everything car-drivable (incl. residential, service) | 186,609 | 2,713,716 | 14.9 MB | 9.8 MB | 10.0 MB |

Two things worth internalising. First, **the whole national road network of Portugal down to
tertiary fits in 2.6 MB inside the APK** — the storage question everyone expects to be hard is not
hard for one country. Second, **the dominant lever is stitching, not simplification**: mean OSM way
length is 122 m, so ~79% of retained points are way *endpoints* rather than shape; merging ways by
class + name + ref cuts points 27% and bytes 33%, whereas moving RDP tolerance from 5 m to 20 m
only moves the total ~20%.

A *curated* set is smaller again. drivingroads.io's ~5,300 roads/country at ~10 km each is roughly
53,000 km — but a DriveDelta-shaped curation ("named roads worth remembering") could reasonably be
the ~2,000 roads of the IP/IC/EN/ER national network plus scored scenic roads, comfortably under
1 MB.

**Where the curation comes from — pick one, they have very different costs:**

- **Derive it ourselves from OSM.** Stitch `motorway|trunk|primary|secondary` plus named `tertiary`
  by `ref`/`name`; that alone yields a list of real, named Portuguese roads (A1, IC8, N236-1 — the
  same names the app's geocoder already produces). Optionally score them for interest with
  drivingroads-style geometry maths (sinuosity, angular-change hairpin detection); SRTM elevation is
  free if wanted. **Cost: engineering, once, offline. Licence: ODbL, see §9.2.**
- **Hand-curate.** A few hundred roads the user actually cares about. Zero pipeline, real ongoing
  human cost, and it does not scale past one country.
- **Inherit someone else's.** drivingroads.io is closed (§2). No other free, licence-clean curated
  driving-road list was found.

### Matching a drive to it

Post-ride, in the existing `PostRideWorker`, offline, with **no additional network calls at all**:

1. The Roads API has already snapped the drive onto road centrelines (§4). Start from those points,
   not the raw GPS — that is what makes the matching easy rather than a real map-matching problem.
2. For each snapped point, look up candidate ways from a bundled spatial index (a simple geohash-6
   grid over the reference set is enough) within ~15 m.
3. Keep candidates whose bearing agrees with travel to within ~30°. This is what separates a
   motorway from its frontage road and its opposite carriageway.
4. Accumulate covered length per road id; apply the §3 thresholds.

This is deliberately *not* HMM/Viterbi map matching. Coverage does not need a topologically valid
route — it needs "did I touch this way", and Google has already put the points on the tarmac. If
this proves too loose in practice, the escalation is well-understood, but start here.

### Score

See §8. Never a global percentage.

**Effort:** medium-large. An offline OSM→bundle pipeline, a bundled index, a matcher, and the
progress UI. Weeks. The pipeline is the part that will take longer than it looks.

---

## 6. Proposal C — full-network coverage percentage (sized, not recommended)

Every car-drivable OSM way is the denominator; the map shades what fraction of the network you have
covered. Included because the brief asked for it as a real alternative rather than a footnote.

**Data:** 186,609 km / 2.7 M points / 14.9 MB for Portugal (10.0 MB stitched, ~7 MB gzipped) — still
APK-sized for one country. **Outside Portugal it is not:** Spain's raw extract is 1.47 GB versus
Portugal's 421 MB, and there is no per-region trick that avoids building your own pipeline for each
country. Realistically it becomes a downloaded-per-region model, which is a server, a CDN and a
region picker.

**Why it is the wrong product anyway.** The denominator includes 67,579 km of `residential`,
18,180 km of `service`, and — if you are not careful with the filter — 190,600 km of `track`, which
is 47% of everything tagged `highway=*` in Portugal. A percentage against that base will read
somewhere near 0.5% forever, and the roads it says you are missing are supermarket car parks and
farm tracks. **This is precisely the score the user ruled out**, and it is worse than it sounds
because most of the denominator is not merely undriveable-in-a-lifetime but actively pointless.

**One more trap.** OSM splits dual carriageways into two ways: OSM's 6,111 km of `motorway` against
the official 3,113 km is a factor of **1.96**. So a naive percentage is halved on exactly the roads
people drive most, unless carriageways are paired first.

Sized for completeness. Not recommended.

---

## 7. Rejected: aggregating segment chords

`DISCUSSION.md` §2 option A — draw a straight line between each synced segment's start and end.
Attractive because segments already sync and nothing new is needed. Measured against the real 210
segments:

| | |
|---|---|
| Real trace length of all segments | 285.7 km |
| Sum of straight-line chords | **267.0 km** |
| Overall ratio | 0.934 |
| Median per-segment ratio | 0.968 |
| p10 | 0.664 |
| **Worst five segments** | **0.34, 0.37, 0.39, 0.44, 0.47** |

The aggregate looks acceptable and the detail is not. A chord ratio of 0.34 means a road drawn at
one third of its real length, cutting straight across whatever the road was going round — and by
construction the worst-affected segments are the twisty ones, which are the ones a driver remembers
and the ones any future "good roads" idea would care about. On a motorway it looks fine; on a
mountain pass it draws a line through the mountain.

Given §1.2 — real geometry costs 2 KB per drive — there is no reason to accept this.

---

## 8. Scoring: completion without implying 100%

The user's constraint: *completion "should not be something to chase intensively because no one can
drive all the roads, and some are private/dangerous"*. A denominator that implies 100% is reachable
is therefore actively wrong. Four score shapes that satisfy it, in the order I would ship them:

1. **New road, per drive and per period.** *"This drive: 47 km of road you have never driven —
   your best since May."* Needs **no denominator at all**, so it works in Proposal A, works
   anywhere on earth, and is the strongest memory-first framing: it rewards going somewhere new
   without ever implying a finish line. This alone may be enough.
2. **Named roads ticked (Proposal B).** *"IC8 — first driven 9 Aug 2026."* A collection, not a
   percentage. Same psychology as drivingroads.io's "23 of the top 100".
3. **Per-municipality progress (Proposal B).** Portugal has 308 *concelhos*; a small one is
   genuinely completable, which is the point. A per-municipality bar is a set of small honest
   denominators instead of one dishonest large one. Do **not** roll them up into a national figure.
4. **Roads near you.** *"You have driven 61% of the named roads within 25 km of home."* A local,
   bounded, achievable denominator — and the one place a percentage is defensible.

What to avoid: a single national or global percentage; anything called "completion"; leaderboards
(sharing is out of scope anyway); and any streak that punishes not driving.

---

## 9. Warnings

**9.1 — `roadKey` is not a road id (§1.1).** 209 distinct keys for 210 segments, measured. Any
design that assumes it de-duplicates repeat drives is broken before it starts. If a future
checkpoint makes `roadKey` genuinely stable across drives, that is a segmentation change with its
own consequences for `routeHash`, personal bests and `CompareAndMatchSegmentsTest` — not a free win.

**9.2 — OSM licensing (Proposals B and C).** Bundling OSM-derived road geometry in the APK creates
a **Derivative Database**, not a Produced Work: the OSMF Produced Work Guideline's test is whether
the published result "is intended for the extraction of the original data", and an app that decodes
those polylines to render them plainly is. Simplification does not change that (ODbL §1.0 covers
"any … alteration"). Concretely:

- **The app's source stays closed.** ODbL §2.3.a: "This License does not apply to computer programs
  used in the making or operation of the Database." ODbL has no copyleft reach into code.
- **The bundled data file must be offered under ODbL**, and §4.6 requires offering recipients
  either the derived database itself or the method used to make it. A static public URL to the
  bundled file is the unambiguous, cheap discharge.
- **Two separate attributions are needed**, and the second is the one implementations miss: the
  usual "© OpenStreetMap contributors" on the map surface, *and* a database-level notice shipped
  **inside** the assets directory alongside the data file (ODbL §4.2 + Attribution Guidelines).
- **The user's own GPS traces are not encumbered** — the Collective Database Guideline has a
  near-verbatim example about deriving traffic data from in-car GPS.
- ⚠️ **The trap specific to DriveDelta:** that same guideline requires the two datasets "do not
  reference each other", defining a reference as anything usable as a database join key. **Persisting
  an OSM way id onto a trip/segment row creates exactly such a reference.** Exposure is low while
  traces stay private, but the clean mitigation is to keep the coverage set in its own table keyed
  by way id and join at render time, rather than stamping OSM ids onto trace rows — and to never
  publish a cross-user "most-driven roads" aggregate keyed to OSM ways without releasing it ODbL.
- Not legal advice. The guidelines say the legal text prevails.

**9.3 — drivingroads.io is off limits as a data source.** Terms §8 forbids scraping, harvesting,
bulk download, systematic extraction, automated access, redistribution and reverse-engineering the
methodology; `robots.txt` disallows `/api/`. Use the product model, not the data. Their pipeline
inputs (OSM + SRTM) are open to us independently.

**9.4 — Overpass API is not an option for on-demand lookups.** The operator's own commons page caps
fair use at "about 10000 requests per day" and "below about 1 GB per day" *per user*, and lists
under **problematic behaviour**: *"Setting up an app for more than just OSM mappers and relying on
the public instances as backend."* The prescribed remedy is to run your own instance. Any proposal
whose runtime depends on Overpass is dead. (Use it offline, once, to build a bundle — that is fine.)

**9.5 — Live lookups are dead on arrival.** `CLAUDE.md`'s cost rule is absolute: Roads API exactly
once per completed trip, never during tracking. Every proposal here keeps matching in
`PostRideWorker`, which is where it belongs anyway — and Proposals A and B add **zero** new network
calls at any point.

**9.6 — The existing drives have no stored snapped path.** Proposal A persists the snap from the
next drive onward. Backfilling means either re-snapping (money, and it re-runs a pipeline whose
output CP20–CP23 spent four checkpoints stabilising) or falling back to raw route points — which is
free, slightly scruffier, and only possible on the device that recorded them. Note the second
option only covers **6 of the 17 trips** on the emulator, for the reason in §9.7.
Recommendation: **build the coverage map from raw route points where a snap is not stored**, mark
the trace `source = "RAW"`, and let the *"Recalculate segments"* menu item already in
Trip Detail be the manual escape hatch for anyone who wants the better geometry.

**9.7 — "Roads driven on this phone" is the default failure mode, and it is already happening.**
Until traces sync, the map means "what this device happens to still have". Measured on the emulator
(same account, restored from Firestore): **11 of the account's 17 trips have zero route points** —
only 6 do. A coverage map built from route points today would draw a third of the account and look
complete. If Proposal A ships without the Firestore half, this bug ships with it, and it is
invisible until someone reinstalls.

**9.8 — Local growth is the unbounded quantity, not the driven set.** 98.3 bytes per route-point
row, 29.8 fixes/km → ~44 MB/year at 15,000 km/year, forever, with no pruning anywhere in the app.
The driven set is negligible beside it (~30 KB/account today, single-digit MB after a decade). Once
a trace is stored, consider pruning raw route points for old drives — but note replay and
*"Recalculate segments"* both read them, so that is a product decision, not a cleanup.

**9.9 — Reinstall.** With traces synced, a reinstall restores the coverage map from Firestore.
Without them, it comes back empty and *looks* like data loss even though the trips are all there.
Whichever proposal ships, make the trace sync part of the same change, not a follow-up.

**9.10 — Abroad.** Proposal A is geography-free. Proposal B's bundle is per-country: a drive in
Spain would silently score nothing while still drawing correctly. Design the UI so the *map* works
everywhere and the *score* is explicitly scoped ("Portugal") rather than quietly wrong. The brief's
"90% of my rides will be in Portugal" makes this acceptable; a silent zero would not be.

**9.11 — Firestore cold start.** `pullAll` already fetches every trip, segment, place, car and fuel
log document from the server on every sign-in. Adding traces to that path would make cold start
noticeably heavier for no benefit; pull the `traces` collection lazily, the first time the coverage
map is opened, and cache it in Room.

---

## 10. Rendering: the measured ceiling

The known data point going in was CP24: **5,162 `Polyline` objects on a Google Map threw
`OutOfMemoryError` and killed the process.** That gave a death point but not a model, so I measured
one. Harness: a throwaway debug `Activity` hosting a plain `MapView`, adding *N* polylines of *K*
points each spread over mainland Portugal, camera framed on the whole bbox so everything is really
drawn; then 5 s of programmatic panning with a `Choreographer` frame callback. Emulator
`emulator-5554`, `dalvik.vm.heapgrowthlimit = 192m`.

| Polylines | Points each | Total points | Add time | Java heap Δ | Pan fps | Jank frames |
|---:|---:|---:|---:|---:|---:|---:|
| 500 | 20 | 10,000 | 7.7 s | +33.3 MB | 36.5 | 44 |
| 2,000 | 20 | 40,000 | 8.7 s | +67.8 MB | 41.9 | 35 |
| 5,000 | 20 | 100,000 | 15.9 s | +134.0 MB | 25.2 | 40 |
| **8,000** | 20 | 160,000 | — | **OutOfMemoryError** | — | — |
| **12,000** | 20 | 240,000 | — | **OutOfMemoryError** | — | — |
| 1,000 | 100 | 100,000 | 11.0 s | +50.9 MB | 37.7 | 15 |
| 100 | 1,000 | 100,000 | 11.3 s | +44.7 MB | 5.8 ⚠️ | 1 |
| 1,000 | 500 | 500,000 | 25.8 s | +154.9 MB | 55.8 | 18 |
| 2,000 | 250 | 500,000 | 20.7 s | +162.4 MB | 36.4 | 20 |
| 20 | 50,000 | 1,000,000 | **never completed** (>10 min) | — | — | — |

**What this says.**

1. **Object count is the wall, and it is a hard one.** 8,000 polylines died holding only 160,000
   points, while 1,000 polylines held 500,000 points and survived. Same geometry, 3× the memory
   when split across 5,000 objects instead of 1,000 (134.0 MB vs 50.9 MB for 100 K points). Rough
   model: **~20 KB per `Polyline` object, plus ~0.2–0.3 KB per vertex.** CP24's crash was not
   "too much geometry", it was "too many objects" — and the fix that shipped (cap at 120) is the
   right shape.
2. **The practical ceiling on this heap is between 5,000 and 8,000 objects.** Scaling by heap
   growth limit, the Galaxy S25 (`256m`, measured with `getprop`) should reach roughly 8,700.
   `android:largeHeap="true"` would raise it further (512 MB on the S25) but is a blunt instrument.
3. **Very long polylines are their own failure mode.** 20 × 50,000 never finished adding in over
   ten minutes — superlinear in points-per-object, so "just merge everything into one polyline" is
   not the escape hatch it looks like.
4. **A safe working budget: ≤ 1,000 objects and ≤ 150,000 vertices**, which measured at ~50 MB and
   ~11 s to add. Note the *add time* is the binding constraint long before memory is: 11 s of
   staring at an empty map is unacceptable, so build the geometry off the main thread and add it in
   viewport-sized batches.

⚠️ **Caveats, stated plainly.** This is an emulator with a software GL renderer, so the **fps
column is not transferable** — read it as relative only, and treat the 5.8 fps at 100 × 1,000 as
probably an artefact of my synthetic geometry (100 lines each ~30 km long, crossing many tiles)
rather than a real property of long polylines. The memory figures and the OOM threshold are the
numbers to carry forward. The add times will be faster on the S25 but the shape will hold.

### The tile alternative, measured on the same harness

The same geometry, rasterised into 256 px tiles by a custom `TileProvider` instead of handed to the
map as vector objects:

| Mode | Lines | Total points | Add time | Java heap Δ | Pan fps |
|---|---:|---:|---:|---:|---:|
| Polyline | 5,000 | 100,000 | 15.9 s | **+134.0 MB** | 25.2 |
| **Tile** | 5,000 | 100,000 | 3.3 s | **+18.6 MB** | 10.4 |
| Polyline | 8,000 | 160,000 | — | **OutOfMemoryError** | — |
| **Tile** | **20,000** | **400,000** | 4.8 s | **+23.4 MB** | 1.3 ⚠️ |

This confirms the property that matters: **tile memory is bounded by the visible tile count, not by
how much geometry exists.** Four times the geometry moved the heap from 18.6 MB to 23.4 MB, in a
range where polylines had already died. The cost simply moves to CPU per tile — and the 10.4 → 1.3
fps collapse is my provider being deliberately naive (it walks every line for every tile, i.e.
O(tiles × lines)). **That collapse is the design note, not a verdict on tiles:** a real provider
needs a spatial index so a tile only touches the geometry inside it, plus a bitmap cache. With
those, tiles are the answer for any driven set that outgrows the polyline budget.

### If the budget is exceeded

In increasing order of cost:

1. **Viewport culling + zoom-dependent simplification.** What drivingroads.io does. Cheapest by far
   and probably sufficient forever for a single user's own traces.
2. **`TileOverlay` with a custom `TileProvider`** — measured above: 20,000 lines / 400,000 points
   in **23.4 MB**, where polylines OOM'd at 8,000 lines / 160,000 points. Needs a spatial index and
   a bitmap cache, or the per-tile CPU cost collapses the frame rate (measured, and by design).
3. **A pre-rendered raster/heatmap** — one `GroundOverlay` per region, rebuilt when new drives land.
   Cheapest to render, worst to keep fresh, and it cannot be tapped.
4. **Drop Google Maps for MapLibre Native.** Real vector tiles, `pmtiles://` support since 11.7.0,
   and a genuine offline story. It is also a rewrite of every map surface in the app and would need
   a basemap source (Protomaps, OpenFreeMap or self-hosted). Two Android-specific gotchas worth
   knowing before anyone gets excited: **`pmtiles://asset://` is not supported** (AssetManager
   cannot do byte-range reads — the file must be copied to `filesDir` first), and PMTiles sources
   do not work with MapLibre's offline-pack manager. For scale: a z0–14 PMTiles basemap for
   mainland Portugal measures **332 MB**; z0–12 is 83 MB. That is 30–100× the size of the road
   *geometry* (§5), which is the argument for not needing a custom basemap at all.

---

## 11. Recommendation and sequencing

**Ship Proposal A first, in one change that includes the Firestore half.** It delivers the stated
primary purpose (memory), needs no reference data, no licence, no new spend and no new failure
modes; it retires `DISCUSSION.md` §2's blocker and the "route point sync" backlog item; and it fixes
the restored-device blank map on the way past. Pair it with score shape 1 from §8 ("new road this
drive"), which needs no denominator and is the honest completion signal.

**Then decide, with the map in front of you, whether the completion vibe still wants a
denominator.** It may not — a filling-in map plus "47 km of new road today" may be the whole
feature. If it does, Proposal B is the version to build, because a curated denominator is the only
one that survives the user's own objection.

**Do not build Proposal C.** Its score is the one that was explicitly ruled out, and its data story
outside Portugal is a server.

---

## 12. Open questions for the user

1. **Is Proposal A on its own enough?** A filling-in map with no roads counter, no percentage, just
   "47 km of new road on this drive". If yes, this is days of work and the rest of the document is
   contingency.
2. **Direction**: should driving one carriageway light both, or only the one you drove? (§3. I
   recommend both, for a memory map.)
3. **The back catalogue** (§9.6/§9.7): re-snap the existing drives at Roads API cost, accept raw-GPS
   traces for the 6 that still have route points, or just start the map from the next drive?
4. **If Proposal B**: derive the curated set from OSM ourselves — accepting the ODbL obligations in
   §9.2 — or hand-curate a few hundred roads and dodge the licence entirely?
5. **Pruning** (§9.8): once a trace is stored, should raw route points for old drives be deleted?
   It costs replay and *Recalculate segments* on those drives, and saves ~44 MB/year.

---

## Appendix — how every number was obtained

**Account data.** Pulled from `emulator-5554`, which is seeded with a copy of the phone's real
database:
```
adb shell "run-as app.drivedelta cat databases/drivedelta.db" > drivedelta.db      # + -shm, -wal
sqlite3 drivedelta.db "select count(*), count(distinct roadKey), count(distinct roadName),
                              sum(distanceMeters)/1000.0 from segments;"
sqlite3 drivedelta.db "select (select sum(pgsize) from dbstat where name='route_points')*1.0
                            /(select count(*) from route_points);"
```
17 trips · 210 segments · 9,791 route points · 318.8 trip km · 285.7 segment km.

**Chord ratios and polyline encoding.** Python over the same database and over
`app/src/test/resources/fixtures/gestosa-home-173km.json` (the CP21 fixture): Haversine for
distance, a straight Douglas–Peucker, and a from-scratch Google encoded-polyline encoder. Note the
fixture was captured **before** the CP22 overlap fix, so its snapped path measures 206.4 km against
a 173.1 km raw trace — the raw-trace numbers used above are unaffected.

**Rendering.** A throwaway `MapStressActivity` in a temporary `app/src/debug/` source set,
launched as
```
adb shell am start -n app.drivedelta/app.drivedelta.debug.MapStressActivity \
  --es mode polyline --ei n 5000 --ei k 20
```
logging add time, `Runtime` heap delta, `Debug.getNativeHeapAllocatedSize`, and a 5 s
`Choreographer` frame count while the camera pans. Emulator `sdk_gphone16k_arm64`, Android 17 image,
`dalvik.vm.heapgrowthlimit = 192m`; the phone (`SM-S931B`) reports `256m`. **The harness and its
`AndroidManifest.xml` were deleted after measuring; nothing remains in the working tree.**

**OSM sizing, Overpass policy, tile services, PMTiles and ODbL.** Delegated research, 2026-08-23.
Road lengths from the ohsome API over an OSM snapshot clipped to the geoBoundaries PRT boundary,
cross-checked against an independent Overpass fetch (agreement 0.4–8.3% per class) and against
HOT/HDX's published 404,200 km (within 1.2%). Official figures from Eurostat `road_if_motorwa` /
`road_if_roadsc` and PORDATA — note Portugal has published **no** official municipal or total road
length since **1996**, so any "Portugal has ~82,000 km of roads" figure is a legacy number. Byte
costs measured over 2,254 km of sampled real geometry with real RDP and real polyline encoding
(6.00 B/point unstitched, 5.56 B/point stitched — confirming the 5–6 B/point assumption). Geofabrik
sizes by `curl -sIL` reading `Content-Length` (`portugal-latest.osm.pbf` = 420.9 MB;
`spain-latest.osm.pbf` = 1.47 GB; Portugal has no sub-regions — that path 404s). PMTiles sizes
measured by range-reading the live planet archive's directories only, summing real tile-blob
lengths. Overpass quotas quoted from `dev.overpass-api.de/overpass-doc/en/preface/commons.html`.
ODbL analysis from the OSMF Produced Work Guideline (board-endorsed 2014-06-06), the Collective
Database Guideline, the Attribution Guidelines and the ODbL text itself.

**drivingroads.io.** Observed by browsing the live site on 2026-08-23: home page copy, `/terms`,
`/robots.txt`, and the map's own network traffic (`api.mapbox.com/styles/v1/mapbox/dark-v11`,
`auth.drivingroads.io/rest/v1/rpc/get_filter_bounds`). The 201,988 / 143,225 road counts are read
from the live filter panel. **No automated access, no bulk retrieval, nothing extracted** — their
Terms §8 forbids it and it was not necessary.
