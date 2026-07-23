package com.agentra.app.llm

import com.agentra.app.config.AppConfig
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class LLMInterface(private val config: AppConfig) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private val systemPrompt = """
You are an AI agent controlling an Android smartphone. You can see the screen through screenshots and perform actions.

## Your Capabilities
- View the screen in real-time via screenshots
- Tap, swipe, scroll, type, and press keys
- Launch apps by package name
- Wait for UI changes to occur

## Coordinate System
All coordinates are NORMALIZED (0-1) relative to screen dimensions:
- (0.5, 0.5) = center of screen
- (0.0, 0.0) = top-left
- (1.0, 1.0) = bottom-right
- Typical phone: 1080x1920 or similar

## Action Space
1. **CLICK/TAP**: Tap at normalized position {"action": "CLICK", "x": 0.5, "y": 0.3}
2. **DOUBLE_TAP**: Double tap at position {"action": "DOUBLE_TAP", "x": 0.5, "y": 0.3}
3. **LONG_PRESS**: Long press at position {"action": "LONG_PRESS", "x": 0.5, "y": 0.3, "duration": 1000}
4. **SWIPE**: Swipe from one position to another {"action": "SWIPE", "x1": 0.5, "y1": 0.7, "x2": 0.5, "y2": 0.3}
5. **INPUT/TYPE**: Type text at current focus {"action": "TYPE", "text": "Hello"}
6. **PRESS**: Press a key {"action": "PRESS", "key": "back|home|enter|delete"}
7. **LAUNCH**: Launch app by package name {"action": "LAUNCH", "package": "com.whatsapp"}
8. **SCROLL**: Scroll screen {"action": "SCROLL", "direction": "up|down|left|right"}
9. **WAIT**: Wait for changes {"action": "WAIT", "duration": 2}
10. **COPY**: Copy text to clipboard {"action": "COPY", "text": "text to copy"}
11. **FINISHED**: Task completed {"action": "FINISHED"}
12. **CALL_USER**: Need user help {"action": "CALL_USER", "reason": "explanation"}

## Common App Package Names
- WhatsApp: com.whatsapp
- Messages: com.google.android.apps.messaging
- Chrome: com.android.chrome
- Settings: com.android.settings
- Camera: com.android.camera2
- Phone: com.android.dialer
- Photos: com.google.android.apps.photos
- Gmail: com.google.android.gm
- YouTube: com.google.android.youtube
- Maps: com.google.android.apps.maps

## Output Format
Return a JSON array with ONE OR MORE actions to perform:
[{"action": "THOUGHT", "thought": "I see the login button, I should tap it"}, {"action": "CLICK", "x": 0.5, "y": 0.8}]

IMPORTANT:
- Return ONLY valid JSON array
- Use normalized coordinates (0-1)
- If task is complete, include {"action": "FINISHED"}
- If stuck after multiple attempts, include {"action": "CALL_USER", "reason": "..."}
- Wait after actions that cause UI changes
- Include your reasoning in "thought" field before actions
- Be precise with coordinates
""".trimIndent()

    fun sendMessage(
        userMessage: String,
        screenshotBase64: String? = null,
        actionHistory: String = ""
    ): String? {
        val fullMessage = buildString {
            append(userMessage)
            append(actionHistory)
        }

        val messages = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("role", "system")
                addProperty("content", systemPrompt)
            })
            add(JsonObject().apply {
                addProperty("role", "user")
                addProperty("content", buildString {
                    append(fullMessage)
                    if (screenshotBase64 != null) {
                        append("\n\n[Screenshot attached - base64 encoded JPEG image of current screen]")
                    }
                })
            })
        }

        val requestBody = JsonObject().apply {
            add("messages", messages)
            addProperty("model", getModelIdentifier())
            addProperty("temperature", config.temperature)
            addProperty("max_tokens", config.maxTokens)
            addProperty("stream", false)
        }

        val request = Request.Builder()
            .url(config.apiEndpoint)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    parseResponse(body)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getModelIdentifier(): String {
        return when {
            config.modelName.contains("Qwen", ignoreCase = true) -> "qwen-3.5"
            config.modelName.contains("MiniMax", ignoreCase = true) -> "minimax-m2.7"
            config.modelName.contains("VL", ignoreCase = true) -> "qwen-vl"
            else -> "gpt-4"
        }
    }

    private fun parseResponse(responseBody: String): String? {
        return try {
            val json = gson.fromJson(responseBody, JsonObject::class.java)
            json.getAsJsonArray("choices")
                ?.firstOrNull()
                ?.asJsonObject
                ?.getAsJsonObject("message")
                ?.get("content")
                ?.asString
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            try {
                val json = gson.fromJson(responseBody, JsonObject::class.java)
                json.getAsJsonArray("choices")
                    ?.firstOrNull()
                    ?.asJsonObject
                    ?.getAsJsonObject("delta")
                    ?.get("content")
                    ?.asString
            } catch (e2: Exception) {
                null
            }
        }
    }
}
