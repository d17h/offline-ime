# 超级符号系统 - 架构图与总结

## 1. 系统架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              应用层 (Application)                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────┐    ┌─────────────────────┐    ┌─────────────────┐ │
│  │  SymbolKeyboardView │    │  SymbolSearchView   │    │  设置/配置界面   │ │
│  │  (符号键盘主视图)    │    │   (搜索界面)         │    │                 │ │
│  └──────────┬──────────┘    └──────────┬──────────┘    └─────────────────┘ │
│             │                          │                                    │
│  ┌──────────▼──────────┐    ┌──────────▼──────────┐                        │
│  │   SymbolAdapter     │    │   SearchAdapter     │                        │
│  │   (符号网格适配器)   │    │   (搜索结果适配器)   │                        │
│  └──────────┬──────────┘    └──────────┬──────────┘                        │
│             │                          │                                    │
│  ┌──────────▼──────────┐    ┌──────────▼──────────┐                        │
│  │ SymbolPreviewPopup  │    │  EmojiImageView     │                        │
│  │   (长按预览弹窗)     │    │   (Emoji图片显示)   │                        │
│  └─────────────────────┘    └─────────────────────┘                        │
│                                                                             │
└─────────────────────────────────────┬───────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              管理层 (Manager)                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    SymbolManager (单例)                              │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐               │   │
│  │  │  数据协调    │  │  搜索管理    │  │  缓存管理    │               │   │
│  │  └──────────────┘  └──────────────┘  └──────────────┘               │   │
│  └──────────┬──────────────────────────────┬───────────────────────────┘   │
│             │                              │                                │
│  ┌──────────▼──────────┐    ┌──────────────▼──────────────┐               │
│  │ SymbolDataProvider  │    │    EmojiDataLoader          │               │
│  │  (基础符号提供者)    │    │    (Emoji数据加载器)         │               │
│  │  - 硬编码数据        │    │    - JSON解析               │               │
│  │  - 懒加载            │    │    - GZIP解压               │               │
│  └─────────────────────┘    │    - 分类映射               │               │
│                             └─────────────────────────────┘               │
│  ┌─────────────────────┐    ┌─────────────────────────────┐               │
│  │   RecentCache       │    │    SymbolSearchIndex        │               │
│  │   (最近使用缓存)     │    │    (搜索索引)               │               │
│  │   - LRU 20个        │    │    - 多字段索引             │               │
│  │   - SharedPrefs     │    │    - 拼音支持               │               │
│  └─────────────────────┘    └─────────────────────────────┘               │
│  ┌─────────────────────┐                                                  │
│  │  FavoriteManager    │                                                  │
│  │   (收藏管理器)       │                                                  │
│  │   - 100个上限        │                                                  │
│  │   - SharedPrefs     │                                                  │
│  └─────────────────────┘                                                  │
│                                                                             │
└─────────────────────────────────────┬───────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              数据层 (Data)                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────┐    ┌─────────────────────────────────────┐    │
│  │    硬编码数据            │    │         资源文件                     │    │
│  │  ┌─────────────────┐    │    │  ┌─────────────────────────────┐   │    │
│  │  │ MATH_SYMBOLS    │    │    │  │ assets/emoji/               │   │    │
│  │  │ GREEK_SYMBOLS   │    │    │  │   ├── emoji_data.json.gz    │   │    │
│  │  │ UNIT_SYMBOLS    │    │    │  │   └── emoji_index.json      │   │    │
│  │  │ CURRENCY_SYMBOLS│    │    │  └─────────────────────────────┘   │    │
│  │  │ ARROW_SYMBOLS   │    │    │                                    │    │
│  │  │ SHAPE_SYMBOLS   │    │    │  ┌─────────────────────────────┐   │    │
│  │  │ PINYIN_SYMBOLS  │    │    │  │ SharedPreferences           │   │    │
│  │  │ RADICAL_SYMBOLS │    │    │  │   ├── symbol_recent.xml     │   │    │
│  │  │ SPECIAL_SYMBOLS │    │    │  │   └── symbol_favorites.xml  │   │    │
│  │  │ KAOMOJI_SYMBOLS │    │    │  └─────────────────────────────┘   │    │
│  │  └─────────────────┘    │    └─────────────────────────────────────┘    │
│  └─────────────────────────┘                                               │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              工具层 (Utils)                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐             │
│  │  EmojiCompat    │  │  MemoryMonitor  │  │  DensityUtils   │             │
│  │  (兼容性检查)    │  │  (内存监控)     │  │  (密度工具)     │             │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘             │
│                                                                             │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐             │
│  │ EmojiImageCache │  │  StringUtils    │  │  LazyLoadManager│             │
│  │ (图片缓存)      │  │  (字符串工具)   │  │  (懒加载管理)   │             │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘             │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 2. 数据流图

```
用户操作
    │
    ├──► 点击分类Tab ──────────────────────────────────────────────┐
    │                                                               │
    │   SymbolKeyboardView.switchCategory(category)                 │
    │                    │                                          │
    │                    ▼                                          │
    │   SymbolManager.getSymbols(category)                          │
    │        │                                                      │
    │        ├──► 最近/收藏 ──► 从内存缓存读取 ──► 返回结果         │
    │        │                                                      │
    │        ├──► 基础符号 ──► SymbolDataProvider.getSymbols()      │
    │        │                    │                                 │
    │        │                    └──► 硬编码数据 ──► 返回结果      │
    │        │                                                      │
    │        └──► Emoji ──► EmojiDataLoader.loadEmojiData()         │
    │                         │                                     │
    │                         ├──► 检查内存缓存                     │
    │                         │         │                           │
    │                         │         ├──► 命中 ──► 返回结果      │
    │                         │         │                           │
    │                         │         └──► 未命中 ──► 加载JSON    │
    │                         │                         │           │
    │                         │                         └──► 解析    │
    │                         │                               │     │
    │                         │                               └──► 存入缓存
    │                         │                                     │
    │                         └──► 返回结果 ◄───────────────────────┘
    │                               │
    │                               ▼
    │   SymbolAdapter.submitList(symbols)
    │                    │
    │                    ▼
    │   RecyclerView 刷新显示
    │
    ├──► 搜索符号 ─────────────────────────────────────────────────┐
    │                                                               │
    │   SymbolManager.searchSymbols(query)                          │
    │                    │                                          │
    │                    ├──► 检查搜索索引                          │
    │                    │         │                                │
    │                    │         ├──► 已构建 ──► 执行搜索         │
    │                    │         │                                │
    │                    │         └──► 未构建 ──► 构建索引         │
    │                    │                         │                │
    │                    │                         └──► 加载所有数据
    │                    │                               │          │
    │                    │                               └──► 索引化
    │                    │                                     │    │
    │                    └──► 返回搜索结果 ◄────────────────────┘    │
    │
    └──► 点击符号 ─────────────────────────────────────────────────┐
                                                                    │
    SymbolAdapter.onItemClick(symbol)                               │
                    │                                               │
                    ├──► SymbolManager.addToRecent(symbol)          │
                    │                    │                          │
                    │                    └──► 更新LRU缓存           │
                    │                          保存到SharedPrefs    │
                    │                                               │
                    └──► onSymbolClick?.invoke(symbol.char)         │
                                         │                          │
                                         └──► 输入到编辑器          │
                                                                    │
    (长按)                                                            │
    SymbolAdapter.onItemLongClick(symbol, view)                     │
                    │                                               │
                    └──► SymbolPreviewPopup.show(view, symbol)      │
                                         │                          │
                                         └──► 显示预览弹窗          │
                                                                    │
```

## 3. 类关系图

```
┌─────────────────────────────────────────────────────────────────┐
│                          数据模型层                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   Symbol ────────► SymbolCategory (enum)                        │
│      │                                                          │
│      │         Emoji ◄─────────── EmojiJsonData                 │
│      │              │                                           │
│      │              └──► toSymbol() ───────► Symbol             │
│      │                                                          │
│      └──► getAllSearchTerms()                                   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                          数据提供层                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   SymbolDataProvider (object)                                   │
│   │                                                             │
│   ├──► MATH_SYMBOLS : String                                    │
│   ├──► GREEK_SYMBOLS : String                                   │
│   ├──► UNIT_SYMBOLS : String                                    │
│   ├──► ...                                                      │
│   │                                                             │
│   └──► getSymbols(category) : List<Symbol>                      │
│            │                                                    │
│            └──► parseSymbols() ───► Symbol.fromChar()           │
│                                                                 │
│   EmojiDataLoader                                               │
│   │                                                             │
│   ├──► loadEmojiData() : Map<Category, List<Emoji>>             │
│   │        │                                                    │
│   │        ├──► GZIP解压                                        │
│   │        ├──► JSON解析                                        │
│   │        └──► 分类映射                                        │
│   │                                                             │
│   └──► getEmojisByCategory(category) : List<Emoji>              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                          核心管理层                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   SymbolManager (Singleton)                                     │
│   │                                                             │
│   ├──► symbolManager : SymbolManager?                           │
│   │                                                             │
│   ├──► emojiDataLoader : EmojiDataLoader                        │
│   ├──► recentCache : RecentCache                                │
│   ├──► favoriteManager : FavoriteManager                        │
│   │                                                             │
│   ├──► basicSymbolCache : Map<Category, List<Symbol>>           │
│   ├──► emojiCache : Map<Category, List<Emoji>>?                 │
│   ├──► searchIndex : SymbolSearchIndex?                         │
│   │                                                             │
│   ├──► getSymbols(category) : List<Symbol>                      │
│   ├──► addToRecent(symbol)                                      │
│   ├──► toggleFavorite(symbol) : Boolean                         │
│   ├──► searchSymbols(query) : List<Symbol>                      │
│   │                                                             │
│   └──► release()                                                │
│                                                                 │
│   SymbolSearchIndex                                             │
│   │                                                             │
│   ├──► charIndex : Map<String, Symbol>                          │
│   ├──► keywordIndex : Map<String, List<Symbol>>                 │
│   ├──► pinyinIndex : Map<String, List<Symbol>>                  │
│   │                                                             │
│   ├──► search(query) : List<Symbol>                             │
│   └──► quickSearch(query) : List<Symbol>                        │
│                                                                 │
│   RecentCache (LRU)                                             │
│   │                                                             │
│   ├──► cache : LinkedHashMap<String, Symbol>                    │
│   ├──► maxSize = 20                                             │
│   │                                                             │
│   ├──► add(symbol)                                              │
│   ├──► getRecent() : List<Symbol>                               │
│   └──► clear()                                                  │
│                                                                 │
│   FavoriteManager                                               │
│   │                                                             │
│   ├──► favoriteSet : Set<String>                                │
│   │                                                             │
│   ├──► toggle(symbol) : Boolean                                 │
│   ├──► isFavorite(char) : Boolean                               │
│   └──► getFavorites() : Set<String>                             │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                          UI组件层                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   SymbolKeyboardView (FrameLayout)                              │
│   │                                                             │
│   ├──► categoryTabLayout : TabLayout                            │
│   ├──► symbolRecyclerView : RecyclerView                        │
│   ├──► symbolAdapter : SymbolAdapter                            │
│   │                                                             │
│   ├──► onSymbolClick : ((String) -> Unit)?                      │
│   ├──► onSymbolLongClick : ((Symbol, View) -> Boolean)?         │
│   │                                                             │
│   ├──► switchCategory(category)                                 │
│   ├──► loadSymbols(category)                                    │
│   └──► refresh()                                                │
│                                                                 │
│   SymbolAdapter (ListAdapter<Symbol, ViewHolder>)               │
│   │                                                             │
│   ├──► onItemClick : (Symbol) -> Unit                           │
│   ├──► onItemLongClick : (Symbol, View) -> Boolean              │
│   │                                                             │
│   └──► SymbolDiffCallback                                       │
│                                                                 │
│   SymbolSearchView (FrameLayout)                                │
│   │                                                             │
│   ├──► searchEditText : EditText                                │
│   ├──► resultRecyclerView : RecyclerView                        │
│   │                                                             │
│   ├──► performSearch(query)                                     │
│   └──► onSymbolSelected : ((String) -> Unit)?                   │
│                                                                 │
│   SymbolPreviewPopup (object)                                   │
│   │                                                             │
│   └──► show(anchor, symbol)                                     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## 4. 内存分配图

```
┌─────────────────────────────────────────────────────────────────┐
│                     内存占用预算 (约 6 MB)                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  基础符号数据                   12 KB  [░░░░░░░░░░░░░░░░]│   │
│  │  - 10类符号硬编码                                        │   │
│  │  - 532个字符                                             │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  Emoji元数据                   469 KB  [░░░░░░░░░░░░░░░░]│   │
│  │  - 4000个Emoji运行时对象                                 │   │
│  │  - 每个约120字节                                         │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  最近使用缓存                    1 KB  [░░░░░░░░░░░░░░░░]│   │
│  │  - LRU 20个                                              │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  收藏数据                        5 KB  [░░░░░░░░░░░░░░░░]│   │
│  │  - 100个收藏                                             │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  搜索索引                      128 KB  [░░░░░░░░░░░░░░░░]│   │
│  │  - Emoji索引                                             │   │
│  │  - 基础符号索引                                          │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  UI组件内存                  5,120 KB  [████████████░░░░]│   │
│  │  - RecyclerView/Adapter      3 MB                       │   │
│  │  - ViewHolder缓存            1 MB                       │   │
│  │  - 其他UI组件                1 MB                       │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  Emoji图片缓存                 360 KB  [░░░░░░░░░░░░░░░░]│   │
│  │  - 当前显示缓存              120 KB                     │   │
│  │  - 预加载缓存                240 KB                     │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  总计                        ~6,095 KB  (~5.95 MB)      │   │
│  │  ✅ 远低于 20MB 限制                                     │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## 5. 懒加载策略

```
应用启动
    │
    ├──► 预加载基础符号（10类，约12KB）
    │         │
    │         └──► 立即完成，用户无感知
    │
    ├──► 加载最近使用（从SharedPrefs）
    │         │
    │         └──► 立即完成
    │
    ├──► 加载收藏（从SharedPrefs）
    │         │
    │         └──► 立即完成
    │
    └──► Emoji数据 ──► 延迟加载
              │
              ├──► 用户首次切换到Emoji分类时
              │         │
              │         └──► 后台线程加载JSON
              │                   │
              │                   ├──► GZIP解压
              │                   ├──► JSON解析
              │                   └──► 存入内存缓存
              │
              └──► 搜索功能首次使用时
                        │
                        └──► 构建搜索索引
                                  │
                                  ├──► 加载所有Emoji
                                  └──► 构建关键词索引
```

## 6. 文件清单

| 文件路径 | 说明 | 大小 |
|---------|------|------|
| `data/SymbolDataProvider.kt` | 基础符号硬编码 | ~5 KB |
| `model/Symbol.kt` | 符号数据模型 | ~1 KB |
| `model/Emoji.kt` | Emoji数据模型 | ~2 KB |
| `manager/SymbolManager.kt` | 核心管理器 | ~8 KB |
| `search/SymbolSearchIndex.kt` | 搜索索引 | ~5 KB |
| `emoji/EmojiDataLoader.kt` | Emoji加载器 | ~4 KB |
| `cache/RecentCache.kt` | 最近使用缓存 | ~2 KB |
| `cache/FavoriteManager.kt` | 收藏管理器 | ~2 KB |
| `cache/EmojiImageCache.kt` | Emoji图片缓存 | ~2 KB |
| `ui/SymbolKeyboardView.kt` | 键盘主视图 | ~4 KB |
| `ui/SymbolAdapter.kt` | 符号适配器 | ~2 KB |
| `ui/SymbolSearchView.kt` | 搜索视图 | ~3 KB |
| `ui/SymbolPreviewPopup.kt` | 预览弹窗 | ~2 KB |
| `memory/MemoryMonitor.kt` | 内存监控 | ~1 KB |
| `emoji/EmojiCompat.kt` | Emoji兼容性 | ~1 KB |
| `utils/DensityUtils.kt` | 密度工具 | ~1 KB |
| `utils/StringUtils.kt` | 字符串工具 | ~1 KB |
| `assets/emoji/emoji_data.json.gz` | Emoji数据（压缩） | ~200 KB |

## 7. 关键特性总结

| 特性 | 实现方案 | 优势 |
|------|---------|------|
| **数据存储** | 基础符号硬编码 + Emoji JSON压缩 | 快速访问、节省空间 |
| **懒加载** | 分类按需加载 | 减少启动时间、节省内存 |
| **搜索** | 多字段索引 + 拼音支持 | 快速搜索、中文友好 |
| **缓存** | LRU + LruCache | 自动回收、内存可控 |
| **最近使用** | LinkedHashMap + SharedPrefs | 持久化、自动去重 |
| **收藏** | Set + SharedPrefs | 快速查询、持久化 |
| **预览** | PopupWindow | 轻量、可定制 |
| **Emoji兼容** | 系统版本检测 | 自动回退、兼容性好 |
| **内存监控** | 实时统计 + 阈值警告 | 可控、可优化 |

## 8. 使用示例

```kotlin
// ====== 初始化 ======
class MyInputMethodService : InputMethodService() {
    
    override fun onCreate() {
        super.onCreate()
        SymbolManager.getInstance(this).initialize()
    }
    
    override fun onCreateInputView(): View {
        return SymbolKeyboardView(this).apply {
            onSymbolClick = { symbol ->
                currentInputConnection?.commitText(symbol, 1)
            }
        }
    }
}

// ====== 搜索 ======
lifecycleScope.launch {
    val results = symbolManager.searchSymbolsAsync("爱心")
    updateSearchResults(results)
}

// ====== 收藏 ======
val isFavorite = symbolManager.toggleFavorite(symbol)
favoriteButton.isSelected = isFavorite

// ====== 内存监控 ======
MemoryMonitor.logMemoryUsage("Keyboard")
if (MemoryMonitor.shouldReleaseMemory()) {
    EmojiImageCache.getInstance().clear()
}
```
