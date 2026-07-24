package com.agentra.app.service

import android.service.voice.VoiceInteractionService
import android.util.Log

/**
 * VoiceInteractionService that registers Agentra as the system's default
 * digital assistant. The companion [AssistantSessionService] handles
 * the actual session creation and UI launch.
 *
 * To set as default assistant:
 *   Settings → Apps → Default apps → Digital assistant app → Agentra
 */
class AssistantService : VoiceInteractionService() {

    companion object {
        const val TAG = "AssistantService"
    }

    override fun onReady() {
        super.onReady()
        Log.i(TAG, "AssistantService ready — registered as system assistant")
    }
}
