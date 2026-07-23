package com.agentra.app.config

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

class AppConfig(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    var modelName: String
        get() = prefs.getString(KEY_MODEL_NAME, "Qwen 3.5") ?: "Qwen 3.5"
        set(value) = prefs.edit().putString(KEY_MODEL_NAME, value).apply()

    var apiEndpoint: String
        get() = prefs.getString(KEY_API_ENDPOINT, "https://api.example.com/v1/chat") ?: ""
        set(value) = prefs.edit().putString(KEY_API_ENDPOINT, value).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    var temperature: Float
        get() = prefs.getFloat(KEY_TEMPERATURE, 0.7f)
        set(value) = prefs.edit().putFloat(KEY_TEMPERATURE, value).apply()

    var maxTokens: Int
        get() = prefs.getInt(KEY_MAX_TOKENS, 2048)
        set(value) = prefs.edit().putInt(KEY_MAX_TOKENS, value).apply()

    var executionDelay: Long
        get() = prefs.getLong(KEY_EXECUTION_DELAY, 500L)
        set(value) = prefs.edit().putLong(KEY_EXECUTION_DELAY, value).apply()

    fun save() {
        prefs.edit().apply()
    }

    fun getModelConfig(): ModelConfig {
        return ModelConfig(
            name = modelName,
            endpoint = apiEndpoint,
            apiKey = apiKey,
            temperature = temperature,
            maxTokens = maxTokens
        )
    }

    data class ModelConfig(
        val name: String,
        val endpoint: String,
        val apiKey: String,
        val temperature: Float,
        val maxTokens: Int
    )

    companion object {
        private const val PREFS_NAME = "agentra_config"
        private const val KEY_MODEL_NAME = "model_name"
        private const val KEY_API_ENDPOINT = "api_endpoint"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_MAX_TOKENS = "max_tokens"
        private const val KEY_EXECUTION_DELAY = "execution_delay"
    }
}
