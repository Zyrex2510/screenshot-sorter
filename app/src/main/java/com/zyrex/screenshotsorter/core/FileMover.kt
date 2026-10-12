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
 * Moves by renaming inside the same storage, which is instant and never copies.
 *
 * The free name is checked first and the rename runs second, and on Android a rename replaces an
 * existing file. So moves run one at a time, and nothing is overwritten as long as this app is the
 * only thing adding files to the target folder, which is true for the per-app folders it creates.
 * [onMoved] tells the gallery index; a problem there does not undo the move.
 */
class DirectFileMover(
    private val onMoved: (from: File, to: File) -> Unit = { _, _ -> },
) : FileMover {
    @Synchronized
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
