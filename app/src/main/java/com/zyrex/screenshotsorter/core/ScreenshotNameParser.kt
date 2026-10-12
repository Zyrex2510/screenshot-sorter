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
