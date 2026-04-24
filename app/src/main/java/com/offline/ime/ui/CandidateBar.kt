package com.offline.ime.ui

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.offline.ime.R

/**
 * 候选词栏 - 显示拼音输入的候选词
 */
class CandidateBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : HorizontalScrollView(context, attrs) {

    private val container: LinearLayout
    var onCandidateSelected: ((String) -> Unit)? = null
    private var maxCandidates = 9

    init {
        isHorizontalScrollBarEnabled = false
        container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 0, 8, 0)
        }
        addView(container)
    }

    fun setCandidates(candidates: List<String>) {
        container.removeAllViews()
        val displayList = candidates.take(maxCandidates)
        displayList.forEachIndexed { index, candidate ->
            val view = createCandidateView(candidate, index + 1)
            container.addView(view)
        }
    }

    fun clearCandidates() {
        container.removeAllViews()
    }

    fun setMaxCandidates(max: Int) {
        maxCandidates = max
    }

    private fun createCandidateView(candidate: String, number: Int): View {
        return TextView(context).apply {
            text = "$number.$candidate"
            textSize = 18f
            setTextColor(resources.getColor(R.color.candidate_text, null))
            setPadding(16, 8, 16, 8)
            gravity = Gravity.CENTER
            setOnClickListener { onCandidateSelected?.invoke(candidate) }
        }
    }
}