package com.zyrex.screenshotsorter

import android.app.Application
import com.zyrex.screenshotsorter.core.AppIdentifier
import com.zyrex.screenshotsorter.core.DirectFileMover
import com.zyrex.screenshotsorter.core.FakeHistory
import com.zyrex.screenshotsorter.core.FakeInstalledApps
import com.zyrex.screenshotsorter.core.HandledKeys
import com.zyrex.screenshotsorter.core.MemoryStore
import com.zyrex.screenshotsorter.core.ScreenshotFinder
import com.zyrex.screenshotsorter.core.Sorter
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * A storage root whose folders cannot be listed. Listing is the one step of a sorting pass that is not about a single
 * screenshot, so this is how a pass fails as a whole. [listings] counts how often the app tried.
 */
class UnlistableRoot : File("unlistable-storage") {
    val listings = AtomicInteger()

    override fun listFiles(): Array<File>? {
        listings.incrementAndGet()
        throw IllegalStateException("Storage cannot be listed")
    }
}

/** Makes every sorting pass the app starts fail as a whole. Needs the app's graph, so call it once the test has set up whatever else it needs. */
fun makeEverySortingPassFail(app: Application): UnlistableRoot {
    val graph = (app as SorterApp).graph
    val root = UnlistableRoot()
    val sorter = Sorter(
        ScreenshotFinder(root),
        AppIdentifier(FakeInstalledApps(emptyMap()), FakeHistory()),
        DirectFileMover(),
        HandledKeys(MemoryStore()),
        graph.log,
    )
    ReflectionHelpers.setField(graph, "sorter", sorter)
    return root
}

/** Makes the set-up check fail, which lists the newest screenshots to learn how the phone names them. */
fun makeReadingTheSetupFail(app: Application): UnlistableRoot {
    val graph = (app as SorterApp).graph
    val root = UnlistableRoot()
    ReflectionHelpers.setField(graph, "finder", ScreenshotFinder(root))
    return root
}
