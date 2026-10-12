package com.zyrex.screenshotsorter.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutostartScreensTest {
    @Test
    fun xiaomiFamilyOpensTheMiuiAutostartScreen() {
        val expected = listOf(
            ScreenTarget("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        )
        for (brand in listOf("Xiaomi", "Redmi", " POCO ")) {
            assertEquals(brand, expected, AutostartScreens.forManufacturer(brand))
            assertNotNull(brand, AutostartScreens.hint(brand))
        }
    }

    @Test
    fun otherKnownBrandsHaveScreensAndHints() {
        for (brand in listOf("OPPO", "realme", "OnePlus", "vivo", "iQOO", "HUAWEI", "HONOR")) {
            assertTrue(brand, AutostartScreens.forManufacturer(brand).isNotEmpty())
            assertNotNull(brand, AutostartScreens.hint(brand))
        }
    }

    @Test
    fun brandsWithoutAnAutostartSwitchHaveNothing() {
        for (brand in listOf("Google", "samsung", "unknown", "")) {
            assertTrue(brand, AutostartScreens.forManufacturer(brand).isEmpty())
            assertNull(brand, AutostartScreens.hint(brand))
        }
    }
}
