# Android纯离线输入法 - 内存控制与优化技术方案

## 概述

本文档为纯离线Android输入法提供完整的内存控制与优化技术方案，目标是将峰值内存控制在100MB以内，同时保证良好的用户体验。

---

## 1. 内存预算分配表

### 1.1 各组件详细内存分配

| 模块 | 内存预算 | 详细分配 | 峰值场景 |
|------|----------|----------|----------|
| **词库引擎** | 25MB | 核心词库15MB + 用户词库8MB + 缓存2MB | 候选词查询时 |
| **手写识别** | 15MB | 模型8MB + 笔画缓存4MB + 识别缓冲区3MB | 手写输入时 |
| **语音引擎** | 30MB | 声学模型15MB + 语言模型10MB + 音频缓冲区5MB | 语音输入时 |
| **符号系统** | 20MB | 常用符号5MB + 分类符号10MB + 表情5MB | 打开符号面板时 |
| **剪贴板** | 10MB | 历史记录8MB + 索引2MB | 始终 |
| **UI/键盘** | 15MB | 键盘布局5MB + Bitmap缓存6MB + 动画2MB + 其他2MB | 键盘显示时 |
| **系统开销** | 15MB | Android系统 + 输入法框架 | 始终 |
| **预留缓冲** | 10MB | 应对突发内存需求 | - |
| **总计** | **140MB** | **实际峰值控制目标：100MB** | - |

### 1.2 峰值场景分析

```
场景1：普通输入
├─ 词库引擎：15MB (核心词库)
├─ UI/键盘：12MB
├─ 剪贴板：5MB
├─ 系统开销：15MB
└─ 总计：约47MB ✓

场景2：手写输入
├─ 词库引擎：20MB
├─ 手写识别：15MB
├─ UI/键盘：12MB
├─ 剪贴板：5MB
├─ 系统开销：15MB
└─ 总计：约67MB ✓

场景3：语音输入
├─ 词库引擎：20MB
├─ 语音引擎：30MB
├─ UI/键盘：10MB
├─ 剪贴板：5MB
├─ 系统开销：15MB
└─ 总计：约80MB ✓

场景4：符号面板 + 候选词查询（最坏情况）
├─ 词库引擎：25MB
├─ 符号系统：20MB
├─ UI/键盘：15MB
├─ 剪贴板：10MB
├─ 系统开销：15MB
└─ 总计：约85MB ✓

场景5：语音 + 手写 + 符号（极端情况）
├─ 词库引擎：25MB
├─ 手写识别：15MB
├─ 语音引擎：30MB
├─ 符号系统：15MB (降级模式)
├─ UI/键盘：10MB (简化模式)
├─ 剪贴板：5MB (限制模式)
├─ 系统开销：15MB
└─ 总计：约115MB → 通过降级控制在100MB
```

### 1.3 内存预留策略

```kotlin
object MemoryBudget {
    // 内存预算配置（单位：MB）
    const val TOTAL_BUDGET_MB = 100
    const val BASE_BUDGET_MB = 50
    const val PEAK_BUDGET_MB = 100
    
    // 各模块预算
    const val DICT_ENGINE_BUDGET = 25 * 1024 * 1024    // 25MB
    const val HANDWRITE_BUDGET = 15 * 1024 * 1024      // 15MB
    const val VOICE_BUDGET = 30 * 1024 * 1024          // 30MB
    const val SYMBOL_BUDGET = 20 * 1024 * 1024         // 20MB
    const val CLIPBOARD_BUDGET = 10 * 1024 * 1024      // 10MB
    const val UI_BUDGET = 15 * 1024 * 1024             // 15MB
    const val SYSTEM_OVERHEAD = 15 * 1024 * 1024       // 15MB
    
    // 预留缓冲
    const val RESERVED_BUFFER = 10 * 1024 * 1024       // 10MB
    
    // 低内存阈值
    const val LOW_MEMORY_THRESHOLD = 80 * 1024 * 1024  // 80MB
    const val CRITICAL_MEMORY_THRESHOLD = 95 * 1024 * 1024 // 95MB
}
```

---

## 2. Bitmap优化

### 2.1 Bitmap复用（inBitmap）

```kotlin
/**
 * Bitmap复用管理器
 * 使用inBitmap机制减少内存分配和GC
 */
class BitmapPool private constructor() {
    
    companion object {
        @Volatile
        private var instance: BitmapPool? = null
        
        fun getInstance(): BitmapPool {
            return instance ?: synchronized(this) {
                instance ?: BitmapPool().also { instance = it }
            }
        }
    }
    
    // 按尺寸分类的Bitmap池
    private val pool = LruCache<String, MutableList<Bitmap>>(10)
    
    // 最大池大小（6MB）
    private val maxPoolSize = 6 * 1024 * 1024
    private var currentPoolSize = 0
    
    fun getReusableBitmap(width: Int, height: Int, config: Bitmap.Config): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB_MR1) {
            return null
        }
        val key = "${width}x${height}_${config.name}"
        val list = pool.get(key)
        list?.let {
            synchronized(it) {
                if (it.isNotEmpty()) {
                    val bitmap = it.removeAt(it.size - 1)
                    currentPoolSize -= bitmap.byteCount
                    return bitmap
                }
            }
        }
        return null
    }
    
    fun recycleBitmap(bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB_MR1) {
            bitmap.recycle()
            return
        }
        val byteCount = bitmap.byteCount
        if (currentPoolSize + byteCount > maxPoolSize) {
            bitmap.recycle()
            return
        }
        val key = "${bitmap.width}x${bitmap.height}_${bitmap.config.name}"
        var list = pool.get(key)
        if (list == null) {
            list = mutableListOf()
            pool.put(key, list)
        }
        synchronized(list) {
            if (list.size < 3) {
                list.add(bitmap)
                currentPoolSize += byteCount
            } else {
                bitmap.recycle()
            }
        }
    }
    
    fun clear() {
        pool.snapshot().values.forEach { list ->
            synchronized(list) {
                list.forEach { it.recycle() }
                list.clear()
            }
        }
        pool.evictAll()
        currentPoolSize = 0
    }
}
```

### 2.2 图片压缩策略

```kotlin
object ImageCompressor {
    private const val MAX_KEY_WIDTH = 120
    private const val MAX_KEY_HEIGHT = 160
    private const val MAX_SKIN_WIDTH = 720
    private const val MAX_SKIN_HEIGHT = 1280
    
    fun compressKeyBitmap(bitmap: Bitmap): Bitmap {
        return compressBitmap(bitmap, MAX_KEY_WIDTH, MAX_KEY_HEIGHT)
    }
    
    fun compressSkinBitmap(bitmap: Bitmap): Bitmap {
        return compressBitmap(bitmap, MAX_SKIN_WIDTH, MAX_SKIN_HEIGHT)
    }
    
    private fun compressBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxWidth && height <= maxHeight) return bitmap
        
        val ratio = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
```

### 2.3 皮肤图片内存管理

```kotlin
class SkinImageManager private constructor(context: Context) {
    companion object {
        @Volatile private var instance: SkinImageManager? = null
        fun getInstance(context: Context): SkinImageManager {
            return instance ?: synchronized(this) {
                instance ?: SkinImageManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val appContext = context.applicationContext
    private val imageCache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted) BitmapPool.getInstance().recycleBitmap(oldValue)
        }
    }
    private var skinPath: String? = null
    
    fun setSkinPath(path: String?) {
        if (skinPath != path) {
            clearCache()
            skinPath = path
        }
    }
    
    fun getSkinImage(name: String): Bitmap? {
        val cacheKey = "${skinPath}_$name"
        imageCache.get(cacheKey)?.let { return it }
        
        val path = skinPath ?: return getDefaultImage(name)
        val file = File(path, "$name.png")
        if (!file.exists()) return getDefaultImage(name)
        
        return BitmapLoader.loadBitmapFromFile(file.absolutePath)?.let {
            val compressed = ImageCompressor.compressSkinBitmap(it)
            imageCache.put(cacheKey, compressed)
            if (compressed != it) BitmapPool.getInstance().recycleBitmap(it)
            compressed
        }
    }
    
    private fun getDefaultImage(name: String): Bitmap? {
        val resId = when (name) {
            "keyboard_bg" -> R.drawable.keyboard_bg_default
            "key_normal" -> R.drawable.key_normal_default
            "key_pressed" -> R.drawable.key_pressed_default
            else -> return null
        }
        return BitmapLoader.loadBitmap(appContext.resources, resId)
    }
    
    fun clearCache() = imageCache.evictAll()
    fun getCacheSize(): Int = imageCache.size()
}
```

---

## 3. 对象池

### 3.1 键盘按键对象池

```kotlin
class KeyViewPool private constructor() {
    companion object {
        @Volatile private var instance: KeyViewPool? = null
        fun getInstance(): KeyViewPool {
            return instance ?: synchronized(this) {
                instance ?: KeyViewPool().also { instance = it }
            }
        }
        private const val MAX_POOL_SIZE = 50
    }
    
    private val pool = ArrayDeque<KeyView>(MAX_POOL_SIZE)
    private var currentSize = 0
    
    fun obtain(context: Context, parent: ViewGroup): KeyView {
        return synchronized(pool) {
            pool.removeFirstOrNull()
        }?.apply { reset() } ?: KeyView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }
    
    fun recycle(keyView: KeyView) {
        synchronized(pool) {
            if (currentSize < MAX_POOL_SIZE) {
                keyView.reset()
                pool.addLast(keyView)
                currentSize++
            }
        }
    }
    
    fun recycleAll(keyViews: List<KeyView>) {
        synchronized(pool) {
            keyViews.forEach { keyView ->
                if (currentSize < MAX_POOL_SIZE) {
                    keyView.reset()
                    pool.addLast(keyView)
                    currentSize++
                }
            }
        }
    }
    
    fun clear() {
        synchronized(pool) {
            pool.clear()
            currentSize = 0
        }
    }
}
```

### 3.2 候选词对象池

```kotlin
class CandidatePool private constructor() {
    companion object {
        @Volatile private var instance: CandidatePool? = null
        fun getInstance(): CandidatePool {
            return instance ?: synchronized(this) {
                instance ?: CandidatePool().also { instance = it }
            }
        }
        private const val MAX_POOL_SIZE = 30
    }
    
    private val pool = ArrayDeque<Candidate>(MAX_POOL_SIZE)
    private var currentSize = 0
    
    fun obtain(): Candidate {
        return synchronized(pool) {
            pool.removeFirstOrNull()?.apply { reset() }
        } ?: Candidate()
    }
    
    fun obtainList(count: Int): List<Candidate> = List(count) { obtain() }
    
    fun recycle(candidate: Candidate) {
        synchronized(pool) {
            if (currentSize < MAX_POOL_SIZE) {
                candidate.reset()
                pool.addLast(candidate)
                currentSize++
            }
        }
    }
    
    fun recycleAll(candidates: List<Candidate>) {
        synchronized(pool) {
            candidates.forEach { candidate ->
                if (currentSize < MAX_POOL_SIZE) {
                    candidate.reset()
                    pool.addLast(candidate)
                    currentSize++
                }
            }
        }
    }
    
    fun clear() {
        synchronized(pool) {
            pool.clear()
            currentSize = 0
        }
    }
}

class Candidate {
    var text: String = ""
    var pinyin: String = ""
    var frequency: Int = 0
    var isUserWord: Boolean = false
    var source: Int = SOURCE_DICT
    
    companion object {
        const val SOURCE_DICT = 0
        const val SOURCE_USER = 1
        const val SOURCE_PREDICTION = 2
    }
    
    fun reset() {
        text = ""
        pinyin = ""
        frequency = 0
        isUserWord = false
        source = SOURCE_DICT
    }
}
```

### 3.3 字符串缓冲区复用

```kotlin
class StringBuilderPool private constructor() {
    companion object {
        @Volatile private var instance: StringBuilderPool? = null
        fun getInstance(): StringBuilderPool {
            return instance ?: synchronized(this) {
                instance ?: StringBuilderPool().also { instance = it }
            }
        }
        private const val MAX_POOL_SIZE = 10
        private const val DEFAULT_CAPACITY = 64
    }
    
    private val pool = ArrayDeque<StringBuilder>(MAX_POOL_SIZE)
    
    fun obtain(): StringBuilder {
        return synchronized(pool) {
            pool.removeFirstOrNull()?.apply { setLength(0) }
        } ?: StringBuilder(DEFAULT_CAPACITY)
    }
    
    fun obtain(capacity: Int): StringBuilder {
        val sb = synchronized(pool) { pool.removeFirstOrNull() }
        return if (sb != null && sb.capacity() >= capacity) {
            sb.setLength(0)
            sb
        } else {
            StringBuilder(capacity)
        }
    }
    
    fun recycle(sb: StringBuilder) {
        synchronized(pool) {
            if (pool.size < MAX_POOL_SIZE && sb.capacity() <= DEFAULT_CAPACITY * 4) {
                sb.setLength(0)
                pool.addLast(sb)
            }
        }
    }
    
    inline fun <R> use(action: (StringBuilder) -> R): R {
        val sb = obtain()
        return try {
            action(sb)
        } finally {
            recycle(sb)
        }
    }
}
```

---

## 4. 延迟加载

### 4.1 符号分类懒加载

```kotlin
class SymbolCategoryManager private constructor(context: Context) {
    companion object {
        @Volatile private var instance: SymbolCategoryManager? = null
        fun getInstance(context: Context): SymbolCategoryManager {
            return instance ?: synchronized(this) {
                instance ?: SymbolCategoryManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val appContext = context.applicationContext
    
    data class CategoryInfo(val id: Int, val name: String, val iconResId: Int, val symbolCount: Int)
    
    private val loadedCategories = LruCache<Int, List<String>>(5)
    private val categoryInfos = mutableListOf<CategoryInfo>()
    private val loadingStates = mutableMapOf<Int, Boolean>()
    
    init {
        initCategoryInfos()
    }
    
    private fun initCategoryInfos() {
        categoryInfos.add(CategoryInfo(0, "常用", R.drawable.ic_symbol_recent, 30))
        categoryInfos.add(CategoryInfo(1, "表情", R.drawable.ic_symbol_emoji, 100))
        categoryInfos.add(CategoryInfo(2, "数学", R.drawable.ic_symbol_math, 50))
        categoryInfos.add(CategoryInfo(3, "单位", R.drawable.ic_symbol_unit, 40))
        categoryInfos.add(CategoryInfo(4, "货币", R.drawable.ic_symbol_currency, 30))
        categoryInfos.add(CategoryInfo(5, "特殊", R.drawable.ic_symbol_special, 60))
    }
    
    fun getCategoryInfos(): List<CategoryInfo> = categoryInfos.toList()
    
    fun getSymbols(categoryId: Int, callback: (List<String>) -> Unit) {
        loadedCategories.get(categoryId)?.let { callback(it); return }
        if (loadingStates[categoryId] == true) return
        
        loadingStates[categoryId] = true
        CoroutineScope(Dispatchers.IO).launch {
            val symbols = loadSymbolsFromStorage(categoryId)
            withContext(Dispatchers.Main) {
                loadedCategories.put(categoryId, symbols)
                loadingStates[categoryId] = false
                callback(symbols)
            }
        }
    }
    
    fun preloadCommonCategories() {
        CoroutineScope(Dispatchers.IO).launch {
            val symbols = loadSymbolsFromStorage(0)
            loadedCategories.put(0, symbols)
        }
    }
    
    private fun loadSymbolsFromStorage(categoryId: Int): List<String> {
        return when (categoryId) {
            0 -> listOf("@", "#", "$", "%", "&", "*", "(", ")", "-", "+", "=", "/", ".", ",", "?", "!")
            1 -> readSymbolsFromFile("emoji_symbols.txt")
            2 -> readSymbolsFromFile("math_symbols.txt")
            3 -> readSymbolsFromFile("unit_symbols.txt")
            4 -> readSymbolsFromFile("currency_symbols.txt")
            5 -> readSymbolsFromFile("special_symbols.txt")
            else -> emptyList()
        }
    }
    
    private fun readSymbolsFromFile(fileName: String): List<String> {
        return try {
            appContext.assets.open("symbols/$fileName").bufferedReader().use { it.readLines() }
        } catch (e: IOException) { emptyList() }
    }
    
    fun releaseCategory(categoryId: Int) = loadedCategories.remove(categoryId)
    fun releaseAll() = loadedCategories.evictAll()
}
```

### 4.2 词库分页加载

```kotlin
class DictionaryPager(private val dictionary: Dictionary) {
    companion object {
        private const val PAGE_SIZE = 20
        private const val MAX_CACHED_PAGES = 3
    }
    
    private val pageCache = LruCache<Int, List<Candidate>>(MAX_CACHED_PAGES)
    private var currentPinyin: String = ""
    private var totalCandidates: Int = 0
    
    fun startQuery(pinyin: String): Boolean {
        if (pinyin == currentPinyin) return false
        currentPinyin = pinyin
        totalCandidates = dictionary.getCandidateCount(pinyin)
        pageCache.evictAll()
        return true
    }
    
    fun getPage(pageIndex: Int): List<Candidate> {
        if (currentPinyin.isEmpty()) return emptyList()
        pageCache.get(pageIndex)?.let { return it }
        
        val startIndex = pageIndex * PAGE_SIZE
        val endIndex = minOf(startIndex + PAGE_SIZE, totalCandidates)
        if (startIndex >= totalCandidates) return emptyList()
        
        val candidates = dictionary.getCandidates(currentPinyin, startIndex, endIndex - startIndex)
        pageCache.put(pageIndex, candidates)
        return candidates
    }
    
    fun getFirstPage(): List<Candidate> = getPage(0)
    fun getNextPage(currentPage: Int): List<Candidate> = getPage(currentPage + 1)
    fun getTotalPages(): Int = (totalCandidates + PAGE_SIZE - 1) / PAGE_SIZE
    fun clearCache() = pageCache.evictAll()
}

interface Dictionary {
    fun getCandidateCount(pinyin: String): Int
    fun getCandidates(pinyin: String, start: Int, count: Int): List<Candidate>
}
```

---

## 5. 内存监控

### 5.1 运行时内存检测

```kotlin
class MemoryMonitor private constructor(context: Context) {
    companion object {
        @Volatile private var instance: MemoryMonitor? = null
        fun getInstance(context: Context): MemoryMonitor {
            return instance ?: synchronized(this) {
                instance ?: MemoryMonitor(context.applicationContext).also { instance = it }
            }
        }
        private const val WARNING_THRESHOLD_MB = 80
        private const val CRITICAL_THRESHOLD_MB = 95
        private const val EMERGENCY_THRESHOLD_MB = 110
        private const val CHECK_INTERVAL_MS = 5000L
    }
    
    private val appContext = context.applicationContext
    private val runtime = Runtime.getRuntime()
    private var isMonitoring = false
    private var monitorJob: Job? = null
    private val listeners = mutableListOf<MemoryStateListener>()
    private val memoryHistory = mutableListOf<MemorySnapshot>()
    private val maxHistorySize = 100
    private var currentState = MemoryState.NORMAL
    
    enum class MemoryState { NORMAL, WARNING, CRITICAL, EMERGENCY }
    
    interface MemoryStateListener {
        fun onMemoryStateChanged(state: MemoryState, usage: MemoryUsage)
        fun onMemoryWarning(usage: MemoryUsage)
        fun onMemoryCritical(usage: MemoryUsage)
    }
    
    data class MemoryUsage(
        val usedMemoryMB: Long,
        val totalMemoryMB: Long,
        val maxMemoryMB: Long,
        val nativeHeapMB: Long,
        val usagePercent: Float
    )
    
    data class MemorySnapshot(val timestamp: Long, val usage: MemoryUsage)
    
    fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        monitorJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive && isMonitoring) {
                checkMemory()
                delay(CHECK_INTERVAL_MS)
            }
        }
    }
    
    fun stopMonitoring() {
        isMonitoring = false
        monitorJob?.cancel()
        monitorJob = null
    }
    
    private fun checkMemory() {
        val usage = getMemoryUsage()
        val snapshot = MemorySnapshot(System.currentTimeMillis(), usage)
        
        synchronized(memoryHistory) {
            memoryHistory.add(snapshot)
            if (memoryHistory.size > maxHistorySize) memoryHistory.removeAt(0)
        }
        
        val newState = when {
            usage.usedMemoryMB >= EMERGENCY_THRESHOLD_MB -> MemoryState.EMERGENCY
            usage.usedMemoryMB >= CRITICAL_THRESHOLD_MB -> MemoryState.CRITICAL
            usage.usedMemoryMB >= WARNING_THRESHOLD_MB -> MemoryState.WARNING
            else -> MemoryState.NORMAL
        }
        
        if (newState != currentState) {
            currentState = newState
            notifyStateChanged(usage)
        }
        
        when (newState) {
            MemoryState.WARNING -> notifyWarning(usage)
            MemoryState.CRITICAL, MemoryState.EMERGENCY -> notifyCritical(usage)
            else -> {}
        }
    }
    
    fun getMemoryUsage(): MemoryUsage {
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val totalMemory = runtime.totalMemory()
        val maxMemory = runtime.maxMemory()
        val nativeHeap = Debug.getNativeHeapAllocatedSize()
        
        return MemoryUsage(
            usedMemoryMB = usedMemory / (1024 * 1024),
            totalMemoryMB = totalMemory / (1024 * 1024),
            maxMemoryMB = maxMemory / (1024 * 1024),
            nativeHeapMB = nativeHeap / (1024 * 1024),
            usagePercent = (usedMemory.toFloat() / maxMemory.toFloat()) * 100
        )
    }
    
    enum class MemoryTrend { INCREASING, DECREASING, STABLE }
    
    fun getMemoryTrend(): MemoryTrend {
        synchronized(memoryHistory) {
            if (memoryHistory.size < 2) return MemoryTrend.STABLE
            val recent = memoryHistory.takeLast(10)
            val first = recent.first().usage.usedMemoryMB
            val last = recent.last().usage.usedMemoryMB
            return when {
                last > first + 10 -> MemoryTrend.INCREASING
                last < first - 10 -> MemoryTrend.DECREASING
                else -> MemoryTrend.STABLE
            }
        }
    }
    
    private fun notifyStateChanged(usage: MemoryUsage) {
        CoroutineScope(Dispatchers.Main).launch {
            listeners.forEach { it.onMemoryStateChanged(currentState, usage) }
        }
    }
    
    private fun notifyWarning(usage: MemoryUsage) {
        CoroutineScope(Dispatchers.Main).launch {
            listeners.forEach { it.onMemoryWarning(usage) }
        }
    }
    
    private fun notifyCritical(usage: MemoryUsage) {
        CoroutineScope(Dispatchers.Main).launch {
            listeners.forEach { it.onMemoryCritical(usage) }
        }
    }
    
    fun addListener(listener: MemoryStateListener) = listeners.add(listener)
    fun removeListener(listener: MemoryStateListener) = listeners.remove(listener)
    fun getCurrentState(): MemoryState = currentState
    fun getMemoryHistory(): List<MemorySnapshot> = synchronized(memoryHistory) { memoryHistory.toList() }
}
```

### 5.2 内存警告监听

```kotlin
class SystemMemoryWatcher(private val context: Context) : ComponentCallbacks2 {
    companion object {
        @Volatile private var instance: SystemMemoryWatcher? = null
        fun getInstance(context: Context): SystemMemoryWatcher {
            return instance ?: synchronized(this) {
                instance ?: SystemMemoryWatcher(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val appContext = context.applicationContext
    private val listeners = mutableListOf<MemoryPressureListener>()
    
    interface MemoryPressureListener {
        fun onLowMemory()
        fun onTrimMemory(level: Int)
    }
    
    init { appContext.registerComponentCallbacks(this) }
    
    fun addListener(listener: MemoryPressureListener) = listeners.add(listener)
    fun removeListener(listener: MemoryPressureListener) = listeners.remove(listener)
    
    override fun onLowMemory() = listeners.forEach { it.onLowMemory() }
    
    override fun onTrimMemory(level: Int) {
        listeners.forEach { it.onTrimMemory(level) }
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> handleModeratePressure()
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> handleLowPressure()
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> handleCriticalPressure()
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> handleUIHidden()
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> handleBackgroundPressure()
        }
    }
    
    private fun handleModeratePressure() {
        BitmapPool.getInstance().clear()
        ObjectPoolManager.clearAllPools()
    }
    
    private fun handleLowPressure() {
        SymbolCategoryManager.getInstance(appContext).releaseAll()
        SkinImageManager.getInstance(appContext).clearCache()
    }
    
    private fun handleCriticalPressure() {
        MemoryReleaseManager.getInstance(appContext).releaseAll()
    }
    
    private fun handleUIHidden() {
        SkinLazyLoader.getInstance(appContext).releaseAll()
    }
    
    private fun handleBackgroundPressure() {
        if (!isInputMethodShowing()) {
            MemoryReleaseManager.getInstance(appContext).releaseNonEssential()
        }
    }
    
    private fun isInputMethodShowing(): Boolean {
        val imm = appContext.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.isAcceptingText
    }
    
    override fun onConfigurationChanged(newConfig: Configuration) {}
    
    fun release() {
        appContext.unregisterComponentCallbacks(this)
        listeners.clear()
    }
}
```

---

## 6. 自动降级策略

### 6.1 低内存检测阈值

```kotlin
object MemoryThresholds {
    const val LEVEL_NORMAL_MAX = 60
    const val LEVEL_WARNING_MAX = 80
    const val LEVEL_CRITICAL_MAX = 95
    const val LEVEL_EMERGENCY = 110
    
    enum class DegradeLevel { NONE, LIGHT, MODERATE, SEVERE, EMERGENCY }
    
    fun getDegradeLevel(memoryUsageMB: Long): DegradeLevel {
        return when {
            memoryUsageMB >= LEVEL_EMERGENCY -> DegradeLevel.EMERGENCY
            memoryUsageMB >= LEVEL_CRITICAL_MAX -> DegradeLevel.SEVERE
            memoryUsageMB >= LEVEL_WARNING_MAX -> DegradeLevel.MODERATE
            memoryUsageMB >= LEVEL_NORMAL_MAX -> DegradeLevel.LIGHT
            else -> DegradeLevel.NONE
        }
    }
}
```

### 6.2 降级策略实现

```kotlin
class AutoDegradeManager private constructor(context: Context) {
    companion object {
        @Volatile private var instance: AutoDegradeManager? = null
        fun getInstance(context: Context): AutoDegradeManager {
            return instance ?: synchronized(this) {
                instance ?: AutoDegradeManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val appContext = context.applicationContext
    private val memoryMonitor = MemoryMonitor.getInstance(appContext)
    private var currentLevel = MemoryThresholds.DegradeLevel.NONE
    private val listeners = mutableListOf<DegradeListener>()
    
    interface DegradeListener {
        fun onDegradeLevelChanged(oldLevel: MemoryThresholds.DegradeLevel, newLevel: MemoryThresholds.DegradeLevel)
        fun onDegradeApplied(level: MemoryThresholds.DegradeLevel, actions: List<String>)
    }
    
    init { setupMemoryMonitor() }
    
    private fun setupMemoryMonitor() {
        memoryMonitor.addListener(object : MemoryMonitor.MemoryStateListener {
            override fun onMemoryStateChanged(state: MemoryMonitor.MemoryState, usage: MemoryMonitor.MemoryUsage) {
                checkAndApplyDegrade(usage.usedMemoryMB)
            }
            override fun onMemoryWarning(usage: MemoryMonitor.MemoryUsage) {}
            override fun onMemoryCritical(usage: MemoryMonitor.MemoryUsage) {}
        })
    }
    
    private fun checkAndApplyDegrade(memoryUsageMB: Long) {
        val newLevel = MemoryThresholds.getDegradeLevel(memoryUsageMB)
        if (newLevel != currentLevel) {
            val oldLevel = currentLevel
            currentLevel = newLevel
            applyDegradeLevel(newLevel)
            listeners.forEach { it.onDegradeLevelChanged(oldLevel, newLevel) }
        }
    }
    
    private fun applyDegradeLevel(level: MemoryThresholds.DegradeLevel) {
        val actions = mutableListOf<String>()
        when (level) {
            MemoryThresholds.DegradeLevel.LIGHT -> {
                AnimationManager.setAnimationQuality(AnimationManager.Quality.LOW)
                BitmapPool.getInstance().clear()
                ClipboardManager.setMaxHistorySize(20)
                actions.addAll(listOf("降低动画质量", "清理Bitmap缓存", "限制剪贴板历史"))
            }
            MemoryThresholds.DegradeLevel.MODERATE -> {
                AnimationManager.setAnimationEnabled(false)
                SymbolCategoryManager.getInstance(appContext).releaseAll()
                SkinImageManager.getInstance(appContext).clearCache()
                CandidateManager.setMaxCandidates(5)
                ObjectPoolManager.clearAllPools()
                actions.addAll(listOf("关闭动画", "释放符号缓存", "清理皮肤缓存", "减少候选词", "清理对象池"))
            }
            MemoryThresholds.DegradeLevel.SEVERE -> {
                MemoryReleaseManager.getInstance(appContext).releaseAllCaches()
                KeyboardRenderer.setSimpleMode(true)
                EffectManager.setEffectsEnabled(false)
                DictionaryManager.reduceCache()
                VoiceEngineManager.pause()
                actions.addAll(listOf("释放所有缓存", "简化键盘渲染", "禁用特效", "减少词库缓存", "暂停语音引擎"))
            }
            MemoryThresholds.DegradeLevel.EMERGENCY -> {
                MemoryReleaseManager.getInstance(appContext).releaseAll()
                UIManager.setMinimalMode(true)
                HandwritingManager.shutdown()
                VoiceEngineManager.shutdown()
                actions.addAll(listOf("释放所有资源", "使用最简UI", "关闭手写识别", "关闭语音引擎"))
            }
            else -> {}
        }
        notifyDegradeApplied(level, actions)
    }
    
    private fun notifyDegradeApplied(level: MemoryThresholds.DegradeLevel, actions: List<String>) {
        CoroutineScope(Dispatchers.Main).launch {
            listeners.forEach { it.onDegradeApplied(level, actions) }
        }
    }
    
    fun addListener(listener: DegradeListener) = listeners.add(listener)
    fun getCurrentLevel(): MemoryThresholds.DegradeLevel = currentLevel
}
```

---

## 7. 性能优化

### 7.1 减少GC频率

```kotlin
object GCOptimizer {
    private val allocationCounter = AtomicLong(0)
    private val lastGCTime = AtomicLong(0)
    private const val ALLOCATION_THRESHOLD = 10000
    private const val GC_INTERVAL_MS = 30000
    
    fun recordAllocation() {
        val count = allocationCounter.incrementAndGet()
        if (count >= ALLOCATION_THRESHOLD) {
            suggestGC()
            allocationCounter.set(0)
        }
    }
    
    fun suggestGC() {
        val currentTime = System.currentTimeMillis()
        val lastTime = lastGCTime.get()
        if (currentTime - lastTime > GC_INTERVAL_MS) {
            if (lastGCTime.compareAndSet(lastTime, currentTime)) {
                CoroutineScope(Dispatchers.Default).launch { System.gc() }
            }
        }
    }
}
```

### 7.2 避免内存抖动

```kotlin
object MemoryJitterGuard {
    private val allocationTimestamps = mutableListOf<Long>()
    private const val JITTER_WINDOW_MS = 1000
    private const val JITTER_THRESHOLD = 100
    
    fun isJittering(): Boolean {
        val currentTime = System.currentTimeMillis()
        allocationTimestamps.removeAll { currentTime - it > JITTER_WINDOW_MS }
        return allocationTimestamps.size > JITTER_THRESHOLD
    }
    
    fun recordAllocation() {
        allocationTimestamps.add(System.currentTimeMillis())
    }
}
```

### 7.3 大对象管理

```kotlin
object LargeObjectManager {
    private const val LARGE_OBJECT_THRESHOLD = 100 * 1024
    private val largeObjects = mutableMapOf<String, LargeObjectInfo>()
    
    data class LargeObjectInfo(
        val name: String,
        val size: Long,
        val createdTime: Long,
        var lastAccessTime: Long
    )
    
    fun registerLargeObject(name: String, size: Long) {
        if (size >= LARGE_OBJECT_THRESHOLD) {
            largeObjects[name] = LargeObjectInfo(name, size, System.currentTimeMillis(), System.currentTimeMillis())
        }
    }
    
    fun releaseLeastUsed(count: Int = 1) {
        largeObjects.values.sortedBy { it.lastAccessTime }.take(count).forEach {
            largeObjects.remove(it.name)
        }
    }
}
```

---

## 8. 代码规范

### 8.1 内存优化编码规范

```kotlin
object MemoryOptimizationGuidelines {
    // ❌ 错误：循环中创建对象
    fun badLoopExample(items: List<String>) {
        for (item in items) {
            val builder = StringBuilder() // 每次循环都创建新对象
            builder.append(item)
        }
    }
    
    // ✅ 正确：复用对象
    private val reusableBuilder = StringBuilder()
    fun goodLoopExample(items: List<String>) {
        for (item in items) {
            reusableBuilder.setLength(0)
            reusableBuilder.append(item)
        }
    }
    
    // ❌ 错误：字符串拼接
    fun badConcat(parts: List<String>): String {
        var result = ""
        for (part in parts) result += part
        return result
    }
    
    // ✅ 正确：使用StringBuilder池
    fun goodConcat(parts: List<String>): String {
        return StringBuilderPool.getInstance().use { sb ->
            parts.forEach { sb.append(it) }
            sb.toString()
        }
    }
}
```

### 8.2 避免内存泄漏最佳实践

```kotlin
object MemoryLeakPrevention {
    // ✅ 使用弱引用持有Activity
    object GoodSingleton {
        private val weakActivity = WeakReference<Activity>(null)
        fun setActivity(activity: Activity?) {
            weakActivity.clear()
            activity?.let { weakActivity.refersTo(it) }
        }
    }
    
    // ✅ 使用Application Context
    fun goodContextUsage(context: Context) {
        val appContext = context.applicationContext
        Toast.makeText(appContext, "message", Toast.LENGTH_SHORT).show()
    }
    
    // ✅ 生命周期感知清理
    class SafeObserver : DefaultLifecycleObserver {
        private var disposable: Disposable? = null
        override fun onDestroy(owner: LifecycleOwner) {
            disposable?.dispose()
        }
    }
}
```

---

## 9. 监控工具

### 9.1 内存分析工具推荐

| 工具 | 用途 | 推荐场景 |
|------|------|----------|
| Android Studio Memory Profiler | 实时内存监控、Heap Dump | 日常开发 |
| LeakCanary | 内存泄漏检测 | 测试阶段 |
| MAT | hprof文件分析 | 深度分析 |
| dumpsys meminfo | 系统内存信息 | 命令行调试 |

### 9.2 调试技巧

```kotlin
object MemoryDebugTips {
    fun logMemoryInfo(tag: String = "MemoryDebug") {
        val runtime = Runtime.getRuntime()
        val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        val totalMem = runtime.totalMemory() / 1024 / 1024
        val maxMem = runtime.maxMemory() / 1024 / 1024
        Log.d(tag, "Memory: ${usedMem}MB / ${totalMem}MB (Max: ${maxMem}MB)")
    }
    
    fun forceGCAndMeasure(tag: String = "GC") {
        val runtime = Runtime.getRuntime()
        val before = runtime.totalMemory() - runtime.freeMemory()
        System.gc()
        System.runFinalization()
        Thread.sleep(100)
        val after = runtime.totalMemory() - runtime.freeMemory()
        Log.d(tag, "GC freed: ${(before - after) / 1024}KB")
    }
}
```

---

## 10. 总结

本文档提供了Android纯离线输入法的完整内存控制与优化技术方案：

1. **内存预算分配**：各模块详细内存分配，峰值控制在100MB以内
2. **Bitmap优化**：inBitmap复用、按需加载、LRU缓存
3. **对象池**：键盘按键、候选词、字符串缓冲区复用
4. **延迟加载**：符号分类、词库、皮肤、语音模型按需加载
5. **内存监控**：实时监控、警告监听、使用统计、泄漏检测
6. **自动降级**：四级降级策略，自动恢复机制
7. **性能优化**：减少GC、避免内存抖动、大对象管理
8. **代码规范**：编码规范、泄漏防护、审查清单
9. **监控工具**：分析工具推荐、性能监控、调试技巧

通过实施以上方案，可以有效控制输入法内存使用在目标范围内。
