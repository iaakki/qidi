package app.qidi

object QidiCommands {
    private const val SENTINEL_PID_FILE = "/data/local/tmp/qidi-watchdog-sentinel.pid"
    private const val SENTINEL_LOG_FILE = "/data/local/tmp/qidi-watchdog-sentinel.log"

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

    fun restartCommand(packageName: String): String {
        val packageArg = ShizukuShell.quote(packageName)
        return "if pidof $packageArg >/dev/null 2>&1; then echo running; else " +
            "activity=\$(cmd package resolve-activity --brief -a android.intent.action.MAIN " +
            "-c android.intent.category.LAUNCHER -p $packageArg 2>/dev/null | tail -n 1); " +
            "if [ -n \"\$activity\" ] && [ \"\$activity\" != \"No activity found\" ]; then " +
            "am start -W --user 0 -n \"\$activity\"; else monkey -p $packageArg 1; fi; fi"
    }

    fun isPackageFocusedCommand(packageName: String): String {
        val packageArg = ShizukuShell.quote(packageName)
        return "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' | grep -F $packageArg >/dev/null && echo true || echo false"
    }

    fun startQidiCommand(packageName: String): String {
        return "am start -W --user 0 -n ${ShizukuShell.quote("$packageName/.MainActivity")} >/dev/null 2>&1 || true"
    }

    fun startSentinelCommand(packageName: String): String {
        val shellDollar = "$"
        val packageArg = ShizukuShell.quote(packageName)
        val serviceArg = ShizukuShell.quote("$packageName/.QidiWatchdogService")
        val activityArg = ShizukuShell.quote("$packageName/.MainActivity")
        val actionArg = ShizukuShell.quote(QidiWatchdogService.ACTION_START)
        return listOf(
            "pidfile=${ShizukuShell.quote(SENTINEL_PID_FILE)}",
            "logfile=${ShizukuShell.quote(SENTINEL_LOG_FILE)}",
            "if [ -f \"${shellDollar}pidfile\" ] && kill -0 \"${shellDollar}(cat \"${shellDollar}pidfile\")\" 2>/dev/null; then exit 0; fi",
            "( while true; do sleep 30; if dumpsys package $packageArg | grep -q 'stopped=true'; then " +
                "echo \"${shellDollar}(date '+%F %T') restarting $packageName\" >> \"${shellDollar}logfile\"; " +
                "cmd package unstop --user 0 $packageArg >/dev/null 2>&1 || true; " +
                "am start-foreground-service --user 0 -a $actionArg -n $serviceArg >/dev/null 2>&1 || " +
                "am start --user 0 -n $activityArg >/dev/null 2>&1 || true; " +
                "fi; done ) >/dev/null 2>&1 & echo ${shellDollar}! > \"${shellDollar}pidfile\""
        ).joinToString("; ")
    }

    fun stopSentinelCommand(): String {
        val shellDollar = "$"
        return listOf(
            "pidfile=${ShizukuShell.quote(SENTINEL_PID_FILE)}",
            "if [ -f \"${shellDollar}pidfile\" ]; then kill \"${shellDollar}(cat \"${shellDollar}pidfile\")\" >/dev/null 2>&1 || true; fi",
            "rm -f \"${shellDollar}pidfile\""
        ).joinToString("; ")
    }
}