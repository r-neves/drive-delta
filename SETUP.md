# DriveDelta — Setup & Kickoff Checklist

Everything you need to do before and when handing the project to Claude Code.
Steps marked **[you]** must be done by you in a browser — Claude Code can't do them.
Steps marked **[cc]** can be delegated to Claude Code.

---

## PART A — Accounts & Cloud Setup (do these first, in a browser) [you]

### 1. Google Cloud Console
- [ ] Go to https://console.cloud.google.com and create a new project (e.g. "DriveDelta").
- [ ] Enable billing on the project (required for Maps/Roads/Places; free tier covers light personal use).
- [ ] Under "APIs & Services → Library", enable each of these:
  - [ ] Maps SDK for Android
  - [ ] Roads API
  - [ ] Places API (New)
  - [ ] Geocoding API
- [ ] Under "APIs & Services → Credentials", create an API key.
- [ ] Restrict the key: Application restriction → Android apps; add your package name `app.drivedelta` and your debug SHA-1 (get it in step B3). You can start unrestricted for local testing and lock it down before release.

### 2. Firebase
- [ ] Go to https://console.firebase.google.com and create a project — **link it to the same Google Cloud project** you just made.
- [ ] Add an Android app to the Firebase project:
  - Package name: `app.drivedelta`
  - Register, then **download `google-services.json`**. Keep it safe; you'll drop it into the repo later.
- [ ] Authentication → Sign-in method → enable **Google** provider.
- [ ] Firestore Database → Create database → Production mode → pick a region (europe-west1 is close to you).
- [ ] Firestore → Rules → paste the security rules from CLAUDE.md (the `userId == request.auth.uid` block) → Publish.

### 3. GitHub
- [ ] Create a new **GitHub** repository (private is fine), e.g. `drivedelta`.
  - Note: it must be GitHub — Claude Code on the web doesn't support GitLab/others.
- [ ] Leave it empty (no README needed) or initialise with a README — either works.

---

## PART B — Local Machine Prep [you]

### 1. Install tooling (if not already present)
- [ ] Android Studio (latest stable) — https://developer.android.com/studio
- [ ] JDK 17+ (bundled with Android Studio)
- [ ] Git
- [ ] Claude Code CLI — https://code.claude.com (or use the web version at claude.ai/code)

### 2. Clone your empty repo
```bash
git clone https://github.com/<your-username>/drivedelta.git
cd drivedelta
```

### 3. Get your debug SHA-1 (needed for Google Sign-In + Maps key restriction)
After the project exists (or generate a debug keystore first), run:
```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```
Copy the SHA-1 and SHA-256 → add them to the Firebase Android app settings AND the Maps API key restriction.

### 4. Place secrets (these are gitignored — never commit them)
- [ ] Put `google-services.json` in the `app/` module directory (Claude Code will create that folder; you may need to add the file after Checkpoint 1 scaffolds the project).
- [ ] Create `local.properties` in the project root with:
```
MAPS_API_KEY=AIza...your-key...
ROADS_API_KEY=AIza...your-key...
PLACES_API_KEY=AIza...your-key...
```
(Can be the same key for all three if all APIs are enabled on it.)

### 5. Add the plan to the repo
- [ ] Copy `CLAUDE.md` into the repo root.
- [ ] Commit and push:
```bash
git add CLAUDE.md
git commit -m "Add project plan"
git push
```

---

## PART C — Kicking Off Claude Code

You have two ways to run it. Pick based on the checkpoint (see the routing note at the end).

### Option 1 — Local terminal (recommended for GPS/Firebase checkpoints)
```bash
cd drivedelta
claude
```
Then paste the kickoff prompt below.

### Option 2 — Claude Code on the web (good for pure-logic checkpoints)
- Go to https://claude.ai/code
- Connect your GitHub account, select the `drivedelta` repo.
- Paste the kickoff prompt as the task.
- It clones, works, and opens a PR you review + merge.
- Note: secrets aren't in the repo, so web sessions can write code but can't produce a fully compiling build that needs the API keys / google-services.json. Use web for logic checkpoints; pull local for the ones needing device testing.

---

## PART D — The Kickoff Prompt

Paste this as your first message to Claude Code:

> I'm building an Android app called DriveDelta. The full specification and build plan is in
> CLAUDE.md in the repo root. Read it fully before doing anything.
>
> We're going to build this checkpoint by checkpoint, in the exact order listed under
> "MVP Build Checkpoints". Do NOT jump ahead.
>
> Start with **Checkpoint 1 — Project Skeleton & Auth** only. Implement every task in that
> checkpoint's list. When you hit its acceptance test, stop and tell me exactly how to verify
> it on my end. Do not begin Checkpoint 2 until I confirm Checkpoint 1 passes.
>
> Rules:
> - Follow the tech stack, package name (`app.drivedelta`), and architecture in CLAUDE.md exactly.
> - Do not add any dependency that isn't listed in the plan without asking me first.
> - Use Kotlin, Jetpack Compose, Hilt (KSP not KAPT), and Gradle Kotlin DSL throughout.
> - If anything in the plan is ambiguous or you'd deviate from it, ask me before proceeding.
>
> Confirm you've read CLAUDE.md and summarise Checkpoint 1's scope back to me before writing code.

---

## PART E — Per-Checkpoint Follow-up Prompts

After each checkpoint passes its acceptance test, use:

> Checkpoint N passes on my end. Proceed to **Checkpoint N+1 — <name>** only. Same rules as
> before: implement all its tasks, stop at the acceptance test, and tell me how to verify.
> Don't start the checkpoint after it until I confirm.

If Claude Code drifts or over-builds:

> Stop — that's beyond the current checkpoint's scope. Revert anything outside Checkpoint N
> and stick strictly to the task list in CLAUDE.md for this checkpoint.

---

## PART F — Which checkpoints to run where

| Checkpoint | Best environment | Why |
|---|---|---|
| 1 — Skeleton & Auth | Local | Needs google-services.json + real sign-in test |
| 2 — Room DB & Sync | Web or Local | Mostly pure logic; verify Firestore write locally |
| 3 — Cars CRUD | Web or Local | Pure logic + UI |
| 4 — Places CRUD | Local | Needs Maps/Places API key to test map picker |
| 5 — Tracking Service | Local | Needs real device GPS |
| 6 — Live Tracking UI | Local | Needs device GPS + service |
| 7 — Roads API + Segments | Local | Needs Roads API key + real trip data |
| 8 — Trip Detail & Compare | Web or Local | Logic-heavy; can mock data |
| 9 — History/Fuel/Dashboard | Local | Ties together device features |
| 10 — Hardening & Play Store | Local | Signing, device testing, release |

**Optional but recommended for web sessions:** add a `.claude/settings.json` with a SessionStart
hook that runs your Gradle sync / dependency install, so cloud sessions start ready to build.
Ask Claude Code to set this up during Checkpoint 1.

---

## PART G — Working Across Multiple Laptops

Goal: start a Claude session on any laptop and pick up exactly where you left off. The key idea:
**Git is the shared brain.** A Claude session's own memory does NOT travel between machines — only
what's committed to the repo does. These practices make the repo carry the full state.

### The golden rule
**A checkpoint isn't done until it's committed AND pushed.** Never walk away from a laptop with
unpushed work — the other machine starts from the last push. Tell Claude Code:
> When a checkpoint passes, tick its boxes in CLAUDE.md, update PROGRESS.md, commit with a clear
> message, and push — before we stop.

### PROGRESS.md is the continuity file
`PROGRESS.md` (in the repo) tracks: current checkpoint, what's done, decisions/deviations, known
issues, and per-machine environment reminders. It replaces session memory with something portable.
Keep it updated and committed.

### Session-start prompt (use on ANY machine)
> Read CLAUDE.md and PROGRESS.md. Tell me which checkpoint we're on, what's done, and what's next
> — before doing anything. Then wait for my go.

This reorients a fresh session from committed state, not memory.

### Moving secrets between machines (they're gitignored — not in the repo)
`google-services.json` and `local.properties` never go in Git. To get them onto another laptop:
- **Recommended:** store both files' contents in a password manager (1Password / Bitwarden) as
  secure notes or attachments. On a new machine, pull them from there into place.
- Do NOT email them to yourself, and do NOT commit them "just this once."

### Debug keystore (important for Google Sign-In)
Each laptop generates its own `~/.android/debug.keystore` by default → a different SHA-1 → Google
Sign-In fails on the second machine. Two fixes:
- **Recommended:** copy one `~/.android/debug.keystore` to every machine (same SHA-1 everywhere,
  register it once in Firebase). Store it in your password manager alongside the secrets.
- Or: register each machine's debug SHA-1 separately in the Firebase Android app settings.

### One active session at a time
Don't run two Claude sessions against the same branch simultaneously — you'll get merge conflicts.
Either:
- Work one checkpoint at a time on `main`, always pull before starting, push when done; or
- Use a branch per checkpoint (`checkpoint-3`, etc.) and merge via PR. Cleaner if you ever overlap.

Always `git pull` at the start of a session and `git push` at the end. If you use the **web**
version (claude.ai/code) on one machine and the **local CLI** on another, the GitHub repo is the
shared state either way — same discipline applies.

### New-machine checklist (fresh laptop, first time)
1. `git clone` the repo.
2. Pull `google-services.json` + `local.properties` from your password manager into place.
3. Copy the shared `debug.keystore` into `~/.android/` (or register this machine's SHA-1).
4. Install Android Studio + JDK 17 + Claude Code (authenticate it).
5. Open the project, let Gradle sync.
6. Start a session with the session-start prompt above.

---

## Quick summary of what only YOU can do
1. Create Google Cloud project + enable 4 APIs + make API key.
2. Create Firebase project + enable Google auth + Firestore + publish rules.
3. Download `google-services.json`.
4. Create GitHub repo.
5. Get debug SHA-1, register it in Firebase + Maps key.
6. Put `google-services.json` + `local.properties` in place (gitignored).
7. Push CLAUDE.md, PROGRESS.md, and the design files to the repo.
8. Store secrets + shared debug.keystore in a password manager for multi-machine use.

Everything after that, Claude Code does — you just verify each checkpoint and say "go" for the next.
