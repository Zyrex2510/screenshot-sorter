plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}

// The project folder is inside OneDrive, which locks and re-uploads build files. Keep build output out of it.
val buildRoot = File(System.getProperty("user.home"), ".screenshot-sorter-build")
allprojects {
    layout.buildDirectory.set(File(buildRoot, if (path == ":") "root" else name))
}
