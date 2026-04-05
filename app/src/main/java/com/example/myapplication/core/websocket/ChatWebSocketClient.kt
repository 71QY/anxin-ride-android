
package com.example.myapplication.core.websocket

import android.util.Log
import com.example.myapplication.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatWebSocketClient @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    private val _messages = Channel<String>(Channel.UNLIMITED)
    val messages = _messages.receiveAsFlow()

    @Volatile
    private var isConnected = false

    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5

    private val backoffTimes = listOf(1000L, 2000L, 4000L, 8000L, 16000L)

    private var currentLat: Double? = null
    private var currentLng: Double? = null

    private var sessionId: String? = null
    private var token: String? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun connect(sessionId: String, token: String) {
        this.sessionId = sessionId
        this.token = token

        // 取消旧的 WebSocket 连接
        webSocket?.cancel()
        webSocket = null
        isConnected = false

        // ⭐ 修复：正确处理 URL 拼接，避免重复的查询参数分隔符
        val baseUrl = BuildConfig.WEBSOCKET_URL.trimEnd('/', '?')
        val url = "$baseUrl?token=$token&sessionId=$sessionId"

        // ⭐ 修复：添加必要的请求头，确保与后端 Java 11 的 WebSocket 服务器兼容
        val request = Request.Builder()
            .url(url)
            .addHeader("Sec-WebSocket-Protocol", "chat")
            .addHeader("User-Agent", "MyApplication/1.0 (Android; Chat Client)")
            .addHeader("X-Session-ID", sessionId)
            .build()

        Log.d("WebSocket", "Attempting connection")
        Log.d("WebSocket", "Token: [HIDDEN]")
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                super.onOpen(webSocket, response)
                isConnected = true
                reconnectAttempts = 0
                Log.d("WebSocket", "Connection established")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                super.onMessage(webSocket, text)
                Log.d("WebSocket", "Received message (${text.length} chars)")
                
                val result = _messages.trySend(text)
                if (result.isFailure) {
                    Log.e("WebSocket", "Failed to send message to Channel: ${result.exceptionOrNull()?.message}")
                } else {
                    Log.d("WebSocket", "Message sent to Channel")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                super.onFailure(webSocket, t, response)
                isConnected = false
                
                val errorMsg = buildString {
                    append("Connection failed: ${t.message}")
                    append("\nException type: ${t.javaClass.simpleName}")
                    response?.let {
                        append("\nHTTP status code: ${it.code}")
                        append("\nResponse headers: ${it.headers}")
                    }
                    append("\nStack trace: ${t.stackTraceToString()}")
                }
                Log.e("WebSocket", errorMsg, t)
                
                when (t) {
                    is java.net.SocketTimeoutException -> {
                        Log.e("WebSocket", "Connection timeout, check: 1.Backend service running 2.Firewall settings 3.Network connectivity")
                    }
                    is java.net.ConnectException -> {
                        Log.e("WebSocket", "Cannot connect to server, verify backend IP and port")
                    }
                    is javax.net.ssl.SSLHandshakeException -> {
                        Log.e("WebSocket", "SSL handshake failed, check if HTTPS is required instead of WS")
                    }
                    else -> {
                        Log.e("WebSocket", "Unknown error, see detailed log above")
                    }
                }

                if (reconnectAttempts < maxReconnectAttempts) {
                    val delayTime = backoffTimes.getOrElse(reconnectAttempts) { 16000L }
                    reconnectAttempts++
                    Log.d("WebSocket", "Reconnecting in ${delayTime}ms ${reconnectAttempts}/${maxReconnectAttempts}")

                    scope.launch {
                        delay(delayTime)
                        if (isActive) {
                            Log.d("WebSocket", "Starting reconnection...")
                            sessionId?.let { sid ->
                                token?.let { tk ->
                                    connect(sid, tk)
                                }
                            }
                        }
                    }
                } else {
                    Log.e("WebSocket", "Reconnection failed, max attempts reached")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosed(webSocket, code, reason)
                isConnected = false
                Log.d("WebSocket", "Connection closed: code=$code, reason=$reason")
                
                // 异常关闭时尝试重连
                if (code != 1000 && code != 1001) {
                    Log.w("WebSocket", "Abnormal close code=$code, attempting reconnection")
                    if (reconnectAttempts < maxReconnectAttempts) {
                        reconnectAttempts++
                        scope.launch {
                            delay(1000)
                            if (isActive) {
                                sessionId?.let { sid ->
                                    token?.let { tk ->
                                        Log.d("WebSocket", "Reconnecting after abnormal close: ${reconnectAttempts}/${maxReconnectAttempts}")
                                        connect(sid, tk)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Log.d("WebSocket", "Normal close, no reconnection")
                }
            }
        })
    }

    fun updateLocation(lat: Double, lng: Double) {
        currentLat = lat
        currentLng = lng
    }

    fun sendRaw(json: String) {
        if (!isConnected) {
            Log.e("WebSocket", "Send failed: not connected")
            return
        }

        try {
            val sent = webSocket?.send(json)
            if (sent == false) {
                Log.e("WebSocket", "Send failed: buffer full")
            } else {
                Log.d("WebSocket", "Sending message")
            }
        } catch (e: Exception) {
            Log.e("WebSocket", "Send exception: ${e.message}", e)
            isConnected = false
        }
    }

    fun disconnect() {
        scope.cancel()
        webSocket?.close(1000, "正常关闭")
        webSocket = null
        isConnected = false
        Log.d("WebSocket", "Disconnected")
    }

    fun isConnected(): Boolean {
        return isConnected && webSocket != null
    }
}