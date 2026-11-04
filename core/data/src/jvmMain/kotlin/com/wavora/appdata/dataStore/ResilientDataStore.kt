package com.wavora.appdata.dataStore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.IOException

/**
 * Makes DataStore writes survive transient Windows file-locking.
 *
 * DataStore writes `settings.preferences_pb.tmp` and then renames it over the real file.
 * On Windows that rename throws "Unable to rename ... settings.preferences_pb.tmp" whenever
 * ANOTHER handle on the file is open at that very moment - antivirus / Defender scanning the
 * fresh .tmp, cloud-sync or backup tools, indexers, or a second copy of the app (installed
 * build next to a dev build). It is a short-lived condition, but the IOException used to
 * propagate to whichever coroutine happened to save a setting (typically right after a track
 * ends: last-played, stats, queue state...) and take the whole app down.
 *
 * Now a failed write is retried with a growing delay. If the file stays locked the write is
 * dropped with a log line instead of crashing - losing one setting update is far better than
 * killing playback.
 */
internal class ResilientDataStore(
    private val delegate: DataStore<Preferences>,
    private val maxAttempts: Int = 6,
    private val baseDelayMs: Long = 120L,
) : DataStore<Preferences> {
    override val data: Flow<Preferences> = delegate.data

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        var attempt = 0
        while (true) {
            try {
                return delegate.updateData(transform)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                attempt++
                if (attempt >= maxAttempts) {
                    System.err.println(
                        "DataStore write dropped after $attempt attempts (file locked by another process?): ${e.message}",
                    )
                    return delegate.data.first()
                }
                delay(baseDelayMs * attempt)
            }
        }
    }
}
