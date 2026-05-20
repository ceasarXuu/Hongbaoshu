package com.xuyutech.hongbaoshu

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-device smoke tests use UiAutomator because MIUI devices can leave
 * Compose's ActivityScenario runner stuck before the activity reaches focus.
 */
@RunWith(AndroidJUnit4::class)
class ReaderInstrumentedTest {
    private lateinit var device: UiDevice
    private val packageName = "com.xuyutech.hongbaoshu"

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
        device.wakeUp()
        launchApp()
    }

    @After
    fun tearDown() {
        device.pressHome()
    }

    @Test
    fun coverScreen_isDisplayed() {
        assertNotNull(waitForCover())
    }

    @Test
    fun navigateToReader_fromCover() {
        enterReader()
        assertTrue(device.wait(Until.gone(By.desc("封面")), TIMEOUT_MS))
    }

    @Test
    fun backGesture_navigatesToCover() {
        enterReader()
        dismissGuideIfPresent()
        device.swipe(
            (device.displayWidth * 0.2f).toInt(),
            device.displayHeight / 2,
            (device.displayWidth * 0.9f).toInt(),
            device.displayHeight / 2,
            24
        )
        assertNotNull(waitForCover())
    }

    @Test
    fun openMenu_verifyDisplay() {
        enterReader()
        dismissGuideIfPresent()
        openReaderMenu()
        assertNotNull(device.wait(Until.findObject(By.text("目录")), TIMEOUT_MS))
        assertNotNull(device.wait(Until.findObject(By.text("字体")), TIMEOUT_MS))
    }

    @Test
    fun toggleNightMode_controlIsInteractive() {
        enterReader()
        dismissGuideIfPresent()
        openReaderMenu()
        val nightButton = device.wait(Until.findObject(By.text("夜间")), TIMEOUT_MS)
            ?: device.wait(Until.findObject(By.text("日间")), TIMEOUT_MS)
        assertNotNull(nightButton)

        device.click(
            (device.displayWidth * 0.9f).toInt(),
            (device.displayHeight * 0.94f).toInt()
        )
        device.waitForIdle()
        assertTrue(device.wait(Until.hasObject(By.pkg(packageName)), TIMEOUT_MS))
        assertTrue(device.wait(Until.gone(By.desc("封面")), SHORT_TIMEOUT_MS))
    }

    private fun launchApp() {
        device.pressHome()
        device.executeShellCommand("am start -W -n $packageName/.MainActivity -f 0x10008000")
        assertTrue(device.wait(Until.hasObject(By.pkg(packageName)), TIMEOUT_MS))
    }

    private fun waitForCover() = device.wait(Until.findObject(By.desc("封面")), TIMEOUT_MS)

    private fun enterReader() {
        val cover = waitForCover()
        assertNotNull(cover)
        cover.click()
        assertTrue(device.wait(Until.gone(By.desc("封面")), TIMEOUT_MS))
    }

    private fun dismissGuideIfPresent() {
        val button = device.wait(Until.findObject(By.text("我知道了")), SHORT_TIMEOUT_MS)
        button?.click()
        device.waitForIdle()
    }

    private fun openReaderMenu() {
        val x = device.displayWidth / 2
        val y = (device.displayHeight * 0.15f).toInt()
        device.click(x, y)
        Thread.sleep(120)
        device.click(x, y)
        device.waitForIdle()
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val SHORT_TIMEOUT_MS = 1_000L
    }
}
