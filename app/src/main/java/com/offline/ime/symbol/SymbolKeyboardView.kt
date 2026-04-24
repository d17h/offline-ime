package com.offline.ime.symbol

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.offline.ime.R

/**
 * 符号键盘视图 - 集成分类Tab和符号网格
 */
class SymbolKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val tabScroll: HorizontalScrollView
    private val tabContainer: LinearLayout
    private val symbolGrid: GridLayout
    private val symbolManager = SymbolManager.getInstance(context)
    private var currentCategoryId = "math"
    var onSymbolClick: ((String) -> Unit)? = null

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.keyboard_symbols, this, true)

        tabScroll = findViewById(R.id.tab_scroll)
        tabContainer = findViewById(R.id.tab_container)
        symbolGrid = findViewById(R.id.symbol_grid)

        setupTabs()
        showCategory("math")
    }

    private fun setupTabs() {
        val tabs = listOf(
            "math" to "数学", "greek" to "希腊", "units" to "单位",
            "currency" to "货币", "arrows" to "箭头", "shapes" to "图形",
            "pinyin" to "拼音", "radicals" to "部首", "special" to "特殊",
            "kaomoji" to "颜文字"
        )

        tabs.forEach { (id, name) ->
            val tabView = TextView(context).apply {
                text = name
                textSize = 14f
                setPadding(16, 8, 16, 8)
                setBackgroundResource(R.drawable.tab_background)
                setOnClickListener {
                    currentCategoryId = id
                    showCategory(id)
                    updateTabSelection(id)
                }
            }
            tabContainer.addView(tabView)
        }
    }

    private fun showCategory(categoryId: String) {
        symbolGrid.removeAllViews()
        val symbols = symbolManager.getSymbolsByCategory(categoryId)

        symbols.forEach { symbol ->
            val button = Button(context).apply {
                text = symbol
                textSize = when {
                    symbol.length > 8 -> 10f
                    symbol.length > 4 -> 12f
                    else -> 16f
                }
                setOnClickListener {
                    symbolManager.addRecent(symbol)
                    onSymbolClick?.invoke(symbol)
                }
                setOnLongClickListener {
                    // 长按预览（简化版）
                    showPreview(symbol)
                    true
                }
            }
            symbolGrid.addView(button)
        }
    }

    private fun updateTabSelection(selectedId: String) {
        for (i in 0 until tabContainer.childCount) {
            val child = tabContainer.getChildAt(i) as TextView
            val isSelected = symbolManager.categories.getOrNull(i)?.id == selectedId
            child.isSelected = isSelected
            if (isSelected) {
                child.setTextColor(resources.getColor(R.color.candidate_selected, null))
            } else {
                child.setTextColor(resources.getColor(R.color.key_text_color, null))
            }
        }
    }

    private fun showPreview(symbol: String) {
        // 符号预览实现（可扩展为PopupWindow）
    }
}