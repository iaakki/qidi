package app.qidi

object QidiCommands {
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

    fun restartCommand(packageName: String): String {
        val packageArg = ShizukuShell.quote(packageName)
        return "if pidof $packageArg >/dev/null 2>&1; then echo running; else " +
            "activity=\$(cmd package resolve-activity --brief -a android.intent.action.MAIN " +
            "-c android.intent.category.LAUNCHER -p $packageArg 2>/dev/null | tail -n 1); " +
            "if [ -n \"\$activity\" ] && [ \"\$activity\" != \"No activity found\" ]; then " +
            "am start --user 0 -n \"\$activity\"; else monkey -p $packageArg 1; fi; fi"
    }
}