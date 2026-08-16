package dev.trove.app.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * App-wide snackbar bus. Events survive screen changes (replay buffer), so
 * an "Undo" snackbar posted from the reader still appears when the user
 * lands back on the inbox.
 */
object SnackbarBus {
    data class Event(
        val message: String,
        val actionLabel: String? = null,
        val action: () -> Unit = {},
    )

    private val _events = MutableSharedFlow<Event>(replay = 1, extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    fun post(event: Event) {
        _events.tryEmit(event)
    }

    fun clear() {
        _events.resetReplayCache()
    }
}
