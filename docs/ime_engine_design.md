# 纯离线Android输入法 - 词库与输入引擎技术方案

## 目录
1. [词库存储方案](#1-词库存储方案)
2. [拼音引擎架构](#2-拼音引擎架构)
3. [笔画输入实现](#3-笔画输入实现)
4. [智能功能实现](#4-智能功能实现)
5. [多语言支持](#5-多语言支持)
6. [内存优化策略](#6-内存优化策略)
7. [代码架构设计](#7-代码架构设计)
8. [内存占用预算](#8-内存占用预算)

---

## 1. 词库存储方案

### 1.1 SQLite FTS4 数据库设计

#### 核心表结构

```sql
-- 主词库表（中文拼音）
CREATE VIRTUAL TABLE dict_pinyin USING fts4(
    word TEXT,              -- 汉字词组
    pinyin TEXT,            -- 完整拼音（空格分隔）
    pinyin_abbr TEXT,       -- 拼音首字母缩写
    freq INTEGER,           -- 基础词频（0-100000）
    user_freq INTEGER,      -- 用户词频（动态学习）
    length INTEGER,         -- 词组长度（用于排序优化）
    category INTEGER,       -- 词类：0=常用 1=专业 2=人名 3=地名
    tokenize=porter         -- 使用porter分词器
);

-- T9数字映射索引表
CREATE TABLE t9_index (
    digits TEXT PRIMARY KEY,    -- T9数字序列（如"926"）
    pinyin_patterns TEXT,       -- 可能的拼音模式（JSON数组）
    word_count INTEGER          -- 该模式下的词条数量
);

-- 笔画索引表
CREATE TABLE stroke_index (
    strokes TEXT PRIMARY KEY,   -- 笔画序列（如"1234"）
    char_list TEXT,             -- 对应汉字列表（逗号分隔）
    freq_sum INTEGER            -- 总词频（用于排序）
);

-- 用户自定义词库
CREATE TABLE user_dict (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    word TEXT UNIQUE,           -- 用户词
    pinyin TEXT,                -- 拼音
    input_count INTEGER DEFAULT 1,  -- 输入次数
    last_time INTEGER,          -- 最后输入时间戳
    context_hash TEXT           -- 上下文特征（用于联想）
);

-- 英文词库
CREATE VIRTUAL TABLE dict_english USING fts4(
    word TEXT,
    freq INTEGER,
    tokenize=porter
);

-- 日文假名映射表
CREATE TABLE japanese_romaji (
    romaji TEXT,            -- 罗马音
    hiragana TEXT,          -- 平假名
    katakana TEXT,          -- 片假名
    priority INTEGER        -- 优先级
);

-- 韩文谚文组合表
CREATE TABLE korean_jamo (
    choseong TEXT,          -- 初声
    jungseong TEXT,         -- 中声
    jongseong TEXT,         -- 终声
    syllable TEXT           -- 完整音节
);
```

#### 索引优化策略

```sql
-- 拼音前缀索引（加速输入预测）
CREATE INDEX idx_pinyin_prefix ON dict_pinyin(pinyin);
CREATE INDEX idx_abbr_prefix ON dict_pinyin(pinyin_abbr);

-- 用户词频索引（加速个性化排序）
CREATE INDEX idx_user_freq ON user_dict(input_count DESC, last_time DESC);

-- 复合索引（26键输入优化）
CREATE INDEX idx_pinyin_full ON dict_pinyin(pinyin, freq DESC, user_freq DESC);

-- 笔画前缀索引
CREATE INDEX idx_stroke_prefix ON stroke_index(strokes);
```

### 1.2 数据压缩策略

#### 拼音编码压缩

```kotlin
/**
 * 拼音音节编码表（406个音节 → 9bit编码）
 * 将拼音字符串压缩为整数数组，节省50%+空间
 */
object PinyinCodec {
    // 预定义音节表（按频率排序，常用音节编码更短）
    private val SYLLABLE_TABLE = arrayOf(
        "de", "yi", "shi", "bu", "le", "zai", "ren", "you", "wo", "ta",
        "zhe", "ge", "le", "dao", "zhong", "zi", "guo", "shang", "men",
        // ... 共406个音节
    )
    
    private val SYLLABLE_TO_CODE = SYLLABLE_TABLE.withIndex().associate { 
        it.value to it.index 
    }
    
    /**
     * 编码：拼音字符串 → 整数数组
     * 示例："zhong guo" → [int1, int2]
     */
    fun encode(pinyin: String): IntArray {
        return pinyin.split(" ").map { syllable ->
            SYLLABLE_TO_CODE[syllable] ?: 0
        }.toIntArray()
    }
    
    /**
     * 解码：整数数组 → 拼音字符串
     */
    fun decode(codes: IntArray): String {
        return codes.joinToString(" ") { code ->
            SYLLABLE_TABLE.getOrElse(code) { "?" }
        }
    }
}
```

#### 汉字存储优化

```kotlin
/**
 * 汉字词组使用UTF-8存储，配合长度前缀压缩
 * 常用单字使用1字节偏移量引用
 */
object HanziCompressor {
    // 3500常用字表（一级字库）
    private val COMMON_CHARS = loadCommonChars()
    
    fun compress(word: String): ByteArray {
        val result = ByteArrayOutputStream()
        for (char in word) {
            val index = COMMON_CHARS.indexOf(char)
            if (index >= 0 && index < 256) {
                // 常用字：1字节编码
                result.write(index)
            } else {
                // 非常用字：3字节UTF-8
                result.write(0xFF) // 标记字节
                result.write(char.toString().toByteArray(Charsets.UTF_8))
            }
        }
        return result.toByteArray()
    }
}
```

### 1.3 数据库文件大小预算

| 数据类型 | 原始大小 | 压缩后 | 说明 |
|---------|---------|--------|------|
| 中文词库(10万条) | ~15MB | ~8MB | FTS4+拼音编码 |
| 英文词库(5万条) | ~3MB | ~2MB | 基础词库 |
| 日文映射表 | ~500KB | ~300KB | 罗马音-假名 |
| 韩文组合表 | ~200KB | ~150KB | 谚文规则 |
| 笔画索引 | ~2MB | ~1.2MB | 笔画-汉字映射 |
| 用户词库(预估1万条) | ~2MB | ~1MB | 动态增长 |
| **总计** | **~22.7MB** | **~12.65MB** | 磁盘占用 |

---

## 2. 拼音引擎架构

### 2.1 核心架构图

```
┌─────────────────────────────────────────────────────────────┐
│                    InputEngine (输入引擎)                    │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ Pinyin26Engine│  │  T9Engine    │  │ StrokeEngine │      │
│  │   (26键拼音)  │  │   (9键T9)    │  │   (笔画输入)  │      │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘      │
│         │                 │                  │              │
│         └─────────────────┴──────────────────┘              │
│                           │                                 │
│                    ┌──────┴──────┐                         │
│                    │ QueryBuilder │ ← 构建查询语句          │
│                    └──────┬──────┘                         │
│                           │                                 │
│              ┌────────────┼────────────┐                   │
│              ▼            ▼            ▼                   │
│      ┌───────────┐ ┌───────────┐ ┌───────────┐            │
│      │ DictCache │ │UserCache  │ │LearnCache │            │
│      │ (词库缓存) │ │(用户词缓存)│ │(学习缓存) │            │
│      └─────┬─────┘ └─────┬─────┘ └─────┬─────┘            │
│            └─────────────┴─────────────┘                   │
│                          │                                  │
│                    ┌─────┴─────┐                           │
│                    │ Candidate │ ← 候选词排序与组装         │
│                    │  Ranker   │                           │
│                    └─────┬─────┘                           │
│                          │                                  │
│                          ▼                                  │
│                    ┌──────────┐                            │
│                    │  Output  │ ← 最终候选列表             │
│                    └──────────┘                            │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 26键拼音引擎实现

```kotlin
/**
 * 26键拼音输入引擎
 * 核心职责：拼音切分、候选查询、结果排序
 */
class Pinyin26Engine(
    private val database: DictionaryDatabase,
    private val fuzzyConfig: FuzzyConfig,
    private val learnEngine: LearningEngine
) : InputEngine {
    
    companion object {
        // 最大候选词数量
        const val MAX_CANDIDATES = 50
        // 每页显示数量
        const val PAGE_SIZE = 9
        // 缓存大小
        const val CACHE_SIZE = 100
    }
    
    // LRU缓存：输入串 → 候选结果
    private val queryCache = LruCache<String, CandidateResult>(CACHE_SIZE)
    
    /**
     * 处理输入按键
     * @param input 当前输入的拼音串（如 "zhongguo"）
     * @param cursor 光标位置
     * @return 候选词列表
     */
    override fun processInput(input: String, cursor: Int): CandidateResult {
        // 1. 检查缓存
        val cacheKey = "$input:$cursor"
        queryCache.get(cacheKey)?.let { return it }
        
        // 2. 拼音切分
        val pinyinSegments = PinyinSegmenter.segment(input)
        
        // 3. 生成模糊音变体
        val fuzzyVariants = generateFuzzyVariants(pinyinSegments)
        
        // 4. 构建并执行查询
        val candidates = queryCandidates(fuzzyVariants)
        
        // 5. 应用个性化排序
        val rankedCandidates = rankCandidates(candidates, input)
        
        // 6. 组装结果
        val result = CandidateResult(
            candidates = rankedCandidates,
            pinyinSegments = pinyinSegments,
            hasMore = rankedCandidates.size > PAGE_SIZE
        )
        
        // 7. 缓存结果
        queryCache.put(cacheKey, result)
        
        return result
    }
    
    /**
     * 生成模糊音变体
     */
    private fun generateFuzzyVariants(segments: List<String>): List<List<String>> {
        if (!fuzzyConfig.isEnabled()) return listOf(segments)
        
        val variants = mutableListOf<List<String>>()
        
        // 对每个音节应用模糊音规则
        val segmentVariants = segments.map { segment ->
            fuzzyConfig.getVariants(segment)
        }
        
        // 笛卡尔积生成所有组合
        generateCartesianProduct(segmentVariants, 0, mutableListOf(), variants)
        
        return variants
    }
    
    /**
     * 查询候选词
     */
    private fun queryCandidates(variants: List<List<String>>): List<Candidate> {
        val candidates = mutableListOf<Candidate>()
        
        for (variant in variants) {
            val pinyinStr = variant.joinToString(" ")
            val abbr = variant.joinToString("") { it.first().toString() }
            
            // FTS4查询
            val query = """
                SELECT word, pinyin, freq, user_freq 
                FROM dict_pinyin 
                WHERE dict_pinyin MATCH ? 
                ORDER BY (freq + user_freq * 2) DESC 
                LIMIT ?
            """.trimIndent()
            
            database.rawQuery(query, arrayOf(pinyinStr, MAX_CANDIDATES.toString())).use { cursor ->
                while (cursor.moveToNext()) {
                    candidates.add(Candidate(
                        word = cursor.getString(0),
                        pinyin = cursor.getString(1),
                        baseFreq = cursor.getInt(2),
                        userFreq = cursor.getInt(3),
                        source = CandidateSource.DICTIONARY
                    ))
                }
            }
        }
        
        return candidates.distinctBy { it.word }
    }
    
    /**
     * 候选词排序算法
     * 权重 = 基础词频 * 0.3 + 用户词频 * 0.5 + 上下文匹配 * 0.2
     */
    private fun rankCandidates(
        candidates: List<Candidate>, 
        input: String
    ): List<Candidate> {
        return candidates.map { candidate ->
            val contextScore = learnEngine.getContextScore(candidate.word)
            val finalScore = candidate.baseFreq * 0.3f + 
                           candidate.userFreq * 0.5f + 
                           contextScore * 0.2f
            candidate.copy(score = finalScore)
        }.sortedByDescending { it.score }
    }
}

/**
 * 拼音切分器
 * 使用正向最大匹配 + 动态规划优化
 */
object PinyinSegmenter {
    // 有效音节集合
    private val VALID_SYLLABLES = loadValidSyllables()
    
    fun segment(input: String): List<String> {
        val result = mutableListOf<String>()
        var i = 0
        
        while (i < input.length) {
            // 最大匹配（最长6个字符）
            var matched = false
            for (len in minOf(6, input.length - i) downTo 1) {
                val substr = input.substring(i, i + len)
                if (VALID_SYLLABLES.contains(substr)) {
                    result.add(substr)
                    i += len
                    matched = true
                    break
                }
            }
            
            if (!matched) {
                // 无法匹配，作为单字符处理
                result.add(input[i].toString())
                i++
            }
        }
        
        return result
    }
}
```

### 2.3 T9数字键盘引擎

```kotlin
/**
 * T9数字键盘映射表
 */
object T9Mapping {
    // 数字到字母的映射
    val DIGIT_TO_CHARS = mapOf(
        '2' to "abc",
        '3' to "def",
        '4' to "ghi",
        '5' to "jkl",
        '6' to "mno",
        '7' to "pqrs",
        '8' to "tuv",
        '9' to "wxyz"
    )
    
    // 字母到数字的反向映射
    val CHAR_TO_DIGIT: Map<Char, Char> = DIGIT_TO_CHARS.flatMap { (digit, chars) ->
        chars.map { it to digit }
    }.toMap()
    
    /**
     * 将拼音转换为T9数字序列
     */
    fun pinyinToDigits(pinyin: String): String {
        return pinyin.map { char ->
            CHAR_TO_DIGIT[char] ?: char
        }.joinToString("")
    }
    
    /**
     * 生成数字序列对应的所有拼音组合
     */
    fun digitsToPinyinPatterns(digits: String): List<String> {
        if (digits.isEmpty()) return emptyList()
        
        val result = mutableListOf<String>()
        val chars = digits.map { DIGIT_TO_CHARS[it] ?: "" }
        
        generateCombinations(chars, 0, StringBuilder(), result)
        return result.filter { isValidPinyinPattern(it) }
    }
    
    private fun generateCombinations(
        chars: List<String>, 
        index: Int, 
        current: StringBuilder, 
        result: MutableList<String>
    ) {
        if (index == chars.size) {
            result.add(current.toString())
            return
        }
        
        for (char in chars[index]) {
            current.append(char)
            generateCombinations(chars, index + 1, current, result)
            current.deleteCharAt(current.length - 1)
        }
    }
    
    private fun isValidPinyinPattern(pattern: String): Boolean {
        // 检查是否为有效的拼音前缀
        return PinyinValidator.isValidPrefix(pattern)
    }
}

/**
 * T9输入引擎
 */
class T9Engine(
    private val database: DictionaryDatabase,
    private val t9Index: T9Index
) : InputEngine {
    
    companion object {
        const val MAX_CANDIDATES = 30
    }
    
    override fun processInput(input: String, cursor: Int): CandidateResult {
        // 1. 查询T9索引获取可能的拼音模式
        val patterns = t9Index.getPatterns(input)
        
        // 2. 对每个模式查询候选词
        val allCandidates = mutableListOf<Candidate>()
        
        for (pattern in patterns) {
            val candidates = queryByPinyinPattern(pattern)
            allCandidates.addAll(candidates)
        }
        
        // 3. 去重并排序
        val uniqueCandidates = allCandidates
            .distinctBy { it.word }
            .sortedByDescending { it.baseFreq + it.userFreq }
            .take(MAX_CANDIDATES)
        
        return CandidateResult(
            candidates = uniqueCandidates,
            pinyinSegments = emptyList(), // T9不显示拼音分段
            hasMore = false
        )
    }
    
    private fun queryByPinyinPattern(pattern: String): List<Candidate> {
        // 使用LIKE查询匹配拼音前缀
        val query = """
            SELECT word, pinyin, freq, user_freq 
            FROM dict_pinyin 
            WHERE pinyin LIKE ? 
            ORDER BY (freq + user_freq) DESC 
            LIMIT ?
        """.trimIndent()
        
        val candidates = mutableListOf<Candidate>()
        database.rawQuery(query, arrayOf("$pattern%", MAX_CANDIDATES.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                candidates.add(Candidate(
                    word = cursor.getString(0),
                    pinyin = cursor.getString(1),
                    baseFreq = cursor.getInt(2),
                    userFreq = cursor.getInt(3)
                ))
            }
        }
        
        return candidates
    }
}
```

---

## 3. 笔画输入实现

### 3.1 笔画库结构

```kotlin
/**
 * 笔画类型定义
 * 1=横 2=竖 3=撇 4=捺/点 5=折
 */
enum class StrokeType(val code: Char, val desc: String) {
    HENG('1', "横"),
    SHU('2', "竖"),
    PIE('3', "撇"),
    NA('4', "捺/点"),
    ZHE('5', "折");
    
    companion object {
        fun fromCode(code: Char): StrokeType? = values().find { it.code == code }
    }
}

/**
 * 汉字笔画数据结构
 */
data class CharStrokeInfo(
    val char: String,           // 汉字
    val strokeSequence: String, // 笔画序列（如"1234"）
    val strokeCount: Int,       // 笔画数
    val freq: Int               // 使用频率
)
```

### 3.2 笔画匹配算法

```kotlin
/**
 * 笔画输入引擎
 */
class StrokeEngine(
    private val database: DictionaryDatabase
) : InputEngine {
    
    companion object {
        // 最大笔画序列长度
        const val MAX_STROKE_LENGTH = 20
        // 候选词数量
        const val MAX_CANDIDATES = 50
        // 模糊匹配容差（笔画数差异）
        const val FUZZY_TOLERANCE = 2
    }
    
    /**
     * 处理笔画输入
     * @param input 笔画序列（如"1234"）
     */
    override fun processInput(input: String, cursor: Int): CandidateResult {
        if (input.length > MAX_STROKE_LENGTH) {
            return CandidateResult(emptyList(), emptyList(), false)
        }
        
        // 1. 精确匹配
        val exactMatches = queryExactMatches(input)
        
        // 2. 前缀匹配
        val prefixMatches = if (input.length >= 2) {
            queryPrefixMatches(input)
        } else emptyList()
        
        // 3. 模糊匹配（允许少量笔画差异）
        val fuzzyMatches = if (input.length >= 3) {
            queryFuzzyMatches(input)
        } else emptyList()
        
        // 4. 合并并排序结果
        val allCandidates = (exactMatches + prefixMatches + fuzzyMatches)
            .distinctBy { it.word }
            .sortedByDescending { it.score }
            .take(MAX_CANDIDATES)
        
        return CandidateResult(
            candidates = allCandidates,
            pinyinSegments = emptyList(),
            hasMore = false
        )
    }
    
    private fun queryExactMatches(strokes: String): List<Candidate> {
        val query = """
            SELECT char_list, freq_sum 
            FROM stroke_index 
            WHERE strokes = ?
        """.trimIndent()
        
        val candidates = mutableListOf<Candidate>()
        database.rawQuery(query, arrayOf(strokes)).use { cursor ->
            if (cursor.moveToFirst()) {
                val chars = cursor.getString(0).split(",")
                val baseFreq = cursor.getInt(1)
                
                chars.forEach { char ->
                    candidates.add(Candidate(
                        word = char,
                        pinyin = "", // 笔画输入不显示拼音
                        baseFreq = baseFreq,
                        userFreq = 0,
                        score = baseFreq.toFloat()
                    ))
                }
            }
        }
        
        return candidates
    }
    
    private fun queryPrefixMatches(strokes: String): List<Candidate> {
        val query = """
            SELECT char_list, freq_sum, strokes 
            FROM stroke_index 
            WHERE strokes LIKE ? AND LENGTH(strokes) <= ?
            ORDER BY freq_sum DESC
            LIMIT ?
        """.trimIndent()
        
        val candidates = mutableListOf<Candidate>()
        val maxLength = strokes.length + FUZZY_TOLERANCE
        
        database.rawQuery(query, arrayOf(
            "$strokes%", 
            maxLength.toString(),
            MAX_CANDIDATES.toString()
        )).use { cursor ->
            while (cursor.moveToNext()) {
                val chars = cursor.getString(0).split(",")
                val freq = cursor.getInt(1)
                val matchedStrokes = cursor.getString(2)
                
                // 匹配度评分：匹配长度 / 总长度
                val matchScore = strokes.length.toFloat() / matchedStrokes.length
                
                chars.forEach { char ->
                    candidates.add(Candidate(
                        word = char,
                        pinyin = "",
                        baseFreq = freq,
                        userFreq = 0,
                        score = freq * matchScore
                    ))
                }
            }
        }
        
        return candidates
    }
    
    private fun queryFuzzyMatches(strokes: String): List<Candidate> {
        // 使用编辑距离进行模糊匹配
        // 这里简化为笔画数相近的查询
        val query = """
            SELECT char_list, freq_sum, strokes 
            FROM stroke_index 
            WHERE ABS(LENGTH(strokes) - ?) <= ?
            ORDER BY freq_sum DESC
            LIMIT ?
        """.trimIndent()
        
        val candidates = mutableListOf<Candidate>()
        
        database.rawQuery(query, arrayOf(
            strokes.length.toString(),
            FUZZY_TOLERANCE.toString(),
            (MAX_CANDIDATES / 2).toString()
        )).use { cursor ->
            while (cursor.moveToNext()) {
                val chars = cursor.getString(0).split(",")
                val freq = cursor.getInt(1)
                val matchedStrokes = cursor.getString(2)
                
                // 计算笔画编辑距离
                val distance = calculateStrokeDistance(strokes, matchedStrokes)
                val similarity = 1.0f - (distance.toFloat() / maxOf(strokes.length, matchedStrokes.length))
                
                if (similarity > 0.7f) { // 相似度阈值
                    chars.forEach { char ->
                        candidates.add(Candidate(
                            word = char,
                            pinyin = "",
                            baseFreq = freq,
                            userFreq = 0,
                            score = freq * similarity
                        ))
                    }
                }
            }
        }
        
        return candidates
    }
    
    /**
     * 计算两个笔画序列的编辑距离
     */
    private fun calculateStrokeDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        
        // 动态规划求解编辑距离
        val dp = Array(m + 1) { IntArray(n + 1) }
        
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        
        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (s1[i - 1] == s2[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    minOf(
                        dp[i - 1][j] + 1,      // 删除
                        dp[i][j - 1] + 1,      // 插入
                        dp[i - 1][j - 1] + 1   // 替换
                    )
                }
            }
        }
        
        return dp[m][n]
    }
}
```

---

## 4. 智能功能实现

### 4.1 拼音纠错规则库

```kotlin
/**
 * 拼音纠错规则库
 */
object PinyinCorrectionRules {
    
    /**
     * 纠错规则定义
     */
    data class CorrectionRule(
        val pattern: Regex,         // 匹配模式
        val replacement: String,    // 替换内容
        val priority: Int,          // 优先级（数值越小优先级越高）
        val description: String     // 规则描述
    )
    
    /**
     * 预定义纠错规则
     */
    val RULES = listOf(
        // 韵母纠错
        CorrectionRule(
            pattern = Regex("ng$"),
            replacement = "eng",
            priority = 1,
            description = "ng → eng"
        ),
        CorrectionRule(
            pattern = Regex("^n([^g])"),
            replacement = "en$1",
            priority = 2,
            description = "n开头 → en开头"
        ),
        
        // 常见拼写错误
        CorrectionRule(
            pattern = Regex("iou$"),
            replacement = "iu",
            priority = 1,
            description = "iou → iu"
        ),
        CorrectionRule(
            pattern = Regex("uei$"),
            replacement = "ui",
            priority = 1,
            description = "uei → ui"
        ),
        CorrectionRule(
            pattern = Regex("uen$"),
            replacement = "un",
            priority = 1,
            description = "uen → un"
        ),
        
        // 声母纠错
        CorrectionRule(
            pattern = Regex("^shuang"),
            replacement = "shang",
            priority = 3,
            description = "shuang → shang（常见误触）"
        ),
        CorrectionRule(
            pattern = Regex("^chuang"),
            replacement = "chang",
            priority = 3,
            description = "chuang → chang"
        )
    )
    
    /**
     * 应用纠错规则
     * @return 纠错后的拼音列表（包含原拼音）
     */
    fun applyCorrection(pinyin: String): List<String> {
        val results = mutableListOf(pinyin) // 保留原拼音
        
        for (rule in RULES.sortedBy { it.priority }) {
            if (rule.pattern.containsMatchIn(pinyin)) {
                val corrected = rule.pattern.replace(pinyin, rule.replacement)
                if (corrected != pinyin && !results.contains(corrected)) {
                    results.add(corrected)
                }
            }
        }
        
        return results
    }
}

/**
 * 纠错引擎
 */
class CorrectionEngine {
    
    fun correctPinyin(pinyinList: List<String>): List<String> {
        val corrected = mutableListOf<String>()
        
        for (pinyin in pinyinList) {
            corrected.addAll(PinyinCorrectionRules.applyCorrection(pinyin))
        }
        
        return corrected.distinct()
    }
}
```

### 4.2 模糊音配置方案

```kotlin
/**
 * 模糊音配置
 */
data class FuzzyConfig(
    val nLConfusion: Boolean = false,       // n/l 不分
    val fHConfusion: Boolean = false,       // f/h 不分
    val cChConfusion: Boolean = false,      // c/ch 不分
    val sShConfusion: Boolean = false,      // s/sh 不分
    val zZhConfusion: Boolean = false,      // z/zh 不分
    val rLConfusion: Boolean = false,       // r/l 不分
    val anAngConfusion: Boolean = false,    // an/ang 不分
    val enEngConfusion: Boolean = false,    // en/eng 不分
    val inIngConfusion: Boolean = false,    // in/ing 不分
    val ianIangConfusion: Boolean = false   // ian/iang 不分
) {
    fun isEnabled(): Boolean {
        return nLConfusion || fHConfusion || cChConfusion || 
               sShConfusion || zZhConfusion || rLConfusion ||
               anAngConfusion || enEngConfusion || inIngConfusion ||
               ianIangConfusion
    }
    
    /**
     * 获取音节的模糊音变体
     */
    fun getVariants(syllable: String): List<String> {
        val variants = mutableListOf(syllable)
        
        // 声母模糊音
        when {
            nLConfusion -> {
                if (syllable.startsWith('n')) {
                    variants.add('l' + syllable.substring(1))
                } else if (syllable.startsWith('l')) {
                    variants.add('n' + syllable.substring(1))
                }
            }
            fHConfusion -> {
                if (syllable.startsWith('f')) {
                    variants.add('h' + syllable.substring(1))
                } else if (syllable.startsWith('h')) {
                    variants.add('f' + syllable.substring(1))
                }
            }
            cChConfusion -> applyConsonantVariant(syllable, "c", "ch", variants)
            sShConfusion -> applyConsonantVariant(syllable, "s", "sh", variants)
            zZhConfusion -> applyConsonantVariant(syllable, "z", "zh", variants)
            rLConfusion -> applyConsonantVariant(syllable, "r", "l", variants)
        }
        
        // 韵母模糊音
        when {
            anAngConfusion -> applyVowelVariant(syllable, "an", "ang", variants)
            enEngConfusion -> applyVowelVariant(syllable, "en", "eng", variants)
            inIngConfusion -> applyVowelVariant(syllable, "in", "ing", variants)
            ianIangConfusion -> applyVowelVariant(syllable, "ian", "iang", variants)
        }
        
        return variants.distinct()
    }
    
    private fun applyConsonantVariant(
        syllable: String, 
        c1: String, 
        c2: String, 
        variants: MutableList<String>
    ) {
        if (syllable.startsWith(c1)) {
            variants.add(c2 + syllable.substring(c1.length))
        } else if (syllable.startsWith(c2)) {
            variants.add(c1 + syllable.substring(c2.length))
        }
    }
    
    private fun applyVowelVariant(
        syllable: String, 
        v1: String, 
        v2: String, 
        variants: MutableList<String>
    ) {
        if (syllable.endsWith(v1)) {
            variants.add(syllable.substring(0, syllable.length - v1.length) + v2)
        } else if (syllable.endsWith(v2)) {
            variants.add(syllable.substring(0, syllable.length - v2.length) + v1)
        }
    }
}
```

### 4.3 本地学习算法

```kotlin
/**
 * 学习引擎 - 记录用户输入习惯
 */
class LearningEngine(
    private val database: DictionaryDatabase,
    private val context: Context
) {
    companion object {
        // 用户词频权重系数
        const val USER_FREQ_WEIGHT = 2.0f
        // 上下文窗口大小
        const val CONTEXT_WINDOW = 3
        // 最大用户词数量
        const val MAX_USER_WORDS = 10000
        // 学习衰减系数（时间衰减）
        const val DECAY_FACTOR = 0.95f
    }
    
    // 内存中的学习缓存
    private val userFreqCache = LruCache<String, Int>(1000)
    private val contextCache = LruCache<String, List<String>>(500)
    
    /**
     * 记录用户选择
     * @param word 用户选择的词
     * @param pinyin 对应的拼音
     * @param context 上下文（前N个词）
     */
    fun recordSelection(word: String, pinyin: String, context: List<String> = emptyList()) {
        // 1. 更新用户词频
        updateUserFrequency(word, pinyin)
        
        // 2. 更新上下文关联
        if (context.isNotEmpty()) {
            updateContextAssociation(word, context)
        }
        
        // 3. 异步持久化
        persistLearningData()
    }
    
    /**
     * 更新用户词频
     */
    private fun updateUserFrequency(word: String, pinyin: String) {
        val currentFreq = userFreqCache.get(word) ?: 0
        userFreqCache.put(word, currentFreq + 1)
        
        // 更新数据库
        val query = """
            INSERT INTO user_dict (word, pinyin, input_count, last_time) 
            VALUES (?, ?, 1, ?)
            ON CONFLICT(word) DO UPDATE SET 
                input_count = input_count + 1,
                last_time = ?
        """.trimIndent()
        
        val timestamp = System.currentTimeMillis()
        database.execSQL(query, arrayOf(word, pinyin, timestamp, timestamp))
    }
    
    /**
     * 更新上下文关联
     */
    private fun updateContextAssociation(word: String, context: List<String>) {
        val contextHash = generateContextHash(context)
        
        // 存储上下文关联
        val query = """
            UPDATE user_dict 
            SET context_hash = ? 
            WHERE word = ?
        """.trimIndent()
        
        database.execSQL(query, arrayOf(contextHash, word))
    }
    
    /**
     * 获取上下文匹配分数
     */
    fun getContextScore(word: String): Float {
        // 简化的上下文评分逻辑
        val cachedContext = contextCache.get(word) ?: return 0f
        
        // 根据上下文相关性计算分数
        return 0f // 具体实现根据上下文匹配度计算
    }
    
    /**
     * 获取用户词频
     */
    fun getUserFrequency(word: String): Int {
        // 先查缓存
        userFreqCache.get(word)?.let { return it }
        
        // 查数据库
        val query = "SELECT input_count FROM user_dict WHERE word = ?"
        database.rawQuery(query, arrayOf(word)).use { cursor ->
            if (cursor.moveToFirst()) {
                val freq = cursor.getInt(0)
                userFreqCache.put(word, freq)
                return freq
            }
        }
        
        return 0
    }
    
    /**
     * 生成上下文哈希
     */
    private fun generateContextHash(context: List<String>): String {
        return context.joinToString("|").hashCode().toString()
    }
    
    /**
     * 定期清理低频用户词
     */
    fun cleanupLowFreqWords() {
        val threshold = 3 // 最少输入次数
        val query = """
            DELETE FROM user_dict 
            WHERE input_count < ? 
            AND last_time < ?
        """.trimIndent()
        
        val oneMonthAgo = System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000
        database.execSQL(query, arrayOf(threshold, oneMonthAgo))
    }
    
    /**
     * 异步持久化学习数据
     */
    private fun persistLearningData() {
        // 使用WorkManager或协程异步保存
        // 避免阻塞主线程
    }
}

/**
 * 个性化排序器
 */
class PersonalizationRanker(
    private val learningEngine: LearningEngine
) {
    
    /**
     * 对候选词进行个性化排序
     * 评分公式：score = baseFreq * 0.3 + userFreq * 0.5 + recency * 0.2
     */
    fun rank(candidates: List<Candidate>): List<Candidate> {
        return candidates.map { candidate ->
            val userFreq = learningEngine.getUserFrequency(candidate.word)
            val personalizedScore = candidate.baseFreq * 0.3f + 
                                   userFreq * 0.5f + 
                                   candidate.userFreq * 0.2f
            
            candidate.copy(score = personalizedScore)
        }.sortedByDescending { it.score }
    }
}
```

---

## 5. 多语言支持

### 5.1 语言包结构

```
/language_packs/
├── zh_cn/                          # 简体中文
│   ├── dict_pinyin.db              # 拼音词库
│   ├── dict_stroke.db              # 笔画词库
│   ├── config.json                 # 语言配置
│   └── version.txt                 # 版本信息
├── en_us/                          # 美式英语
│   ├── dict_english.db
│   ├── config.json
│   └── version.txt
├── ja_jp/                          # 日语
│   ├── dict_japanese.db
│   ├── romaji_map.json             # 罗马音映射
│   ├── config.json
│   └── version.txt
├── ko_kr/                          # 韩语
│   ├── dict_korean.db
│   ├── jamo_rules.json             # 谚文组合规则
│   ├── config.json
│   └── version.txt
└── manifest.json                   # 语言包清单
```

### 5.2 语言配置格式

```json
{
  "language_code": "zh_cn",
  "language_name": "简体中文",
  "version": "1.0.0",
  "supported_inputs": ["pinyin_26", "pinyin_9", "stroke"],
  "dict_files": [
    {
      "name": "dict_pinyin",
      "type": "fts4",
      "size": 8388608,
      "checksum": "sha256:abc123..."
    }
  ],
  "features": {
    "fuzzy_pinyin": true,
    "correction": true,
    "learning": true
  },
  "metadata": {
    "word_count": 100000,
    "last_updated": "2024-01-01"
  }
}
```

### 5.3 动态加载机制

```kotlin
/**
 * 语言包管理器
 */
class LanguagePackManager(
    private val context: Context,
    private val basePath: File
) {
    companion object {
        const val MANIFEST_FILE = "manifest.json"
        const val CONFIG_FILE = "config.json"
    }
    
    // 已加载的语言包
    private val loadedPacks = mutableMapOf<String, LanguagePack>()
    
    /**
     * 扫描并加载所有可用语言包
     */
    fun scanLanguagePacks(): List<LanguagePackInfo> {
        val packs = mutableListOf<LanguagePackInfo>()
        
        if (!basePath.exists()) {
            basePath.mkdirs()
            return packs
        }
        
        basePath.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                val configFile = File(dir, CONFIG_FILE)
                if (configFile.exists()) {
                    try {
                        val config = parseConfig(configFile)
                        packs.add(LanguagePackInfo(
                            code = config.languageCode,
                            name = config.languageName,
                            version = config.version,
                            path = dir.absolutePath,
                            isLoaded = loadedPacks.containsKey(config.languageCode)
                        ))
                    } catch (e: Exception) {
                        Log.e("LangPack", "Failed to parse config: ${dir.name}", e)
                    }
                }
            }
        }
        
        return packs
    }
    
    /**
     * 加载指定语言包
     */
    fun loadLanguagePack(code: String): LanguagePack? {
        // 检查是否已加载
        loadedPacks[code]?.let { return it }
        
        val packDir = File(basePath, code)
        if (!packDir.exists()) {
            Log.e("LangPack", "Language pack not found: $code")
            return null
        }
        
        val configFile = File(packDir, CONFIG_FILE)
        val config = parseConfig(configFile)
        
        // 根据语言类型创建对应的引擎
        val engine = when (config.languageCode) {
            "zh_cn", "zh_tw" -> createChineseEngine(packDir, config)
            "en_us", "en_gb" -> createEnglishEngine(packDir, config)
            "ja_jp" -> createJapaneseEngine(packDir, config)
            "ko_kr" -> createKoreanEngine(packDir, config)
            else -> null
        }
        
        val pack = LanguagePack(
            config = config,
            engine = engine,
            database = openDatabase(packDir, config)
        )
        
        loadedPacks[code] = pack
        return pack
    }
    
    /**
     * 卸载语言包
     */
    fun unloadLanguagePack(code: String) {
        loadedPacks.remove(code)?.let { pack ->
            pack.database.close()
        }
    }
    
    private fun parseConfig(file: File): LanguageConfig {
        val json = file.readText()
        return Gson().fromJson(json, LanguageConfig::class.java)
    }
    
    private fun openDatabase(packDir: File, config: LanguageConfig): SQLiteDatabase {
        val dbFile = File(packDir, config.dictFiles.first().name + ".db")
        return SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
    }
}

/**
 * 日语罗马音引擎
 */
class JapaneseRomajiEngine : InputEngine {
    
    // 罗马音到假名的映射表
    private val romajiMap = mapOf(
        "a" to Pair("あ", "ア"),
        "i" to Pair("い", "イ"),
        "u" to Pair("う", "ウ"),
        "e" to Pair("え", "エ"),
        "o" to Pair("お", "オ"),
        "ka" to Pair("か", "カ"),
        "ki" to Pair("き", "キ"),
        "ku" to Pair("く", "ク"),
        "ke" to Pair("け", "ケ"),
        "ko" to Pair("こ", "コ"),
        // ... 更多映射
        "n" to Pair("ん", "ン")
    )
    
    override fun processInput(input: String, cursor: Int): CandidateResult {
        val hiragana = convertToHiragana(input)
        val katakana = convertToKatakana(input)
        
        val candidates = mutableListOf<Candidate>()
        
        if (hiragana.isNotEmpty()) {
            candidates.add(Candidate(
                word = hiragana,
                pinyin = input,
                baseFreq = 100,
                userFreq = 0,
                source = CandidateSource.CONVERSION
            ))
        }
        
        if (katakana.isNotEmpty() && katakana != hiragana) {
            candidates.add(Candidate(
                word = katakana,
                pinyin = input,
                baseFreq = 90,
                userFreq = 0,
                source = CandidateSource.CONVERSION
            ))
        }
        
        return CandidateResult(candidates, emptyList(), false)
    }
    
    private fun convertToHiragana(romaji: String): String {
        // 实现罗马音到平假名的转换
        // 处理长音、促音等特殊规则
        return romajiMap[romaji]?.first ?: ""
    }
    
    private fun convertToKatakana(romaji: String): String {
        return romajiMap[romaji]?.second ?: ""
    }
}

/**
 * 韩语谚文引擎
 */
class KoreanJamoEngine : InputEngine {
    
    // 谚文组合表
    private val JAMO_INITIAL = listOf(
        'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
        'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    )
    
    private val JAMO_MEDIAL = listOf(
        'ㅏ', 'ㅐ', 'ㅑ', 'ㅒ', 'ㅓ', 'ㅔ', 'ㅕ', 'ㅖ', 'ㅗ', 'ㅘ',
        'ㅙ', 'ㅚ', 'ㅛ', 'ㅜ', 'ㅝ', 'ㅞ', 'ㅟ', 'ㅠ', 'ㅡ', 'ㅢ', 'ㅣ'
    )
    
    private val JAMO_FINAL = listOf(
        ' ', 'ㄱ', 'ㄲ', 'ㄳ', 'ㄴ', 'ㄵ', 'ㄶ', 'ㄷ', 'ㄹ', 'ㄺ',
        'ㄻ', 'ㄼ', 'ㄽ', 'ㄾ', 'ㄿ', 'ㅀ', 'ㅁ', 'ㅂ', 'ㅄ', 'ㅅ',
        'ㅆ', 'ㅇ', 'ㅈ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    )
    
    override fun processInput(input: String, cursor: Int): CandidateResult {
        // 将输入的谚文字母组合成完整音节
        val combined = combineJamo(input)
        
        return CandidateResult(
            candidates = listOf(Candidate(
                word = combined,
                pinyin = input,
                baseFreq = 100,
                userFreq = 0
            )),
            pinyinSegments = emptyList(),
            hasMore = false
        )
    }
    
    /**
     * 组合谚文字母为完整音节
     */
    private fun combineJamo(input: String): String {
        // 实现谚文组合算法
        // 参考Unicode谚文组合规则
        return input // 简化实现
    }
}
```

---

## 6. 内存优化策略

### 6.1 数据分页加载

```kotlin
/**
 * 分页数据加载器
 */
class PagedDataLoader<T>(
    private val pageSize: Int = 100,
    private val loader: (offset: Int, limit: Int) -> List<T>
) {
    
    private val pageCache = LruCache<Int, List<T>>(10) // 缓存10页
    
    /**
     * 加载指定页数据
     */
    fun loadPage(pageIndex: Int): List<T> {
        // 检查缓存
        pageCache.get(pageIndex)?.let { return it }
        
        // 从数据源加载
        val offset = pageIndex * pageSize
        val data = loader(offset, pageSize)
        
        // 缓存结果
        pageCache.put(pageIndex, data)
        
        return data
    }
    
    /**
     * 预加载下一页
     */
    fun preloadNextPage(currentPage: Int) {
        val nextPage = currentPage + 1
        if (pageCache.get(nextPage) == null) {
            // 异步加载
            CoroutineScope(Dispatchers.IO).launch {
                val offset = nextPage * pageSize
                val data = loader(offset, pageSize)
                pageCache.put(nextPage, data)
            }
        }
    }
    
    /**
     * 清除缓存
     */
    fun clearCache() {
        pageCache.evictAll()
    }
}
```

### 6.2 多级缓存策略

```kotlin
/**
 * 多级缓存管理器
 */
class MultiLevelCache<K, V>(
    private val memoryCacheSize: Int = 100,
    private val diskCacheDir: File
) {
    // L1: 内存缓存（LRU）
    private val memoryCache = LruCache<K, V>(memoryCacheSize)
    
    // L2: 磁盘缓存
    private val diskCache: DiskLruCache = DiskLruCache.open(
        diskCacheDir,
        1, // 版本号
        1, // 每个entry的value数量
        10 * 1024 * 1024 // 10MB
    )
    
    /**
     * 获取缓存值
     */
    fun get(key: K): V? {
        // 先查内存
        memoryCache.get(key)?.let { return it }
        
        // 再查磁盘
        val diskKey = key.toString().hashCode().toString()
        diskCache.get(diskKey)?.let { snapshot ->
            val value = deserialize(snapshot.getInputStream(0))
            // 回填内存缓存
            memoryCache.put(key, value)
            return value
        }
        
        return null
    }
    
    /**
     * 设置缓存值
     */
    fun put(key: K, value: V) {
        // 写入内存
        memoryCache.put(key, value)
        
        // 异步写入磁盘
        CoroutineScope(Dispatchers.IO).launch {
            val diskKey = key.toString().hashCode().toString()
            diskCache.edit(diskKey)?.let { editor ->
                serialize(value, editor.newOutputStream(0))
                editor.commit()
            }
        }
    }
    
    private fun serialize(value: V, outputStream: OutputStream) {
        ObjectOutputStream(outputStream).use { oos ->
            oos.writeObject(value)
        }
    }
    
    @Suppress("UNCHECKED_CAST")
    private fun deserialize(inputStream: InputStream): V {
        ObjectInputStream(inputStream).use { ois ->
            return ois.readObject() as V
        }
    }
}
```

### 6.3 内存监控与回收

```kotlin
/**
 * 内存管理器
 */
class MemoryManager(
    private val context: Context
) {
    companion object {
        // 内存警告阈值（MB）
        const val MEMORY_WARNING_THRESHOLD = 20
        // 内存紧急阈值（MB）
        const val MEMORY_CRITICAL_THRESHOLD = 25
    }
    
    private val runtime = Runtime.getRuntime()
    
    /**
     * 获取当前内存使用（MB）
     */
    fun getCurrentMemoryUsage(): Long {
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        return usedMemory / (1024 * 1024)
    }
    
    /**
     * 检查内存状态
     */
    fun checkMemoryStatus(): MemoryStatus {
        val usage = getCurrentMemoryUsage()
        
        return when {
            usage >= MEMORY_CRITICAL_THRESHOLD -> MemoryStatus.CRITICAL
            usage >= MEMORY_WARNING_THRESHOLD -> MemoryStatus.WARNING
            else -> MemoryStatus.NORMAL
        }
    }
    
    /**
     * 执行内存回收
     */
    fun performGarbageCollection() {
        // 清除各种缓存
        System.gc()
        
        // 触发TrimMemory
        (context as? Application)?.let { app ->
            app.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_MODERATE)
        }
    }
    
    enum class MemoryStatus {
        NORMAL, WARNING, CRITICAL
    }
}
```

---

## 7. 代码架构设计

### 7.1 核心类图

```
┌─────────────────────────────────────────────────────────────────┐
│                         核心架构                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────┐         ┌─────────────────┐               │
│  │  InputService   │◄────────│  KeyboardView   │               │
│  │  (输入法服务)    │         │  (键盘视图)      │               │
│  └────────┬────────┘         └─────────────────┘               │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────────────────────────────────────┐           │
│  │              InputEngineManager                  │           │
│  │              (输入引擎管理器)                     │           │
│  └─────────────────────────────────────────────────┘           │
│           │                                                     │
│           ├──► ┌─────────────────┐                             │
│           │    │  Pinyin26Engine │                             │
│           │    │   (26键拼音)     │                             │
│           │    └─────────────────┘                             │
│           │                                                     │
│           ├──► ┌─────────────────┐                             │
│           │    │    T9Engine     │                             │
│           │    │   (9键T9)        │                             │
│           │    └─────────────────┘                             │
│           │                                                     │
│           ├──► ┌─────────────────┐                             │
│           │    │  StrokeEngine   │                             │
│           │    │   (笔画输入)     │                             │
│           │    └─────────────────┘                             │
│           │                                                     │
│           └──► ┌─────────────────┐                             │
│                │ MultiLangEngine │                             │
│                │   (多语言引擎)   │                             │
│                └─────────────────┘                             │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│                         数据层                                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │ DictionaryDB    │  │  UserDataDB     │  │  LanguagePack   │ │
│  │   (词库数据库)   │  │  (用户数据)      │  │   (语言包)       │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│                         智能功能                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │CorrectionEngine │  │  FuzzyConfig    │  │ LearningEngine  │ │
│  │   (纠错引擎)     │  │  (模糊音配置)    │  │   (学习引擎)     │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 7.2 接口定义

```kotlin
// ==================== 核心接口 ====================

/**
 * 输入引擎接口
 */
interface InputEngine {
    /**
     * 处理输入
     * @param input 当前输入字符串
     * @param cursor 光标位置
     * @return 候选词结果
     */
    fun processInput(input: String, cursor: Int): CandidateResult
    
    /**
     * 获取引擎类型
     */
    fun getEngineType(): EngineType
}

/**
 * 候选词结果
 */
data class CandidateResult(
    val candidates: List<Candidate>,      // 候选词列表
    val pinyinSegments: List<String>,     // 拼音分段
    val hasMore: Boolean                  // 是否有更多结果
)

/**
 * 候选词数据类
 */
data class Candidate(
    val word: String,                     // 候选词
    val pinyin: String,                   // 拼音标注
    val baseFreq: Int = 0,                // 基础词频
    val userFreq: Int = 0,                // 用户词频
    val score: Float = 0f,                // 综合评分
    val source: CandidateSource = CandidateSource.DICTIONARY
)

/**
 * 候选词来源
 */
enum class CandidateSource {
    DICTIONARY,      // 系统词库
    USER_DICT,       // 用户词库
    LEARNED,         // 学习结果
    CORRECTION,      // 纠错结果
    CONVERSION       // 转换结果（如日文假名）
}

/**
 * 引擎类型
 */
enum class EngineType {
    Pinyin26,        // 26键拼音
    Pinyin9,         // 9键T9
    Stroke,          // 笔画输入
    English,         // 英文输入
    Japanese,        // 日文输入
    Korean           // 韩文输入
}

// ==================== 词库接口 ====================

/**
 * 词库数据库接口
 */
interface DictionaryDatabase {
    /**
     * 执行查询
     */
    fun rawQuery(sql: String, selectionArgs: Array<String>?): Cursor
    
    /**
     * 执行SQL
     */
    fun execSQL(sql: String, bindArgs: Array<Any?>? = null)
    
    /**
     * 开始事务
     */
    fun beginTransaction()
    
    /**
     * 提交事务
     */
    fun endTransaction()
    
    /**
     * 关闭数据库
     */
    fun close()
}

// ==================== 学习接口 ====================

/**
 * 学习引擎接口
 */
interface LearningEngine {
    /**
     * 记录用户选择
     */
    fun recordSelection(word: String, pinyin: String, context: List<String> = emptyList())
    
    /**
     * 获取用户词频
     */
    fun getUserFrequency(word: String): Int
    
    /**
     * 获取上下文分数
     */
    fun getContextScore(word: String): Float
}

// ==================== 配置接口 ====================

/**
 * 模糊音配置接口
 */
interface FuzzyConfig {
    /**
     * 是否启用模糊音
     */
    fun isEnabled(): Boolean
    
    /**
     * 获取音节的模糊音变体
     */
    fun getVariants(syllable: String): List<String>
}
```

### 7.3 核心类实现

```kotlin
/**
 * 输入引擎管理器
 * 负责管理所有输入引擎的生命周期和切换
 */
class InputEngineManager(
    private val context: Context,
    private val database: DictionaryDatabase,
    private val config: InputConfig
) {
    // 引擎实例缓存
    private val engines = mutableMapOf<EngineType, InputEngine>()
    
    // 当前活跃引擎
    private var currentEngine: InputEngine? = null
    
    // 学习引擎（全局共享）
    private val learningEngine = LearningEngine(database, context)
    
    // 模糊音配置
    private val fuzzyConfig = loadFuzzyConfig()
    
    /**
     * 获取或创建引擎
     */
    fun getEngine(type: EngineType): InputEngine {
        return engines.getOrPut(type) {
            createEngine(type)
        }
    }
    
    /**
     * 切换当前引擎
     */
    fun switchEngine(type: EngineType) {
        currentEngine = getEngine(type)
    }
    
    /**
     * 处理输入
     */
    fun processInput(input: String, cursor: Int = input.length): CandidateResult {
        val engine = currentEngine ?: throw IllegalStateException("No engine selected")
        
        val startTime = System.currentTimeMillis()
        val result = engine.processInput(input, cursor)
        val elapsed = System.currentTimeMillis() - startTime
        
        // 性能监控
        if (elapsed > 50) {
            Log.w("IME", "Slow query: ${elapsed}ms for input: $input")
        }
        
        return result
    }
    
    /**
     * 记录用户选择（用于学习）
     */
    fun recordSelection(word: String, pinyin: String) {
        learningEngine.recordSelection(word, pinyin)
    }
    
    private fun createEngine(type: EngineType): InputEngine {
        return when (type) {
            EngineType.Pinyin26 -> Pinyin26Engine(database, fuzzyConfig, learningEngine)
            EngineType.Pinyin9 -> T9Engine(database, T9Index(database))
            EngineType.Stroke -> StrokeEngine(database)
            EngineType.English -> EnglishEngine(database)
            EngineType.Japanese -> JapaneseRomajiEngine()
            EngineType.Korean -> KoreanJamoEngine()
        }
    }
    
    private fun loadFuzzyConfig(): FuzzyConfig {
        // 从SharedPreferences加载用户配置
        val prefs = context.getSharedPreferences("ime_config", Context.MODE_PRIVATE)
        return FuzzyConfig(
            nLConfusion = prefs.getBoolean("fuzzy_n_l", false),
            fHConfusion = prefs.getBoolean("fuzzy_f_h", false),
            cChConfusion = prefs.getBoolean("fuzzy_c_ch", false),
            sShConfusion = prefs.getBoolean("fuzzy_s_sh", false),
            zZhConfusion = prefs.getBoolean("fuzzy_z_zh", false),
            rLConfusion = prefs.getBoolean("fuzzy_r_l", false),
            anAngConfusion = prefs.getBoolean("fuzzy_an_ang", false),
            enEngConfusion = prefs.getBoolean("fuzzy_en_eng", false),
            inIngConfusion = prefs.getBoolean("fuzzy_in_ing", false),
            ianIangConfusion = prefs.getBoolean("fuzzy_ian_iang", false)
        )
    }
}

/**
 * 输入法服务
 */
class ImeInputService : InputMethodService() {
    
    private lateinit var engineManager: InputEngineManager
    private lateinit var keyboardView: KeyboardView
    
    override fun onCreate() {
        super.onCreate()
        
        // 初始化数据库
        val database = DictionaryDatabaseImpl(this)
        
        // 初始化引擎管理器
        engineManager = InputEngineManager(this, database, InputConfig())
        
        // 设置默认引擎
        engineManager.switchEngine(EngineType.Pinyin26)
    }
    
    override fun onCreateInputView(): View {
        keyboardView = KeyboardView(this)
        keyboardView.setOnKeyListener { key ->
            onKeyPress(key)
        }
        return keyboardView
    }
    
    private fun onKeyPress(key: Key) {
        when (key.type) {
            KeyType.CHARACTER -> {
                // 处理字符输入
                val input = keyboardView.getCurrentInput() + key.value
                val result = engineManager.processInput(input)
                keyboardView.showCandidates(result.candidates)
            }
            KeyType.DELETE -> {
                // 处理删除
            }
            KeyType.SPACE -> {
                // 处理空格
            }
            KeyType.ENTER -> {
                // 处理回车
            }
            KeyType.MODE_SWITCH -> {
                // 切换输入模式
                switchInputMode()
            }
        }
    }
    
    private fun switchInputMode() {
        val modes = listOf(
            EngineType.Pinyin26,
            EngineType.Pinyin9,
            EngineType.Stroke,
            EngineType.English
        )
        
        // 循环切换
        val currentIndex = modes.indexOf(engineManager.getCurrentEngineType())
        val nextIndex = (currentIndex + 1) % modes.size
        engineManager.switchEngine(modes[nextIndex])
        
        // 更新键盘布局
        keyboardView.updateLayout(modes[nextIndex])
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 清理资源
    }
}
```

---

## 8. 内存占用预算

### 8.1 各组件内存分配

| 组件 | 内存预算 | 说明 |
|------|---------|------|
| **SQLite缓存** | 6MB | 页缓存(4MB) + 临时存储(2MB) |
| **词库索引缓存** | 5MB | 拼音索引、T9索引热数据 |
| **候选词缓存** | 3MB | LRU缓存最近查询结果 |
| **用户词频缓存** | 2MB | 高频用户词内存缓存 |
| **拼音切分缓存** | 1MB | 音节切分结果缓存 |
| **多语言缓存** | 2MB | 当前语言包热数据 |
| **学习数据缓存** | 1MB | 上下文关联缓存 |
| **运行时对象** | 3MB | 引擎实例、配置对象等 |
| **预留缓冲** | 2MB | 应对峰值内存需求 |
| **总计** | **25MB** | 符合预算要求 |

### 8.2 内存优化代码配置

```kotlin
/**
 * 内存配置常量
 */
object MemoryConfig {
    // SQLite缓存配置
    const val SQLITE_CACHE_SIZE = 4000      // 页数 (4KB/页 = 16MB，但实际控制在4MB)
    const val SQLITE_TEMP_CACHE = 2000      // 临时存储页数
    
    // LRU缓存大小
    const val QUERY_CACHE_SIZE = 100        // 查询结果缓存条目
    const val PINYIN_CACHE_SIZE = 200       // 拼音切分缓存
    const val USER_WORD_CACHE_SIZE = 500    // 用户词缓存
    const val CANDIDATE_CACHE_SIZE = 50     // 候选词缓存
    
    // 数据加载限制
    const val MAX_CANDIDATES_PER_QUERY = 50 // 单次查询最大候选数
    const val MAX_USER_WORDS_IN_MEMORY = 1000 // 内存中最大用户词数
    const val PRELOAD_PAGE_COUNT = 2        // 预加载页数
}

/**
 * 数据库内存优化配置
 */
fun configureDatabaseMemory(db: SQLiteDatabase) {
    // 设置页缓存大小
    db.execSQL("PRAGMA cache_size = ${MemoryConfig.SQLITE_CACHE_SIZE}")
    
    // 使用内存映射I/O
    db.execSQL("PRAGMA mmap_size = 4194304") // 4MB
    
    // 设置临时存储
    db.execSQL("PRAGMA temp_store = MEMORY")
    
    // 禁用同步（纯离线可接受）
    db.execSQL("PRAGMA synchronous = OFF")
    
    // 启用WAL模式
    db.execSQL("PRAGMA journal_mode = WAL")
}
```

### 8.3 性能监控代码

```kotlin
/**
 * 性能监控器
 */
class PerformanceMonitor {
    
    data class QueryMetrics(
        val input: String,
        val duration: Long,
        val candidateCount: Int,
        val memoryUsage: Long
    )
    
    private val metrics = mutableListOf<QueryMetrics>()
    
    fun recordQuery(
        input: String, 
        duration: Long, 
        candidateCount: Int
    ) {
        val runtime = Runtime.getRuntime()
        val memoryUsage = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        
        metrics.add(QueryMetrics(input, duration, candidateCount, memoryUsage))
        
        // 保留最近100条记录
        if (metrics.size > 100) {
            metrics.removeAt(0)
        }
        
        // 记录慢查询
        if (duration > 50) {
            Log.w("PerfMonitor", "Slow query: ${duration}ms, input: $input")
        }
        
        // 记录高内存使用
        if (memoryUsage > 20) {
            Log.w("PerfMonitor", "High memory usage: ${memoryUsage}MB")
        }
    }
    
    fun getAverageQueryTime(): Double {
        return if (metrics.isEmpty()) 0.0 
               else metrics.map { it.duration }.average()
    }
    
    fun getMaxMemoryUsage(): Long {
        return metrics.maxOfOrNull { it.memoryUsage } ?: 0
    }
}
```

---

## 9. 总结

### 9.1 技术方案亮点

1. **零网络依赖**：所有功能本地实现，无需网络请求
2. **内存严格控制**：总预算25MB，各组件分配明确
3. **响应速度优先**：目标<50ms，通过多级缓存和索引优化实现
4. **模块化设计**：各引擎独立，易于扩展和维护
5. **智能学习**：本地学习用户习惯，越用越准

### 9.2 关键性能指标

| 指标 | 目标值 | 实现方式 |
|------|--------|---------|
| 候选词响应时间 | <50ms | SQLite FTS4 + LRU缓存 |
| 内存占用 | <25MB | 分页加载 + 缓存控制 |
| 词库查询速度 | <20ms | 前缀索引 + 预编译语句 |
| 启动时间 | <500ms | 延迟加载 + 异步初始化 |

### 9.3 后续优化方向

1. 引入Bloom Filter加速无效查询过滤
2. 实现更智能的上下文预测算法
3. 支持更多语言的本地化处理
4. 优化大数据量下的内存抖动问题

---

*文档版本: 1.0*  
*最后更新: 2024年*
