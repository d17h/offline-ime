package com.offline.ime.core

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import com.offline.ime.ui.KeyboardView

/**
 * 离线输入法核心服务
 * 零网络请求，所有功能完全本地化
 */
class OfflineInputMethodService : InputMethodService() {

    private lateinit var keyboardView: KeyboardView
    private val memoryMonitor by lazy { MemoryMonitor.getInstance() }

    override fun onCreate() {
        super.onCreate()
        // 启动内存监控
        memoryMonitor.startMonitoring(this)
    }

    override fun onCreateInputView(): View {
        keyboardView = KeyboardView(this)
        return keyboardView
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboardView.onStartInput(info)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        keyboardView.onFinishInput()
    }

    override fun onDestroy() {
        memoryMonitor.stopMonitoring()
        super.onDestroy()
    }

    /**
     * 获取当前输入连接
     */
    fun getCurrentInputConnection() = currentInputConnection

    /**
     * 发送按键事件
     */
    fun sendKeyEvent(keyCode: Int) {
        sendDownUpKeyEvents(keyCode)
    }

    /**
     * 提交文本
     */
    fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }
}