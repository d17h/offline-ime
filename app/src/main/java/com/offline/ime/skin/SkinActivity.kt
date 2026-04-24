package com.offline.ime.skin

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.offline.ime.R

/**
 * 皮肤设置界面
 */
class SkinActivity : AppCompatActivity() {

    private lateinit var radioGroup: RadioGroup
    private lateinit var heightSeekBar: SeekBar
    private lateinit var heightValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_skin)

        title = getString(R.string.skin_title)
        val prefs = getSharedPreferences("ime_settings", MODE_PRIVATE)

        radioGroup = findViewById(R.id.skin_radio_group)
        heightSeekBar = findViewById(R.id.height_seekbar)
        heightValue = findViewById(R.id.height_value)

        // 加载内置皮肤选项
        val skins = listOf(
            "default" to "默认深色",
            "light" to "浅色",
            "blue" to "蓝色",
            "green" to "绿色",
            "purple" to "紫色"
        )

        skins.forEachIndexed { index, (id, name) ->
            val radio = RadioButton(this).apply {
                text = name
                tag = id
                id = index + 1
            }
            radioGroup.addView(radio)
        }

        // 恢复选中状态
        val currentSkin = prefs.getString("skin", "default")
        for (i in 0 until radioGroup.childCount) {
            val child = radioGroup.getChildAt(i) as RadioButton
            if (child.tag == currentSkin) {
                radioGroup.check(child.id)
                break
            }
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val selected = radioGroup.findViewById<RadioButton>(checkedId)
            prefs.edit().putString("skin", selected.tag.toString()).apply()
            Toast.makeText(this, "已选择: ${selected.text}", Toast.LENGTH_SHORT).show()
        }

        // 键盘高度设置
        val currentHeight = prefs.getInt("keyboard_height", 48)
        heightSeekBar.progress = currentHeight - 30
        heightValue.text = "${currentHeight}dp"

        heightSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val height = progress + 30
                heightValue.text = "${height}dp"
                prefs.edit().putInt("keyboard_height", height).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
}