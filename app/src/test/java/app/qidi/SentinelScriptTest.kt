package app.qidi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Checks the generated sentinel script, which otherwise only fails once it is on the device. */
class SentinelScriptTest {
    private val script: String = QidiCommands.installSentinelCommand()
        .substringAfter("<< 'QIDI_SENTINEL_EOF'\n")
        .substringBefore("\nQIDI_SENTINEL_EOF")

    private fun sh(vararg args: String, input: String): Pair<Int, String> {
        val process = ProcessBuilder("sh", *args).redirectErrorStream(true).start()
        process.outputStream.bufferedWriter().use { it.write(input) }
        val output = process.inputStream.bufferedReader().readText()
        return process.waitFor() to output
    }

    @Test
    fun scriptIsValidShell() {
        assertTrue(script.startsWith("#!/system/bin/sh"))
        assertFalse("Kotlin placeholder left in script", script.contains('＄'))
        val (code, output) = sh("-n", input = script)
        assertEquals("sh -n: $output", 0, code)
    }

    private fun lastExit(dump: String, pkg: String): String {
        val function = Regex("(?ms)^last_exit\\(\\) \\{.*?^}$").find(script)!!.value
        val fixture = File.createTempFile("exit-info", ".txt").apply { writeText(dump) }
        try {
            val (_, output) = sh(
                input = "$function\ndumpsys() { cat '${fixture.absolutePath}'; }\nlast_exit '$pkg'\n"
            )
            return output.trim()
        } finally {
            fixture.delete()
        }
    }

    // Shape copied from a real `dumpsys activity exit-info` on the target device, names replaced.
    private val dump = """
        dumpKillWhiteList:
        [com.example.music]
        ACTIVITY MANAGER PROCESS EXIT INFO (dumpsys activity exit-info)
          package: com.example.app
            Historical Process Exit for uid=0
                ApplicationExitInfo #0:
                  timestamp=2026-11-13 18:00:00.000 pid=1 realUid=99659 packageUid=10478 definingUid=10192 user=0
                  process=com.google.android.webview:sandboxed_process0:x:1 reason=1 (EXIT_SELF) subreason=0 (UNKNOWN) status=0
                  importance=300   description=null state=empty trace=null
            Historical Process Exit for uid=10478
                ApplicationExitInfo #0:
                  timestamp=2026-11-13 16:42:52.950 pid=2 realUid=10478 packageUid=10478 definingUid=10478 user=0
                  process=com.example.app reason=2 (SIGNALED) subreason=0 (UNKNOWN) status=9
                  importance=400   description=tguard-kill state=71 bytes trace=null
                ApplicationExitInfo #1:
                  timestamp=2026-11-13 15:32:36.703 pid=3 realUid=10478 packageUid=10478 definingUid=10478 user=0
                  process=com.example.app reason=13 (OTHER KILLS BY SYSTEM) subreason=3 (TOO MANY EMPTY PROCS) status=0
                  importance=400   description=too many empty state=empty trace=null
          package: com.example.crashy
            Historical Process Exit for uid=10500
                ApplicationExitInfo #0:
                  timestamp=2026-10-13 11:41:54.737 pid=4 realUid=10500 packageUid=10500 definingUid=10500 user=0
                  process=com.example.crashy reason=4 (APP CRASH(EXCEPTION)) subreason=0 (UNKNOWN) status=0
                  importance=125   description=crash state=empty trace=null
                ApplicationExitInfo #1:
                  timestamp=2026-09-29 09:54:00.664 pid=5 realUid=10500 packageUid=10500 definingUid=10500 user=0
                  process=com.example.crashy reason=10 (USER REQUESTED) subreason=21 (FORCE STOP) status=0
                  importance=125   description=stop com.example.crashy due to from pid 1600 (system) state=empty trace=null
    """.trimIndent()

    @Test
    fun lastExitPicksLatestMainProcessRecord() {
        assertEquals(
            "2026-11-13 16:42:52 SIGNALED/UNKNOWN (tguard-kill)",
            lastExit(dump.substringBefore("  package: com.example.crashy"), "com.example.app")
        )
    }

    @Test
    fun lastExitHandlesParenthesesInReasonNames() {
        assertEquals(
            "2026-10-13 11:41:54 APP CRASH(EXCEPTION)/UNKNOWN (crash)",
            lastExit(dump.substringAfter("  package: com.example.crashy").let { "  package: com.example.crashy$it" }, "com.example.crashy")
        )
    }

    @Test
    fun lastExitWithoutRecords() {
        assertEquals("no exit record", lastExit("ACTIVITY MANAGER PROCESS EXIT INFO\n", "com.example.none"))
    }

    // ---- brightness floor ----

    private fun floor(floorValue: String?, display: String, mode: String = "1", awake: Boolean = true): String {
        val function = Regex("(?ms)^enforce_brightness_floor\\(\\) \\{.*?^}$").find(script)!!.value
        val dir = createTempDir("qidi-floor")
        try {
            val floorFile = File(dir, "brightness-floor")
            if (floorValue != null) floorFile.writeText(floorValue)
            val displayFile = File(dir, "display.txt").apply { writeText(display) }
            val (_, output) = sh(
                input = """
                    FLOOR_FILE='${floorFile.absolutePath}'
                    settings() { echo '$mode'; }
                    screen_awake() { ${if (awake) "true" else "false"}; }
                    dumpsys() { cat '${displayFile.absolutePath}'; }
                    cmd() { echo "CMD ＄*" >> '${File(dir, "calls").absolutePath}'; }
                    log() { echo "LOG ＄1" >> '${File(dir, "calls").absolutePath}'; }
                    $function
                    enforce_brightness_floor
                    cat '${File(dir, "calls").absolutePath}' 2>/dev/null
                """.trimIndent().replace('＄', '$') + "\n"
            )
            return output.trim()
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun display(user: String, auto: String) = """
          mScreenAutoBrightness=$auto
          SimpleMappingStrategy
            mAutoBrightnessAdjustment=0.0
            mUserLux=-1.0
            mUserBrightness=$user
          Mapper for mode idle:
            mUserBrightness=0.9
    """.trimIndent()

    @Test
    fun floorRaisesDarkAutoBrightness() {
        assertEquals(
            "CMD display set-brightness 0.150\nLOG brightness floor 0.150 applied (auto chose 0.0)",
            floor("0.150", display(user = "NaN", auto = "0.0"))
        )
    }

    @Test
    fun floorRespectsUserBrightness() {
        assertEquals("", floor("0.150", display(user = "0.05", auto = "0.0")))
    }

    @Test
    fun floorLeavesBrighterAutoAlone() {
        assertEquals("", floor("0.150", display(user = "NaN", auto = "0.3")))
    }

    @Test
    fun floorNeedsFileAutoModeAndScreenOn() {
        val dark = display(user = "NaN", auto = "0.0")
        assertEquals("", floor(null, dark))
        assertEquals("", floor("0.150", dark, mode = "0"))
        assertEquals("", floor("0.150", dark, awake = false))
        assertEquals("", floor("1.5", dark))
    }
}
