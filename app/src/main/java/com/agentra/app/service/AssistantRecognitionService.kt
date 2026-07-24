package com.agentra.app.service

import android.os.Bundle
import android.speech.RecognitionService
import android.util.Log

/**
 * Minimal RecognitionService required by the VoiceInteractionService
 * for the system to consider it "valid" as a default assistant.
 *
 * Actual wake word detection is handled separately by [WakeWordService]
 * as a foreground service using SpeechRecognizer.
 */
class AssistantRecognitionService : RecognitionService() {

    companion object {
        const val TAG = "AssistantRecognition"
    }

    override fun onStartListening(intent: android.content.Intent?, listener: Callback?) {
        Log.d(TAG, "onStartListening")
    }

    override fun onStopListening(listener: Callback?) {
        Log.d(TAG, "onStopListening")
    }

    override fun onCancel(listener: Callback?) {
        Log.d(TAG, "onCancel")
    }
}
