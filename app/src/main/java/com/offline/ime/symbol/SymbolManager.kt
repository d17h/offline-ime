package com.offline.ime.symbol

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 符号系统管理器 - 所有符号本地存储，零网络
 * 内存预算：峰值<20MB
 */
class SymbolManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val recentCache = LinkedHashSet<String>()
    private val favoriteCache = LinkedHashSet<String>()

    // 符号分类数据（硬编码，无需网络）
    val categories: List<SymbolCategory> = listOf(
        SymbolCategory("math", "数学", MATH_SYMBOLS),
        SymbolCategory("greek", "希腊", GREEK_LETTERS),
        SymbolCategory("units", "单位", UNIT_SYMBOLS),
        SymbolCategory("currency", "货币", CURRENCY_SYMBOLS),
        SymbolCategory("arrows", "箭头", ARROW_SYMBOLS),
        SymbolCategory("shapes", "图形", SHAPE_SYMBOLS),
        SymbolCategory("pinyin", "拼音", PINYIN_SYMBOLS),
        SymbolCategory("radicals", "部首", RADICAL_SYMBOLS),
        SymbolCategory("special", "特殊", SPECIAL_SYMBOLS),
        SymbolCategory("kaomoji", "颜文字", KAOMOJI_SYMBOLS)
    )

    fun initialize() {
        loadRecent()
        loadFavorites()
    }

    fun getSymbolsByCategory(categoryId: String): List<String> {
        return categories.find { it.id == categoryId }?.symbols ?: emptyList()
    }

    fun addRecent(symbol: String) {
        recentCache.remove(symbol)
        recentCache.add(symbol)
        if (recentCache.size > MAX_RECENT) {
            recentCache.remove(recentCache.first())
        }
        saveRecent()
    }

    fun getRecentSymbols(): List<String> = recentCache.toList().reversed()

    fun toggleFavorite(symbol: String): Boolean {
        val added = if (favoriteCache.contains(symbol)) {
            favoriteCache.remove(symbol)
            false
        } else {
            favoriteCache.add(symbol)
            true
        }
        saveFavorites()
        return added
    }

    fun isFavorite(symbol: String): Boolean = favoriteCache.contains(symbol)
    fun getFavorites(): List<String> = favoriteCache.toList()

    fun searchSymbols(query: String): List<SearchResult> {
        if (query.isEmpty()) return emptyList()
        val results = mutableListOf<SearchResult>()
        val lowerQuery = query.lowercase()

        categories.forEach { category ->
            category.symbols.forEachIndexed { index, symbol ->
                if (symbol.contains(query) || getSymbolName(symbol).contains(lowerQuery)) {
                    results.add(SearchResult(symbol, category.name, index))
                }
            }
        }
        return results
    }

    fun clearCache() {
        // 清理时保留最近和收藏
    }

    private fun getSymbolName(symbol: String): String {
        // 简化实现：返回符号本身
        return symbol
    }

    private fun loadRecent() {
        val json = prefs.getString(KEY_RECENT, "[]") ?: "[]"
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                recentCache.add(array.getString(i))
            }
        } catch (_: Exception) {}
    }

    private fun saveRecent() {
        val array = JSONArray()
        recentCache.forEach { array.put(it) }
        prefs.edit().putString(KEY_RECENT, array.toString()).apply()
    }

    private fun loadFavorites() {
        val json = prefs.getString(KEY_FAVORITES, "[]") ?: "[]"
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                favoriteCache.add(array.getString(i))
            }
        } catch (_: Exception) {}
    }

    private fun saveFavorites() {
        val array = JSONArray()
        favoriteCache.forEach { array.put(it) }
        prefs.edit().putString(KEY_FAVORITES, array.toString()).apply()
    }

    data class SymbolCategory(val id: String, val name: String, val symbols: List<String>)
    data class SearchResult(val symbol: String, val category: String, val index: Int)

    companion object {
        private const val PREFS_NAME = "symbol_prefs"
        private const val KEY_RECENT = "recent_symbols"
        private const val KEY_FAVORITES = "favorite_symbols"
        private const val MAX_RECENT = 20

        @Volatile
        private var instance: SymbolManager? = null

        fun getInstance(context: Context): SymbolManager {
            return instance ?: synchronized(this) {
                instance ?: SymbolManager(context.applicationContext).also { instance = it }
            }
        }

        // ===== 符号数据（硬编码，零网络） =====
        val MATH_SYMBOLS = listOf(
            "+", "−", "×", "÷", "=", "≠", "≈", "≡", "<", ">", "≤", "≥",
            "±", "∓", "∝", "∞", "∑", "∏", "∫", "∮", "∂", "√", "∛", "∜",
            "∴", "∵", "∈", "∉", "⊂", "⊃", "⊆", "⊇", "∩", "∪",
            "∧", "∨", "¬", "∀", "∃", "∄",
            "⁰", "¹", "²", "³", "⁴", "⁵", "⁶", "⁷", "⁸", "⁹",
            "₀", "₁", "₂", "₃", "₄", "₅", "₆", "₇", "₈", "₉",
            "½", "⅓", "⅔", "¼", "¾", "⅕", "⅖", "⅗", "⅘", "⅚", "⅛", "⅜", "⅝", "⅞",
            "Ⅰ", "Ⅱ", "Ⅲ", "Ⅳ", "Ⅴ", "Ⅵ", "Ⅶ", "Ⅷ", "Ⅸ", "Ⅹ"
        )

        val GREEK_LETTERS = listOf(
            "Α", "α", "Β", "β", "Γ", "γ", "Δ", "δ", "Ε", "ε",
            "Ζ", "ζ", "Η", "η", "Θ", "θ", "Ι", "ι", "Κ", "κ",
            "Λ", "λ", "Μ", "μ", "Ν", "ν", "Ξ", "ξ", "Ο", "ο",
            "Π", "π", "Ρ", "ρ", "Σ", "σ", "ς", "Τ", "τ", "Υ", "υ",
            "Φ", "φ", "Χ", "χ", "Ψ", "ψ", "Ω", "ω"
        )

        val UNIT_SYMBOLS = listOf(
            "nm", "μm", "mm", "cm", "m", "km", "㎡", "㎢", "亩", "公顷",
            "ml", "l", "㎖", "㎗", "㎘", "㏄", "mg", "g", "kg", "t",
            "斤", "两", "磅", "oz", "℃", "℉", "K", "s", "min", "h", "d", "㎧", "㎨"
        )

        val CURRENCY_SYMBOLS = listOf(
            "¥", "$", "€", "£", "₩", "₽", "₹", "₱", "฿", "₫",
            "₴", "₪", "₡", "₢", "₣", "₤", "₥", "₦", "₧", "₨",
            "₩", "₪", "₫", "€", "₭", "₮", "₯", "₰", "₱", "₲",
            "₳", "₴", "₵", "₶", "₷", "₸", "₹", "₺", "₻", "₼", "₽", "₾", "₿"
        )

        val ARROW_SYMBOLS = listOf(
            "←", "↑", "→", "↓", "↔", "↕", "↖", "↗", "↘", "↙",
            "⇐", "⇑", "⇒", "⇓", "⇔", "⇕", "⇦", "⇧", "⇨", "⇩",
            "⤡", "⤢", "⤣", "⤤", "⤥", "⤦", "⤧", "⤨", "⤩", "⤪",
            "⟵", "⟶", "⟷", "⟸", "⟹", "⟺"
        )

        val SHAPE_SYMBOLS = listOf(
            "■", "□", "▲", "△", "▼", "▽", "◆", "◇", "○", "●",
            "◎", "◐", "◑", "★", "☆", "✦", "✧",
            "┌", "┬", "┐", "├", "┼", "┤", "└", "┴", "┘", "│", "─",
            "╔", "╦", "╗", "╠", "╬", "╣", "╚", "╩", "╝", "║", "═"
        )

        val PINYIN_SYMBOLS = listOf(
            "ā", "á", "ǎ", "à", "ō", "ó", "ǒ", "ò", "ē", "é", "ě", "è",
            "ī", "í", "ǐ", "ì", "ū", "ú", "ǔ", "ù", "ǖ", "ǘ", "ǚ", "ǜ",
            "ㄅ", "ㄆ", "ㄇ", "ㄈ", "ㄉ", "ㄊ", "ㄋ", "ㄌ", "ㄍ", "ㄎ", "ㄏ",
            "ㄐ", "ㄑ", "ㄒ", "ㄓ", "ㄔ", "ㄕ", "ㄖ", "ㄗ", "ㄘ", "ㄙ",
            "ㄚ", "ㄛ", "ㄜ", "ㄝ", "ㄞ", "ㄟ", "ㄠ", "ㄡ", "ㄢ", "ㄣ", "ㄤ", "ㄥ", "ㄦ",
            "ㄧ", "ㄨ", "ㄩ"
        )

        val RADICAL_SYMBOLS = listOf(
            "亻", "冫", "冖", "讠", "扌", "氵", "忄", "宀", "辶", "艹",
            "廾", "攵", "爫", "犭", "疒", "癶", "衤", "鸟", "饣", "钅",
            "礻", "罒", "耂", "虍", "囗", "弋", "刂", "爻"
        )

        val SPECIAL_SYMBOLS = listOf(
            "©", "®", "™", "℠", "℗", "℡", "℻",
            "☀", "☁", "☂", "☃", "☄", "★", "☆",
            "♠", "♥", "♦", "♣", "♩", "♪", "♫", "♬", "♭", "♮", "♯",
            "♈", "♉", "♊", "♋", "♌", "♍", "♎", "♏", "♐", "♑", "♒", "♓",
            "♂", "♀", "⚧", "♻", "☑", "☒", "☐", "✓", "✗", "✔", "✘",
            "＊", "※", "•", "·", "⋆", "✦", "✧", "✪", "✯", "✡", "✺",
            "☎", "✈", "✉", "✂", "✏", "✒", "✎", "✐",
            "❄", "❅", "❆", "❇", "❈", "❉", "❊", "❋"
        )

        val KAOMOJI_SYMBOLS = listOf(
            "(^_^)", "(´▽`)", "(｡♥‿♥｡)", "٩(◕‿◕｡)۶", "(T_T)", "(╥﹏╥)",
            "(｡•́︿•̀｡)", "(╬ Ò﹏Ó)", "(╯°□°）╯︵ ┻━┻", "(O_O)", "(°o°:)",
            "(⊙_⊙)", "(=^･ω･^=)", "(｡◕‿◕｡)", "(◠‿◠✿)", "(¬‿¬)",
            "(✯◡✯)", "(⌒‿⌒)", "(◕‿◕✿)", "(◠‿◕)", "(❁´◡`❁)",
            "(☆▽☆)", "(✧ω✧)", "(◍•ᴗ•◍)", "(｡･ω･｡)ﾉ♡",
            "╮(￣ω￣;)╭", "(￢_￢;)", "(¬_¬;)", "(；￣Д￣)", "(╯︵╰,)",
            "( ﾟдﾟ)つ", "(っ˘̩╭╮˘̩)っ", "(｡•́︿•̀｡)", "(个_个)", "(╥﹏╥)",
            "¯\_(ツ)_/¯", "(╯°□°)╯", "(┛◉Д◉)┛", "(「✖益✖)」", "(ノಠ益ಠ)ノ"
        )
    }
}