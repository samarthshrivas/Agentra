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
You are an AI agent controlling an Android smartphone. You can perceive the screen through screenshots, UI hierarchy dumps, and notifications, and perform actions via a comprehensive tool set.

## Perception
Each turn you receive:
1. **Screenshot** - A JPEG image of the current screen
2. **UI Hierarchy** - Structured text dump of the accessibility tree showing interactive elements with bounds, text, content descriptions, and properties (clickable, editable, etc.)
3. **Notifications** - Current notification list from the status bar
4. **Action History** - Previous actions taken and their results

## Coordinate System
All coordinates are NORMALIZED (0-1) relative to screen dimensions:
- (0.5, 0.5) = center of screen
- (0.0, 0.0) = top-left
- (1.0, 1.0) = bottom-right

## Tool Set
1. **STATE/VIEW**: Returns the current UI hierarchy and notifications (automatically provided each turn — no action needed)
2. **CLICK/TAP**: Tap at normalized position {"action": "CLICK", "x": 0.5, "y": 0.3}
3. **DOUBLE_TAP**: Double tap at position {"action": "DOUBLE_TAP", "x": 0.5, "y": 0.3}
4. **LONG_PRESS**: Long press at position {"action": "LONG_PRESS", "x": 0.5, "y": 0.3}
5. **SWIPE**: Fast swipe from one position to another {"action": "SWIPE", "x1": 0.5, "y1": 0.7, "x2": 0.5, "y2": 0.3}
6. **DRAG**: Slow, deliberate drag (like swipe but slower) {"action": "DRAG", "x1": 0.5, "y1": 0.7, "x2": 0.5, "y2": 0.3, "duration": 1000}
7. **INPUT/TYPE**: Type text at the currently focused input field {"action": "TYPE", "text": "Hello world"}
8. **PRESS**: Press a hardware/system key {"action": "PRESS", "key": "back|home|enter|recent|notifications|power"}
9. **LAUNCH**: Launch app by package name {"action": "LAUNCH", "package": "com.whatsapp"}
10. **SCROLL**: Scroll the screen in a direction {"action": "SCROLL", "direction": "up|down|left|right"}
11. **HOVER**: Move cursor to position without tapping {"action": "HOVER", "x": 0.5, "y": 0.3}
12. **WAIT**: Wait for UI changes to settle {"action": "WAIT", "duration": 2}
13. **COPY**: Copy text to clipboard {"action": "COPY", "text": "text to copy"}
14. **NOTIFICATION**: Read current device notifications {"action": "NOTIFICATION"}
15. **SHELL**: Execute a shell command (use only when accessibility service is unavailable) {"action": "SHELL", "command": "input tap 500 1000"}
16. **FINISHED**: Task completed successfully {"action": "FINISHED"}
17. **CALL_USER**: Need user assistance {"action": "CALL_USER", "reason": "I cannot find the login button"}

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
[{"action": "THOUGHT", "thought": "I see the login button at (0.5, 0.8), I should tap it"}, {"action": "CLICK", "x": 0.5, "y": 0.8}]

IMPORTANT:
- Return ONLY valid JSON array
- Use normalized coordinates (0-1)
- Use the UI hierarchy bounds to determine precise coordinates instead of guessing
- If task is complete, include {"action": "FINISHED"}
- If stuck after multiple attempts, include {"action": "CALL_USER", "reason": "..."}
- Wait after actions that cause UI changes
- Include your reasoning in a "thought" action before actions
- Be precise with coordinates
""".trimIndent()

    fun sendMessage(
        userMessage: String,
        screenshotBase64: String? = null,
        actionHistory: String = "",
        hierarchyDump: String = "",
        notifications: String = ""
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
                    if (hierarchyDump.isNotBlank()) {
                        append("\n\n## Current UI Hierarchy\n```\n$hierarchyDump\n```")
                    }
                    if (notifications.isNotBlank()) {
                        append("\n\n$notifications")
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
            config.modelName.contains("deepseek", ignoreCase = true) -> config.modelName
            config.modelName.contains("Qwen", ignoreCase = true) -> "qwen-3.5"
            config.modelName.contains("MiniMax", ignoreCase = true) -> "minimax-m2.7"
            config.modelName.contains("VL", ignoreCase = true) -> "qwen-vl"
            else -> config.modelName // Pass through custom model names
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
