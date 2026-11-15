package com.wavora.app

import com.sun.jna.CallbackReference
import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Guid
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import com.wavora.logger.Logger
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.awt.Window
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "WindowsSystemMediaSession"

internal data class WindowsMediaSnapshot(
    val title: String?,
    val artist: String?,
    val album: String?,
    val artworkUri: String?,
    val isPlaying: Boolean,
    val canGoNext: Boolean,
    val canGoPrevious: Boolean,
    /** Total length in milliseconds, 0 when the stream has no known duration (radio, live). */
    val durationMs: Long = 0L,
    /** Current playback position in milliseconds. */
    val positionMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: WindowsMediaRepeat = WindowsMediaRepeat.Off,
)

/** App repeat mode, mapped onto the Windows <c>MediaPlaybackAutoRepeatMode</c> values. */
internal enum class WindowsMediaRepeat(
    val abiValue: Int,
) {
    Off(ABI_REPEAT_NONE),
    All(ABI_REPEAT_ALL),
    One(ABI_REPEAT_TRACK),
    ;

    companion object {
        fun fromAbi(value: Int): WindowsMediaRepeat = entries.firstOrNull { it.abiValue == value } ?: Off
    }
}

internal sealed interface WindowsMediaCommand {
    data object Play : WindowsMediaCommand

    data object Pause : WindowsMediaCommand

    data object Stop : WindowsMediaCommand

    data object Next : WindowsMediaCommand

    data object Previous : WindowsMediaCommand

    /** The user dragged the seek bar in the Windows media flyout. */
    data class SeekTo(val positionMs: Long) : WindowsMediaCommand

    /** The user pressed the shuffle button: this is an absolute value, not a toggle. */
    data class SetShuffle(val enabled: Boolean) : WindowsMediaCommand

    /** The user pressed the repeat button: this is an absolute mode, not a cycle. */
    data class SetRepeat(val mode: WindowsMediaRepeat) : WindowsMediaCommand
}

internal class WindowsSystemMediaSession private constructor(
    private val apartment: WinRtApartment,
    private val controls: ComPtr,
    private val controls2: ComPtr?,
    private val display: ComPtr,
    private val music: ComPtr,
    private val buttonHandler: WinRtDelegate,
    private val registration: Long,
    private val events: List<EventRegistration>,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private var timeline: ComPtr? = null
    private var publishedMetadata: MetadataState? = null
    private var publishedShuffle: Boolean? = null
    private var publishedRepeat: WindowsMediaRepeat? = null
    private var publishedTimeline: TimelineState? = null
    private val artworkExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { task ->
            Thread(task, "Wavora-Media-Artwork").apply { isDaemon = true }
        }
    private val artworkRequest = AtomicInteger()
    private val artworkFiles = ConcurrentHashMap.newKeySet<File>()
    @Volatile private var requestedArtworkUri: String? = null
    private var thumbnailStream: ComPtr? = null
    private var thumbnailReference: ComPtr? = null

    fun update(snapshot: WindowsMediaSnapshot) {
        if (!closed.get()) {
            updateArtwork(snapshot.artworkUri)
            apartment.post {
                if (!closed.get()) {
                    try {
                        publish(snapshot)
                    } catch (error: Exception) {
                        Logger.e(TAG, "Windows media-session update failed: ${error.message}", error)
                    }
                }
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        apartment.finish {
            try {
                events.forEach { event ->
                    try {
                        controls2?.call(event.removeSlot, event.token)?.checkHresult()
                    } catch (error: Exception) {
                        Logger.w(TAG, "Could not detach a Windows media event: ${error.message}")
                    }
                }
                events.forEach { event ->
                    event.handler.detach()
                    WinRtDelegate.forget(event.handler)
                }
                controls.call(SMTC_REMOVE_BUTTON_PRESSED, registration).checkHresult()
                WinRtDelegate.forget(buttonHandler)
                controls.call(SMTC_SET_IS_ENABLED, 0).checkHresult()
                controls.call(SMTC_SET_PLAYBACK_STATUS, PLAYBACK_CLOSED).checkHresult()
                display.call(DISPLAY_CLEAR_ALL).checkHresult()
                display.call(DISPLAY_UPDATE).checkHresult()
                releaseThumbnailObjects()
            } catch (error: Exception) {
                Logger.e(TAG, "Windows media-session shutdown failed: ${error.message}", error)
            } finally {
                releaseThumbnailObjects()
                artworkExecutor.shutdownNow()
                artworkFiles.forEach { it.delete() }
                artworkFiles.clear()
                buttonHandler.detach()
                timeline?.close()
                timeline = null
                music.close()
                display.close()
                controls2?.close()
                controls.close()
            }
        }
    }

    private fun publish(rawSnapshot: WindowsMediaSnapshot) {
        val snapshot = rawSnapshot.withCleanFields()
        val title = snapshot.title?.takeIf(String::isNotBlank)
        if (title == null) {
            controls.call(SMTC_SET_IS_ENABLED, 0).checkHresult()
            controls.call(SMTC_SET_PLAYBACK_STATUS, PLAYBACK_CLOSED).checkHresult()
            display.call(DISPLAY_CLEAR_ALL).checkHresult()
            display.call(DISPLAY_UPDATE).checkHresult()
            releaseThumbnailObjects()
            publishedMetadata = null
            publishedShuffle = null
            publishedRepeat = null
            publishedTimeline = null
            return
        }

        // The position is part of the snapshot, so update() runs every few seconds during playback.
        // Only rewrite the shell metadata when something visible actually changed.
        val metadata =
            MetadataState(
                title = title,
                artist = snapshot.artist,
                album = snapshot.album,
                isPlaying = snapshot.isPlaying,
                canGoNext = snapshot.canGoNext,
                canGoPrevious = snapshot.canGoPrevious,
            )
        if (metadata != publishedMetadata) {
            controls.call(SMTC_SET_IS_ENABLED, 1).checkHresult()
            controls.call(SMTC_SET_IS_PLAY_ENABLED, 1).checkHresult()
            controls.call(SMTC_SET_IS_PAUSE_ENABLED, 1).checkHresult()
            controls.call(SMTC_SET_IS_STOP_ENABLED, 0).checkHresult()
            controls.call(SMTC_SET_IS_NEXT_ENABLED, snapshot.canGoNext.asAbiBoolean()).checkHresult()
            controls.call(SMTC_SET_IS_PREVIOUS_ENABLED, snapshot.canGoPrevious.asAbiBoolean()).checkHresult()
            controls
                .call(
                    SMTC_SET_PLAYBACK_STATUS,
                    if (snapshot.isPlaying) PLAYBACK_PLAYING else PLAYBACK_PAUSED,
                ).checkHresult()

            music.setString(MUSIC_SET_TITLE, title)
            music.setString(MUSIC_SET_ARTIST, snapshot.artist.orEmpty())
            music.setString(MUSIC_SET_ALBUM, snapshot.album.orEmpty())
            display.call(DISPLAY_UPDATE).checkHresult()
            publishedMetadata = metadata
        }

        publishExtendedState(snapshot)
    }

    // Timeline, shuffle and repeat live on ISystemMediaTransportControls2. Windows greys the buttons out
    // until these properties are published, so they are pushed on every change.
    private fun publishExtendedState(snapshot: WindowsMediaSnapshot) {
        val extended = controls2 ?: return

        if (publishedShuffle != snapshot.shuffleEnabled) {
            val result = extended.call(SMTC2_SET_SHUFFLE_ENABLED, snapshot.shuffleEnabled.asAbiBoolean())
            if (result >= 0) {
                publishedShuffle = snapshot.shuffleEnabled
            } else {
                Logger.w(TAG, "Could not publish the shuffle state: 0x${result.toUInt().toString(16)}")
            }
        }

        if (publishedRepeat != snapshot.repeatMode) {
            val result = extended.call(SMTC2_SET_AUTO_REPEAT_MODE, snapshot.repeatMode.abiValue)
            if (result >= 0) {
                publishedRepeat = snapshot.repeatMode
            } else {
                Logger.w(TAG, "Could not publish the repeat mode: 0x${result.toUInt().toString(16)}")
            }
        }

        publishTimeline(extended, snapshot)
    }

    private fun publishTimeline(
        extended: ComPtr,
        snapshot: WindowsMediaSnapshot,
    ) {
        val duration = snapshot.durationMs
        if (duration <= 0L) {
            // Live streams have no known length: leave the seek bar disabled instead of showing 0:00 / 0:00.
            publishedTimeline = null
            return
        }

        val position = snapshot.positionMs.coerceIn(0L, duration)
        val state = TimelineState(durationMs = duration, positionMs = position)
        if (state == publishedTimeline) return

        val properties = timelineProperties()
        properties.setTimeSpan(TIMELINE_SET_START_TIME, 0L)
        properties.setTimeSpan(TIMELINE_SET_END_TIME, duration)
        properties.setTimeSpan(TIMELINE_SET_MIN_SEEK_TIME, 0L)
        properties.setTimeSpan(TIMELINE_SET_MAX_SEEK_TIME, duration)
        properties.setTimeSpan(TIMELINE_SET_POSITION, position)
        extended.call(SMTC2_UPDATE_TIMELINE_PROPERTIES, properties.pointer).checkHresult()
        publishedTimeline = state
    }

    private fun timelineProperties(): ComPtr {
        timeline?.let { return it }
        val result = PointerByReference()
        useHString(TIMELINE_PROPERTIES_CLASS) { className ->
            WinRt.api
                .RoActivateInstance(className ?: error("Windows returned an empty runtime class name"), result)
                .checkHresult()
        }
        val created = ComPtr(result.value ?: error("Windows returned an empty timeline-properties object"))
        timeline = created
        return created
    }

    private fun updateArtwork(uri: String?) {
        if (uri == requestedArtworkUri) return
        requestedArtworkUri = uri
        val requestId = artworkRequest.incrementAndGet()

        if (uri.isNullOrBlank()) {
            apartment.post {
                if (!closed.get() && requestId == artworkRequest.get()) {
                    clearThumbnail()
                }
            }
            return
        }

        apartment.post {
            if (!closed.get() && requestId == artworkRequest.get()) {
                clearThumbnail()
            }
        }

        try {
            artworkExecutor.execute {
                var file: File? = null
                try {
                    file = downloadArtwork(uri)
                    if (closed.get() || requestId != artworkRequest.get()) {
                        file.delete()
                        return@execute
                    }
                    artworkFiles += file
                    apartment.post {
                        if (!closed.get() && requestId == artworkRequest.get()) {
                            try {
                                setThumbnail(file)
                            } catch (error: Exception) {
                                Logger.w(TAG, "Could not set media-session artwork: ${error.message}")
                            }
                        }
                    }
                } catch (error: Exception) {
                    file?.delete()
                    Logger.w(TAG, "Could not load media-session artwork: ${error.message}")
                }
            }
        } catch (error: RejectedExecutionException) {
            Logger.w(TAG, "Media-session artwork loading was rejected: ${error.message}")
        }
    }

    private fun downloadArtwork(uri: String): File {
        val url = URI(uri).toURL()
        require(url.protocol == "https" || url.protocol == "http") { "Artwork URL must use HTTP or HTTPS" }
        val connection =
            (url.openConnection() as? HttpURLConnection)
                ?: error("Artwork URL did not resolve to an HTTP connection")
        connection.connectTimeout = ARTWORK_TIMEOUT_MS
        connection.readTimeout = ARTWORK_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        try {
            connection.connect()
            check(connection.responseCode in 200..299) { "Artwork server returned HTTP ${connection.responseCode}" }
            check(connection.contentType?.startsWith("image/") == true) { "Artwork response is not an image" }
            check(connection.contentLengthLong <= MAX_ARTWORK_BYTES) { "Artwork image is too large" }

            val file = Files.createTempFile("wavora-media-art-", ".img").toFile()
            try {
                connection.inputStream.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            check(total <= MAX_ARTWORK_BYTES) { "Artwork image is too large" }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                return file
            } catch (error: Exception) {
                file.delete()
                throw error
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun setThumbnail(file: File) {
        val (stream, reference) = createThumbnailReference(file)
        try {
            display.call(DISPLAY_SET_THUMBNAIL, reference.pointer).checkHresult()
            display.call(DISPLAY_UPDATE).checkHresult()
        } catch (error: Exception) {
            reference.close()
            stream.close()
            throw error
        }
        releaseThumbnailObjects()
        thumbnailStream = stream
        thumbnailReference = reference
    }

    private fun clearThumbnail() {
        display.call(DISPLAY_SET_THUMBNAIL, null).checkHresult()
        display.call(DISPLAY_UPDATE).checkHresult()
        releaseThumbnailObjects()
    }

    private fun releaseThumbnailObjects() {
        thumbnailReference?.close()
        thumbnailReference = null
        thumbnailStream?.close()
        thumbnailStream = null
    }

    private fun createThumbnailReference(file: File): Pair<ComPtr, ComPtr> {
        val streamResult = PointerByReference()
        Shcore.api
            .CreateRandomAccessStreamOnFile(
                com.sun.jna.WString(file.absolutePath),
                STGM_READ,
                IID_RANDOM_ACCESS_STREAM,
                streamResult,
            ).checkHresult()
        val stream = ComPtr(streamResult.value ?: error("Could not open artwork stream"))

        var factory: ComPtr? = null
        try {
            factory =
                useHString(RANDOM_ACCESS_STREAM_REFERENCE_CLASS) { className ->
                    val result = PointerByReference()
                    WinRt.api
                        .RoGetActivationFactory(
                            className ?: error("Windows returned an empty runtime class name"),
                            IID_RANDOM_ACCESS_STREAM_REFERENCE_STATICS,
                            result,
                        ).checkHresult()
                    ComPtr(result.value ?: error("Windows returned an empty stream-reference factory"))
                }
            val referenceResult = PointerByReference()
            factory.call(STREAM_REFERENCE_CREATE_FROM_STREAM, stream.pointer, referenceResult).checkHresult()
            val reference =
                ComPtr(referenceResult.value ?: error("Could not create the artwork stream reference"))
            return stream to reference
        } catch (error: Exception) {
            stream.close()
            throw error
        } finally {
            factory?.close()
        }
    }

    companion object {
        fun open(
            window: Window,
            onCommand: (WindowsMediaCommand) -> Unit,
        ): WindowsSystemMediaSession? {
            if (!Platform.isWindows()) return null
            for (attempt in 0 until 40) {
                if (window.isDisplayable) break
                Thread.sleep(50)
            }
            check(window.isDisplayable) { "The main window has no native Windows handle yet" }

            val apartment = WinRtApartment()
            return try {
                apartment.start()
                apartment.invoke {
                    createForWindow(Native.getWindowPointer(window), apartment, onCommand)
                }
            } catch (error: Exception) {
                apartment.close()
                Logger.e(TAG, "Windows media controls could not be initialized: ${error.message}", error)
                null
            }
        }

        /**
         * Идентичность окна для панели задач Windows.
         *
         * WHY пусто: раньше здесь вызывался `SetCurrentProcessExplicitAppUserModelID("Wavora")`.
         * Явный AppUserModelID, для которого нет зарегистрированного ярлыка с тем же ID,
         * заставляет Windows создать отдельную группу «Wavora» и нарисовать для неё ПУСТОЙ
         * значок — именно поэтому в панели задач не было иконки приложения. Без этого вызова
         * Windows берёт идентичность от `Aira.exe`, куда jpackage уже вшил иконку и имя.
         */
        fun initializeWindowsAppUserModelId() {
            // Намеренно ничего не делает — см. KDoc выше.
        }

        private fun createForWindow(
            hwnd: Pointer,
            apartment: WinRtApartment,
            onCommand: (WindowsMediaCommand) -> Unit,
        ): WindowsSystemMediaSession {
            val factory =
                useHString(SMTC_CLASS_NAME) { className ->
                    val result = PointerByReference()
                    WinRt.api
                        .RoGetActivationFactory(
                            className ?: error("Windows returned an empty runtime class name"),
                            IID_SMTC_INTEROP,
                            result,
                        ).checkHresult()
                    ComPtr(result.value ?: error("Windows returned an empty activation factory"))
                }

            val controls =
                try {
                    val result = PointerByReference()
                    factory.call(INTEROP_GET_FOR_WINDOW, hwnd, IID_SMTC, result).checkHresult()
                    ComPtr(result.value ?: error("Windows returned an empty transport-controls interface"))
                } finally {
                    factory.close()
                }

            var display: ComPtr? = null
            var music: ComPtr? = null
            var controls2: ComPtr? = null
            var handler: WinRtDelegate? = null
            val events = mutableListOf<EventRegistration>()
            try {
                display = controls.getInterface(SMTC_GET_DISPLAY_UPDATER)
                display.call(DISPLAY_SET_TYPE, MEDIA_TYPE_MUSIC).checkHresult()
                display.setString(DISPLAY_SET_APP_MEDIA_ID, SMTC_APP_MEDIA_ID)
                music = display.getInterface(DISPLAY_GET_MUSIC_PROPERTIES)
                controls.call(SMTC_SET_IS_PLAY_ENABLED, 1).checkHresult()
                controls.call(SMTC_SET_IS_PAUSE_ENABLED, 1).checkHresult()

                handler = buttonPressedDelegate(onCommand)
                val token = LongByReference()
                controls
                    .call(SMTC_ADD_BUTTON_PRESSED, handler.instance, token)
                    .checkHresult()

                controls2 = controls.queryInterface(IID_SMTC2)
                if (controls2 == null) {
                    Logger.w(TAG, "Windows media controls do not expose timeline, shuffle or repeat support")
                } else {
                    registerExtendedEvents(controls2, onCommand, events)
                }

                return WindowsSystemMediaSession(
                    apartment = apartment,
                    controls = controls,
                    controls2 = controls2,
                    display = display,
                    music = music,
                    buttonHandler = handler,
                    registration = token.value,
                    events = events,
                )
            } catch (error: Exception) {
                events.forEach { event ->
                    runCatching { controls2?.call(event.removeSlot, event.token)?.checkHresult() }
                    event.handler.detach()
                    WinRtDelegate.forget(event.handler)
                }
                handler?.detach()
                handler?.let(WinRtDelegate::forget)
                music?.close()
                display?.close()
                controls2?.close()
                controls.close()
                throw error
            }
        }

        private fun registerExtendedEvents(
            controls2: ComPtr,
            onCommand: (WindowsMediaCommand) -> Unit,
            events: MutableList<EventRegistration>,
        ) {
            val request = { addSlot: Int, removeSlot: Int, delegate: WinRtDelegate ->
                runCatching {
                    val token = LongByReference()
                    controls2.call(addSlot, delegate.instance, token).checkHresult()
                    events += EventRegistration(removeSlot = removeSlot, token = token.value, handler = delegate)
                }.onFailure { error ->
                    delegate.detach()
                    WinRtDelegate.forget(delegate)
                    Logger.w(TAG, "Could not subscribe to a Windows media event: ${error.message}")
                }
            }
            request(SMTC2_ADD_POSITION_REQUESTED, SMTC2_REMOVE_POSITION_REQUESTED, positionRequestedDelegate(onCommand))
            request(SMTC2_ADD_SHUFFLE_REQUESTED, SMTC2_REMOVE_SHUFFLE_REQUESTED, shuffleRequestedDelegate(onCommand))
            request(SMTC2_ADD_REPEAT_REQUESTED, SMTC2_REMOVE_REPEAT_REQUESTED, repeatRequestedDelegate(onCommand))
        }
    }
}

private class WinRtApartment {
    private val worker: ExecutorService =
        Executors.newSingleThreadExecutor { task ->
            Thread(task, "Wavora-WinRT").apply { isDaemon = true }
        }
    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    fun start() {
        invoke {
            WinRt.api.RoInitialize(RO_INIT_MULTITHREADED).checkHresult()
            started.set(true)
        }
    }

    fun <T> invoke(action: () -> T): T =
        worker
            .submit(Callable(action))
            .get(10, TimeUnit.SECONDS)

    fun post(action: () -> Unit) {
        worker.execute { action() }
    }

    fun finish(action: () -> Unit) {
        if (!closed.compareAndSet(false, true)) return
        try {
            worker
                .submit(
                    Callable {
                        try {
                            action()
                        } finally {
                            if (started.get()) WinRt.api.RoUninitialize()
                        }
                    },
                ).get(10, TimeUnit.SECONDS)
        } catch (error: Exception) {
            Logger.e(TAG, "Could not stop the Windows Runtime apartment: ${error.message}", error)
        } finally {
            worker.shutdown()
        }
    }

    fun close() = finish {}
}

private class ComPtr(
    private val address: Pointer,
) : AutoCloseable {
    val pointer: Pointer get() = address

    fun call(
        slot: Int,
        vararg arguments: Any?,
    ): Int {
        val vtable = address.getPointer(0)
        val function = Function.getFunction(vtable.getPointer(slot.toLong() * Native.POINTER_SIZE), Function.ALT_CONVENTION)
        return function.invokeInt(arrayOf(address, *arguments))
    }

    fun getInterface(slot: Int): ComPtr {
        val result = PointerByReference()
        call(slot, result).checkHresult()
        return ComPtr(result.value ?: error("COM method $slot returned a null interface"))
    }

    /** IUnknown::QueryInterface; returns null when the object does not implement [iid]. */
    fun queryInterface(iid: Guid.GUID): ComPtr? {
        val result = PointerByReference()
        return if (call(QUERY_INTERFACE, iid, result) >= 0) result.value?.let(::ComPtr) else null
    }

    /** Sets a WinRT TimeSpan property, which travels as a 100-nanosecond tick count. */
    fun setTimeSpan(
        slot: Int,
        milliseconds: Long,
    ) {
        call(slot, milliseconds * TICKS_PER_MILLISECOND).checkHresult()
    }

    fun setString(
        slot: Int,
        value: String,
    ) {
        useHString(value) { string -> call(slot, string).checkHresult() }
    }

    override fun close() {
        call(COM_RELEASE)
    }
}

private class WinRtDelegate(
    private val acceptedIids: Set<String>,
    private val onInvoke: (Pointer) -> Unit,
) {
    private interface QueryInterface : StdCallLibrary.StdCallCallback {
        fun invoke(self: Pointer?, requested: Pointer?, result: Pointer?): Int
    }

    private interface RefCount : StdCallLibrary.StdCallCallback {
        fun invoke(self: Pointer?): Int
    }

    private interface EventInvoke : StdCallLibrary.StdCallCallback {
        fun invoke(self: Pointer?, source: Pointer?, eventArgs: Pointer?): Int
    }

    private val references = AtomicInteger(1)
    private val alive = AtomicBoolean(true)

    init {
        ACTIVE_HANDLERS += this
    }

    private val query: QueryInterface = object : QueryInterface {
        override fun invoke(self: Pointer?, requested: Pointer?, result: Pointer?): Int {
            if (requested == null || result == null) return E_POINTER
            val iid = Guid.GUID(requested).toGuidString()
            if (iid !in acceptedIids) {
                // Answering a marker interface this handler does not implement (INoMarshal, IMarshal, ...) makes
                // the WinRT event registration fail, so unknown IIDs are always rejected. Windows probes those
                // markers on every launch, so the rejection is logged at debug level only.
                if (PROBED_IIDS.add(iid)) {
                    Logger.d(TAG, "Windows media delegate declined an interface: $iid")
                }
                result.setPointer(0, null)
                return E_NOINTERFACE
            }
            result.setPointer(0, instance)
            references.incrementAndGet()
            return S_OK
        }
    }
    private val add: RefCount = object : RefCount {
        override fun invoke(self: Pointer?): Int = references.incrementAndGet()
    }
    private val release: RefCount = object : RefCount {
        override fun invoke(self: Pointer?): Int = references.decrementAndGet().coerceAtLeast(1)
    }
    private val dispatch: EventInvoke = object : EventInvoke {
        override fun invoke(self: Pointer?, source: Pointer?, eventArgs: Pointer?): Int {
            if (!alive.get() || eventArgs == null) return S_OK
            try {
                onInvoke(eventArgs)
            } catch (error: Exception) {
                // A Kotlin exception must never escape into the Windows Runtime callback.
                Logger.w(TAG, "Windows media event handler failed: ${error.message}")
            }
            return S_OK
        }
    }

    private val table: Memory =
        Memory(4L * Native.POINTER_SIZE).apply {
            setPointer(0, CallbackReference.getFunctionPointer(query as com.sun.jna.Callback))
            setPointer(Native.POINTER_SIZE.toLong(), CallbackReference.getFunctionPointer(add as com.sun.jna.Callback))
            setPointer(2L * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(release as com.sun.jna.Callback))
            setPointer(3L * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(dispatch as com.sun.jna.Callback))
        }
    val instance: Memory =
        Memory(Native.POINTER_SIZE.toLong()).apply {
            setPointer(0, table)
        }

    fun detach() {
        alive.set(false)
    }

    companion object {
        private val ACTIVE_HANDLERS = ConcurrentHashMap.newKeySet<WinRtDelegate>()
        private val PROBED_IIDS = ConcurrentHashMap.newKeySet<String>()

        fun forget(delegate: WinRtDelegate) {
            ACTIVE_HANDLERS.remove(delegate)
        }
    }
}

/** A registered Windows Runtime event: the slot that removes it, its token and its callback object. */
private class EventRegistration(
    val removeSlot: Int,
    val token: Long,
    val handler: WinRtDelegate,
)

private data class MetadataState(
    val title: String,
    val artist: String?,
    val album: String?,
    val isPlaying: Boolean,
    val canGoNext: Boolean,
    val canGoPrevious: Boolean,
)

private data class TimelineState(
    val durationMs: Long,
    val positionMs: Long,
)

private fun buttonPressedDelegate(onCommand: (WindowsMediaCommand) -> Unit): WinRtDelegate =
    WinRtDelegate(setOf(IID_BUTTON_HANDLER.toGuidString(), IID_IUNKNOWN.toGuidString(), IID_IAGILE_OBJECT.toGuidString())) { eventArgs ->
        val value = IntByReference()
        if (ComPtr(eventArgs).call(EVENT_ARGS_GET_REQUESTED_VALUE, value) >= 0) {
            mediaCommandForButton(value.value)?.let(onCommand)
        }
    }

private fun positionRequestedDelegate(onCommand: (WindowsMediaCommand) -> Unit): WinRtDelegate =
    WinRtDelegate(acceptedDelegateIids(IID_PLAYBACK_POSITION_REQUESTED_HANDLER)) { eventArgs ->
        val ticks = Memory(TIMESPAN_BYTES)
        if (ComPtr(eventArgs).call(EVENT_ARGS_GET_REQUESTED_VALUE, ticks) >= 0) {
            val position = ticks.getLong(0) / TICKS_PER_MILLISECOND
            if (position >= 0) onCommand(WindowsMediaCommand.SeekTo(position))
        }
    }

private fun shuffleRequestedDelegate(onCommand: (WindowsMediaCommand) -> Unit): WinRtDelegate =
    WinRtDelegate(acceptedDelegateIids(IID_SHUFFLE_ENABLED_REQUESTED_HANDLER)) { eventArgs ->
        val value = IntByReference()
        if (ComPtr(eventArgs).call(EVENT_ARGS_GET_REQUESTED_VALUE, value) >= 0) {
            onCommand(WindowsMediaCommand.SetShuffle(enabled = value.value != 0))
        }
    }

private fun repeatRequestedDelegate(onCommand: (WindowsMediaCommand) -> Unit): WinRtDelegate =
    WinRtDelegate(acceptedDelegateIids(IID_AUTO_REPEAT_MODE_REQUESTED_HANDLER)) { eventArgs ->
        val value = IntByReference()
        if (ComPtr(eventArgs).call(EVENT_ARGS_GET_REQUESTED_VALUE, value) >= 0) {
            onCommand(WindowsMediaCommand.SetRepeat(WindowsMediaRepeat.fromAbi(value.value)))
        }
    }

// Delegates must answer IUnknown (mandatory) and IAgileObject (they are called from whichever thread raises
// the event). Everything else - INoMarshal, IMarshal and friends - has to be refused, otherwise the Windows
// runtime trusts a marker interface this handler does not implement and the registration fails.
private fun acceptedDelegateIids(delegate: Guid.GUID): Set<String> =
    setOf(delegate.toGuidString(), IID_IUNKNOWN.toGuidString(), IID_IAGILE_OBJECT.toGuidString())

private fun mediaCommandForButton(button: Int): WindowsMediaCommand? =
    when (button) {
        BUTTON_PLAY -> WindowsMediaCommand.Play
        BUTTON_PAUSE -> WindowsMediaCommand.Pause
        BUTTON_STOP -> WindowsMediaCommand.Stop
        BUTTON_NEXT -> WindowsMediaCommand.Next
        BUTTON_PREVIOUS -> WindowsMediaCommand.Previous
        else -> null
    }

private object WinRt {
    interface Api : StdCallLibrary {
        fun RoInitialize(mode: Int): Int

        fun RoUninitialize()

        fun RoGetActivationFactory(
            className: Pointer,
            iid: Guid.GUID,
            factory: PointerByReference,
        ): Int

        fun RoActivateInstance(
            activatableClassId: Pointer,
            instance: PointerByReference,
        ): Int

        fun WindowsCreateString(
            source: WString,
            length: Int,
            value: PointerByReference,
        ): Int

        fun WindowsDeleteString(value: Pointer?): Int
    }

    val api: Api by lazy { Native.load("combase", Api::class.java) }
}

private object Shcore {
    interface Api : StdCallLibrary {
        fun CreateRandomAccessStreamOnFile(
            filePath: WString,
            accessMode: Int,
            iid: Guid.GUID,
            stream: PointerByReference,
        ): Int
    }

    val api: Api by lazy { Native.load("shcore", Api::class.java) }
}

private inline fun <T> useHString(
    value: String,
    operation: (Pointer?) -> T,
): T {
    val result = PointerByReference()
    WinRt.api.WindowsCreateString(WString(value), value.length, result).checkHresult()
    try {
        return operation(result.value)
    } finally {
        WinRt.api.WindowsDeleteString(result.value)
    }
}

private fun Boolean.asAbiBoolean(): Int = if (this) 1 else 0

private fun Int.checkHresult() {
    check(this >= 0) { "WinRT operation failed with HRESULT 0x${toUInt().toString(16)}" }
}

private const val COM_RELEASE = 2
private const val QUERY_INTERFACE = 0
private const val INTEROP_GET_FOR_WINDOW = 6
private const val SMTC_SET_PLAYBACK_STATUS = 7
private const val SMTC_GET_DISPLAY_UPDATER = 8
private const val SMTC_SET_IS_ENABLED = 11
private const val SMTC_SET_IS_PLAY_ENABLED = 13
private const val SMTC_SET_IS_STOP_ENABLED = 15
private const val SMTC_SET_IS_PAUSE_ENABLED = 17
private const val SMTC_SET_IS_PREVIOUS_ENABLED = 25
private const val SMTC_SET_IS_NEXT_ENABLED = 27
private const val SMTC_ADD_BUTTON_PRESSED = 32
private const val SMTC_REMOVE_BUTTON_PRESSED = 33
private const val SMTC2_SET_AUTO_REPEAT_MODE = 7
private const val SMTC2_SET_SHUFFLE_ENABLED = 9
private const val SMTC2_UPDATE_TIMELINE_PROPERTIES = 12
private const val SMTC2_ADD_POSITION_REQUESTED = 13
private const val SMTC2_REMOVE_POSITION_REQUESTED = 14
private const val SMTC2_ADD_SHUFFLE_REQUESTED = 17
private const val SMTC2_REMOVE_SHUFFLE_REQUESTED = 18
private const val SMTC2_ADD_REPEAT_REQUESTED = 19
private const val SMTC2_REMOVE_REPEAT_REQUESTED = 20
private const val TIMELINE_SET_START_TIME = 7
private const val TIMELINE_SET_END_TIME = 9
private const val TIMELINE_SET_MIN_SEEK_TIME = 11
private const val TIMELINE_SET_MAX_SEEK_TIME = 13
private const val TIMELINE_SET_POSITION = 15
private const val DISPLAY_SET_TYPE = 7
private const val DISPLAY_GET_MUSIC_PROPERTIES = 12
private const val DISPLAY_CLEAR_ALL = 16
private const val DISPLAY_UPDATE = 17
private const val DISPLAY_SET_APP_MEDIA_ID = 9
private const val DISPLAY_SET_THUMBNAIL = 11
private const val MUSIC_SET_TITLE = 7
private const val MUSIC_SET_ARTIST = 9
private const val MUSIC_SET_ALBUM = 11
private const val EVENT_ARGS_GET_REQUESTED_VALUE = 6
private const val RO_INIT_MULTITHREADED = 1
private const val MEDIA_TYPE_MUSIC = 1
private const val PLAYBACK_CLOSED = 0
private const val PLAYBACK_PLAYING = 3
private const val PLAYBACK_PAUSED = 4
private const val BUTTON_PLAY = 0
private const val BUTTON_PAUSE = 1
private const val BUTTON_STOP = 2
private const val BUTTON_NEXT = 6
private const val BUTTON_PREVIOUS = 7
private const val ABI_REPEAT_NONE = 0
private const val ABI_REPEAT_TRACK = 1
private const val ABI_REPEAT_ALL = 2
private const val S_OK = 0
private const val E_NOINTERFACE = 0x80004002.toInt()
private const val E_POINTER = 0x80004003.toInt()
private const val SMTC_CLASS_NAME = "Windows.Media.SystemMediaTransportControls"
private const val TIMELINE_PROPERTIES_CLASS =
    "Windows.Media.SystemMediaTransportControlsTimelineProperties"
private const val STGM_READ = 0
/// Идентификатор приложения для медиа-панели Windows (не AppUserModelID процесса —
/// тот больше не задаём явно, иначе панель задач рисует пустую иконку).
private const val SMTC_APP_MEDIA_ID = "Aira"
private const val STREAM_REFERENCE_CREATE_FROM_STREAM = 8
private const val MAX_ARTWORK_BYTES = 8L * 1024 * 1024
private const val ARTWORK_TIMEOUT_MS = 5_000
private const val TICKS_PER_MILLISECOND = 10_000L
private const val TIMESPAN_BYTES = 8L
private const val RANDOM_ACCESS_STREAM_REFERENCE_CLASS = "Windows.Storage.Streams.RandomAccessStreamReference"

private val IID_SMTC_INTEROP = Guid.GUID.fromString("{ddb0472d-c911-4a1f-86d9-dc3d71a95f5a}")
private val IID_SMTC = Guid.GUID.fromString("{99fa3ff4-1742-42a6-902e-087d41f965ec}")
private val IID_SMTC2 = Guid.GUID.fromString("{EA98D2F6-7F3C-4AF2-A586-72889808EFB1}")
private val IID_BUTTON_HANDLER = Guid.GUID.fromString("{0557e996-7b23-5bae-aa81-ea0d671143a4}")
private val IID_IUNKNOWN = Guid.GUID.fromString("{00000000-0000-0000-C000-000000000046}")
private val IID_IAGILE_OBJECT = Guid.GUID.fromString("{94EA2B94-E9CC-49E0-C0FF-EE64CA8F5B90}")
private val IID_RANDOM_ACCESS_STREAM = Guid.GUID.fromString("{905A0FE1-BC53-11DF-8C49-001E4FC686DA}")
private val IID_RANDOM_ACCESS_STREAM_REFERENCE_STATICS =
    Guid.GUID.fromString("{857309DC-3FBF-4E7D-986F-EF3B1A07A964}")

// Delegate (event-handler) interfaces Windows asks for once the matching event is raised. They are the
// parameterized TypedEventHandler<SystemMediaTransportControls, ...EventArgs> identities reported by the
// runtime itself for this Windows build.
private val IID_PLAYBACK_POSITION_REQUESTED_HANDLER =
    Guid.GUID.fromString("{44E34F15-BDC0-50A7-ACE4-39E91FB753F1}")
private val IID_SHUFFLE_ENABLED_REQUESTED_HANDLER =
    Guid.GUID.fromString("{17ECEA80-27E4-5DAE-ABB4-C858AD1C5307}")
private val IID_AUTO_REPEAT_MODE_REQUESTED_HANDLER =
    Guid.GUID.fromString("{A6214BDE-02D5-55B3-AB0D-C6031BE70DA1}")

// Title / artist / album must be three independent, non-repeating fields: no play/view counters in the
// artist line, no repeated artists, and no album that merely repeats the title or the artist.
private val mediaCounterRegex =
    Regex(
        """(?i)(\d[\d\s.,]*\s*(k|m|b|тыс|млн|млрд|к)?\.?\s*(views?|plays?|listens?|просмотр\p{L}*|прослушива\p{L}*|подписчик\p{L}*|subscribers?))""",
    )

private fun String?.cleanMediaText(): String = this.orEmpty().replace(Regex("\\s+"), " ").trim()

internal fun WindowsMediaSnapshot.withCleanFields(): WindowsMediaSnapshot {
    val cleanTitle = title.cleanMediaText()
    val cleanArtist =
        artist.cleanMediaText()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() && !mediaCounterRegex.containsMatchIn(it) }
            .distinctBy { it.lowercase() }
            .joinToString(", ")
    val cleanAlbum =
        album.cleanMediaText().takeUnless {
            it.isBlank() || it.equals("Album", ignoreCase = true) ||
                it.equals(cleanTitle, ignoreCase = true) || it.equals(cleanArtist, ignoreCase = true)
        }
    return copy(
        title = cleanTitle.takeIf { it.isNotBlank() },
        artist = cleanArtist.takeIf { it.isNotBlank() },
        album = cleanAlbum,
    )
}