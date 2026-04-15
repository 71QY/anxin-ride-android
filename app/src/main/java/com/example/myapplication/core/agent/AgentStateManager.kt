package com.example.myapplication.core.agent

import android.util.Log

/**
 * 智能体状态管理器
 * 负责管理智能体会话的状态流转
 * 
 * 对齐后端文档 4.4 节：AgentState 状态枚举
 */
class AgentStateManager {
    
    // 当前会话状态
    private var currentState: AgentState = AgentState.INIT
    
    // 候选 POI 列表（用于确认选择）
    private var candidatePois: List<PoiItem> = emptyList()
    
    // 最后一条用户消息
    private var lastUserMessage: String? = null
    
    // 是否需要用户确认
    private var needsConfirmation: Boolean = false
    
    /**
     * 获取当前状态
     */
    fun getCurrentState(): AgentState {
        return currentState
    }
    
    /**
     * 重置状态到初始状态
     */
    fun resetState() {
        Log.d("AgentStateManager", "🔄 重置状态")
        currentState = AgentState.INIT
        candidatePois = emptyList()
        lastUserMessage = null
        needsConfirmation = false
    }
    
    /**
     * 更新状态（收到用户消息后）
     */
    fun onUserMessage(message: String) {
        Log.d("AgentStateManager", "📝 用户消息，当前状态: $currentState")
        lastUserMessage = message
        
        // 如果之前需要确认，现在收到新消息，重置确认状态
        if (needsConfirmation) {
            needsConfirmation = false
            currentState = AgentState.INIT
        }
    }
    
    /**
     * 更新状态（收到搜索结果后）
     * @param pois POI 列表
     * @param needConfirm 是否需要用户确认
     */
    fun onSearchResult(pois: List<PoiItem>, needConfirm: Boolean) {
        Log.d("AgentStateManager", "🔍 搜索结果: ${pois.size} 个地点, needConfirm=$needConfirm")
        
        candidatePois = pois
        needsConfirmation = needConfirm
        
        currentState = if (needConfirm && pois.isNotEmpty()) {
            AgentState.DEST_PARSED  // 已解析目的地（多个候选）
        } else if (pois.size == 1) {
            AgentState.ROUTE_READY  // 路线已就绪，等待确认
        } else {
            AgentState.INIT
        }
    }
    
    /**
     * 更新状态（用户确认选择后）
     * @param selectedPoiName 选择的 POI 名称
     */
    fun onConfirmSelection(selectedPoiName: String): PoiItem? {
        Log.d("AgentStateManager", "✅ 用户确认: $selectedPoiName")
        
        val selectedPoi = candidatePois.find { it.name == selectedPoiName }
        
        if (selectedPoi != null) {
            currentState = AgentState.WAIT_CONFIRM  // 等待用户确认
            needsConfirmation = false
            return selectedPoi
        } else {
            Log.e("AgentStateManager", "❌ 未找到 POI: $selectedPoiName")
            currentState = AgentState.ERROR
            return null
        }
    }
    
    /**
     * 更新状态（订单创建成功后）
     */
    fun onOrderCreated() {
        Log.d("AgentStateManager", "🚗 订单创建成功")
        currentState = AgentState.ORDER_CREATED
        needsConfirmation = false
    }
    
    /**
     * 更新状态（图片识别成功后）
     * @param ocrText OCR 识别文本
     * @param pois POI 列表
     * @param needConfirm 是否需要确认
     */
    fun onImageRecognized(ocrText: String, pois: List<PoiItem>, needConfirm: Boolean) {
        Log.d("AgentStateManager", "🖼️ 图片识别: ocrText=$ocrText, places=${pois.size}")
        
        candidatePois = pois
        needsConfirmation = needConfirm
        
        currentState = if (pois.isNotEmpty()) {
            AgentState.IMAGE_RECOGNIZED
        } else {
            AgentState.INIT
        }
    }
    
    /**
     * 更新状态（发生错误）
     */
    fun onError(error: String) {
        Log.e("AgentStateManager", "❌ 错误: $error")
        currentState = AgentState.ERROR
    }
    
    /**
     * 检查是否处于可发送消息的状态
     */
    fun canSendMessage(): Boolean {
        return currentState !in setOf(AgentState.ERROR)
    }
    
    /**
     * 检查是否需要用户确认
     */
    fun isWaitingForConfirmation(): Boolean {
        return needsConfirmation
    }
    
    /**
     * 获取候选 POI 列表
     */
    fun getCandidatePois(): List<PoiItem> {
        return candidatePois
    }
    
    /**
     * 获取最后一条用户消息
     */
    fun getLastUserMessage(): String? {
        return lastUserMessage
    }
    
    /**
     * 获取状态描述文本
     */
    fun getStateDescription(): String {
        return when (currentState) {
            AgentState.INIT -> "等待输入"
            AgentState.INTENT_RECOGNIZED -> "已识别意图"
            AgentState.DEST_PARSED -> "请选择目的地"
            AgentState.ROUTE_READY -> "路线已规划"
            AgentState.WAIT_CONFIRM -> "等待确认"
            AgentState.ORDER_CREATED -> "订单已创建"
            AgentState.IMAGE_RECOGNIZED -> "图片识别完成"
            AgentState.ERROR -> "发生错误"
        }
    }
    
    /**
     * 智能体状态枚举
     * 对齐后端文档 4.4 节
     */
    enum class AgentState {
        INIT,                    // 初始状态
        INTENT_RECOGNIZED,       // 已识别叫车意图
        DEST_PARSED,             // 已解析目的地（多个候选）
        ROUTE_READY,             // 路线已就绪，等待确认
        WAIT_CONFIRM,            // 等待用户确认
        ORDER_CREATED,           // 订单已创建
        IMAGE_RECOGNIZED,        // 图片识别成功
        ERROR                    // 异常状态
    }
    
    /**
     * POI 数据项
     */
    data class PoiItem(
        val id: String,
        val name: String,
        val address: String,
        val lat: Double,
        val lng: Double,
        val distance: Double,
        val type: String,
        val duration: Int,
        val price: Double,
        val score: Double
    )
}
