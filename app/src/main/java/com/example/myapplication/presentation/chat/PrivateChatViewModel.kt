package com.example.myapplication.presentation.chat

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.core.network.ApiService
import com.example.myapplication.core.datastore.TokenManager  // ⭐ 修正路径
import com.example.myapplication.data.model.PrivateChatMessage
import com.example.myapplication.data.model.SendPrivateMessageRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 亲友聊天 ViewModel（真正的P2P私聊）
 */
@HiltViewModel
class PrivateChatViewModel @Inject constructor(
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _messages = MutableStateFlow<List<PrivateChatMessage>>(emptyList())
    val messages: StateFlow<List<PrivateChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // 当前聊天的亲友ID
    private var currentGuardianId: Long = 0L
    
    // ⭐ 新增：当前用户ID（用于判断消息是否是自己发的）
    private val _currentUserId = MutableStateFlow<Long?>(null)
    val currentUserId: StateFlow<Long?> = _currentUserId.asStateFlow()
    
    init {
        // 初始化时获取当前用户ID
        viewModelScope.launch {
            _currentUserId.value = tokenManager.getUserId()
            Log.d("PrivateChatViewModel", "当前用户ID: ${_currentUserId.value}")
        }
    }

    /**
     * 初始化聊天 - 加载历史消息
     */
    fun initChat(guardianId: Long) {
        currentGuardianId = guardianId
        loadChatHistory(guardianId)
        markMessagesAsRead(guardianId)
    }

    /**
     * 加载聊天历史
     */
    private fun loadChatHistory(guardianId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val userId = tokenManager.getUserId()
                if (userId == null) {
                    _error.value = "用户未登录"
                    Log.e("PrivateChatViewModel", "用户ID为空")
                    _isLoading.value = false
                    return@launch
                }
                
                Log.d("PrivateChatViewModel", "加载聊天历史: userId=$userId, guardianId=$guardianId")
                
                val result = apiService.getPrivateChatHistory(userId, guardianId)
                
                if (result.isSuccess()) {
                    val history = result.data ?: emptyList()
                    _messages.value = history.sortedBy { parseTimestamp(it.createdAt) }
                    Log.d("PrivateChatViewModel", "加载到 ${history.size} 条历史消息")
                } else {
                    _error.value = result.message ?: "加载历史失败"
                    Log.e("PrivateChatViewModel", "加载历史失败: ${result.message}")
                }
            } catch (e: Exception) {
                _error.value = "网络异常: ${e.message}"
                Log.e("PrivateChatViewModel", "加载历史异常", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 发送文字消息
     */
    fun sendTextMessage(content: String) {
        if (content.isBlank()) return
        
        viewModelScope.launch {
            try {
                val userId = tokenManager.getUserId()
                if (userId == null) {
                    _error.value = "用户未登录"
                    Log.e("PrivateChatViewModel", "用户ID为空")
                    return@launch
                }
                
                Log.d("PrivateChatViewModel", "发送文字消息: userId=$userId, receiverId=$currentGuardianId, content=$content")
                
                val request = SendPrivateMessageRequest(
                    messageType = 1,  // 1=文字
                    content = content
                )
                
                val result = apiService.sendPrivateMessage(userId, currentGuardianId, request)
                
                if (result.isSuccess()) {
                    Log.d("PrivateChatViewModel", "✅ 消息发送成功")
                    // ⭐ 重新加载历史消息以获取最新状态
                    loadChatHistory(currentGuardianId)
                } else {
                    _error.value = result.message ?: "发送失败"
                    Log.e("PrivateChatViewModel", "❌ 消息发送失败: ${result.message}")
                }
            } catch (e: Exception) {
                _error.value = "网络异常: ${e.message}"
                Log.e("PrivateChatViewModel", "❌ 消息发送异常", e)
            }
        }
    }

    /**
     * 发送语音消息
     */
    fun sendVoiceMessage(audioUrl: String) {
        viewModelScope.launch {
            try {
                val userId = tokenManager.getUserId()
                if (userId == null) {
                    _error.value = "用户未登录"
                    Log.e("PrivateChatViewModel", "用户ID为空")
                    return@launch
                }
                
                Log.d("PrivateChatViewModel", "发送语音消息: userId=$userId, receiverId=$currentGuardianId, audioUrl=$audioUrl")
                
                val request = SendPrivateMessageRequest(
                    messageType = 2,  // 2=语音
                    content = audioUrl
                )
                
                val result = apiService.sendPrivateMessage(userId, currentGuardianId, request)
                
                if (result.isSuccess()) {
                    Log.d("PrivateChatViewModel", "✅ 语音消息发送成功")
                    loadChatHistory(currentGuardianId)
                } else {
                    _error.value = result.message ?: "发送失败"
                    Log.e("PrivateChatViewModel", "❌ 语音消息发送失败: ${result.message}")
                }
            } catch (e: Exception) {
                _error.value = "网络异常: ${e.message}"
                Log.e("PrivateChatViewModel", "❌ 语音消息发送异常", e)
            }
        }
    }

    /**
     * 发送图片消息
     */
    fun sendImageMessage(imageUrl: String) {
        viewModelScope.launch {
            try {
                val userId = tokenManager.getUserId()
                if (userId == null) {
                    _error.value = "用户未登录"
                    Log.e("PrivateChatViewModel", "用户ID为空")
                    return@launch
                }
                
                Log.d("PrivateChatViewModel", "发送图片消息: userId=$userId, receiverId=$currentGuardianId, imageUrl=$imageUrl")
                
                val request = SendPrivateMessageRequest(
                    messageType = 3,  // 3=图片
                    content = imageUrl
                )
                
                val result = apiService.sendPrivateMessage(userId, currentGuardianId, request)
                
                if (result.isSuccess()) {
                    Log.d("PrivateChatViewModel", "✅ 图片消息发送成功")
                    loadChatHistory(currentGuardianId)
                } else {
                    _error.value = result.message ?: "发送失败"
                    Log.e("PrivateChatViewModel", "❌ 图片消息发送失败: ${result.message}")
                }
            } catch (e: Exception) {
                _error.value = "网络异常: ${e.message}"
                Log.e("PrivateChatViewModel", "❌ 图片消息发送异常", e)
            }
        }
    }

    /**
     * 标记消息为已读
     */
    private fun markMessagesAsRead(senderId: Long) {
        viewModelScope.launch {
            try {
                val userId = tokenManager.getUserId()
                if (userId == null) {
                    Log.e("PrivateChatViewModel", "用户ID为空，无法标记已读")
                    return@launch
                }
                
                Log.d("PrivateChatViewModel", "标记已读: userId=$userId, senderId=$senderId")
                
                val result = apiService.markAsRead(userId, senderId)
                
                if (result.isSuccess()) {
                    Log.d("PrivateChatViewModel", "✅ 标记已读成功")
                } else {
                    Log.e("PrivateChatViewModel", "❌ 标记已读失败: ${result.message}")
                }
            } catch (e: Exception) {
                Log.e("PrivateChatViewModel", "❌ 标记已读异常", e)
            }
        }
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * 解析时间戳
     */
    private fun parseTimestamp(timestamp: String): Long {
        return try {
            // 尝试解析 ISO 8601 格式
            java.time.Instant.parse(timestamp).toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}
