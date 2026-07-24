package com.agentra.app.tools

import android.content.Context
import android.content.SharedPreferences
import com.agentra.app.hierarchy.ShellExecutor

/**
 * Registry of all available agent tools with SharedPreferences-backed
 * enabled/disabled state and support for testing each tool in isolation.
 */
class ToolRegistry(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Returns the full list of available tools with their current enabled state. */
    fun getAllTools(): List<ToolDefinition> = TOOLS.map { tool ->
        tool.copy(isEnabled = prefs.getBoolean(KEY_PREFIX + tool.id, true))
    }

    /** Returns only enabled tools. */
    fun getEnabledTools(): List<ToolDefinition> = getAllTools().filter { it.isEnabled }

    /** Update whether a tool is enabled/disabled. */
    fun setEnabled(toolId: String, enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PREFIX + toolId, enabled).apply()
    }

    /** Reset all tools to enabled. */
    fun resetAll() {
        prefs.edit().apply {
            TOOLS.forEach { putBoolean(KEY_PREFIX + it.id, true) }
            apply()
        }
    }

    /**
     * Test a tool by executing its default action via the shell.
     * Returns a ShellResult describing what happened.
     */
    fun testTool(toolId: String): ShellExecutor.ShellResult {
        val tool = TOOLS.find { it.id == toolId } ?: return ShellExecutor.ShellResult(false, "Unknown tool: $toolId")
        return when (tool.id) {
            "TAP" -> ShellExecutor.tap(
                (tool.testParams["x"] as? Int ?: 500),
                (tool.testParams["y"] as? Int ?: 1000)
            )
            "DOUBLE_TAP" -> ShellExecutor.execute(
                "input tap ${tool.testParams["x"] ?: 500} ${tool.testParams["y"] ?: 1000} && sleep 0.15 && input tap ${tool.testParams["x"] ?: 500} ${tool.testParams["y"] ?: 1000}"
            )
            "LONG_PRESS" -> ShellExecutor.swipe(
                (tool.testParams["x"] as? Int ?: 500),
                (tool.testParams["y"] as? Int ?: 1000),
                (tool.testParams["x"] as? Int ?: 500),
                (tool.testParams["y"] as? Int ?: 1000),
                (tool.testParams["duration"] as? Int ?: 1000)
            )
            "SWIPE" -> ShellExecutor.swipe(
                (tool.testParams["x1"] as? Int ?: 200),
                (tool.testParams["y1"] as? Int ?: 1000),
                (tool.testParams["x2"] as? Int ?: 800),
                (tool.testParams["y2"] as? Int ?: 1000)
            )
            "DRAG" -> ShellExecutor.swipe(
                (tool.testParams["x1"] as? Int ?: 200),
                (tool.testParams["y1"] as? Int ?: 1000),
                (tool.testParams["x2"] as? Int ?: 800),
                (tool.testParams["y2"] as? Int ?: 1000),
                (tool.testParams["duration"] as? Int ?: 1000)
            )
            "TYPE" -> ShellExecutor.type(tool.testParams["text"] as? String ?: "Hello Agentra")
            "PRESS" -> {
                val key = tool.testParams["key"] as? String ?: "back"
                val keyCode = when (key) {
                    "back" -> 4; "home" -> 3; "enter" -> 66
                    "recent" -> 187; "notifications" -> 40; "power" -> 26
                    else -> 4
                }
                ShellExecutor.keyEvent(keyCode)
            }
            "LAUNCH" -> ShellExecutor.execute(
                "am start -n ${tool.testParams["package"] ?: "com.android.chrome"}/.MainActivity"
            )
            "SCROLL" -> {
                val dir = tool.testParams["direction"] as? String ?: "up"
                val (x1, y1, x2, y2) = if (dir == "up") {
                    listOf(500, 1400, 500, 400)
                } else {
                    listOf(500, 400, 500, 1400)
                }
                ShellExecutor.swipe(x1, y1, x2, y2)
            }
            "HOVER" -> ShellExecutor.execute("input tap ${tool.testParams["x"] ?: 500} ${tool.testParams["y"] ?: 1000}")
            "COPY" -> ShellExecutor.ShellResult(true, "COPY: clipboard write is not testable via shell")
            "WAIT" -> ShellExecutor.ShellResult(true, "WAIT: 2s delay (simulated via shell sleep)")
            "SCREENSHOT" -> ShellExecutor.execute("screencap -p /sdcard/agentra_test.png && echo Captured to /sdcard/agentra_test.png")
            "NOTIFICATION" -> ShellExecutor.execute("cmd notification list")
            else -> ShellExecutor.ShellResult(false, "Tool $toolId has no test defined")
        }
    }

    companion object {
        private const val PREFS_NAME = "agentra_tool_state"
        private const val KEY_PREFIX = "tool_enabled_"

        /** The canonical list of all agent tools. */
        val TOOLS: List<ToolDefinition> = listOf(
            ToolDefinition(
                id = "TAP",
                name = "Tap",
                description = "Tap at a screen coordinate",
                testParams = mapOf("x" to 540, "y" to 1200)
            ),
            ToolDefinition(
                id = "DOUBLE_TAP",
                name = "Double Tap",
                description = "Double-tap at a screen coordinate",
                testParams = mapOf("x" to 540, "y" to 1200)
            ),
            ToolDefinition(
                id = "LONG_PRESS",
                name = "Long Press",
                description = "Long-press at a coordinate for 1 second",
                testParams = mapOf("x" to 540, "y" to 1200, "duration" to 1000)
            ),
            ToolDefinition(
                id = "SWIPE",
                name = "Swipe",
                description = "Swipe from one coordinate to another",
                testParams = mapOf("x1" to 200, "y1" to 1000, "x2" to 800, "y2" to 1000)
            ),
            ToolDefinition(
                id = "DRAG",
                name = "Drag",
                description = "Slow drag from one coordinate to another",
                testParams = mapOf("x1" to 200, "y1" to 1000, "x2" to 800, "y2" to 1000, "duration" to 1000)
            ),
            ToolDefinition(
                id = "TYPE",
                name = "Type",
                description = "Type text at the currently focused input",
                testParams = mapOf("text" to "Hello Agentra")
            ),
            ToolDefinition(
                id = "PRESS",
                name = "Key Press",
                description = "Press a system key (back, home, enter, etc.)",
                testParams = mapOf("key" to "back")
            ),
            ToolDefinition(
                id = "LAUNCH",
                name = "Launch App",
                description = "Launch an app by package name",
                testParams = mapOf("package" to "com.android.chrome")
            ),
            ToolDefinition(
                id = "SCROLL",
                name = "Scroll",
                description = "Scroll the screen in a direction",
                testParams = mapOf("direction" to "up")
            ),
            ToolDefinition(
                id = "HOVER",
                name = "Hover",
                description = "Move cursor to a position (accessibility hover)",
                testParams = mapOf("x" to 540, "y" to 1200)
            ),
            ToolDefinition(
                id = "COPY",
                name = "Copy",
                description = "Copy text to the clipboard",
                testParams = mapOf("text" to "Agentra test clipboard content")
            ),
            ToolDefinition(
                id = "WAIT",
                name = "Wait",
                description = "Wait for UI to settle (default 2s)",
                testParams = mapOf("duration" to 2)
            ),
            ToolDefinition(
                id = "SCREENSHOT",
                name = "Screenshot",
                description = "Capture the current screen",
                testParams = emptyMap()
            ),
            ToolDefinition(
                id = "NOTIFICATION",
                name = "Notifications",
                description = "Read current device notifications",
                testParams = emptyMap()
            ),
        )
    }
}
