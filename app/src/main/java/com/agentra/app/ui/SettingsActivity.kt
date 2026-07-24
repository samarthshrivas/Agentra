package com.agentra.app.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.agentra.app.R
import com.agentra.app.config.AppConfig
import com.agentra.app.databinding.ActivitySettingsBinding
import com.agentra.app.service.FloatingPromptService
import com.agentra.app.service.WakeWordService

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var config: AppConfig

    private val microphonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        binding.switchMicrophone.isChecked = granted
        updateMicrophoneStatus(granted)
        if (granted && binding.switchWakeWord.isChecked) {
            WakeWordService.start(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        config = AppConfig(this)
        setupToolbar()
        setupAIConfig()
        setupAssistantConfig()
        setupPermissions()
        loadSettings()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            saveSettings()
        }
    }

    private fun setupAIConfig() {
        binding.sliderTemperature.addOnChangeListener { _, value, _ ->
            binding.tvTemperature.text = String.format("%.1f", value)
        }

        binding.sliderMaxTokens.addOnChangeListener { _, value, _ ->
            binding.tvMaxTokens.text = value.toInt().toString()
        }
    }

    private fun setupAssistantConfig() {
        // Wake word toggle
        binding.switchWakeWord.setOnCheckedChangeListener { _, isChecked ->
            config.isWakeWordEnabled = isChecked
            updateWakeWordStatus()

            if (isChecked) {
                // Check microphone permission first
                if (hasMicrophonePermission()) {
                    WakeWordService.start(this)
                } else {
                    requestMicrophonePermission()
                }
            } else {
                WakeWordService.stop(this)
            }
        }

        // Wake word phrase
        binding.etWakeWordPhrase.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val phrase = binding.etWakeWordPhrase.text?.toString()?.trim()
                if (!phrase.isNullOrBlank()) {
                    config.wakeWordPhrase = phrase
                    // Restart wake word service if active
                    if (config.isWakeWordEnabled) {
                        WakeWordService.stop(this)
                        WakeWordService.start(this)
                    }
                }
            }
        }

        // Default assistant link
        binding.layoutDefaultAssistant.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
            } catch (e: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.fromParts("package", packageName, null)
                    })
                } catch (e2: Exception) {
                    Toast.makeText(this, R.string.default_assistant_instructions, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupPermissions() {
        // Microphone permission
        binding.layoutMicrophone.setOnClickListener {
            if (!hasMicrophonePermission()) {
                requestMicrophonePermission()
            } else {
                Toast.makeText(this, "Microphone permission already granted", Toast.LENGTH_SHORT).show()
            }
        }

        binding.switchMicrophone.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !hasMicrophonePermission()) {
                requestMicrophonePermission()
            }
        }

        // Accessibility
        binding.layoutAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.switchAccessibility.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != isAccessibilityEnabled()) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        // Test Tools
        binding.layoutTestTools.setOnClickListener {
            startActivity(Intent(this, TestToolsActivity::class.java))
        }

        // Floating button
        binding.switchFloatingButton.setOnCheckedChangeListener { _, isChecked ->
            config.isFloatingButtonEnabled = isChecked
            if (isChecked) {
                if (hasOverlayPermission()) {
                    FloatingPromptService.start(this)
                } else {
                    requestOverlayPermission()
                    binding.switchFloatingButton.isChecked = false
                }
            } else {
                FloatingPromptService.stop(this)
            }
            updateFloatingButtonStatus()
        }

        binding.layoutFloatingButton.setOnClickListener {
            val isChecked = !binding.switchFloatingButton.isChecked
            binding.switchFloatingButton.isChecked = isChecked
        }

        // Save button
        binding.btnSaveSettings.setOnClickListener {
            saveSettings()
        }
    }

    private fun loadSettings() {
        // AI config
        binding.etModel.setText(config.modelName)
        binding.etApiEndpoint.setText(config.apiEndpoint)
        binding.etApiKey.setText(config.apiKey)
        binding.sliderTemperature.value = config.temperature
        binding.sliderMaxTokens.value = config.maxTokens.toFloat()
        binding.tvTemperature.text = String.format("%.1f", config.temperature)
        binding.tvMaxTokens.text = config.maxTokens.toString()

        // Wake word
        binding.switchWakeWord.isChecked = config.isWakeWordEnabled
        binding.etWakeWordPhrase.setText(config.wakeWordPhrase)

        // Floating button
        binding.switchFloatingButton.isChecked = config.isFloatingButtonEnabled
        updateFloatingButtonStatus()
    }

    private fun saveSettings() {
        config.modelName = binding.etModel.text?.toString()?.trim() ?: "deepseek-v4-flash-free"
        config.apiEndpoint = binding.etApiEndpoint.text.toString()
        config.apiKey = binding.etApiKey.text.toString()
        config.temperature = binding.sliderTemperature.value
        config.maxTokens = binding.sliderMaxTokens.value.toInt()

        // Wake word
        val phrase = binding.etWakeWordPhrase.text?.toString()?.trim()
        if (!phrase.isNullOrBlank()) {
            config.wakeWordPhrase = phrase
        }
        config.isWakeWordEnabled = binding.switchWakeWord.isChecked

        config.save()

        // Restart wake word service if needed
        if (config.isWakeWordEnabled && hasMicrophonePermission()) {
            WakeWordService.stop(this)
            WakeWordService.start(this)
        }

        // Return to main activity
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    // ─── Permission helpers ───

    private fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun requestMicrophonePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(this)
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
        Toast.makeText(this, "Grant the 'Display over other apps' permission", Toast.LENGTH_LONG).show()
    }

    private fun updateFloatingButtonStatus() {
        val enabled = config.isFloatingButtonEnabled && hasOverlayPermission()
        binding.switchFloatingButton.isChecked = enabled
        binding.tvFloatingButtonStatus.text = if (enabled) {
            "Floating button active"
        } else if (!hasOverlayPermission()) {
            "Requires overlay permission"
        } else {
            getString(R.string.floating_button_summary)
        }
        binding.tvFloatingButtonStatus.setTextColor(
            getColor(if (enabled) R.color.status_success else R.color.on_surface_variant)
        )
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return enabledServices.any { it.id.contains(packageName) }
    }

    // ─── Status updates ───

    private fun updateWakeWordStatus() {
        val enabled = config.isWakeWordEnabled
        binding.tvWakeWordStatus.text = if (enabled) {
            getString(R.string.wake_word_listening, config.wakeWordPhrase)
        } else {
            getString(R.string.wake_word_disabled)
        }
        binding.tvWakeWordStatus.setTextColor(
            getColor(if (enabled) R.color.status_success else R.color.on_surface_variant)
        )
    }

    private fun updateMicrophoneStatus(granted: Boolean) {
        binding.tvMicrophoneStatus.text = if (granted) "Granted" else "Not granted"
        binding.tvMicrophoneStatus.setTextColor(
            getColor(if (granted) R.color.status_success else R.color.status_error)
        )
        binding.switchMicrophone.isChecked = granted
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus()
        updateWakeWordStatus()
        updateMicrophoneStatus(hasMicrophonePermission())
        updateFloatingButtonStatus()
    }

    private fun updateAccessibilityStatus() {
        val enabled = isAccessibilityEnabled()
        binding.switchAccessibility.isChecked = enabled
        binding.tvAccessibilityStatus.text = if (enabled) "Enabled" else "Not enabled"
        binding.tvAccessibilityStatus.setTextColor(
            getColor(if (enabled) R.color.status_success else R.color.status_error)
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        saveSettings()
    }
}
