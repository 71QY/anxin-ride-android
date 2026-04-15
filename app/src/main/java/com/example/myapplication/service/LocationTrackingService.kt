package com.example.myapplication.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.example.myapplication.MainActivity
import com.example.myapplication.R
import com.example.myapplication.core.websocket.ChatWebSocketClient
import com.example.myapplication.data.repository.AgentRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * ⭐ 新增：后台定位追踪服务
 * 
 * 作用：
 * 1. 保持应用在后台运行时持续获取位置
 * 2. 维持WebSocket连接不断开
 * 3. 持续向后端上报位置信息（用于代叫车功能）
 */
@AndroidEntryPoint
class LocationTrackingService : Service() {

    companion object {
        private const val TAG = "LocationTrackingService"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "location_tracking_channel"
        private const val CHANNEL_NAME = "位置追踪服务"
        
        // Intent Actions
        const val ACTION_START_TRACKING = "com.example.myapplication.ACTION_START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.example.myapplication.ACTION_STOP_TRACKING"
    }

    @Inject
    lateinit var webSocketClient: ChatWebSocketClient
    
    @Inject
    lateinit var agentRepository: AgentRepository

    private var locationClient: AMapLocationClient? = null
    private var serviceScope: CoroutineScope? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "✅ 后台定位服务创建")
        
        // 启动前台服务
        startForegroundService()
        
        // 初始化协程作用域
        serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        
        // 启动定位
        startLocationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                Log.d(TAG, "🚀 收到启动追踪命令")
            }
            ACTION_STOP_TRACKING -> {
                Log.d(TAG, "🛑 收到停止追踪命令")
                stopSelf()
            }
        }
        
        // START_STICKY: 如果服务被杀死，系统会尝试重启
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "❌ 后台定位服务销毁")
        
        // 停止定位
        stopLocationUpdates()
        
        // 取消协程
        serviceScope?.cancel()
        serviceScope = null
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /**
     * 启动前台服务通知
     */
    private fun startForegroundService() {
        try {
            // 创建通知渠道
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持位置追踪和WebSocket连接"
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)

            // 创建点击通知跳转的PendingIntent
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 构建通知
            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("安心出行")
                .setContentText("后台服务运行中 - 持续获取位置并维持连接")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .build()

            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "✅ 前台服务已启动，通知ID: $NOTIFICATION_ID")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 启动前台服务失败", e)
        }
    }

    /**
     * 启动高德地图定位
     */
    private fun startLocationUpdates() {
        try {
            // 检查权限
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "❌ 缺少定位权限")
                return
            }

            // 初始化定位客户端
            locationClient = AMapLocationClient(applicationContext)
            
            // 配置定位参数
            val option = AMapLocationClientOption().apply {
                // 高精度模式
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                // 需要地址信息
                isNeedAddress = true
                // 禁止模拟位置
                isMockEnable = false
                // 定位间隔：5秒
                interval = 5000
                // 连续定位
                isOnceLocation = false
                isOnceLocationLatest = false
                // 超时时间
                httpTimeOut = 20000
                // 不使用缓存
                isLocationCacheEnable = false
                // GPS优先
                isGpsFirst = true
            }
            
            locationClient?.setLocationOption(option)
            
            // 设置定位监听器
            locationClient?.setLocationListener { location ->
                handleLocationUpdate(location)
            }
            
            // 启动定位
            locationClient?.startLocation()
            Log.d(TAG, "✅ 定位服务已启动")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 启动定位失败", e)
        }
    }

    /**
     * 处理位置更新
     */
    private fun handleLocationUpdate(location: AMapLocation?) {
        if (location == null) {
            Log.w(TAG, "⚠️ 位置信息为空")
            return
        }

        if (location.errorCode != 0) {
            Log.e(TAG, "❌ 定位错误: code=${location.errorCode}, msg=${location.errorInfo}")
            return
        }

        val lat = location.latitude
        val lng = location.longitude
        val accuracy = location.accuracy
        
        Log.d(TAG, "📍 后台位置更新: lat=$lat, lng=$lng, accuracy=${accuracy}m")

        // 更新WebSocket客户端的位置
        webSocketClient.updateLocation(lat, lng)
        
        // 定期上报位置到后端（通过WebSocket）
        reportLocationToBackend(lat, lng, accuracy)
    }

    /**
     * 上报位置到后端
     */
    private fun reportLocationToBackend(lat: Double, lng: Double, accuracy: Float) {
        serviceScope?.launch {
            try {
                // ⭐ 新增：通过HTTP API上报位置到后端
                val userId = getUserIdFromToken()
                if (userId == null) {
                    Log.w(TAG, "⚠️ 用户未登录，无法上报位置")
                    return@launch
                }
                
                // sessionId 格式：user_{userId}
                val sessionId = "user_$userId"
                Log.d(TAG, "📍 开始上报位置到后端：lat=$lat, lng=$lng, accuracy=$accuracy, sessionId=$sessionId")
                
                val result = agentRepository.updateLocation(sessionId, lat, lng)
                if (result.code == 200) {
                    Log.d(TAG, "✅ 位置上报成功")
                } else {
                    Log.e(TAG, "❌ 位置上报失败：${result.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 上报位置异常", e)
            }
        }
    }
    
    /**
     * 从 TokenManager 获取用户ID
     */
    private suspend fun getUserIdFromToken(): Long? {
        return try {
            com.example.myapplication.MyApplication.tokenManager.getUserId()
        } catch (e: Exception) {
            Log.e(TAG, "获取用户ID失败", e)
            null
        }
    }

    /**
     * 停止定位
     */
    private fun stopLocationUpdates() {
        try {
            locationClient?.stopLocation()
            locationClient?.onDestroy()
            locationClient = null
            Log.d(TAG, "✅ 定位服务已停止")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 停止定位失败", e)
        }
    }
}
