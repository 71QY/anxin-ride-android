package com.example.myapplication.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.myapplication.MainActivity
import com.example.myapplication.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AgentFloatService : Service() {

    companion object {
        private const val TAG = "AgentFloatService"
    }

    private var windowManager: WindowManager? = null
    private var floatView: LinearLayout? = null
    private var floatIcon: ImageView? = null
    private var floatLabel: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var isLongPressMode = false
    private var longPressStartTime = 0L
    private val LONG_PRESS_DELAY = 500L
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, " 悬浮窗服务 onCreate 被调用")
        
        // ⭐ 关键修复：Android 8.0+ 必须启动前台服务，否则系统会静默杀死服务
        startForegroundService()
        
        createFloatWindow()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeFloatWindow()
        Log.d(TAG, "悬浮窗服务已销毁")
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    // ⭐ 新增：启动前台服务（必须调用，否则 Android 8+ 会崩溃）
    private fun startForegroundService() {
        try {
            val channelId = "float_service_channel"
            val channelName = "AI助手悬浮窗"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = "保持AI助手悬浮窗运行"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)

            val notification: Notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("AI助手")
                .setContentText("悬浮窗正在后台运行")
                .setSmallIcon(R.drawable.ic_launcher_foreground) // 使用应用图标
                .setOngoing(true)
                .build()

            startForeground(1001, notification)
            Log.d(TAG, "✅ 已启动前台服务，系统将保持服务存活")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 启动前台服务失败", e)
        }
    }

    // ⭐ 新增：震动辅助函数
    private fun vibrate(milliseconds: Long) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(milliseconds)
            }
        } catch (e: Exception) {
            Log.e(TAG, "震动失败: ${e.message}")
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Log.e(TAG, "没有悬浮窗权限，停止服务")
                stopSelf()
                return
            }
        }
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val wmParams = WindowManager.LayoutParams(
            150,
            150,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }
        layoutParams = wmParams

        floatView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            
            val backgroundDrawable = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#EEFFFFFF"))  // ⭐ 修改：浅白色背景，让黑色文字更清晰
                cornerRadius = 16f
            }
            this.background = backgroundDrawable
            this.setPadding(8, 8, 8, 8)

            floatIcon = ImageView(this@AgentFloatService).apply {
                // ⭐ 修改：使用新的机器人图标
                setImageResource(R.drawable.ic_agent_bot)
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    width = 120
                    height = 120
                }
            }
            addView(floatIcon)

            floatLabel = TextView(this@AgentFloatService).apply {
                text = "智能体"  // ⭐ 修改：文字改为智能体
                textSize = 14f  // ⭐ 修改：增大字体到 14f
                setTextColor(android.graphics.Color.BLACK)  // ⭐ 修改：使用黑色
                gravity = Gravity.CENTER
                setTypeface(null, android.graphics.Typeface.BOLD)  // ⭐ 新增：加粗字体
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 8  // ⭐ 修改：增加上边距
                }
                // ⭐ 移除软件层和阴影，避免字体模糊
                // setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                // setShadowLayer(2f, 1f, 1f, android.graphics.Color.BLACK)
            }
            addView(floatLabel)

            var isMoved = false
            var downX = 0f
            var downY = 0f
            var longPressTimer: android.os.Handler? = null
            val longPressRunnable = Runnable {
                isLongPressMode = true
                floatLabel?.text = "拖动中"
                
                // ⭐ 新增：长按触发时震动反馈
                this@AgentFloatService.vibrate(40)
                
                Log.d(TAG, "✅ 长按触发，进入拖动模式")
            }

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        val lp = layoutParams as? WindowManager.LayoutParams
                        if (lp != null) {
                            initialX = lp.x
                            initialY = lp.y
                        }
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        downX = event.rawX
                        downY = event.rawY
                        isMoved = false
                        isLongPressMode = false
                        
                        // ⭐ 修改：启动长按定时器（300ms）
                        longPressTimer = android.os.Handler(android.os.Looper.getMainLooper())
                        longPressTimer?.postDelayed(longPressRunnable, 300L)
                        
                        Log.d(TAG, "👇 ACTION_DOWN: 初始位置 ($initialX, $initialY), 触摸点 ($initialTouchX, $initialTouchY)")
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        val moveDistance = kotlin.math.sqrt(
                            ((event.rawX - downX) * (event.rawX - downX) + 
                             (event.rawY - downY) * (event.rawY - downY)).toDouble()
                        ).toFloat()

                        // ⭐ 修改：如果移动距离超过 10px，取消长按定时器并直接进入拖动模式
                        if (!isLongPressMode && moveDistance > 10) {
                            longPressTimer?.removeCallbacks(longPressRunnable)
                            isLongPressMode = true
                            floatLabel?.text = "拖动中"
                            Log.d(TAG, "⚠️ 移动距离过大($moveDistance)，直接进入拖动模式")
                        }

                        if (isLongPressMode) {
                            isMoved = true
                            val lp = layoutParams as? WindowManager.LayoutParams
                            if (lp != null) {
                                lp.x = initialX + dx
                                lp.y = initialY + dy
                                windowManager?.updateViewLayout(floatView, lp)
                                Log.v(TAG, "📍 拖动中: 新位置 (${lp.x}, ${lp.y})")
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        // ⭐ 新增：取消长按定时器
                        longPressTimer?.removeCallbacks(longPressRunnable)
                        longPressTimer = null
                        
                        Log.d(TAG, "👆 ACTION_UP: isMoved=$isMoved, isLongPressMode=$isLongPressMode")
                        
                        if (!isMoved && !isLongPressMode) {
                            Log.d(TAG, "✅ 检测到点击事件，正在打开聊天界面...")
                            val intent = Intent(this@AgentFloatService, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                putExtra("navigate_to_chat", true)
                            }
                            try {
                                startActivity(intent)
                                Log.d(TAG, "✅ 成功发送跳转意图")
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ 跳转失败: ${e.message}", e)
                            }
                        } else if (isMoved) {
                            Log.d(TAG, "✅ 释放拖拽，悬浮窗已移动到新位置")
                        }
                        
                        isLongPressMode = false
                        floatLabel?.text = "智能体"  // ⭐ 修改：恢复为智能体
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        // ⭐ 新增：处理取消事件
                        longPressTimer?.removeCallbacks(longPressRunnable)
                        longPressTimer = null
                        isLongPressMode = false
                        floatLabel?.text = "智能体"  // ⭐ 修改：恢复为智能体
                        true
                    }
                    else -> false
                }
            }
        }

        try {
            windowManager?.addView(floatView, layoutParams)
            Log.d(TAG, "悬浮窗已添加到窗口")
        } catch (e: Exception) {
            Log.e(TAG, "添加悬浮窗失败", e)
        }
    }

    private fun removeFloatWindow() {
        try {
            if (floatView != null) {
                windowManager?.removeView(floatView)
                floatView = null
                windowManager = null
                layoutParams = null
                Log.d(TAG, "悬浮窗已移除")
            }
        } catch (e: Exception) {
            Log.e(TAG, "移除悬浮窗失败", e)
        }
    }
}
