package com.my.kizzy.gateway

import com.wavora.logger.Logger
import com.my.kizzy.gateway.entities.Heartbeat
import com.my.kizzy.gateway.entities.Identify.Companion.toIdentifyPayload
import com.my.kizzy.gateway.entities.Payload
import com.my.kizzy.gateway.entities.Ready
import com.my.kizzy.gateway.entities.Resume
import com.my.kizzy.gateway.entities.op.OpCode
import com.my.kizzy.gateway.entities.op.OpCode.DISPATCH
import com.my.kizzy.gateway.entities.op.OpCode.HEARTBEAT
import com.my.kizzy.gateway.entities.op.OpCode.HEARTBEAT_ACK
import com.my.kizzy.gateway.entities.op.OpCode.HELLO
import com.my.kizzy.gateway.entities.op.OpCode.IDENTIFY
import com.my.kizzy.gateway.entities.op.OpCode.INVALID_SESSION
import com.my.kizzy.gateway.entities.op.OpCode.PRESENCE_UPDATE
import com.my.kizzy.gateway.entities.op.OpCode.RECONNECT
import com.my.kizzy.gateway.entities.op.OpCode.RESUME
import com.my.kizzy.gateway.entities.presence.Presence
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Modified by Zion Huang
 */
private const val TAG = "DiscordWebSocket"

open class DiscordWebSocket(
    private val token: String,
    private val os: String = "Android",
    private val browser: String = "Discord Android",
    private val device: String = "Generic Android Device",
) : CoroutineScope {
    private val gatewayUrl = "wss://gateway.discord.gg/?v=9&encoding=json"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sendMutex = kotlinx.coroutines.sync.Mutex()
    private var websocket: DefaultClientWebSocketSession? = null
    private var sequence = 0
    private var sessionId: String? = null
    private var heartbeatInterval = 0L
    private var resumeGatewayUrl: String? = null
    private var heartbeatJob: Job? = null
    private var connected = false
    private var client: HttpClient =
        HttpClient {
            install(WebSockets)
        }
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private var connectionJob: Job? = null

    override val coroutineContext: CoroutineContext = scope.coroutineContext

    fun connect() {
        if (connectionJob?.isActive == true) {
            Logger.i(TAG, "Gateway connection loop is already running.")
            return
        }
        connectionJob =
            scope.launch {
                var reconnectDelay = INITIAL_RECONNECT_DELAY
                while (isActive) {
                    try {
                        val url = resumeGatewayUrl ?: gatewayUrl
                        Logger.i(TAG, "Connecting to Discord Gateway at $url")
                        val session =
                            client.webSocketSession(url) {
                                header("User-Agent", "Discord-Android/314013;RNA")
                                header("Accept-Language", "en-US")
                                header("Cache-Control", "no-cache")
                                header("Pragma", "no-cache")
                            }
                        websocket = session
                        connected = false
                        Logger.i(TAG, "Discord Gateway WebSocket established.")
                        session.incoming.receiveAsFlow().collect { frame ->
                            if (frame is Frame.Text) {
                                onMessage(json.decodeFromString(frame.readText()))
                            }
                        }
                        reconnectDelay = INITIAL_RECONNECT_DELAY
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Logger.e(TAG, "Gateway connection error: ${e.stackTraceToString()}")
                    } finally {
                        heartbeatJob?.cancel()
                        heartbeatJob = null
                        connected = false
                        websocket = null
                    }
                    if (isActive) {
                        delay(reconnectDelay)
                        reconnectDelay = (reconnectDelay * 2).coerceAtMost(MAX_RECONNECT_DELAY)
                    }
                }
            }
    }

    private suspend fun onMessage(payload: Payload) {
        Logger.i(TAG, "Gateway received: op=${payload.op}, seq=${payload.s}, event=${payload.t}")
        payload.s?.let {
            sequence = it
        }
        when (payload.op) {
            DISPATCH -> {
                payload.handleDispatch()
            }
            HEARTBEAT -> {
                sendHeartBeat()
            }
            HEARTBEAT_ACK -> {}
            RECONNECT -> {
                reconnectWebSocket()
            }
            INVALID_SESSION -> {
                handleInvalidSession()
            }
            HELLO -> {
                payload.handleHello()
            }
            else -> {}
        }
    }

    open fun Payload.handleDispatch() {
        when (this.t.toString()) {
            "READY" -> {
                val ready = json.decodeFromJsonElement<Ready>(this.d!!)
                sessionId = ready.sessionId
                resumeGatewayUrl = ready.resumeGatewayUrl + "/?v=9&encoding=json"
                Logger.i(TAG, "Gateway READY: resume_gateway_url updated to $resumeGatewayUrl, session_id updated to $sessionId")
                connected = true
                return
            }

            "RESUMED" -> {
                Logger.i(TAG, "Gateway: Session Resumed")
                connected = true
            }

            else -> {}
        }
    }

    private suspend inline fun handleInvalidSession() {
        Logger.w(TAG, "Gateway: Session invalidated. Resetting resume state and identifying again.")
        sessionId = null
        resumeGatewayUrl = null
        sequence = 0
        delay(150)
        sendIdentify()
    }

    private suspend inline fun Payload.handleHello() {
        if (sequence > 0 && !sessionId.isNullOrBlank()) {
            sendResume()
        } else {
            sendIdentify()
        }
        heartbeatInterval = json.decodeFromJsonElement<Heartbeat>(this.d!!).heartbeatInterval
        Logger.i(TAG, "Gateway: Setting heartbeatInterval=$heartbeatInterval")
        startHeartbeatJob(heartbeatInterval.coerceAtLeast(1L))
    }

    private suspend fun sendHeartBeat() {
        Logger.i(TAG, "Gateway: Sending $HEARTBEAT with seq: $sequence")
        send(
            op = HEARTBEAT,
            d = if (sequence == 0) "null" else sequence.toString(),
        )
    }

    private suspend inline fun reconnectWebSocket() {
        websocket?.close(
            CloseReason(
                code = 4000,
                message = "Attempting to reconnect",
            ),
        )
    }

    private suspend fun sendIdentify() {
        Logger.i(TAG, "Gateway: Sending $IDENTIFY")
        send(
            op = IDENTIFY,
            d =
                token.toIdentifyPayload(
                    os = os,
                    browser = browser,
                    device = device,
                ),
        )
    }

    private suspend fun sendResume() {
        Logger.i(TAG, "Gateway: Sending $RESUME")
        send(
            op = RESUME,
            d =
                Resume(
                    seq = sequence,
                    sessionId = sessionId,
                    token = token,
                ),
        )
    }

    private fun startHeartbeatJob(interval: Long) {
        heartbeatJob?.cancel()
        heartbeatJob =
            launch {
                while (isActive) {
                    delay(interval)
                    sendHeartBeat()
                }
            }
    }

    // Returns true only after Discord has sent READY (sessionId is set), meaning
    // IDENTIFY has been processed and the session is authenticated. Sending presence
    // before this causes Discord to silently ignore it, which is why Rich Presence
    // was not showing even when the token was correct.
    private fun isSocketConnectedToAccount(): Boolean =
        connected && websocket?.isActive == true && !sessionId.isNullOrBlank()

    fun isWebSocketConnected(): Boolean = isSocketConnectedToAccount()

    private suspend inline fun <reified T> send(
        op: OpCode,
        d: T?,
    ) {
        sendMutex.lock()
        try {
            val session = websocket
            if (session?.isActive == true) {
                val payload =
                    json.encodeToString(
                        Payload(
                            op = op,
                            d = json.encodeToJsonElement(d),
                        ),
                    )
                if (op == IDENTIFY) {
                    Logger.i(TAG, "Gateway sending payload: [REDACTED IDENTIFY PAYLOAD]")
                } else {
                    Logger.i(TAG, "Gateway sending payload: $payload")
                }
                session.send(Frame.Text(payload))
            }
        } finally {
            sendMutex.unlock()
        }
    }

    fun close() {
        connectionJob?.cancel()
        heartbeatJob?.cancel()
        heartbeatJob = null
        scope.cancel()
        resumeGatewayUrl = null
        sessionId = null
        connected = false
        runBlocking {
            websocket?.close()
            client.close()
            Logger.e(TAG, "Gateway: Connection to gateway closed")
        }
    }

    suspend fun sendActivity(presence: Presence) {
        // TODO : Figure out a better way to wait for socket to be connected to account
        if (!isSocketConnectedToAccount()) {
            connect()
            kotlinx.coroutines.withTimeout(15.seconds) {
                while (!isSocketConnectedToAccount()) {
                    delay(50.milliseconds)
                }
            }
        }
        Logger.i(TAG, "Gateway: Sending $PRESENCE_UPDATE")
        send(
            op = PRESENCE_UPDATE,
            d = presence,
        )
    }

    companion object {
        private val INITIAL_RECONNECT_DELAY = 1.seconds
        private val MAX_RECONNECT_DELAY = 60.seconds
    }
}