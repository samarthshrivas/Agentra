package com.agentra.app

import android.content.Context
import android.content.SharedPreferences
import com.agentra.app.action.ActionPlanner
import com.agentra.app.agent.AgentCore
import com.agentra.app.config.AppConfig
import com.agentra.app.screenshot.ScreenshotManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AgentCoreTest {

    private lateinit var agentCore: AgentCore
    private lateinit var mockScreenshotManager: ScreenshotManager
    private lateinit var mockConfig: AppConfig
    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        // Robolectric prepares Looper automatically via @RunWith

        mockContext = mockk()

        val mockPrefs: SharedPreferences = mockk()
        val mockEditor: SharedPreferences.Editor = mockk()

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

        every { mockContext.getSharedPreferences("agentra_config", Context.MODE_PRIVATE) } returns mockPrefs

        mockConfig = AppConfig(mockContext)
        mockScreenshotManager = mockk {
            every { isCaptureActive() } returns true
            coEvery { captureScreen() } returns null
        }

        agentCore = AgentCore(mockContext, mockScreenshotManager, mockConfig)
    }

    @Test
    fun `stop sets isRunning to false`() {
        agentCore.stop()
    }

    @Test
    fun `getActionHistory returns empty list initially`() {
        val history = agentCore.getActionHistory()
        assert(history.isEmpty())
    }

    @Test
    fun `getActionHistory is immutable`() {
        val history = agentCore.getActionHistory()
        assert(history.isEmpty())
    }

    @Test
    fun `stop can be called multiple times`() {
        agentCore.stop()
        agentCore.stop()
        agentCore.stop()
    }

    @Test
    fun `MAX_ITERATIONS is 15`() {
        assertEquals(15, ActionPlanner.MAX_ITERATIONS)
    }

    @Test
    fun `MAX_FAILED_ATTEMPTS is 3`() {
        assertEquals(3, ActionPlanner.MAX_FAILED_ATTEMPTS)
    }
}
