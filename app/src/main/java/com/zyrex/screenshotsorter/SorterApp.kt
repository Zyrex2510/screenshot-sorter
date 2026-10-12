package com.zyrex.screenshotsorter

import android.app.Application

class SorterApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}
