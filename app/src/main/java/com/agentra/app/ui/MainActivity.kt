package com.agentra.app.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.agentra.app.R
import com.agentra.app.agent.AgentCore
import com.agentra.app.config.AppConfig
import com.agentra.app.databinding.ActivityMainBinding
import com.agentra.app.screenshot.ScreenshotManager
import com.agentra.app.service.AgentAccessibilityService
import com.agentra.app.ui.adapter.LogAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var logAdapter: LogAdapter
    private lateinit var config: AppConfig
    private lateinit var agentCore: AgentCore
    private lateinit var screenshotManager: ScreenshotManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        config = AppConfig(this)
        screenshotManager = ScreenshotManager(this)
        agentCore = AgentCore(this, screenshotManager, config, AgentAccessibilityService.instance)

        setupUI()
        checkAccessibility()
        handleLaunchIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLaunchIntent(intent)
    }

    /**
     * Handles being launched from WakeWordService or AssistantService.
     */
    private fun handleLaunchIntent(intent: Intent?) {
        if (intent == null) return

        // Launched by wake word detection
        if (intent.getBooleanExtra("wake_word_triggered", false)) {
            val voiceInput = intent.getStringExtra("voice_input") ?: ""
            addLog("Wake word detected! Voice input: \"$voiceInput\"", LogAdapter.LogType.AGENT)
            binding.etCommand.setText(voiceInput)
        }

        // Launched by system assistant (home button long-press)
        if (intent.getBooleanExtra("assistant_triggered", false)) {
            addLog("Assistant triggered by system gesture", LogAdapter.LogType.INFO)
        }

        // Launched by floating button overlay
        val floatingPrompt = intent.getStringExtra("floating_prompt")
        if (!floatingPrompt.isNullOrBlank()) {
            addLog("Quick task: \"$floatingPrompt\"", LogAdapter.LogType.USER)
            binding.etCommand.setText(floatingPrompt)
            executeCommand()
        }
    }

    private fun setupUI() {
        logAdapter = LogAdapter()
        binding.rvLogs.apply {
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                stackFromEnd = true
            }
            adapter = logAdapter
        }

        binding.btnExecute.setOnClickListener {
            executeCommand()
        }

        binding.btnStop.setOnClickListener {
            stopExecution()
        }

        binding.btnClearLogs.setOnClickListener {
            logAdapter.clear()
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnDebug.setOnClickListener {
            startActivity(Intent(this, DebugActivity::class.java))
        }
    }

    private fun executeCommand() {
        val command = binding.etCommand.text?.toString()?.trim()
        if (command.isNullOrEmpty()) {
            addLog("Please enter a command", LogAdapter.LogType.ERROR)
            return
        }

        if (!isAccessibilityEnabled()) {
            addLog("Accessibility service not enabled", LogAdapter.LogType.ERROR)
            return
        }

        updateStatus(true)
        addLog("Executing: $command", LogAdapter.LogType.USER)

        lifecycleScope.launch {
            agentCore.execute(command) { log, type ->
                runOnUiThread { addLog(log, type) }
            }
            runOnUiThread { updateStatus(false) }
        }
    }

    private fun stopExecution() {
        agentCore.stop()
        updateStatus(false)
        addLog("Execution stopped", LogAdapter.LogType.INFO)
    }

    private fun updateStatus(running: Boolean) {
        binding.tvStatus.text = if (running) getString(R.string.status_running) else getString(R.string.status_idle)
        binding.progressIndicator.isVisible = running
        binding.btnExecute.isVisible = !running
        binding.btnStop.isVisible = running
    }

    private fun addLog(message: String, type: LogAdapter.LogType) {
        logAdapter.addLog(message, type)
        binding.rvLogs.smoothScrollToPosition(logAdapter.itemCount - 1)
    }

    private fun checkAccessibility() {
        if (isAccessibilityEnabled()) {
            addLog("Accessibility service enabled", LogAdapter.LogType.INFO)
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return enabledServices.any { it.id.contains(packageName) }
    }

    override fun onResume() {
        super.onResume()
        checkAccessibility()
    }
}
