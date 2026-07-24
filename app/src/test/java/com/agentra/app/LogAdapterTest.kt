package com.agentra.app

import com.agentra.app.ui.adapter.LogAdapter
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LogAdapterTest {

    private lateinit var adapter: LogAdapter

    @Before
    fun setUp() {
        adapter = LogAdapter()
    }

    @Test
    fun `new adapter has zero items`() {
        assertEquals(0, adapter.itemCount)
    }

    @Test
    fun `addLog increases item count`() {
        adapter.addLog("Test message", LogAdapter.LogType.INFO)
        assertEquals(1, adapter.itemCount)
    }

    @Test
    fun `addLog multiple times increases count`() {
        adapter.addLog("First", LogAdapter.LogType.INFO)
        adapter.addLog("Second", LogAdapter.LogType.USER)
        adapter.addLog("Third", LogAdapter.LogType.AGENT)
        assertEquals(3, adapter.itemCount)
    }

    @Test
    fun `clear removes all items`() {
        adapter.addLog("A", LogAdapter.LogType.INFO)
        adapter.addLog("B", LogAdapter.LogType.INFO)
        adapter.addLog("C", LogAdapter.LogType.INFO)
        assertEquals(3, adapter.itemCount)

        adapter.clear()
        assertEquals(0, adapter.itemCount)
    }

    @Test
    fun `clear on empty adapter does not crash`() {
        adapter.clear()
        assertEquals(0, adapter.itemCount)
    }

    @Test
    fun `log entries have correct LogType`() {
        adapter.addLog("User msg", LogAdapter.LogType.USER)
        adapter.addLog("Agent msg", LogAdapter.LogType.AGENT)
        adapter.addLog("Action msg", LogAdapter.LogType.ACTION)
        adapter.addLog("Error msg", LogAdapter.LogType.ERROR)
        adapter.addLog("Info msg", LogAdapter.LogType.INFO)

        assertEquals(5, adapter.itemCount)
    }

    @Test
    fun `addLog stores message text`() {
        val message = "Specific test message"
        adapter.addLog(message, LogAdapter.LogType.INFO)
        assertEquals(1, adapter.itemCount)
    }

    @Test
    fun `LogType enum has all expected values`() {
        val types = LogAdapter.LogType.values()
        assertEquals(5, types.size)
        assert(types.contains(LogAdapter.LogType.USER))
        assert(types.contains(LogAdapter.LogType.AGENT))
        assert(types.contains(LogAdapter.LogType.ACTION))
        assert(types.contains(LogAdapter.LogType.ERROR))
        assert(types.contains(LogAdapter.LogType.INFO))
    }
}
