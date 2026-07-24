package com.agentra.app.hierarchy

import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * NotificationListenerService that captures and provides access to
 * current device notifications.
 *
 * Must be declared in AndroidManifest with:
 *   android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
 *
 * User must grant notification access in:
 *   Settings → Apps → Special Access → Notification Access → Agentra
 */
class NotificationReader : NotificationListenerService() {

    companion object {
        const val TAG = "NotificationReader"
        private var currentNotifications: List<StatusBarNotification> = emptyList()
        private var instance: NotificationReader? = null

        /**
         * Returns a formatted string of all current notifications.
         */
        fun getNotificationsText(): String {
            val notifications = currentNotifications
            if (notifications.isEmpty()) return "[No notifications]"

            return buildString {
                appendLine("## Notifications (${notifications.size})")
                notifications.forEachIndexed { i, n ->
                    val notification = n.notification
                    appendLine("${i + 1}. [${n.packageName}]")
                    notification.extras?.let { extras ->
                        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
                        val text = extras.getString(Notification.EXTRA_TEXT) ?: ""
                        val bigText = extras.getString(Notification.EXTRA_BIG_TEXT) ?: ""
                        val infoText = extras.getString(Notification.EXTRA_INFO_TEXT) ?: ""
                        val subText = extras.getString(Notification.EXTRA_SUB_TEXT) ?: ""

                        if (title.isNotBlank()) appendLine("   Title: $title")
                        if (text.isNotBlank()) appendLine("   Text: $text")
                        if (bigText.isNotBlank()) appendLine("   Details: $bigText")
                        if (infoText.isNotBlank()) appendLine("   Info: $infoText")
                        if (subText.isNotBlank()) appendLine("   Sub: $subText")
                    }
                    appendLine()
                }
            }.trimEnd()
        }

        /**
         * Dismisses a notification from the given app.
         */
        fun dismissNotification(packageName: String) {
            instance?.dismissNotificationsForPackage(packageName)
        }
    }

    private fun dismissNotificationsForPackage(packageName: String) {
        val toCancel = currentNotifications.filter { it.packageName == packageName }
        for (n in toCancel) {
            try {
                cancelNotification(n.key)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to cancel notification: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "NotificationReader service created")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        updateNotifications()
        Log.d(TAG, "Notification posted from ${sbn.packageName}: ${sbn.notification.extras?.getString(Notification.EXTRA_TITLE)}")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        updateNotifications()
        Log.d(TAG, "Notification removed from ${sbn.packageName}")
    }

    @Suppress("DEPRECATION")
    private fun updateNotifications() {
        currentNotifications = try {
            activeNotifications.toList()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get active notifications", e)
            emptyList()
        }
    }
}
