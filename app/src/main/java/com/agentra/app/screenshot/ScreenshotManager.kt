package com.agentra.app.screenshot

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.view.Display
import androidx.annotation.RequiresApi
import com.agentra.app.service.AgentAccessibilityService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Captures screenshots using [AccessibilityService.takeScreenshot] (Android 11+).
 *
 * No MediaProjection permission dialog needed — the accessibility service
 * can capture the screen silently. Falls back to a placeholder bitmap
 * on API < 30 or when the accessibility service is not connected.
 */
class ScreenshotManager(private val context: Context) {

    /** Returns true if the accessibility service is connected and screenshots can be taken. */
    fun isCaptureActive(): Boolean = AgentAccessibilityService.instance != null

    /**
     * Captures a screenshot via the accessibility service API.
     *
     * This is a suspend function that waits for the async callback from
     * [AccessibilityService.takeScreenshot].
     */
    suspend fun captureScreen(): Bitmap? {
        val service = AgentAccessibilityService.instance ?: return createPlaceholder("No accessibility service")

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return createPlaceholder("Screen capture requires Android 11+")
        }

        return takeScreenshotInternal(service)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun takeScreenshotInternal(service: AccessibilityService): Bitmap? {
        return suspendCancellableCoroutine { cont ->
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                context.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        if (cont.isActive) {
                            // Extract bitmap via reflection for cross-API compatibility
                            val bitmap = try {
                                val m = screenshot::class.java.getMethod("getBitmap")
                                m.invoke(screenshot) as? Bitmap
                            } catch (_: Exception) {
                                null
                            }
                            cont.resume(bitmap)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            )
        }
    }

    private fun createPlaceholder(message: String): Bitmap {
        val dm = context.resources.displayMetrics
        val width = dm.widthPixels.coerceAtLeast(1080)
        val height = dm.heightPixels.coerceAtLeast(1920)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.DKGRAY)
        val paint = Paint().apply {
            color = Color.WHITE
            textSize = 48f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Agentra - Screen Capture", width / 2f, height / 2f, paint)
        canvas.drawText(message, width / 2f, height / 2f + 80, paint)
        return bitmap
    }
}
