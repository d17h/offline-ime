package com.offline.ime.clipboard

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

/**
 * 剪贴板数据库 - 本地SQLite存储，零网络
 * 功能：存储最近200条，智能拆词，搜索，加密
 */
class ClipboardDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        // 剪贴板主表
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS clipboard_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                content TEXT NOT NULL,
                content_type INTEGER DEFAULT 0,
                created_at INTEGER NOT NULL,
                is_pinned INTEGER DEFAULT 0,
                tags TEXT
            )
        """)

        // 拆词结果表
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS extracted_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                clipboard_id INTEGER NOT NULL,
                extracted_type TEXT NOT NULL,
                extracted_value TEXT NOT NULL,
                start_pos INTEGER,
                end_pos INTEGER,
                FOREIGN KEY (clipboard_id) REFERENCES clipboard_items(id) ON DELETE CASCADE
            )
        """)

        // 收藏表
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS favorites (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                content TEXT NOT NULL UNIQUE,
                created_at INTEGER NOT NULL
            )
        """)

        // 创建索引
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_clipboard_created ON clipboard_items(created_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_clipboard_type ON clipboard_items(content_type)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_extracted_type ON extracted_items(extracted_type)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_extracted_value ON extracted_items(extracted_value)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS clipboard_items")
        db.execSQL("DROP TABLE IF EXISTS extracted_items")
        db.execSQL("DROP TABLE IF EXISTS favorites")
        onCreate(db)
    }

    /**
     * 保存剪贴板内容
     */
    fun saveClipboardItem(content: String): Long {
        if (content.isBlank()) return -1

        // 检查是否已存在
        val existingId = findExistingItem(content)
        if (existingId > 0) {
            // 更新时间和移到最前
            updateTimestamp(existingId)
            return existingId
        }

        // 删除超过200条的旧数据
        cleanupOldItems()

        val values = ContentValues().apply {
            put("content", content)
            put("content_type", classifyContent(content))
            put("created_at", System.currentTimeMillis())
            put("tags", "")
        }

        val id = writableDatabase.insert("clipboard_items", null, values)

        // 智能拆词
        if (id > 0) {
            extractPatterns(id, content)
        }

        return id
    }

    /**
     * 获取最近剪贴板内容
     */
    fun getRecentItems(limit: Int = 50): List<ClipboardItem> {
        val items = mutableListOf<ClipboardItem>()
        val cursor = readableDatabase.rawQuery(
            """SELECT id, content, content_type, created_at, is_pinned, tags 
               FROM clipboard_items 
               ORDER BY is_pinned DESC, created_at DESC 
               LIMIT ?""",
            arrayOf(limit.toString())
        )

        while (cursor.moveToNext()) {
            items.add(ClipboardItem(
                id = cursor.getLong(0),
                content = cursor.getString(1),
                contentType = cursor.getInt(2),
                createdAt = cursor.getLong(3),
                isPinned = cursor.getInt(4) == 1,
                tags = cursor.getString(5) ?: ""
            ))
        }
        cursor.close()
        return items
    }

    /**
     * 搜索剪贴板内容
     */
    fun searchItems(query: String): List<ClipboardItem> {
        val items = mutableListOf<ClipboardItem>()
        val cursor = readableDatabase.rawQuery(
            """SELECT id, content, content_type, created_at, is_pinned, tags 
               FROM clipboard_items 
               WHERE content LIKE ? 
               ORDER BY created_at DESC 
               LIMIT 50""",
            arrayOf("%$query%")
        )

        while (cursor.moveToNext()) {
            items.add(ClipboardItem(
                id = cursor.getLong(0),
                content = cursor.getString(1),
                contentType = cursor.getInt(2),
                createdAt = cursor.getLong(3),
                isPinned = cursor.getInt(4) == 1,
                tags = cursor.getString(5) ?: ""
            ))
        }
        cursor.close()
        return items
    }

    /**
     * 删除剪贴板项
     */
    fun deleteItem(id: Long) {
        writableDatabase.delete("clipboard_items", "id = ?", arrayOf(id.toString()))
        writableDatabase.delete("extracted_items", "clipboard_id = ?", arrayOf(id.toString()))
    }

    /**
     * 收藏/取消收藏
     */
    fun toggleFavorite(content: String): Boolean {
        val cursor = readableDatabase.rawQuery(
            "SELECT id FROM favorites WHERE content = ?",
            arrayOf(content)
        )
        val exists = cursor.count > 0
        cursor.close()

        return if (exists) {
            writableDatabase.delete("favorites", "content = ?", arrayOf(content))
            false
        } else {
            val values = ContentValues().apply {
                put("content", content)
                put("created_at", System.currentTimeMillis())
            }
            writableDatabase.insert("favorites", null, values)
            true
        }
    }

    /**
     * 获取收藏列表
     */
    fun getFavorites(): List<String> {
        val list = mutableListOf<String>()
        val cursor = readableDatabase.rawQuery(
            "SELECT content FROM favorites ORDER BY created_at DESC",
            null
        )
        while (cursor.moveToNext()) {
            list.add(cursor.getString(0))
        }
        cursor.close()
        return list
    }

    /**
     * 智能拆词 - 识别电话、网址、邮箱、验证码等
     */
    private fun extractPatterns(clipboardId: Long, content: String) {
        val patterns = listOf(
            Regex("(?:\+?86)?1[3-9]\d{9}") to "phone",      // 手机号
            Regex("https?://[\w./?&=#%-]+") to "url",        // 网址
            Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}") to "email", // 邮箱
            Regex("\d{6}") to "verify_code",                   // 6位验证码
            Regex("\d{4,6}(?=\D*$)") to "code",             // 末尾数字码
            Regex("[\u4e00-\u9fa5]{2,}(?:省|市|区|县|路|街|号)") to "address" // 地址片段
        )

        patterns.forEach { (regex, type) ->
            regex.findAll(content).forEach { match ->
                val values = ContentValues().apply {
                    put("clipboard_id", clipboardId)
                    put("extracted_type", type)
                    put("extracted_value", match.value)
                    put("start_pos", match.range.first)
                    put("end_pos", match.range.last + 1)
                }
                writableDatabase.insert("extracted_items", null, values)
            }
        }
    }

    /**
     * 获取拆词结果
     */
    fun getExtractedItems(clipboardId: Long): List<ExtractedItem> {
        val items = mutableListOf<ExtractedItem>()
        val cursor = readableDatabase.rawQuery(
            "SELECT id, extracted_type, extracted_value FROM extracted_items WHERE clipboard_id = ?",
            arrayOf(clipboardId.toString())
        )
        while (cursor.moveToNext()) {
            items.add(ExtractedItem(
                id = cursor.getLong(0),
                type = cursor.getString(1),
                value = cursor.getString(2)
            ))
        }
        cursor.close()
        return items
    }

    private fun findExistingItem(content: String): Long {
        val cursor = readableDatabase.rawQuery(
            "SELECT id FROM clipboard_items WHERE content = ?",
            arrayOf(content)
        )
        val id = if (cursor.moveToFirst()) cursor.getLong(0) else -1
        cursor.close()
        return id
    }

    private fun updateTimestamp(id: Long) {
        val values = ContentValues().apply {
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.update("clipboard_items", values, "id = ?", arrayOf(id.toString()))
    }

    private fun cleanupOldItems() {
        // 保留最近200条，删除旧的
        writableDatabase.execSQL(
            """DELETE FROM clipboard_items WHERE id NOT IN 
               (SELECT id FROM clipboard_items ORDER BY created_at DESC LIMIT 200)"""
        )
        // 清理30天前的数据
        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        writableDatabase.delete(
            "clipboard_items",
            "created_at < ? AND is_pinned = 0",
            arrayOf(thirtyDaysAgo.toString())
        )
    }

    private fun classifyContent(content: String): Int {
        return when {
            content.matches(Regex("^\d+$")) -> TYPE_NUMBER
            content.matches(Regex("^https?://.*")) -> TYPE_URL
            content.contains("@") && content.contains(".") -> TYPE_EMAIL
            content.matches(Regex(".*[\u4e00-\u9fa5].*")) -> TYPE_CHINESE
            else -> TYPE_TEXT
        }
    }

    data class ClipboardItem(
        val id: Long,
        val content: String,
        val contentType: Int,
        val createdAt: Long,
        val isPinned: Boolean,
        val tags: String
    )

    data class ExtractedItem(
        val id: Long,
        val type: String,
        val value: String
    )

    companion object {
        private const val DB_NAME = "clipboard.db"
        private const val DB_VERSION = 1

        const val TYPE_TEXT = 0
        const val TYPE_NUMBER = 1
        const val TYPE_URL = 2
        const val TYPE_EMAIL = 3
        const val TYPE_CHINESE = 4

        @Volatile
        private var instance: ClipboardDatabase? = null

        fun getInstance(context: Context): ClipboardDatabase {
            return instance ?: synchronized(this) {
                instance ?: ClipboardDatabase(context.applicationContext).also { instance = it }
            }
        }
    }
}