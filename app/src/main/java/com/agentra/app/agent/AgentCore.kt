package com.agentra.app.agent

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import com.agentra.app.action.ActionExecutor
import com.agentra.app.action.ActionPlanner
import com.agentra.app.config.AppConfig
import com.agentra.app.llm.LLMInterface
import com.agentra.app.screenshot.ScreenshotManager
import com.agentra.app.ui.adapter.LogAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class AgentCore(private val context: Context, private val screenshotManager: ScreenshotManager, private val config: AppConfig) {
    private var isRunning = false
    private val llm = LLMInterface(config)
    private val actionPlanner = ActionPlanner()
    private val actionExecutor = ActionExecutor(context)
    private val actionHistory = mutableListOf<ActionHistoryEntry>()
    private var consecutiveFailures = 0
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    data class ActionHistoryEntry(val thought: String?, val action: String, val result: String, val success: Boolean)

    suspend fun execute(command: String, onLog: (String, LogAdapter.LogType) -> Unit) {
        isRunning = true; consecutiveFailures = 0; actionHistory.clear()
        onLog("Starting task: $command", LogAdapter.LogType.INFO)

        while (isRunning) {
            if (!checkPreconditions(onLog)) break
            onLog("Taking screenshot...", LogAdapter.LogType.INFO)
            val screenshot = screenshotManager.captureScreen()
            if (screenshot == null) { onLog("Failed to capture screenshot", LogAdapter.LogType.ERROR); break }
            val screenshotBase64 = bitmapToBase64(screenshot); screenshot.recycle()
            onLog("Analyzing screen (${actionHistory.size + 1} steps)...", LogAdapter.LogType.AGENT)

            val response = withContext(Dispatchers.IO) { llm.sendMessage(command, screenshotBase64, buildActionHistory()) }
            if (response == null) { onLog("LLM request failed", LogAdapter.LogType.ERROR); break }

            val actions = actionPlanner.parseActions(response)
            if (actions.isEmpty()) {
                if (response.contains("finished", ignoreCase = true) || response.contains("done", ignoreCase = true)) { onLog("Task completed!", LogAdapter.LogType.INFO); break }
                onLog("Could not parse actions", LogAdapter.LogType.ERROR); consecutiveFailures++
                if (consecutiveFailures >= ActionPlanner.MAX_FAILED_ATTEMPTS) { onLog("Max failures reached", LogAdapter.LogType.ERROR); break }
                continue
            }

            for (action in actions) {
                if (!isRunning) break
                handleAction(action, onLog)
                if (action.type == ActionExecutor.ActionType.FINISHED || action.type == ActionExecutor.ActionType.CALL_USER) { isRunning = false; break }
            }
            if (actionHistory.size >= ActionPlanner.MAX_ITERATIONS) { onLog("Max iterations reached", LogAdapter.LogType.INFO); break }
        }
        isRunning = false; onLog("Agent stopped", LogAdapter.LogType.INFO)
    }

    private fun handleAction(action: ActionExecutor.Action, onLog: (String, LogAdapter.LogType) -> Unit) {
        when (action.type) {
            ActionExecutor.ActionType.WAIT -> { val dur = action.duration ?: 2; onLog("Waiting ${dur}s...", LogAdapter.LogType.INFO); scope.launch { delay(dur * 1000L) } }
            else -> {
                var result: ActionExecutor.ExecutionResult? = null
                actionExecutor.execute(action) { res -> result = res }
                val logType = if (result?.success == true) LogAdapter.LogType.ACTION else LogAdapter.LogType.ERROR
                onLog(result?.message ?: "Unknown", logType)
                actionHistory.add(ActionHistoryEntry(null, action.type.name, result?.message ?: "", result?.success == true))
                if (result?.success != true) { consecutiveFailures++; if (consecutiveFailures >= ActionPlanner.MAX_FAILED_ATTEMPTS) isRunning = false }
                else consecutiveFailures = 0
                scope.launch { delay(config.executionDelay) }
            }
        }
    }

    private fun checkPreconditions(onLog: (String, LogAdapter.LogType) -> Unit): Boolean {
        if (!isRunning) return false
        if (!screenshotManager.isCaptureActive()) { onLog("Screen capture not active", LogAdapter.LogType.ERROR); return false }
        return true
    }

    fun stop() { isRunning = false; scope.cancel() }
    fun getActionHistory(): List<ActionHistoryEntry> = actionHistory.toList()

    private fun buildActionHistory(): String {
        if (actionHistory.isEmpty()) return ""
        return buildString { appendLine("\n## Action History:"); actionHistory.forEachIndexed { i, e -> appendLine("${i + 1}. [${e.action}] -> ${e.result} ${if (e.success) "(success)" else "(failed)"}") } }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        val scaled = Bitmap.createScaledBitmap(bitmap, 720, 1280, true)
        scaled.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }
}
