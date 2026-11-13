# CLAUDE.md

@AGENTS.md

`AGENTS.md` (imported above) is the main briefing. It covers the adversary, the architecture,
the debugging playbook, the verified facts and the dead ends. This file adds what Claude Code
needs on top of it. If the two disagree, check the code, then fix whichever file is wrong.

## Working rules

- **Read `AGENTS.md` §7 and §8 before proposing a fix.** Most "obvious" ideas (alarms,
  disabling Smart Manager, `am start`, `BOOT_COMPLETED`, MIN-importance notifications) have
  already been tried on the device and failed.
- **The phone is the owner's daily phone.** Ask before anything that changes device state
  beyond reading: `am force-stop`, killing `shizuku_server`, installing an APK, changing
  settings. Restore any setting you change. Never uninstall Qidi: that wipes the protected
  list.
- **Signatures:** the device runs the CI-signed release build. A debug APK *cannot* be
  installed over it without uninstalling first (see the rule above). Ship device changes as a
  signed release, or ask the owner.
- **Privacy:** real package names of the owner's apps appear in every device dump, in the
  sentinel log and in `qidi-protected-packages.txt`. Pulled data goes in `archive/` (gitignored)
  only. Never quote those names in commits, PRs, issues, tracked docs or code. Use placeholders
  such as `com.example.chat`.
- **Shell code lives in `QidiCommands.kt`** as Kotlin strings using the `＄` placeholder. The
  sentinel script is regenerated from there and reinstalled by Qidi when its content changes
  (`installSentinelCommand` compares with `cmp`). Never edit the script on the device by hand.
  It will be overwritten.
- **Verify with a build:** `./gradlew assembleDebug`. There are no unit tests. Changes to shell
  logic should be dry-run on the device with `adb shell` before being baked into the script.
- Commit only when asked. Branch off `main` first.

## Getting logs off the phone

The device runs a **release** build, so `run-as app.qidi` fails. Qidi's own logs
(`files/qidi-events.log`, `files/qidi-field-events.log`) are **not reachable right now**.
There is no export path or log viewer in the app (the UI shows only size + delete), and
`adb backup` excludes app data for targetSdk ≥ 31. The sources that *are* reachable:

| Source | What it shows | Retention |
| --- | --- | --- |
| `/data/local/tmp/qidi-watchdog-sentinel.log` | sentinel restarts of Qidi, apps, Shizuku | trimmed to 400 lines once it passes 800 |
| `dumpsys activity exit-info <pkg>` | why/when each process died (reason, subreason, description) | last few records per package |
| `logcat -d -b all` | ActivityManager force-stop lines, kills | hours (ring buffer) |
| `dumpsys alarm`, `dumpsys deviceidle`, `am get-standby-bucket`, `cmd appops get` | whether the protections held | current state only |
| `dumpsys usagestats` | foreground/background transitions | days |

Collect into a dated folder under `archive/`:

```bash
D=archive/logs-$(date +%F); mkdir -p "$D"
adb shell cat /data/local/tmp/qidi-watchdog-sentinel.log       > "$D/sentinel.log"
adb shell cat /data/local/tmp/qidi-protected-packages.txt      > "$D/protected.txt"
for p in app.qidi moe.shizuku.privileged.api $(tr -d '\r' < "$D/protected.txt"); do
  adb shell dumpsys activity exit-info "$p"                     > "$D/exit-info-$p.txt"
done
adb shell logcat -d -b all                                      > "$D/logcat.txt"
adb shell dumpsys alarm                                         > "$D/alarm.txt"
adb shell dumpsys deviceidle whitelist                          > "$D/deviceidle.txt"
adb shell dumpsys package app.qidi | grep -E 'versionName|versionCode|firstInstall|lastUpdate' > "$D/qidi-version.txt"
```

Check that the phone is connected first (`adb devices`; if the list is empty, `lsusb` should
show `T951K`). Analyse the data from `archive/` and keep findings that contain package names
there too.

If Qidi's internal field log is needed, the app needs a way to hand it out first. One option
fits the existing design: have the watchdog copy it through Shizuku into `/data/local/tmp/`,
where `adb` can read it. Another is a share intent. Either is a product decision, since
`AGENTS.md` §9 rules out a log-browsing UI. Ask before building one.

## Known gaps found in the code (not yet acted on)

- **`screen_off_only` setting is not enforced.** `QidiSettings.isScreenOffOnly` is shown in
  Settings but nothing in the watchdog or the sentinel reads it. The sentinel always defers
  while the screen is on.
- `QidiEventLog.recent()` / `QidiFieldLog.recent()` have no callers.
- Restart rate limiting (`3 per 60 s`) and `lastKnownRunning` are in memory, so they reset
  every time Qidi is force-stopped. That happens on every Smart Manager batch.
- Both logs read and rewrite the whole file on every append (up to 2 000 lines).
- The installed `v0.2.1` build reports `0.2.0`/`2` because it was tagged without a bump.
  Fixed going forward (`0.3.0`/`3`).
- `QidiBootReceiver.kt` has a mis-indented line. It is cosmetic.

## Field findings (2026-11-13, details in `archive/logs-2026-11-13/FINDINGS.md`)

- No Smart Manager force-stops since 2026-09-29. The active killer is now **TGuard**
  (`com.tcl.guard`, exit description `tguard-kill`). It is a memory-pressure killer with an
  "auto restart" allowlist it skips (`skipped kill as auto restart:<pkg>` in logcat).
- **`shizuku_server` died of OutOfMemoryError about every 8 h** (`ParcelFileDescriptor Transfer
  Thread`). Cause: the Shizuku client caches every `newProcess` result in a static set that
  is only cleared when the server dies, so the server leaked one `RemoteProcessHolder` per
  command (~15 per 15 s cycle). **Fix (v0.3.1):** `ShizukuShell` keeps one long-lived `sh`
  session and sends each command to it framed by a random marker. Never go back to one
  `newProcess` per command. To verify, watch `VmRSS` of `shizuku_server` stay flat.
- **Shizuku UserService does not work on this device** (tried in v0.3.0). Shizuku's
  `ServiceStarter` crashes with an NPE in `LoadedApk.makeApplicationInner` on Android 16 /
  Shizuku 13.6.0 before any app code runs. Do not retry it without new evidence.
- Most sentinel restarts answer `TOO MANY EMPTY PROCS` (normal cached-process trimming). They
  ping-pong every 20–30 min and achieve nothing.

## Status (2026-11-13)

Qidi has been running on the owner's phone for about two months and works well. The next step
is to pull logs (above) and look for improvements based on real data. Good questions for that
analysis: how often do kills happen, and at what times? How long until recovery? Do any apps
end up `deferred` or failing? Does Shizuku itself die? Does the sentinel ever die?
