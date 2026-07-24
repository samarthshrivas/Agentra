package com.agentra.app

import android.content.Context
import android.content.SharedPreferences
import com.agentra.app.config.AppConfig
import com.agentra.app.llm.LLMInterface
import io.mockk.every
import io.mockk.mockk
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LLMInterfaceTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var llm: LLMInterface
    private lateinit var config: AppConfig

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()

        val mockPrefs: SharedPreferences = mockk()
        val mockEditor: SharedPreferences.Editor = mockk()

        // Mock string prefs with proper default value matching
        every { mockPrefs.getString("model_name", "Qwen 3.5") } returns "Test-Model"
        every { mockPrefs.getString("api_endpoint", "https://api.example.com/v1/chat") } returns mockWebServer.url("/v1/chat").toString()
        every { mockPrefs.getString("api_key", "") } returns "test-key"
        every { mockPrefs.getString("wake_word_phrase", "Hey Agentra") } returns "Hey Agentra"

        every { mockPrefs.getFloat("temperature", 0.7f) } returns 0.7f
        every { mockPrefs.getInt("max_tokens", 2048) } returns 2048
        every { mockPrefs.getLong("execution_delay", 500L) } returns 500L
        every { mockPrefs.getBoolean("wake_word_enabled", false) } returns false
        every { mockPrefs.edit() } returns mockEditor

        // Mock editor
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
        llm = LLMInterface(config)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `sendMessage returns parsed content on successful response`() {
        mockWebServer.enqueue(MockResponse().setBody("""
            {
                "choices": [
                    {
                        "message": {
                            "content": "[{\"action\": \"CLICK\", \"x\": 0.5, \"y\": 0.5}]"
                        }
                    }
                ]
            }
        """.trimIndent()).setResponseCode(200))

        val result = llm.sendMessage("Tap the center")
        assertNotNull(result)
        assertEquals("[{\"action\": \"CLICK\", \"x\": 0.5, \"y\": 0.5}]", result)
    }

    @Test
    fun `sendMessage returns null on non-200 response`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val result = llm.sendMessage("test")
        assertNull(result)
    }

    @Test
    fun `sendMessage returns null on invalid response body`() {
        mockWebServer.enqueue(MockResponse().setBody("not json").setResponseCode(200))

        val result = llm.sendMessage("test")
        assertNull(result)
    }

    @Test
    fun `sendMessage returns null on empty choices array`() {
        mockWebServer.enqueue(MockResponse().setBody("""
            {
                "choices": []
            }
        """.trimIndent()).setResponseCode(200))

        val result = llm.sendMessage("test")
        assertNull(result)
    }

    @Test
    fun `sendMessage sends POST request with user message`() {
        mockWebServer.enqueue(MockResponse().setBody("""
            {
                "choices": [{"message": {"content": "ok"}}]
            }
        """.trimIndent()).setResponseCode(200))

        llm.sendMessage("Hello LLM")

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        val body = recordedRequest.body.readUtf8()
        assertTrue(body.contains("Hello LLM"))
        assertTrue(body.contains("model"))
        assertTrue(body.contains("messages"))
    }

    @Test
    fun `sendMessage request has correct auth header`() {
        mockWebServer.enqueue(MockResponse().setBody("""
            {
                "choices": [{"message": {"content": "ok"}}]
            }
        """.trimIndent()).setResponseCode(200))

        llm.sendMessage("test")

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("Bearer test-key", recordedRequest.getHeader("Authorization"))
    }

    @Test
    fun `sendMessage request has content type header`() {
        mockWebServer.enqueue(MockResponse().setBody("""
            {
                "choices": [{"message": {"content": "ok"}}]
            }
        """.trimIndent()).setResponseCode(200))

        llm.sendMessage("test")

        val recordedRequest = mockWebServer.takeRequest()
        val contentType = recordedRequest.getHeader("Content-Type")
        assertNotNull(contentType)
        assertTrue(contentType!!.startsWith("application/json"))
    }

    @Test
    fun `sendMessage with screenshot still returns content`() {
        mockWebServer.enqueue(MockResponse().setBody("""
            {
                "choices": [{"message": {"content": "action done"}}]
            }
        """.trimIndent()).setResponseCode(200))

        val result = llm.sendMessage("test", "base64imagestringhere")
        assertNotNull(result)
        assertEquals("action done", result)
    }

    @Test
    fun `sendMessage with action history includes it in the request`() {
        mockWebServer.enqueue(MockResponse().setBody("""
            {
                "choices": [{"message": {"content": "ok"}}]
            }
        """.trimIndent()).setResponseCode(200))

        llm.sendMessage("test", null, "\n## Action History:\n1. [TAP] -> (success)")

        val recordedRequest = mockWebServer.takeRequest()
        val body = recordedRequest.body.readUtf8()
        assertTrue(body.contains("Action History"))
    }

    @Test
    fun `sendMessage includes model parameter in request body`() {
        mockWebServer.enqueue(MockResponse().setBody("""
            {
                "choices": [{"message": {"content": "ok"}}]
            }
        """.trimIndent()).setResponseCode(200))

        llm.sendMessage("test")

        val recordedRequest = mockWebServer.takeRequest()
        val body = recordedRequest.body.readUtf8()
        assertTrue(body.contains("\"model\""))
        assertTrue(body.contains("\"max_tokens\""))
        assertTrue(body.contains("\"temperature\""))
    }
}
