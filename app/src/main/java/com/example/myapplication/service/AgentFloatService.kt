package com.example.myapplication.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.OptIn
import coil.load
import coil.transform.CircleCropTransformation
import com.example.myapplication.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint  // ⭐ 新增：添加 Hilt 注解
class AgentFloatService : Service() {

    companion object {
        private const val TAG = "AgentFloatService"
    }

    private var windowManager: WindowManager? = null
    private var floatView: LinearLayout? = null  // ⭐ 改为 LinearLayout 包裹 ImageView 和 TextView
    private var floatIcon: ImageView? = null
    private var floatLabel: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    // 记录初始位置
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    
    // ⭐ 长按拖动相关
    private var isLongPressMode = false  // 是否进入长按模式
    private var longPressStartTime = 0L
    private val LONG_PRESS_DELAY = 500L  // ⭐ 改为 0.5 秒，更快响应

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "悬浮窗服务已启动")
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

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatWindow() {
        // ⭐ 新增：先检查权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Log.e(TAG, "没有悬浮窗权限，停止服务")
                stopSelf()
                return
            }
        }
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // ⭐ 修改：显式指定 WindowManager.LayoutParams 类型，避免类型推断为父类
        val wmParams = WindowManager.LayoutParams(
            150, // width
            150, // height
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
            
            // ⭐ 设置背景（半透明黑色圆角矩形）
            val backgroundDrawable = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#CC000000"))  // 半透明黑色背景
                cornerRadius = 16f  // 圆角
            }
            this.background = backgroundDrawable
            this.setPadding(8, 8, 8, 8)  // 内边距

            floatIcon = ImageView(this@AgentFloatService).apply {  // ⭐ 使用 Service 作为 Context
                load(R.drawable.ic_launcher_foreground) {
                    transformations(CircleCropTransformation())
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    width = 120  // 图标宽度
                    height = 120  // 图标高度
                }
            }
            addView(floatIcon)

            floatLabel = TextView(this@AgentFloatService).apply {  // ⭐ 使用 Service 作为 Context
                text = "AI 助手"
                textSize = 12f  // ⭐ 稍微增大字体
                setTextColor(android.graphics.Color.parseColor("#FFFFFF"))  // ⭐ 纯白色
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 6  // ⭐ 增加顶部间距
                }
                // ⭐ 添加文字阴影，让文字更清晰
                setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                setShadowLayer(2f, 1f, 1f, android.graphics.Color.BLACK)
            }
            addView(floatLabel)

            // ⭐ 修改：添加点击标志，区分拖拽和点击
            var isMoved = false

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // ⭐ 修改：强制转换为 WindowManager.LayoutParams
                        val lp = layoutParams as? WindowManager.LayoutParams
                        if (lp != null) {
                            initialX = lp.x
                            initialY = lp.y
                        }
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isMoved = false
                        longPressStartTime = System.currentTimeMillis()
                        isLongPressMode = false  // ⭐ 重置长按状态
                        Log.d(TAG, "ACTION_DOWN: 初始位置 ($initialX, $initialY), 触摸点 ($initialTouchX, $initialTouchY)")
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()

                        // ⭐ 长按检测：超过 3 秒进入拖动模式
                        if (!isLongPressMode && System.currentTimeMillis() - longPressStartTime >= LONG_PRESS_DELAY) {
                            isLongPressMode = true
                            floatLabel?.text = "👆 拖动中"
                            Log.d(TAG, "进入长按拖动模式")
                        }

                        // ⭐ 只有长按模式或移动距离超过阈值才触发拖拽
                        if (isLongPressMode || kotlin.math.abs(dx) > 5 || kotlin.math.abs(dy) > 5) {
                            isMoved = true
                            val lp = layoutParams as? WindowManager.LayoutParams
                            if (lp != null) {
                                lp.x = initialX + dx
                                lp.y = initialY + dy
                                windowManager?.updateViewLayout(floatView, lp)
                                Log.d(TAG, "拖拽中")
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        Log.d(TAG, "ACTION_UP: isMoved=$isMoved, isLongPressMode=$isLongPressMode")
                        
                        // ⭐ 如果没有移动且没有进入长按模式，则触发点击事件
                        if (!isMoved && !isLongPressMode) {
                            Log.d(TAG, "点击悬浮窗，打开聊天界面")
                            val intent = Intent(this@AgentFloatService,
                                Class.forName("com.example.myapplication.MainActivity")).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                putExtra("navigate_to_chat", true)
                            }
                            startActivity(intent)
                        } else if (isMoved) {
                            Log.d(TAG, "释放拖拽，恢复显示")
                        }
                        
                        // ⭐ 重置长按状态并恢复文字
                        isLongPressMode = false
                        floatLabel?.text = "AI 助手"
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
