package com.agentra.app.service

import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.util.Log
import com.agentra.app.ui.MainActivity

/**
 * VoiceInteractionSessionService that creates sessions for the Agentra assistant.
 * Required for the VoiceInteractionService to be recognized as a valid
 * system-level digital assistant.
 */
class AssistantSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(bundle: Bundle?): VoiceInteractionSession {
        Log.i(AssistantService.TAG, "Creating new assistant session")
        return AssistantSession()
    }

    inner class AssistantSession : VoiceInteractionSession(this@AssistantSessionService) {

        override fun onCreate() {
            super.onCreate()
            Log.i(AssistantService.TAG, "Assistant session created")
        }

        override fun onShow(args: Bundle?, showFlags: Int) {
            super.onShow(args, showFlags)
            Log.i(AssistantService.TAG, "Assistant session shown — launching MainActivity")

            val intent = Intent(this@AssistantSessionService, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("assistant_triggered", true)
            }
            this@AssistantSessionService.startActivity(intent)
        }

        override fun onHide() {
            super.onHide()
            Log.i(AssistantService.TAG, "Assistant session hidden")
        }
    }
}
