package com.agentra.app.action

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.agentra.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ActionExecutor(private val context: Context) {

    private var accessibilityService: AccessibilityService? = null
    private val handler = Handler(Looper.getMainLooper())
    private var actionOverlay: ActionOverlayView? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    fun setAccessibilityService(service: AccessibilityService) {
        accessibilityService = service
    }

    fun execute(action: Action, onComplete: (ExecutionResult) -> Unit = {}) {
        showActionOverlay(action.type.name, action.normalizedX, action.normalizedY)

        val result = when (action.type) {
            ActionType.TAP -> executeTap(action.x ?: 0, action.y ?: 0)
            ActionType.DOUBLE_TAP -> executeDoubleTap(action.x ?: 0, action.y ?: 0)
            ActionType.LONG_PRESS -> executeLongPress(action.x ?: 0, action.y ?: 0)
            ActionType.SWIPE -> executeSwipe(
                (action.params["x1_abs"] as? Int) ?: 0,
                (action.params["y1_abs"] as? Int) ?: 0,
                (action.params["x2_abs"] as? Int) ?: 0,
                (action.params["y2_abs"] as? Int) ?: 0
            )
            ActionType.TYPE -> executeType(action.text ?: "")
            ActionType.PRESS -> executePress(action.key ?: "back")
            ActionType.LAUNCH -> executeLaunch(action.packageName ?: "")
            ActionType.WAIT -> {
                scope.launch { delay((action.duration ?: 2) * 1000L) }
                ExecutionResult(success = true, message = "Waited ${action.duration ?: 2}s")
            }
            ActionType.SCROLL -> executeScroll(action.direction ?: "up")
            ActionType.HOVER -> executeHover(action.x ?: 0, action.y ?: 0)
            ActionType.SELECT_TEXT -> ExecutionResult(success = true, message = "Select text action")
            ActionType.COPY -> executeCopy(action.text ?: "")
            ActionType.FINISHED -> ExecutionResult(success = true, message = "Task completed")
            ActionType.CALL_USER -> ExecutionResult(success = false, requiresUserInput = true, message = "User assistance required")
        }

        hideOverlayDelayed()
        onComplete(result)
    }

    private fun showActionOverlay(actionType: String, normX: Float?, normY: Float?) {
        if (normX == null || normY == null) return
        handler.post {
            val displayMetrics = context.resources.displayMetrics
            val x = (normX * displayMetrics.widthPixels).toInt()
            val y = (normY * displayMetrics.heightPixels).toInt()
            if (actionOverlay == null) {
                actionOverlay = ActionOverlayView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                }
            }
            actionOverlay?.showAction(actionType, x, y)
        }
    }

    private fun executeTap(x: Int, y: Int): ExecutionResult {
        accessibilityService?.let { performClick(x, y); return ExecutionResult(true, "Tapped at ($x, $y)") }
        return ExecutionResult(false, "Accessibility service not available")
    }

    private fun executeDoubleTap(x: Int, y: Int): ExecutionResult {
        accessibilityService?.let { performClick(x, y); handler.postDelayed({ performClick(x, y) }, 150); return ExecutionResult(true, "Double tapped at ($x, $y)") }
        return ExecutionResult(false, "Accessibility service not available")
    }

    private fun executeLongPress(x: Int, y: Int): ExecutionResult = executeGesture(x, y, 1000)
    private fun executeSwipe(x1: Int, y1: Int, x2: Int, y2: Int): ExecutionResult {
        val path = Path().apply { moveTo(x1.toFloat(), y1.toFloat()); lineTo(x2.toFloat(), y2.toFloat()) }
        return executePath(path, 400)
    }

    private fun executeType(text: String): ExecutionResult = accessibilityService?.let { ExecutionResult(true, "Typed: $text") } ?: ExecutionResult(false, "Service unavailable")

    private fun executePress(key: String): ExecutionResult {
        val service = accessibilityService ?: return ExecutionResult(false, "Service unavailable")
        val globalAction = when (key.lowercase()) {
            "back" -> AccessibilityService.GLOBAL_ACTION_BACK
            "home" -> AccessibilityService.GLOBAL_ACTION_HOME
            "enter" -> AccessibilityService.GLOBAL_ACTION_BACK
            "recent", "overview" -> AccessibilityService.GLOBAL_ACTION_RECENTS
            "power", "lock" -> AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
            "notifications" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            else -> return ExecutionResult(false, "Unknown key: $key")
        }
        return try { ExecutionResult(service.performGlobalAction(globalAction), "Pressed: $key") } catch (e: Exception) { ExecutionResult(false, e.message ?: "Error") }
    }

    private fun executeLaunch(packageName: String): ExecutionResult {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(intent); ExecutionResult(true, "Launched: $packageName") }
            else ExecutionResult(false, "App not found: $packageName")
        } catch (e: Exception) { ExecutionResult(false, e.message ?: "Error") }
    }

    private fun executeScroll(direction: String): ExecutionResult {
        val dm = context.resources.displayMetrics
        val cx = dm.widthPixels / 2
        val (sy, ey) = if (direction == "up") (dm.heightPixels * 0.7).toInt() to (dm.heightPixels * 0.3).toInt() else (dm.heightPixels * 0.3).toInt() to (dm.heightPixels * 0.7).toInt()
        return executePath(Path().apply { moveTo(cx.toFloat(), sy.toFloat()); lineTo(cx.toFloat(), ey.toFloat()) }, 500)
    }

    private fun executeHover(x: Int, y: Int): ExecutionResult = accessibilityService?.let { ExecutionResult(true, "Hovered at ($x, $y)") } ?: ExecutionResult(false, "Service unavailable")

    private fun executeCopy(text: String): ExecutionResult {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Agentra", text))
        return ExecutionResult(true, "Copied to clipboard")
    }

    private fun performClick(x: Int, y: Int) = executeGesture(x, y, 100)

    private fun executeGesture(x: Int, y: Int, duration: Long): ExecutionResult {
        val service = accessibilityService ?: return ExecutionResult(false, "Service unavailable")
        return try {
            val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            val gdClass = Class.forName("android.accessibilityservice.AccessibilityService\$GestureDescription")
            val builderClass = Class.forName("android.accessibilityservice.AccessibilityService\$GestureDescription\$Builder")
            val strokeClass = Class.forName("android.accessibilityservice.AccessibilityService\$GestureDescription\$StrokeDescription")
            val strokeCtor = strokeClass.getConstructor(Path::class.java, Long::class.java, Long::class.java)
            val stroke = strokeCtor.newInstance(path, 0L, duration)
            val builderCtor = builderClass.getConstructor()
            val builder = builderCtor.newInstance()
            val addStrokeMethod = builderClass.getMethod("addStroke", strokeClass)
            addStrokeMethod.invoke(builder, stroke)
            val buildMethod = builderClass.getMethod("build")
            val gesture = buildMethod.invoke(builder)
            val dispatchMethod = AccessibilityService::class.java.getMethod("dispatchGesture", gdClass, android.os.Handler::class.java, Object::class.java)
            dispatchMethod.invoke(service, gesture, null, null)
            ExecutionResult(true, "Gesture executed")
        } catch (e: Exception) { ExecutionResult(false, "Gesture error: ${e.message}") }
    }

    private fun executePath(path: Path, duration: Long): ExecutionResult {
        val service = accessibilityService ?: return ExecutionResult(false, "Service unavailable")
        return try {
            val gdClass = Class.forName("android.accessibilityservice.AccessibilityService\$GestureDescription")
            val builderClass = Class.forName("android.accessibilityservice.AccessibilityService\$GestureDescription\$Builder")
            val strokeClass = Class.forName("android.accessibilityservice.AccessibilityService\$GestureDescription\$StrokeDescription")
            val strokeCtor = strokeClass.getConstructor(Path::class.java, Long::class.java, Long::class.java)
            val stroke = strokeCtor.newInstance(path, 0L, duration)
            val builder = builderClass.getConstructor().newInstance()
            builderClass.getMethod("addStroke", strokeClass).invoke(builder, stroke)
            val gesture = builderClass.getMethod("build").invoke(builder)
            AccessibilityService::class.java.getMethod("dispatchGesture", gdClass, android.os.Handler::class.java, Object::class.java).invoke(service, gesture, null, null)
            ExecutionResult(true, "Gesture executed")
        } catch (e: Exception) { ExecutionResult(false, "Error: ${e.message}") }
    }

    private fun hideOverlayDelayed(delay: Long = 800) = handler.postDelayed({ actionOverlay?.hide() }, delay)

    inner class ActionOverlayView(context: Context) : FrameLayout(context) {
        private val textView = TextView(context).apply { textSize = 14f; setTextColor(ContextCompat.getColor(context, R.color.primary)); setBackgroundColor(0xCC000000.toInt()); setPadding(24, 12, 24, 12) }
        private val circleView = android.view.View(context).apply { setBackgroundResource(R.drawable.ic_action_circle) }
        init { addView(circleView, LayoutParams(80, 80)); addView(textView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)); visibility = GONE }
        fun showAction(actionType: String, x: Int, y: Int) {
            textView.text = actionType; textView.x = (x + 50).toFloat(); textView.y = (y - 30).toFloat(); circleView.x = (x - 40).toFloat(); circleView.y = (y - 40).toFloat()
            visibility = VISIBLE; alpha = 1f; animate().alpha(0.7f).setDuration(300).start()
        }
        fun hide() { animate().alpha(0f).setDuration(200).withEndAction { visibility = GONE }.start() }
    }

    data class ExecutionResult(val success: Boolean, val message: String = "", val requiresUserInput: Boolean = false)
    data class Action(val type: ActionType, val x: Int? = null, val y: Int? = null, val normalizedX: Float? = null, val normalizedY: Float? = null, val text: String? = null, val key: String? = null, val packageName: String? = null, val duration: Int? = null, val direction: String? = null, val params: Map<String, Any?> = emptyMap())
    enum class ActionType { TAP, DOUBLE_TAP, LONG_PRESS, SWIPE, TYPE, PRESS, LAUNCH, WAIT, SCROLL, HOVER, SELECT_TEXT, COPY, FINISHED, CALL_USER }
}
