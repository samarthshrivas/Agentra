package com.agentra.app.hierarchy

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Executes shell commands on the Android device and returns the output.
 * Uses `Runtime.exec()` to run commands via `sh`.
 *
 * WARNING: Shell commands can modify system state. Only execute
 * known-safe commands (input taps, am start, etc.).
 */
object ShellExecutor {

    private const val TAG = "ShellExecutor"
    private const val TIMEOUT_MS = 5000L

    /**
     * Executes a shell command and returns the combined stdout + stderr.
     *
     * @param command The shell command to execute (e.g., "input tap 500 1000")
     * @return ShellResult with success flag and output/error message
     */
    fun execute(command: String): ShellResult {
        return try {
            Log.d(TAG, "Executing: $command")

            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))

            // Read stdout
            val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
            val stdout = stdoutReader.readText()

            // Read stderr
            val stderrReader = BufferedReader(InputStreamReader(process.errorStream))
            val stderr = stderrReader.readText()

            // Wait for completion with timeout
            val exited = process.waitFor()

            val exitCode = process.exitValue()
            val output = buildString {
                if (stdout.isNotBlank()) appendLine(stdout.trimEnd())
                if (stderr.isNotBlank()) appendLine("stderr: ${stderr.trimEnd()}")
            }.trimEnd()

            Log.d(TAG, "Exit code: $exitCode | Output: ${output.take(200)}")

            if (exitCode == 0) {
                ShellResult(true, output.ifEmpty { "(command succeeded with no output)" })
            } else {
                ShellResult(false, output.ifEmpty { "Exit code: $exitCode" })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Shell command failed: ${e.message}", e)
            ShellResult(false, "Shell error: ${e.message}")
        }
    }

    /**
     * Convenience: inject a tap event at screen coordinates.
     */
    fun tap(x: Int, y: Int): ShellResult = execute("input tap $x $y")

    /**
     * Convenience: inject a swipe gesture.
     */
    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int = 300): ShellResult =
        execute("input swipe $x1 $y1 $x2 $y2 $durationMs")

    /**
     * Convenience: type text via shell (requires focused input field).
     */
    fun type(text: String): ShellResult = execute("input text ${escapeShellArg(text)}")

    /**
     * Convenience: press a key via shell.
     */
    fun keyEvent(keyCode: Int): ShellResult = execute("input keyevent $keyCode")

    /**
     * Convenience: launch an app via shell.
     */
    fun launchApp(packageName: String): ShellResult =
        execute("am start -n $packageName/.MainActivity")

    private fun escapeShellArg(arg: String): String {
        return arg
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace(" ", "\\ ")
            .replace("(", "\\(")
            .replace(")", "\\)")
            .replace("&", "\\&")
            .replace("|", "\\|")
            .replace(";", "\\;")
            .replace("\n", " ")
            .replace("\r", " ")
    }

    data class ShellResult(
        val success: Boolean,
        val output: String
    )
}
