package com.zyrex.screenshotsorter.core

data class ScreenTarget(val packageName: String, val className: String)

/** Some brands stop apps in the background unless an extra "autostart" switch is on. */
object AutostartScreens {
    /** Screens to try, best first. They differ between system versions, so any of them may be missing. */
    fun forManufacturer(manufacturer: String): List<ScreenTarget> = when (brand(manufacturer)) {
        Brand.XIAOMI -> listOf(
            ScreenTarget("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        )
        Brand.OPPO -> listOf(
            ScreenTarget("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ScreenTarget("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            ScreenTarget("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        )
        Brand.VIVO -> listOf(
            ScreenTarget("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ScreenTarget("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        )
        Brand.HUAWEI -> listOf(
            ScreenTarget("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ScreenTarget("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        )
        null -> emptyList()
    }

    /** What to do by hand, shown next to the button. Null for brands with no such switch. */
    fun hint(manufacturer: String): String? = when (brand(manufacturer)) {
        Brand.XIAOMI -> "Turn on Autostart for Screenshot Sorter. Then in App info, set Battery saver to No restrictions."
        Brand.OPPO -> "Allow Auto-launch for Screenshot Sorter. Then in App info, allow background activity under Battery usage."
        Brand.VIVO -> "Allow Autostart for Screenshot Sorter. Then in Battery settings, allow high background power use for it."
        Brand.HUAWEI -> "In App launch, switch Screenshot Sorter to Manage manually and allow Auto-launch and Run in background."
        null -> null
    }

    private enum class Brand { XIAOMI, OPPO, VIVO, HUAWEI }

    private fun brand(manufacturer: String): Brand? = when (manufacturer.trim().lowercase()) {
        "xiaomi", "redmi", "poco" -> Brand.XIAOMI
        "oppo", "realme", "oneplus" -> Brand.OPPO
        "vivo", "iqoo" -> Brand.VIVO
        "huawei", "honor" -> Brand.HUAWEI
        else -> null
    }
}
