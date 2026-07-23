package com.agentra.app.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.agentra.app.R
import com.agentra.app.agent.AgentCore
import com.agentra.app.config.AppConfig
import com.agentra.app.databinding.ActivityMainBinding
import com.agentra.app.screenshot.ScreenshotManager
import com.agentra.app.ui.adapter.LogAdapter
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var logAdapter: LogAdapter
    private lateinit var config: AppConfig
    private lateinit var agentCore: AgentCore
    private lateinit var screenshotManager: ScreenshotManager

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            screenshotManager.startCapture(result.resultCode, result.data!!)
            updateScreenCaptureStatus(true)
            addLog("Screen capture permission granted", LogAdapter.LogType.INFO)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        config = AppConfig(this)
        screenshotManager = ScreenshotManager(this)
        agentCore = AgentCore(this, screenshotManager, config)

        setupUI()
        checkPermissions()
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

        if (!screenshotManager.isCaptureActive()) {
            requestScreenCapture()
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

    private fun checkPermissions() {
        updateAccessibilityStatus(isAccessibilityEnabled())
        updateScreenCaptureStatus(screenshotManager.isCaptureActive())
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return enabledServices.any { it.id.contains(packageName) }
    }

    private fun updateAccessibilityStatus(enabled: Boolean) {
        if (enabled) {
            addLog("Accessibility service enabled", LogAdapter.LogType.INFO)
        }
    }

    private fun updateScreenCaptureStatus(granted: Boolean) {
        if (granted) {
            addLog("Screen capture ready", LogAdapter.LogType.INFO)
        }
    }

    private fun requestScreenCapture() {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(mpm.createScreenCaptureIntent())
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }
}
