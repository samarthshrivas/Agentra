package com.agentra.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.agentra.app.ui.DebugActivity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DebugActivityTest {

    private lateinit var device: UiDevice
    private val pkg = "com.agenttra.app"

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        ActivityScenario.launch(DebugActivity::class.java)
        device.wait(Until.hasObject(By.pkg(pkg).depth(0)), TimeUnit.SECONDS.toMillis(3))
    }

    @Test
    fun activity_launches_successfully() {
        assertNotNull("btnClear should exist", device.findObject(By.res(pkg, "btnClear")))
        assertNotNull("btnCopy should exist", device.findObject(By.res(pkg, "btnCopy")))
    }

    @Test
    fun toolbar_is_displayed() {
        assertNotNull("toolbar should exist", device.findObject(By.res(pkg, "toolbar")))
    }

    @Test
    fun ai_response_section_is_displayed() {
        assertNotNull("tvAIResponse should exist", device.findObject(By.res(pkg, "tvAIResponse")))
    }

    @Test
    fun errors_section_is_displayed() {
        assertNotNull("tvErrors should exist", device.findObject(By.res(pkg, "tvErrors")))
    }

    @Test
    fun raw_response_section_is_displayed() {
        assertNotNull("tvRawResponse should exist", device.findObject(By.res(pkg, "tvRawResponse")))
    }

    @Test
    fun clear_button_is_clickable() {
        val btn = device.findObject(By.res(pkg, "btnClear"))
        assertNotNull("btnClear should exist", btn)
        assertTrue("btnClear should be clickable", btn!!.isClickable)
        btn.click()
    }

    @Test
    fun copy_button_is_clickable() {
        val btn = device.findObject(By.res(pkg, "btnCopy"))
        assertNotNull("btnCopy should exist", btn)
        assertTrue("btnCopy should be clickable", btn!!.isClickable)
        btn.click()
    }

    @Test
    fun default_state_shows_placeholder_text() {
        val noResponse = device.findObject(By.text("No response yet"))
        assertNotNull("Should show 'No response yet' placeholder", noResponse)

        val noErrors = device.findObject(By.text("No errors"))
        assertNotNull("Should show 'No errors' placeholder", noErrors)

        val noRaw = device.findObject(By.text("No raw response"))
        assertNotNull("Should show 'No raw response' placeholder", noRaw)
    }

}
