package com.zyrex.screenshotsorter.platform

import android.app.Application
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Android 11+ answers the All files access question by looking up the storage volume. Right after a reboot the
 * lookup can fail, and the boot receiver and the catch-up job ask at exactly that moment, so it must say "no".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StorageVolumeMissingTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()

    // No shared-storage dir is added here, so the simulated phone has no volume to look up.
    @Test
    fun hasStorageSaysNoInsteadOfThrowingWhenTheVolumeCannotBeFound() {
        assertFalse(SetupState.hasStorage(app))
    }
}
