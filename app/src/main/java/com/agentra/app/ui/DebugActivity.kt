package com.agentra.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.agentra.app.databinding.ActivityDebugBinding

class DebugActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDebugBinding

    companion object {
        var aiResponse: String = ""
        var errors: String = ""
        var rawResponse: String = ""
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDebugBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupButtons()
        updateUI()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupButtons() {
        binding.btnClear.setOnClickListener {
            aiResponse = ""
            errors = ""
            rawResponse = ""
            updateUI()
        }

        binding.btnCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = buildString {
                appendLine("=== AI Response ===")
                appendLine(aiResponse)
                appendLine()
                appendLine("=== Errors ===")
                appendLine(errors)
                appendLine()
                appendLine("=== Raw Response ===")
                appendLine(rawResponse)
            }
            clipboard.setPrimaryClip(ClipData.newPlainText("Agentra Debug", text))
        }
    }

    private fun updateUI() {
        binding.tvAIResponse.text = aiResponse.ifEmpty { "No response yet" }
        binding.tvErrors.text = errors.ifEmpty { "No errors" }
        binding.tvRawResponse.text = rawResponse.ifEmpty { "No raw response" }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    fun updateDebugInfo(ai: String, err: String, raw: String) {
        aiResponse = ai
        errors = err
        rawResponse = raw
    }
}
