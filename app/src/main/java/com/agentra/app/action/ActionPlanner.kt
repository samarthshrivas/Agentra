package com.agentra.app.action

import com.google.gson.JsonParser

class ActionPlanner {
    companion object { const val MAX_ITERATIONS = 15; const val MAX_FAILED_ATTEMPTS = 3 }

    fun parseActions(response: String): List<ActionExecutor.Action> {
        val actions = mutableListOf<ActionExecutor.Action>()
        try {
            val cleaned = cleanResponse(response)
            val jsonArray = JsonParser.parseString(cleaned).asJsonArray
            jsonArray.forEach { element ->
                val obj = element.asJsonObject
                val actionType = obj.get("action")?.asString?.uppercase() ?: return@forEach
                val action = buildAction(actionType, obj)
                action?.let { actions.add(it) }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return actions
    }

    private fun buildAction(actionType: String, obj: com.google.gson.JsonObject): ActionExecutor.Action? {
        return when (actionType) {
            "CLICK", "TAP" -> {
                val pos = extractPosition(obj)
                ActionExecutor.Action(ActionExecutor.ActionType.TAP, x = (pos.first * 1080).toInt(), y = (pos.second * 1920).toInt(), normalizedX = pos.first, normalizedY = pos.second)
            }
            "DOUBLE_CLICK", "DOUBLE_TAP" -> {
                val pos = extractPosition(obj)
                ActionExecutor.Action(ActionExecutor.ActionType.DOUBLE_TAP, x = (pos.first * 1080).toInt(), y = (pos.second * 1920).toInt(), normalizedX = pos.first, normalizedY = pos.second)
            }
            "LONG_PRESS" -> {
                val pos = extractPosition(obj)
                ActionExecutor.Action(ActionExecutor.ActionType.LONG_PRESS, x = (pos.first * 1080).toInt(), y = (pos.second * 1920).toInt(), normalizedX = pos.first, normalizedY = pos.second, duration = 1000)
            }
            "SWIPE" -> {
                val x1 = obj.get("x1")?.asFloat ?: 0.5f; val y1 = obj.get("y1")?.asFloat ?: 0.5f
                val x2 = obj.get("x2")?.asFloat ?: 0.5f; val y2 = obj.get("y2")?.asFloat ?: 0.3f
                ActionExecutor.Action(ActionExecutor.ActionType.SWIPE, x = (x1 * 1080).toInt(), y = (y1 * 1920).toInt(), normalizedX = x1, normalizedY = y1, params = mapOf("x1_abs" to (x1 * 1080).toInt(), "y1_abs" to (y1 * 1920).toInt(), "x2_abs" to (x2 * 1080).toInt(), "y2_abs" to (y2 * 1920).toInt()))
            }
            "DRAG" -> {
                val x1 = obj.get("x1")?.asFloat ?: 0.5f; val y1 = obj.get("y1")?.asFloat ?: 0.5f
                val x2 = obj.get("x2")?.asFloat ?: 0.5f; val y2 = obj.get("y2")?.asFloat ?: 0.3f
                val duration = obj.get("duration")?.asInt ?: 1000
                ActionExecutor.Action(ActionExecutor.ActionType.DRAG, x = (x1 * 1080).toInt(), y = (y1 * 1920).toInt(), normalizedX = x1, normalizedY = y1, duration = duration, params = mapOf("x1_abs" to (x1 * 1080).toInt(), "y1_abs" to (y1 * 1920).toInt(), "x2_abs" to (x2 * 1080).toInt(), "y2_abs" to (y2 * 1920).toInt()))
            }
            "INPUT", "TYPE" -> ActionExecutor.Action(ActionExecutor.ActionType.TYPE, text = obj.get("text")?.asString ?: "")
            "PRESS", "ENTER", "BACK" -> ActionExecutor.Action(ActionExecutor.ActionType.PRESS, key = obj.get("key")?.asString ?: "enter")
            "LAUNCH", "OPEN" -> ActionExecutor.Action(ActionExecutor.ActionType.LAUNCH, packageName = obj.get("package")?.asString ?: "")
            "WAIT" -> ActionExecutor.Action(ActionExecutor.ActionType.WAIT, duration = obj.get("duration")?.asInt ?: 2)
            "SCROLL" -> ActionExecutor.Action(ActionExecutor.ActionType.SCROLL, direction = obj.get("direction")?.asString?.lowercase() ?: "up")
            "COPY" -> ActionExecutor.Action(ActionExecutor.ActionType.COPY, text = obj.get("text")?.asString)
            "FINISHED", "DONE" -> ActionExecutor.Action(ActionExecutor.ActionType.FINISHED)
            "CALL_USER", "ASK_USER" -> ActionExecutor.Action(ActionExecutor.ActionType.CALL_USER)
            else -> null
        }
    }

    private fun extractPosition(obj: com.google.gson.JsonObject): Pair<Float, Float> {
        return try {
            val xVal = if (obj.has("x")) obj.get("x").asFloat.let { if (it > 1) it / 1080 else it } else 0.5f
            val yVal = if (obj.has("y")) obj.get("y").asFloat.let { if (it > 1) it / 1920 else it } else 0.5f
            Pair(xVal.coerceIn(0f, 1f), yVal.coerceIn(0f, 1f))
        } catch (e: Exception) { Pair(0.5f, 0.5f) }
    }

    private fun cleanResponse(response: String): String {
        var cleaned = response.trim()
        val start = cleaned.indexOf('['); val end = cleaned.lastIndexOf(']')
        return if (start != -1 && end != -1 && end > start) cleaned.substring(start, end + 1) else "[$cleaned]"
    }
}
