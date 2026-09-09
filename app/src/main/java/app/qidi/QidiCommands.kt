package app.qidi

object QidiCommands {
    private const val SENTINEL_PID_FILE = "/data/local/tmp/qidi-watchdog-sentinel.pid"
    private const val SENTINEL_LOG_FILE = "/data/local/tmp/qidi-watchdog-sentinel.log"
    private const val SENTINEL_SCRIPT_FILE = "/data/local/tmp/qidi-sentinel.sh"
    private const val PROTECTED_LIST_FILE = "/data/local/tmp/qidi-protected-packages.txt"

    fun protectionCommands(packageName: String): List<String> {
        val packageArg = ShizukuShell.quote(packageName)
        return listOf(
            "cmd deviceidle whitelist +$packageArg",
            "am set-standby-bucket $packageArg active",
            "cmd appops set $packageArg RUN_ANY_IN_BACKGROUND allow",
            "cmd appops set $packageArg RUN_IN_BACKGROUND allow",
            "cmd appops set $packageArg START_FOREGROUND allow",
            "cmd appops set $packageArg SCHEDULE_EXACT_ALARM allow"
        )
    }

    fun processStateCommand(packageName: String): String {
        val packageArg = ShizukuShell.quote(packageName)
        return "if pidof $packageArg >/dev/null 2>&1; then echo running; " +
            "elif dumpsys package $packageArg | grep -q 'stopped=true'; then echo stopped; " +
            "else echo not_running; fi"
    }

    fun packageStateCommand(packageName: String): String {
        val packageArg = ShizukuShell.quote(packageName)
        return "dumpsys package $packageArg | grep -E 'User 0:|stopped=|enabled=|suspended=|distractionFlags=' | head -n 6"
    }

    fun recentExitInfoCommand(packageName: String): String {
        val packageArg = ShizukuShell.quote(packageName)
        return "dumpsys activity exit-info $packageArg | grep -E 'timestamp=|process=|importance=' | sed -n '1,9p'"
    }

    fun isScreenAwakeCommand(): String {
        return "dumpsys power | grep -m1 'mWakefulness=' | grep -q Awake && echo true || echo false"
    }

    fun shizukuServerAliveCommand(): String {
        return "pidof shizuku_server >/dev/null 2>&1 && echo true || echo false"
    }

    /** Restarts an app without pulling it to the foreground; see [sentinelScript]. */
    fun quietRestartCommand(packageName: String): String {
        return "sh ${ShizukuShell.quote(SENTINEL_SCRIPT_FILE)} start ${ShizukuShell.quote(packageName)}"
    }

    fun startWatchdogServiceCommand(packageName: String): String {
        val actionArg = ShizukuShell.quote(QidiWatchdogService.ACTION_START)
        val serviceArg = ShizukuShell.quote("$packageName/.QidiWatchdogService")
        return "am start-foreground-service --user 0 -a $actionArg -n $serviceArg >/dev/null 2>&1 || true"
    }

    fun writeProtectedPackagesCommand(packages: Collection<String>): String {
        val body = packages.filter { it.isNotBlank() }.joinToString("\n")
        return "printf '%s\\n' ${ShizukuShell.quote(body)} > ${ShizukuShell.quote(PROTECTED_LIST_FILE)}"
    }

    fun sentinelLogCommand(lines: Int = 30): String {
        return "tail -n $lines ${ShizukuShell.quote(SENTINEL_LOG_FILE)} 2>/dev/null || true"
    }

    /** Rewrites the sentinel script and restarts it only when the script or the process changed. */
    fun installSentinelCommand(): String {
        val header = """
            script=${ShizukuShell.quote(SENTINEL_SCRIPT_FILE)}
            pidfile=${ShizukuShell.quote(SENTINEL_PID_FILE)}
            cat > "＄script.new" << 'QIDI_SENTINEL_EOF'
        """.trimIndent().shellDollars()

        val footer = """
            QIDI_SENTINEL_EOF
            chmod 755 "＄script.new"
            if [ -f "＄script" ] && cmp -s "＄script" "＄script.new" && [ -f "＄pidfile" ] && kill -0 "＄(cat "＄pidfile")" 2>/dev/null; then
              rm -f "＄script.new"
              echo sentinel-ok
              exit 0
            fi
            mv "＄script.new" "＄script"
            if [ -f "＄pidfile" ]; then kill "＄(cat "＄pidfile")" >/dev/null 2>&1 || true; fi
            nohup sh "＄script" >/dev/null 2>&1 &
            echo ＄! > "＄pidfile"
            echo sentinel-started
        """.trimIndent().shellDollars()

        return "$header\n${sentinelScript()}\n$footer"
    }

    fun stopSentinelCommand(): String {
        return """
            pidfile=${ShizukuShell.quote(SENTINEL_PID_FILE)}
            if [ -f "＄pidfile" ]; then kill "＄(cat "＄pidfile")" >/dev/null 2>&1 || true; fi
            rm -f "＄pidfile"
        """.trimIndent().shellDollars()
    }

    /**
     * Runs as `shell` detached from Qidi, so it survives force-stop of the app and death of
     * Shizuku. Apps are started through a service or receiver so they do not steal the screen;
     * a launcher activity is only used while the display is off.
     */
    private fun sentinelScript(): String = """
        #!/system/bin/sh
        LOG=$SENTINEL_LOG_FILE
        PKG_LIST=$PROTECTED_LIST_FILE
        QIDI=app.qidi
        QIDI_SERVICE=app.qidi/.QidiWatchdogService
        QIDI_ACTION=${QidiWatchdogService.ACTION_START}
        WAKE_ACTION=app.qidi.action.WAKE
        SHIZUKU=moe.shizuku.privileged.api

        log() { echo "＄(date '+%F %T') ＄1" >> "＄LOG"; }

        trim_log() {
          [ -f "＄LOG" ] || return 0
          n=＄(wc -l < "＄LOG" 2>/dev/null) || return 0
          [ "＄n" -gt 800 ] || return 0
          tail -n 400 "＄LOG" > "＄LOG.new" 2>/dev/null && mv "＄LOG.new" "＄LOG"
        }

        running() { pidof "＄1" >/dev/null 2>&1; }
        stopped() { dumpsys package "＄1" 2>/dev/null | grep -q 'stopped=true'; }
        screen_awake() { dumpsys power 2>/dev/null | grep -m1 'mWakefulness=' | grep -q Awake; }

        component() {
          dumpsys package "＄1" 2>/dev/null | awk -v table="＄2" -v prefix="＄1/" '
            index(＄0, table) > 0 { found = 1; next }
            found && index(＄2, prefix) == 1 { print ＄2; exit }
          '
        }

        quiet_start() {
          pkg=＄1
          running "＄pkg" && return 0
          cmd package unstop --user 0 "＄pkg" >/dev/null 2>&1
          svc=＄(component "＄pkg" "Service Resolver Table:")
          if [ -n "＄svc" ]; then
            am start-service --user 0 -n "＄svc" >/dev/null 2>&1 ||
              am start-foreground-service --user 0 -n "＄svc" >/dev/null 2>&1
          fi
          running "＄pkg" && { log "background-start service ＄pkg"; return 0; }
          rcv=＄(component "＄pkg" "Receiver Resolver Table:")
          if [ -n "＄rcv" ]; then
            am broadcast --user 0 -n "＄rcv" -a "＄WAKE_ACTION" >/dev/null 2>&1
          fi
          running "＄pkg" && { log "background-start receiver ＄pkg"; return 0; }
          if screen_awake; then
            log "deferred ＄pkg until screen off"
            return 1
          fi
          act=＄(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p "＄pkg" 2>/dev/null | tail -n 1)
          case "＄act" in
            */*) am start --user 0 -n "＄act" >/dev/null 2>&1; log "activity-start ＄pkg while screen off" ;;
            *) log "no background entry point for ＄pkg" ;;
          esac
        }

        ensure_shizuku() {
          running shizuku_server && return 0
          dir=＄(pm path "＄SHIZUKU" 2>/dev/null | head -n 1 | sed 's/^package://; s#/base.apk##')
          [ -n "＄dir" ] || return 1
          for abi in arm64 arm x86_64 x86; do
            starter="＄dir/lib/＄abi/libshizuku.so"
            [ -x "＄starter" ] || continue
            log "shizuku_server missing; running starter"
            "＄starter" >/dev/null 2>&1
            sleep 3
            if running shizuku_server; then log "shizuku_server restarted"; else log "shizuku_server restart failed"; fi
            return 0
          done
          log "shizuku starter binary not found"
          return 1
        }

        ensure_qidi() {
          if stopped "＄QIDI" || ! running "＄QIDI"; then
            log "restarting ＄QIDI"
            cmd package unstop --user 0 "＄QIDI" >/dev/null 2>&1
            am start-foreground-service --user 0 -a "＄QIDI_ACTION" -n "＄QIDI_SERVICE" >/dev/null 2>&1
            sleep 2
            if ! running "＄QIDI" && ! screen_awake; then
              am start --user 0 -n "＄QIDI/.MainActivity" >/dev/null 2>&1
              log "＄QIDI activity fallback while screen off"
            fi
          fi
        }

        if [ "＄1" = "start" ] && [ -n "＄2" ]; then
          quiet_start "＄2"
          exit 0
        fi

        while true; do
          sleep 30
          trim_log
          ensure_shizuku
          ensure_qidi
          [ -f "＄PKG_LIST" ] || continue
          for pkg in ＄(cat "＄PKG_LIST" 2>/dev/null); do
            [ "＄pkg" = "＄QIDI" ] && continue
            running "＄pkg" || quiet_start "＄pkg"
          done
        done
    """.trimIndent().shellDollars()

    // Shell scripts above are written with ＄ so Kotlin does not read them as string templates.
    private fun String.shellDollars(): String = replace('＄', '$')
}
