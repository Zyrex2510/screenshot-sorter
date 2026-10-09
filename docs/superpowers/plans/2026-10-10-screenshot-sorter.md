# Screenshot Sorter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An Android app (APK) that moves each new screenshot into a folder named after the app it was taken in.

**Architecture:** Pure Kotlin logic in `core/` (filename parsing, app identification, file moving, sorting pass) with no Android imports, so it is unit-tested on the JVM. Thin Android adapters in `platform/` and `watch/` (installed apps, usage history, gallery index, foreground service, boot receiver, periodic job). One Compose screen in `ui/`.

**Tech Stack:** Kotlin 2.4.21 (AGP built-in Kotlin), Android Gradle Plugin 9.4.1, Gradle 9.8.1, JDK 17, Jetpack Compose (BOM 2026.09.00, Material 3), JUnit 4, Robolectric 4.17, Roborazzi 1.76.0.

**Spec:** `docs/superpowers/specs/2026-10-09-screenshot-sorter-design.md`

## Global Constraints

- App ID and namespace: `com.zyrex.screenshotsorter`. App name: `Screenshot Sorter`.
- `minSdk = 23` (Android 6.0), `targetSdk = 36`, `compileSdk = 37`.
- UI text is English only, and lives in `res/values/strings.xml`.
- A screenshot is never overwritten, deleted or copied. Moves are renames inside the same storage.
- A screenshot is moved no sooner than 10 seconds after it last changed.
- Only image files (`jpg`, `jpeg`, `png`, `webp`) directly inside a screenshot folder are processed. A screenshot folder is `Screenshots` or `Screenshot` directly inside `DCIM` or `Pictures`, any letter case.
- A sorted file goes into a subfolder of the screenshot folder it was found in.
- `core/` must not import `android.*` or `androidx.*`.
- No emulator (the user's internet is limited). Verification is JVM unit tests, Robolectric on Android 6.0 (API 23) and Android 15 (API 35), Android lint, and the friend's Redmi.
- All Gradle commands run from the project root in Git Bash as:
  `JAVA_HOME="$HOME/.android-toolchain/jdk-17" ./gradlew <tasks>`
- Build output goes to `~/.screenshot-sorter-build/` (outside OneDrive).
- Every commit message ends with the trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

Inputs the spec implies but does not spell out, most likely to bite first. Each has a test in the task named.

1. A screenshot that is still being written, or is open in the system's edit/share popup: it must stay put until it is 10 seconds old. (Task 5, `leavesAFreshScreenshotForTheNextPass`)
2. A filename ending that is not an app (`_1`, `display-1`, a hash, `~2`): it must not create a junk folder. (Task 2, `tails`; Task 3, `tailThatIsNotAnAppFallsBackToHistory`)
3. An app name that is not a legal folder name (slashes, colons, leading dots, empty, very long, emoji at the cut point). (Task 2, `FolderNamesTest`)
4. A file with the same name already in the target folder, or a plain file sitting where the folder should be. (Task 4, `addsANumberInsteadOfOverwriting`, `aFileInTheWayIsAFailure`; Task 5, `neverOverwritesAnExistingFile`)
5. Storage permission missing or revoked mid-run, or the screenshot deleted between listing and moving: no crash, nothing lost. (Task 4, `aMissingSourceIsAFailure`; Task 5, `missingStorageIsNotAnError`; Task 6, `PlatformSmokeTest`)

---

### Task 1: Toolchain and an empty app that builds

**Files:**
- Create: `.gitignore`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `local.properties` (not committed)
- Create: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`
- Create: `app/build.gradle.kts`, `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`, `app/src/main/res/values/themes.xml`, `app/src/main/res/values-night/themes.xml`
- Create: `app/src/main/java/com/zyrex/screenshotsorter/ui/MainActivity.kt`
- Test: `app/src/test/java/com/zyrex/screenshotsorter/core/BuildSanityTest.kt`

**Interfaces:**
- Consumes: `~/.android-toolchain/downloads/jdk17.zip` and `cmdline-tools.zip` (already downloaded).
- Produces: a project where `./gradlew :app:assembleDebug :app:testDebugUnitTest` succeeds. Later tasks add files under `app/src/main/java/com/zyrex/screenshotsorter/` and `app/src/test/java/com/zyrex/screenshotsorter/`.

- [ ] **Step 1: Unpack the JDK**

```bash
cd "$HOME/.android-toolchain"
unzip -q downloads/jdk17.zip -d .
mv jdk-17* jdk-17
./jdk-17/bin/java -version
```

Expected: prints `openjdk version "17...`.

- [ ] **Step 2: Unpack the Android command-line tools and accept the SDK licenses**

```bash
SDK="$LOCALAPPDATA/Android/Sdk"
mkdir -p "$SDK/cmdline-tools"
unzip -q "$HOME/.android-toolchain/downloads/cmdline-tools.zip" -d "$SDK/cmdline-tools"
mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
export JAVA_HOME="$HOME/.android-toolchain/jdk-17"
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager.bat" --licenses > /dev/null
ls "$SDK/licenses"
```

Expected: `android-sdk-license` is listed. The user approved accepting this license. The Android Gradle plugin downloads the platform and build tools itself on the first build.

- [ ] **Step 3: Add the Gradle wrapper**

```bash
cd "/c/Users/i7/OneDrive/Desktop/screenshot-sorter"
mkdir -p gradle/wrapper
BASE=https://raw.githubusercontent.com/gradle/gradle/v9.8.1
curl -sS -L --fail -o gradle/wrapper/gradle-wrapper.jar "$BASE/gradle/wrapper/gradle-wrapper.jar"
curl -sS -L --fail -o gradlew "$BASE/gradlew"
curl -sS -L --fail -o gradlew.bat "$BASE/gradlew.bat"
chmod +x gradlew
echo "$(curl -sS -L https://services.gradle.org/distributions/gradle-9.8.1-wrapper.jar.sha256)  gradle/wrapper/gradle-wrapper.jar" | sha256sum -c -
```

Expected: `gradle/wrapper/gradle-wrapper.jar: OK`. If the checksum does not match, stop and do not run the wrapper.

`gradle/wrapper/gradle-wrapper.properties`:

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-9.8.1-bin.zip
networkTimeout=60000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 4: Write the build files**

`.gitignore`:

```gitignore
.gradle/
.kotlin/
build/
local.properties
keystore.properties
*.jks
dist/
```

`local.properties`:

```properties
sdk.dir=C\:\\Users\\i7\\AppData\\Local\\Android\\Sdk
```

`gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
kotlin.code.style=official
```

`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "screenshot-sorter"
include(":app")
```

`build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}

// The project folder is inside OneDrive, which locks and re-uploads build files. Keep build output out of it.
val buildRoot = File(System.getProperty("user.home"), ".screenshot-sorter-build")
allprojects {
    layout.buildDirectory.set(File(buildRoot, if (path == ":") "root" else name))
}
```

`app/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.zyrex.screenshotsorter"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.zyrex.screenshotsorter"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    testImplementation("junit:junit:4.13.2")
}
```

`app/proguard-rules.pro`:

```proguard
# No project-specific rules. The app uses no reflection.
```

- [ ] **Step 5: Write the manifest, resources and a placeholder screen**

`app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="false"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.ScreenshotSorter">
        <activity
            android:name=".ui.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

`app/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">Screenshot Sorter</string>
</resources>
```

`app/src/main/res/values/themes.xml`:

```xml
<resources>
    <style name="Theme.ScreenshotSorter" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

`app/src/main/res/values-night/themes.xml`:

```xml
<resources>
    <style name="Theme.ScreenshotSorter" parent="android:Theme.Material.NoActionBar" />
</resources>
```

`app/src/main/java/com/zyrex/screenshotsorter/ui/MainActivity.kt`:

```kotlin
package com.zyrex.screenshotsorter.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.zyrex.screenshotsorter.R

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text(stringResource(R.string.app_name)) }
    }
}
```

`app/src/test/java/com/zyrex/screenshotsorter/core/BuildSanityTest.kt`:

```kotlin
package com.zyrex.screenshotsorter.core

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildSanityTest {
    @Test
    fun unitTestsRun() {
        assertEquals(4, 2 + 2)
    }
}
```

- [ ] **Step 6: Build and test**

Run: `JAVA_HOME="$HOME/.android-toolchain/jdk-17" ./gradlew :app:assembleDebug :app:testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`. The first run downloads Gradle, the Android platform, build tools and libraries, so it is slow. If the build reports that a library needs a different `compileSdk` or Gradle version, apply exactly the version it names and re-run.

- [ ] **Step 7: Commit**

```bash
rm app/src/test/java/com/zyrex/screenshotsorter/core/BuildSanityTest.kt
git add -A
git commit -m "Scaffold the Android project" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Filename parsing and folder names

**Files:**
- Create: `app/src/main/java/com/zyrex/screenshotsorter/core/ScreenshotNameParser.kt`
- Create: `app/src/main/java/com/zyrex/screenshotsorter/core/Names.kt`
- Test: `app/src/test/java/com/zyrex/screenshotsorter/core/ScreenshotNameParserTest.kt`
- Test: `app/src/test/java/com/zyrex/screenshotsorter/core/NamesTest.kt`

**Interfaces:**
- Produces:
  - `ScreenshotNameParser.tail(fileName: String): String?`
  - `ScreenshotNameParser.looksLikePackageId(text: String): Boolean`
  - `FolderNames.clean(label: String): String`
  - `UniqueName.pick(fileName: String, taken: (String) -> Boolean): String`

- [ ] **Step 1: Write the failing tests**

`ScreenshotNameParserTest.kt`:

```kotlin
package com.zyrex.screenshotsorter.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotNameParserTest {
    @Test
    fun tails() {
        val cases = mapOf(
            // Xiaomi: the friend's real filename.
            "Screenshot_2026-10-09-10-12-05-243_pdf.reader.pdfviewer.pdfeditor.jpg" to "pdf.reader.pdfviewer.pdfeditor",
            "Screenshot_2026-10-09-14-22-33-456_org.telegram.messenger.jpg" to "org.telegram.messenger",
            // Samsung and Huawei.
            "Screenshot_20261009_101205_Telegram.jpg" to "Telegram",
            "Screenshot_20261009_101205_Samsung Internet.jpg" to "Samsung Internet",
            "Screenshot_20261009-101205_Chrome.jpg" to "Chrome",
            "Screenshot_20261009_101205_2GIS.jpg" to "2GIS",
            "Screenshot_20261009_101205_com.whatsapp.PNG" to "com.whatsapp",
            // Endings that are not apps still come back as text; the caller decides.
            "Screenshot_20261009_101205_1.jpg" to "1",
            "Screenshot_20261009-101205_display-1.png" to "display-1",
            "Screenshot_2026-10-09-10-12-05-24_e39d2c7de19156b0683cd93e8735f348.jpg" to "e39d2c7de19156b0683cd93e8735f348",
            // No ending.
            "Screenshot_20261009-101205.png" to null,
            "Screenshot_20261009_101205.jpg" to null,
            "Screenshot_20261009-101205~2.png" to null,
            // Not a screenshot name.
            "IMG_20261009_101205.jpg" to null,
            "holiday.jpg" to null,
            "" to null,
        )
        for ((name, expected) in cases) {
            assertEquals(name, expected, ScreenshotNameParser.tail(name))
        }
    }

    @Test
    fun packageIds() {
        for (id in listOf("org.telegram.messenger", "com.whatsapp", "pdf.reader.pdfviewer.pdfeditor", "Booking.com", "a.b_c.d1")) {
            assertTrue(id, ScreenshotNameParser.looksLikePackageId(id))
        }
        for (text in listOf("Telegram", "Samsung Internet", "1", "1.2", "display-1", "com.foo.", ".com.foo", "com..foo", "")) {
            assertFalse(text, ScreenshotNameParser.looksLikePackageId(text))
        }
    }
}
```

`NamesTest.kt`:

```kotlin
package com.zyrex.screenshotsorter.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderNamesTest {
    @Test
    fun keepsOrdinaryNames() {
        for (name in listOf("Telegram", "PDF Reader - Hi Read", "Телеграм", "Fun 🎉", "2GIS")) {
            assertEquals(name, FolderNames.clean(name))
        }
    }

    @Test
    fun removesCharactersFoldersCannotHave() {
        assertEquals("A B Test", FolderNames.clean("A/B: Test?"))
        assertEquals("a b c d e f g h i", FolderNames.clean("a\\b/c:d*e?f\"g<h>i|"))
        assertEquals("Line Break", FolderNames.clean("Line\nBreak"))
        assertEquals("WhatsApp", FolderNames.clean("  WhatsApp  "))
    }

    @Test
    fun neverMakesAHiddenOrEmptyFolder() {
        assertEquals("hidden", FolderNames.clean("..hidden"))
        assertEquals("Name", FolderNames.clean("Name. "))
        assertEquals("Other", FolderNames.clean("///"))
        assertEquals("Other", FolderNames.clean(""))
        assertEquals("Other", FolderNames.clean(" . "))
    }

    @Test
    fun cutsLongNamesWithoutSplittingAnEmoji() {
        assertEquals(60, FolderNames.clean("x".repeat(100)).length)
        // 59 letters then an emoji: the emoji is two chars, so a cut at 60 would split it.
        assertEquals("x".repeat(59), FolderNames.clean("x".repeat(59) + "🎉"))
    }
}

class UniqueNameTest {
    @Test
    fun keepsAFreeName() {
        assertEquals("a.jpg", UniqueName.pick("a.jpg") { false })
    }

    @Test
    fun addsTheFirstFreeNumberBeforeTheExtension() {
        val taken = setOf("a.jpg", "a (1).jpg", "a (2).jpg")
        assertEquals("a (3).jpg", UniqueName.pick("a.jpg") { it in taken })
    }

    @Test
    fun usesTheLastDotAsTheExtension() {
        val name = "Screenshot_2026-10-09-14-22-33-456_org.telegram.messenger.jpg"
        assertEquals(
            "Screenshot_2026-10-09-14-22-33-456_org.telegram.messenger (1).jpg",
            UniqueName.pick(name) { it == name },
        )
    }

    @Test
    fun handlesNamesWithoutAnExtension() {
        assertEquals("notes (1)", UniqueName.pick("notes") { it == "notes" })
    }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `JAVA_HOME="$HOME/.android-toolchain/jdk-17" ./gradlew :app:testDebugUnitTest`

Expected: compilation fails with `Unresolved reference: ScreenshotNameParser`.

- [ ] **Step 3: Write the implementation**

`ScreenshotNameParser.kt`:

```kotlin
package com.zyrex.screenshotsorter.core

/** Reads the hint some phone makers put after the date in a screenshot's filename. */
object ScreenshotNameParser {
    // Screenshot_2026-10-09-10-12-05-243_<tail>.jpg   Xiaomi, Oppo
    // Screenshot_20261009_101205_<tail>.jpg            Samsung, Huawei
    // Screenshot_20261009-101205.png                   Pixel and stock Android (no tail)
    private val pattern = Regex(
        """^screenshot[_-](?:\d{4}-\d{2}-\d{2}-\d{2}-\d{2}-\d{2}(?:-\d{1,3})?|\d{8}[-_]\d{6})(?:_(.+))?$""",
        RegexOption.IGNORE_CASE,
    )
    private val imageExtension = Regex("""\.(jpe?g|png|webp)$""", RegexOption.IGNORE_CASE)
    private val packageId = Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$""")

    /** The text after the date and time, or null when the name has none or is not a screenshot name. */
    fun tail(fileName: String): String? {
        val base = fileName.replace(imageExtension, "")
        val match = pattern.matchEntire(base) ?: return null
        return match.groupValues[1].trim().ifEmpty { null }
    }

    fun looksLikePackageId(text: String): Boolean = packageId.matches(text)
}
```

`Names.kt`:

```kotlin
package com.zyrex.screenshotsorter.core

object FolderNames {
    const val MAX_LENGTH = 60
    const val FALLBACK = "Other"

    private val illegal = Regex("""[\\/:*?"<>|\p{Cntrl}]""")
    private val spaces = Regex("""\s+""")

    /** Turns an app's display name into a folder name that every phone accepts and no gallery hides. */
    fun clean(label: String): String {
        val tidy = label.replace(illegal, " ").replace(spaces, " ").trim().trimStart('.').trim()
        var short = tidy.take(MAX_LENGTH)
        if (short.isNotEmpty() && short.last().isHighSurrogate()) short = short.dropLast(1)
        return short.trimEnd('.', ' ').ifEmpty { FALLBACK }
    }
}

object UniqueName {
    /** [fileName] if it is free, otherwise "name (1).ext", "name (2).ext", ... until [taken] says no. */
    fun pick(fileName: String, taken: (String) -> Boolean): String {
        if (!taken(fileName)) return fileName
        val dot = fileName.lastIndexOf('.')
        val stem = if (dot > 0) fileName.substring(0, dot) else fileName
        val extension = if (dot > 0) fileName.substring(dot) else ""
        var number = 1
        while (taken("$stem ($number)$extension")) number++
        return "$stem ($number)$extension"
    }
}
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `JAVA_HOME="$HOME/.android-toolchain/jdk-17" ./gradlew :app:testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Parse screenshot filenames and clean folder names" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Working out which app a screenshot belongs to

**Files:**
- Create: `app/src/main/java/com/zyrex/screenshotsorter/core/ForegroundPicker.kt`
- Create: `app/src/main/java/com/zyrex/screenshotsorter/core/AppIdentifier.kt`
- Create: `app/src/main/java/com/zyrex/screenshotsorter/core/UsageNeed.kt`
- Test: `app/src/test/java/com/zyrex/screenshotsorter/core/Fakes.kt`
- Test: `app/src/test/java/com/zyrex/screenshotsorter/core/ForegroundPickerTest.kt`
- Test: `app/src/test/java/com/zyrex/screenshotsorter/core/AppIdentifierTest.kt`
- Test: `app/src/test/java/com/zyrex/screenshotsorter/core/UsageNeedTest.kt`

**Interfaces:**
- Consumes: `ScreenshotNameParser.tail`, `ScreenshotNameParser.looksLikePackageId`, `FolderNames.clean`.
- Produces:
  - `data class ForegroundEvent(val packageName: String, val timeMillis: Long)`
  - `ForegroundPicker.pick(events: List<ForegroundEvent>, atMillis: Long, ignored: (String) -> Boolean): String?`
  - `ForegroundPicker.isScreenshotTool(packageName: String, ownPackage: String): Boolean`
  - `interface InstalledApps { fun labelForPackage(packageId: String): String?; fun labelMatching(text: String): String? }`
  - `interface ForegroundAppFinder { fun packageAt(timeMillis: Long): String? }`
  - `class AppIdentifier(installed: InstalledApps, foreground: ForegroundAppFinder)` with `folderFor(fileName: String, createdAtMillis: Long): String?` and `nameIdentifies(fileName: String): Boolean`
  - `UsageNeed.isNeeded(recentNames: List<String>, manufacturer: String, nameIdentifies: (String) -> Boolean): Boolean`
  - Test fakes: `FakeInstalledApps(byPackage: Map<String, String>)`, `FakeHistory(var packageName: String? = null)` with `var calls: Int`

- [ ] **Step 1: Write the failing tests**

`Fakes.kt`:

```kotlin
package com.zyrex.screenshotsorter.core

class FakeInstalledApps(private val byPackage: Map<String, String>) : InstalledApps {
    override fun labelForPackage(packageId: String): String? = byPackage[packageId]

    override fun labelMatching(text: String): String? =
        byPackage.values.firstOrNull { it.equals(text.trim(), ignoreCase = true) }
}

class FakeHistory(var packageName: String? = null) : ForegroundAppFinder {
    var calls = 0

    override fun packageAt(timeMillis: Long): String? {
        calls++
        return packageName
    }
}
```

`ForegroundPickerTest.kt`:

```kotlin
package com.zyrex.screenshotsorter.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundPickerTest {
    private val events = listOf(
        ForegroundEvent("c", 300),
        ForegroundEvent("a", 100),
        ForegroundEvent("b", 200),
    )

    @Test
    fun picksTheLatestAppNotAfterTheMoment() {
        assertEquals("b", ForegroundPicker.pick(events, 250) { false })
        assertEquals("c", ForegroundPicker.pick(events, 300) { false })
        assertEquals("c", ForegroundPicker.pick(events, 9_999) { false })
    }

    @Test
    fun nothingBeforeTheFirstEvent() {
        assertNull(ForegroundPicker.pick(events, 50) { false })
        assertNull(ForegroundPicker.pick(emptyList(), 50) { false })
    }

    @Test
    fun skipsIgnoredApps() {
        assertEquals("b", ForegroundPicker.pick(events, 350) { it == "c" })
    }

    @Test
    fun knowsScreenshotToolsAndItself() {
        val own = "com.zyrex.screenshotsorter"
        for (tool in listOf(own, "com.android.systemui", "com.miui.screenshot", "com.oplus.screenshot", "com.samsung.android.app.smartcapture")) {
            assertTrue(tool, ForegroundPicker.isScreenshotTool(tool, own))
        }
        for (app in listOf("org.telegram.messenger", "com.miui.home", "com.duolingo")) {
            assertFalse(app, ForegroundPicker.isScreenshotTool(app, own))
        }
    }
}
```

`AppIdentifierTest.kt`:

```kotlin
package com.zyrex.screenshotsorter.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIdentifierTest {
    private val installed = FakeInstalledApps(
        mapOf(
            "org.telegram.messenger" to "Telegram",
            "pdf.reader.pdfviewer.pdfeditor" to "PDF Reader - Hi Read",
            "com.odd" to "A/B: Odd",
        ),
    )
    private val history = FakeHistory()
    private val identifier = AppIdentifier(installed, history)

    @Test
    fun xiaomiNameOfAnInstalledAppGivesItsDisplayName() {
        val name = "Screenshot_2026-10-09-10-12-05-243_pdf.reader.pdfviewer.pdfeditor.jpg"
        assertEquals("PDF Reader - Hi Read", identifier.folderFor(name, 0))
        assertEquals(0, history.calls)
    }

    @Test
    fun xiaomiNameOfAnUninstalledAppFallsBackToTheAppId() {
        assertEquals("com.gone.app", identifier.folderFor("Screenshot_2026-10-09-10-12-05-243_com.gone.app.jpg", 0))
    }

    @Test
    fun samsungNameMatchesAnInstalledAppIgnoringCase() {
        assertEquals("Telegram", identifier.folderFor("Screenshot_20261009_101205_telegram.jpg", 0))
    }

    @Test
    fun tailThatIsNotAnAppFallsBackToHistory() {
        history.packageName = "org.telegram.messenger"
        for (name in listOf("Screenshot_20261009_101205_1.jpg", "Screenshot_20261009-101205_display-1.png")) {
            assertEquals(name, "Telegram", identifier.folderFor(name, 0))
        }
    }

    @Test
    fun plainNameUsesHistory() {
        history.packageName = "org.telegram.messenger"
        assertEquals("Telegram", identifier.folderFor("Screenshot_20261009-101205.png", 0))
        history.packageName = "com.not.visible"
        assertEquals("com.not.visible", identifier.folderFor("Screenshot_20261009-101205.png", 0))
    }

    @Test
    fun nothingKnownGivesNull() {
        assertNull(identifier.folderFor("Screenshot_20261009-101205.png", 0))
        assertNull(identifier.folderFor("Screenshot_20261009_101205_1.jpg", 0))
    }

    @Test
    fun folderNamesAreCleaned() {
        assertEquals("A B Odd", identifier.folderFor("Screenshot_2026-10-09-10-12-05-243_com.odd.jpg", 0))
    }

    @Test
    fun nameIdentifiesOnlyWhenTheFilenameIsEnough() {
        assertTrue(identifier.nameIdentifies("Screenshot_2026-10-09-10-12-05-243_org.telegram.messenger.jpg"))
        assertTrue(identifier.nameIdentifies("Screenshot_20261009_101205_Telegram.jpg"))
        assertFalse(identifier.nameIdentifies("Screenshot_20261009-101205.png"))
        assertFalse(identifier.nameIdentifies("Screenshot_20261009_101205_1.jpg"))
    }
}
```

`UsageNeedTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `JAVA_HOME="$HOME/.android-toolchain/jdk-17" ./gradlew :app:testDebugUnitTest`

Expected: compilation fails with `Unresolved reference: InstalledApps`.

- [ ] **Step 3: Write the implementation**

`ForegroundPicker.kt`:

```kotlin
package com.zyrex.screenshotsorter.core

data class ForegroundEvent(val packageName: String, val timeMillis: Long)

object ForegroundPicker {
    private val toolWords = listOf("screenshot", "screencapture", "smartcapture")

    /** The app that was on screen at [atMillis]: the latest one brought forward at or before it. */
    fun pick(events: List<ForegroundEvent>, atMillis: Long, ignored: (String) -> Boolean): String? =
        events.asSequence()
            .filter { it.timeMillis <= atMillis && !ignored(it.packageName) }
            .maxByOrNull { it.timeMillis }
            ?.packageName

    /** Screenshot tools pop up over the real app, so they must not be mistaken for it. */
    fun isScreenshotTool(packageName: String, ownPackage: String): Boolean {
        val name = packageName.lowercase()
        return packageName == ownPackage || name == "com.android.systemui" || toolWords.any { it in name }
    }
}
```

`AppIdentifier.kt`:

```kotlin
package com.zyrex.screenshotsorter.core

interface InstalledApps {
    /** Display name of the installed app with this ID, or null when it is not installed. */
    fun labelForPackage(packageId: String): String?

    /** The display name of an installed app that equals [text] ignoring case, or null. */
    fun labelMatching(text: String): String?
}

interface ForegroundAppFinder {
    /** ID of the app that was on screen at that moment, or null when the phone cannot say. */
    fun packageAt(timeMillis: Long): String?
}

class AppIdentifier(
    private val installed: InstalledApps,
    private val foreground: ForegroundAppFinder,
) {
    /** Folder name for this screenshot, or null when the app cannot be worked out. */
    fun folderFor(fileName: String, createdAtMillis: Long): String? {
        fromName(fileName)?.let { return FolderNames.clean(it) }
        val packageId = foreground.packageAt(createdAtMillis) ?: return null
        return FolderNames.clean(installed.labelForPackage(packageId) ?: packageId)
    }

    /** True when the filename alone names the app, so usage history is not needed for it. */
    fun nameIdentifies(fileName: String): Boolean = fromName(fileName) != null

    private fun fromName(fileName: String): String? {
        val tail = ScreenshotNameParser.tail(fileName) ?: return null
        if (ScreenshotNameParser.looksLikePackageId(tail)) {
            // An ID of an app that was uninstalled still makes a usable folder name.
            return installed.labelForPackage(tail) ?: installed.labelMatching(tail) ?: tail
        }
        return installed.labelMatching(tail)
    }
}
```

`UsageNeed.kt`:

```kotlin
package com.zyrex.screenshotsorter.core

object UsageNeed {
    private val brandsThatNameTheApp = setOf("xiaomi", "redmi", "poco", "samsung", "huawei", "honor")

    /**
     * Whether this phone needs the Usage access permission. It does not when its screenshot filenames
     * already say which app they came from. With no screenshots to look at yet, go by the brand.
     */
    fun isNeeded(recentNames: List<String>, manufacturer: String, nameIdentifies: (String) -> Boolean): Boolean {
        if (recentNames.isEmpty()) return manufacturer.trim().lowercase() !in brandsThatNameTheApp
        return recentNames.count(nameIdentifies) * 2 < recentNames.size
    }
}
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `JAVA_HOME="$HOME/.android-toolchain/jdk-17" ./gradlew :app:testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Identify the app a screenshot came from" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Finding screenshots and moving files

**Files:**
- Create: `app/src/main/java/com/zyrex/screenshotsorter/core/ScreenshotFinder.kt`
- Create: `app/src/main/java/com/zyrex/screenshotsorter/core/FileMover.kt`
- Test: `app/src/test/java/com/zyrex/screenshotsorter/core/ScreenshotFinderTest.kt`
- Test: `app/src/test/java/com/zyrex/screenshotsorter/core/DirectFileMoverTest.kt`

**Interfaces:**
- Consumes: `UniqueName.pick`.
- Produces:
  - `class ScreenshotFinder(storageRoot: File)` with `folders(): List<File>`, `unsorted(): List<File>` (oldest first), `recentNames(limit: Int): List<String>` (newest first, includes one level of subfolders)
  - `sealed interface MoveResult { data class Moved(val destination: File); data class Failed(val reason: String) }`
  - `interface FileMover { fun move(source: File, targetDir: File): MoveResult }`
  - `class DirectFileMover(onMoved: (from: File, to: File) -> Unit = { _, _ -> }) : FileMover`

- [ ] **Step 1: Write the failing tests**

`ScreenshotFinderTest.kt`:

```kotlin
package com.zyrex.screenshotsorter.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ScreenshotFinderTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun file(path: String, modified: Long = 1_000_000_000_000L): File =
        File(temp.root, path).apply {
            parentFile!!.mkdirs()
            writeText("x")
            setLastModified(modified)
        }

    @Test
    fun findsImagesDirectlyInScreenshotFoldersOldestFirst() {
        file("DCIM/Screenshots/b.jpg", modified = 1_000_000_002_000L)
        file("Pictures/screenshots/a.png", modified = 1_000_000_001_000L)
        file("Pictures/Screenshot/c.webp", modified = 1_000_000_003_000L)

        assertEquals(listOf("a.png", "b.jpg", "c.webp"), ScreenshotFinder(temp.root).unsorted().map { it.name })
    }

    @Test
    fun ignoresEverythingElse() {
        file("DCIM/Screenshots/Telegram/sorted.jpg")
        file("DCIM/Camera/photo.jpg")
        file("DCIM/Screenshots/.pending-123-new.png")
        file("DCIM/Screenshots/notes.txt")
        file("Download/Screenshots/elsewhere.jpg")
        File(temp.root, "DCIM/Screenshots/folder.jpg").mkdirs()

        assertTrue(ScreenshotFinder(temp.root).unsorted().isEmpty())
    }

    @Test
    fun aMissingOrUnreadableRootGivesNothing() {
        val finder = ScreenshotFinder(File(temp.root, "nope"))
        assertTrue(finder.folders().isEmpty())
        assertTrue(finder.unsorted().isEmpty())
        assertTrue(finder.recentNames(30).isEmpty())
    }

    @Test
    fun recentNamesIncludeSortedOnesNewestFirst() {
        file("DCIM/Screenshots/old.jpg", modified = 1_000_000_001_000L)
        file("DCIM/Screenshots/Telegram/new.jpg", modified = 1_000_000_003_000L)
        file("DCIM/Screenshots/Telegram/mid.jpg", modified = 1_000_000_002_000L)
        file("DCIM/Screenshots/Telegram/deep/too-deep.jpg", modified = 1_000_000_004_000L)

        val finder = ScreenshotFinder(temp.root)
        assertEquals(listOf("new.jpg", "mid.jpg", "old.jpg"), finder.recentNames(30))
        assertEquals(listOf("new.jpg", "mid.jpg"), finder.recentNames(2))
    }
}
```

`DirectFileMoverTest.kt`:

```kotlin
package com.zyrex.screenshotsorter.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DirectFileMoverTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun source(name: String = "shot.jpg", text: String = "new"): File =
        File(temp.root, name).apply { writeText(text) }

    @Test
    fun movesIntoANewFolder() {
        val source = source()
        val target = File(temp.root, "Telegram")

        val result = DirectFileMover().move(source, target)

        assertEquals(MoveResult.Moved(File(target, "shot.jpg")), result)
        assertFalse(source.exists())
        assertEquals("new", File(target, "shot.jpg").readText())
    }

    @Test
    fun addsANumberInsteadOfOverwriting() {
        val target = File(temp.root, "Telegram").apply { mkdirs() }
        File(target, "shot.jpg").writeText("old")

        val result = DirectFileMover().move(source(), target)

        assertEquals(MoveResult.Moved(File(target, "shot (1).jpg")), result)
        assertEquals("old", File(target, "shot.jpg").readText())
        assertEquals("new", File(target, "shot (1).jpg").readText())
    }

    @Test
    fun aMissingSourceIsAFailure() {
        val result = DirectFileMover().move(File(temp.root, "gone.jpg"), File(temp.root, "Telegram"))

        assertTrue(result is MoveResult.Failed)
        assertFalse(File(temp.root, "Telegram").exists())
    }

    @Test
    fun aFileInTheWayIsAFailure() {
        val source = source()
        val blocked = File(temp.root, "Telegram").apply { writeText("not a folder") }

        val result = DirectFileMover().move(source, blocked)

        assertTrue(result is MoveResult.Failed)
        assertTrue(source.exists())
        assertEquals("not a folder", blocked.readText())
    }

    @Test
    fun tellsTheIndexAboutTheMove() {
        val seen = mutableListOf<Pair<File, File>>()
        val source = source()
        val target = File(temp.root, "Telegram")

        DirectFileMover { from, to -> seen += from to to }.move(source, target)

        assertEquals(listOf(source to File(target, "shot.jpg")), seen)
    }

    @Test
    fun anIndexErrorDoesNotUndoTheMove() {
        val target = File(temp.root, "Telegram")

        val result = DirectFileMover { _, _ -> error("index is down") }.move(source(), target)

        assertEquals(MoveResult.Moved(File(target, "shot.jpg")), result)
    }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `JAVA_HOME="$HOME/.android-toolchain/jdk-17" ./gradlew :app:testDebugUnitTest`

Expected: compilation fails with `Unresolved reference: ScreenshotFinder`.

- [ ] **Step 3: Write the implementation**

`ScreenshotFinder.kt`:

```kotlin
package com.zyrex.screenshotsorter.core

import java.io.File

/** Finds screenshots by plain file listing, which works the same on every Android version. */
class ScreenshotFinder(private val storageRoot: File) {

    /** Screenshot folders that exist: `DCIM` or `Pictures`, then `Screenshots` or `Screenshot`, any letter case. */
    fun folders(): List<File> =
        children(storageRoot)
            .filter { it.isDirectory && it.name.lowercase() in PARENTS }
            .flatMap { parent -> children(parent).filter { it.isDirectory && it.name.lowercase() in NAMES } }

    /** Images directly inside the screenshot folders, oldest first. Sorted ones are in subfolders and are left out. */
    fun unsorted(): List<File> = folders().flatMap(::imagesIn).sortedBy { it.lastModified() }

    /** Names of the newest screenshots, sorted or not. Used only to learn how this phone names them. */
    fun recentNames(limit: Int): List<String> =
        folders()
            .flatMap { folder -> imagesIn(folder) + children(folder).filter { it.isDirectory }.flatMap(::imagesIn) }
            .sortedByDescending { it.lastModified() }
            .take(limit)
            .map { it.name }

    private fun imagesIn(folder: File): List<File> =
        children(folder).filter { it.isFile && !it.name.startsWith(".") && it.extension.lowercase() in EXTENSIONS }

    // listFiles() returns null when the folder is missing or the app may not read it.
    private fun children(folder: File): List<File> = folder.listFiles()?.toList() ?: emptyList()

    private companion object {
        val PARENTS = setOf("dcim", "pictures")
        val NAMES = setOf("screenshots", "screenshot")
        val EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    }
}
```

`FileMover.kt`:

```kotlin
package com.zyrex.screenshotsorter.core

import java.io.File

sealed interface MoveResult {
    data class Moved(val destination: File) : MoveResult
    data class Failed(val reason: String) : MoveResult
}

interface FileMover {
    fun move(source: File, targetDir: File): MoveResult
}

/**
 * Moves by renaming inside the same storage, which is instant and never copies or overwrites.
 * [onMoved] tells the gallery index; a problem there does not undo the move.
 */
class DirectFileMover(
    private val onMoved: (from: File, to: File) -> Unit = { _, _ -> },
) : FileMover {
    override fun move(source: File, targetDir: File): MoveResult {
        if (!source.isFile) return MoveResult.Failed("The screenshot is no longer there")
        if (targetDir.exists() && !targetDir.isDirectory) {
            return MoveResult.Failed("A file named ${targetDir.name} is in the way")
        }
        if (!targetDir.isDirectory && !targetDir.mkdirs()) {
            return MoveResult.Failed("Could not create the folder ${targetDir.name}")
        }
        val destination = File(targetDir, UniqueName.pick(source.name) { File(targetDir, it).exists() })
        if (!source.renameTo(destination)) return MoveResult.Failed("Could not move the file")
        runCatching { onMoved(source, destination) }
        return MoveResult.Moved(destination)
    }
}
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `JAVA_HOME="$HOME/.android-toolchain/jdk-17" ./gradlew :app:testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Find unsorted screenshots and move files safely" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: The sorting pass, its memory and its log

**Files:**
- Create: `app/src/main/java/com/zyrex/screenshotsorter/core/HandledKeys.kt`
- Create: `app/src/main/java/com/zyrex/screenshotsorter/core/ActivityLog.kt`
- Create: `app/src/main/java/com/zyrex/screenshotsorter/core/Sorter.kt`
- Modify: `app/src/test/java/com/zyrex/screenshotsorter/core/Fakes.kt` (add `MemoryStore`)
- Test: `app/src/test/java/com/zyrex/screenshotsorter/core/HandledKeysTest.kt`
- Test: `app/src/test/java/com/zyrex/screenshotsorter/core/ActivityLogTest.kt`
- Test: `app/src/test/java/com/zyrex/screenshotsorter/core/SorterTest.kt`

**Interfaces:**
- Consumes: `ScreenshotFinder.unsorted()`, `AppIdentifier.folderFor`, `FileMover.move`, `MoveResult`.
- Produces:
  - `interface TextStore { fun read(): String; fun write(text: String) }`
  - `class HandledKeys(store: TextStore, capacity: Int = 2000)` with `contains(key: String): Boolean`, `add(key: String)`, and `HandledKeys.keyFor(file: File): String`
  - `enum class Outcome { MOVED, UNKNOWN_APP, FAILED }`
  - `data class LogEntry(val timeMillis: Long, val fileName: String, val outcome: Outcome, val detail: String)` (`detail` is the folder for `MOVED`, the reason for `FAILED`, empty for `UNKNOWN_APP`)
  - `LogCodec.encode(entries: List<LogEntry>): String`, `LogCodec.decode(text: String): List<LogEntry>`
  - `class ActivityLog(store: TextStore, capacity: Int = 20)` with `val entries: StateFlow<List<LogEntry>>` (newest first) and `record(entry: LogEntry)`
  - `data class SweepResult(val moved: Int, val tooNew: Int)`
  - `class Sorter(finder: ScreenshotFinder, identifier: AppIdentifier, mover: FileMover, handled: HandledKeys, log: ActivityLog, now: () -> Long = System::currentTimeMillis)` with `sortPending(recheckAll: Boolean = false, minAgeMillis: Long = Sorter.MIN_AGE_MILLIS): SweepResult`, `countSortable(): Int`, `Sorter.MIN_AGE_MILLIS = 10_000L`
  - Test fake: `MemoryStore(var text: String = "") : TextStore`

- [ ] **Step 1: Write the failing tests**

Append to `Fakes.kt`:

```kotlin
class MemoryStore(var text: String = "") : TextStore {
    override fun read(): String = text

    override fun write(text: String) {
        this.text = text
    }
}
```

`HandledKeysTest.kt`:

```kotlin
package com.zyrex.screenshotsorter.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandledKeysTest {
    @Test
    fun remembersWhatWasAdded() {
        val keys = HandledKeys(MemoryStore())
        assertFalse(keys.contains("a"))
        keys.add("a")
        assertTrue(keys.contains("a"))
    }

    @Test
    fun survivesARestart() {
        val store = MemoryStore()
        HandledKeys(store).apply {
            add("a")
            add("b")
        }
        val reopened = HandledKeys(store)
        assertTrue(reopened.contains("a"))
        assertTrue(reopened.contains("b"))
    }

    @Test
    fun forgetsTheOldestWhenFull() {
        val keys = HandledKeys(MemoryStore(), capacity = 2)
        keys.add("a")
        keys.add("b")
        keys.add("c")
        assertFalse(keys.contains("a"))
        assertTrue(keys.contains("b"))
        assertTrue(keys.contains("c"))
    }
}
```

`ActivityLogTest.kt`:

```kotlin
package com.zyrex.screenshotsorter.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityLogTest {
    private fun moved(name: String, time: Long = 1) = LogEntry(time, name, Outcome.MOVED, "Telegram")

    @Test
    fun codecRoundTrips() {
        val entries = listOf(
            moved("a.jpg", 3),
            LogEntry(2, "b.png", Outcome.UNKNOWN_APP, ""),
            LogEntry(1, "c.jpg", Outcome.FAILED, "Could not move the file"),
        )
        assertEquals(entries, LogCodec.decode(LogCodec.encode(entries)))
    }

    @Test
    fun codecKeepsOddCharactersOnOneLine() {
        val entry = LogEntry(1, "a\tb\nc.jpg", Outcome.MOVED, "X\tY")
        assertEquals(listOf(LogEntry(1, "a b c.jpg", Outcome.MOVED, "X Y")), LogCodec.decode(LogCodec.encode(listOf(entry))))
    }

    @Test
    fun codecSkipsDamagedLines() {
        val text = "garbage\n5\tok.jpg\tMOVED\tTelegram\nx\tbad-time.jpg\tMOVED\tT\n6\tbad-outcome.jpg\tNOPE\tT"
        assertEquals(listOf(moved("ok.jpg", 5)), LogCodec.decode(text))
        assertEquals(emptyList<LogEntry>(), LogCodec.decode(""))
    }

    @Test
    fun newestFirstAndCapped() {
        val log = ActivityLog(MemoryStore(), capacity = 2)
        log.record(moved("a.jpg"))
        log.record(moved("b.jpg"))
        log.record(moved("c.jpg"))
        assertEquals(listOf("c.jpg", "b.jpg"), log.entries.value.map { it.fileName })
    }

    @Test
    fun aRepeatedResultIsListedOnce() {
        val log = ActivityLog(MemoryStore())
        val failed = LogEntry(1, "a.jpg", Outcome.FAILED, "Could not move the file")
        log.record(failed)
        log.record(failed.copy(timeMillis = 2))
        log.record(moved("a.jpg", 3))
        assertEquals(listOf(Outcome.MOVED, Outcome.FAILED), log.entries.value.map { it.outcome })
    }

    @Test
    fun survivesARestart() {
        val store = MemoryStore()
        ActivityLog(store).record(moved("a.jpg"))
        assertEquals(listOf(moved("a.jpg")), ActivityLog(store).entries.value)
    }
}
```

`SorterTest.kt`:

```kotlin
package com.zyrex.screenshotsorter.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SorterTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val clock = 1_000_000_000_000L
    private val telegramShot = "Screenshot_2026-10-09-14-22-33-456_org.telegram.messenger.jpg"
    private val plainShot = "Screenshot_20261009-101205.png"
    private val history = FakeHistory()
    private lateinit var shots: File
    private lateinit var log: ActivityLog
    private lateinit var sorter: Sorter

    @Before
    fun setUp() {
        shots = temp.newFolder("DCIM", "Screenshots")
        log = ActivityLog(MemoryStore())
        sorter = sorterFor(temp.root)
    }

    private fun sorterFor(root: File) = Sorter(
        ScreenshotFinder(root),
        AppIdentifier(FakeInstalledApps(mapOf("org.telegram.messenger" to "Telegram")), history),
        DirectFileMover(),
        HandledKeys(MemoryStore()),
        log,
    ) { clock }

    private fun shot(name: String, ageMillis: Long = 60_000, text: String = "new"): File =
        File(shots, name).apply {
            writeText(text)
            setLastModified(clock - ageMillis)
        }

    @Test
    fun movesAScreenshotIntoTheAppFolder() {
        val file = shot(telegramShot)

        assertEquals(SweepResult(moved = 1, tooNew = 0), sorter.sortPending())

        assertFalse(file.exists())
        assertTrue(File(shots, "Telegram/$telegramShot").isFile)
        assertEquals(listOf(LogEntry(clock, telegramShot, Outcome.MOVED, "Telegram")), log.entries.value)
    }

    @Test
    fun leavesAFreshScreenshotForTheNextPass() {
        val file = shot(telegramShot, ageMillis = 3_000)

        assertEquals(SweepResult(moved = 0, tooNew = 1), sorter.sortPending())

        assertTrue(file.exists())
        assertTrue(log.entries.value.isEmpty())
    }

    @Test
    fun anUnknownScreenshotStaysAndIsNotCheckedAgain() {
        val file = shot(plainShot)

        sorter.sortPending()
        sorter.sortPending()

        assertTrue(file.exists())
        assertEquals(1, history.calls)
        assertEquals(listOf(LogEntry(clock, plainShot, Outcome.UNKNOWN_APP, "")), log.entries.value)
    }

    @Test
    fun recheckAllLooksAtGivenUpScreenshotsAgain() {
        shot(plainShot)
        sorter.sortPending()
        history.packageName = "org.telegram.messenger"

        assertEquals(SweepResult(moved = 1, tooNew = 0), sorter.sortPending(recheckAll = true))
        assertTrue(File(shots, "Telegram/$plainShot").isFile)
    }

    @Test
    fun oldUnknownScreenshotsAreNotListedInRecent() {
        shot(plainShot, ageMillis = 2 * 60 * 60_000L)

        sorter.sortPending()

        assertTrue(log.entries.value.isEmpty())
    }

    @Test
    fun aFailedMoveIsLoggedAndTriedAgain() {
        val file = shot(telegramShot)
        val inTheWay = File(shots, "Telegram").apply { writeText("not a folder") }

        assertEquals(SweepResult(moved = 0, tooNew = 0), sorter.sortPending())
        assertTrue(file.exists())
        assertEquals(Outcome.FAILED, log.entries.value.first().outcome)

        inTheWay.delete()
        assertEquals(SweepResult(moved = 1, tooNew = 0), sorter.sortPending())
    }

    @Test
    fun neverOverwritesAnExistingFile() {
        File(shots, "Telegram").mkdirs()
        File(shots, "Telegram/$telegramShot").writeText("old")
        shot(telegramShot, text = "new")

        sorter.sortPending()

        assertEquals("old", File(shots, "Telegram/$telegramShot").readText())
        assertEquals("new", File(shots, "Telegram/${telegramShot.removeSuffix(".jpg")} (1).jpg").readText())
    }

    @Test
    fun aZeroMinimumAgeSortsFreshOnesToo() {
        shot(telegramShot, ageMillis = 0)

        assertEquals(SweepResult(moved = 1, tooNew = 0), sorter.sortPending(minAgeMillis = 0))
    }

    @Test
    fun countsOnlyWhatItCanIdentify() {
        shot(telegramShot)
        shot(plainShot)

        assertEquals(1, sorter.countSortable())
    }

    @Test
    fun missingStorageIsNotAnError() {
        val nowhere = sorterFor(File(temp.root, "nope"))

        assertEquals(SweepResult(moved = 0, tooNew = 0), nowhere.sortPending())
        assertEquals(0, nowhere.countSortable())
    }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `JAVA_HOME="$HOME/.android-toolchain/jdk-17" ./gradlew :app:testDebugUnitTest`

Expected: compilation fails with `Unresolved reference: TextStore`.

- [ ] **Step 3: Write the implementation**

`HandledKeys.kt`:

```kotlin
package com.zyrex.screenshotsorter.core

import java.io.File

/** A small piece of saved text. On the phone it is backed by app preferences. */
interface TextStore {
    fun read(): String
    fun write(text: String)
}

/**
 * Remembers screenshots that were looked at and could not be matched to an app,
 * so they are not looked at again on every pass.
 */
class HandledKeys(private val store: TextStore, private val capacity: Int = 2000) {
    private val keys = LinkedHashSet(store.read().split('\n').filter { it.isNotEmpty() })

    @Synchronized
    fun contains(key: String): Boolean = key in keys

    @Synchronized
    fun add(key: String) {
        if (!keys.add(key)) return
        while (keys.size > capacity) keys.remove(keys.first())
        store.write(keys.joinToString("\n"))
    }

    companion object {
        /** Changes when the file is edited or replaced, so an edited screenshot gets a fresh look. */
        fun keyFor(file: File): String =
            "${file.absolutePath}|${file.lastModified()}|${file.length()}".replace('\n', ' ')
    }
}
```

`ActivityLog.kt`:

```kotlin
package com.zyrex.screenshotsorter.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class Outcome { MOVED, UNKNOWN_APP, FAILED }

/** [detail] is the folder for MOVED, the reason for FAILED, and empty for UNKNOWN_APP. */
data class LogEntry(val timeMillis: Long, val fileName: String, val outcome: Outcome, val detail: String)

/** One entry per line, fields separated by tabs. */
object LogCodec {
    fun encode(entries: List<LogEntry>): String = entries.joinToString("\n") { entry ->
        listOf(entry.timeMillis.toString(), oneLine(entry.fileName), entry.outcome.name, oneLine(entry.detail))
            .joinToString("\t")
    }

    fun decode(text: String): List<LogEntry> = text.lineSequence().mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size != 4) return@mapNotNull null
        val time = parts[0].toLongOrNull() ?: return@mapNotNull null
        val outcome = Outcome.entries.firstOrNull { it.name == parts[2] } ?: return@mapNotNull null
        LogEntry(time, parts[1], outcome, parts[3])
    }.toList()

    private fun oneLine(text: String): String = text.replace('\t', ' ').replace('\n', ' ')
}

/** The recent list shown on the screen, newest first. */
class ActivityLog(private val store: TextStore, private val capacity: Int = 20) {
    private val state = MutableStateFlow(LogCodec.decode(store.read()).take(capacity))
    val entries: StateFlow<List<LogEntry>> = state

    @Synchronized
    fun record(entry: LogEntry) {
        val current = state.value
        // A move that keeps failing is retried on every pass; list it once.
        if (current.any { it.fileName == entry.fileName && it.outcome == entry.outcome && it.detail == entry.detail }) return
        val next = (listOf(entry) + current).take(capacity)
        state.value = next
        store.write(LogCodec.encode(next))
    }
}
```

`Sorter.kt`:

```kotlin
package com.zyrex.screenshotsorter.core

import java.io.File

data class SweepResult(val moved: Int, val tooNew: Int)

/** One pass over the waiting screenshots. Shared by the watcher, the catch-up job and "Sort old screenshots". */
class Sorter(
    private val finder: ScreenshotFinder,
    private val identifier: AppIdentifier,
    private val mover: FileMover,
    private val handled: HandledKeys,
    private val log: ActivityLog,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /**
     * Files changed less than [minAgeMillis] ago are left for the next pass, so the system's edit/share
     * popup keeps working. [recheckAll] also retries the ones that could not be matched earlier.
     */
    @Synchronized
    fun sortPending(recheckAll: Boolean = false, minAgeMillis: Long = MIN_AGE_MILLIS): SweepResult {
        var moved = 0
        var tooNew = 0
        for (file in finder.unsorted()) {
            val age = now() - file.lastModified()
            if (age < minAgeMillis) {
                tooNew++
                continue
            }
            val key = HandledKeys.keyFor(file)
            if (!recheckAll && handled.contains(key)) continue

            val folder = identifier.folderFor(file.name, file.lastModified())
            if (folder == null) {
                handled.add(key)
                // An old backlog would flood the recent list; only say so for ones just taken.
                if (age < RECENT_MILLIS) log.record(LogEntry(now(), file.name, Outcome.UNKNOWN_APP, ""))
                continue
            }
            when (val result = mover.move(file, File(file.parentFile, folder))) {
                is MoveResult.Moved -> {
                    moved++
                    log.record(LogEntry(now(), file.name, Outcome.MOVED, folder))
                }
                is MoveResult.Failed -> log.record(LogEntry(now(), file.name, Outcome.FAILED, result.reason))
            }
        }
        return SweepResult(moved, tooNew)
    }

    /** How many waiting screenshots can be matched to an app right now. */
    fun countSortable(): Int =
        finder.unsorted().count { identifier.folderFor(it.name, it.lastModified()) != null }

    companion object {
        const val MIN_AGE_MILLIS = 10_000L
        private const val RECENT_MILLIS = 10 * 60_000L
    }
}
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `JAVA_HOME="$HOME/.android-toolchain/jdk-17" ./gradlew :app:testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Add the sorting pass with its log and memory" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Android adapters and permissions

**Files:**
- Create: `app/src/main/java/com/zyrex/screenshotsorter/core/AutostartScreens.kt`
- Create: `app/src/main/java/com/zyrex/screenshotsorter/platform/SetupState.kt`
- Create: `app/src/main/java/com/zyrex/screenshotsorter/platform/AndroidInstalledApps.kt`
- Create: `app/src/main/java/com/zyrex/screenshotsorter/platform/UsageForegroundAppFinder.kt`
- Create: `app/src/main/java/com/zyrex/screenshotsorter/platform/MediaIndex.kt`
- Create: `app/src/main/java/com/zyrex/screenshotsorter/platform/Prefs.kt`
- Create: `app/src/main/java/com/zyrex/screenshotsorter/AppGraph.kt`
- Create: `app/src/main/java/com/zyrex/screenshotsorter/SorterApp.kt`
- Modify: `app/src/main/AndroidManifest.xml` (permissions, `android:name=".SorterApp"`, legacy storage flag)
- Modify: `app/build.gradle.kts` (Robolectric, `testOptions`)
- Test: `app/src/test/java/com/zyrex/screenshotsorter/core/AutostartScreensTest.kt`
- Test: `app/src/test/java/com/zyrex/screenshotsorter/platform/PlatformSmokeTest.kt`

**Interfaces:**
- Consumes: `InstalledApps`, `ForegroundAppFinder`, `ForegroundEvent`, `ForegroundPicker`, `TextStore`, `ScreenshotFinder`, `AppIdentifier`, `DirectFileMover`, `HandledKeys`, `ActivityLog`, `Sorter`.
- Produces:
  - `data class ScreenTarget(val packageName: String, val className: String)`
  - `AutostartScreens.forManufacturer(manufacturer: String): List<ScreenTarget>`, `AutostartScreens.hint(manufacturer: String): String?`
  - `SetupState.hasStorage(context)`, `hasUsageAccess(context)`, `ignoresBatteryLimits(context)`, `notificationsAllowed(context)`: all `Boolean`
  - `SetupState.allFilesAccessIntents(context)` (API 30+), `usageAccessIntents(context)`, `batteryIntents(context)`, `autostartIntents(context)`, `notificationSettingsIntents(context)`: all `List<Intent>`; `SetupState.appDetailsIntent(context): Intent`
  - `SetupState.openFirst(context: Context, intents: List<Intent>): Boolean`
  - `class Prefs(context)` with `var enabled: Boolean`, `var usageAccessSeen: Boolean`, `textStore(key: String): TextStore`
  - `class AppGraph(context)` with `prefs`, `finder`, `identifier`, `log`, `sorter`
  - `class SorterApp : Application` with `val graph: AppGraph`

- [ ] **Step 1: Add Robolectric and write the failing tests**

In `app/build.gradle.kts`, add inside `android { }`:

```kotlin
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
```

and inside `dependencies { }`:

```kotlin
    testImplementation("org.robolectric:robolectric:4.17")
```

`AutostartScreensTest.kt`:

```kotlin
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
```

`PlatformSmokeTest.kt` (runs the real Android code of each version on the JVM, so a call that does not exist on Android 6.0 fails here):

```kotlin
package com.zyrex.screenshotsorter.platform

import android.Manifest
import android.app.Application
import com.zyrex.screenshotsorter.SorterApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 35])
class PlatformSmokeTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()

    // Robolectric grants every manifest permission by default; a freshly installed app has none.
    @Before
    fun startWithoutStorage() {
        shadowOf(app).denyPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    @Test
    fun readsEveryPermissionWithoutCrashing() {
        assertFalse(SetupState.hasStorage(app))
        assertFalse(SetupState.hasUsageAccess(app))
        SetupState.ignoresBatteryLimits(app)
        SetupState.notificationsAllowed(app)
    }

    @Test
    fun buildsEverySettingsIntent() {
        assertTrue(SetupState.usageAccessIntents(app).isNotEmpty())
        assertTrue(SetupState.batteryIntents(app).isNotEmpty())
        assertTrue(SetupState.notificationSettingsIntents(app).isNotEmpty())
        SetupState.autostartIntents(app)
        SetupState.appDetailsIntent(app)
        if (android.os.Build.VERSION.SDK_INT >= 30) assertTrue(SetupState.allFilesAccessIntents(app).isNotEmpty())
    }

    @Test
    fun resolvesInstalledApps() {
        val apps = AndroidInstalledApps(app)
        assertEquals("Screenshot Sorter", apps.labelForPackage(app.packageName))
        assertNull(apps.labelForPackage("no.such.app"))
        assertNull(apps.labelMatching("No Such App"))
    }

    @Test
    fun historyIsSilentWithoutUsageAccess() {
        assertNull(UsageForegroundAppFinder(app).packageAt(System.currentTimeMillis()))
    }

    @Test
    fun indexUpdateDoesNotCrash() {
        MediaIndex(app).onMoved(
            File("/storage/emulated/0/DCIM/Screenshots/a.jpg"),
            File("/storage/emulated/0/DCIM/Screenshots/Telegram/a.jpg"),
        )
    }

    @Test
    fun prefsRoundTrip() {
        val prefs = Prefs(app)
        assertFalse(prefs.enabled)
        prefs.enabled = true
        assertTrue(Prefs(app).enabled)
        prefs.textStore("log").write("hello")
        assertEquals("hello", Prefs(app).textStore("log").read())
        assertEquals("", Prefs(app).textStore("other").read())
    }

    @Test
    fun theGraphBuildsAndSortsNothingWithoutStorage() {
        val graph = (app as SorterApp).graph
        assertEquals(0, graph.sorter.sortPending().moved)
    }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `JAVA_HOME="$HOME/.android-toolchain/jdk-17" ./gradlew :app:testDebugUnitTest`

Expected: compilation fails with `Unresolved reference: AutostartScreens`.

- [ ] **Step 3: Write `AutostartScreens`**

`AutostartScreens.kt`:

```kotlin
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
```

- [ ] **Step 4: Write the Android adapters**

`SetupState.kt`:

```kotlin
package com.zyrex.screenshotsorter.platform

import android.Manifest
import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.zyrex.screenshotsorter.core.AutostartScreens

/** What the phone has allowed so far, and which settings screen grants each thing. */
object SetupState {
    fun hasStorage(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun ignoresBatteryLimits(context: Context): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun notificationsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    @RequiresApi(Build.VERSION_CODES.R)
    fun allFilesAccessIntents(context: Context): List<Intent> = listOf(
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri(context)),
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
    )

    fun usageAccessIntents(context: Context): List<Intent> = listOf(
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, packageUri(context)),
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
    )

    fun batteryIntents(context: Context): List<Intent> = listOf(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context)),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    )

    fun autostartIntents(context: Context): List<Intent> =
        AutostartScreens.forManufacturer(Build.MANUFACTURER).map {
            Intent().setComponent(ComponentName(it.packageName, it.className))
        }

    fun notificationSettingsIntents(context: Context): List<Intent> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            listOf(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                appDetailsIntent(context),
            )
        } else {
            listOf(appDetailsIntent(context))
        }

    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))

    /** Opens the first screen that exists on this phone. False when none could be opened. */
    fun openFirst(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: ActivityNotFoundException) {
                // Not on this phone; try the next one.
            } catch (e: SecurityException) {
                // The screen exists but other apps may not open it; try the next one.
            }
        }
        return false
    }

    private fun packageUri(context: Context): Uri = Uri.parse("package:${context.packageName}")
}
```

`AndroidInstalledApps.kt`:

```kotlin
package com.zyrex.screenshotsorter.platform

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import com.zyrex.screenshotsorter.core.InstalledApps

class AndroidInstalledApps(context: Context) : InstalledApps {
    private val packages = context.packageManager
    private var labels: Map<String, String> = emptyMap()
    private var labelsLoadedAt = Long.MIN_VALUE

    override fun labelForPackage(packageId: String): String? = try {
        packages.getApplicationLabel(packages.getApplicationInfo(packageId, 0)).toString().trim().ifEmpty { null }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    @Synchronized
    override fun labelMatching(text: String): String? {
        val now = SystemClock.elapsedRealtime()
        // Apps get installed and removed; a minute-old list is fresh enough and avoids reloading per screenshot.
        if (labelsLoadedAt == Long.MIN_VALUE || now - labelsLoadedAt > REFRESH_MILLIS) {
            labels = loadLabels()
            labelsLoadedAt = now
        }
        return labels[text.trim().lowercase()]
    }

    private fun loadLabels(): Map<String, String> {
        val launchable = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packages.queryIntentActivities(launchable, 0).associate { info ->
            val label = info.activityInfo.applicationInfo.loadLabel(packages).toString().trim()
            label.lowercase() to label
        }
    }

    private companion object {
        const val REFRESH_MILLIS = 60_000L
    }
}
```

`UsageForegroundAppFinder.kt`:

```kotlin
package com.zyrex.screenshotsorter.platform

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.zyrex.screenshotsorter.core.ForegroundAppFinder
import com.zyrex.screenshotsorter.core.ForegroundEvent
import com.zyrex.screenshotsorter.core.ForegroundPicker

class UsageForegroundAppFinder(private val context: Context) : ForegroundAppFinder {
    override fun packageAt(timeMillis: Long): String? {
        // Android keeps usage events for only a few days, so do not bother asking about older moments.
        if (System.currentTimeMillis() - timeMillis > RETENTION_MILLIS) return null
        if (!SetupState.hasUsageAccess(context)) return null

        val usage = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val cursor = usage.queryEvents(timeMillis - LOOKBACK_MILLIS, timeMillis + 1) ?: return null
        val events = ArrayList<ForegroundEvent>()
        val event = UsageEvents.Event()
        while (cursor.hasNextEvent()) {
            cursor.getNextEvent(event)
            @Suppress("DEPRECATION")
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                events += ForegroundEvent(event.packageName, event.timeStamp)
            }
        }
        return ForegroundPicker.pick(events, timeMillis) {
            ForegroundPicker.isScreenshotTool(it, context.packageName)
        }
    }

    private companion object {
        const val RETENTION_MILLIS = 14 * 24 * 60 * 60_000L
        // The app on screen may have been opened long before the screenshot.
        const val LOOKBACK_MILLIS = 24 * 60 * 60_000L
    }
}
```

`MediaIndex.kt`:

```kotlin
package com.zyrex.screenshotsorter.platform

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.provider.MediaStore
import java.io.File

/** Keeps the gallery's index in step after a file was moved by path. */
class MediaIndex(private val context: Context) {
    fun onMoved(from: File, to: File) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // Before Android 11 the index does not notice renames, so the old entry would linger as a broken image.
            context.contentResolver.delete(
                MediaStore.Files.getContentUri("external"),
                "${MediaStore.MediaColumns.DATA}=?",
                arrayOf(from.absolutePath),
            )
        }
        MediaScannerConnection.scanFile(context, arrayOf(to.absolutePath), null, null)
    }
}
```

`Prefs.kt`:

```kotlin
package com.zyrex.screenshotsorter.platform

import android.content.Context
import com.zyrex.screenshotsorter.core.TextStore

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** The big on/off switch. */
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()

    /** Whether usage access was already granted the last time the screen looked. */
    var usageAccessSeen: Boolean
        get() = prefs.getBoolean("usageAccessSeen", false)
        set(value) = prefs.edit().putBoolean("usageAccessSeen", value).apply()

    fun textStore(key: String): TextStore = object : TextStore {
        override fun read(): String = prefs.getString(key, "") ?: ""

        override fun write(text: String) {
            prefs.edit().putString(key, text).apply()
        }
    }
}
```

`AppGraph.kt`:

```kotlin
package com.zyrex.screenshotsorter

import android.content.Context
import android.os.Environment
import com.zyrex.screenshotsorter.core.ActivityLog
import com.zyrex.screenshotsorter.core.AppIdentifier
import com.zyrex.screenshotsorter.core.DirectFileMover
import com.zyrex.screenshotsorter.core.HandledKeys
import com.zyrex.screenshotsorter.core.ScreenshotFinder
import com.zyrex.screenshotsorter.core.Sorter
import com.zyrex.screenshotsorter.platform.AndroidInstalledApps
import com.zyrex.screenshotsorter.platform.MediaIndex
import com.zyrex.screenshotsorter.platform.Prefs
import com.zyrex.screenshotsorter.platform.UsageForegroundAppFinder

/** The app's long-lived objects, built once per process and shared by the screen, the service and the job. */
class AppGraph(context: Context) {
    val prefs = Prefs(context)

    @Suppress("DEPRECATION")
    val finder = ScreenshotFinder(Environment.getExternalStorageDirectory())
    val identifier = AppIdentifier(AndroidInstalledApps(context), UsageForegroundAppFinder(context))
    val log = ActivityLog(prefs.textStore("log"))
    val sorter = Sorter(
        finder,
        identifier,
        DirectFileMover(MediaIndex(context)::onMoved),
        HandledKeys(prefs.textStore("handled")),
        log,
    )
}
```

`SorterApp.kt`:

```kotlin
package com.zyrex.screenshotsorter

import android.app.Application

class SorterApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}
```

- [ ] **Step 5: Declare permissions in the manifest**

Replace `app/src/main/AndroidManifest.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- Android 6 to 10: the normal storage permission. Android 11+: All files access. -->
    <uses-permission
        android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="29" />
    <uses-permission
        android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="29" />
    <uses-permission
        android:name="android.permission.MANAGE_EXTERNAL_STORAGE"
        tools:ignore="ScopedStorage" />

    <!-- Which app was on screen, for phones whose screenshot names do not say. -->
    <uses-permission
        android:name="android.permission.PACKAGE_USAGE_STATS"
        tools:ignore="ProtectedPermissions" />
    <!-- App ID to display name. Must be replaced by a <queries> block before any Google Play release. -->
    <uses-permission
        android:name="android.permission.QUERY_ALL_PACKAGES"
        tools:ignore="QueryAllPackagesPermission" />

    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission
        android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"
        tools:ignore="BatteryLife" />

    <application
        android:name=".SorterApp"
        android:allowBackup="false"
        android:label="@string/app_name"
        android:requestLegacyExternalStorage="true"
        android:supportsRtl="true"
        android:theme="@style/Theme.ScreenshotSorter">
        <activity
            android:name=".ui.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

- [ ] **Step 6: Run the tests to see them pass**

Run: `JAVA_HOME="$HOME/.android-toolchain/jdk-17" ./gradlew :app:testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`. `PlatformSmokeTest` runs twice, once per Android version (the first run downloads the two Android runtime jars). If Robolectric reports that it does not support API 23 or needs a newer JDK for API 35, use the closest version it names and note the change here.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "Add Android adapters and permissions" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Watching in the background

**Files:**
- Create: `app/src/main/java/com/zyrex/screenshotsorter/watch/WatchService.kt`
- Create: `app/src/main/java/com/zyrex/screenshotsorter/watch/CatchUpJob.kt`
- Create: `app/src/main/java/com/zyrex/screenshotsorter/watch/BootReceiver.kt`
- Create: `app/src/main/res/drawable/ic_stat_sort.xml`
- Modify: `app/src/main/res/values/strings.xml` (notification strings)
- Modify: `app/src/main/AndroidManifest.xml` (service, job, receiver)
- Test: `app/src/test/java/com/zyrex/screenshotsorter/watch/WatchSmokeTest.kt`

**Interfaces:**
- Consumes: `SorterApp.graph` (`prefs.enabled`, `sorter.sortPending()`), `SetupState.hasStorage`, `Sorter.MIN_AGE_MILLIS`, `SweepResult.tooNew`, `ui.MainActivity`.
- Produces:
  - `WatchService.start(context: Context): Boolean` (false when Android refuses a background start), `WatchService.stop(context: Context)`
  - `CatchUpJob.schedule(context: Context)`, `CatchUpJob.cancel(context: Context)`
  - `BootReceiver` (manifest-registered)

- [ ] **Step 1: Write the failing test**

`WatchSmokeTest.kt`:

```kotlin
package com.zyrex.screenshotsorter.watch

import android.Manifest
import android.app.Application
import android.app.job.JobScheduler
import android.content.Context
import android.content.Intent
import com.zyrex.screenshotsorter.SorterApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 35])
class WatchSmokeTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val jobs: JobScheduler get() = app.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

    // Robolectric grants every manifest permission by default; a freshly installed app has none.
    @Before
    fun startWithoutStorage() {
        shadowOf(app).denyPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    @Test
    fun theServiceGoesForegroundAndShutsDownCleanly() {
        val controller = Robolectric.buildService(WatchService::class.java).create()
        assertNotNull(shadowOf(controller.get()).lastForegroundNotification)
        controller.destroy()
    }

    @Test
    fun startAsksAndroidForTheService() {
        assertTrue(WatchService.start(app))
        assertEquals(WatchService::class.java.name, shadowOf(app).nextStartedService.component?.className)
    }

    @Test
    fun theCatchUpJobIsScheduledOnceAndCanBeCancelled() {
        CatchUpJob.schedule(app)
        CatchUpJob.schedule(app)
        assertEquals(1, jobs.allPendingJobs.size)
        CatchUpJob.cancel(app)
        assertEquals(0, jobs.allPendingJobs.size)
    }

    @Test
    fun bootDoesNothingWhileSortingIsOff() {
        BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNull(shadowOf(app).nextStartedService)
        assertEquals(0, jobs.allPendingJobs.size)
    }

    @Test
    fun bootSchedulesCatchUpWhenSortingIsOn() {
        (app as SorterApp).graph.prefs.enabled = true
        BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(1, jobs.allPendingJobs.size)
        // Storage is not granted in this test, so the watcher itself must not start.
        assertNull(shadowOf(app).nextStartedService)
    }
}
```

- [ ] **Step 2: Run the test to see it fail**

Run: `JAVA_HOME="$HOME/.android-toolchain/jdk-17" ./gradlew :app:testDebugUnitTest`

Expected: compilation fails with `Unresolved reference: WatchService`.

- [ ] **Step 3: Add the notification icon and strings**

`app/src/main/res/drawable/ic_stat_sort.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M10,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z" />
</vector>
```

Add to `strings.xml` inside `<resources>`:

```xml
    <string name="channel_name">Sorting status</string>
    <string name="notification_title">Screenshot Sorter is on</string>
    <string name="notification_text">New screenshots are filed by app.</string>
```

- [ ] **Step 4: Write the service, the job and the receiver**

`WatchService.kt`:

```kotlin
package com.zyrex.screenshotsorter.watch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.zyrex.screenshotsorter.R
import com.zyrex.screenshotsorter.SorterApp
import com.zyrex.screenshotsorter.core.Sorter
import com.zyrex.screenshotsorter.platform.SetupState
import com.zyrex.screenshotsorter.ui.MainActivity
import java.util.concurrent.atomic.AtomicBoolean

/** Stays alive as a foreground service and sorts each new screenshot shortly after it appears. */
class WatchService : Service() {
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var observer: ContentObserver? = null
    private val sweepQueued = AtomicBoolean(false)
    private val sweep = Runnable { runSweep() }

    override fun onCreate() {
        super.onCreate()
        if (!goForeground()) {
            // Android refused (the app is in the background). The catch-up job covers until the app is opened.
            stopSelf()
            return
        }
        val worker = HandlerThread("screenshot-watch").apply { start() }
        val workerHandler = Handler(worker.looper)
        val mediaObserver = object : ContentObserver(workerHandler) {
            override fun onChange(selfChange: Boolean) = queueSweep(Sorter.MIN_AGE_MILLIS + SETTLE_MILLIS)
        }
        contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, mediaObserver)
        thread = worker
        handler = workerHandler
        observer = mediaObserver
        queueSweep(0)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        observer?.let(contentResolver::unregisterContentObserver)
        handler?.removeCallbacksAndMessages(null)
        thread?.quitSafely()
        super.onDestroy()
    }

    // One queued sweep is enough: it handles everything old enough and re-queues itself for the rest.
    private fun queueSweep(delayMillis: Long) {
        if (sweepQueued.compareAndSet(false, true)) handler?.postDelayed(sweep, delayMillis)
    }

    private fun runSweep() {
        sweepQueued.set(false)
        if (!SetupState.hasStorage(this)) return
        val result = (application as SorterApp).graph.sorter.sortPending()
        if (result.tooNew > 0) queueSweep(Sorter.MIN_AGE_MILLIS)
    }

    private fun goForeground(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
            channel.setShowBadge(false)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_sort)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        return try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
            true
        } catch (e: IllegalStateException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    companion object {
        private const val CHANNEL_ID = "sorting"
        private const val NOTIFICATION_ID = 1
        private const val SETTLE_MILLIS = 500L

        /** Starts watching. False when Android refuses to start a service from the background. */
        fun start(context: Context): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, WatchService::class.java))
            true
        } catch (e: IllegalStateException) {
            false
        } catch (e: SecurityException) {
            false
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WatchService::class.java))
        }
    }
}
```

`CatchUpJob.kt`:

```kotlin
package com.zyrex.screenshotsorter.watch

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.zyrex.screenshotsorter.SorterApp
import com.zyrex.screenshotsorter.platform.SetupState

/** Runs about every 15 minutes: sorts what the watcher missed and brings the watcher back if the phone killed it. */
class CatchUpJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val graph = (application as SorterApp).graph
        if (!graph.prefs.enabled || !SetupState.hasStorage(this)) return false
        Thread {
            runCatching { graph.sorter.sortPending() }
            WatchService.start(this)
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = false

    companion object {
        private const val JOB_ID = 1
        private const val PERIOD_MILLIS = 15 * 60_000L

        fun schedule(context: Context) {
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, CatchUpJob::class.java))
                .setPeriodic(PERIOD_MILLIS)
                .setPersisted(true)
                .build()
            scheduler(context).schedule(job)
        }

        fun cancel(context: Context) {
            scheduler(context).cancel(JOB_ID)
        }

        private fun scheduler(context: Context) =
            context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
    }
}
```

`BootReceiver.kt`:

```kotlin
package com.zyrex.screenshotsorter.watch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zyrex.screenshotsorter.SorterApp
import com.zyrex.screenshotsorter.platform.SetupState

/** Brings sorting back after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = (context.applicationContext as SorterApp).graph
        if (!graph.prefs.enabled) return
        CatchUpJob.schedule(context)
        if (SetupState.hasStorage(context)) WatchService.start(context)
    }
}
```

- [ ] **Step 5: Register them in the manifest**

Add inside `<application>`, after the `<activity>` element:

```xml
        <service
            android:name=".watch.WatchService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="Moves new screenshots into per-app folders" />
        </service>

        <service
            android:name=".watch.CatchUpJob"
            android:exported="false"
            android:permission="android.permission.BIND_JOB_SERVICE" />

        <receiver
            android:name=".watch.BootReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.QUICKBOOT_POWERON" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
            </intent-filter>
        </receiver>
```

- [ ] **Step 6: Run the tests to see them pass**

Run: `JAVA_HOME="$HOME/.android-toolchain/jdk-17" ./gradlew :app:testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`, all tests pass on both Android versions.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "Watch for screenshots in the background" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: The screen

**Files:**
- Create: `app/src/main/java/com/zyrex/screenshotsorter/ui/MainViewModel.kt`
- Create: `app/src/main/java/com/zyrex/screenshotsorter/ui/MainScreen.kt`
- Create: `app/src/main/java/com/zyrex/screenshotsorter/ui/Theme.kt`
- Modify: `app/src/main/java/com/zyrex/screenshotsorter/ui/MainActivity.kt` (replace the placeholder)
- Modify: `app/src/main/res/values/strings.xml` (all screen text)
- Create: `app/src/main/res/drawable/ic_check_circle.xml`, `ic_circle.xml`, `ic_folder.xml`, `ic_help.xml`, `ic_alert.xml`, `ic_launcher_foreground.xml`
- Create: `app/src/main/res/values/colors.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `app/src/main/res/mipmap-anydpi/ic_launcher.xml`
- Modify: `app/src/main/AndroidManifest.xml` (`android:icon`)
- Modify: `app/build.gradle.kts` (Compose test and Roborazzi dependencies, screenshot properties)
- Test: `app/src/test/java/com/zyrex/screenshotsorter/ui/MainScreenSmokeTest.kt`
- Test: `app/src/test/java/com/zyrex/screenshotsorter/ui/MainScreenShots.kt`

**Interfaces:**
- Consumes: `SorterApp.graph` (`prefs`, `finder.recentNames`, `identifier.nameIdentifies`, `log.entries`, `sorter`), `SetupState.*`, `UsageNeed.isNeeded`, `AutostartScreens.hint`, `WatchService.start/stop`, `CatchUpJob.schedule/cancel`, `LogEntry`, `Outcome`.
- Produces:
  - `data class SetupUi(storage, usage, usageNeeded, battery, notifications, asksNotifications: Boolean, brandHint: String?)`
  - `sealed interface OldSort { Idle; Counting; Confirm(count: Int); Working; Done(moved: Int) }`
  - `data class MainUi(setup: SetupUi, enabled: Boolean, recent: List<LogEntry>, oldSort: OldSort)`
  - `class MainViewModel(app: Application)` with `ui: StateFlow<MainUi>`, `refresh()`, `setEnabled(on: Boolean)`, `countOld()`, `confirmOld()`, `dismissOld()`
  - `class MainActions(...)` and `@Composable fun MainScreen(ui: MainUi, actions: MainActions)`
  - `@Composable fun SorterTheme(content: @Composable () -> Unit)`

- [ ] **Step 1: Add test dependencies and write the failing tests**

In `app/build.gradle.kts`, replace the `testOptions` block with:

```kotlin
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("roborazzi.test.record", "true")
                it.systemProperty("shots.dir", layout.buildDirectory.dir("shots").get().asFile.absolutePath)
            }
        }
    }
```

and add inside `dependencies { }`:

```kotlin
    testImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.76.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.76.0")
```

`MainScreenSmokeTest.kt` (launches the real activity on Android 6.0 and 15):

```kotlin
package com.zyrex.screenshotsorter.ui

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 35])
class MainScreenSmokeTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    // Robolectric grants every manifest permission by default; a freshly installed app has none.
    // The permission must be taken away before the activity starts, so the activity is launched by hand.
    private fun onFirstLaunch(check: () -> Unit) {
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        ActivityScenario.launch(MainActivity::class.java).use { check() }
    }

    @Test
    fun firstLaunchShowsTheSwitchOffAndTheSetupList() = onFirstLaunch {
        compose.onNodeWithText("Sorting is off").assertIsDisplayed()
        compose.onNodeWithText("Allow files access below first.").assertIsDisplayed()
        compose.onNodeWithText("Files access").assertIsDisplayed()
        compose.onNodeWithText("Run in the background").assertIsDisplayed()
    }

    @Test
    fun sortingOldScreenshotsWaitsForFilesAccess() = onFirstLaunch {
        compose.onNodeWithText("Sort old screenshots").performScrollTo().assertIsNotEnabled()
    }
}
```

`MainScreenShots.kt` (renders the screen to PNG files so the design can be looked at without a phone):

```kotlin
package com.zyrex.screenshotsorter.ui

import com.github.takahirom.roborazzi.captureRoboImage
import com.zyrex.screenshotsorter.core.AutostartScreens
import com.zyrex.screenshotsorter.core.LogEntry
import com.zyrex.screenshotsorter.core.Outcome
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h1400dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainScreenShots {
    private val now = System.currentTimeMillis()
    private val working = MainUi(
        setup = SetupUi(
            storage = true,
            usage = false,
            usageNeeded = false,
            battery = true,
            notifications = false,
            asksNotifications = true,
            brandHint = AutostartScreens.hint("Xiaomi"),
        ),
        enabled = true,
        recent = listOf(
            LogEntry(now - 60_000, "Screenshot_2026-10-10-14-22-33-456_org.telegram.messenger.jpg", Outcome.MOVED, "Telegram"),
            LogEntry(now - 9 * 60_000, "Screenshot_2026-10-10-14-13-02-120_com.duolingo.jpg", Outcome.MOVED, "Duolingo"),
            LogEntry(now - 40 * 60_000, "Screenshot_2026-10-10-13-41-55-803_pdf.reader.pdfviewer.pdfeditor.jpg", Outcome.MOVED, "PDF Reader - Hi Read"),
            LogEntry(now - 3 * 3_600_000L, "Screenshot_20261010-110501.png", Outcome.UNKNOWN_APP, ""),
            LogEntry(now - 5 * 3_600_000L, "Screenshot_2026-10-10-09-00-00-001_com.google.android.youtube.jpg", Outcome.FAILED, "Could not move the file"),
        ),
        oldSort = OldSort.Idle,
    )

    @Test
    fun firstLaunch() = shoot("first-launch.png", MainUi())

    @Test
    fun workingLight() = shoot("working-light.png", working)

    @Test
    @Config(qualifiers = "+night")
    fun workingDark() = shoot("working-dark.png", working)

    private fun shoot(name: String, ui: MainUi) {
        captureRoboImage(File(System.getProperty("shots.dir"), name).path) {
            SorterTheme { MainScreen(ui, MainActions()) }
        }
    }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `JAVA_HOME="$HOME/.android-toolchain/jdk-17" ./gradlew :app:testDebugUnitTest`

Expected: compilation fails with `Unresolved reference: MainUi`.

- [ ] **Step 3: Add the text, colours and icons**

Replace `app/src/main/res/values/strings.xml` with:

```xml
<resources>
    <string name="app_name">Screenshot Sorter</string>

    <string name="sorting_on">Sorting is on</string>
    <string name="sorting_off">Sorting is off</string>
    <string name="sorting_on_detail">New screenshots move into a folder named after the app, about 10 seconds after you take them.</string>
    <string name="sorting_off_detail">Turn it on to file new screenshots by app.</string>
    <string name="sorting_needs_files">Allow files access below first.</string>

    <string name="setup_title">Setup</string>
    <string name="setup_files">Files access</string>
    <string name="setup_files_detail">Lets the app move screenshots into folders.</string>
    <string name="setup_usage">Usage access</string>
    <string name="setup_usage_detail">Lets the app see which app was open when you took the screenshot.</string>
    <string name="setup_usage_not_needed">Not needed on this phone. Its screenshot names already include the app.</string>
    <string name="setup_usage_restricted">If the switch there is greyed out, open this app\'s App info, tap the ⋮ menu and choose Allow restricted settings.</string>
    <string name="setup_battery">Run in the background</string>
    <string name="setup_battery_detail">Stops the phone from putting the app to sleep.</string>
    <string name="setup_autostart">Autostart</string>
    <string name="setup_notifications">Notification</string>
    <string name="setup_notifications_detail">Optional. Shows a small note while sorting is on.</string>
    <string name="action_allow">Allow</string>
    <string name="action_open">Open</string>

    <string name="old_title">Old screenshots</string>
    <string name="old_detail">Sort the screenshots that are already in your gallery.</string>
    <string name="old_button">Sort old screenshots</string>
    <plurals name="old_confirm_title">
        <item quantity="one">Sort %d screenshot?</item>
        <item quantity="other">Sort %d screenshots?</item>
    </plurals>
    <string name="old_confirm_text">They move into folders named after their apps. Nothing is deleted.</string>
    <string name="old_none_title">Nothing to sort</string>
    <string name="old_none_text">No waiting screenshot could be matched to an app. Phones that do not put the app in the screenshot name can only match the last few days.</string>
    <plurals name="old_done_title">
        <item quantity="one">Moved %d screenshot</item>
        <item quantity="other">Moved %d screenshots</item>
    </plurals>
    <string name="action_sort">Sort</string>
    <string name="action_cancel">Cancel</string>
    <string name="action_ok">OK</string>

    <string name="recent_title">Recent</string>
    <string name="recent_empty">Nothing yet. Take a screenshot to try it.</string>
    <string name="recent_moved">Moved to %s</string>
    <string name="recent_unknown">Could not tell which app</string>
    <string name="recent_failed">Not moved: %s</string>

    <string name="channel_name">Sorting status</string>
    <string name="notification_title">Screenshot Sorter is on</string>
    <string name="notification_text">New screenshots are filed by app.</string>
</resources>
```

`app/src/main/res/values/colors.xml`:

```xml
<resources>
    <color name="launcher_background">#1F6A4D</color>
</resources>
```

Each icon is a 24dp vector with this shape; only `pathData` differs:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="PATH" />
</vector>
```

| File | `pathData` |
|---|---|
| `drawable/ic_check_circle.xml` | `M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM10,17l-5,-5 1.41,-1.41L10,14.17l7.59,-7.59L19,8l-9,9z` |
| `drawable/ic_circle.xml` | `M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8z` |
| `drawable/ic_folder.xml` | `M10,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z` |
| `drawable/ic_help.xml` | `M11,18h2v-2h-2v2zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8zM12,6c-2.21,0 -4,1.79 -4,4h2c0,-1.1 0.9,-2 2,-2s2,0.9 2,2c0,2 -3,1.75 -3,5h2c0,-2.25 3,-2.5 3,-5 0,-2.21 -1.79,-4 -4,-4z` |
| `drawable/ic_alert.xml` | `M11,15h2v2h-2zM11,7h2v6h-2zM11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2zM12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8z` |

`app/src/main/res/drawable/ic_launcher_foreground.xml` (a white folder centred in the 108dp adaptive-icon canvas):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group
        android:scaleX="2"
        android:scaleY="2"
        android:translateX="30"
        android:translateY="30">
        <path
            android:fillColor="#FFFFFFFF"
            android:pathData="M10,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z" />
    </group>
</vector>
```

`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

`app/src/main/res/mipmap-anydpi/ic_launcher.xml` (Android 6 and 7 have no adaptive icons):

```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape android:shape="oval">
            <solid android:color="@color/launcher_background" />
        </shape>
    </item>
    <item android:drawable="@drawable/ic_launcher_foreground" />
</layer-list>
```

In the manifest's `<application>` tag add `android:icon="@mipmap/ic_launcher"`.

- [ ] **Step 4: Write the view model**

`MainViewModel.kt`:

```kotlin
package com.zyrex.screenshotsorter.ui

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zyrex.screenshotsorter.SorterApp
import com.zyrex.screenshotsorter.core.AutostartScreens
import com.zyrex.screenshotsorter.core.LogEntry
import com.zyrex.screenshotsorter.core.UsageNeed
import com.zyrex.screenshotsorter.platform.SetupState
import com.zyrex.screenshotsorter.watch.CatchUpJob
import com.zyrex.screenshotsorter.watch.WatchService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SetupUi(
    val storage: Boolean = false,
    val usage: Boolean = false,
    val usageNeeded: Boolean = true,
    val battery: Boolean = false,
    val notifications: Boolean = true,
    val asksNotifications: Boolean = false,
    val brandHint: String? = null,
)

sealed interface OldSort {
    data object Idle : OldSort
    data object Counting : OldSort
    data class Confirm(val count: Int) : OldSort
    data object Working : OldSort
    data class Done(val moved: Int) : OldSort
}

data class MainUi(
    val setup: SetupUi = SetupUi(),
    val enabled: Boolean = false,
    val recent: List<LogEntry> = emptyList(),
    val oldSort: OldSort = OldSort.Idle,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as SorterApp).graph
    private val setup = MutableStateFlow(SetupUi())
    private val enabled = MutableStateFlow(graph.prefs.enabled)
    private val oldSort = MutableStateFlow<OldSort>(OldSort.Idle)
    private var knownNames: List<String> = emptyList()

    val ui: StateFlow<MainUi> =
        combine(setup, enabled, graph.log.entries, oldSort) { s, e, recent, old -> MainUi(s, e, recent, old) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, MainUi())

    /** Re-reads every permission. Called each time the screen comes back, because they change in system settings. */
    fun refresh() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) { readSetup(context) }
            setup.value = state
            if (enabled.value && state.storage) {
                // Opening the app also revives a watcher the phone may have killed.
                WatchService.start(context)
                CatchUpJob.schedule(context)
            }
            if (state.usage && !graph.prefs.usageAccessSeen) {
                // Usage access was just granted: screenshots given up on earlier can now be matched.
                withContext(Dispatchers.IO) { graph.sorter.sortPending(recheckAll = true) }
            }
            graph.prefs.usageAccessSeen = state.usage
        }
    }

    fun setEnabled(on: Boolean) {
        if (on && !setup.value.storage) return
        val context = getApplication<Application>()
        graph.prefs.enabled = on
        enabled.value = on
        if (on) {
            WatchService.start(context)
            CatchUpJob.schedule(context)
        } else {
            WatchService.stop(context)
            CatchUpJob.cancel(context)
        }
    }

    fun countOld() {
        oldSort.value = OldSort.Counting
        viewModelScope.launch {
            oldSort.value = OldSort.Confirm(withContext(Dispatchers.IO) { graph.sorter.countSortable() })
        }
    }

    fun confirmOld() {
        oldSort.value = OldSort.Working
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { graph.sorter.sortPending(recheckAll = true, minAgeMillis = 0) }
            oldSort.value = OldSort.Done(result.moved)
        }
    }

    fun dismissOld() {
        oldSort.value = OldSort.Idle
    }

    private fun readSetup(context: Context): SetupUi {
        val storage = SetupState.hasStorage(context)
        // Listing every screenshot is slow on a full gallery, so keep the first answer that has names in it.
        if (storage && knownNames.isEmpty()) knownNames = graph.finder.recentNames(NAME_SAMPLE)
        return SetupUi(
            storage = storage,
            usage = SetupState.hasUsageAccess(context),
            usageNeeded = UsageNeed.isNeeded(knownNames, Build.MANUFACTURER, graph.identifier::nameIdentifies),
            battery = SetupState.ignoresBatteryLimits(context),
            notifications = SetupState.notificationsAllowed(context),
            asksNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
            brandHint = AutostartScreens.hint(Build.MANUFACTURER),
        )
    }

    private companion object {
        const val NAME_SAMPLE = 30
    }
}
```

- [ ] **Step 5: Write the theme and the screen**

`Theme.kt`:

```kotlin
package com.zyrex.screenshotsorter.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F6A4D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA8F2CC),
    onPrimaryContainer = Color(0xFF002114),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8CD5B1),
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF005138),
    onPrimaryContainer = Color(0xFFA8F2CC),
)

@Composable
fun SorterTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        // Android 12+ can take the colours from the user's wallpaper.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
```

`MainScreen.kt`:

```kotlin
package com.zyrex.screenshotsorter.ui

import android.os.Build
import android.text.format.DateUtils
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zyrex.screenshotsorter.R
import com.zyrex.screenshotsorter.core.LogEntry
import com.zyrex.screenshotsorter.core.Outcome

class MainActions(
    val onToggle: (Boolean) -> Unit = {},
    val onGrantStorage: () -> Unit = {},
    val onGrantUsage: () -> Unit = {},
    val onAllowBackground: () -> Unit = {},
    val onOpenAutostart: () -> Unit = {},
    val onAllowNotifications: () -> Unit = {},
    val onSortOld: () -> Unit = {},
    val onConfirmSortOld: () -> Unit = {},
    val onDismissSortOld: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(ui: MainUi, actions: MainActions) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SwitchCard(ui, actions.onToggle) }
            item { SectionTitle(R.string.setup_title) }
            item { SetupCard(ui.setup, actions) }
            item { SectionTitle(R.string.old_title) }
            item { OldCard(ui.oldSort, canSort = ui.setup.storage, onSortOld = actions.onSortOld) }
            item { SectionTitle(R.string.recent_title) }
            if (ui.recent.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.recent_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            } else {
                items(ui.recent) { RecentRow(it) }
            }
        }
    }
    OldSortDialog(ui.oldSort, actions)
}

@Composable
private fun SectionTitle(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp),
    )
}

@Composable
private fun SwitchCard(ui: MainUi, onToggle: (Boolean) -> Unit) {
    val ready = ui.setup.storage
    val on = ui.enabled && ready
    val container = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    Card(colors = CardDefaults.cardColors(containerColor = container)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(if (on) R.string.sorting_on else R.string.sorting_off),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    stringResource(
                        when {
                            !ready -> R.string.sorting_needs_files
                            on -> R.string.sorting_on_detail
                            else -> R.string.sorting_off_detail
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(checked = on, onCheckedChange = onToggle, enabled = ready)
        }
    }
}

private enum class StepState {
    Done, Todo, NotNeeded, Manual;

    companion object {
        fun of(done: Boolean) = if (done) Done else Todo
    }
}

@Composable
private fun SetupCard(setup: SetupUi, actions: MainActions) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        SetupRow(
            R.string.setup_files,
            stringResource(R.string.setup_files_detail),
            StepState.of(setup.storage),
            R.string.action_allow,
            actions.onGrantStorage,
        )
        HorizontalDivider()
        if (setup.usageNeeded) {
            // Android 15 hides this switch for apps installed from an APK until the user unlocks it.
            val restricted = !setup.usage && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
            val detail = stringResource(R.string.setup_usage_detail) +
                if (restricted) "\n" + stringResource(R.string.setup_usage_restricted) else ""
            SetupRow(R.string.setup_usage, detail, StepState.of(setup.usage), R.string.action_allow, actions.onGrantUsage)
        } else {
            SetupRow(
                R.string.setup_usage,
                stringResource(R.string.setup_usage_not_needed),
                StepState.NotNeeded,
                R.string.action_allow,
                actions.onGrantUsage,
            )
        }
        HorizontalDivider()
        SetupRow(
            R.string.setup_battery,
            stringResource(R.string.setup_battery_detail),
            StepState.of(setup.battery),
            R.string.action_allow,
            actions.onAllowBackground,
        )
        if (setup.brandHint != null) {
            HorizontalDivider()
            SetupRow(R.string.setup_autostart, setup.brandHint, StepState.Manual, R.string.action_open, actions.onOpenAutostart)
        }
        if (setup.asksNotifications) {
            HorizontalDivider()
            SetupRow(
                R.string.setup_notifications,
                stringResource(R.string.setup_notifications_detail),
                StepState.of(setup.notifications),
                R.string.action_allow,
                actions.onAllowNotifications,
            )
        }
    }
}

@Composable
private fun SetupRow(@StringRes title: Int, detail: String, state: StepState, @StringRes action: Int, onClick: () -> Unit) {
    val ticked = state == StepState.Done || state == StepState.NotNeeded
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(if (ticked) R.drawable.ic_check_circle else R.drawable.ic_circle),
            contentDescription = null,
            tint = if (state == StepState.Done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(24.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state == StepState.Todo || state == StepState.Manual) {
            FilledTonalButton(onClick = onClick) { Text(stringResource(action)) }
        }
    }
}

@Composable
private fun OldCard(state: OldSort, canSort: Boolean, onSortOld: () -> Unit) {
    val busy = state is OldSort.Counting || state is OldSort.Working
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.old_detail), style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onSortOld, enabled = canSort && !busy) {
                    Text(stringResource(R.string.old_button))
                }
                if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun OldSortDialog(state: OldSort, actions: MainActions) {
    when (state) {
        is OldSort.Confirm -> if (state.count > 0) {
            AlertDialog(
                onDismissRequest = actions.onDismissSortOld,
                title = { Text(pluralStringResource(R.plurals.old_confirm_title, state.count, state.count)) },
                text = { Text(stringResource(R.string.old_confirm_text)) },
                confirmButton = {
                    TextButton(onClick = actions.onConfirmSortOld) { Text(stringResource(R.string.action_sort)) }
                },
                dismissButton = {
                    TextButton(onClick = actions.onDismissSortOld) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        } else {
            NoticeDialog(stringResource(R.string.old_none_title), stringResource(R.string.old_none_text), actions.onDismissSortOld)
        }
        is OldSort.Done -> NoticeDialog(
            pluralStringResource(R.plurals.old_done_title, state.moved, state.moved),
            null,
            actions.onDismissSortOld,
        )
        else -> Unit
    }
}

@Composable
private fun NoticeDialog(title: String, text: String?, onDismiss: () -> Unit) {
    val body: (@Composable () -> Unit)? = if (text != null) {
        { Text(text) }
    } else {
        null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = body,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

@Composable
private fun RecentRow(entry: LogEntry) {
    @DrawableRes val icon: Int
    val tint: Color
    val text: String
    when (entry.outcome) {
        Outcome.MOVED -> {
            icon = R.drawable.ic_folder
            tint = MaterialTheme.colorScheme.primary
            text = stringResource(R.string.recent_moved, entry.detail)
        }
        Outcome.UNKNOWN_APP -> {
            icon = R.drawable.ic_help
            tint = MaterialTheme.colorScheme.outline
            text = stringResource(R.string.recent_unknown)
        }
        Outcome.FAILED -> {
            icon = R.drawable.ic_alert
            tint = MaterialTheme.colorScheme.error
            text = stringResource(R.string.recent_failed, entry.detail)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                entry.fileName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            DateUtils.getRelativeTimeSpanString(entry.timeMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

- [ ] **Step 6: Connect the activity**

Replace `MainActivity.kt` with:

```kotlin
package com.zyrex.screenshotsorter.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zyrex.screenshotsorter.platform.SetupState

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SorterTheme {
                val ui by viewModel.ui.collectAsStateWithLifecycle()
                val activity = this
                val storageRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    // After a second refusal Android stops asking, so the only way left is the app's settings page.
                    if (!granted && !shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        SetupState.openFirst(activity, listOf(SetupState.appDetailsIntent(activity)))
                    }
                    viewModel.refresh()
                }
                val notificationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                    ) {
                        SetupState.openFirst(activity, SetupState.notificationSettingsIntents(activity))
                    }
                    viewModel.refresh()
                }
                val actions = remember {
                    MainActions(
                        onToggle = viewModel::setEnabled,
                        onGrantStorage = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                SetupState.openFirst(activity, SetupState.allFilesAccessIntents(activity))
                            } else {
                                storageRequest.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            }
                        },
                        onGrantUsage = { SetupState.openFirst(activity, SetupState.usageAccessIntents(activity)) },
                        onAllowBackground = { SetupState.openFirst(activity, SetupState.batteryIntents(activity)) },
                        onOpenAutostart = {
                            SetupState.openFirst(
                                activity,
                                SetupState.autostartIntents(activity) + SetupState.appDetailsIntent(activity),
                            )
                        },
                        onAllowNotifications = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onSortOld = viewModel::countOld,
                        onConfirmSortOld = viewModel::confirmOld,
                        onDismissSortOld = viewModel::dismissOld,
                    )
                }
                MainScreen(ui, actions)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }
}
```

- [ ] **Step 7: Run the tests to see them pass**

Run: `JAVA_HOME="$HOME/.android-toolchain/jdk-17" ./gradlew :app:testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`. Three PNG files appear in `~/.screenshot-sorter-build/app/shots/`.

- [ ] **Step 8: Look at the screen**

Read `first-launch.png`, `working-light.png` and `working-dark.png` with the Read tool. Check: nothing is clipped or overlapping, the switch card is the clear first thing, every setup row's text fits beside its button, long filenames end in an ellipsis, and the dark version is readable. Fix what is wrong in `MainScreen.kt` and re-run Step 7 until the three images are clean. Copy the final images to `docs/screens/` so the user can see them.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "Add the main screen" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Signed release APK and the phone checklist

**Files:**
- Create: `release.jks`, `keystore.properties` (neither committed)
- Modify: `app/build.gradle.kts` (signing, shrinking)
- Create: `docs/phone-test-checklist.md`
- Create: `dist/ScreenshotSorter-1.0.apk` (not committed)
- Modify: `docs/superpowers/specs/2026-10-09-screenshot-sorter-design.md` (record what was actually tested)

**Interfaces:**
- Consumes: everything above.
- Produces: `dist/ScreenshotSorter-1.0.apk`, signed, installable on Android 6.0+.

- [ ] **Step 1: Create the signing key**

```bash
cd "/c/Users/i7/OneDrive/Desktop/screenshot-sorter"
PASS=$(openssl rand -hex 16)
"$HOME/.android-toolchain/jdk-17/bin/keytool" -genkeypair -keystore release.jks -alias screenshotsorter \
  -keyalg RSA -keysize 2048 -validity 10000 -storepass "$PASS" -keypass "$PASS" -dname "CN=Zyrex"
printf 'storeFile=release.jks\nstorePassword=%s\nkeyAlias=screenshotsorter\nkeyPassword=%s\n' "$PASS" "$PASS" > keystore.properties
git status --short
```

Expected: `git status` lists neither `release.jks` nor `keystore.properties` (both are ignored). Never print the password. Both files must be kept: an update only installs over the old app if it is signed with this key.

- [ ] **Step 2: Sign and shrink the release build**

In `app/build.gradle.kts`, add at the top, under the `plugins { }` block:

```kotlin
val keystore = java.util.Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}
```

and add inside `android { }`, after `defaultConfig { }`:

```kotlin
    signingConfigs {
        if (keystore.containsKey("storeFile")) {
            create("release") {
                storeFile = rootProject.file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }
```

- [ ] **Step 3: Run lint, all tests and the release build**

Run: `JAVA_HOME="$HOME/.android-toolchain/jdk-17" ./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleRelease`

Expected: `BUILD SUCCESSFUL`. Lint must report no errors; a `NewApi` error means a call that does not exist on Android 6.0 and must be fixed, not suppressed. Read the lint report at `~/.screenshot-sorter-build/app/reports/lint-results-debug.txt` and fix anything that is a real problem.

- [ ] **Step 4: Check the APK**

```bash
APK="$HOME/.screenshot-sorter-build/app/outputs/apk/release/app-release.apk"
BT=$(ls -d "$LOCALAPPDATA/Android/Sdk/build-tools/"* | tail -1)
"$BT/apksigner.bat" verify --verbose --print-certs "$APK" | head -8
"$BT/aapt2.exe" dump badging "$APK" | grep -E "^package|sdkVersion|targetSdkVersion|application-label:"
mkdir -p dist && cp "$APK" dist/ScreenshotSorter-1.0.apk && ls -la dist
```

Expected: `Verifies`, signer `CN=Zyrex`, `sdkVersion:'23'`, `targetSdkVersion:'36'`, `application-label:'Screenshot Sorter'`, package `com.zyrex.screenshotsorter`.

- [ ] **Step 5: Write the phone checklist**

`docs/phone-test-checklist.md`:

```markdown
# Testing Screenshot Sorter on the phone

The app has only been tested on a computer so far. This is the first run on a real phone, so please go through every step.

## Install

1. Send `ScreenshotSorter-1.0.apk` to the phone (Telegram works) and open it.
2. If the phone asks, allow installing apps from this source.
3. On Xiaomi, wait for the security scan. If it warns about an unknown app, choose to install anyway.

## Set up

4. Open Screenshot Sorter.
5. Tap **Allow** next to *Files access* and switch it on. Go back to the app.
6. Tap **Allow** next to *Run in the background* and confirm.
7. Xiaomi, Redmi, POCO: tap **Open** next to *Autostart* and switch on Screenshot Sorter. Then open the app's App info and set *Battery saver* to *No restrictions*.
8. Turn on the big switch at the top.

## Check

- **A.** Take a screenshot in Telegram. Wait 15 seconds. Open the gallery's Albums. Is there a "Telegram" album with the screenshot in it?
- **B.** Take a screenshot in another app. Same check.
- **C.** Take a screenshot and immediately tap the small preview to edit or share it. Does that still work?
- **D.** Tap **Sort old screenshots**. Does the number look right? After confirming, are the old screenshots in app albums?
- **E.** Restart the phone and do not open Screenshot Sorter. Take a screenshot. Is it sorted within a minute? If not, is it sorted after 20 minutes?
- **F.** The next day: is it still sorting?

## Tell me

- Phone model and Android / MIUI / HyperOS version.
- Which of A to F did not work.
- A screenshot of the app's *Recent* list.
```

- [ ] **Step 6: Bring the spec in line with what was done**

In the spec, replace the **Emulator** bullet in "Testing" with:

```markdown
- **Simulated Android on the computer** (Robolectric) on Android 6.0 and Android 15: every Android call the
  app makes, the background service, the boot receiver, the periodic job, and a launch of the real screen.
  No emulator was used because the download is several gigabytes; the real phone is the first full test.
```

and replace the first bullet in "Build and delivery" with:

```markdown
- Minimum Android 6.0 (API 23), the lowest the current Android libraries support. Targets Android 16
  (API 36) and is compiled against API 37.
```

and add to "Later: Google Play":

```markdown
- Replace the `QUERY_ALL_PACKAGES` permission with a `<queries>` block, and drop the direct
  battery-optimisation request, both of which Google restricts.
```

- [ ] **Step 7: Commit**

```bash
git add -A
git status --short
git commit -m "Sign the release build and add the phone checklist" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

Expected before committing: `release.jks`, `keystore.properties` and `dist/` are not in the list.

---

## Self-review notes

- **Spec coverage.** Purpose and success criteria: Tasks 5, 7. Filename, Samsung-name and usage-history detection: Tasks 2, 3, 6. Folder naming and screenshot folders: Tasks 2, 4. Ten-second wait, rename-only moves, no overwrite, retry on failure, "already looked at" memory: Tasks 4, 5. Permission table: Task 6 (manifest, `SetupState`) and Task 8 (rows). "Usage access not needed on this phone": Task 3 (`UsageNeed`) and Task 8. Foreground service, start on switch/app open/boot, catch-up on start/open/every 15 minutes: Tasks 7, 8. Brand autostart help: Tasks 6, 8. The screen's four parts: Task 8. Build and delivery: Tasks 1, 9. Android 15 restricted-settings note: Task 8.
- **Changed from the spec, on purpose.** Emulator testing is replaced by Robolectric plus lint (limited internet); Task 9 Step 6 updates the spec to say so. `targetSdk` is 36 with `compileSdk` 37, because the current libraries require compiling against 37.
- **Not covered by any automated test.** Xiaomi's background killing, how the Xiaomi gallery shows the new folders, and real file moves under All files access. These are exactly the "only a real phone can confirm" items in the spec and are what the checklist in Task 9 asks the friend to check.
