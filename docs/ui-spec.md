# Qidi — UI & Notification Design Brief

A brief for producing a design proposal. Qidi is an existing, working Android app; this
describes a redesign of its interface and notification behaviour, not a new product.

---

## 1. What Qidi is

Qidi is an Android utility that keeps chosen apps alive on a phone whose manufacturer
aggressively kills background apps. It uses **Shizuku** to run privileged shell commands
without root, detects when a protected app has been force-stopped, and restarts it.

The device it runs on force-stops protected apps in **batches** — every app dies within
about one second of the others, including Qidi itself. Over a recent five-week period Qidi
was killed and automatically recovered **181 times**, roughly 2–3 times per day.

Two consequences shape the whole design:

1. **Recovery is routine and boring.** Success happens many times a day and must not
   generate interface noise.
2. **Qidi itself is a victim.** The interface must communicate not only "are your apps
   alive?" but "is Qidi alive and able to do its job?"

### Glossary for the designer

| Term | Meaning |
| --- | --- |
| **Shizuku** | A separate app providing privileged access. If it dies, Qidi is powerless. |
| **Protected app** | An app the user has chosen to keep alive. |
| **Force-stop** | Android fully terminating an app; it stays dead until something restarts it. |
| **Watchdog** | Qidi's own always-running service that checks and restarts apps. |
| **Sentinel** | A tiny background helper running outside Qidi that can restart Qidi and Shizuku if they die. Internal plumbing, but it is the last line of defence. |
| **Deferred** | An app that could not be restarted invisibly, so Qidi is waiting until the screen turns off rather than interrupting the user. |

---

## 2. Design goals

1. **Glanceable trust.** "Is my protection working right now?" answered within two seconds
   of opening the app.
2. **Silence when healthy.** Routine recoveries are invisible. Only genuine problems speak.
3. **No log reading.** The user should never need to parse raw diagnostic text.
4. **Set and forget.** Pick apps once; maintenance effort tends to zero.
5. **Honest about limits.** Some failures Qidi cannot fix alone. Say so plainly and say what
   the user should do.

### Tone

Utility and instrumentation, not consumer-playful. Think diagnostic panel or aviation status
board: calm, precise, unambiguous. No celebration of success, no alarm-fatigue red.

---

## 3. User and context

A single technical owner, on their own phone. Typically opens the app **because they suspect
something is wrong** — an app stopped delivering notifications, or they want reassurance
before relying on it. Usually one-handed, often in dark surroundings. Dark mode should be
treated as the primary theme, not an afterthought.

---

## 4. Information architecture

The current app has three tabs (Watchdog, Apps, Terminations) and exposes raw logs. The
redesign reduces this to **two screens plus settings**.

```
Status  (home)         →  the health answer
Protected apps         →  what Qidi is guarding
Settings               →  a short list, reachable from Status
```

**Removed:** the log-browsing screen and all raw text dumps. Diagnostics become a single
on/off setting.

---

## 5. Screen specifications

### 5.1 Status (home)

The most important screen. Its only job is to answer "is this working?"

**A. Health hero**

One dominant element expressing exactly one of four states:

| State | Meaning | Example line |
| --- | --- | --- |
| **Protected** | Everything running as intended | "All 7 apps running" |
| **Attention** | Working, but something is pending or partly degraded | "1 app waiting for screen off" |
| **Broken** | Qidi cannot protect anything right now | "Shizuku is not responding" |
| **Off** | Protection intentionally disabled by the user | "Protection is off" |

Accompanied by a plain-language sentence and a last-checked timestamp
("Checked 12 seconds ago"). The hero must never require colour alone to be understood —
pair colour with an icon and text.

**B. Health checklist**

Three rows, each a subsystem with its own state and, when unhealthy, a suggested action:

- **Shizuku** — Ready / Permission needed / Not responding
- **Watchdog** — Running / Stopped
- **Backup sentinel** — Active / Missing

Healthy rows should be quiet and compact. Unhealthy rows expand to offer the fix
(e.g. "Grant permission", "Start protection"). Design both densities.

**C. Protected apps summary**

A compact preview: count of protected apps and how many are currently running, plus a few
app icons. Tapping opens the Protected apps screen. This is a summary, not a list.

**D. Recent activity — as a figure, not a log**

A single humane sentence plus optional minimal visualisation:

> "12 recoveries in the last 24 hours · most recent 09:14"

Consider a very small 24-hour bar strip showing when recoveries happened. This must read as
reassurance ("it is working hard for you"), not as an error list. If this cannot be made
calm, propose omitting it.

**E. Actions**

- Primary: start / stop protection
- Secondary: "Recover now" (force an immediate check)

**Required states to design:** first run (nothing selected yet), protection off, Shizuku
missing or permission not granted, everything healthy, one app deferred, Qidi recently
recovered from being killed.

### 5.2 Protected apps

- Search field at top.
- Two sections: **Protected** (selected, pinned to top) then **All apps**.
- Row anatomy: app icon, app name, package name as secondary text, a state indicator
  (running / stopped / deferred), and a toggle.
- Qidi's own entry is always on and cannot be turned off — show this as locked with a short
  reason rather than a disabled-looking control with no explanation.
- Empty state: guidance to pick the apps that matter.

### 5.3 Settings

Deliberately short.

- **Record diagnostic log** — a single toggle. Subtitle explains it is only needed for
  troubleshooting and consumes storage. Default on. When on, show current log size and a
  "Delete log now" action. This replaces the entire removed log-browsing screen.
- **Alert me to problems** — toggle for problem notifications (default on).
- **Status notification** — explained as always-on and why (see §6); not user-disableable
  from within Qidi.
- **Only launch apps while the screen is off** — controls whether Qidi may visibly launch an
  app that has no invisible way to start. Default on, meaning Qidi never interrupts you.
- About / version.

---

## 6. Notification design

Notifications are a core part of this product's UX and should be designed alongside the
screens. The current behaviour updates a single notification on every check cycle, which the
user experiences as noise. The redesign splits it in two.

### Channel 1 — "Qidi status" (heartbeat)

- Minimum or low importance, silent, ongoing, no badge.
- Content is stable: something like **"Qidi · protecting 7 apps"**. It must **not** rewrite
  itself on every check; update only when the state genuinely changes.
- Android requires this notification while Qidi's service runs, so it cannot be removed.
  That constraint is turned into a feature: **its presence means Qidi is alive, and its
  disappearance is the signal that Qidi was killed.** This is the dead man's switch the user
  relies on, so its resting appearance should be recognisable at a glance in a crowded shade.

Design the collapsed form primarily. Consider whether an expanded form adds value
(e.g. per-app dots) or is unnecessary.

### Channel 2 — "Qidi problems" (alerts)

Default importance, may make sound. Used **only** for conditions the user must know about:

- Shizuku is not responding, or its permission was revoked
- Qidi could not restart an app after repeated attempts
- The backup sentinel is missing
- Qidi was killed and had to be recovered (optional, possibly summarised daily rather than
  per event)

Never used for successful routine restarts. Alerts should auto-clear when the condition
resolves, and should state the recommended user action.

### Sentinel-posted alerts (verified capability)

The sentinel runs outside Qidi as a privileged shell process and **can post system
notifications on its own** — this has been tested and works on the target device. That means
the user can still be alerted when Qidi is entirely dead. These messages are plain and
unstyled; design should account for a fallback alert that does not look like the rest of the
app.

---

## 7. Visual direction

- Material 3 with dynamic colour; **dark theme is primary**.
- Status semantics need a defined palette (healthy / attention / broken / inactive) that
  survives dynamic colour and never conveys meaning through hue alone.
- Comfortable reading density; this is a screen that gets scanned, not browsed.
- Motion kept minimal and functional — state changes, not decoration.
- Accessibility: minimum 4.5:1 contrast for text, 48dp touch targets, meaningful content
  descriptions on every status indicator, and legibility at large font scales.

---

## 8. Technical constraints

The design proposal should respect these.

- `minSdk 26`, `targetSdk 36`, phone form factor only, portrait-first.
- The app currently builds its UI **programmatically with plain Android Views** and has **no
  AndroidX, Material Components, or Compose dependency** — the only dependencies are the
  Shizuku API libraries. Adopting Material 3 / Compose is expected but is a real decision:
  please flag any proposal element that requires it.
- Single activity, no navigation library today.
- No network access, no accounts, no cloud, no onboarding beyond granting Shizuku permission.
- A persistent foreground-service notification is mandatory and cannot be designed away.
- The launcher icon is currently a placeholder; a proposal is welcome but optional.

---

## 9. Non-goals

- No log viewer, no raw shell output, no exit-info dumps in the UI.
- No heavy analytics or dashboard charting.
- No multi-device, sync, or account features.
- No settings sprawl — if a setting is not needed monthly, it probably should not exist.
- No gamification of uptime.

---

## 10. Requested deliverables

1. Status, Protected apps, and Settings screens in light and dark.
2. All required states listed in §5.1, plus empty and error states.
3. Notification mockups for both channels, collapsed and expanded, shown in a realistic
   notification shade.
4. Component inventory and design tokens (colour roles, type scale, spacing, status
   indicators).
5. A short rationale for the health-hero concept and the recent-activity treatment.

---

## 11. Open questions for the designer

1. How should "deferred until screen off" be shown so it reads as *considerate* rather than
   *failing*? It is arguably the most nuanced state in the product.
2. Should recovery history be visualised at all, or reduced to a single number? The risk is
   turning a calm screen into an anxiety-inducing incident feed.
3. How prominent should the "backup sentinel" be? It is internal plumbing the user never
   configures, yet it is what saves the system when everything else dies.
4. Should the status notification convey degraded health, or stay visually constant so that
   only its presence or absence carries meaning?
