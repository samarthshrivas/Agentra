package com.agentra.app

import android.content.Intent
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.agentra.app.ui.SettingsActivity
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SettingsActivityTest {

    private lateinit var device: UiDevice
    private val pkg = "com.agentra.app"

    @Before
    fun setUp() {
        // Skip on Android 15 preview (API 37) — Espresso InputManagerEventInjectionStrategy incompatibility
        Assume.assumeTrue(Build.VERSION.SDK_INT < 37)
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        // Launch SettingsActivity via explicit Intent
        val intent = Intent(InstrumentationRegistry.getInstrumentation().targetContext, SettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        InstrumentationRegistry.getInstrumentation().targetContext.startActivity(intent)
        device.waitForIdle(2000)
        device.wait(Until.hasObject(By.pkg(pkg).depth(0)), TimeUnit.SECONDS.toMillis(5))
        device.waitForIdle(1000)
    }

    @Test
    fun activity_launches_successfully() {
        val btn = device.wait(Until.findObject(By.res("$pkg:id/btnSaveSettings")), 5000)
        assertNotNull("btnSaveSettings should exist", btn)
    }

    @Test
    fun toolbar_is_displayed() {
        val tb = device.wait(Until.findObject(By.res("$pkg:id/toolbar")), 5000)
        assertNotNull("toolbar should exist", tb)
    }

    @Test
    fun settings_title_is_shown() {
        val title = device.wait(Until.findObject(By.text("Settings")), 5000)
        assertNotNull("Settings title should be visible", title)
    }

    @Test
    fun model_selection_is_visible() {
        val model = device.wait(Until.findObject(By.res("$pkg:id/actvModel")), 5000)
        assertNotNull("actvModel should exist", model)
    }

    @Test
    fun api_endpoint_field_is_visible() {
        val ep = device.wait(Until.findObject(By.res("$pkg:id/etApiEndpoint")), 5000)
        assertNotNull("etApiEndpoint should exist", ep)
    }

    @Test
    fun api_key_field_is_visible() {
        val key = device.wait(Until.findObject(By.res("$pkg:id/etApiKey")), 5000)
        assertNotNull("etApiKey should exist", key)
    }

    @Test
    fun temperature_slider_is_visible() {
        val slider = device.wait(Until.findObject(By.res("$pkg:id/sliderTemperature")), 5000)
        assertNotNull("sliderTemperature should exist", slider)
    }

    @Test
    fun max_tokens_slider_is_visible() {
        val slider = device.wait(Until.findObject(By.res("$pkg:id/sliderMaxTokens")), 5000)
        assertNotNull("sliderMaxTokens should exist", slider)
    }

    @Test
    fun save_button_is_clickable() {
        val btn = device.wait(Until.findObject(By.res("$pkg:id/btnSaveSettings")), 5000)
        assertNotNull("btnSaveSettings should exist", btn)
        assertTrue("btnSaveSettings should be clickable", btn!!.isClickable)
        btn.click()
    }

    @Test
    fun wake_word_switch_is_displayed() {
        val sw = device.wait(Until.findObject(By.res("$pkg:id/switchWakeWord")), 5000)
        assertNotNull("switchWakeWord should exist", sw)
    }

    @Test
    fun wake_word_phrase_input_is_displayed() {
        val input = device.wait(Until.findObject(By.res("$pkg:id/etWakeWordPhrase")), 5000)
        assertNotNull("etWakeWordPhrase should exist", input)
    }

    @Test
    fun wake_word_section_is_displayed() {
        val section = device.wait(Until.findObject(By.res("$pkg:id/layoutWakeWord")), 5000)
        assertNotNull("layoutWakeWord should exist", section)
    }

    @Test
    fun default_assistant_section_is_displayed() {
        val section = device.wait(Until.findObject(By.res("$pkg:id/layoutDefaultAssistant")), 5000)
        assertNotNull("layoutDefaultAssistant should exist", section)
    }

    @Test
    fun microphone_permission_row_is_displayed() {
        val row = device.wait(Until.findObject(By.res("$pkg:id/layoutMicrophone")), 5000)
        assertNotNull("layoutMicrophone should exist", row)
    }

    @Test
    fun accessibility_permission_row_is_displayed() {
        val row = device.wait(Until.findObject(By.res("$pkg:id/layoutAccessibility")), 5000)
        assertNotNull("layoutAccessibility should exist", row)
    }

    @Test
    fun floating_button_toggle_is_displayed() {
        val fb = device.wait(Until.findObject(By.res("$pkg:id/layoutFloatingButton")), 5000)
        assertNotNull("layoutFloatingButton should exist", fb)
    }

    @Test
    fun test_tools_nav_is_displayed() {
        val tt = device.wait(Until.findObject(By.res("$pkg:id/layoutTestTools")), 5000)
        assertNotNull("layoutTestTools should exist", tt)
    }

    @Test
    fun ai_configuration_card_title_is_displayed() {
        val title = device.wait(Until.findObject(By.text("AI Configuration")), 5000)
        assertNotNull("AI Configuration title should be visible", title)
    }
}
