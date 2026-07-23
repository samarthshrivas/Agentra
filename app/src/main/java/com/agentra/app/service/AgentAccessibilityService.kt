package com.agentra.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.agentra.app.action.ActionExecutor

class AgentAccessibilityService : AccessibilityService() {
    private var actionExecutor: ActionExecutor? = null

    override fun onCreate() {
        super.onCreate()
        actionExecutor = ActionExecutor(this)
        actionExecutor?.setAccessibilityService(this)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { super.onDestroy() }
    override fun onUnbind(intent: Intent?): Boolean = super.onUnbind(intent)

    fun getRootNode(): AccessibilityNodeInfo? = rootInActiveWindow
    fun findNodeByText(text: String): AccessibilityNodeInfo? = rootInActiveWindow?.findAccessibilityNodeInfosByText(text)?.firstOrNull()
    fun findNodeByViewId(viewId: String): AccessibilityNodeInfo? = rootInActiveWindow?.findAccessibilityNodeInfosByViewId(viewId)?.firstOrNull()
    fun getActionExecutor(): ActionExecutor? = actionExecutor
}
