package com.offline.ime.engine

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.PriorityQueue

/**
 * 词库管理器 - 完全离线的拼音/笔画/T9输入引擎
 * 内存预算：25MB（运行时）
 */
class DictionaryManager private constructor(private val context: Context) {

    private var database: SQLiteDatabase? = null
    private val userWordFreq = ConcurrentHashMap<String, Int>()
    private val cache = LruCache<String, List<String>>(100)
    private var isInitialized = false

    // 模糊音映射表
    private val fuzzyPairs = mapOf(
        'n' to "l", 'l' to "n",
        'z' to "zh", 'zh' to "z",
        'c' to "ch", 'ch' to "c",
        's' to "sh", 'sh' to "s",
        'f' to "h", 'h' to "f",
        'r' to "l", 'l' to "r",
        'an' to "ang", 'ang' to "an",
        'en' to "eng", 'eng' to "en",
        'in' to "ing", 'ing' to "in"
    )

    fun initialize() {
        if (isInitialized) return

        Thread {
            try {
                copyDatabaseFromAssets()
                openDatabase()
                loadUserWordFrequency()
                isInitialized = true
                Log.d(TAG, "Dictionary initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize dictionary", e)
            }
        }.start()
    }

    /**
     * 查询候选词 - 支持拼音/首字母/模糊音
     * 响应时间目标：< 50ms
     */
    fun queryCandidates(input: String): List<String> {
        if (!isInitialized || input.isEmpty()) return emptyList()

        // 检查缓存
        cache.get(input)?.let { return it }

        val candidates = mutableListOf<String>()

        try {
            database?.let { db ->
                // 1. 全拼匹配
                val fullPinyinMatches = queryByPinyin(db, input)
                candidates.addAll(fullPinyinMatches)

                // 2. 首字母缩写匹配
                if (input.length <= 6) {
                    val abbrMatches = queryByAbbreviation(db, input)
                    candidates.addAll(abbrMatches)
                }

                // 3. 模糊音匹配（如果启用）
                val fuzzyMatches = queryWithFuzzyPinyin(db, input)
                candidates.addAll(fuzzyMatches)

                // 4. 用户个性化排序
                val sorted = sortByUserFrequency(candidates.distinct())

                // 存入缓存
                cache.put(input, sorted)
                return sorted
            }
        } catch (e: Exception) {
            Log.e(TAG, "Query failed for input: $input", e)
        }

        return candidates
    }

    /**
     * 记录用户选择的词（学习功能）
     */
    fun learnWord(word: String, pinyin: String = "") {
        val currentFreq = userWordFreq.getOrDefault(word, 0) + 1
        userWordFreq[word] = currentFreq

        // 异步保存到数据库
        Thread {
            try {
                database?.execSQL(
                    "INSERT OR REPLACE INTO user_dict (word, pinyin, frequency, last_used) VALUES (?, ?, ?, ?)",
                    arrayOf(word, pinyin, currentFreq, System.currentTimeMillis())
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to learn word: $word", e)
            }
        }.start()
    }

    /**
     * T9数字查询
     */
    fun queryT9Candidates(digits: String): List<String> {
        if (digits.isEmpty()) return emptyList()

        val possibleLetters = digits.map { digit ->
            T9_MAPPING[digit] ?: ""
        }

        // 生成可能的拼音组合并查询
        val candidates = mutableListOf<String>()
        generateT9Combinations(possibleLetters, 0, "", candidates)
        return candidates.distinct().take(50)
    }

    /**
     * 笔画查询
     */
    fun queryStrokeCandidates(strokes: String): List<String> {
        if (strokes.isEmpty() || database == null) return emptyList()

        return try {
            val cursor = database!!.rawQuery(
                "SELECT word FROM stroke_dict WHERE strokes = ? OR strokes LIKE ? ORDER BY frequency DESC LIMIT 20",
                arrayOf(strokes, "$strokes%")
            )
            val results = mutableListOf<String>()
            while (cursor.moveToNext()) {
                results.add(cursor.getString(0))
            }
            cursor.close()
            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun queryByPinyin(db: SQLiteDatabase, pinyin: String): List<String> {
        val results = mutableListOf<String>()
        try {
            val cursor = db.rawQuery(
                "SELECT word, frequency FROM dict_pinyin WHERE pinyin = ? ORDER BY frequency DESC LIMIT 15",
                arrayOf(pinyin)
            )
            while (cursor.moveToNext()) {
                results.add(cursor.getString(0))
            }
            cursor.close()
        } catch (e: Exception) {
            Log.w(TAG, "Pinyin query failed", e)
        }
        return results
    }

    private fun queryByAbbreviation(db: SQLiteDatabase, abbr: String): List<String> {
        val results = mutableListOf<String>()
        try {
            val cursor = db.rawQuery(
                "SELECT word, frequency FROM dict_pinyin WHERE pinyin_abbr = ? ORDER BY frequency DESC LIMIT 10",
                arrayOf(abbr)
            )
            while (cursor.moveToNext()) {
                results.add(cursor.getString(0))
            }
            cursor.close()
        } catch (e: Exception) {
            Log.w(TAG, "Abbreviation query failed", e)
        }
        return results
    }

    private fun queryWithFuzzyPinyin(db: SQLiteDatabase, input: String): List<String> {
        val results = mutableListOf<String>()
        // 生成模糊音变体
        val fuzzyVariants = generateFuzzyVariants(input)
        for (variant in fuzzyVariants) {
            results.addAll(queryByPinyin(db, variant))
        }
        return results
    }

    private fun generateFuzzyVariants(pinyin: String): List<String> {
        val variants = mutableListOf<String>()
        for ((from, to) in fuzzyPairs) {
            if (pinyin.contains(from)) {
                variants.add(pinyin.replace(from.toString(), to))
            }
        }
        return variants
    }

    private fun sortByUserFrequency(words: List<String>): List<String> {
        return words.sortedWith(compareByDescending { userWordFreq[it] ?: 0 })
    }

    private fun generateT9Combinations(
        letters: List<String>,
        index: Int,
        current: String,
        results: MutableList<String>
    ) {
        if (index == letters.size) {
            // 查询这个词的候选
            database?.let { db ->
                try {
                    val cursor = db.rawQuery(
                        "SELECT word FROM dict_pinyin WHERE pinyin = ? LIMIT 5",
                        arrayOf(current)
                    )
                    while (cursor.moveToNext()) {
                        results.add(cursor.getString(0))
                    }
                    cursor.close()
                } catch (_: Exception) {}
            }
            return
        }

        val currentLetters = letters[index]
        for (i in currentLetters.indices) {
            generateT9Combinations(letters, index + 1, current + currentLetters[i], results)
        }
    }

    private fun copyDatabaseFromAssets() {
        val dbFile = File(context.getDatabasePath(DB_NAME).parent)
        if (!dbFile.exists()) dbFile.mkdirs()

        val dbPath = context.getDatabasePath(DB_NAME)
        if (dbPath.exists() && dbPath.length() > 0) return

        try {
            context.assets.open("dict/$DB_NAME").use { input ->
                FileOutputStream(dbPath).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Database copied from assets")
        } catch (e: Exception) {
            // 如果assets中没有，创建空数据库
            createEmptyDatabase(dbPath)
        }
    }

    private fun openDatabase() {
        val dbPath = context.getDatabasePath(DB_NAME).absolutePath
        database = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READWRITE)
    }

    private fun loadUserWordFrequency() {
        try {
            val cursor = database?.rawQuery("SELECT word, frequency FROM user_dict", null)
            cursor?.let {
                while (it.moveToNext()) {
                    userWordFreq[it.getString(0)] = it.getInt(1)
                }
                it.close()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load user frequency", e)
        }
    }

    private fun createEmptyDatabase(dbPath: File) {
        val db = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
        // 创建词库表
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS dict_pinyin (
                id INTEGER PRIMARY KEY,
                word TEXT,
                pinyin TEXT,
                pinyin_abbr TEXT,
                frequency INTEGER DEFAULT 0
            )
        """)
        // 创建笔画表
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS stroke_dict (
                id INTEGER PRIMARY KEY,
                word TEXT,
                strokes TEXT,
                frequency INTEGER DEFAULT 0
            )
        """)
        // 创建用户词库表
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS user_dict (
                id INTEGER PRIMARY KEY,
                word TEXT UNIQUE,
                pinyin TEXT,
                frequency INTEGER DEFAULT 1,
                last_used INTEGER
            )
        """)
        // 创建索引
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_pinyin ON dict_pinyin(pinyin)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_abbr ON dict_pinyin(pinyin_abbr)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_strokes ON stroke_dict(strokes)")
        db.close()
    }

    companion object {
        private const val TAG = "DictionaryManager"
        private const val DB_NAME = "ime_dict.db"

        // T9数字到字母的映射
        private val T9_MAPPING = mapOf(
            '2' to "abc", '3' to "def", '4' to "ghi",
            '5' to "jkl", '6' to "mno", '7' to "pqrs",
            '8' to "tuv", '9' to "wxyz"
        )

        @Volatile
        private var instance: DictionaryManager? = null

        fun getInstance(context: Context): DictionaryManager {
            return instance ?: synchronized(this) {
                instance ?: DictionaryManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * 简单的LRU缓存实现
     */
    private class LruCache<K, V>(private val maxSize: Int) {
        private val map = LinkedHashMap<K, V>(maxSize, 0.75f, true)

        @Synchronized
        fun get(key: K): V? = map[key]

        @Synchronized
        fun put(key: K, value: V) {
            map[key] = value
            if (map.size > maxSize) {
                val eldest = map.entries.iterator().next()
                map.remove(eldest.key)
            }
        }
    }
}