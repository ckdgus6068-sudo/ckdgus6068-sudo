package io.github.ckdgus6068.jellycalendar

import android.app.Application
import io.github.ckdgus6068.jellycalendar.core.FileStorage
import io.github.ckdgus6068.jellycalendar.core.JellyStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns the calendar for the lifetime of the process and writes every change to disk. */
class JellyApplication : Application() {
    lateinit var store: JellyStore
        private set
    private lateinit var storage: FileStorage
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        storage = FileStorage(File(filesDir, "jelly-calendar.json"))
        store = JellyStore(storage.load())
        scope.launch {
            store.data.drop(1).collectLatest { data ->
                // Coalesce bursts of changes (dragging, resizing) into one write.
                delay(400)
                withContext(Dispatchers.IO) { storage.save(data) }
            }
        }
    }

    /** Writes immediately, e.g. when the app goes to the background. */
    fun flush() {
        runCatching { storage.save(store.current) }
    }
}
