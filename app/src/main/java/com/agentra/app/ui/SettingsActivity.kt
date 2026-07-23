package com.agentra.app.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.agentra.app.R
import com.agentra.app.config.AppConfig
import com.agentra.app.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var config: AppConfig

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { updateScreenCaptureStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        config = AppConfig(this)
        setupToolbar()
        setupAIConfig()
        setupPermissions()
        loadSettings()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { 
            saveSettings() 
        }
    }

    private fun setupAIConfig() {
        // Allow custom model input by using editable TextInputEditText
        val models = listOf("Qwen 3.5", "MiniMax M2.7", "Custom")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, models)
        binding.actvModel.setAdapter(adapter)
        
        // Enable typing for custom model
        binding.actvModel.setOnClickListener {
            binding.actvModel.showDropDown()
        }
        
        binding.actvModel.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.actvModel.showDropDown()
            }
        }

        binding.sliderTemperature.addOnChangeListener { _, value, _ ->
            binding.tvTemperature.text = String.format("%.1f", value)
        }

        binding.sliderMaxTokens.addOnChangeListener { _, value, _ ->
            binding.tvMaxTokens.text = value.toInt().toString()
        }
    }

    private fun setupPermissions() {
        binding.layoutAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.layoutScreenCapture.setOnClickListener {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            screenCaptureLauncher.launch(mpm.createScreenCaptureIntent())
        }

        binding.switchAccessibility.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != isAccessibilityEnabled()) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        binding.switchScreenCapture.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                screenCaptureLauncher.launch(mpm.createScreenCaptureIntent())
            }
        }
        
        // Save button
        binding.btnSaveSettings.setOnClickListener {
            saveSettings()
        }
    }

    private fun loadSettings() {
        binding.actvModel.setText(config.modelName, false)
        binding.etApiEndpoint.setText(config.apiEndpoint)
        binding.etApiKey.setText(config.apiKey)
        binding.sliderTemperature.value = config.temperature
        binding.sliderMaxTokens.value = config.maxTokens.toFloat()
        binding.tvTemperature.text = String.format("%.1f", config.temperature)
        binding.tvMaxTokens.text = config.maxTokens.toString()
    }

    private fun saveSettings() {
        config.modelName = binding.actvModel.text.toString()
        config.apiEndpoint = binding.etApiEndpoint.text.toString()
        config.apiKey = binding.etApiKey.text.toString()
        config.temperature = binding.sliderTemperature.value
        config.maxTokens = binding.sliderMaxTokens.value.toInt()
        config.save()
        
        // Return to main activity
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return enabledServices.any { it.id.contains(packageName) }
    }

    private fun updateAccessibilityStatus() {
        val enabled = isAccessibilityEnabled()
        binding.switchAccessibility.isChecked = enabled
        binding.tvAccessibilityStatus.text = if (enabled) "Enabled" else "Not enabled"
        binding.tvAccessibilityStatus.setTextColor(
            getColor(if (enabled) R.color.status_success else R.color.status_error)
        )
    }

    private fun updateScreenCaptureStatus() {
        binding.switchScreenCapture.isChecked = true
        binding.tvScreenCaptureStatus.text = "Granted"
        binding.tvScreenCaptureStatus.setTextColor(getColor(R.color.status_success))
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        saveSettings()
    }
}
