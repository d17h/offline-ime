package com.offline.ime.core

import android.app.Application
import com.offline.ime.engine.DictionaryManager
import com.offline.ime.clipboard.ClipboardDatabase
import com.offline.ime.symbol.SymbolManager

/**
 * 应用入口 - 完全离线，零网络初始化
 */
class IMEApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 初始化本地词库（异步）
        DictionaryManager.getInstance(this).initialize()

        // 初始化符号系统
        SymbolManager.getInstance(this).initialize()

        // 初始化剪贴板数据库
        ClipboardDatabase.getInstance(this)
    }

    companion object {
        lateinit var instance: IMEApplication
            private set
    }
}