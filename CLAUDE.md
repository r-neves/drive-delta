# DriveDelta — Android App · Claude Code Master Plan

> **Session start:** Before doing anything, read `PROGRESS.md` in the repo root to see which
> checkpoint we're on, what's done, and what's next. This plan is built in strict checkpoint order
> (see "MVP Build Checkpoints"). When a checkpoint completes: tick its boxes here, update
> `PROGRESS.md`, then commit + push. A checkpoint isn't done until it's committed and pushed —
> this is what lets work continue seamlessly across multiple machines.

## Product Summary

Native Android app (Kotlin + Jetpack Compose) that records car drives, tracks GPS traces,
compares road segments against personal bests, and logs fuel/energy consumption per vehicle.
Users sign in with Google SSO; all data is private per user, stored in Firestore with full
offline support via Room as local cache. AAOS support is a post-MVP goal.

---

## Confirmed Requirements

| Topic | Decision |
|---|---|
| Auth | Google SSO via Firebase Auth |
| Data storage | Firestore (cloud) + Room (offline-first local cache) |
| UI toolkit | Jetpack Compose + Material 3 |
| Maps | Google Maps SDK + Google Roads API (snap-to-road, road segment naming) |
| Segment definition | Road-based via Roads API (road name / junction changes) |
| Live tracking HUD | Full dashboard: speed, elapsed time, current vs best split, distance |
| Post-ride summary | Map + segment splits + ride replay |
| Trip end | Auto-stop (geofence arrival) as default + manual stop with confirmation |
| GPS gap fill | Drop inaccurate points + linear interpolation. No full dead-reckoning for MVP. |
| Consumption logging | Manual only (fill-up form per ride) |
| Social features | None for MVP; architecture must not block future addition |
| AAOS | Post-MVP |
| Language | System locale: pt-PT and en |
| Distribution | Play Store (each user sees only their own data) |
| Design | High-fi mockups in Claude Design → imported to `/design/` → translated to Compose theme. **Dark theme only** for the POC. |

---

## Tech Stack

### Android App

| Layer | Library | Notes |
|---|---|---|
| Language | Kotlin 2.0+ | 100% Kotlin, no Java |
| UI | Jetpack Compose + Material 3 (BOM 2024.06+) | Single-activity |
| Architecture | MVVM + Clean Architecture (UseCases) | |
| DI | Hilt 2.51+ | KSP, not KAPT |
| Navigation | Navigation Compose 2.7+ | |
| Local DB | Room 2.6+ | Source of truth |
| Async | Coroutines + Flow 1.8+ | |
| Auth | Firebase Auth (Google Sign-In) | Firebase BoM 33+ |
| Remote DB | Firebase Firestore | Firebase BoM 33+ |
| Background sync | WorkManager 2.9+ | Periodic Firestore push |
| Maps | Maps Compose 4.3+ | |
| Location | FusedLocationProviderClient 21+ | |
| Roads/Snap | Retrofit 2.11+ → Google Roads API | Post-ride only |
| Address search | Google Places SDK 3.5+ | For Place creation |
| Charts | Vico 2.0+ (Compose-native) | Segment comparison |
| Serialization | Kotlin Serialization 1.7+ | |
| Testing | JUnit4 + MockK + Turbine + Compose Test | |
| Build | Gradle Kotlin DSL + Version Catalog | |

### Backend (Firebase — no custom server needed for MVP)

| Service | Purpose |
|---|---|
| Firebase Auth | Google SSO, user identity |
| Firestore | Per-user private subcollections, offline persistence |
| Firebase Rules | Enforce `userId == request.auth.uid` on all documents |

### External APIs

| API | Used for | Cost note |
|---|---|---|
| Google Maps SDK Android | Map display, polyline rendering | Free quota |
| Google Roads API — snapToRoads | Snap raw GPS to road network | $10/1000 req |
| Google Roads API — nearestRoads | Road name per snapped point | Same |
| Google Places API — Autocomplete | Address search in Place editor | $17/1000 req |
| Geocoding API | Reverse geocode marker position | $5/1000 req |

> **Cost control rule:** Roads API called exactly once per completed trip (post-ride, async).
> Never called during live tracking. Snapped polylines cached in Room + Firestore so re-views
> are free. Places/Geocoding called only on explicit user action.

---

## Project Structure

> Note on paths: the outer `app/` is the standard Gradle **module** directory. The package
> `app.drivedelta` (application ID) maps to the **source** path `src/main/kotlin/app/drivedelta/`.
> These two `app` segments live at different levels and are unrelated — this is normal for an
> `app.*` application ID. All package declarations are `package app.drivedelta.<...>`.

```
design/                                        # Checkpoint 0 output (design reference, not code)
├── mockups/
│   │                                          # PNG exports — the visual reference to view per screen:
│   ├── auth.png
│   ├── dashboard.png
│   ├── car-edit.png
│   ├── place-edit.png
│   ├── trip-detail.png                        # Splits tab, incl. the purple-sector PB row
│   ├── tracking-hud-ahead.png                 # HUD, green "ahead of best" delta state
│   ├── tracking-hud-behind.png                # HUD, red "behind best" delta state
│   ├── ride-moments-pre-ride.png              # pre-ride setup sheet (F5)
│   ├── ride-moments-stop-confirm.png          # StopConfirmSheet (F6-A)
│   ├── ride-moments-auto-finish.png           # ArrivalSheet w/ 30s countdown (F6-B)
│   ├── ride-moments-acquiring-gps.png         # cold-GPS-start HUD state (Checkpoint 10)
│   ├── ride-moments-no-trips.png              # empty states ↓
│   ├── ride-moments-no-cars.png
│   ├── ride-moments-no-places.png
│   │                                          # source docs — React-templated, DO NOT read as spec:
│   ├── auth.html  dashboard.html  tracking-hud.html  trip-detail.html
│   ├── car-edit.html  place-edit.html  ride-moments.html
│   ├── app-shell.html                         # canvas index; imports the others by doc name
│   └── support.js                             # Claude Design runtime (needs React; won't run offline)
├── frames/
│   └── android-frame.jsx                      # M3 device chrome — NOT DriveDelta brand, ignore
└── tokens.md                                  # ← SOURCE OF TRUTH for all colors/type/spacing/specs

app/                                           # Gradle module directory
├── build.gradle.kts
├── google-services.json                      (gitignored)
└── src/main/
    ├── AndroidManifest.xml
    └── kotlin/app/drivedelta/                 # source root = package app.drivedelta
        ├── DriveDeltaApplication.kt           # Hilt + Firebase + Places SDK init
        ├── MainActivity.kt                    # Single Compose activity
        │
        ├── core/
        │   ├── auth/
        │   │   ├── AuthRepository.kt          # Interface
        │   │   └── FirebaseAuthRepository.kt
        │   ├── sync/
        │   │   ├── SyncManager.kt             # Room ↔ Firestore bidirectional sync
        │   │   └── SyncWorker.kt              # WorkManager worker
        │   └── util/
        │       ├── GeoUtils.kt                # Haversine, bearing, interpolation helpers
        │       └── DateUtils.kt
        │
        ├── data/
        │   ├── local/
        │   │   ├── AppDatabase.kt
        │   │   ├── dao/
        │   │   │   ├── TripDao.kt
        │   │   │   ├── RoutePointDao.kt
        │   │   │   ├── SegmentDao.kt
        │   │   │   ├── PlaceDao.kt
        │   │   │   ├── CarDao.kt
        │   │   │   └── FuelLogDao.kt
        │   │   └── entity/
        │   │       ├── TripEntity.kt
        │   │       ├── RoutePointEntity.kt
        │   │       ├── SegmentEntity.kt
        │   │       ├── PlaceEntity.kt
        │   │       ├── CarEntity.kt
        │   │       └── FuelLogEntity.kt
        │   ├── remote/
        │   │   ├── firestore/
        │   │   │   ├── dto/                   # Firestore data transfer objects
        │   │   │   │   ├── TripDto.kt
        │   │   │   │   ├── SegmentDto.kt
        │   │   │   │   ├── PlaceDto.kt
        │   │   │   │   ├── CarDto.kt
        │   │   │   │   └── FuelLogDto.kt
        │   │   │   └── FirestoreDataSource.kt # All Firestore CRUD operations
        │   │   └── roads/
        │   │       ├── RoadsApiService.kt     # Retrofit interface
        │   │       ├── RoadsDto.kt            # Response DTOs
        │   │       └── RoadsDataSource.kt     # Chunking + stitching logic
        │   └── repository/
        │       ├── TripRepositoryImpl.kt
        │       ├── PlaceRepositoryImpl.kt
        │       ├── CarRepositoryImpl.kt
        │       └── FuelLogRepositoryImpl.kt
        │
        ├── domain/
        │   ├── model/
        │   │   ├── Trip.kt
        │   │   ├── RoutePoint.kt
        │   │   ├── Segment.kt
        │   │   ├── SegmentComparison.kt
        │   │   ├── Place.kt
        │   │   ├── Car.kt
        │   │   ├── FuelLog.kt
        │   │   └── TrackingState.kt
        │   ├── repository/                    # Interfaces only
        │   │   ├── TripRepository.kt
        │   │   ├── PlaceRepository.kt
        │   │   ├── CarRepository.kt
        │   │   └── FuelLogRepository.kt
        │   └── usecase/
        │       ├── trip/
        │       │   ├── StartTripUseCase.kt
        │       │   ├── StopTripUseCase.kt
        │       │   ├── GetTripsUseCase.kt
        │       │   └── GetTripDetailUseCase.kt
        │       ├── segment/
        │       │   ├── SnapRouteToRoadsUseCase.kt
        │       │   ├── BuildSegmentsUseCase.kt
        │       │   ├── MatchSegmentsUseCase.kt
        │       │   └── CompareSegmentsUseCase.kt
        │       ├── place/
        │       │   ├── SavePlaceUseCase.kt
        │       │   ├── DeletePlaceUseCase.kt
        │       │   ├── GetPlacesUseCase.kt
        │       │   └── DetectNearbyPlaceUseCase.kt
        │       ├── car/
        │       │   ├── SaveCarUseCase.kt
        │       │   ├── DeleteCarUseCase.kt
        │       │   └── GetCarsUseCase.kt
        │       ├── fuel/
        │       │   └── LogFuelUseCase.kt
        │       └── arrival/
        │           └── DetectArrivalUseCase.kt
        │
        ├── service/
        │   └── TrackingForegroundService.kt
        │
        └── ui/
            ├── navigation/
            │   ├── AppNavGraph.kt
            │   └── NavDestinations.kt
            ├── theme/
            │   ├── Theme.kt
            │   ├── Color.kt
            │   └── Type.kt
            ├── auth/
            │   ├── AuthScreen.kt
            │   └── AuthViewModel.kt
            ├── dashboard/
            │   ├── DashboardScreen.kt
            │   └── DashboardViewModel.kt
            ├── tracking/
            │   ├── TrackingScreen.kt
            │   ├── TrackingViewModel.kt
            │   └── components/
            │       ├── HudOverlay.kt
            │       ├── SegmentSplitRow.kt
            │       ├── StopConfirmSheet.kt
            │       └── ArrivalSheet.kt
            ├── tripdetail/
            │   ├── TripDetailScreen.kt
            │   ├── TripDetailViewModel.kt
            │   └── components/
            │       ├── RouteMapView.kt
            │       ├── SegmentSplitList.kt
            │       └── ReplayController.kt
            ├── compare/
            │   ├── CompareScreen.kt
            │   └── CompareViewModel.kt
            ├── history/
            │   ├── HistoryScreen.kt
            │   └── HistoryViewModel.kt
            ├── places/
            │   ├── PlacesScreen.kt
            │   ├── PlacesViewModel.kt
            │   ├── PlaceEditScreen.kt
            │   └── PlaceEditViewModel.kt
            ├── cars/
            │   ├── CarsScreen.kt
            │   ├── CarsViewModel.kt
            │   ├── CarEditScreen.kt
            │   └── CarEditViewModel.kt
            └── fuel/
                ├── FuelLogScreen.kt
                └── FuelLogViewModel.kt
```

---

## Data Models

### Room Entities

```kotlin
// TripEntity.kt
@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val id: String,           // UUID generated locally
    val userId: String,                   // Firebase UID — all queries filter by this
    val startTime: Long,                  // epoch ms
    val endTime: Long?,                   // null while in progress
    val startLat: Double,
    val startLng: Double,
    val endLat: Double?,
    val endLng: Double?,
    val startPlaceId: String?,            // FK to places.id
    val endPlaceId: String?,
    val carId: String?,                   // FK to cars.id
    val distanceMeters: Float,
    val durationMs: Long,
    val routeHash: String = "",           // SHA-256 of ordered roadKey sequence; set post-snap
    val stopTrigger: String = "",         // "MANUAL" | "GEOFENCE"
    val roadsProcessed: Boolean = false,  // true once Roads API snap is complete
    val syncedAt: Long? = null,           // null = pending Firestore sync
    val notes: String = ""
)

// RoutePointEntity.kt
@Entity(tableName = "route_points")
data class RoutePointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: String,
    val timestamp: Long,
    val lat: Double,
    val lng: Double,
    val accuracyMeters: Float,
    val speedMps: Float,
    val altitudeMeters: Double,
    val isInterpolated: Boolean = false   // true = GPS gap, linearly interpolated
)

// SegmentEntity.kt — computed post-ride after Roads API snap
@Entity(tableName = "segments")
data class SegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: String,
    val segmentIndex: Int,
    // roadKey is the stable cross-trip identifier for this stretch of road.
    // Format: "RoadName|startLat4dp,startLng4dp|endLat4dp,endLng4dp"
    val roadKey: String,
    val roadName: String,                 // human-readable, e.g. "A1 - Autoestrada do Norte"
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double,
    val distanceMeters: Float,
    val durationMs: Long,
    val avgSpeedMps: Float,
    val maxSpeedMps: Float
)

// PlaceEntity.kt
@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey val id: String,           // UUID
    val userId: String,
    val name: String,
    val address: String,                  // reverse-geocoded label
    val lat: Double,
    val lng: Double,
    val radiusMeters: Float = 100f,       // geofence radius 50–500m
    val iconEmoji: String = "📍",
    val createdAt: Long,
    val syncedAt: Long? = null
)

// CarEntity.kt
@Entity(tableName = "cars")
data class CarEntity(
    @PrimaryKey val id: String,           // UUID
    val userId: String,
    val name: String,
    val licensePlate: String = "",
    // fuelType stored as String: "PETROL" | "DIESEL" | "HYBRID" | "ELECTRIC" | "LPG"
    val fuelType: String,
    val tankCapacityLiters: Float?,       // null for electric
    val batteryCapacityKwh: Float?,       // null for non-electric
    val defaultConsumption: Float?,       // L/100km or kWh/100km, user's estimate
    val isDefault: Boolean = false,
    val isDeleted: Boolean = false,       // soft delete; synced to Firestore before removal
    val createdAt: Long,
    val syncedAt: Long? = null
)

// FuelLogEntity.kt
@Entity(tableName = "fuel_logs")
data class FuelLogEntity(
    @PrimaryKey val id: String,           // UUID
    val userId: String,
    val tripId: String?,                  // nullable — standalone log or linked to trip
    val carId: String?,
    val timestamp: Long,
    val liters: Float?,                   // null for electric logs
    val pricePerLiter: Float?,
    val kwhCharged: Float?,               // null for fuel logs
    val pricePerKwh: Float?,
    val totalCost: Float,
    val odometerKm: Float?,
    val syncedAt: Long? = null
)
```

### Domain Models (mapped from entities in repositories)

```kotlin
// Segment.kt
data class Segment(
    val tripId: String,
    val segmentIndex: Int,
    val roadKey: String,
    val roadName: String,
    val distanceMeters: Float,
    val durationMs: Long,
    val avgSpeedKph: Float,
    val maxSpeedKph: Float
)

// SegmentComparison.kt
data class SegmentComparison(
    val roadKey: String,
    val roadName: String,
    val tripADurationMs: Long?,
    val tripBDurationMs: Long?,
    val bestEverMs: Long?,                // min across ALL trips for this roadKey
    val deltaMs: Long?,                   // tripA - tripB; negative = A was faster
    val winner: ComparisonWinner          // enum: A | B | TIE
)

// TrackingState.kt
data class TrackingState(
    val isTracking: Boolean = false,
    val currentLocation: android.location.Location? = null,
    val elapsedMs: Long = 0L,
    val distanceMeters: Float = 0f,
    val currentSpeedKph: Float = 0f,
    val currentRoadName: String? = null,
    val currentSegmentElapsedMs: Long = 0L,
    val bestSegmentMs: Long? = null,      // best known time for current roadKey
    val arrivalStatus: ArrivalStatus = ArrivalStatus.EN_ROUTE
)

enum class ArrivalStatus { EN_ROUTE, APPROACHING, ARRIVED }
```

### Firestore Structure

```
/users/{userId}/
    trips/{tripId}        → TripEntity fields (no route points — too large)
    segments/{segmentId}  → SegmentEntity fields
    places/{placeId}      → PlaceEntity fields
    cars/{carId}          → CarEntity fields
    fuel_logs/{logId}     → FuelLogEntity fields
```

**Route points are stored in Room only for MVP.** They are never synced to Firestore (too large,
not needed on other devices). Future: compress and upload to Firebase Storage per trip.

**Firestore Security Rules:**
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId}/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

---

## Design System

> **Source of truth for all UI.** The design was produced in Claude Design (project
> "DriveDelta Live Tracking") and imported into `/design/`. It outputs web code (HTML/CSS/React),
> NOT Compose — it is a visual + token reference, and Claude Code implements the actual Compose UI
> from it. Do not invent colors, spacing, or type — use these tokens.

### Design assets location

Read them in this order. Each answers a different question:

| Read | For | Cost |
|---|---|---|
| **`design/tokens.md`** | Every exact value: colors, type scale, spacing, radii, shadows, and per-component specs (§7). **This is authoritative** — and the values are confirmed against the rendered PNGs. | cheap |
| `design/mockups/<screen>.png` | Layout, proportion, hierarchy, density — what a token table can't encode. 14 full-res exports (see the tree above); view the one for the screen you're building. | cheap |
| `design/mockups/<screen>.html` | Last resort, to settle what neither answers. | expensive |

**Do not implement from the raw `.dc.html`.** Three reasons, all verified:

1. **They don't render.** `support.js` needs `window.React`/`window.ReactDOM`; no mockup loads React.
   Opening one throws `dc-runtime: window.React is not available yet`. Claude Design injects it.
2. **Six of eight are React-templated** (`data-dc-script` + `<sc-if>` + `{{ }}`). The static markup
   contains *every* conditional branch, not the one that renders, and the `hint-placeholder-val`
   attribute is **not** the real default. `auth.html` carries four logo variants: the placeholder
   hint says `isClassic`, while `data-props` declares the default is `"apex"`. Trust `data-props`.
3. **They're mostly SVG noise.** `ride-moments.html` is 345 lines, 258 inline styles, 58 hand-drawn
   SVG paths. Reading all eight costs ~50k tokens to recover values already extracted in `tokens.md`.

`design/frames/android-frame.jsx` is a generic Material 3 device-chrome scaffold (teal `#006a60`).
It is **not** DriveDelta brand. Ignore it.

### Scope: dark theme only

The POC ships **dark only**. All eight screens are authored dark-first; light variants exist only
for the HUD and the pre-ride sheet, and are out of scope. Build a single dark `ColorScheme`; do not
wire a light scheme or a `isSystemInDarkTheme()` branch. The light table below is recorded for
future reference, not for implementation.

The two color collisions are **accepted for now**: Diesel's badge equals `primary` (`#5B8DEF`), and
Electric's equals `success`/`deltaFaster` (`#37D67A`). Consequence: a fuel-type badge must always
carry its text label and never signal by color alone.

### Brand & aesthetic direction
DriveDelta blends **motorsport telemetry** (precision, data density, split-timing, the "purple
sector" fastest-time motif) with **everyday-driving calm** (clean, legible, not aggressive). The
signature screen is the Live Tracking HUD. Think race-engineer dashboard, not arcade racer.

### Design tokens (extracted from Claude Design project "DriveDelta Live Tracking")

Source mockups live in `/design/mockups/`; the full extraction lives in `/design/tokens.md`.
The mockups define no CSS custom properties — every value below was read from literal inline
styles across the eight screens and cross-checked for consistency. `Color.kt`, `Type.kt`, and
`Theme.kt` must match these exactly.

> **Dark is the designed theme.** All eight screens are authored dark-first. Light values exist
> only for the Live Tracking HUD and the pre-ride sheet. Values marked **†** were derived by
> extension, not designed — confirm them against a light mockup before shipping a light theme.

**Colors — Dark theme** (the designed default)
| Token | Hex | Usage |
|---|---|---|
| `primary` | `#5B8DEF` | Primary actions, active states, route polyline |
| `onPrimary` | `#FFFFFF` | Text/icons on primary |
| `secondary` | `#82A8F4` | Accents, chips, link hover |
| `background` | `#0A0B0D` | Screen background (near-black) |
| `surface` | `#14161A` | Cards, inputs |
| `onSurface` | `#F2F4F7` | Primary text |
| `onSurfaceVariant` | `#8A9099` | Secondary text |
| `outline` | `#FFFFFF` @ 8% | Borders, dividers (`rgba(255,255,255,0.08)`) |
| `error` | `#FF556A` | Errors, "slower than best" delta, destructive |
| `success` | `#37D67A` | "Faster than best" delta (green) |
| `deltaSlower` | `#FF556A` | HUD delta positive (red family) |
| `deltaFaster` | `#37D67A` | HUD delta negative (green family) |
| `purpleSector` | `#B388FF` | Fastest-ever segment highlight (motorsport purple) |

Additional dark surfaces (needed for sheets, nav, segmented control):
`surfaceSheet #101216` · `surfaceVariant #16181C` · `surfaceElevated #1B1E24` ·
`navBackground #0C0E11` · `segmentActive #23262C` · `mapBase #0A0B0D` · `mapRoads #15171B`
Additional dark text ramp: `#E8EAED` (bright) → `#8A9099` → `#7E858F` → `#6B7178` (dim)
Purple-sector row: text `#C8B3FF`, muted `#8F83B0`, bg `rgba(179,136,255,0.09)`, border `rgba(179,136,255,0.25)`

**Colors — Light theme** (HUD + pre-ride sheet only; rest derived)
| Token | Hex | Usage |
|---|---|---|
| `primary` | `#2F6BE0` | Primary actions, active states |
| `onPrimary` | `#FFFFFF` | Text/icons on primary |
| `secondary` | `#E9EDF1` † | Accents, chips |
| `background` | `#FFFFFF` | Screen background |
| `surface` | `#F4F6F8` | Cards, sheets |
| `onSurface` | `#14181D` | Primary text |
| `onSurfaceVariant` | `#6B727B` | Secondary text |
| `outline` | `#000000` @ 6% | Borders, dividers (`rgba(0,0,0,0.06)`) |
| `error` | `#D92D4A` | Errors, "slower than best" delta |
| `success` | `#0E9F55` | "Faster than best" delta (green) |
| `deltaSlower` | `#D92D4A` | HUD delta positive (red family) |
| `deltaFaster` | `#0E9F55` | HUD delta negative (green family) |
| `purpleSector` | `#7C4DFF` | Fastest-ever segment highlight |

Light extras: `placeholder #98A0AA` · `mapBase #E7ECF1` · `mapRoads #FFFFFF`

**Fuel-type badge colors** (referenced in Cars feature F2)
| Type | Hex |
|---|---|
| Electric | `#37D67A` (green) |
| Diesel | `#5B8DEF` (blue) |
| Petrol | `#F0913E` (orange) |
| Hybrid | `#F2C94C` (yellow) |
| LPG | `#8A9099` (grey) |

Badge composite: text = badge colour, bg = colour @ 12%, border = colour @ 30%, radius 10dp,
padding 6×10dp, 12sp/600.

**Typography** — two families, both OFL-licensed, loaded from Google Fonts in the mockups.
Add `Geist` (400/500/600/700) and `Geist Mono` (400/500/600) to `res/font/`.

| Role | Font family | Size (sp) | Weight | Usage |
|---|---|---|---|---|
| `displayLarge` | `Geist Mono` | `88` | `600` | HUD speed readout (ls −3sp, lh 0.82) |
| `displayMedium` | `Geist Mono` | `34` | `600` | HUD segment time (ls −1sp) |
| `headlineMedium` | `Geist` | `24` | `600` | Screen titles, greeting (ls −0.4sp) |
| `titleLarge` | `Geist` | `18` | `600` | App-bar titles (ls −0.3sp) |
| `titleMedium` | `Geist` | `15` | `500` | Card titles, road names |
| `bodyLarge` | `Geist` | `16` | `400` | Primary body text, field values |
| `bodyMedium` | `Geist` | `14` | `400` | Secondary text |
| `labelLarge` | `Geist` | `15` | `600` | Buttons, chips |
| `labelSmall` | `Geist` | `11` | `600` | Eyebrows/section labels (ls +1.5sp, uppercase) |
| `numericMono` | `Geist Mono` (monospace) | `16` | `500` | Split times / deltas (tabular figures) |

> `numericMono` and every `Geist Mono` role must set `FontFeatureSetting("tnum")` so digits don't
> jitter as they update. HUD delta value is 21sp/600; the "best 0:39.0" caption is 10sp.
> Delta glyphs are literal characters: `▾` faster, `▴` slower, `★` personal best, `−` (U+2212) minus.

**Spacing scale** (Compose `dp`)
| Token | dp |
|---|---|
| `spaceXs` | `4` |
| `spaceSm` | `8` |
| `spaceMd` | `12` |
| `spaceLg` | `16` |
| `spaceXl` | `24` |

Screen horizontal padding is `20dp`; card padding is `18dp` vertical × `20dp` horizontal.

**Shape / corner radius**
| Token | dp | Usage |
|---|---|---|
| `radiusSm` | `10` | Chips, badges, active segment |
| `radiusInput` | `14` | Text fields, segmented-control container |
| `radiusMd` | `16` | Buttons, toggle rows |
| `radiusCard` | `22` | Trip/car/place cards |
| `radiusLg` | `28` | Bottom sheets (top corners only) |
| `radiusHudPanel` | `30` | HUD telemetry overlay |

**Elevation** — the design uses hairline borders + large soft shadows rather than Material tint.
| Token | dp | Notes |
|---|---|---|
| `elevationCard` | `0` | Flat; 1dp `outline` border instead of a shadow |
| `elevationSheet` | `16` | Approximates `0 -14dp 40dp rgba(0,0,0,0.5)` |

Primary buttons carry a coloured glow — `0 12dp 28dp rgba(91,141,239,0.30)` — which Compose
`elevation` cannot express. Use `Modifier.shadow(spotColor = primary, ambientColor = primary)`.
Glass surfaces (HUD panel, map controls) use `backdrop-filter: blur(18–30px)`; on Android use
a `RenderEffect.createBlurEffect` haze or a flat `surface @ 74–82%` alpha fallback.

### Core screens to design in Checkpoint 0

Design these first — they define the visual language; remaining screens reuse the same components:

1. **Auth screen** — logo, tagline, single Google Sign-In button. Sets brand tone.
2. **Dashboard** — Start Ride CTA, recent trips cards, personal bests, weekly stats. Sets card/layout language.
3. **Live Tracking HUD** — the signature screen. Map background + overlay showing speed, elapsed time,
   distance, current road, segment split vs best, delta (colored). Design light + dark; dark is default.
4. **Trip Detail** — tabbed (Map / Splits / Replay). Sets the split-table and data-viz language.
5. **Car edit** — form with fuel-type segmented control + conditional fields. Sets form language.
6. **Place edit** — map with draggable marker, radius slider, emoji picker. Sets map-interaction language.

### Component styles to define (reused everywhere)
- Primary / secondary / destructive buttons
- Cards (trip card, car card, place card)
- Chips (filter chips, nearby-place suggestion chip, "Default" car chip)
- Fuel-type badge (5 color variants)
- Bottom sheets (stop-confirm, arrival, pre-ride setup)
- Split row (road name + time + best + delta with color) — the core data component
- HUD overlay panel (semi-transparent, dark)
- Segmented control (fuel type selector)
- Text fields (with label, error state)
- Empty states (no trips yet, no cars yet, no places yet)

---

## Feature Specifications

### F1 — Authentication

- `AuthScreen`: single Google Sign-In button. Loading state while Firebase resolves. Error snackbar on failure.
- On success → navigate to `DashboardScreen`; replace back stack so Back does not return to auth.
- On cold launch: if `FirebaseAuth.currentUser != null` → go directly to Dashboard.
- Sign-out: clears Room DB rows for current `userId`, calls `FirebaseAuth.signOut()`, navigates to AuthScreen.
- Every DAO query and every repository method filters by `WHERE userId = currentUserId`. Never expose another user's data.

---

### F2 — Cars (CRUD)

**CarsScreen:**
- `LazyColumn` of car cards showing: name, license plate, fuel type badge (colour-coded: 🟢 Electric / 🔵 Diesel / 🟠 Petrol / 🟡 Hybrid / ⚪ LPG), estimated consumption, "Default" chip.
- `SwipeToDismiss` → soft delete with undo `Snackbar` (sets `isDeleted = true`, syncs deletion to Firestore, then hard-deletes from Room after sync).
- FAB → `CarEditScreen`

**CarEditScreen:**
- Name (required), License plate (optional).
- Fuel type: `SegmentedButton` row: Petrol / Diesel / Hybrid / Electric / LPG.
- Conditional fields based on fuel type:
  - All fuel types: Tank capacity (L), Estimated consumption (L/100km).
  - Electric only: Battery capacity (kWh), Estimated consumption (kWh/100km).
- "Set as default vehicle" toggle. Only one car can be default at a time — saving with toggle ON clears `isDefault` on all other cars.
- Save → `SaveCarUseCase` → Room upsert → mark `syncedAt = null`.

---

### F3 — Places (CRUD)

**PlacesScreen:**
- `LazyColumn` of place cards: emoji, name, address, radius badge.
- `SwipeToDismiss` → delete with confirmation `AlertDialog`.
- FAB → `PlaceEditScreen`

**PlaceEditScreen:**
- Name field (required).
- Emoji picker: horizontal `LazyRow` with ~20 options: 🏠 🏢 🏋️ ⛽ 🛒 🏖️ 🏫 🏥 ⚽ 🎯 🍕 🏨 🚉 ✈️ 🏕️ 🏪 🎭 🎮 🌳 🏟️
- **Address search bar** using `Places SDK Autocomplete`. Selecting a result:
  1. Moves the map camera to the result's `LatLng`
  2. Positions the draggable marker there
  3. Fills the address label field
- **GoogleMap Composable** with a single draggable `Marker`. When the user finishes dragging:
  1. Trigger reverse geocode (Geocoding API, debounced 1 second)
  2. Update address label field with result
- **"Use my current location"** button → requests location permission if needed, snaps marker to device GPS position, reverse geocodes.
- **Radius slider** (50m → 500m, step 25m). As slider moves, update a semi-transparent `Circle` overlay on the map in real time.
- Save → `SavePlaceUseCase` → Room upsert → mark `syncedAt = null`.

---

### F4 — Tracking Foreground Service

**File:** `TrackingForegroundService.kt`

- `startForeground()` called immediately on `onCreate()` with a persistent notification showing: elapsed time | current speed | distance so far. Notification updates every 5 seconds.
- Location config via `FusedLocationProviderClient`:
  ```kotlin
  LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
      .setMinUpdateDistanceMeters(5f)
      .setMinUpdateIntervalMillis(1000L)
      .build()
  ```
- **Accuracy filter:** discard any `Location` where `!hasAccuracy() || accuracy > 25f`.
- **Warm-up period:** discard all points for the first 10 seconds after tracking starts to let GPS settle.
- **GPS gap interpolation:** if no valid location arrives for > 8 seconds:
  - Compute interpolated points using last known `lat`, `lng`, `bearing`, and `speedMps`.
  - Mark as `isInterpolated = true`.
  - Cap interpolation at 30 seconds. If gap exceeds 30 seconds, insert a single gap-marker point and wait for next valid fix.
- **In-memory buffer:** collect `RoutePointEntity` in a `mutableListOf`. Batch-insert to Room every 30 seconds via a coroutine on the IO dispatcher.
- **StateFlow:** expose `StateFlow<TrackingState>` accessible from `TrackingViewModel` via service binding.
- **Arrival detection:** on each valid location update, call `DetectArrivalUseCase.onLocationUpdate()`. If result is `ARRIVED`, emit `arrivalStatus = ARRIVED` in `TrackingState`. Reset on service start.
- **Service stop sequence:**
  1. Flush remaining buffer to Room.
  2. Set `endTime`, `endLat`, `endLng`, `distanceMeters`, `durationMs` on the `TripEntity`.
  3. Update Room.
  4. Launch background coroutine: `SnapRouteToRoadsUseCase` → `BuildSegmentsUseCase` → queue Firestore sync.
  5. Call `stopForeground(STOP_FOREGROUND_REMOVE)`.

**Required Manifest entries:**
```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

<service
    android:name=".service.TrackingForegroundService"
    android:foregroundServiceType="location"
    android:exported="false" />
```

---

### F5 — Trip Start Flow

**Pre-ride bottom sheet** (triggered from Dashboard "Start Ride" button):

1. **Car selector** — `ExposedDropdownMenuBox` listing all non-deleted cars. Pre-selects the car with `isDefault = true`.
2. **Origin Place** — optional dropdown. On sheet open, `DetectNearbyPlaceUseCase` checks if current GPS is within any saved Place's radius. If yes, show a suggestion chip: "📍 Near [Place name] — use as origin?" Tapping the chip sets origin to that place.
3. **Destination Place** — optional dropdown. If set, enables geofence auto-stop during the ride.
4. **"Start Ride"** button:
   - Validate at least one car is available (if no cars → show prompt to add one first).
   - Call `StartTripUseCase`:
     - Generate trip UUID.
     - Snapshot current GPS as `startLat/startLng`.
     - Persist `TripEntity` to Room immediately.
     - If destination selected: pass `destination` to service for arrival monitoring.
     - Load best-ever segment times for this route (by matching `routeHash` from previous trips) into `TrackingViewModel` for live split display.
   - Start + bind `TrackingForegroundService`.
   - Navigate to `TrackingScreen`.

---

### F6 — Trip End Flow

**A) Manual stop:**
- User taps the STOP button in `TrackingScreen`.
- Show `StopConfirmSheet` (Modal `BottomSheet`):
  - Title: "Finish this ride?"
  - Stats row: elapsed time | distance | avg speed so far
  - **"Finish Ride"** button (primary, red) → calls `StopTripUseCase(trigger = "MANUAL")`
  - **"Keep Going"** button (secondary) → dismisses sheet, ride continues

**B) Automatic arrival (geofence):**
- `DetectArrivalUseCase`:
  ```kotlin
  class DetectArrivalUseCase @Inject constructor() {
      private var consecutiveTicks = 0
      private val requiredTicks = 5        // 5 × 2s interval = 10 continuous seconds inside radius

      fun onLocationUpdate(location: Location, destination: Place): ArrivalStatus {
          val distance = location.distanceTo(destination.toAndroidLocation())
          return if (distance <= destination.radiusMeters) {
              consecutiveTicks++
              if (consecutiveTicks >= requiredTicks) ArrivalStatus.ARRIVED
              else ArrivalStatus.APPROACHING
          } else {
              consecutiveTicks = 0
              ArrivalStatus.EN_ROUTE
          }
      }

      fun reset() { consecutiveTicks = 0 }
  }
  ```
- When `TrackingState.arrivalStatus == ARRIVED`, `TrackingScreen` shows `ArrivalSheet`:
  - Title: "You've arrived at [Destination Place name] 🎉"
  - Subtitle: countdown "Auto-finishing in 30s..." (animated)
  - **"Finish Ride"** → `StopTripUseCase(trigger = "GEOFENCE")`
  - **"I'm just passing"** → dismisses sheet, resets `DetectArrivalUseCase`, ride continues
  - After 30s with no interaction → auto-confirms (calls `StopTripUseCase`)

---

### F7 — Roads API: Snap-to-Road & Segment Building

**SnapRouteToRoadsUseCase.kt** (runs post-ride on IO dispatcher):

1. Load all `RoutePointEntity` for the trip from Room, ordered by `timestamp`.
2. Filter to non-interpolated points. Apply Ramer–Douglas–Peucker simplification (epsilon = 10m) to thin the list.
3. Split into chunks of max 100 points with 10-point overlap (Roads API hard limit: 100 pts/request).
4. For each chunk: call `RoadsApiService.snapToRoads(points, interpolate = true)`.
5. Stitch chunks: remove duplicated overlap points from stitched result using point index.
6. Store snapped `lat/lng` back as updated `RoutePointEntity` rows in Room (in-place update).

**BuildSegmentsUseCase.kt** (runs after snap, on IO dispatcher):

1. Load snapped `RoutePointEntity` rows ordered by timestamp.
2. Each snapped point from Roads API carries a `placeId` (the road's Google Place ID). Walk points and group consecutive points with the same `placeId` into a segment.
3. On `placeId` change → segment boundary. Compute per-segment:
   - `roadName`: from Roads API response metadata (or fall back to a Geocoding API call on the midpoint if name not available).
   - `roadKey`: `"${roadName}|${startLat4dp},${startLng4dp}|${endLat4dp},${endLng4dp}"` — coordinates rounded to 4 decimal places (~11m precision). Stable across trips for the same road stretch.
   - `durationMs`, `distanceMeters`, `avgSpeedMps`, `maxSpeedMps`.
4. Compute `routeHash` = SHA-256 of the full ordered sequence of `roadKey` strings joined by `|`. Store in `TripEntity`.
5. Batch-insert all `SegmentEntity` rows to Room.
6. Mark `TripEntity.roadsProcessed = true`.
7. Queue Firestore sync for trip + all its segments.

**Fallback if Roads API fails:**
- Log the error and set `roadsProcessed = false`.
- Fall back to fixed 500m distance-based segmentation using raw GPS points.
- Use `"RAW|${startLat4dp},${startLng4dp}|${endLat4dp},${endLng4dp}"` as `roadKey` and `"Unknown road"` as `roadName`.
- Trips processed with fallback can be reprocessed later if Roads API becomes available.

---

### F8 — Segment Matching & Comparison

**MatchSegmentsUseCase.kt:**
- Two trips share the same route if `routeHash` is identical.
- Partial match (same roads, different start/end): trips that share ≥ 70% of their `roadKey` sequence via sliding window comparison. Used to surface "best time on A1 Norte" even across trips with different starting places.

**CompareSegmentsUseCase.kt:**
- Given two Trip IDs, load both `SegmentEntity` lists from Room.
- Align by `roadKey` (ordered). Segments present in one trip but absent in the other → `null` for the missing trip.
- For each aligned pair, look up `bestEverMs` by querying `MIN(durationMs) FROM segments WHERE roadKey = ? AND tripId IN (all user trips)`.
- Produce `List<SegmentComparison>`.

**Live split comparison during a ride:**
- At trip start, `StartTripUseCase` queries Room for the best `durationMs` per `roadKey` for the estimated route (based on origin/destination Place pair from previous trips with matching `routeHash`).
- Loads result into `TrackingViewModel` as a `Map<String, Long>` (roadKey → bestMs).
- As service emits `currentRoadName` changes, `TrackingViewModel` looks up `bestSegmentMs` from the map and includes it in the `TrackingState` stream.

---

### F9 — Live Tracking Screen

**TrackingScreen.kt** + **TrackingViewModel.kt**

- Binds to `TrackingForegroundService` via `ServiceConnection` in ViewModel.
- Observes `TrackingState` as a `StateFlow`, collected with `collectAsStateWithLifecycle()`.

**Map layer (full screen):**
- `GoogleMap` Composable.
- `Polyline` that grows as new `RoutePoint` objects are added.
- Camera follows user with bearing, debounced to every 3 seconds (avoid jitter).

**HUD overlay (top of screen, semi-transparent dark card):**
```
┌──────────────────────────────────────────────┐
│  ⏱ 00:14:32               📍 12.4 km        │
│  🚗 87 km/h                                  │
├──────────────────────────────────────────────┤
│  A1 - Autoestrada do Norte                   │
│  Segment:  01:23 elapsed   Best: 01:15       │
│  Delta:    +00:08  🔴 (slower than best)     │
└──────────────────────────────────────────────┘
```
- Delta shown green (negative = faster than best), red (positive = slower), grey (no best known).

**Destination chip** (if destination Place set):
- "🏁 Home — 8.2 km remaining" shown below HUD.
- Distance remaining updates every location update.

**STOP button** (bottom centre, red `FilledButton`):
- Tapping shows `StopConfirmSheet`.

**ArrivalSheet** appears automatically when `arrivalStatus == ARRIVED`.

---

### F10 — Trip Detail Screen

**TripDetailScreen.kt** — three tabs using `TabRow` + `HorizontalPager`:

**Tab 1 — Map:**
- Full-screen `GoogleMap` with the complete trip polyline, coloured per segment by `avgSpeedKph` relative to the trip's max speed. Green = fastest, red = slowest.
- `Marker` at start and end with Place emoji (if linked) or default pin.
- Segment boundary markers. Tapping a marker scrolls the Splits tab to that segment.

**Tab 2 — Splits:**
- Summary header row: Total time | Best total time | Delta.
- `LazyColumn` of `SegmentSplitRow` composables:
  ```
  [index] [Road name]             [time]    [best]   [delta]
    1     A1 Norte                01:23     01:15    +00:08 🔴
    2     IC19 Lisboa             03:41     03:55    -00:14 🟢
  ```
- Filter toggle: "vs. best run ever" / "vs. previous run on this route".

**Tab 3 — Replay:**
- `Slider` scrubber (0% → 100% of trip duration).
- Play / Pause / 2× speed `IconButton` controls.
- A `Marker` animates along the stored polyline, jumping to the correct `RoutePoint` for the current scrubber position.
- HUD shows simulated speed and current road name at replay position.
- Playback uses a coroutine that emits position updates at real-time speed (or 2× speed), updating state via `StateFlow`.

**First-open bottom sheet (post-ride only):**
- "Add a fill-up for this ride?" with car name.
- "Add fill-up" button → navigates to `FuelLogScreen` with `tripId` pre-filled.
- "Skip" dismisses permanently for this trip (store a flag in `TripEntity.notes` or a separate Room field).

---

### F11 — History Screen

**HistoryScreen.kt:**
- `LazyColumn` grouped by month header.
- Each trip card: date/time | Origin → Destination (Place names or "Unknown" if no places) | duration | distance | car name | fuel efficiency if a `FuelLog` is linked.
- Tap → `TripDetailScreen`.
- Filter chips (horizontal `LazyRow` above list): by car | by place pair | date range (`DateRangePicker`).
- Long-press → delete with `AlertDialog` confirmation → soft delete + Firestore sync + Room removal.

---

### F12 — Fuel / Energy Log

**FuelLogScreen.kt:**
- Car selector (required, `ExposedDropdownMenuBox`).
- Form adapts based on selected car's `fuelType`:
  - **Fuel (Petrol/Diesel/Hybrid/LPG):** Litres filled (`FloatField`), Price per litre → auto-calculates total.
  - **Electric:** kWh charged, Price per kWh → auto-calculates total.
- Total cost field (editable, overrides auto-calculation if user types directly).
- Odometer (optional `IntField`).
- Trip link (optional `ExposedDropdownMenuBox` showing recent trips for selected car).
- Date/time picker (defaults to now, `DatePickerDialog` + `TimePickerDialog`).
- Save → `LogFuelUseCase` → Room → mark `syncedAt = null`.

**Post-save stats (shown inline below form):**
- "This fill-up: €XX.XX"
- If trip linked and distanceMeters > 0: "X.X L/100km" or "X.X kWh/100km"
- "All-time average for [car name]: X.X L/100km" (queried from Room across all logs for that car)

---

### F13 — Dashboard

**DashboardScreen.kt:**
- Top: **"Start Ride"** `ExtendedFloatingActionButton` — opens pre-ride bottom sheet.
- Nearby place banner (conditional): "📍 You're near Home — set as origin?"
- **Recent rides** section: last 3 trip cards.
- **Personal bests** section: top 3 most-driven routes (by `routeHash`) with best total time.
- **Weekly summary** card: total km | total drive time | total fuel cost (current calendar week).

---

### F14 — Offline-First Sync

**Rule: Room is always the source of truth. Firestore is a remote mirror.**

**Write path:**
1. All writes go to Room first.
2. New/modified records have `syncedAt = null`.
3. `SyncWorker` (WorkManager, `PeriodicWorkRequest`, 15-minute interval, requires `NetworkType.CONNECTED`) calls `SyncManager.pushPending()`.
4. `pushPending()` queries all tables for `syncedAt IS NULL`, pushes each to Firestore, sets `syncedAt = now()` on success.

**Initial pull (new device / fresh install):**
1. After sign-in, `SyncManager.pullAll(userId)` fetches all Firestore subcollections.
2. Merges into Room via `INSERT OR REPLACE` (keyed on UUID primary key).
3. Uses `syncedAt` as last-write-wins conflict resolution (rare with single-user app).

**Note:** `RoutePointEntity` rows are NOT synced to Firestore. They are local-only for MVP.

---

## Gradle Dependencies (app/build.gradle.kts)

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.kotlin.serialization)
}

android {
    compileSdk = 35
    defaultConfig {
        applicationId = "app.drivedelta"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        manifestPlaceholders["MAPS_API_KEY"] = providers.gradleProperty("MAPS_API_KEY").orElse("").get()
        buildConfigField("String", "ROADS_API_KEY", "\"${providers.gradleProperty("ROADS_API_KEY").orElse("").get()}\"")
        buildConfigField("String", "PLACES_API_KEY", "\"${providers.gradleProperty("PLACES_API_KEY").orElse("").get()}\"")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")

    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // Location
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Maps Compose
    implementation("com.google.maps.android:maps-compose:4.3.3")
    implementation("com.google.android.gms:play-services-maps:19.0.0")

    // Places (address search)
    implementation("com.google.android.libraries.places:places:3.5.0")

    // Retrofit (Roads API + Geocoding API)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Charts (Compose-native)
    implementation("com.patrykandpatrick.vico:compose-m3:2.0.0-beta.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("app.cash.turbine:turbine:1.1.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

---

## Permissions Flow (MainActivity)

Request in strict order; never batch all at once (Android rejects this):
1. `ACCESS_FINE_LOCATION` — show rationale: "To record your ride route."
2. `ACCESS_BACKGROUND_LOCATION` (Android 10+, must be a separate request after fine location is granted) — show rationale: "To keep tracking while you use navigation apps."
3. `POST_NOTIFICATIONS` (Android 13+) — show rationale: "To show the active ride notification."
4. Prompt to disable battery optimisation: `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — show rationale: "To prevent Android from pausing tracking on long drives."
5. Any permanently denied permission → show dialog linking to system app settings (`ACTION_APPLICATION_DETAILS_SETTINGS`).

---

## API & Firebase Setup Instructions

**Google Cloud Console:**
1. Create a project.
2. Enable: Maps SDK for Android, Roads API, Places API (New), Geocoding API.
3. Create an API key. Restrict it to Android apps (your package + SHA-1).
4. Add key to `local.properties` (gitignored):
   ```
   MAPS_API_KEY=AIza...
   ROADS_API_KEY=AIza...   # can be same key if all APIs enabled
   PLACES_API_KEY=AIza...
   ```

**Firebase Console:**
1. Create Firebase project linked to the same GCP project.
2. Enable Authentication → Google Sign-In provider.
3. Enable Firestore → start in production mode.
4. Deploy Security Rules as specified above.
5. Download `google-services.json` → place in `app/` directory.

**AndroidManifest.xml Maps key reference:**
```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="${MAPS_API_KEY}" />
```

---

## MVP Build Checkpoints

Implement in strict order. Each checkpoint must compile, run, and pass its test criteria
before moving to the next. Do not start checkpoint N+1 until checkpoint N is verified.

---

### ✅ CHECKPOINT 0 — Design (done by YOU in Claude Design, before any code)

**Goal:** Lock the high-fi visual design and extract a concrete design-token set, so every
subsequent checkpoint implements against approved designs instead of inventing UI.

> Design happens in Claude Design, not in Claude Code. Claude Code's involvement is importing the
> project, extracting tokens, and later translating them into Compose theme files (Checkpoint 1).
> Claude Design emits web code, not Compose — its output is a visual reference + tokens, never
> importable code.

- [x] Read the "Design System" section above for the aesthetic direction and the list of core screens.
- [x] Paste the design brief (`DESIGN_BRIEF.md`) into Claude Design as the opening prompt.
- [x] Design the core screens in high fidelity (**dark only** — light is out of scope for the POC):
  - [x] Auth screen
  - [x] Dashboard
  - [x] Live Tracking HUD (signature screen — spend the most time here)
  - [x] Trip Detail (Map / Splits / Replay tabs)
  - [x] Car edit (segmented control + conditional fields)
  - [x] Place edit (map + draggable marker + radius slider + emoji picker)
  - [x] Ride Moments (bonus — pre-ride/stop/arrival sheets, acquiring-GPS, 3 empty states)
- [x] Design the reused component styles listed under "Component styles to define".
- [x] Import the Claude Design project into `/design/` via the `claude_design` MCP (`/design-login`).
      Project: `50aaa2d0-469b-4699-aba1-25ae18291f19` — "DriveDelta Live Tracking".
- [x] Extract the design tokens (colors, typography, spacing, radius, elevation) into
      `design/tokens.md` and fill in the tables in the "Design System" section above.
- [x] **Export each screen from Claude Design as PNG → `design/mockups/<screen>.png`.**
      Exported from the Claude Design UI (the `.dc.html` sources can't render offline). 14 PNGs at
      1176×2631: the 6 core screens, both HUD delta states, and 7 Ride Moments states. All verified
      to render the true dark design (`#0A0B0D` canvas, apex logo, correct deltas).
- [x] Commit `/design/` and the updated CLAUDE.md to the repo. (`d199717`, pushed to `origin/main`)
- [x] **Acceptance:** Every core screen exists as a PNG in `/design/mockups/`, `design/tokens.md`
      is complete, and every token table in the Design System section holds real values.

---

### ✅ CHECKPOINT 1 — Project Skeleton, Theme & Auth

**Goal:** App launches, the design system is implemented as a Compose theme, Google Sign-In works,
user is routed to an empty Dashboard.

- [x] Create Android project: package `app.drivedelta`, minSdk 26, Kotlin DSL, Jetpack Compose
- [x] Set up `libs.versions.toml` version catalog with all libraries listed above
- [~] Add `google-services.json`, configure Firebase in `build.gradle.kts` — google-services plugin
      wired; **you must drop `app/google-services.json` in** (gitignored) before the build will sync.
- [x] `DriveDeltaApplication.kt`: `@HiltAndroidApp`, Places SDK init (Firebase auto-inits from json)
- [x] `MainActivity.kt`: single-activity Compose host (splash + edge-to-edge)
- [x] **Implement the design system from Checkpoint 0 as Compose theme files.** Dark only.
  - [x] `Color.kt` — dark tokens + extended surfaces + purple-sector row colors
  - [x] `Type.kt` — type scale on Geist + Geist Mono (vendored to `res/font/`), `tnum` on mono roles
  - [x] `Theme.kt` — single dark `ColorScheme`, `surfaceTint = Transparent`, tokens via
        `LocalDdTokens` / `LocalDdType` CompositionLocals
  - [x] Fuel-type badge colors as named values (`DdFuelElectric` … `DdFuelLpg`)
- [x] `AppNavGraph.kt` + `NavDestinations.kt`: Auth + Dashboard routes (others added later)
- [x] `FirebaseAuthRepository.kt`: `signInWithGoogle()`, `signOut()`, `currentUserId`, `isSignedIn`
- [x] `AuthViewModel.kt` + `AuthScreen.kt`: Google button, loading, error snackbar
- [x] `DashboardScreen.kt` stub: "Dashboard" text + Sign Out button
- [x] Routing logic in `AppNavGraph`: unauthenticated → Auth, authenticated → Dashboard, back stack cleared
- [ ] **Acceptance test (you run in Android Studio):** Cold launch → AuthScreen → sign in →
      DashboardScreen. Kill app → reopen → DashboardScreen directly (no auth prompt). Sign out → AuthScreen.

> **Before this builds** (this dev box has no JDK/SDK — authored, not compiled here):
> 1. `app/google-services.json` from Firebase (package `app.drivedelta`), Google provider enabled.
> 2. Debug keystore SHA-1 registered in Firebase (Google Sign-In needs it).
> 3. Optionally API keys in `local.properties` (see `local.properties.example`) — not needed for auth.

---

### ✅ CHECKPOINT 2 — Room Database & Sync Skeleton

**Goal:** All local data structures exist; sync framework is wired.

- [ ] Implement all 6 Room entities with exact fields from data model section
- [ ] Implement all 6 DAOs:
  - `TripDao`: `insertOrReplace`, `update`, `getByUser: Flow<List<TripEntity>>`, `getById`, `getPendingSync: List<TripEntity>`
  - `RoutePointDao`: `insertAll`, `getByTrip: List<RoutePointEntity>`, `deleteByTrip`
  - `SegmentDao`: `insertAll`, `getByTrip: Flow<List<SegmentEntity>>`, `getBestDurationForRoadKey`
  - `PlaceDao`: `insertOrReplace`, `delete`, `getByUser: Flow<List<PlaceEntity>>`, `getPendingSync`
  - `CarDao`: `insertOrReplace`, `softDelete`, `getByUser: Flow<List<CarEntity>>`, `getDefault`, `getPendingSync`
  - `FuelLogDao`: `insertOrReplace`, `getByUser: Flow<List<FuelLogEntity>>`, `getPendingSync`
- [ ] `AppDatabase.kt`: register all entities, version 1, export schema
- [ ] `DatabaseModule.kt`: Hilt `@Singleton` provision of DB and all DAOs
- [ ] Firestore DTOs (`TripDto`, `SegmentDto`, etc.) with `@Serializable` and mapping functions to/from entities
- [ ] `FirestoreDataSource.kt`: `pushTrip()`, `pushPlace()`, `pushCar()`, `pushFuelLog()`, `pullAll(userId)`
- [ ] `SyncManager.kt`: `pushPending()` loops all tables, `pullAll(userId)` on login
- [ ] `SyncWorker.kt`: WorkManager `CoroutineWorker` calling `SyncManager.pushPending()`; enqueued as `PeriodicWorkRequest` (15 min, `NetworkType.CONNECTED`)
- [ ] Enqueue `SyncWorker` in `DriveDeltaApplication.onCreate()`
- [ ] **Acceptance test:** Manually insert a `PlaceEntity` into Room via a test button. Wait ≤ 15 min or force-run worker. Verify document appears in Firestore console under `/users/{uid}/places/`.

---

### ✅ CHECKPOINT 3 — Cars Feature (Full CRUD)

**Goal:** User can manage their vehicles.

- [ ] `CarRepository` interface + `CarRepositoryImpl` (reads from Room as Flow, writes to Room + sets `syncedAt = null`)
- [ ] `SaveCarUseCase`, `DeleteCarUseCase`, `GetCarsUseCase`
- [ ] `CarsViewModel.kt`: exposes `StateFlow<List<Car>>`, handles save/delete
- [ ] `CarsScreen.kt`: `LazyColumn` with fuel type badge colours, swipe-to-delete + undo `Snackbar`, FAB
- [ ] `CarEditViewModel.kt`: form state, validation, fuel-type-driven conditional field visibility
- [ ] `CarEditScreen.kt`: all fields, `SegmentedButton` for fuel type, conditional fields, default toggle
- [ ] Add Cars to `AppNavGraph` and to bottom navigation bar
- [ ] **Acceptance test:** Add a Petrol car and an Electric car. Edit the Petrol car to be default. Delete the Electric car (confirm undo works). Verify correct data in Firestore after sync.

---

### ✅ CHECKPOINT 4 — Places Feature (Full CRUD)

**Goal:** User can create named places with map picker and address search.

- [ ] `PlaceRepository` interface + `PlaceRepositoryImpl`
- [ ] `SavePlaceUseCase`, `DeletePlaceUseCase`, `GetPlacesUseCase`, `DetectNearbyPlaceUseCase`
- [ ] `PlacesViewModel.kt` + `PlacesScreen.kt`
- [ ] `PlaceEditViewModel.kt`: map marker state, address state, radius state, emoji state, form validation
- [ ] `PlaceEditScreen.kt`:
  - Address search bar using Places SDK `FindAutocompletePredictionsRequest`
  - `GoogleMap` Composable with draggable `MarkerState`
  - "Use my location" button (permission check inline)
  - Radius `Slider` with live `Circle` overlay on map
  - Emoji picker `LazyRow`
  - Reverse geocode on marker move (debounced with `LaunchedEffect` + `delay(1000)`)
- [ ] Add Places to `AppNavGraph` and bottom nav
- [ ] **Acceptance test:** Create "Home" using address search. Create "Office" by dragging the map marker. Verify radius circle renders at 200m. Verify both appear in Firestore. Delete Home, verify removed.

---

### ✅ CHECKPOINT 5 — Background GPS Tracking Service

**Goal:** App reliably records GPS traces in the background.

- [ ] `LocationProvider.kt`: wraps `FusedLocationProviderClient`, returns `Flow<Location>` using `callbackFlow`
- [ ] `GeoUtils.kt`: Haversine distance, linear interpolation between two `LatLng` points with bearing and speed
- [ ] `TrackingForegroundService.kt`: full implementation per F4 spec
- [ ] `DetectArrivalUseCase.kt`: per F6 spec
- [ ] `StartTripUseCase.kt`: creates `TripEntity`, starts service
- [ ] `StopTripUseCase.kt`: stops service, triggers async post-processing
- [ ] `TripRepository` interface + `TripRepositoryImpl`
- [ ] Permission flow in `MainActivity`: sequential requests for fine location, background location, notifications, battery optimisation
- [ ] **Acceptance test:** Grant all permissions. Start a trip from a test button on Dashboard. Switch to Google Maps and drive (or walk) for 5 minutes. Return to app. Verify route points exist in Room with no gap longer than 35 seconds. Verify interpolated points are marked `isInterpolated = true`.

---

### ✅ CHECKPOINT 6 — Live Tracking Screen

**Goal:** User sees the full HUD dashboard while a trip is active.

- [ ] `TrackingViewModel.kt`: binds to `TrackingForegroundService`, collects `TrackingState`, handles stop triggers
- [ ] `TrackingScreen.kt`: `GoogleMap` with live `Polyline`, camera follow logic (debounced), `HudOverlay` composable, STOP button
- [ ] `HudOverlay.kt`: speed, elapsed time, distance, road name, segment time, best time, delta with colour
- [ ] `StopConfirmSheet.kt`: `ModalBottomSheet` with stats + confirm/cancel
- [ ] `ArrivalSheet.kt`: `ModalBottomSheet` with countdown timer (30s auto-confirm using `LaunchedEffect`)
- [ ] Pre-ride bottom sheet: car selector, origin/destination place dropdowns, nearby place suggestion chip
- [ ] Wire Dashboard "Start Ride" FAB → pre-ride sheet → `StartTripUseCase` → `TrackingScreen`
- [ ] **Acceptance test:** Start a trip with "Home" as destination. Drive past Home without entering → sheet does NOT appear. Enter Home's radius and stay 10+ seconds → `ArrivalSheet` appears. Tap "I'm just passing" → continues. Re-enter and wait 30s → auto-confirms and trip ends.

---

### ✅ CHECKPOINT 7 — Roads API Integration & Segment Building

**Goal:** Completed trips are snapped to road geometry and split into named segments.

- [ ] Enable Roads API + Geocoding API in Google Cloud Console
- [ ] `RoadsApiService.kt`: Retrofit interface:
  ```kotlin
  @GET("v1/snapToRoads")
  suspend fun snapToRoads(
      @Query("path") path: String,       // "lat,lng|lat,lng|..."
      @Query("interpolate") interpolate: Boolean = true,
      @Query("key") key: String = BuildConfig.ROADS_API_KEY
  ): RoadsSnapResponse

  @GET("v1/nearestRoads")
  suspend fun nearestRoads(
      @Query("points") points: String,
      @Query("key") key: String = BuildConfig.ROADS_API_KEY
  ): RoadsNearestResponse
  ```
- [ ] `RoadsDto.kt`: `@Serializable` response classes matching Roads API JSON
- [ ] `RoadsDataSource.kt`: chunking (100pt limit), 10-point overlap, stitching
- [ ] `SnapRouteToRoadsUseCase.kt`: loads RoutePoints → RDP simplification → chunks → API calls → update Room
- [ ] `BuildSegmentsUseCase.kt`: group by `placeId` → compute `roadKey` → compute `routeHash` → persist segments → mark `roadsProcessed = true`
- [ ] Graceful fallback: if Roads API returns error, use 500m fixed chunks with `RAW|` prefixed `roadKey`
- [ ] Both use cases triggered from `StopTripUseCase` via `launch(Dispatchers.IO)`
- [ ] **Acceptance test:** Complete a short trip on known roads. Wait for async processing (check `roadsProcessed = true` in Room). Verify `SegmentEntity` rows have correct `roadName` values. Verify `routeHash` is set on `TripEntity`.

---

### ✅ CHECKPOINT 8 — Trip Detail & Comparison Screens

**Goal:** User can view post-ride summary with map, splits, replay and compare two trips.

- [ ] `GetTripDetailUseCase.kt`: loads trip + segments + route points from Room; computes best-ever times per `roadKey`
- [ ] `TripDetailViewModel.kt`: exposes trip detail, tab state, replay state
- [ ] `TripDetailScreen.kt`: `TabRow` + `HorizontalPager` with 3 tabs
- [ ] `RouteMapView.kt`: speed-coloured `Polyline` segments (use `PolylineOptions` with per-segment colour based on `avgSpeedKph` relative to trip max)
- [ ] `SegmentSplitList.kt`: comparison table with filter toggle
- [ ] `ReplayController.kt`: `Slider` scrubber, play/pause/2x controls, animated marker using `LaunchedEffect` coroutine, speed state
- [ ] Post-ride fuel prompt bottom sheet (first-open only, dismissed flag stored in `TripEntity`)
- [ ] `CompareSegmentsUseCase.kt` + `MatchSegmentsUseCase.kt`
- [ ] `CompareViewModel.kt` + `CompareScreen.kt`: trip selectors (filtered by `routeHash`), Vico bar chart (delta per segment), split table
- [ ] "Compare" button in `TripDetailScreen` → `CompareScreen` with current trip pre-selected
- [ ] **Acceptance test:** Drive the same route twice (same origin + destination). Open comparison screen, select both trips. Verify segment table shows correct road names, times, and deltas. Verify bar chart renders.

---

### ✅ CHECKPOINT 9 — History, Fuel Log & Dashboard Polish

**Goal:** All screens complete. Full end-to-end user journey works.

- [ ] `HistoryViewModel.kt` + `HistoryScreen.kt`: grouped by month, filter chips, delete
- [ ] `FuelLogViewModel.kt` + `FuelLogScreen.kt`: adaptive form, post-save stats
- [ ] Polish `DashboardScreen.kt`: recent trips, personal bests, weekly stats (Room queries)
- [ ] Live split in `TrackingViewModel`: load best-ever segment times at trip start (based on previous trips from same origin/destination), stream delta into HUD
- [ ] Sign-out flow: `DELETE FROM trips WHERE userId = ?` (all tables), Firebase sign-out, navigate to AuthScreen
- [ ] i18n: extract all hardcoded strings to `res/values/strings.xml` (English). Create `res/values-pt/strings.xml` with Portuguese translations.
- [ ] Add `ContentDescription` to all interactive Composables for accessibility
- [ ] **Acceptance test (full E2E):** Sign in → create 2 cars (1 electric, 1 petrol) + 2 places → start trip with origin, destination, and car selected → drive route → auto-stop triggers at destination → view TripDetail with all 3 tabs → log fuel → repeat trip → compare both runs → check History filters → sign out → sign back in on same device → all data still present (from Firestore sync).

---

### ✅ CHECKPOINT 10 — Hardening & Play Store Prep

**Goal:** Production-ready internal Play Store release.

- [ ] Handle all permission permanently-denied states: rationale dialogs + settings deep-link for each
- [ ] Handle no-internet state gracefully: all screens load from Room; sync retries automatically when connectivity restored (`NetworkCallback` in `SyncManager`)
- [ ] Handle Roads API quota exceeded: exponential backoff retry (max 3 attempts), fallback to raw segments
- [ ] Handle cold GPS start: show "Acquiring GPS..." state in HUD for first 10 seconds, grey out Start button if no GPS fix
- [ ] ProGuard/R8 rules for Firebase, Retrofit, Hilt, Vico, Kotlin Serialization
- [ ] Add Firebase Crashlytics: `implementation("com.google.firebase:firebase-crashlytics-ktx")`
- [ ] Unit tests for: `BuildSegmentsUseCase`, `CompareSegmentsUseCase`, `DetectArrivalUseCase`, `SnapRouteToRoadsUseCase` (mock API), `SyncManager` (mock Firestore)
- [ ] Configure Play Store internal track: create keystore, set up signing in `build.gradle.kts`, upload AAB
- [ ] **Acceptance test:** Install from Play Store internal track on a **fresh device with no existing data**. Sign in → verify Firestore pull restores all previous data → create a new trip → verify it syncs.

---

## Post-MVP Backlog (do not implement now)

- Android Automotive OS (AAOS manifest, `automotiveApp` XML, rotary nav support, 76dp tap targets)
- Social / sharing features (shared routes, friend segment comparisons, leaderboards)
- OBD-II Bluetooth integration (real-time fuel consumption from car ECU)
- Route point sync to Firebase Storage (compressed binary, enables cross-device replay)
- Export to GPX / CSV
- Widget (home screen quick-start ride button)
- Wear OS companion (current segment split on wrist)
- OSRM self-hosted as zero-cost Roads API fallback
- Automatic trip detection (start tracking when car motion detected, no manual tap needed)
