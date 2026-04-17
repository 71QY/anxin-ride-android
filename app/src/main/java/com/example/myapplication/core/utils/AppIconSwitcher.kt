package com.example.myapplication.core.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.example.myapplication.MainActivity

/**
 * 动态应用图标切换工具类
 * 根据用户身份（普通用户/长辈）切换应用图标
 */
object AppIconSwitcher {
    private const val TAG = "AppIconSwitcher"

    // Activity Alias 名称
    private const val ALIAS_DEFAULT = "com.example.myapplication.MainActivityDefault"
    private const val ALIAS_ELDER = "com.example.myapplication.MainActivityElder"

    /**
     * 切换到长辈端图标
     */
    fun switchToElderIcon(context: Context) {
        Log.d(TAG, "🔄 切换到长辈端图标")
        switchIcon(context, enableElder = true)
    }

    /**
     * 切换到普通用户图标
     */
    fun switchToDefaultIcon(context: Context) {
        Log.d(TAG, "🔄 切换到普通用户图标")
        switchIcon(context, enableElder = false)
    }

    /**
     * 根据长辈模式状态切换图标
     */
    fun switchIconByGuardMode(context: Context, guardMode: Int) {
        if (guardMode == 1) {
            switchToElderIcon(context)
        } else {
            switchToDefaultIcon(context)
        }
    }

    /**
     * 执行图标切换
     */
    private fun switchIcon(context: Context, enableElder: Boolean) {
        val pm = context.packageManager

        val defaultComponent = ComponentName(context, ALIAS_DEFAULT)
        val elderComponent = ComponentName(context, ALIAS_ELDER)

        try {
            if (enableElder) {
                // 启用长辈端图标，禁用默认图标
                pm.setComponentEnabledSetting(
                    elderComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                pm.setComponentEnabledSetting(
                    defaultComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            } else {
                // 启用默认图标，禁用长辈端图标
                pm.setComponentEnabledSetting(
                    defaultComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                pm.setComponentEnabledSetting(
                    elderComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            Log.d(TAG, "✅ 图标切换成功：elder=$enableElder")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 图标切换失败", e)
        }
    }

    /**
     * 获取当前启用的图标类型
     * @return 0=普通用户图标，1=长辈端图标，-1=未知
     */
    fun getCurrentIconType(context: Context): Int {
        val pm = context.packageManager
        val elderComponent = ComponentName(context, ALIAS_ELDER)

        return try {
            val state = pm.getComponentEnabledSetting(elderComponent)
            if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                1  // 长辈端图标
            } else {
                0  // 普通用户图标
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取当前图标类型失败", e)
            -1
        }
    }
}
