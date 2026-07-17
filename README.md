# Qidi

Qidi is an Android/Shizuku tool for tracing and defending background apps on aggressive OEM Android builds.

Initial goals:

- Let the user choose apps that should keep running in the background.
- Reapply Doze whitelist, App Standby bucket, and AppOps protections through Shizuku.
- Trace termination incidents with `ApplicationExitInfo`, package stopped state, ActivityManager event logs, and nearby OEM process state.
- Show honest evidence about whether an app was force-stopped, background-killed, or killed by ordinary memory pressure.

This repository started from investigation of a TCL Android device where protected apps repeatedly reached `stopped=true` and ActivityManager recorded `FORCE STOP` / `KILL BACKGROUND` exits from `system_server`.

## Build Status

The source skeleton is present, but this machine does not currently have `gradle` on PATH and the Gradle wrapper has not been generated yet.

After installing Gradle or opening the project in Android Studio, generate/check the wrapper and build with:

```bash
gradle wrapper
./gradlew assembleDebug
```

## Current Package

```text
app.qidi
```

## Notes

Local phone dumps, APK backups, and raw trace notes are kept outside Git in `archive/`.
