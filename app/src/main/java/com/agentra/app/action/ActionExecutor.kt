package com.agentra.app.action

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.agentra.app.R
import com.agentra.app.service.AgentAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Executes actions using [AccessibilityService.dispatchGesture] and other
 * accessibility APIs. No shell commands are used for touch injection, so
 * this works without INJECT_EVENTS permission.
 */
class ActionExecutor(private val context: Context) {

    private var accessibilityService: AccessibilityService? = null
    private val handler = Handler(Looper.getMainLooper())
    private var actionOverlay: ActionOverlayView? = null
    private var overlayAttached = false
    private val scope = CoroutineScope(Dispatchers.Main)
    private val windowManager: WindowManager by lazy {
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    /** Refresh the service reference from the singleton if not already set. */
    fun setAccessibilityService(service: AccessibilityService) {
        accessibilityService = service
    }

    /** Lazily refresh service reference from the AgentAccessibilityService singleton. */
    private fun ensureService(): AccessibilityService? {
        if (accessibilityService == null) {
            accessibilityService = AgentAccessibilityService.instance
        }
        return accessibilityService
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
            ActionType.DRAG -> executeDrag(
                (action.params["x1_abs"] as? Int) ?: 0,
                (action.params["y1_abs"] as? Int) ?: 0,
                (action.params["x2_abs"] as? Int) ?: 0,
                (action.params["y2_abs"] as? Int) ?: 0,
                action.duration ?: 1000
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
            ActionType.CALL_USER -> ExecutionResult(
                success = false,
                requiresUserInput = true,
                message = "User assistance required"
            )
        }

        hideOverlayDelayed()
        onComplete(result)
    }

    // ─── Overlay ────────────────────────────────────────────────────────

    private fun showActionOverlay(actionType: String, normX: Float?, normY: Float?) {
        if (normX == null || normY == null) return
        handler.post {
            val displayMetrics = context.resources.displayMetrics
            val x = (normX * displayMetrics.widthPixels).toInt()
            val y = (normY * displayMetrics.heightPixels).toInt()
            if (actionOverlay == null) {
                actionOverlay = ActionOverlayView(context)
            }
            if (!overlayAttached) {
                try {
                    val params = WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT
                    ).apply { gravity = Gravity.TOP or Gravity.START }
                    windowManager.addView(actionOverlay, params)
                    overlayAttached = true
                } catch (e: SecurityException) {
                    // Overlay permission not granted — silently skip overlay
                } catch (e: Exception) {
                    // Ignore other overlay failures
                }
            }
            actionOverlay?.showAction(actionType, x, y)
        }
    }

    // ─── Touch actions via GestureDescription ───────────────────────────

    private fun executeTap(x: Int, y: Int): ExecutionResult {
        val service = ensureService() ?: return ExecutionResult(false, "Accessibility service not available")
        return performGesture(service, buildTapPath(x, y), 80)
    }

    private fun executeDoubleTap(x: Int, y: Int): ExecutionResult {
        val service = ensureService() ?: return ExecutionResult(false, "Accessibility service not available")
        val tapPath = buildTapPath(x, y)
        val stroke1 = GestureDescription.StrokeDescription(tapPath, 0L, 80L)
        val stroke2 = GestureDescription.StrokeDescription(tapPath, 200L, 80L)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke1)
            .addStroke(stroke2)
            .build()
        return try {
            service.dispatchGesture(gesture, null, null)
            ExecutionResult(true, "Double tapped at ($x, $y)")
        } catch (e: Exception) {
            ExecutionResult(false, "Double tap error: ${e.message}")
        }
    }

    private fun executeLongPress(x: Int, y: Int): ExecutionResult {
        val service = ensureService() ?: return ExecutionResult(false, "Service unavailable")
        return performGesture(service, buildTapPath(x, y), 1000)
    }

    private fun executeSwipe(x1: Int, y1: Int, x2: Int, y2: Int): ExecutionResult {
        val service = ensureService() ?: return ExecutionResult(false, "Service unavailable")
        val path = Path().apply { moveTo(x1.toFloat(), y1.toFloat()); lineTo(x2.toFloat(), y2.toFloat()) }
        return performGesture(service, path, 400)
    }

    private fun executeDrag(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int = 1000): ExecutionResult {
        val service = ensureService() ?: return ExecutionResult(false, "Service unavailable")
        val path = Path().apply { moveTo(x1.toFloat(), y1.toFloat()); lineTo(x2.toFloat(), y2.toFloat()) }
        return performGesture(service, path, durationMs.toLong())
    }

    private fun executeScroll(direction: String): ExecutionResult {
        val service = ensureService() ?: return ExecutionResult(false, "Service unavailable")
        val dm = context.resources.displayMetrics
        val cx = dm.widthPixels / 2
        val (sy, ey) = if (direction == "up") {
            (dm.heightPixels * 0.7).toInt() to (dm.heightPixels * 0.3).toInt()
        } else {
            (dm.heightPixels * 0.3).toInt() to (dm.heightPixels * 0.7).toInt()
        }
        val path = Path().apply { moveTo(cx.toFloat(), sy.toFloat()); lineTo(cx.toFloat(), ey.toFloat()) }
        return performGesture(service, path, 500)
    }

    /** Hover is simulated as a very short tap (no dedicated hover gesture API). */
    private fun executeHover(x: Int, y: Int): ExecutionResult {
        val service = ensureService() ?: return ExecutionResult(false, "Service unavailable")
        return performGesture(service, buildTapPath(x, y), 1)
    }

    private fun buildTapPath(x: Int, y: Int): Path =
        Path().apply { moveTo(x.toFloat(), y.toFloat()) }

    private fun performGesture(service: AccessibilityService, path: Path, durationMs: Long): ExecutionResult {
        return try {
            val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            service.dispatchGesture(gesture, null, null)
            ExecutionResult(true, "Gesture executed (${durationMs}ms)")
        } catch (e: Exception) {
            ExecutionResult(false, "Gesture error: ${e.message}")
        }
    }

    // ─── TYPE via accessibility node API ────────────────────────────────

    private fun executeType(text: String): ExecutionResult {
        val service = ensureService() ?: return ExecutionResult(false, "Service unavailable")
        val root = service.rootInActiveWindow ?: return ExecutionResult(false, "No active window")
        var result = ExecutionResult(false, "No editable text field found")

        try {
            // Strategy 1: find the focused input field
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null) {
                setNodeText(focused, text)
                result = ExecutionResult(true, "Typed into focused field: $text")
                focused.recycle()
                return result
            }

            // Strategy 2: search the tree for editable fields
            val editable = findEditableNode(root)
            if (editable != null) {
                editable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                setNodeText(editable, text)
                result = ExecutionResult(true, "Typed into editable field: $text")
                editable.recycle()
                return result
            }
        } finally {
            root.recycle()
        }

        return result
    }

    private fun setNodeText(node: AccessibilityNodeInfo, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
    }

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                val found = findEditableNode(child)
                if (found != null) return found
            } finally {
                child.recycle()
            }
        }
        return null
    }

    // ─── Other actions ──────────────────────────────────────────────────

    private fun executePress(key: String): ExecutionResult {
        val service = ensureService() ?: return ExecutionResult(false, "Service unavailable")
        val globalAction = when (key.lowercase()) {
            "back" -> AccessibilityService.GLOBAL_ACTION_BACK
            "home" -> AccessibilityService.GLOBAL_ACTION_HOME
            "enter" -> AccessibilityService.GLOBAL_ACTION_BACK
            "recent", "overview" -> AccessibilityService.GLOBAL_ACTION_RECENTS
            "power", "lock" -> AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
            "notifications" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            else -> return ExecutionResult(false, "Unknown key: $key")
        }
        return try {
            ExecutionResult(service.performGlobalAction(globalAction), "Pressed: $key")
        } catch (e: Exception) {
            ExecutionResult(false, e.message ?: "Error")
        }
    }

    private fun executeLaunch(packageName: String): ExecutionResult {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                ExecutionResult(true, "Launched: $packageName")
            } else {
                ExecutionResult(false, "App not found: $packageName")
            }
        } catch (e: Exception) {
            ExecutionResult(false, e.message ?: "Error")
        }
    }

    private fun executeCopy(text: String): ExecutionResult {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Agentra", text))
        return ExecutionResult(true, "Copied to clipboard")
    }

    // ─── Overlay hide ───────────────────────────────────────────────────

    private fun hideOverlayDelayed(delay: Long = 800) {
        handler.postDelayed({ actionOverlay?.hide() }, delay)
    }

    // ─── ActionOverlayView (floating indicator at the tap point) ────────

    inner class ActionOverlayView(context: Context) : FrameLayout(context) {
        private val textView = TextView(context).apply {
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.primary))
            setBackgroundColor(0xCC000000.toInt())
            setPadding(24, 12, 24, 12)
        }
        private val circleView = View(context).apply {
            setBackgroundResource(R.drawable.ic_action_circle)
        }

        init {
            addView(circleView, LayoutParams(80, 80))
            addView(textView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            visibility = GONE
        }

        fun showAction(actionType: String, x: Int, y: Int) {
            textView.text = actionType
            textView.x = (x + 50).toFloat()
            textView.y = (y - 30).toFloat()
            circleView.x = (x - 40).toFloat()
            circleView.y = (y - 40).toFloat()
            visibility = VISIBLE
            alpha = 1f
            animate().alpha(0.7f).setDuration(300).start()
        }

        fun hide() {
            animate().alpha(0f).setDuration(200).withEndAction { visibility = GONE }.start()
        }
    }

    // ─── Data types ─────────────────────────────────────────────────────

    data class ExecutionResult(
        val success: Boolean,
        val message: String = "",
        val requiresUserInput: Boolean = false
    )

    data class Action(
        val type: ActionType,
        val x: Int? = null,
        val y: Int? = null,
        val normalizedX: Float? = null,
        val normalizedY: Float? = null,
        val text: String? = null,
        val key: String? = null,
        val packageName: String? = null,
        val duration: Int? = null,
        val direction: String? = null,
        val params: Map<String, Any?> = emptyMap()
    )

    enum class ActionType {
        TAP, DOUBLE_TAP, LONG_PRESS, SWIPE, DRAG, TYPE,
        PRESS, LAUNCH, WAIT, SCROLL, HOVER, SELECT_TEXT,
        COPY, FINISHED, CALL_USER
    }
}
