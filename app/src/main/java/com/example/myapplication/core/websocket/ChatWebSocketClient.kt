
package com.example.myapplication.core.websocket

import android.util.Log
import com.example.myapplication.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import okhttp3.*
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

        Log.d("WebSocket", "尝试连接：$url")
        Log.d("WebSocket", "Token: ${token.take(20)}...")
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                super.onOpen(webSocket, response)
                isConnected = true
                reconnectAttempts = 0
                Log.d("WebSocket", "连接成功：$url")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                super.onMessage(webSocket, text)
                val result = _messages.trySend(text)
                if (result.isFailure) {
                    Log.e("WebSocket", "消息发送到 Channel 失败：${result.exceptionOrNull()?.message}")
                }
                Log.d("WebSocket", "收到消息：$text")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                super.onFailure(webSocket, t, response)
                isConnected = false
                
                // ⭐ 修复：详细的错误日志，帮助诊断 Java 11 vs Java 17 兼容性问题
                val errorMsg = buildString {
                    append("连接失败：${t.message}")
                    append("\n异常类型：${t.javaClass.simpleName}")
                    response?.let {
                        append("\nHTTP 状态码：${it.code}")
                        append("\n响应头：${it.headers}")
                    }
                    append("\n堆栈跟踪：${t.stackTraceToString()}")
                }
                Log.e("WebSocket", errorMsg, t)
                
                // ⭐ 新增：根据异常类型提供具体的解决建议
                when (t) {
                    is java.net.SocketTimeoutException -> {
                        Log.e("WebSocket", "⚠️ 连接超时，请检查：1.后端服务是否运行 2.防火墙设置 3.网络连通性")
                    }
                    is java.net.ConnectException -> {
                        Log.e("WebSocket", "⚠️ 无法连接到服务器，请确认后端 IP 和端口是否正确")
                    }
                    is javax.net.ssl.SSLHandshakeException -> {
                        Log.e("WebSocket", "⚠️ SSL 握手失败，检查是否需要 HTTPS 而不是 WS")
                    }
                    else -> {
                        Log.e("WebSocket", "⚠️ 未知错误，查看上方详细日志")
                    }
                }

                if (reconnectAttempts < maxReconnectAttempts) {
                    val delayTime = backoffTimes.getOrElse(reconnectAttempts) { 16000L }
                    reconnectAttempts++
                    Log.d("WebSocket", "${delayTime}ms 后尝试重连 ${reconnectAttempts}/${maxReconnectAttempts}")

                    scope.launch {
                        delay(delayTime)
                        if (isActive) {
                            Log.d("WebSocket", "开始重连...")
                            sessionId?.let { sid ->
                                token?.let { tk ->
                                    connect(sid, tk)
                                }
                            }
                        }
                    }
                } else {
                    Log.e("WebSocket", "重连失败，已达到最大次数")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosed(webSocket, code, reason)
                isConnected = false
                Log.d("WebSocket", "连接关闭：code=$code, reason=$reason")
                
                // 异常关闭时尝试重连
                if (code != 1000 && code != 1001) {
                    Log.w("WebSocket", "异常关闭 code=$code，尝试重连")
                    if (reconnectAttempts < maxReconnectAttempts) {
                        reconnectAttempts++
                        scope.launch {
                            delay(1000)
                            if (isActive) {
                                sessionId?.let { sid ->
                                    token?.let { tk ->
                                        Log.d("WebSocket", "异常关闭后重连：${reconnectAttempts}/${maxReconnectAttempts}")
                                        connect(sid, tk)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Log.d("WebSocket", "正常关闭，不重连")
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
            Log.e("WebSocket", "发送失败：未连接")
            return
        }

        try {
            val sent = webSocket?.send(json)
            if (sent == false) {
                Log.e("WebSocket", "发送失败：缓冲区已满")
            } else {
                Log.d("WebSocket", "发送消息：$json")
            }
        } catch (e: Exception) {
            Log.e("WebSocket", "发送异常：${e.message}", e)
            // ⭐ 修改：捕获异常，避免崩溃
            isConnected = false
        }
    }

    fun disconnect() {
        scope.cancel()
        webSocket?.close(1000, "正常关闭")
        webSocket = null
        isConnected = false
        Log.d("WebSocket", "已断开连接")
    }

    // ⭐ 修改：添加同步检查方法
    fun isConnected(): Boolean {
        return isConnected && webSocket != null
    }
}