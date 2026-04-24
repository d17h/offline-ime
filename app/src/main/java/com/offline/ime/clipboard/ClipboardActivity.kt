package com.offline.ime.clipboard

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.offline.ime.R

/**
 * 剪贴板管理界面 - 完全离线
 */
class ClipboardActivity : AppCompatActivity() {

    private lateinit var db: ClipboardDatabase
    private lateinit var searchInput: EditText
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clipboard)

        db = ClipboardDatabase.getInstance(this)
        searchInput = findViewById(R.id.search_input)
        container = findViewById(R.id.clipboard_container)

        findViewById<Button>(R.id.btn_search).setOnClickListener { performSearch() }
        findViewById<Button>(R.id.btn_clear).setOnClickListener { clearAll() }

        loadRecentItems()
    }

    private fun loadRecentItems() {
        container.removeAllViews()
        val items = db.getRecentItems(50)

        if (items.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "暂无剪贴板记录"
                textSize = 16f
                setPadding(32, 32, 32, 32)
            }
            container.addView(emptyView)
            return
        }

        items.forEach { item ->
            val itemView = createItemView(item)
            container.addView(itemView)
        }
    }

    private fun createItemView(item: ClipboardDatabase.ClipboardItem): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 8, 16, 8)
        }

        val contentView = TextView(this).apply {
            text = item.content
            textSize = 16f
            maxLines = 3
            setPadding(8, 8, 8, 8)
        }

        val actionsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val copyBtn = Button(this).apply {
            text = "复制"
            setOnClickListener {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", item.content))
                Toast.makeText(this@ClipboardActivity, "已复制", Toast.LENGTH_SHORT).show()
            }
        }

        val deleteBtn = Button(this).apply {
            text = "删除"
            setOnClickListener {
                db.deleteItem(item.id)
                loadRecentItems()
            }
        }

        val favBtn = Button(this).apply {
            text = "收藏"
            setOnClickListener {
                db.toggleFavorite(item.content)
                Toast.makeText(this@ClipboardActivity, "已收藏", Toast.LENGTH_SHORT).show()
            }
        }

        actionsLayout.addView(copyBtn)
        actionsLayout.addView(favBtn)
        actionsLayout.addView(deleteBtn)

        // 显示拆词结果
        val extractedItems = db.getExtractedItems(item.id)
        val extractedLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        extractedItems.forEach { extracted ->
            val chip = TextView(this@ClipboardActivity).apply {
                text = "${getTypeLabel(extracted.type)}: ${extracted.value}"
                textSize = 12f
                setPadding(8, 4, 8, 4)
                setBackgroundResource(R.drawable.chip_background)
            }
            extractedLayout.addView(chip)
        }

        layout.addView(contentView)
        if (extractedItems.isNotEmpty()) {
            layout.addView(extractedLayout)
        }
        layout.addView(actionsLayout)

        return layout
    }

    private fun getTypeLabel(type: String): String {
        return when (type) {
            "phone" -> "电话"
            "url" -> "链接"
            "email" -> "邮箱"
            "verify_code" -> "验证码"
            "code" -> "数字码"
            "address" -> "地址"
            else -> type
        }
    }

    private fun performSearch() {
        val query = searchInput.text.toString()
        if (query.isEmpty()) {
            loadRecentItems()
            return
        }

        container.removeAllViews()
        val items = db.searchItems(query)
        items.forEach { item ->
            container.addView(createItemView(item))
        }
    }

    private fun clearAll() {
        db.getRecentItems(200).forEach { db.deleteItem(it.id) }
        loadRecentItems()
        Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show()
    }
}