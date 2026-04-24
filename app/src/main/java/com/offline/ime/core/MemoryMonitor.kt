package com.offline.ime.core

import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 内存监控器 - 运行时内存检测与自动降级
 * 峰值控制目标：100MB以内
 */
class MemoryMonitor private constructor() {

    private val handler = Handler(Looper.getMainLooper())
    private var isMonitoring = false
    private val checkIntervalMs = 5000L // 5秒检测一次
    private var currentLevel = MemoryLevel.NORMAL
    private var listener: MemoryLevelListener? = null

    interface MemoryLevelListener {
        fun onMemoryLevelChanged(level: MemoryLevel)
    }

    fun setListener(listener: MemoryLevelListener) {
        this.listener = listener
    }

    fun startMonitoring(context: Context) {
        if (isMonitoring) return
        isMonitoring = true
        startPeriodicCheck()
        registerSystemCallback(context)
    }

    fun stopMonitoring() {
        isMonitoring = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun startPeriodicCheck() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (!isMonitoring) return
                checkMemoryStatus()
                handler.postDelayed(this, checkIntervalMs)
            }
        }, checkIntervalMs)
    }

    private fun registerSystemCallback(context: Context) {
        context.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                when (level) {
                    ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE ->
                        notifyLevelChanged(MemoryLevel.WARNING)
                    ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
                    ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ->
                        notifyLevelChanged(MemoryLevel.CRITICAL)
                    ComponentCallbacks2.TRIM_MEMORY_COMPLETE ->
                        notifyLevelChanged(MemoryLevel.EMERGENCY)
                }
            }

            override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {}
            override fun onLowMemory() {
                notifyLevelChanged(MemoryLevel.EMERGENCY)
            }
        })
    }

    private fun checkMemoryStatus() {
        val runtime = Runtime.getRuntime()
        val usedMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)

        val newLevel = when {
            usedMemoryMB > 95 -> MemoryLevel.EMERGENCY
            usedMemoryMB > 80 -> MemoryLevel.CRITICAL
            usedMemoryMB > 60 -> MemoryLevel.WARNING
            else -> MemoryLevel.NORMAL
        }

        if (newLevel != currentLevel) {
            Log.d(TAG, "Memory level changed: $currentLevel -> $newLevel (used: ${usedMemoryMB}MB)")
            notifyLevelChanged(newLevel)
        }
    }

    private fun notifyLevelChanged(level: MemoryLevel) {
        currentLevel = level
        listener?.onMemoryLevelChanged(level)
    }

    companion object {
        private const val TAG = "MemoryMonitor"

        @Volatile
        private var instance: MemoryMonitor? = null

        fun getInstance(): MemoryMonitor {
            return instance ?: synchronized(this) {
                instance ?: MemoryMonitor().also { instance = it }
            }
        }
    }
}

/**
 * 内存状态分级
 */
enum class MemoryLevel {
    NORMAL,      // < 60MB - 正常运行
    WARNING,     // 60-80MB - 降低动画质量
    CRITICAL,    // 80-95MB - 关闭动画，释放缓存
    EMERGENCY    // > 95MB - 最简UI，关闭引擎
}