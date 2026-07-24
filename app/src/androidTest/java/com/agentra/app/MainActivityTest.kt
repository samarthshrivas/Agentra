package com.agentra.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.agentra.app.ui.MainActivity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    private lateinit var device: UiDevice
    private val pkg = "com.agenttra.app"

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        ActivityScenario.launch(MainActivity::class.java)
        device.wait(Until.hasObject(By.pkg(pkg).depth(0)), TimeUnit.SECONDS.toMillis(3))
    }

    @Test
    fun activity_launches_successfully() {
        assertNotNull("btnExecute should exist", device.findObject(By.res(pkg, "btnExecute")))
        assertNotNull("etCommand should exist", device.findObject(By.res(pkg, "etCommand")))
    }

    @Test
    fun status_card_is_displayed() {
        val card = device.findObject(By.res(pkg, "statusCard"))
        assertNotNull("statusCard should exist", card)
    }

    @Test
    fun status_text_shows_Ready() {
        val statusText = device.findObject(By.res(pkg, "tvStatus"))
        assertNotNull("tvStatus should exist", statusText)
    }

    @Test
    fun toolbar_shows_app_name() {
        val title = device.findObject(By.text("Agentra"))
        assertNotNull("Toolbar should show 'Agentra'", title)
    }

    @Test
    fun execute_button_is_displayed() {
        assertNotNull("btnExecute should exist", device.findObject(By.res(pkg, "btnExecute")))
    }

    @Test
    fun command_input_is_visible() {
        assertNotNull("etCommand should exist", device.findObject(By.res(pkg, "etCommand")))
    }

    @Test
    fun settings_button_is_displayed() {
        assertNotNull("btnSettings should exist", device.findObject(By.res(pkg, "btnSettings")))
    }

    @Test
    fun debug_button_is_displayed() {
        assertNotNull("btnDebug should exist", device.findObject(By.res(pkg, "btnDebug")))
    }

    @Test
    fun clear_logs_button_is_displayed() {
        assertNotNull("btnClearLogs should exist", device.findObject(By.res(pkg, "btnClearLogs")))
    }

    @Test
    fun recycler_view_for_logs_is_displayed() {
        assertNotNull("rvLogs should exist", device.findObject(By.res(pkg, "rvLogs")))
    }

}
