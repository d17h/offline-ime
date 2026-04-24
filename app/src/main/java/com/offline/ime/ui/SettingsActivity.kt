package com.offline.ime.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.offline.ime.R
import com.offline.ime.clipboard.ClipboardActivity
import com.offline.ime.skin.SkinActivity

/**
 * 输入法设置界面
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        title = getString(R.string.settings_title)

        setupSwitches()
        setupButtons()
    }

    private fun setupSwitches() {
        val prefs = getSharedPreferences("ime_settings", MODE_PRIVATE)

        findViewById<Switch>(R.id.switch_vibrate).apply {
            isChecked = prefs.getBoolean("vibrate", true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("vibrate", checked).apply()
            }
        }

        findViewById<Switch>(R.id.switch_sound).apply {
            isChecked = prefs.getBoolean("sound", false)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("sound", checked).apply()
            }
        }

        findViewById<Switch>(R.id.switch_fuzzy).apply {
            isChecked = prefs.getBoolean("fuzzy_pinyin", true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("fuzzy_pinyin", checked).apply()
            }
        }
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btn_clipboard).setOnClickListener {
            startActivity(Intent(this, ClipboardActivity::class.java))
        }

        findViewById<Button>(R.id.btn_skin).setOnClickListener {
            startActivity(Intent(this, SkinActivity::class.java))
        }

        findViewById<Button>(R.id.btn_backup).setOnClickListener {
            backupUserDict()
        }

        findViewById<Button>(R.id.btn_restore).setOnClickListener {
            restoreUserDict()
        }

        findViewById<Button>(R.id.btn_clear).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("确认清除")
                .setMessage("这将清除所有用户数据和设置，确定继续？")
                .setPositiveButton("确定") { _, _ ->
                    clearAllData()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun backupUserDict() {
        val dbFile = getDatabasePath("ime_dict.db")
        val backupDir = getExternalFilesDir("backups")
        if (backupDir != null) {
            if (!backupDir.exists()) backupDir.mkdirs()
            val backupFile = java.io.File(backupDir, "user_dict_backup_${System.currentTimeMillis()}.db")
            dbFile.copyTo(backupFile, overwrite = true)
            Toast.makeText(this, "备份成功: ${backupFile.absolutePath}", Toast.LENGTH_LONG).show()
        }
    }

    private fun restoreUserDict() {
        val backupDir = getExternalFilesDir("backups")
        if (backupDir == null || !backupDir.exists()) {
            Toast.makeText(this, "未找到备份文件", Toast.LENGTH_SHORT).show()
            return
        }

        val backups = backupDir.listFiles { f -> f.name.endsWith(".db") } ?: emptyArray()
        if (backups.isEmpty()) {
            Toast.makeText(this, "未找到备份文件", Toast.LENGTH_SHORT).show()
            return
        }

        val items = backups.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择备份")
            .setItems(items) { _, which ->
                val dbFile = getDatabasePath("ime_dict.db")
                backups[which].copyTo(dbFile, overwrite = true)
                Toast.makeText(this, "恢复成功", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun clearAllData() {
        // 清除用户词库
        deleteDatabase("ime_dict.db")
        // 清除剪贴板
        deleteDatabase("clipboard.db")
        // 清除设置
        getSharedPreferences("ime_settings", MODE_PRIVATE).edit().clear().apply()
        getSharedPreferences("symbol_prefs", MODE_PRIVATE).edit().clear().apply()

        Toast.makeText(this, "所有数据已清除", Toast.LENGTH_SHORT).show()
    }
}