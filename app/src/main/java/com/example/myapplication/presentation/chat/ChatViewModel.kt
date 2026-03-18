package com.example.myapplication.presentation.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.MyApplication
import com.example.myapplication.core.utils.SpeechRecognizerHelper
import com.example.myapplication.core.websocket.ChatWebSocketClient
import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.Order
import com.example.myapplication.data.model.UserMessage
import com.example.myapplication.data.model.UserImage
import com.example.myapplication.domain.repository.IOrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val webSocketClient: ChatWebSocketClient,
    private val orderRepository: IOrderRepository
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _sessionId = MutableStateFlow(UUID.randomUUID().toString())
    val sessionId: StateFlow<String> = _sessionId

    private var speechHelper: SpeechRecognizerHelper? = null

    private val _orderState = MutableStateFlow<OrderState>(OrderState.Idle)
    val orderState: StateFlow<OrderState> = _orderState

    init {
        viewModelScope.launch {
            webSocketClient.messages.collect { serverMessage ->
                val chatMessage = parseServerMessage(serverMessage)
                _messages.value += chatMessage // 使用 += 简化
            }
        }

        viewModelScope.launch {
            val token = withContext(Dispatchers.IO) {
                MyApplication.tokenManager.getToken()
            }
            if (!token.isNullOrBlank()) {
                webSocketClient.connect(sessionId.value, token)
            } else {
                Log.e("ChatViewModel", "Token 为空，无法连接 WebSocket")
            }
        }
    }

    fun sendMessage(content: String) {
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            content = content,
            isUser = true,
            timestamp = System.currentTimeMillis(),
            imageBase64 = null
        )
        _messages.value += userMessage

        val wsMessage = UserMessage("user_message", sessionId.value, content)
        val json = Json.encodeToString<UserMessage>(wsMessage) // 显式指定泛型
        webSocketClient.sendRaw(json)
    }

    fun sendImageFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                var inputStream: InputStream? = null
                try {
                    inputStream = context.contentResolver.openInputStream(uri)
                    BitmapFactory.decodeStream(inputStream)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                } finally {
                    inputStream?.close()
                }
            }
            bitmap?.let { sendImage(it) }
        }
    }

    fun sendImage(bitmap: Bitmap) {
        viewModelScope.launch {
            val base64 = withContext(Dispatchers.IO) {
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                val byteArray = stream.toByteArray()
                Base64.encodeToString(byteArray, Base64.NO_WRAP)
            }

            val imageMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                content = "📷 [图片]",
                isUser = true,
                timestamp = System.currentTimeMillis(),
                imageBase64 = base64
            )
            _messages.value += imageMessage

            val wsImage = UserImage("user_image", sessionId.value, base64)
            val json = Json.encodeToString<UserImage>(wsImage) // 显式指定泛型
            webSocketClient.sendRaw(json)
        }
    }

    fun startVoiceInput(context: Context) {
        if (speechHelper == null) {
            speechHelper = SpeechRecognizerHelper(context) { result ->
                sendMessage(result)
            }
        }
        speechHelper?.startListening()
    }

    fun createOrder(destName: String) {
        viewModelScope.launch {
            _orderState.value = OrderState.Loading
            val result = orderRepository.createOrder(destName, 39.9087, 116.3975)
            if (result.isSuccess()) {
                result.data?.let { order ->
                    _orderState.value = OrderState.Success(order)
                } ?: run {
                    _orderState.value = OrderState.Error("返回数据为空")
                }
            } else {
                _orderState.value = OrderState.Error(result.message ?: "未知错误")
            }
        }
    }

    fun resetOrderState() {
        _orderState.value = OrderState.Idle
    }

    private fun parseServerMessage(json: String): ChatMessage {
        return ChatMessage(
            id = UUID.randomUUID().toString(),
            content = json,
            isUser = false,
            timestamp = System.currentTimeMillis(),
            imageBase64 = null
        )
    }

    override fun onCleared() {
        webSocketClient.disconnect()
        speechHelper?.destroy()
        super.onCleared()
    }

    sealed class OrderState {
        object Idle : OrderState()
        object Loading : OrderState()
        data class Success(val order: Order) : OrderState()
        data class Error(val message: String) : OrderState()
    }
}