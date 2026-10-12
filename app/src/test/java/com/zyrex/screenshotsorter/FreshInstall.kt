package com.zyrex.screenshotsorter

import android.Manifest
import android.app.AppOpsManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowEnvironment

// The app-op behind All files access; the SDK only exposes its name to system apps.
private const val MANAGE_EXTERNAL_STORAGE_OP = "android:manage_external_storage"

/**
 * Makes the simulated phone look like one the app was just installed on.
 *
 * Robolectric grants every manifest permission and allows every app-op by default, and its phone has no
 * shared storage; a fresh install has no permissions and no special access. Call this first in a test's
 * `@Before` for any test that reads `SetupState`, then grant only what the test needs.
 */
fun startAsAFreshInstall(app: Application) {
    shadowOf(app).denyPermissions(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.MANAGE_EXTERNAL_STORAGE,
    )
    val appOps = shadowOf(app.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager)
    appOps.setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName, AppOpsManager.MODE_DEFAULT)
    if (Build.VERSION.SDK_INT >= 30) {
        appOps.setMode(MANAGE_EXTERNAL_STORAGE_OP, app.applicationInfo.uid, app.packageName, AppOpsManager.MODE_DEFAULT)
        // The simulated phone has no shared storage unless one is added, and the system's All files access check reads it.
        ShadowEnvironment.addExternalDir("shared-storage")
    }
}
