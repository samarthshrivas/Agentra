package com.agentra.app.screenshot

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.HardwareBuffer
import android.os.Build
import android.view.Display
import androidx.annotation.RequiresApi
import com.agentra.app.service.AgentAccessibilityService
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
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
        val executor = Executors.newSingleThreadExecutor()
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { executor.shutdownNow() }
            try {
                service.takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    executor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                            try {
                                val bitmap = if (Build.VERSION.SDK_INT >= 37) {
                                    // API 37+: ScreenshotResult exposes hardwareBuffer + colorSpace
                                    // Use Bitmap.wrapHardwareBuffer() to convert (available since API 26)
                                    val hardwareBuffer: HardwareBuffer = screenshot.hardwareBuffer
                                    val colorSpace = screenshot.colorSpace
                                    try {
                                        Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                                    } finally {
                                        // CRITICAL: Close HardwareBuffer to prevent graphics memory leak.
                                        // Android 17 (API 37) has aggressive ART garbage collection and
                                        // unclosed buffers trigger system-enforced app termination.
                                        hardwareBuffer.close()
                                    }
                                } else {
                                    // API 30-36: Use reflection for getBitmap() cross-compat
                                    try {
                                        val m = screenshot::class.java.getMethod("getBitmap")
                                        m.invoke(screenshot) as? Bitmap
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                                if (cont.isActive) cont.resume(bitmap)
                            } catch (e: Exception) {
                                if (cont.isActive) cont.resume(null)
                            } finally {
                                executor.shutdown()
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            if (cont.isActive) cont.resume(null)
                            executor.shutdown()
                        }
                    }
                )
            } catch (e: Exception) {
                // takeScreenshot() can throw SecurityException synchronously if the
                // accessibility service's screenshot capability is blocked by the OS
                // (e.g. misconfigured manifest flags like missing isAccessibilityTool on API 37).
                if (cont.isActive) cont.resume(null)
                executor.shutdown()
            }
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
