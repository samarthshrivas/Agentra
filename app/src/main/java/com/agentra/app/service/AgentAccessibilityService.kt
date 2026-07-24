package com.agentra.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.agentra.app.action.ActionExecutor

class AgentAccessibilityService : AccessibilityService() {
    companion object {
        var instance: AgentAccessibilityService? = null
    }
    private var actionExecutor: ActionExecutor? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        actionExecutor = ActionExecutor(this)
        actionExecutor?.setAccessibilityService(this)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Screenshot capability is set via XML (android:canTakeScreenshot="true")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() {
        if (instance == this) instance = null
        super.onDestroy()
    }
    override fun onUnbind(intent: Intent?): Boolean = super.onUnbind(intent)

    fun getRootNode(): AccessibilityNodeInfo? = rootInActiveWindow
    fun findNodeByText(text: String): AccessibilityNodeInfo? = rootInActiveWindow?.findAccessibilityNodeInfosByText(text)?.firstOrNull()
    fun findNodeByViewId(viewId: String): AccessibilityNodeInfo? = rootInActiveWindow?.findAccessibilityNodeInfosByViewId(viewId)?.firstOrNull()
    fun getActionExecutor(): ActionExecutor? = actionExecutor
}
