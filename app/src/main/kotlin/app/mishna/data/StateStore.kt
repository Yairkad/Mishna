package app.mishna.data

import android.content.Context
import app.mishna.core.state.AppState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Keeps [AppState] in one JSON file. Writes go to a temp file first so a crash never leaves half a file. */
class StateStore(context: Context) {
    private val file = File(context.filesDir, "state.json")
    private val lock = Mutex()
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppState> = _state

    private fun read(): AppState =
        runCatching { AppState.decode(file.readText()) }.getOrElse { AppState() }

    suspend fun update(change: (AppState) -> AppState) = lock.withLock {
        val next = change(_state.value)
        if (next == _state.value) return@withLock
        _state.value = next
        withContext(Dispatchers.IO) {
            val tmp = File(file.parentFile, "state.json.tmp")
            tmp.writeText(next.encode())
            tmp.renameTo(file)
        }
    }

    companion object {
        @Volatile private var instance: StateStore? = null
        fun get(context: Context): StateStore = instance ?: synchronized(this) {
            instance ?: StateStore(context.applicationContext).also { instance = it }
        }
    }
}
