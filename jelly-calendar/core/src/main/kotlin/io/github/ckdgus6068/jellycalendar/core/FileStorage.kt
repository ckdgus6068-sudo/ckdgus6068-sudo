package io.github.ckdgus6068.jellycalendar.core

import java.io.File

/**
 * Keeps [AppData] in one JSON file. Writes go to a temporary file first and are then renamed,
 * so a crash in the middle of a write never leaves a half-written calendar behind.
 * The previous version is kept as ".bak" and used when the main file cannot be read.
 */
class FileStorage(private val file: File) {
    private val backup = File(file.path + ".bak")
    private val temp = File(file.path + ".tmp")
    private var lastWritten: String? = null

    fun load(): AppData {
        for (candidate in listOf(file, backup)) {
            if (!candidate.exists()) continue
            val text = runCatching { candidate.readText() }.getOrNull() ?: continue
            val data = runCatching { JellyCodec.decode(text) }.getOrNull()
            if (data != null) {
                if (candidate == file) lastWritten = text
                return data
            }
            // Keep the unreadable file around instead of silently overwriting it later.
            runCatching {
                candidate.copyTo(File(candidate.path + ".broken-" + System.currentTimeMillis()), overwrite = true)
            }
        }
        return AppData()
    }

    @Synchronized
    fun save(data: AppData) {
        val text = JellyCodec.encode(data)
        if (text == lastWritten) return
        file.parentFile?.mkdirs()
        temp.writeText(text)
        if (file.exists()) file.copyTo(backup, overwrite = true)
        if (!temp.renameTo(file)) {
            file.writeText(text)
            temp.delete()
        }
        lastWritten = text
    }
}
