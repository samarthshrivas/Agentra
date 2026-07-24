package com.agentra.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.agentra.app.R
import com.agentra.app.config.AppConfig
import com.agentra.app.ui.MainActivity
import java.util.Locale

/**
 * Foreground service that continuously listens for a wake word phrase
 * using Android's SpeechRecognizer API. When the wake word is detected,
 * it fires an intent to launch the assistant UI.
 */
class WakeWordService : Service() {

    private lateinit var config: AppConfig
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var shouldRestart = true
    private var isMicAvailable = true

    companion object {
        const val CHANNEL_ID = "agentra_wake_word_channel"
        const val NOTIFICATION_ID = 1002
        const val TAG = "WakeWordService"

        const val ACTION_STOP = "com.agentra.app.action.STOP_WAKE_WORD"
        const val ACTION_WAKE_WORD_DETECTED = "com.agentra.app.action.WAKE_WORD_DETECTED"

        fun start(context: Context) {
            val intent = Intent(context, WakeWordService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WakeWordService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        config = AppConfig(this)
        createNotificationChannel()
        registerStopReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (!config.isWakeWordEnabled) {
            Log.d(TAG, "Wake word disabled in config, stopping service")
            stopSelf()
            return START_NOT_STICKY
        }

        shouldRestart = true
        startListening()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        shouldRestart = false
        stopListening()
        unregisterStopReceiver()
        super.onDestroy()
    }

    private fun startListening() {
        if (!config.isWakeWordEnabled || !shouldRestart) return
        if (!isMicAvailable) {
            Log.w(TAG, "Microphone not available, will retry later")
            scheduleRestart()
            return
        }
        if (isListening) return

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    Log.d(TAG, "Ready for speech")
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "Beginning of speech")
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.d(TAG, "End of speech")
                    isListening = false
                    if (shouldRestart) {
                        restartAfterDelay()
                    }
                }

                override fun onError(error: Int) {
                    val errorMsg = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Missing RECORD_AUDIO permission"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Too many requests"
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Language not supported"
                        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Language unavailable"
                        else -> "Unknown error: $error"
                    }
                    Log.w(TAG, "Speech recognition error: $errorMsg")

                    // If recognizer is busy or server error, mark mic as temporarily unavailable
                    if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                        error == SpeechRecognizer.ERROR_SERVER ||
                        error == SpeechRecognizer.ERROR_TOO_MANY_REQUESTS
                    ) {
                        isMicAvailable = false
                    }

                    isListening = false
                    if (shouldRestart) {
                        restartAfterDelay()
                    }
                }

                @Suppress("DEPRECATION")
                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (matches != null) {
                        for (i in 0 until matches.size) {
                            val match = matches[i] ?: continue
                            Log.d(TAG, "Recognized: $match")
                            if (isWakeWordDetected(match)) {
                                onWakeWordDetected(match)
                                return
                            }
                        }
                    }
                    if (shouldRestart) {
                        restartAfterDelay()
                    }
                }

                @Suppress("DEPRECATION")
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (matches != null) {
                        for (i in 0 until matches.size) {
                            val match = matches[i] ?: continue
                            if (isWakeWordDetected(match)) {
                                onWakeWordDetected(match)
                                return
                            }
                        }
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                // On API 34+, prefer on-device recognition for wake word
                if (Build.VERSION.SDK_INT >= 34) {
                    putExtra("android.speech.extra.EXTRA_PREFER_ON_DEVICE_RECOGNITION", true)
                }
            }

            speechRecognizer?.startListening(intent)
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing RECORD_AUDIO permission", e)
            scheduleRestart()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognition", e)
            scheduleRestart()
        }
    }

    private fun stopListening() {
        shouldRestart = false
        isListening = false
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping recognizer", e)
        }
        speechRecognizer = null
    }

    private fun isWakeWordDetected(text: String): Boolean {
        val lowerText = text.lowercase(Locale.ROOT).trim()
        val wakeWord = config.wakeWordPhrase.lowercase(Locale.ROOT).trim()
        return lowerText.contains(wakeWord)
    }

    private fun onWakeWordDetected(matchedText: String) {
        Log.i(TAG, "Wake word detected! Matched: $matchedText")
        stopListening()

        // Broadcast that wake word was detected (for logging/tracking)
        sendBroadcast(Intent(ACTION_WAKE_WORD_DETECTED).apply {
            putExtra("text", matchedText)
        })

        // Launch the assistant activity
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("wake_word_triggered", true)
            putExtra("voice_input", matchedText)
        }
        startActivity(intent)

        // If we should keep listening after detection
        if (shouldRestart) {
            restartAfterDelay(1000)
        }
    }

    private fun restartAfterDelay(delayMs: Long = 500) {
        android.os.Handler(mainLooper).postDelayed({
            if (shouldRestart) {
                isMicAvailable = true // Reset mic availability check
                startListening()
            }
        }, delayMs)
    }

    private fun scheduleRestart() {
        if (!shouldRestart) return
        android.os.Handler(mainLooper).postDelayed({
            if (shouldRestart) {
                isMicAvailable = true
                startListening()
            }
        }, 3000)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Wake Word Detection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Listening for wake word"
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

        val stopIntent = Intent(ACTION_STOP).apply { setPackage(packageName) }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Agentra - Listening")
            .setContentText("Wake word: \"${config.wakeWordPhrase}\"")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // ─── Receiver to allow stopping from notification ───

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_STOP == intent.action) {
                config.isWakeWordEnabled = false
                stopSelf()
            }
        }
    }

    private fun registerStopReceiver() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            registerReceiver(stopReceiver, IntentFilter(ACTION_STOP), RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(stopReceiver, IntentFilter(ACTION_STOP))
        }
    }

    private fun unregisterStopReceiver() {
        try {
            unregisterReceiver(stopReceiver)
        } catch (e: Exception) {
            // Ignore if already unregistered
        }
    }
}
