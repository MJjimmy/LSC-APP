package com.example.lsc_app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

/** Resource/source contracts only, not device rendering or WebView instrumentation. */
class LightAppearanceTest {
    private val res = File("src/main/res")
    private fun elements(file: File, tag: String): List<Element> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }
    private fun items(style: Element) = (0 until style.childNodes.length)
        .mapNotNull { style.childNodes.item(it) as? Element }
        .associate { it.getAttribute("name") to it.textContent.trim() }

    @Test fun webViewAndSystemBarsNeverFollowDeviceNightMode() {
        val source = File("src/main/java/com/example/lsc_app/MainActivity.kt").readText()
        assertFalse("Never opt web content into algorithmic darkening", source.contains("isAlgorithmicDarkeningAllowed = true"))
        assertTrue(source.contains("isAlgorithmicDarkeningAllowed = false"))
        assertFalse("No device-night branch for native chrome", source.contains("UI_MODE_NIGHT"))
        val bars = source.substringAfter("private fun updateSystemBars(").substringBefore("private fun confirmLeaving")
        assertTrue(bars.contains("val background = getColor(R.color.lsc_surface)"))
        assertTrue(bars.contains("setSystemBarsAppearance(mask, mask)"))
        assertFalse(bars.contains("if (light)"))
        assertTrue(bars.contains("Build.VERSION_CODES.O"))
    }

    @Test fun welcomeAndBothSplashPathsUseTheDaySurface() {
        val layout = File(res, "layout/activity_main.xml").readText()
        assertFalse("Light welcome needs dark readable labels", layout.contains("android:textColor=\"@color/lsc_cream\""))
        assertFalse(layout.contains("android:textColor=\"@color/lsc_muted\""))
        assertTrue(layout.contains("android:background=\"@color/lsc_surface\""))
        val welcome = File(res, "drawable/lsc_welcome_background.xml").readText()
        assertTrue("Welcome is a light surface, not a dark gradient", welcome.contains("@color/lsc_surface"))
        val launch = File(res, "drawable/lsc_launch_background.xml").readText()
        assertTrue(launch.contains("@color/lsc_surface"))
        assertFalse(launch.contains("@color/lsc_navy"))
        val splash = elements(File(res, "values-v31/lsc_native.xml"), "style").single()
        assertEquals("@color/lsc_surface", items(splash)["android:windowSplashScreenBackground"])
        assertEquals("@drawable/lsc_launcher", items(splash)["android:windowSplashScreenAnimatedIcon"])
    }

    @Test fun legacyNavigationBarKeepsReadableIconsWithoutANightModeBranch() {
        val source = File("src/main/java/com/example/lsc_app/MainActivity.kt").readText()
        assertTrue(source.contains("window.navigationBarColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)"))
        assertTrue(source.contains("background else getColor(R.color.lsc_navy)"))
        val base = elements(File(res, "values/lsc_native.xml"), "style").single { it.getAttribute("name") == "Base.LSC.Native" }
        assertEquals("@color/lsc_legacy_navigation_bar", items(base)["android:navigationBarColor"])
        val day = elements(File(res, "values/lsc_native.xml"), "color").single { it.getAttribute("name") == "lsc_legacy_navigation_bar" }
        assertEquals("#102137", day.textContent.trim())
        val modern = elements(File(res, "values-v26/lsc_light.xml"), "color").single()
        assertEquals("lsc_legacy_navigation_bar", modern.getAttribute("name"))
        assertEquals("@color/lsc_surface", modern.textContent.trim())
    }

    @Test fun nativeThemeAndPaletteRemainLightUnderNightConfiguration() {
        val day = File(res, "values/lsc_native.xml")
        val dayColors = elements(day, "color").associate { it.getAttribute("name") to it.textContent.trim() }
        val base = elements(day, "style").single { it.getAttribute("name") == "Base.LSC.Native" }
        assertEquals("Base.LSC.Platform", base.getAttribute("parent"))
        for (file in listOf(day, File(res, "values-v26/lsc_light.xml"), File(res, "values-v29/lsc_light.xml"))) {
            val platform = elements(file, "style").single { it.getAttribute("name") == "Base.LSC.Platform" }
            assertEquals("android:style/Theme.Material.Light.NoActionBar", platform.getAttribute("parent"))
        }
        assertEquals("true", items(base)["android:isLightTheme"])
        assertEquals("true", items(base)["android:windowLightStatusBar"])
        assertEquals("@color/lsc_surface", items(base)["android:statusBarColor"])
        assertEquals("@color/lsc_legacy_navigation_bar", items(base)["android:navigationBarColor"])
        for (directory in res.listFiles().orEmpty().filter { it.name.startsWith("values-night") }) {
            for (file in directory.listFiles().orEmpty().filter { it.extension == "xml" }) {
                for (color in elements(file, "color")) {
                    val name = color.getAttribute("name")
                    if (name in dayColors) assertEquals("Night must not replace $name", dayColors[name], color.textContent.trim())
                }
                for (style in elements(file, "style")) {
                    assertFalse("No dark/DayNight theme override", style.getAttribute("parent").contains("DayNight"))
                    assertFalse("No night native base override", style.getAttribute("name") == "Base.LSC.Native")
                }
            }
        }
        val forceDark = elements(File(res, "values-v29/lsc_light.xml"), "style").single()
        assertEquals("false", items(forceDark)["android:forceDarkAllowed"])
        val bars = elements(File(res, "values-v26/lsc_light.xml"), "style").single()
        assertEquals("true", items(bars)["android:windowLightNavigationBar"])
        for (file in listOf("values/themes.xml", "values-night/themes.xml")) {
            assertFalse(File(res, file).readText().contains("DayNight"))
        }
    }
}
