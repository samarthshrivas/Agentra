package com.agentra.app.screenshot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.view.WindowManager

class ScreenshotManager(private val context: Context) {
    private var mediaProjection: MediaProjection? = null
    private var isActive = false
    private var hasPermission = false

    fun startCapture(resultCode: Int, data: android.content.Intent) {
        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpm.getMediaProjection(resultCode, data)
        isActive = true
        hasPermission = true
    }

    fun hasPermissionGranted(): Boolean = hasPermission

    fun captureScreen(): Bitmap? {
        if (!isActive || mediaProjection == null) return createPlaceholderBitmap()

        return try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            @Suppress("DEPRECATION")
            val display = wm.defaultDisplay
            val dm = context.resources.displayMetrics
            val width = dm.widthPixels
            val height = dm.heightPixels

            // Use MediaProjection to capture
            val reader = android.media.ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2)

            val callback = object : MediaProjection.Callback() {}
            mediaProjection?.registerCallback(callback, null)

            @Suppress("DEPRECATION")
            val virtualDisplay = mediaProjection?.createVirtualDisplay(
                "Agentra", width, height, dm.densityDpi,
                display.flags, reader.surface, null, null
            )

            var bitmap: Bitmap? = null
            val image = reader.acquireLatestImage()

            if (image != null) {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width

                bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
                bitmap?.copyPixelsFromBuffer(buffer)
                bitmap = bitmap?.let { Bitmap.createBitmap(it, 0, 0, width, height) }
                image.close()
            }

            reader.close()
            virtualDisplay?.release()
            bitmap ?: createPlaceholderBitmap()
        } catch (e: Exception) {
            e.printStackTrace()
            createPlaceholderBitmap()
        }
    }

    private fun createPlaceholderBitmap(): Bitmap {
        val dm = context.resources.displayMetrics
        val width = dm.widthPixels.coerceAtLeast(1080)
        val height = dm.heightPixels.coerceAtLeast(1920)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.DKGRAY)

        val paint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 48f
            textAlign = Paint.Align.CENTER
        }

        canvas.drawText("Agentra - Screen Capture", width / 2f, height / 2f, paint)
        canvas.drawText("Enable screen capture to continue", width / 2f, height / 2f + 80, paint)

        return bitmap
    }

    fun isCaptureActive(): Boolean = isActive

    fun stopCapture() {
        try {
            mediaProjection?.stop()
        } catch (e: Exception) {
            // Ignore
        }
        mediaProjection = null
        isActive = false
        hasPermission = false
    }
}
