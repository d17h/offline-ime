package com.offline.ime.ui

import android.content.Context
import android.inputmethodservice.Keyboard
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.offline.ime.R
import com.offline.ime.core.MemoryLevel
import com.offline.ime.core.MemoryMonitor
import com.offline.ime.core.OfflineInputMethodService
import com.offline.ime.engine.DictionaryManager
import com.offline.ime.symbol.SymbolKeyboardView

/**
 * 主键盘视图 - 集成所有输入模式
 */
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs), MemoryMonitor.MemoryLevelListener {

    private var service: OfflineInputMethodService? = null
    private val candidateBar: CandidateBar
    private val keyboardContainer: LinearLayout
    private val qwertyKeyboard: QwertyKeyboardView
    private val symbolKeyboard: SymbolKeyboardView
    private var currentMode = InputMode.QWERTY
    private var currentInputType = EditorInfo.TYPE_CLASS_TEXT

    init {
        LayoutInflater.from(context).inflate(R.layout.keyboard_main, this, true)
        candidateBar = findViewById(R.id.candidate_bar)
        keyboardContainer = findViewById(R.id.keyboard_container)
        qwertyKeyboard = QwertyKeyboardView(context)
        symbolKeyboard = SymbolKeyboardView(context)

        candidateBar.onCandidateSelected = { candidate ->
            commitText(candidate)
        }

        qwertyKeyboard.onTextInput = { text ->
            handleTextInput(text)
        }
        qwertyKeyboard.onKeyAction = { action ->
            handleKeyAction(action)
        }

        // 默认显示全键盘
        showKeyboard(qwertyKeyboard)

        // 注册内存监听
        MemoryMonitor.getInstance().setListener(this)
    }

    fun attachService(service: OfflineInputMethodService) {
        this.service = service
        qwertyKeyboard.attachService(service)
    }

    fun onStartInput(info: EditorInfo) {
        currentInputType = info.inputType
        qwertyKeyboard.onStartInput(info)
    }

    fun onFinishInput() {
        qwertyKeyboard.onFinishInput()
    }

    private fun handleTextInput(text: String) {
        if (text.isEmpty()) return

        if (currentMode == InputMode.QWERTY || currentMode == InputMode.T9) {
            // 拼音输入模式，查询词库
            val candidates = DictionaryManager.getInstance(context).queryCandidates(text)
            candidateBar.setCandidates(candidates)
        } else {
            commitText(text)
        }
    }

    private fun handleKeyAction(action: KeyAction) {
        when (action) {
            is KeyAction.CommitText -> commitText(action.text)
            is KeyAction.Delete -> service?.sendKeyEvent(android.view.KeyEvent.KEYCODE_DEL)
            is KeyAction.Enter -> service?.sendKeyEvent(android.view.KeyEvent.KEYCODE_ENTER)
            is KeyAction.Space -> commitText(" ")
            is KeyAction.SwitchSymbol -> switchToSymbols()
            is KeyAction.SwitchKeyboard -> switchKeyboardMode()
            is KeyAction.SwitchLanguage -> switchLanguage()
        }
    }

    private fun commitText(text: String) {
        service?.commitText(text)
        candidateBar.clearCandidates()
    }

    private fun switchToSymbols() {
        currentMode = InputMode.SYMBOL
        showKeyboard(symbolKeyboard)
    }

    private fun switchKeyboardMode() {
        currentMode = when (currentMode) {
            InputMode.QWERTY -> InputMode.T9
            InputMode.T9 -> InputMode.QWERTY
            else -> InputMode.QWERTY
        }
        qwertyKeyboard.setMode(currentMode)
        if (currentMode != InputMode.SYMBOL) {
            showKeyboard(qwertyKeyboard)
        }
    }

    private fun switchLanguage() {
        // 切换语言子类型
        service?.switchToNextInputMethod(null, false)
    }

    private fun showKeyboard(view: View) {
        keyboardContainer.removeAllViews()
        keyboardContainer.addView(view)
    }

    override fun onMemoryLevelChanged(level: MemoryLevel) {
        when (level) {
            MemoryLevel.NORMAL -> qwertyKeyboard.setAnimationEnabled(true)
            MemoryLevel.WARNING -> qwertyKeyboard.setAnimationEnabled(false)
            MemoryLevel.CRITICAL -> {
                qwertyKeyboard.setAnimationEnabled(false)
                candidateBar.setMaxCandidates(5)
            }
            MemoryLevel.EMERGENCY -> {
                // 最简模式
                qwertyKeyboard.setSimplifiedMode(true)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        MemoryMonitor.getInstance().setListener(null)
    }
}

enum class InputMode {
    QWERTY, T9, HANDWRITING, STROKE, SYMBOL
}

sealed class KeyAction {
    data class CommitText(val text: String) : KeyAction()
    object Delete : KeyAction()
    object Enter : KeyAction()
    object Space : KeyAction()
    object SwitchSymbol : KeyAction()
    object SwitchKeyboard : KeyAction()
    object SwitchLanguage : KeyAction()
}