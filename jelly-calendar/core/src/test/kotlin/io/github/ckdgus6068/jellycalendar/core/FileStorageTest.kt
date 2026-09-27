package io.github.ckdgus6068.jellycalendar.core

import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileStorageTest {

    private fun tempDir(): File = Files.createTempDirectory("jelly").toFile()

    @Test
    fun missingFileGivesEmptyCalendar() {
        val storage = FileStorage(File(tempDir(), "data.json"))
        assertEquals(AppData(), storage.load())
    }

    @Test
    fun savedDataComesBack() {
        val dir = tempDir()
        val data = AppData(
            jellies = listOf(Jelly(id = "a", title = "러닝", date = LocalDate.of(2026, 9, 28), startMin = 360)),
            seeded = true,
        )
        FileStorage(File(dir, "data.json")).save(data)
        assertEquals(data, FileStorage(File(dir, "data.json")).load())
    }

    @Test
    fun brokenMainFileFallsBackToBackup() {
        val dir = tempDir()
        val file = File(dir, "data.json")
        val storage = FileStorage(file)
        val first = AppData(seeded = true)
        val second = first.copy(jellies = listOf(Jelly(id = "b", title = "빨래")))
        storage.save(first)
        storage.save(second)
        file.writeText("{ not json")
        assertEquals(first, FileStorage(file).load())
        assertTrue(dir.listFiles()!!.any { it.name.startsWith("data.json.broken-") })
    }
}
