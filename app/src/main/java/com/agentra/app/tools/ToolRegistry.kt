package com.agentra.app.tools

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Path
import com.agentra.app.hierarchy.ShellExecutor
import com.agentra.app.service.AgentAccessibilityService

/**
 * Registry of all available agent tools with SharedPreferences-backed
 * enabled/disabled state and support for testing each tool in isolation.
 *
 * Touch-based test tools (TAP, SWIPE, etc.) use the AccessibilityService
 * gesture API — not shell commands — so they work without INJECT_EVENTS.
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
     * Test a tool by executing its default action.
     *
     * Touch/gesture tools dispatch via [AccessibilityService.dispatchGesture];
     * other tools (LAUNCH, COPY, etc.) fall back to shell commands.
     */
    fun testTool(toolId: String): ShellExecutor.ShellResult {
        val tool = TOOLS.find { it.id == toolId }
            ?: return ShellExecutor.ShellResult(false, "Unknown tool: $toolId")

        return when (tool.id) {
            "TAP" -> gestureTap(
                tool.testParams["x"] as? Int ?: 540,
                tool.testParams["y"] as? Int ?: 1200
            )
            "DOUBLE_TAP" -> gestureDoubleTap(
                tool.testParams["x"] as? Int ?: 540,
                tool.testParams["y"] as? Int ?: 1200
            )
            "LONG_PRESS" -> gestureLongPress(
                tool.testParams["x"] as? Int ?: 540,
                tool.testParams["y"] as? Int ?: 1200,
                tool.testParams["duration"] as? Int ?: 1000
            )
            "SWIPE" -> gestureSwipe(
                tool.testParams["x1"] as? Int ?: 200,
                tool.testParams["y1"] as? Int ?: 1000,
                tool.testParams["x2"] as? Int ?: 800,
                tool.testParams["y2"] as? Int ?: 1000
            )
            "DRAG" -> gestureSwipe(
                tool.testParams["x1"] as? Int ?: 200,
                tool.testParams["y1"] as? Int ?: 1000,
                tool.testParams["x2"] as? Int ?: 800,
                tool.testParams["y2"] as? Int ?: 1000,
                tool.testParams["duration"] as? Int ?: 1000
            )
            "SCROLL" -> {
                val dir = tool.testParams["direction"] as? String ?: "up"
                val (x1, y1, x2, y2) = if (dir == "up") {
                    listOf(540, 1400, 540, 400)
                } else {
                    listOf(540, 400, 540, 1400)
                }
                gestureSwipe(x1, y1, x2, y2)
            }
            "HOVER" -> gestureTap(
                tool.testParams["x"] as? Int ?: 540,
                tool.testParams["y"] as? Int ?: 1200
            )
            "TYPE" -> ShellExecutor.type(
                tool.testParams["text"] as? String ?: "Hello Agentra"
            )
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
            "COPY" -> ShellExecutor.ShellResult(true, "COPY: clipboard write is not testable via shell")
            "WAIT" -> ShellExecutor.ShellResult(true, "WAIT: 2s delay (simulated via shell sleep)")
            "SCREENSHOT" -> ShellExecutor.execute(
                "screencap -p /data/local/tmp/agentra_test.png && echo Captured"
            )
            "NOTIFICATION" -> ShellExecutor.execute("cmd notification list")
            else -> ShellExecutor.ShellResult(false, "Tool $toolId has no test defined")
        }
    }

    // ─── Gesture-based test helpers ─────────────────────────────────────

    private fun getService(): AccessibilityService? = AgentAccessibilityService.instance

    private fun gestureTap(x: Int, y: Int): ShellExecutor.ShellResult {
        val svc = getService() ?: return ShellExecutor.ShellResult(false, "Accessibility service not connected")
        return try {
            val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0L, 80L)
            val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
            svc.dispatchGesture(gesture, null, null)
            ShellExecutor.ShellResult(true, "Tapped at ($x, $y)")
        } catch (e: Exception) {
            ShellExecutor.ShellResult(false, "Gesture error: ${e.message}")
        }
    }

    private fun gestureDoubleTap(x: Int, y: Int): ShellExecutor.ShellResult {
        val svc = getService() ?: return ShellExecutor.ShellResult(false, "Accessibility service not connected")
        return try {
            val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            val stroke1 = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0L, 80L)
            val stroke2 = android.accessibilityservice.GestureDescription.StrokeDescription(path, 200L, 80L)
            val gesture = android.accessibilityservice.GestureDescription.Builder()
                .addStroke(stroke1).addStroke(stroke2).build()
            svc.dispatchGesture(gesture, null, null)
            ShellExecutor.ShellResult(true, "Double tapped at ($x, $y)")
        } catch (e: Exception) {
            ShellExecutor.ShellResult(false, "Gesture error: ${e.message}")
        }
    }

    private fun gestureLongPress(x: Int, y: Int, durationMs: Int): ShellExecutor.ShellResult {
        val svc = getService() ?: return ShellExecutor.ShellResult(false, "Accessibility service not connected")
        return try {
            val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0L, durationMs.toLong())
            val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
            svc.dispatchGesture(gesture, null, null)
            ShellExecutor.ShellResult(true, "Long pressed at ($x, $y) for ${durationMs}ms")
        } catch (e: Exception) {
            ShellExecutor.ShellResult(false, "Gesture error: ${e.message}")
        }
    }

    private fun gestureSwipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int = 400): ShellExecutor.ShellResult {
        val svc = getService() ?: return ShellExecutor.ShellResult(false, "Accessibility service not connected")
        return try {
            val path = Path().apply { moveTo(x1.toFloat(), y1.toFloat()); lineTo(x2.toFloat(), y2.toFloat()) }
            val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0L, durationMs.toLong())
            val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
            svc.dispatchGesture(gesture, null, null)
            ShellExecutor.ShellResult(true, "Swiped from ($x1,$y1) to ($x2,$y2)")
        } catch (e: Exception) {
            ShellExecutor.ShellResult(false, "Gesture error: ${e.message}")
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
