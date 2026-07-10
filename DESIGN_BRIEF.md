# DriveDelta — Design Brief

Paste this into Claude Design or Pencil as your opening prompt to generate the first high-fidelity
mockups. Adjust anything that doesn't match your taste — this is a starting point, not a spec.

---

## The one-line pitch

DriveDelta is a native Android app that records your car drives, tracks your GPS route, and analyses
your time on each stretch of road like a motorsport telemetry tool — comparing every drive against
your personal best, split by split. It also logs fuel/energy per vehicle. Think "race engineer's
dashboard for your everyday commute."

## Design inspiration

Take strong visual inspiration from the **Tesla mobile app**: a clean, premium, minimal aesthetic
built primarily around a **dark theme**, with generous negative space, restrained use of colour,
crisp typography, and a calm, high-end feel. Not flashy or arcade-like — confident and understated.
Where Tesla shows a car render on a dark canvas, DriveDelta centres its map and telemetry data.

Blend that Tesla calm with a subtle **motorsport telemetry** influence: precise data readouts,
split-timing, and the "purple sector" idea from racing (the fastest-ever time on a segment is
highlighted in purple/violet). The result should feel like premium automotive software, not a game.

## Core mood & principles

- **Dark-first.** The default theme is dark — deep near-black or very dark grey/blue backgrounds,
  light text, reduces glare while driving. Provide a light theme too, but design dark first.
- **Minimal & spacious.** Lots of breathing room. One clear focus per screen. Avoid clutter even on
  data-dense screens like the HUD — group and prioritise.
- **Premium & restrained.** Limited accent colour. Most of the screen is neutral (blacks, greys,
  whites); colour is used purposefully — for the primary action, and for the delta/split states.
- **Data with clarity.** Numbers (speed, times, deltas) are first-class. Use a clean, slightly
  technical/monospaced feel for numeric readouts so digits are precise and don't shift as they update.
- **Soft depth.** Subtle elevation, soft shadows, gentle rounded corners on cards and sheets (the
  Tesla app leans into soft, rounded, slightly neumorphic surfaces — you can nod to that without going
  full neumorphism).

## Colour direction (suggested — refine freely)

- **Background:** near-black / very dark charcoal, optionally with a faint cool (blue-grey) tint.
- **Surfaces (cards, sheets):** a slightly lighter dark grey, soft rounded corners, gentle shadow.
- **Primary accent:** a single confident colour for primary actions (Start Ride, confirm). A cool
  electric blue or a clean white-on-dark both fit the Tesla feel. (Tesla itself uses red sparingly;
  you may reserve red for the delta "slower" state rather than as the brand accent.)
- **Delta / split states** (the signature data colours):
  - *Faster than best* → green
  - *Slower than best* → red
  - *Fastest ever (personal best) on a segment* → **purple/violet** (the motorsport "purple sector")
- **Fuel-type badges:** electric = green, diesel = blue, petrol = orange, hybrid = yellow, LPG = grey.
- Keep everything else neutral so these signal colours pop.

## Typography direction

- A clean, modern sans-serif for UI text (something in the spirit of Tesla's clean Gotham-like type —
  Inter, Manrope, or similar work well and are free).
- A **monospaced / tabular-figures** face for numeric readouts — speed, elapsed time, split times,
  deltas — so digits align and don't jitter when updating. (e.g. JetBrains Mono, Roboto Mono.)
- Large, confident numeric displays on the HUD. Generous type hierarchy elsewhere.

## Screens to design (high fidelity, dark theme first)

Design these six. They define the visual language; everything else reuses their components.

### 1. Auth screen
- Minimal. Dark canvas, DriveDelta logo/wordmark, a short tagline ("Your drive, analysed."),
  and a single Google Sign-In button. Lots of space. Sets the premium tone.

### 2. Dashboard (home)
- The landing screen after sign-in. A prominent **Start Ride** button (the hero action — treat it
  like Tesla's primary control: large, central, unmistakable).
- Below: a few recent-trip cards (date, route as "Origin → Destination", duration, distance, car).
- A "personal bests" area (fastest times on your most-driven routes).
- A compact weekly-stats summary (total km, drive time, fuel cost).
- Soft rounded cards on the dark canvas, clear hierarchy, minimal chrome.

### 3. Live Tracking HUD — the signature screen (design light + dark; dark is default)
- Full-screen **map** as the background (dark map style to match the theme).
- A **telemetry overlay** floating over the map — semi-transparent dark panel, softly rounded — showing:
  - Large **speed** readout (km/h) — the biggest number on screen.
  - **Elapsed time** and **distance**.
  - **Current road name**.
  - The **split**: current segment time vs your best on that segment, with a coloured **delta**
    (green if ahead, red if behind, purple if you're setting a new best).
  - If a destination is set: a small "X km remaining" chip.
- A clear **STOP** control (bottom, unmistakable).
- This screen should feel like a race engineer's live timing screen, but calm and legible at a glance.

### 4. Trip Detail — post-ride analysis (tabbed)
- Three tabs: **Map**, **Splits**, **Replay**.
- *Map:* the full route drawn on a dark map, coloured by speed (fast = bright/green, slow = dim/red).
- *Splits:* a clean table — each row is a road segment: road name, your time, your best time, and the
  delta (coloured). This is the core data component; make it crisp and scannable. Highlight any
  segment where this run set a new personal best in **purple**.
- *Replay:* playback controls (play/pause/2×) and a scrubber; a marker animates along the route.

### 5. Car edit
- A form screen. Name, license plate, a **segmented control** for fuel type
  (Petrol / Diesel / Hybrid / Electric / LPG), and conditional fields (tank capacity + L/100km for
  fuel; battery kWh + kWh/100km for electric). A "set as default" toggle. Show the fuel-type badge
  colours. Clean, spacious form styling.

### 6. Place edit
- A **map** with a draggable pin the user positions precisely.
- An address search bar at the top.
- A **radius slider** with a translucent circle on the map showing the geofence.
- An emoji/icon picker row for the place.
- "Use my current location" button.

## Reusable components to establish (so all screens share a language)

Primary / secondary / destructive buttons · trip card · car card · place card · filter chips ·
the "nearby place" suggestion chip · fuel-type badge (5 colour variants) · bottom sheets
(stop-confirm, arrival, pre-ride setup) · the **split row** (road name + time + best + coloured delta) ·
the HUD telemetry overlay panel · segmented control · text fields with labels/error states ·
empty states (no trips / no cars / no places yet).

## Deliverables

- High-fidelity mockups of the six screens above (dark theme; plus light variants for the HUD and map).
- A defined set of design tokens: colours (light + dark), typography scale, spacing scale, corner
  radii, elevation. I'll transfer these into the app's Compose theme.

## What to avoid

- Arcade / gamer aesthetics (no neon gradients everywhere, no aggressive racing clichés).
- Clutter or too many accent colours competing.
- Tiny, hard-to-read numbers on the HUD — the driving-glance readability matters.

---

*Note: I'm building this as a native Android app in Jetpack Compose, so please treat any generated
code as visual reference only — I'll reimplement the UI in Compose. What I need from you is the
**visual design and the design tokens**, not production code.*
