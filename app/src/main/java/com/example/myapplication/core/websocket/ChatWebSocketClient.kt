@file:Suppress("DEPRECATION", "NewApi", "UNRESOLVED_REFERENCE")

package com.example.myapplication.core.websocket

import android.util.Log
import com.example.myapplication.BuildConfig
import kotlinx.coroutines.*
// import kotlinx.coroutines.flow.BufferOverflow  // ⭐ IDE缓存问题，编译时可用
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    private val _messages = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.flow.BufferOverflow.SUSPEND  // ⭐ 使用全限定名
    )
    val messages = _messages.asSharedFlow()

    @Volatile
    private var isConnected = false
    
    @Volatile
    private var isConnecting = false  // ⭐ 新增：标记正在连接中

    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5

    private val backoffTimes = listOf(1000L, 2000L, 4000L, 8000L, 16000L)

    private var currentLat: Double? = null
    private var currentLng: Double? = null

    private var sessionId: String? = null
    private var token: String? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun connect(sessionId: String, token: String) {
        // ⭐ 关键修复：防止重复连接
        if (isConnecting) {
            Log.w("WebSocket", "⚠️ 正在连接中，跳过重复连接请求")
            return
        }
        
        // ⭐ 如果已经连接成功，不需要重新连接
        if (isConnected && this.sessionId == sessionId) {
            Log.d("WebSocket", "✅ 已连接到相同会话，跳过连接")
            return
        }
        
        this.sessionId = sessionId
        this.token = token
        isConnecting = true  // ⭐ 标记开始连接

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
                isConnecting = false  // ⭐ 连接成功，清除标记
                reconnectAttempts = 0
                Log.d("WebSocket", "✅ 连接建立成功")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                super.onMessage(webSocket, text)
                Log.d("WebSocket", "Received message (${text.length} chars)")
                
                scope.launch {
                    _messages.emit(text)
                    Log.d("WebSocket", "Message emitted to SharedFlow")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                super.onFailure(webSocket, t, response)
                isConnected = false
                isConnecting = false  // ⭐ 连接失败，清除标记
                
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

                // ⭐ 优化：增加重连间隔，避免频繁重连导致后端刷屏
                if (reconnectAttempts < maxReconnectAttempts) {
                    val delayTime = backoffTimes.getOrElse(reconnectAttempts) { 16000L }
                    reconnectAttempts++
                    Log.d("WebSocket", "⏳ 将在 ${delayTime}ms 后重连 (${reconnectAttempts}/${maxReconnectAttempts})")

                    scope.launch {
                        delay(delayTime)
                        if (isActive) {
                            Log.d("WebSocket", "🔄 开始重连...")
                            sessionId?.let { sid ->
                                token?.let { tk ->
                                    connect(sid, tk)
                                }
                            }
                        }
                    }
                } else {
                    Log.e("WebSocket", "❌ 重连失败，已达最大尝试次数")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosed(webSocket, code, reason)
                isConnected = false
                isConnecting = false  // ⭐ 连接关闭，清除标记
                Log.d("WebSocket", "🔌 连接关闭: code=$code, reason=$reason")
                
                // ⭐ 优化：异常关闭时增加重连间隔，避免频繁重连
                if (code != 1000 && code != 1001) {
                    Log.w("WebSocket", "⚠️ 异常关闭 code=$code，准备重连")
                    if (reconnectAttempts < maxReconnectAttempts) {
                        reconnectAttempts++
                        scope.launch {
                            // ⭐ 增加延迟到 5 秒，避免每秒重连
                            delay(5000)
                            if (isActive) {
                                sessionId?.let { sid ->
                                    token?.let { tk ->
                                        Log.d("WebSocket", "🔄 异常关闭后重连: ${reconnectAttempts}/${maxReconnectAttempts}")
                                        connect(sid, tk)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Log.d("WebSocket", "✅ 正常关闭，不重连")
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
        Log.d("WebSocket", "🔌 主动断开 WebSocket 连接")
        // ⭐ 先取消作用域，停止所有重连任务
        scope.cancel()
        // ⭐ 关闭 WebSocket 连接
        webSocket?.close(1000, "正常关闭")
        // ⭐ 清理资源
        webSocket = null
        isConnected = false
        isConnecting = false  // ⭐ 清除连接中状态
        // ⭐ 关键修复：重置重连计数器，避免影响下次连接
        reconnectAttempts = 0
        sessionId = null
        token = null
        Log.d("WebSocket", "✅ WebSocket 已完全断开，资源已清理")
    }

    fun isConnected(): Boolean {
        return isConnected && webSocket != null
    }
}