package com.offline.ime.ui

import android.content.Context
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import com.offline.ime.R
import com.offline.ime.core.OfflineInputMethodService

/**
 * 全键盘(QWERTY)和九宫格(T9)视图
 */
class QwertyKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val keyboardView: KeyboardView
    private var service: OfflineInputMethodService? = null
    private var currentMode = InputMode.QWERTY
    private val inputBuffer = StringBuilder()

    var onTextInput: ((String) -> Unit)? = null
    var onKeyAction: ((KeyAction) -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.keyboard_qwerty, this, true)
        keyboardView = findViewById(R.id.keyboard_view)

        setupKeyboardListener()
        loadKeyboard(R.xml.kbd_qwerty)
    }

    private fun setupKeyboardListener() {
        keyboardView.setOnKeyboardActionListener(object : KeyboardView.OnKeyboardActionListener {
            override fun onPress(primaryCode: Int) {}
            override fun onRelease(primaryCode: Int) {}

            override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
                when {
                    primaryCode >= 97 && primaryCode <= 122 -> {
                        // 字母键
                        val char = primaryCode.toChar()
                        inputBuffer.append(char)
                        onTextInput?.invoke(inputBuffer.toString())
                    }
                    primaryCode == Keyboard.KEYCODE_DELETE -> {
                        if (inputBuffer.isNotEmpty()) {
                            inputBuffer.deleteCharAt(inputBuffer.length - 1)
                            onTextInput?.invoke(inputBuffer.toString())
                        } else {
                            onKeyAction?.invoke(KeyAction.Delete)
                        }
                    }
                    primaryCode == Keyboard.KEYCODE_DONE -> {
                        onKeyAction?.invoke(KeyAction.Enter)
                    }
                    primaryCode == Keyboard.KEYCODE_MODE_CHANGE -> {
                        onKeyAction?.invoke(KeyAction.SwitchKeyboard)
                    }
                    primaryCode == 32 -> { // 空格
                        if (inputBuffer.isNotEmpty()) {
                            commitBuffer()
                        } else {
                            onKeyAction?.invoke(KeyAction.Space)
                        }
                    }
                    primaryCode == -2 -> { // 符号切换
                        onKeyAction?.invoke(KeyAction.SwitchSymbol)
                    }
                    primaryCode == -3 -> { // 语言切换
                        onKeyAction?.invoke(KeyAction.SwitchLanguage)
                    }
                    primaryCode >= 48 && primaryCode <= 57 -> {
                        // 数字键
                        val char = primaryCode.toChar()
                        if (currentMode == InputMode.T9) {
                            inputBuffer.append(char)
                            onTextInput?.invoke(inputBuffer.toString())
                        } else {
                            commitBuffer()
                            onKeyAction?.invoke(KeyAction.CommitText(char.toString()))
                        }
                    }
                }
            }

            override fun onText(text: CharSequence?) {
                text?.let { onKeyAction?.invoke(KeyAction.CommitText(it.toString())) }
            }

            override fun swipeLeft() {}
            override fun swipeRight() {}
            override fun swipeDown() {}
            override fun swipeUp() {}
        })
    }

    fun attachService(service: OfflineInputMethodService) {
        this.service = service
    }

    fun setMode(mode: InputMode) {
        currentMode = mode
        val xmlRes = when (mode) {
            InputMode.T9 -> R.xml.kbd_t9
            else -> R.xml.kbd_qwerty
        }
        loadKeyboard(xmlRes)
    }

    fun onStartInput(info: EditorInfo) {
        // 根据输入类型调整键盘
        when (info.inputType and EditorInfo.TYPE_MASK_CLASS) {
            EditorInfo.TYPE_CLASS_NUMBER,
            EditorInfo.TYPE_CLASS_PHONE -> {
                loadKeyboard(R.xml.kbd_number)
            }
            else -> {
                loadKeyboard(if (currentMode == InputMode.T9) R.xml.kbd_t9 else R.xml.kbd_qwerty)
            }
        }
    }

    fun onFinishInput() {
        inputBuffer.clear()
    }

    fun setAnimationEnabled(enabled: Boolean) {
        keyboardView.isPreviewEnabled = enabled
    }

    fun setSimplifiedMode(simplified: Boolean) {
        // 简化模式：关闭预览，简化渲染
        keyboardView.isPreviewEnabled = !simplified
    }

    private fun loadKeyboard(xmlResId: Int) {
        val keyboard = Keyboard(context, xmlResId)
        keyboardView.keyboard = keyboard
    }

    private fun commitBuffer() {
        if (inputBuffer.isNotEmpty()) {
            val text = inputBuffer.toString()
            inputBuffer.clear()
            onKeyAction?.invoke(KeyAction.CommitText(text))
        }
    }

    // 内部KeyboardView引用
    private inner class KeyboardView @JvmOverloads constructor(
        ctx: Context,
        a: AttributeSet? = null
    ) : android.inputmethodservice.KeyboardView(ctx, a, R.attr.keyboardViewStyle) {
        init {
            isPreviewEnabled = true
            isProximityCorrectionEnabled = true
        }
    }
}