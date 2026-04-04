package com.example.myapplication.debug

import android.util.Log
import com.example.myapplication.core.websocket.ChatWebSocketClient
import kotlinx.coroutines.delay

/**
 * WebSocket 连接调试工具
 * 用于监控和诊断 WebSocket 连接状态
 */
object WebSocketDebugMonitor {
    private const val TAG = "WebSocketDebug"
    
    // ⭐ 连接事件记录
    private val connectionEvents = mutableListOf<String>()
    
    /**
     * 开始监控 WebSocket 连接
     * @param webSocketClient WebSocket 客户端实例
     * @param intervalMs 检查间隔（毫秒），默认 5000ms
     */
    suspend fun startMonitoring(
        webSocketClient: ChatWebSocketClient,
        intervalMs: Long = 5000L
    ) {
        Log.d(TAG, "=== 开始监控 WebSocket 连接 ===")
        
        while (true) {
            delay(intervalMs)
            
            val isConnected = webSocketClient.isConnected()
            val timestamp = System.currentTimeMillis()
            val event = "${timestamp}: connected=$isConnected"
            
            connectionEvents.add(event)
            
            // 保持最多 100 条记录
            if (connectionEvents.size > 100) {
                connectionEvents.removeAt(0)
            }
            
            Log.d(TAG, "[$timestamp] WebSocket 状态：${if (isConnected) "✅ 已连接" else "❌ 未连接"}")
            
            if (!isConnected) {
                Log.w(TAG, "⚠️ WebSocket 连接断开！最近事件：${connectionEvents.takeLast(5)}")
            }
        }
    }
    
    /**
     * 获取最近的连接事件
     */
    fun getRecentEvents(count: Int = 10): List<String> {
        return connectionEvents.takeLast(count)
    }
    
    /**
     * 清除事件记录
     */
    fun clearEvents() {
        connectionEvents.clear()
    }
    
    /**
     * 打印连接统计信息
     */
    fun printStatistics() {
        val total = connectionEvents.size
        val connectedCount = connectionEvents.count { it.contains("connected=true") }
        val disconnectedCount = total - connectedCount
        
        Log.d(TAG, "=== WebSocket 连接统计 ===")
        Log.d(TAG, "总记录数：$total")
        Log.d(TAG, "已连接次数：$connectedCount")
        Log.d(TAG, "未连接次数：$disconnectedCount")
        Log.d(TAG, "连接成功率：${if (total > 0) connectedCount * 100 / total else 0}%")
    }
}
