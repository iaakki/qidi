package app.qidi

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

/** Drives [ShellSession] with a local `sh`, the same protocol used through Shizuku. */
class ShellSessionTest {
    private lateinit var timeouts: ScheduledExecutorService
    private lateinit var stderrFile: File
    private lateinit var process: Process
    private lateinit var session: ShellSession

    @Before
    fun setUp() {
        timeouts = Executors.newSingleThreadScheduledExecutor()
        stderrFile = File.createTempFile("qidi-shell", ".err")
        process = ProcessBuilder("sh").start()
        session = ShellSession(process, stderrFile.absolutePath, 5, timeouts)
    }

    @After
    fun tearDown() {
        session.close()
        timeouts.shutdownNow()
        stderrFile.delete()
    }

    @Test
    fun returnsStdoutStderrAndExitCode() {
        val result = session.exec("echo hello; echo oops >&2; exit 3")!!
        assertEquals(3, result.exitCode)
        assertEquals("hello\n", result.stdout)
        assertEquals("oops\n", result.stderr)
    }

    @Test
    fun preservesOutputExactly() {
        assertEquals("no-newline", session.exec("printf no-newline")!!.stdout)
        assertEquals("", session.exec("true")!!.stdout)
        assertEquals("a\n\nb\n", session.exec("echo a; echo; echo b")!!.stdout)
        assertEquals("\n\n", session.exec("echo; echo")!!.stdout)
    }

    @Test
    fun quotesAndDollarsReachTheCommandIntact() {
        val result = session.exec("x='it''s'; echo \"\$x\" '\$HOME' \"a'b\"")!!
        assertEquals("its \$HOME a'b\n", result.stdout)
    }

    @Test
    fun exitInsideCommandDoesNotEndTheSession() {
        assertEquals(0, session.exec("exit 0")!!.exitCode)
        assertEquals("still here\n", session.exec("echo still here")!!.stdout)
    }

    @Test
    fun reusesOneShellAcrossManyCommands() {
        val pids = (1..50).map { session.exec("echo \$PPID")!!.stdout.trim() }.toSet()
        assertEquals("every sh -c must share the same parent session shell", 1, pids.size)
        assertTrue(process.isAlive)
    }

    @Test
    fun commandsCannotReadTheSessionInput() {
        assertEquals("", session.exec("cat")!!.stdout)
        assertEquals("after\n", session.exec("echo after")!!.stdout)
    }

    @Test
    fun backgroundJobWithRedirectsDoesNotBlock() {
        val result = session.exec("nohup sleep 30 >/dev/null 2>&1 & echo started")
        assertEquals("started\n", result!!.stdout)
    }

    @Test
    fun largeOutputAndLongCommandsWork() {
        val result = session.exec("i=0; while [ \$i -lt 5000 ]; do echo line\$i; i=\$((i+1)); done")!!
        assertEquals(5000, result.stdout.lines().count { it.startsWith("line") })
        val longArg = "x".repeat(20_000)
        assertEquals(20_000, session.exec("printf %s '$longArg' | wc -c")!!.stdout.trim().toInt())
    }

    @Test
    fun timeoutReturnsNull() {
        val short = ShellSession(ProcessBuilder("sh").start(), stderrFile.absolutePath, 1, timeouts)
        assertNull(short.exec("sleep 10"))
        short.close()
    }

    @Test
    fun deadShellReturnsNull() {
        process.destroy()
        process.waitFor()
        assertNull(session.exec("echo hi"))
    }
}
