package com.agentra.app

import android.content.Context
import android.content.SharedPreferences
import com.agentra.app.config.AppConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppConfigTest {

    private lateinit var config: AppConfig
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private val capturedApply = slot<() -> Unit>()

    @Before
    fun setUp() {
        mockPrefs = mockk()
        mockEditor = mockk()

        every { mockPrefs.getString(any(), any()) } returns ""
        every { mockPrefs.getFloat(any(), any()) } returns 0.0f
        every { mockPrefs.getInt(any(), any()) } returns 0
        every { mockPrefs.getLong(any(), any()) } returns 0L
        every { mockPrefs.getBoolean(any(), any()) } returns false
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.putFloat(any(), any()) } returns mockEditor
        every { mockEditor.putInt(any(), any()) } returns mockEditor
        every { mockEditor.putLong(any(), any()) } returns mockEditor
        every { mockEditor.putBoolean(any(), any()) } returns mockEditor
        every { mockEditor.apply() } returns Unit

        val mockContext: Context = mockk {
            every { getSharedPreferences("agentra_config", Context.MODE_PRIVATE) } returns mockPrefs
        }

        config = AppConfig(mockContext)
    }

    @Test
    fun `default model name is Qwen 3_5`() {
        every { mockPrefs.getString("model_name", "Qwen 3.5") } returns "Qwen 3.5"
        assertEquals("Qwen 3.5", config.modelName)
    }

    @Test
    fun `set and get model name`() {
        config.modelName = "GPT-4"
        verify { mockEditor.putString("model_name", "GPT-4") }
    }

    @Test
    fun `set and get API endpoint`() {
        config.apiEndpoint = "https://api.openai.com/v1/chat"
        verify { mockEditor.putString("api_endpoint", "https://api.openai.com/v1/chat") }
    }

    @Test
    fun `set and get API key`() {
        config.apiKey = "sk-test-key-12345"
        verify { mockEditor.putString("api_key", "sk-test-key-12345") }
    }

    @Test
    fun `set and get temperature`() {
        config.temperature = 0.5f
        verify { mockEditor.putFloat("temperature", 0.5f) }
    }

    @Test
    fun `set and get max tokens`() {
        config.maxTokens = 4096
        verify { mockEditor.putInt("max_tokens", 4096) }
    }

    @Test
    fun `set and get execution delay`() {
        config.executionDelay = 1000L
        verify { mockEditor.putLong("execution_delay", 1000L) }
    }

    @Test
    fun `default wake word enabled is false`() {
        every { mockPrefs.getBoolean("wake_word_enabled", false) } returns false
        assertFalse(config.isWakeWordEnabled)
    }

    @Test
    fun `set wake word enabled to true`() {
        config.isWakeWordEnabled = true
        verify { mockEditor.putBoolean("wake_word_enabled", true) }
    }

    @Test
    fun `wake word phrase get default`() {
        every { mockPrefs.getString("wake_word_phrase", "Hey Agentra") } returns "Hey Agentra"
        assertEquals("Hey Agentra", config.wakeWordPhrase)
    }

    @Test
    fun `set custom wake word phrase`() {
        config.wakeWordPhrase = "Hey Jarvis"
        verify { mockEditor.putString("wake_word_phrase", "Hey Jarvis") }
    }

    @Test
    fun `getModelConfig returns expected values`() {
        every { mockPrefs.getString("model_name", "Qwen 3.5") } returns "Test-Model"
        every { mockPrefs.getString("api_endpoint", any()) } returns "https://test.api"
        every { mockPrefs.getString("api_key", any()) } returns "test-key"
        every { mockPrefs.getFloat("temperature", 0.7f) } returns 0.3f
        every { mockPrefs.getInt("max_tokens", 2048) } returns 512

        val modelConfig = config.getModelConfig()
        assertEquals("Test-Model", modelConfig.name)
        assertEquals("https://test.api", modelConfig.endpoint)
        assertEquals("test-key", modelConfig.apiKey)
        assertEquals(0.3f, modelConfig.temperature, 0.01f)
        assertEquals(512, modelConfig.maxTokens)
    }

    @Test
    fun `save calls apply`() {
        config.save()
        verify { mockEditor.apply() }
    }
}
