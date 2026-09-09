# Handoff: Qidi — UI & notification redesign

## Overview

Qidi is an Android utility that keeps chosen apps alive on a device that force-stops apps in
batches (including Qidi itself — 181 self-recoveries in five weeks). This handoff covers a
redesign of its interface and notification behaviour: three screens (Status, Protected apps,
Settings), eight product states, and two notification channels.

The redesign collapses the old three-tab + raw-log app into **two screens plus settings**, and
removes all log browsing in favour of a single diagnostics toggle.

## About the design files

`prototype/Qidi.dc.html` is a **design reference created in HTML** — a running prototype that
shows intended look and behaviour. It is *not* production code to port. The task is to
**recreate these designs in the Android app** using its own environment and patterns
(Jetpack Compose + Material 3 is assumed — see "Platform notes").

Open `prototype/Qidi.dc.html` in a browser. The left rail is a *scaffold for reviewing the
design* and is not part of the product: it switches product state, screen, theme, and the
notification shade overlay. Everything inside the phone bezel is the design.

## Fidelity

**High fidelity.** Colours, type, spacing, radii, copy and interaction states are final and
should be matched. The only deliberate placeholders are the **app icons** (rounded squares with
an initial) — real launcher icons come from `PackageManager` at runtime.

---

## Platform notes (flag: what requires Compose)

The app currently builds UI programmatically with plain Android Views, no AndroidX / Material /
Compose. This design assumes **Compose + Material 3 with dynamic colour disabled** (the status
palette below is fixed, because status semantics must not shift with wallpaper).

Elements that genuinely need Compose (or non-trivial View work if you refuse it):

| Element | Why |
| --- | --- |
| Health hero state change | Cross-fade of colour + glyph + copy as one transition |
| Checklist row expansion | Animated height change when a row becomes unhealthy |
| Toggles | M3 `Switch` behaviour and thumb transition |
| Search filtering on Protected apps | List diffing / animated reorder |

Everything else (static layout, notification builders, the settings list) maps to plain Views
without difficulty. Notifications are `NotificationCompat` regardless.

---

## Design tokens

Derived from the "Organic" design system (`prototype/_ds/.../styles.css` — the source of truth;
`readme.md` beside it explains the system). Dark is the **primary** theme.

### Colour — surfaces

| Role | Dark | Light |
| --- | --- | --- |
| Background | `#15120f` | `#f5ead8` |
| Surface (cards) | `#221e19` | `#ece0ca` |
| Elevated (inset rows, toggle track) | `#2c2721` | `#fffaf0` |
| Text | `#f7f1e6` | `#201e1d` |
| Muted text | `#b3a897` | `#6b6355` |
| Divider | text @ 13% | text @ 15% |
| Accent (primary buttons, brand) | `#c67139` | `#c67139` |

Primary button: accent fill, label in `--color-bg` (i.e. dark text on terracotta).

### Colour — status semantics

Four states. **Colour is never the only carrier** — every status pairs a hue with a distinct
glyph and an explicit word ("Protected" / "Attention" / "Broken" / "Off").

| Status | Dark hue | Light hue | Tint fill | Glyph (Lucide, stroke 2.75) |
| --- | --- | --- | --- | --- |
| `ok` — Protected | `#b9cb9b` | `#56633f` | hue @ 12% | shield + check |
| `wait` — Attention | `#f0a16f` | `#a2551f` | hue @ 12% | shield + clock hands |
| `bad` — Broken | `#e5806c` | `#9d3b28` | hue @ 13% | shield + cross |
| `off` — Off | `#9c9384` | `#82796a` | hue @ 10% | power symbol |

A **monochrome mode** exists in the prototype (a review tweak, not a shipping feature) that
strips all four hues to neutrals — use it to confirm every state still reads from glyph and
word alone. It should stay readable; if a change breaks that, the change is wrong.

### Type

- Display / headings: **Caprasimo** 400 — `h2` 27px/1.12, screen titles 22–25px.
  On Android substitute a warm rounded display face; Caprasimo is a web font.
- Body / UI: **Figtree** — 15px/1.5 body, 15px 600 row labels, 12.5–13px 1.45 secondary,
  11.5px package names, 10.5px 700 uppercase section labels (letter-spacing .13em).
- Minimum text size in the UI: 10.5px (only for uppercase micro-labels); body never below 12.5px.

### Spacing, radius, elevation

- Spacing scale (1.10×): 4.4 / 8.8 / 13.2 / 17.6 / 26.4 / 35.2 px.
- Screen gutter: 16px. Card padding: 14–22px. Row padding: 10–15px vertical, 14–18px horizontal.
- Radii: hero 30px · cards 26px · rows inside cards 21px · inset blocks 18px ·
  app icon squares 13px (34px squares use 11px) · buttons, chips and inputs 999px (pill).
- Touch targets: 48×48 minimum for icon buttons and rows; app rows are 64px tall; primary
  action button 50px; toggles 52×32 inside a 48px row.
- No drop shadows are used in the app UI — separation comes from surface fills.

---

## Screens

### 1. Status (home)

**Purpose.** Answer "is my protection working right now?" in under two seconds.

Vertical stack, 16px gutter, all children full-width:

1. **App bar** — 56px. "Qidi" in the display face 25px, flush left; gear icon button 48×48 right,
   opens Settings. No back affordance (this is home).

2. **Recovery banner** *(only after Qidi recovered itself)* — 22px radius, `ok` tint fill, refresh
   glyph, text: *"Qidi was force-stopped at 09:14 and brought itself back 4 seconds later.
   Nothing was missed."* Trailing text button "Got it" dismisses it. Deliberately in the healthy
   palette — this is reassurance, not an incident.

3. **Health hero** — the dominant element. 30px radius, tint fill of the current status, 1px
   border of the status hue @ 32%, 22px padding.
   - Row: 54px circle, 2.5px border in status hue, 27px status glyph inside; beside it a pill
     chip (status hue border + text, 10.5px 700 uppercase) reading the status word.
   - `h2` 27px title (the answer).
   - 14.5px muted sentence (the explanation).
   - Footer row: 8px dot in the status hue with a 2.6s breathe animation, then the freshness
     label ("Checked 12 seconds ago"). `aria-live="polite"`.

   Copy per state:

   | State | Chip | Title | Sentence |
   | --- | --- | --- | --- |
   | healthy | Protected | All 7 apps running | Everything Qidi watches is alive. Nothing has needed you. |
   | deferred | Protected | 6 running, 1 queued | Nextcloud has no invisible way to start, so Qidi is holding it until your screen turns off rather than interrupting you. |
   | recovered | Protected | All 7 apps running | Everything Qidi watches is alive. Nothing has needed you. |
   | sentinel missing | Attention | Protection is thinner than usual | Your apps are running, but nothing is watching Qidi itself. If Qidi is killed now, it stays dead until you open it. |
   | permission revoked | Broken | Shizuku permission was revoked | Qidi cannot run privileged commands, so nothing is being protected right now. |
   | Shizuku dead | Broken | Shizuku is not responding | Qidi has no privileged access and cannot restart anything. Start the Shizuku service and protection resumes on its own. |
   | off | Off | Protection is off | You turned Qidi off at 08:02. Nothing is being watched. |
   | first run | Not set up | Nothing to protect yet | Choose the apps that must stay alive. Qidi always protects itself. |

4. **Health checklist** — surface card, 26px radius, 6px padding, rows 21px radius.
   Healthy row (compact, 24px content height): 9px status dot · label 15px 600 · value 13px 700
   in the status hue, right-aligned. Unhealthy row expands: gains the status tint as a row
   background and a second line — 12.5px muted hint (flush with the label, 21px indent) plus a
   40px pill primary button with the fix.

   | Subsystem | Values | Action when unhealthy |
   | --- | --- | --- |
   | Shizuku | Ready / Permission needed / Not responding | "Grant" · "Open Shizuku" |
   | Watchdog | Running / Idle / Stopped / Waiting | "Start" |
   | Backup sentinel | Active / Missing | "Reinstall" |

   **Sentinel prominence:** by default the sentinel is *not* a row while healthy — it is a quiet
   12px footnote under the card ("Backup sentinel active · verified 09:12", small shield glyph).
   It becomes a full expanded row only when missing. Rationale: it is plumbing the user never
   configures; giving it standing equal to Shizuku implies it needs attention. The prototype has
   a tweak to make it always a row if you disagree.

5. **Protected apps summary** — tappable surface card, 26px radius, 16px padding. Overlapping
   app icon squares (34px, -8px overlap, 2px border in the card colour) for the first five, then
   "7 apps protected" 15px 600 with a 12.5px muted sub-line ("7 running now" / "6 running · 1
   waiting for screen off" / "None protected while Shizuku is unavailable"), then a chevron.
   Hidden on first run.

6. **Recent activity** — one muted 12.5px sentence, no chart:
   *"12 recoveries in the last 24 hours · most recent 09:14"*. When Shizuku is down it becomes
   *"9 recoveries in the last 24 hours · none since Shizuku stopped responding at 09:30"*.
   Rationale below.

7. **Actions** — 50px row. Primary pill (accent fill), flex-grow: "Stop protection" /
   "Start protection" / "Choose apps to protect" (first run). Secondary outlined pill
   "Recover now" — forces an immediate check; label becomes "Checking…" and the hero freshness
   line reads "Checking now…" for ~1.4s, then "Checked just now". Hidden when off or first run.

### 2. Protected apps

- Back button 48×48, title 22px display face.
- Search field: pill input, 48px, surface fill, 17px search glyph inset left at 40px padding.
  Filters both sections live on name and package.
- Section header: 10.5px uppercase muted — "Protected · 7", then "All apps".
- **Row** (64px): 42px icon square (13px radius) with the app's initial · name 15px 600 with
  ellipsis · package 11.5px muted with ellipsis · a third line with a 7px status dot and an
  11.5px 600 status word in the status hue ("Running" / "Queued for screen off" / "Unprotected" /
  "Not watched") · trailing 52×32 toggle.
- **Qidi's own row** is locked: instead of a toggle, a pill reading "Always on" with a lock glyph,
  and beneath it 10.5px muted "Qidi is killed too — it must guard itself". Never render a
  disabled-looking switch with no explanation.
- Rows in "All apps" have their icon at 75% opacity and no status line.
- **Empty state** (nothing protected): surface card, 46px accent-ringed plus circle, "Nothing
  protected yet", then *"Turn on the few apps that must never die — a messenger, a sync client,
  your automation. Qidi keeps itself alive automatically, so it is already on the list."*

### 3. Settings

Single surface card, four rows, then an about block. Row: label 15px 600, 12.5px muted subtitle,
trailing 52×32 toggle (or lock pill).

| Setting | Default | Subtitle |
| --- | --- | --- |
| Record diagnostic log | on | Only needed when something goes wrong. It writes continuously and uses storage. |
| Alert me to problems | on | Sound for things you must act on. Never for routine restarts. |
| Status notification | locked "Always on" | Android requires it while Qidi runs — and its disappearance is how you know Qidi was killed. That is why it cannot be turned off. |
| Only launch apps while the screen is off | on | Qidi waits rather than throwing an app in front of you. Turning this off lets it launch visibly. |

When the diagnostic log is on, an inset block (18px radius, elevated fill) appears under that
row: "Diagnostic log · 4.2 MB" plus a 38px outlined "Delete now" button. This replaces the whole
removed log-browsing screen.

About block, 12px muted: "Qidi 2.0 · build 214" / "181 recoveries since install · watching since
2 August".

---

## Notifications

Shown in the prototype as a shade overlay (rail button "Notification shade"). Notification cards
are drawn in **Android system styling, not the app's** — deliberately: the OS owns that chrome.
Only Qidi's small icon carries the brand.

### Channel 1 — "Qidi status" (heartbeat)

`IMPORTANCE_MIN`, silent, ongoing, no badge, no timestamp churn. **Only re-post when the state
string actually changes** — not on every check cycle.

- Small icon: rounded-square "Q" tinted with the current status hue (fill @ tint, 1.5px border).
- Title: `Qidi · protecting 7 apps` — or `Qidi · not protecting` when Shizuku is unavailable.
- Body: `Ongoing · silent` / `1 waiting for screen off` / `Backup sentinel missing` /
  `Waiting for Shizuku`.
- **Degraded variant is subtle by design**: the silhouette, title format and position never
  change; only the icon tint and the body suffix move. The card must stay recognisable at a
  glance in a crowded shade, because its *presence* is the signal.
- Expanded form: a row of 26px per-app squares in status hues, then
  "Last check 09:41:07 · next in 20s · 12 recoveries today", then actions "Recover now" / "Open Qidi".

When protection is off there is no notification at all — the prototype shows a dashed
placeholder explaining that this looks identical to a killed Qidi, which is the accepted
trade-off of using presence as the dead man's switch.

### Channel 2 — "Qidi problems" (alerts)

`IMPORTANCE_DEFAULT`, may sound. Never for successful routine restarts. Auto-cancel when the
condition clears.

| Condition | Title | Body | Action |
| --- | --- | --- | --- |
| Permission revoked | Shizuku permission was revoked | Grant Qidi permission in Shizuku to resume protection. | Open Shizuku |
| Shizuku dead | Shizuku is not responding | Open Shizuku and start the service. Protection resumes automatically. | Open Shizuku |
| Sentinel gone | Backup sentinel is missing | Nothing will restart Qidi if it is killed. Reinstall it from Qidi. | Reinstall |

### Sentinel-posted fallback

Posted by the privileged shell process when Qidi is entirely dead, so it cannot use the app's
icon or styling: grey circle, "Android System", plain text "Qidi was stopped. Restarting."
Design accounts for it looking unlike the rest of the app — that is honest, and it only ever
appears when the app is gone.

---

## Interactions & behaviour

- Screen changes: gear → Settings, summary card → Protected apps, back arrows → Status.
- "Stop protection" moves the whole app to the off state (hero, checklist, summary, notification
  all follow); "Start protection" returns it to healthy.
- "Recover now": 1.4s pending state, then the freshness line reads "Checked just now".
- Toggles: 180ms track colour + 20px thumb translate. On = `ok` hue track, background-coloured thumb.
- Row hover/press: text colour @ 6% overlay.
- Focus: 2px accent outline, 2px offset — never the platform default ring.
- Breathing dot in the hero: 2.6s ease-in-out, opacity .35→1 with a slight scale. This is the
  only decorative motion in the app; everything else animates only on state change.
- Search is case-insensitive across app name and package name.

## State

| State | Type | Notes |
| --- | --- | --- |
| `healthState` | enum ok / wait / bad / off | Derived, never stored — computed from the three subsystem checks plus the protection flag |
| `shizuku` | enum ready / permissionNeeded / notResponding | |
| `watchdog` | enum running / idle / stopped / waiting | |
| `sentinel` | enum active / missing | |
| `protectedApps` | set of package names | Always contains Qidi's own package; not removable |
| `appStatus[pkg]` | enum running / stopped / deferred | `deferred` = waiting for screen-off |
| `lastCheckAt` | timestamp | Rendered as relative text, refreshed on a timer |
| `recoveryCount24h`, `lastRecoveryAt` | | Feeds the activity sentence |
| `justRecovered` | bool + timestamp | Drives the banner; dismissible, auto-clears after a day |
| `settings` | log / alerts / screenOffOnly booleans | |

## Rationale (short)

**Health hero.** The user opens this app because they already suspect something. A single
dominant element that resolves to one of four states — with word, glyph and hue agreeing — answers
that suspicion before they read anything else. Four states, not a score: the honest question is
"can Qidi do its job", and that is categorical.

**Deferred as a queue, not a fault.** "Deferred" stays in the healthy palette and is phrased as
consideration ("rather than interrupting you"), with the per-app chip reading "Queued for screen
off". Treating it as a warning would train the user to distrust correct behaviour.

**Recent activity as one sentence.** A bar strip of 181 recoveries is an incident feed wearing a
chart's clothes; on a screen whose whole job is calm, it invites the user to study noise. One
number carries the same reassurance ("it is working hard for you") with no reading required.
The line is suppressed on first run and while off, where it would be meaningless.

## Assets

- **App icons are placeholders** — coloured rounded squares with the app's initial. Use the real
  icons from `PackageManager.getApplicationIcon()`.
- **Icons**: Lucide (https://lucide.dev), stroke-width 2.75. Used: shield, shield+check,
  shield+clock, shield+x, power, refresh-cw, settings, arrow-left, chevron-right, chevron-down,
  search, lock, plus, alert.
- **Fonts**: Caprasimo (display) and Figtree (body), Google Fonts. Both are OFL; bundle them or
  substitute a comparable warm rounded display + humanist sans on Android.
- **Launcher icon**: not proposed. Still a placeholder in the app.

## Files

- `prototype/Qidi.dc.html` — the prototype. Template markup + a logic class near the bottom
  of the file hold all copy, state machine and per-scenario data.
- `prototype/android-frame.jsx` — the review-only Android bezel. Not part of the design.
- `prototype/support.js` — prototype runtime. Not part of the design.
- `prototype/_ds/organic-.../styles.css` — design tokens (colour ramps, type, spacing, radii).
- `prototype/_ds/organic-.../readme.md` — the visual system's own guidance.

Open the prototype by serving the `prototype/` folder over any static HTTP server
(`python3 -m http.server`) and visiting `Qidi.dc.html`; opening it from `file://` will block the
local script loads.
