package com.zyrex.screenshotsorter.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageNeedTest {
    private val identifies = { name: String -> name.contains("_org.") }

    @Test
    fun notNeededWhenMostNamesIdentifyTheApp() {
        assertFalse(UsageNeed.isNeeded(listOf("a_org.x.jpg", "b_org.y.jpg", "c.png"), "Google", identifies))
    }

    @Test
    fun neededWhenMostNamesDoNot() {
        assertTrue(UsageNeed.isNeeded(listOf("a_org.x.jpg", "b.png", "c.png"), "Xiaomi", identifies))
    }

    @Test
    fun withNoScreenshotsYetItGoesByBrand() {
        for (brand in listOf("Xiaomi", "Redmi", " POCO ", "samsung", "HUAWEI", "Honor")) {
            assertFalse(brand, UsageNeed.isNeeded(emptyList(), brand, identifies))
        }
        for (brand in listOf("Google", "OnePlus", "unknown", "")) {
            assertTrue(brand, UsageNeed.isNeeded(emptyList(), brand, identifies))
        }
    }
}
