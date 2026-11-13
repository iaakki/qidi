# Agent guide to Qidi

Context for an AI agent working on this repository. It covers what the project is, what it is
fighting, how to debug it on a real device, and which approaches have already been tried and
ruled out. Read the "Verified facts" and "Dead ends" sections before proposing changes — most of
it was established by on-device experiment and is expensive to rediscover.

---

## 1. What Qidi is

An Android app that keeps chosen apps alive on a phone whose manufacturer aggressively
force-stops background apps. It drives privileged shell commands through
[Shizuku](https://shizuku.rikka.app/) — **no root** — detects when a protected app has been
killed, and restarts it without stealing the foreground.

Package `app.qidi`. `minSdk 26`, `targetSdk 36`. Kotlin 2.0.21, AGP 8.7.3, Jetpack Compose +
Material 3.

## 2. The adversary

The target device is a **TCL T951K ("MonaLisa")**. Its OEM cleanup app is
`com.tct.onetouchbooster` ("Smart Manager", `/system/app/SmartManager/SmartManager.apk`).

Observed behaviour, from `dumpsys activity exit-info`:

```text
reason=10 (USER REQUESTED) subreason=21 (FORCE STOP)
description=stop <pkg> due to from pid 1225 (system)
```

Key characteristics:

- **Batch kills.** Every protected app plus Qidi itself died inside ~1.1 seconds in one
  observed event. There is no useful window for Qidi to react from inside its own process.
- **Android usually records `system_server` as the executor**, not the OEM app that requested
  it. Occasionally the exit record names `com.tct.onetouchbooster` directly, which is the only
  hard evidence linking Smart Manager to the kills.
- **Frequency**: roughly 2–3 times per day. Over one five-week stretch the sentinel restarted
  Qidi **181 times**.
- **Force-stop also cancels the app's pending alarms.** Confirmed in `dumpsys alarm` with
  `Reason=pi_cancelled` at the exact second of the kill.

## 3. Architecture — four layers of defence

1. **Protections** (`QidiCommands.protectionCommands`) — Doze whitelist, `active` standby
   bucket, background AppOps, reapplied every cycle. Necessary but *not sufficient*: apps that
   were already whitelisted still got force-stopped.
2. **Watchdog** (`QidiWatchdogService`) — foreground service, polls every 15 s, restarts
   anything stopped. Dies whenever Qidi is force-stopped.
3. **Recovery alarm** (`QidiWatchdogScheduler`) — 30-minute `setAndAllowWhileIdle` alarm.
   Largely symbolic, because force-stop cancels it. Kept as a cheap backstop for process death
   that is *not* a package force-stop.
4. **Shell sentinel** — the layer that actually works. A script at
   `/data/local/tmp/qidi-sentinel.sh`, launched through Shizuku, running as uid `shell` with
   **PPID 1**. Being outside Qidi's package, a package force-stop cannot touch it. It restarts
   Qidi, restarts the protected apps, and restarts Shizuku itself.

Sentinel files on device:

```text
/data/local/tmp/qidi-sentinel.sh                 the script (generated from QidiCommands)
/data/local/tmp/qidi-watchdog-sentinel.pid       pid of the running loop
/data/local/tmp/qidi-watchdog-sentinel.log       its log (survives app uninstall)
/data/local/tmp/qidi-protected-packages.txt      package list written by Qidi for the sentinel
```

The script is the single source of truth for restart logic — `QidiWatchdogService` invokes
`sh /data/local/tmp/qidi-sentinel.sh start <pkg>` rather than duplicating it.

## 4. Key files

```text
app/src/main/java/app/qidi/
  QidiWatchdogService.kt   polling loop, restart decisions, rate limiting
  QidiCommands.kt          every shell command + the sentinel script (generated as a string)
  ShizukuShell.kt          runs commands in one long-lived Shizuku `sh` session (one
                           newProcess per command leaked memory in shizuku_server)
  QidiNotifications.kt     two channels: silent heartbeat, problem alerts
  QidiSettings.kt          prefs, recovery counters, toggles
  QidiEventLog.kt          human-readable event log (gated by the logging toggle)
  QidiFieldLog.kt          incident log with package state + exit-info (same gate)
  QidiWatchdogScheduler.kt the 30-minute recovery alarm
  ui/                      Compose theme, three screens, state loader
```

`QidiCommands.kt` uses a `＄` placeholder for shell `$`, converted by `String.shellDollars()`,
so Kotlin does not interpret shell variables as string templates. Keep that convention.

## 5. Build, install, release

The Gradle wrapper is checked in. JDK 17+ and the Android SDK are required.

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Releases are triggered by pushing a tag starting with `v`; see
`.github/workflows/release.yml`. The build is signed from four repository secrets
(`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`), which Gradle reads as the
env vars `QIDI_KEYSTORE`, `QIDI_KEYSTORE_PASSWORD`, `QIDI_KEY_ALIAS`, `QIDI_KEY_PASSWORD`. The
workflow fails fast if the secrets are missing rather than shipping an un-upgradable APK.

**Bump `versionCode`/`versionName` in `app/build.gradle.kts` before tagging**, and make the tag
match `versionName` (e.g. `3` / `0.3.0` → `v0.3.0`). `v0.2.1` was tagged without a bump and
reports itself as `0.2.0`.

## 6. Debugging playbook

All of these need `adb`. The phone is connected over USB intermittently; check first.

**Is everything alive?**

```bash
adb shell 'echo "qidi: $(pidof app.qidi || echo none)";
  echo "shizuku_server: $(pidof shizuku_server || echo none)";
  ps -A -o PID,PPID,ARGS | grep -F qidi-sentinel.sh | grep -v grep'
```

`shizuku_server` is the privileged server; `moe.shizuku.privileged.api` is only the UI process.
**Seeing the app process does not mean Shizuku works** — check `shizuku_server`.

**What killed something, and when**

```bash
adb shell 'dumpsys activity exit-info <pkg> | grep -E "timestamp=|process=|importance=" | head -n 20'
```

**Sentinel activity** (survives uninstall, readable on release builds):

```bash
adb shell tail -n 40 /data/local/tmp/qidi-watchdog-sentinel.log
```

**Qidi's own logs** — only on **debug** builds, since release builds are not debuggable:

```bash
adb shell 'run-as app.qidi tail -n 40 files/qidi-field-events.log' | cut -c1-200
adb shell 'run-as app.qidi cat shared_prefs/qidi.xml'
```

On a release build these files are currently unreachable: the app has no log viewer or
export (only size + delete). Use the sentinel log and `dumpsys` sources instead.

**Recovery alarm registered?**

```bash
adb shell 'dumpsys alarm | grep -Ei -A 5 "app\.qidi\.action\.RECOVER_WATCHDOG"'
```

**Notification state**

```bash
adb shell 'dumpsys activity services app.qidi | grep -E "ServiceRecord|isForeground|channel="'
```

**Screen state** (drives the deferred-restart logic)

```bash
adb shell 'dumpsys power | grep -m1 mWakefulness='   # Awake / Asleep / Dozing
```

**Simulate a Smart Manager kill**

```bash
adb shell am force-stop <pkg>          # one app
adb shell am force-stop app.qidi       # Qidi itself
adb shell kill $(adb shell pidof shizuku_server)   # Shizuku
```

Then watch recovery. Expect the sentinel to act within ~30 s.

**Screenshots** — the phone's screen timeout is short, so raise it, capture, restore:

```bash
ORIG=$(adb shell settings get system screen_off_timeout | tr -d '\r')
adb shell settings put system screen_off_timeout 300000
adb shell 'input keyevent KEYCODE_WAKEUP; sleep 1; input swipe 540 1800 540 700'
adb exec-out screencap -p > shot.png
adb shell settings put system screen_off_timeout "$ORIG"
```

## 7. Verified facts — do not re-litigate

Each of these was confirmed on the device.

- **`cmd package unstop --user 0 <pkg>` works** and clears `stopped=true` without launching the
  app. It is the first step of every restart.
- **An app can be started invisibly** by starting a declared service, or by sending an explicit
  broadcast to a declared receiver. Component names are extractable from `dumpsys package`:

  ```bash
  dumpsys package <pkg> | awk -v t="Service Resolver Table:" -v p="<pkg>/" \
    'index($0,t)>0{f=1;next} f&&index($2,p)==1{print $2;exit}'
  ```

- **Shizuku can be restarted from `shell` with no PC.** Its APK ships the starter as a native
  library; running it re-launches `shizuku_server`:

  ```bash
  dir=$(pm path moe.shizuku.privileged.api | head -1 | sed 's/^package://; s#/base.apk##')
  "$dir/lib/arm64/libshizuku.so"
  ```

  This is what closes the biggest hole — previously a dead Shizuku meant total paralysis.
  Allow ~3 s before checking `pidof shizuku_server`; the starter daemonises.
- **`cmd notification post` works from `shell`**, so the sentinel can alert the user even when
  Qidi is entirely dead. There is **no `cmd notification cancel`** — posted notifications must
  be swiped away.
- **Notification channel importance is frozen at creation.** Changing it in code does nothing
  for existing installs; the channel id must be versioned (hence `qidi_status_v2`).
- **Release builds are not debuggable**, so `run-as` fails after installing a release APK.
- **Vector drawables must not use `?attr/...`**; the app uses a platform theme
  (`android:Theme.Material`), so AAPT fails with `resource attr/colorControlNormal not found`.
- **Figtree is a variable font**; weights come from `FontVariation.Settings`, which requires
  `@OptIn(ExperimentalTextApi::class)`.

### Environment quirks

- **Gradle may fail inside a restricted sandbox** with
  `Could not create service of type FileAccessTimeJournal`. It is a lock/permission issue on
  `~/.gradle`, not a project problem — the same command succeeds with full filesystem access.
- **adb needs access to `/dev/bus/usb`.** A sandboxed adb server reports "no devices" even
  though the phone is plugged in. Check `lsusb` for `T & A Mobile Phones T951K` before
  concluding the phone is absent.

## 8. Dead ends — already tried, do not repeat

- **Disabling Smart Manager.** `pm disable-user --user 0 com.tct.onetouchbooster` fails with
  `SecurityException: Cannot disable protected package`. Component-level disabling fails the
  same way. AppOps clamping and `restricted` standby bucket were applied but did not stop the
  kills.
- **Relying on AlarmManager to recover from force-stop.** Android cancels the app's alarms as
  part of the force-stop, so the alarm never fires. This was the original design and it did not
  work; the sentinel replaced it.
- **Broadcasting `BOOT_COMPLETED` to wake an app.** It is a protected broadcast; `shell` is not
  allowed to send it.
- **`am start` / `monkey` to restart apps.** Both pull the app into the foreground, which was
  the single most annoying behaviour in daily use. A launcher activity is now used *only* while
  the screen is off.
- **`IMPORTANCE_MIN` for the status notification.** The design asked for it, but MIN removes the
  status-bar icon, which defeats a dead-man's switch built on the notification's *presence*.
  `IMPORTANCE_LOW` is used instead — silent, but visible.
- **Doze whitelist + standby bucket + AppOps alone.** All protected apps were already
  whitelisted and in the `active` bucket when they were force-stopped.

## 9. Behaviour worth preserving

- **Quiet by default.** Routine restarts must never produce a notification. The status
  notification only re-posts when its text actually changes — not every 15 s cycle.
- **Never steal the foreground while the user is on the phone.** If an app has no invisible
  entry point and the screen is on, it is marked `deferred` and retried after screen-off.
  Deferred is presented in the *healthy* palette; it is considerate behaviour, not a fault.
- **The status notification is the dead-man's switch.** Its disappearance is how the user learns
  Qidi was killed. Do not make it dismissible or conditional.
- **No log-browsing UI.** Diagnostics are a single on/off toggle plus the size and a delete
  button. This was an explicit product decision.

## 10. Privacy and safety rules

- **The owner's personal app package names must not enter tracked source.** They were scrubbed
  from git history once already with a `filter-branch` + force-push. `defaultProtectedPackages`
  in `QidiSettings.kt` is intentionally `emptySet()` — do not repopulate it with real apps.
- **`archive/` is gitignored** and holds phone dumps, APK backups and raw traces. Never
  `git add -f` it.
- **`docs/*.zip`, `*.jks`, `*.keystore` are gitignored.** Signing keys never go in the repo.
- **Screenshots**: `docs/images/status.png` and `settings.png` are committed. A screenshot of
  the Protected apps screen is **not** acceptable — it lists real package names.
- **Never ask for or echo the keystore, its base64, or any password.** If the user offers one,
  tell them to rotate it.
- The device is the owner's daily phone. Uninstalling Qidi wipes their protected-app list;
  changing system settings (e.g. `screen_off_timeout`) must be restored afterwards.

## 11. Current state and open items

- `main` is at the `v0.2.1` tag. Branch `fix/shizuku-userservice` carries the
  Shizuku leak fix: `v0.3.0` (UserService, broken on this device) and `v0.3.1` (shell session).
- The device runs a **signed release** build, so `run-as` is unavailable there.
- `v0.2.0`'s published release asset is **debug-signed and cannot update any install** — it
  should be deleted from GitHub along with its tag.
- The expanded notification layout from the design handoff (per-app status dots, "Recover now"
  action) is **not implemented**; only the collapsed form exists.
- There is no CI build check on pull requests, only the release workflow.
- Design references live in `docs/design_handoff_qidi_redesign/` (UI) and
  `docs/design_handoff_qidi_icon/` (icon); `docs/ui-spec.md` is the brief that produced them.
