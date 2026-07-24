package com.agentra.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.core.app.NotificationCompat
import com.agentra.app.R
import com.agentra.app.ui.MainActivity

/**
 * Foreground service that shows a floating overlay button accessible from any screen.
 *
 * Collapsed: a small circular button that can be dragged around the screen.
 * Expanded: a prompt input panel for entering agent tasks.
 * When submitted, launches MainActivity with the prompt text.
 */
class FloatingPromptService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    /** Tracks whether the input panel is expanded. */
    private var isExpanded = false

    /** Drag tracking state. */
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialX = 0
    private var initialY = 0

    companion object {
        const val CHANNEL_ID = "agentra_floating_channel"
        const val NOTIFICATION_ID = 1003
        const val TAG = "FloatingPrompt"

        fun start(context: Context) {
            val intent = Intent(context, FloatingPromptService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingPromptService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        if (overlayView == null) {
            createOverlay()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    // ─── Overlay creation ───

    private fun createOverlay() {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_floating_prompt, null)

        val displayMetrics = resources.displayMetrics
        val size = Point().also { windowManager.defaultDisplay.getSize(it) }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = size.x - 100   // Right side
            y = size.y / 3     // Middle vertically
        }

        windowManager.addView(overlayView, layoutParams)

        setupFloatingButton()
        setupInputPanel()
    }

    private fun removeOverlay() {
        try {
            overlayView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Error removing overlay", e)
        }
        overlayView = null
        layoutParams = null
    }

    // ─── UI setup ───

    private fun setupFloatingButton() {
        val floatingBtn = overlayView?.findViewById<View>(R.id.floatingBtn) ?: return

        floatingBtn.setOnClickListener {
            toggleExpanded()
        }

        // Drag support
        floatingBtn.setOnTouchListener { _, event ->
            val params = layoutParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    initialX = params.x
                    initialY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(overlayView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // If barely moved, treat as a click (handled by onClick)
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (Math.abs(dx) < 10 && Math.abs(dy) < 10) {
                        floatingBtn.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupInputPanel() {
        val etPrompt = overlayView?.findViewById<View>(R.id.etPrompt) ?: return
        val btnCancel = overlayView?.findViewById<View>(R.id.btnCancelOverlay) ?: return
        val btnSubmit = overlayView?.findViewById<View>(R.id.btnSubmitOverlay) ?: return
        val inputPanel = overlayView?.findViewById<View>(R.id.inputPanel) ?: return

        btnCancel.setOnClickListener {
            collapse()
        }

        btnSubmit.setOnClickListener {
            submitPrompt()
        }
    }

    private fun toggleExpanded() {
        if (isExpanded) {
            collapse()
        } else {
            expand()
        }
    }

    private fun expand() {
        val floatingBtn = overlayView?.findViewById<View>(R.id.floatingBtn) ?: return
        val inputPanel = overlayView?.findViewById<View>(R.id.inputPanel) ?: return

        floatingBtn.visibility = View.GONE
        inputPanel.visibility = View.VISIBLE

        // Move panel to center of screen
        layoutParams?.let { params ->
            params.x = (resources.displayMetrics.widthPixels - 280) / 2
            params.y = (resources.displayMetrics.heightPixels / 4)
            windowManager.updateViewLayout(overlayView, params)
        }

        // Make focusable so keyboard can appear
        layoutParams?.let { params ->
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            windowManager.updateViewLayout(overlayView, params)
        }

        isExpanded = true

        // Show keyboard
        val etPrompt = overlayView?.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPrompt)
        etPrompt?.post {
            etPrompt.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(etPrompt, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun collapse() {
        val floatingBtn = overlayView?.findViewById<View>(R.id.floatingBtn) ?: return
        val inputPanel = overlayView?.findViewById<View>(R.id.inputPanel) ?: return
        val etPrompt = overlayView?.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPrompt) ?: return

        // Hide keyboard
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etPrompt.windowToken, 0)

        etPrompt.clearFocus()
        inputPanel.visibility = View.GONE
        floatingBtn.visibility = View.VISIBLE

        // Restore not-focusable flag
        layoutParams?.let { params ->
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager.updateViewLayout(overlayView, params)
        }

        isExpanded = false
    }

    private fun submitPrompt() {
        val etPrompt = overlayView?.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPrompt) ?: return
        val prompt = etPrompt.text?.toString()?.trim()

        if (prompt.isNullOrEmpty()) return

        collapse()

        // Launch MainActivity with the prompt
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("floating_prompt", prompt)
        }
        startActivity(intent)

        etPrompt.text = null // Clear for next use
    }

    // ─── Notification ───

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.floating_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.floating_channel_description)
            setShowBadge(false)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.floating_button_title))
            .setContentText(getString(R.string.floating_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
