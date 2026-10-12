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
