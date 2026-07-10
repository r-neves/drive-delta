# DriveDelta — Design Tokens

Extracted from the Claude Design project **"DriveDelta Live Tracking"**
(`50aaa2d0-469b-4699-aba1-25ae18291f19`), imported into `/design/mockups/`.

> **How these were derived.** The mockups declare no CSS custom properties. Every value here was
> read from literal inline `style` attributes across all eight screens and cross-checked for
> agreement between them. Where a value appears in only one screen it is called out as such.
>
> **Dark is the designed theme.** Light values exist only for the Live Tracking HUD and the
> pre-ride sheet. Anything marked **†** was derived by extension rather than designed.

---

## 1. Screen inventory

| Mockup | Source doc | Theme |
|---|---|---|
| `mockups/auth.html` | `Auth.dc.html` | dark |
| `mockups/dashboard.html` | `Dashboard.dc.html` | dark |
| `mockups/tracking-hud.html` | `Live Tracking HUD.dc.html` | **dark + light** |
| `mockups/trip-detail.html` | `Trip Detail.dc.html` | dark |
| `mockups/car-edit.html` | `Car Edit.dc.html` | dark |
| `mockups/place-edit.html` | `Place Edit.dc.html` | dark |
| `mockups/ride-moments.html` | `Ride Moments.dc.html` | dark + one light sheet |
| `mockups/app-shell.html` | `DriveDelta App.dc.html` | canvas index |

**None of these files render from disk.** `mockups/support.js` (Claude Design's generated runtime)
requires `window.React` and `window.ReactDOM`, and no mockup loads React from anywhere — the design
app injects it. Opening one in a browser throws `dc-runtime: window.React is not available yet`.
`app-shell.html` additionally resolves its `<dc-import name="Auth">` children by doc name rather
than filename, so it will not find them on disk either.

**Six of the eight docs are React-templated** (`data-dc-script` + `<sc-if>` + `{{ }}` bindings), so
the static markup contains *every* conditional branch, not the one that renders. The markup's
`hint-placeholder-val` is not the real default. Example — `auth.html` carries four logo variants;
the placeholder hint says `isClassic = true` while `data-props` declares the default is `"apex"`.

> **Read this file, not the HTML.** Everything numeric was extracted here already, resolved against
> the real prop defaults. Use the rendered PNGs for layout and hierarchy. Only open the raw `.dc.html`
> to settle a question neither answers — and when you do, check `data-props` before trusting an
> `<sc-if>` branch.

`frames/android-frame.jsx` is a Material 3 device-chrome scaffold. Its `MD_C` palette
(teal `#006a60`, surface `#f4fbf8`, …) is **generic M3 mock chrome and is not DriveDelta brand**.
Ignore it entirely when writing the Compose theme.

---

## 2. Colour

### 2.1 Dark theme — the designed default

**Core roles**

| Token | Hex | Usage |
|---|---|---|
| `primary` | `#5B8DEF` | Primary actions, active nav/tab, route polyline, focus ring |
| `onPrimary` | `#FFFFFF` | Text/icons on primary |
| `secondary` | `#82A8F4` | Link hover, accent chips |
| `background` | `#0A0B0D` | Screen background, map base |
| `surface` | `#14161A` | Cards, text inputs, circular icon buttons |
| `onSurface` | `#F2F4F7` | Primary text, HUD numerals |
| `onSurfaceVariant` | `#8A9099` | Secondary text, field labels |
| `outline` | `rgba(255,255,255,0.08)` | Borders, dividers |
| `error` | `#FF556A` | Errors, destructive text, "slower than best" |
| `success` | `#37D67A` | "Faster than best" |
| `deltaSlower` | `#FF556A` | HUD delta, positive (red family) |
| `deltaFaster` | `#37D67A` | HUD delta, negative (green family) |
| `purpleSector` | `#B388FF` | Fastest-ever segment (motorsport purple) |

**Extended surfaces**

| Name | Hex | Where |
|---|---|---|
| `surfaceSheet` | `#101216` | Bottom sheets |
| `surfaceVariant` | `#16181C` | Emoji tiles, Place Edit inputs, Google button |
| `surfaceElevated` | `#1B1E24` | Car-icon tile, avatar circle |
| `navBackground` | `#0C0E11` | Bottom navigation bar |
| `segmentActive` | `#23262C` | Active segmented-control segment; dimmed replay route |
| `mapRoads` | `#15171B` | Map road strokes |
| `mapBlocks` | `#0E1014` | Map building fills |
| `bezel` | `#2B2D31` | Device frame (mockup chrome only — not app UI) |

**Text ramp** — four steps, brightest to dimmest:
`#F2F4F7` (primary) → `#E8EAED` (bright secondary) → `#8A9099` (secondary) → `#7E858F` (tertiary)
→ `#6B7178` (dim / disabled / inactive nav). Rarely: `#C7CCD3`, `#A7ADB5`, `#3A4048` (disabled numerals).

**Border alphas** — all white overlays: `0.05` (row divider), `0.06` (card, nav top border),
`0.07` (tab underline track, emoji tile), `0.08` (standard card/input), `0.09` (map glass control),
`0.12` (Google button, slider track), `0.18` (sheet drag handle).

**Purple-sector row** (a personal-best split row in Trip Detail):
background `rgba(179,136,255,0.09)` · top border `rgba(179,136,255,0.25)` ·
road name + time `#C8B3FF` @600 · captions `#8F83B0` · `★ PB` glyph `#B388FF` @700.

**Amber** `#F0B24E` — the "Steady" map speed band and the GPS "SEARCHING" pill. Not a brand accent.

### 2.2 Light theme — HUD + pre-ride sheet only

| Token | Hex | Usage |
|---|---|---|
| `primary` | `#2F6BE0` | Primary actions |
| `onPrimary` | `#FFFFFF` | Text/icons on primary |
| `secondary` | `#E9EDF1` † | Chips, accent surfaces |
| `background` | `#FFFFFF` | Screen background |
| `surface` | `#F4F6F8` | Cards, sheets |
| `onSurface` | `#14181D` | Primary text |
| `onSurfaceVariant` | `#6B727B` | Secondary text |
| `outline` | `rgba(0,0,0,0.06)` | Borders, dividers |
| `error` / `deltaSlower` | `#D92D4A` | "Slower than best" |
| `success` / `deltaFaster` | `#0E9F55` | "Faster than best" |
| `purpleSector` | `#7C4DFF` | Fastest-ever segment |
| `placeholder` | `#98A0AA` | Placeholder / tertiary |
| `mapBase` | `#E7ECF1` | Map background |
| `mapRoads` | `#FFFFFF` | Map road strokes |
| `recordingDot` | `#E0263F` | Recording pulse (dark: `#FF4D4D`) |

Every other light-theme role must be derived. Do not guess the light purple-sector row treatment —
it was never drawn.

### 2.3 Fuel-type badges

| Type | Hex | Colour |
|---|---|---|
| Electric | `#37D67A` | green |
| Diesel | `#5B8DEF` | blue |
| Petrol | `#F0913E` | orange |
| Hybrid | `#F2C94C` | yellow |
| LPG | `#8A9099` | grey |

Badge composite (from the Electric badge in Car Edit, the only one drawn as a filled badge):
text = badge colour · background = colour @ 12% · border = 1px colour @ 30% ·
radius `10dp` · padding `6×10dp` · `12sp/600`.

Note Electric reuses `success` and Diesel reuses `primary`. That collision is intentional in the
design but means a fuel badge must never sit directly adjacent to a delta indicator without a label.

---

## 3. Typography

Two families, both OFL-licensed and free. The mockups load them from Google Fonts:

```
https://fonts.googleapis.com/css2?family=Geist:wght@400;500;600;700&family=Geist+Mono:wght@400;500;600&display=swap
```

For the app, bundle them into `res/font/`:
- **Geist** — 400, 500, 600, 700 — all UI text
- **Geist Mono** — 400, 500, 600 (HUD also uses 700) — all numerics

Every mono usage sets `font-variant-numeric: tabular-nums`. In Compose that is
`TextStyle(fontFeatureSettings = "tnum")` — required so digits do not jitter as they tick.

### 3.1 Compose type scale

| Role | Family | Size | Weight | Letter-spacing | Usage |
|---|---|---|---|---|---|
| `displayLarge` | Geist Mono | 88sp | 600 | −3sp | HUD speed (line-height 0.82) |
| `displayMedium` | Geist Mono | 34sp | 600 | −1sp | HUD segment time |
| `headlineMedium` | Geist | 24sp | 600 | −0.4sp | Screen titles, greeting |
| `titleLarge` | Geist | 18sp | 600 | −0.3sp | App-bar titles |
| `titleMedium` | Geist | 15sp | 500 | — | Card titles, road names |
| `bodyLarge` | Geist | 16sp | 400 | — | Body text, field values |
| `bodyMedium` | Geist | 14sp | 400 | — | Secondary text |
| `labelLarge` | Geist | 15sp | 600 | — | Buttons |
| `labelSmall` | Geist | 11sp | 600 | +1.5sp | Uppercase eyebrows / section labels |
| `numericMono` | Geist Mono | 16sp | 500 | — | Split times, deltas |

### 3.2 HUD readouts (exact)

| Element | Family | Size | Weight | Letter-spacing |
|---|---|---|---|---|
| Speed | Geist Mono | 88 | 600 | −3, lh 0.82 |
| "KM/H" unit | Geist | 12 | 600 | +3 |
| Segment time | Geist Mono | 34 | 600 | −1 |
| Delta value | Geist Mono | 21 | 600 | −0.5 |
| Delta caret | Geist Mono | 15 | 600 | — |
| Segment status label | Geist Mono | 11 | 600 | +1.5 |
| "BEST … · SECTOR 3" | Geist Mono | 11 | — | +0.5 |
| Elapsed / Distance | Geist Mono | 24 | 500 | — |
| Section labels | Geist | 11 | 600 | +1.5–2 |
| STOP button | Geist | 15 | 600 | +1 |

### 3.3 Delta glyphs

Literal characters, not icons: `▾` (U+25BE) faster · `▴` (U+25B4) slower · `★` (U+2605) personal
best · `−` (U+2212, true minus) for negative deltas such as `−2.4s`.

Segment status labels read `AHEAD OF BEST` / `BEHIND BEST` / `NEW BEST`.

---

## 4. Spacing

No named scale exists in the mockups. The recurring literals distil to:

| Token | dp |
|---|---|
| `spaceXs` | 4 |
| `spaceSm` | 8 |
| `spaceMd` | 12 |
| `spaceLg` | 16 |
| `spaceXl` | 24 |

Fixed layout constants observed:
- Screen horizontal padding **20dp**
- Card padding **18dp × 20dp**; split row padding **14dp × 20dp**; toggle row **16dp × 18dp**
- App bar padding `6dp 16dp 10–12dp`; status bar height **46dp**
- Text field height **52dp** (Car Edit) / **50dp** (Place Edit); primary button height **54dp**
- Google sign-in button height **56dp**
- Design canvas **392×844dp** inner (414×866 device frame)

Split-table column widths: road name `flex:1` · TIME `70dp` · Δ VS BEST `86dp`.

---

## 5. Shape

| Token | dp | Usage |
|---|---|---|
| `radiusSm` | 10 | Chips, badges, active segmented segment |
| `radiusInput` | 14 | Text fields, segmented-control container, map glass controls |
| `radiusMd` | 16 | Buttons, toggle rows |
| `radiusCard` | 22 | Trip / car / place cards, logo tile |
| `radiusLg` | 28 | Bottom sheets (top corners only: `28,28,0,0`) |
| `radiusHudPanel` | 30 | HUD telemetry overlay |
| — | 13 | Emoji picker tiles (46×46dp) |
| — | 3 | Slider tracks, tab underline, nav pill |
| — | 50% | Avatars, knobs, circular icon buttons |
| — | 48 | Device frame (mockup chrome only) |

---

## 6. Elevation & effects

The design leans on **hairline borders plus large soft shadows**, not Material's tonal elevation.
Set `MaterialTheme` surface tint to transparent so Compose does not tint surfaces on elevation.

| Token | Compose dp | Source shadow |
|---|---|---|
| `elevationCard` | 0 | none — 1dp `outline` border instead |
| `elevationSheet` | 16 | `0 -14dp 40dp rgba(0,0,0,0.5)` (Place Edit) / `0 -20dp 50dp rgba(0,0,0,0.6)` (ride moments) |

Shadows Compose `elevation` cannot express — use `Modifier.shadow(spotColor=, ambientColor=)`:

| Element | Shadow |
|---|---|
| Primary button | `0 12dp 28dp rgba(91,141,239,0.30)` |
| Start Ride hero | `0 18dp 40dp rgba(63,111,214,0.35)` |
| Green confirm button | `0 14dp 32dp rgba(55,214,122,0.30)` |
| Red destructive button | `0 14dp 32dp rgba(255,85,106,0.32)` |
| HUD telemetry panel | `0 24dp 60dp rgba(0,0,0,0.55)` |
| Active segmented segment | `0 2dp 8dp rgba(0,0,0,0.40)` |
| Input focus ring | `0 0 0 4dp rgba(91,141,239,0.12)` |

### 6.1 Glass surfaces

| Surface | Dark fill | Light fill | Blur |
|---|---|---|---|
| HUD telemetry panel | `rgba(15,17,21,0.74)` | `rgba(255,255,255,0.78)` | 30px |
| Top chip | `rgba(14,16,20,0.72)` | `rgba(255,255,255,0.82)` | 18px |
| Map legend | `rgba(15,17,21,0.78)` | — | 20px |
| Replay panel | `rgba(15,17,21,0.82)` | — | 24px |
| Map controls (Place Edit) | `rgba(15,17,21,0.78)` | — | 18px |

Android has no CSS `backdrop-filter`. Either apply `RenderEffect.createBlurEffect` to the map layer
behind the panel (API 31+), or fall back to the flat translucent fill above — it is legible on its own.

HUD panel geometry: `left 12dp, right 12dp, bottom 40dp`, radius 30dp, border
`1dp rgba(255,255,255,0.08)`, internal divider `1dp rgba(255,255,255,0.07)`.

---

## 7. Component specs

**Primary button** — full width, 54dp, radius 16, `#5B8DEF`, `#FFF` 16sp/600, blue glow shadow.
**Destructive text button** — 50dp, no background, `#FF556A` 15sp/600.
**STOP button (HUD)** — width 116dp, radius 16, border `rgba(255,85,106,0.38)`,
background `rgba(255,85,106,0.13)`, label `#FF7A88`, square glyph `#FF556A`.

**Segmented control (fuel type)** — container `display:flex; gap 4dp; padding 4dp; radius 14;
background #14161A; border 1dp rgba(255,255,255,0.06)`. Segment: `flex:1; padding 10×2dp; radius 10`,
icon (20dp, fuel-type coloured) stacked over label with 6dp gap.
Active: `background #23262C`, shadow `0 2dp 8dp rgba(0,0,0,0.4)`, label `#F2F4F7` @700.
Inactive: transparent, label `#8A9099` 11sp/500.

**Text field** — height 52dp, padding `0 16dp`, radius 14, background `#14161A`,
border `1.5dp rgba(255,255,255,0.08)`. Focused: border `1.5dp #5B8DEF` + ring
`0 0 0 4dp rgba(91,141,239,0.12)`. Value 16sp `#F2F4F7`; unit suffix 13sp `#7E858F`.
Label sits above: 12sp/600 `#8A9099`, uppercase, +0.2 tracking, 7dp gap. Helper text 12sp `#6B7178`.
**No error state was drawn** — build it from `error #FF556A` (border + helper text).

**Toggle (on)** — track 48×28dp radius 14 `#5B8DEF`; knob 22dp white, inset 3dp,
shadow `0 1dp 3dp rgba(0,0,0,0.4)`.

**Radius slider (Place Edit)** — track 5dp radius 3 `rgba(255,255,255,0.12)`; fill `#5B8DEF`;
knob 20dp white, shadow `0 2dp 8dp rgba(0,0,0,0.5)`. Value readout mono 14sp/600.
Geofence circle on map: fill `rgba(91,141,239,0.14)`, stroke `1.5dp rgba(91,141,239,0.55)`,
centre dot 8dp `rgba(91,141,239,0.6)`.

**Emoji picker tile** — 46×46dp, radius 13, 22sp glyph.
Selected: `background rgba(91,141,239,0.16)`, `border 1.5dp #5B8DEF`.
Unselected: `background #16181C`, `border 1dp rgba(255,255,255,0.07)`.

**Split row** — `padding 14×20dp`, top border `1dp rgba(255,255,255,0.05)`.
Road name Geist 15sp/500 `#F2F4F7` + distance sub-line 11sp `#7E858F` (2dp gap) ·
TIME column 70dp right-aligned, Geist Mono 16sp ·
Δ VS BEST column 86dp right-aligned, Geist Mono 15sp/600 tinted green/red, with a
`best 0:39.0` caption beneath in Geist Mono 10sp `#6B7178`.
Header row: 10sp/600, +1.5 tracking, `#6B7178`, labels `SEGMENT` / `TIME` / `Δ VS BEST`.

**Trip Detail tabs** — Map / Splits / Replay; **Splits is the default active tab**.
Bar `border-bottom 1dp rgba(255,255,255,0.07)`; active text `#F2F4F7` + `2dp #5B8DEF` underline
(`margin-bottom:-1dp` so it overlaps the divider); inactive `#7E858F`.

**Map speed bands** (Trip Detail Map tab): Fast `#37D67A` · Steady `#F0B24E` · Slow `#FF556A`.
Start marker = green ring, end marker = white.

**Bottom navigation** — 4 tabs: Home · Trips · Cars · Places.
Background `#0C0E11`, top border `rgba(255,255,255,0.06)`.
Active `#5B8DEF` label 10sp/600; inactive `#6B7178` label 10sp/500.

---

## 8. `Ride Moments` — an unplanned screen worth keeping

`Ride Moments.dc.html` is not in the CLAUDE.md checkpoint plan. It is not a product screen but a
**reference board for overlays and edge states**, and it resolves several things the plan leaves
undrawn. Its own subtitle: *"Bottom sheets that slide over the live map, plus the GPS-acquiring
state and the three empty states."*

Row 1 — modal bottom sheets over the live map:
1. **Pre-ride sheet (dark)** — vehicle selector ("Model 3 · Default vehicle" + Electric badge),
   Origin / Destination place fields (Destination dashed-empty, both "· optional"), hint
   *"Set a destination to auto-finish the ride on arrival."*, blue Start Ride button, plus the
   floating "Near Home / Use as origin?" suggestion chip. → **Checkpoint 6, F5**
2. **Pre-ride sheet (light)** — same layout, light palette. The only full light-theme sheet drawn.
3. **Stop · Confirm** — "Finish this ride?", stats row (Elapsed 24:18 / Distance 31.2 km / Avg 58 km/h),
   red Finish Ride + Keep Going. → **F6-A / `StopConfirmSheet`**
4. **Arrival · Auto-finish** — 30s countdown ring + shrinking bar, "You've arrived at Home 🎉",
   green Finish Ride + "I'm just passing". → **F6-B / `ArrivalSheet`**

Row 2 — GPS and empty states:
5. **HUD · Acquiring GPS** — pulse spinner, amber `SEARCHING` pill, dimmed telemetry panel showing
   `--` km/h and `--:--`, CANCEL button. → **Checkpoint 10's cold-GPS-start requirement**
6. **Empty · No Trips** — "No drives yet" + "Start your first ride".
7. **Empty · No Cars** — "No cars added" + "Add a vehicle to track fuel or energy use…".
8. **Empty · No Places** — "No saved places" + "Save places like Home or Office…".

States 5–8 satisfy the "Empty states" and "Bottom sheets" entries under *Component styles to define*.

---

## 9. Gaps and decisions

### Decided — no action needed

1. **Light theme: out of scope for the POC.** Ship dark only. Build a single dark `ColorScheme`;
   no `isSystemInDarkTheme()` branch. §2.2 is recorded for future reference, not implementation.
   This also moots the missing light `purpleSector` row treatment.
2. **Colour collisions: accepted.** Diesel `#5B8DEF` == `primary`, and Electric `#37D67A` ==
   `success`/`deltaFaster`. **Consequence that still binds:** a fuel-type badge must always render
   its text label and must never signal by colour alone — for correctness and for accessibility.

### Open — resolve during implementation

3. **No error state was drawn on text fields.** Derive it: border + helper text in `error #FF556A`,
   reusing the focused field's 1.5dp border and 4dp ring geometry.
4. **Only Electric was drawn as a filled badge.** The other four colours come from segmented-control
   icon strokes. Apply the same 12% background / 30% border composite to them.
5. **Geist / Geist Mono are not yet vendored** into `res/font/`. Both are OFL-licensed.
   Weights needed: Geist 400/500/600/700, Geist Mono 400/500/600.
6. **Contrast:** `#6B7178` on `#0A0B0D` is roughly 4.0:1 — below WCAG AA (4.5:1) for body text.
   It is used for dim captions and inactive nav labels at 10–11sp. Either accept it as decorative
   (WCAG exempts nothing at that size — 4.5:1 applies below 18pt), or lighten to `#7E858F` (~5.2:1)
   for anything a user must actually read. Recommend lightening the inactive nav labels.
