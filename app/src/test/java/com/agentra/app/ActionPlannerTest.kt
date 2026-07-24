package com.agentra.app

import com.agentra.app.action.ActionExecutor
import com.agentra.app.action.ActionPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ActionPlannerTest {

    private lateinit var planner: ActionPlanner

    @Before
    fun setUp() {
        planner = ActionPlanner()
    }

    @Test
    fun `parseActions returns empty list on empty input`() {
        val result = planner.parseActions("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseActions returns empty list on malformed JSON`() {
        val result = planner.parseActions("not json at all")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseActions handles CLICK action with normalized coordinates`() {
        val json = """[{"action": "CLICK", "x": 0.5, "y": 0.3}]"""
        val result = planner.parseActions(json)
        assertEquals(1, result.size)
        assertEquals(ActionExecutor.ActionType.TAP, result[0].type)
        assertEquals(0.5f, result[0].normalizedX!!, 0.01f)
        assertEquals(0.3f, result[0].normalizedY!!, 0.01f)
    }

    @Test
    fun `parseActions handles TAP alias`() {
        val json = """[{"action": "TAP", "x": 0.8, "y": 0.9}]"""
        val result = planner.parseActions(json)
        assertEquals(1, result.size)
        assertEquals(ActionExecutor.ActionType.TAP, result[0].type)
    }

    @Test
    fun `parseActions handles DOUBLE_TAP`() {
        val json = """[{"action": "DOUBLE_TAP", "x": 0.5, "y": 0.5}]"""
        val result = planner.parseActions(json)
        assertEquals(1, result.size)
        assertEquals(ActionExecutor.ActionType.DOUBLE_TAP, result[0].type)
    }

    @Test
    fun `parseActions handles DOUBLE_CLICK as double tap`() {
        val json = """[{"action": "DOUBLE_CLICK", "x": 0.5, "y": 0.5}]"""
        val result = planner.parseActions(json)
        assertEquals(1, result.size)
        assertEquals(ActionExecutor.ActionType.DOUBLE_TAP, result[0].type)
    }

    @Test
    fun `parseActions handles LONG_PRESS`() {
        val json = """[{"action": "LONG_PRESS", "x": 0.5, "y": 0.5}]"""
        val result = planner.parseActions(json)
        assertEquals(1, result.size)
        assertEquals(ActionExecutor.ActionType.LONG_PRESS, result[0].type)
    }

    @Test
    fun `parseActions handles SWIPE with all coordinates`() {
        val json = """[{"action": "SWIPE", "x1": 0.5, "y1": 0.7, "x2": 0.5, "y2": 0.3}]"""
        val result = planner.parseActions(json)
        assertEquals(1, result.size)
        assertEquals(ActionExecutor.ActionType.SWIPE, result[0].type)
    }

    @Test
    fun `parseActions handles TYPE action`() {
        val json = """[{"action": "TYPE", "text": "Hello World"}]"""
        val result = planner.parseActions(json)
        assertEquals(1, result.size)
        assertEquals(ActionExecutor.ActionType.TYPE, result[0].type)
        assertEquals("Hello World", result[0].text)
    }

    @Test
    fun `parseActions handles INPUT alias for TYPE`() {
        val json = """[{"action": "INPUT", "text": "test"}]"""
        val result = planner.parseActions(json)
        assertEquals(1, result.size)
        assertEquals(ActionExecutor.ActionType.TYPE, result[0].type)
        assertEquals("test", result[0].text)
    }

    @Test
    fun `parseActions handles PRESS action`() {
        val json = """[{"action": "PRESS", "key": "back"}]"""
        val result = planner.parseActions(json)
        assertEquals(1, result.size)
        assertEquals(ActionExecutor.ActionType.PRESS, result[0].type)
        assertEquals("back", result[0].key)
    }

    @Test
    fun `parseActions handles LAUNCH action`() {
        val json = """[{"action": "LAUNCH", "package": "com.whatsapp"}]"""
        val result = planner.parseActions(json)
        assertEquals(1, result.size)
        assertEquals(ActionExecutor.ActionType.LAUNCH, result[0].type)
        assertEquals("com.whatsapp", result[0].packageName)
    }

    @Test
    fun `parseActions handles OPEN alias for LAUNCH`() {
        val json = """[{"action": "OPEN", "package": "com.android.chrome"}]"""
        val result = planner.parseActions(json)
        assertEquals(1, result.size)
        assertEquals(ActionExecutor.ActionType.LAUNCH, result[0].type)
    }

    @Test
    fun `parseActions handles WAIT with duration`() {
        val json = """[{"action": "WAIT", "duration": 3}]"""
        val result = planner.parseActions(json)
        assertEquals(1, result.size)
        assertEquals(ActionExecutor.ActionType.WAIT, result[0].type)
        assertEquals(3, result[0].duration)
    }

    @Test
    fun `parseActions uses default wait duration of 2 when not specified`() {
        val json = """[{"action": "WAIT"}]"""
        val result = planner.parseActions(json)
        assertEquals(2, result[0].duration)
    }

    @Test
    fun `parseActions handles SCROLL up`() {
        val json = """[{"action": "SCROLL", "direction": "up"}]"""
        val result = planner.parseActions(json)
        assertEquals(1, result.size)
        assertEquals(ActionExecutor.ActionType.SCROLL, result[0].type)
        assertEquals("up", result[0].direction)
    }

    @Test
    fun `parseActions defaults scroll direction to up`() {
        val json = """[{"action": "SCROLL"}]"""
        val result = planner.parseActions(json)
        assertEquals("up", result[0].direction)
    }

    @Test
    fun `parseActions handles COPY action`() {
        val json = """[{"action": "COPY", "text": "copied text"}]"""
        val result = planner.parseActions(json)
        assertEquals(1, result.size)
        assertEquals(ActionExecutor.ActionType.COPY, result[0].type)
        assertEquals("copied text", result[0].text)
    }

    @Test
    fun `parseActions handles FINISHED action`() {
        val json = """[{"action": "FINISHED"}]"""
        val result = planner.parseActions(json)
        assertEquals(1, result.size)
        assertEquals(ActionExecutor.ActionType.FINISHED, result[0].type)
    }

    @Test
    fun `parseActions handles DONE alias for FINISHED`() {
        val json = """[{"action": "DONE"}]"""
        val result = planner.parseActions(json)
        assertEquals(1, result.size)
        assertEquals(ActionExecutor.ActionType.FINISHED, result[0].type)
    }

    @Test
    fun `parseActions handles CALL_USER action`() {
        val json = """[{"action": "CALL_USER", "reason": "Need help"}]"""
        val result = planner.parseActions(json)
        assertEquals(1, result.size)
        assertEquals(ActionExecutor.ActionType.CALL_USER, result[0].type)
    }

    @Test
    fun `parseActions handles ASK_USER alias`() {
        val json = """[{"action": "ASK_USER", "reason": "Confirmation needed"}]"""
        val result = planner.parseActions(json)
        assertEquals(1, result.size)
        assertEquals(ActionExecutor.ActionType.CALL_USER, result[0].type)
    }

    @Test
    fun `parseActions handles multiple actions in sequence`() {
        val json = """[
            {"action": "CLICK", "x": 0.5, "y": 0.5},
            {"action": "TYPE", "text": "hello"},
            {"action": "PRESS", "key": "enter"},
            {"action": "FINISHED"}
        ]"""
        val result = planner.parseActions(json)
        assertEquals(4, result.size)
        assertEquals(ActionExecutor.ActionType.TAP, result[0].type)
        assertEquals(ActionExecutor.ActionType.TYPE, result[1].type)
        assertEquals(ActionExecutor.ActionType.PRESS, result[2].type)
        assertEquals(ActionExecutor.ActionType.FINISHED, result[3].type)
    }

    @Test
    fun `parseActions handles THOUGHT actions by extracting position`() {
        val json = """[{"action": "THOUGHT", "thought": "I should tap"}, {"action": "CLICK", "x": 0.5, "y": 0.5}]"""
        val result = planner.parseActions(json)
        assertEquals(1, result.size) // THOUGHT should be filtered out
        assertEquals(ActionExecutor.ActionType.TAP, result[0].type)
    }

    @Test
    fun `parseActions strips markdown code fences`() {
        val json = """```json
[{"action": "CLICK", "x": 0.5, "y": 0.3}]
```"""
        val result = planner.parseActions(json)
        assertEquals(1, result.size)
        assertEquals(ActionExecutor.ActionType.TAP, result[0].type)
    }

    @Test
    fun `parseActions handles extra text around JSON array`() {
        val json = """Here are the actions: [{"action": "CLICK", "x": 0.5, "y": 0.5}] End."""
        val result = planner.parseActions(json)
        assertEquals(1, result.size)
        assertEquals(ActionExecutor.ActionType.TAP, result[0].type)
    }

    @Test
    fun `extractPosition normalizes absolute coordinates`() {
        val json = """[{"action": "CLICK", "x": 540, "y": 960}]"""
        val result = planner.parseActions(json)
        assertEquals(0.5f, result[0].normalizedX!!, 0.01f)
        assertEquals(0.5f, result[0].normalizedY!!, 0.01f)
    }

    @Test
    fun `extractPosition coerces out-of-range values to 0-1`() {
        val json = """[{"action": "CLICK", "x": -1, "y": 2}]"""
        val result = planner.parseActions(json)
        assertTrue(result[0].normalizedX!! >= 0f && result[0].normalizedX!! <= 1f)
        assertTrue(result[0].normalizedY!! >= 0f && result[0].normalizedY!! <= 1f)
    }

    @Test
    fun `extractPosition defaults to center when missing`() {
        val json = """[{"action": "CLICK"}]"""
        val result = planner.parseActions(json)
        assertEquals(0.5f, result[0].normalizedX!!, 0.01f)
        assertEquals(0.5f, result[0].normalizedY!!, 0.01f)
    }

    @Test
    fun `parseActions returns empty list for unknown action type`() {
        val json = """[{"action": "FLY"}]"""
        val result = planner.parseActions(json)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `MAX_ITERATIONS and MAX_FAILED_ATTEMPTS have expected values`() {
        assertEquals(15, ActionPlanner.MAX_ITERATIONS)
        assertEquals(3, ActionPlanner.MAX_FAILED_ATTEMPTS)
    }
}
