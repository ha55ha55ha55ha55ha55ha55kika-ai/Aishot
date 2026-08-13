package com.example.screenai

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.*
import android.view.Gravity
import android.view.View

class SettingsActivity : Activity() {

    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("screenai_prefs", Context.MODE_PRIVATE)

        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }
        scroll.addView(layout)
        setContentView(scroll)

        fun label(text: String) = TextView(this).apply {
            this.text = text
            textSize = 16f
            setPadding(0, 30, 0, 10)
        }

        fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

        // Провайдер
        layout.addView(label("Активный AI провайдер:"))
        val providerSpinner = Spinner(this)
        val providers = arrayOf("Gemini", "Claude", "OpenAI")
        providerSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, providers)
        val currentProvider = prefs.getString("provider", "Gemini")
        providerSpinner.setSelection(providers.indexOf(currentProvider).coerceAtLeast(0))
        layout.addView(providerSpinner)

        // Ключи
        layout.addView(label("Gemini API ключ:"))
        val geminiKey = EditText(this).apply { setText(prefs.getString("gemini_key", "")) }
        layout.addView(geminiKey)

        // Модель Gemini: список известных моделей + возможность вписать свою вручную
        // (Google часто переименовывает модели, поэтому список не всегда актуален —
        // выбирайте "Своя модель (ввести вручную)" и вписывайте точный ID из документации Google).
        layout.addView(label("Модель Gemini:"))
        val geminiModels = arrayOf(
            "gemini-3.6-flash",
            "gemini-3.5-flash-lite",
            "gemini-3.1-pro-preview",
            "gemini-3-pro-preview",
            "gemini-2.5-flash",
            "gemini-2.5-pro",
            "Своя модель (ввести вручную)"
        )
        val savedGeminiModel = prefs.getString("gemini_model", "gemini-3.6-flash") ?: "gemini-3.6-flash"
        val geminiModelSpinner = Spinner(this)
        geminiModelSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, geminiModels)
        val geminiModelCustom = EditText(this).apply {
            hint = "Например: gemini-3.1-flash-lite"
        }
        val knownIndex = geminiModels.indexOf(savedGeminiModel)
        if (knownIndex >= 0) {
            geminiModelSpinner.setSelection(knownIndex)
            geminiModelCustom.visibility = View.GONE
        } else {
            geminiModelSpinner.setSelection(geminiModels.size - 1) // "Своя модель"
            geminiModelCustom.setText(savedGeminiModel)
            geminiModelCustom.visibility = View.VISIBLE
        }
        geminiModelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                geminiModelCustom.visibility = if (position == geminiModels.size - 1) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        layout.addView(geminiModelSpinner)
        layout.addView(geminiModelCustom)

        layout.addView(label("Claude API ключ:"))
        val claudeKey = EditText(this).apply { setText(prefs.getString("claude_key", "")) }
        layout.addView(claudeKey)

        layout.addView(label("Модель Claude:"))
        val claudeModel = EditText(this).apply {
            setText(prefs.getString("claude_model", "claude-sonnet-4-5"))
            hint = "Например: claude-sonnet-4-5, claude-opus-4-8"
        }
        layout.addView(claudeModel)

        layout.addView(label("OpenAI API ключ:"))
        val openaiKey = EditText(this).apply { setText(prefs.getString("openai_key", "")) }
        layout.addView(openaiKey)

        layout.addView(label("Модель OpenAI:"))
        val openaiModel = EditText(this).apply {
            setText(prefs.getString("openai_model", "gpt-4o"))
            hint = "Например: gpt-4o, gpt-5"
        }
        layout.addView(openaiModel)

        // ---- Внешний вид плавающей кнопки ----

        // Размер кнопки (в dp)
        layout.addView(label("Размер кнопки AI:"))
        val sizeValueLabel = TextView(this)
        val savedSize = prefs.getInt("button_size_dp", 56)
        sizeValueLabel.text = "$savedSize dp"
        val sizeSeekBar = SeekBar(this).apply {
            max = 100 // диапазон 40..140 dp
            progress = (savedSize - 40).coerceIn(0, 100)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    sizeValueLabel.text = "${40 + progress} dp"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        layout.addView(sizeSeekBar)
        layout.addView(sizeValueLabel)

        // Цвет кнопки: палитра готовых цветов + свои RGB-ползунки с превью
        layout.addView(label("Цвет кнопки:"))
        val savedColor = prefs.getInt("button_color", Color.parseColor("#2196F3"))

        val colorPreview = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(savedColor)
            }
        }
        layout.addView(colorPreview, LinearLayout.LayoutParams(dp(48), dp(48)).apply {
            topMargin = 10
            bottomMargin = 10
        })

        fun updatePreview(color: Int) {
            (colorPreview.background as GradientDrawable).setColor(color)
        }

        // Готовая палитра
        val palette = intArrayOf(
            Color.parseColor("#2196F3"), // синий
            Color.parseColor("#4CAF50"), // зелёный
            Color.parseColor("#F44336"), // красный
            Color.parseColor("#FF9800"), // оранжевый
            Color.parseColor("#9C27B0"), // фиолетовый
            Color.parseColor("#00BCD4"), // бирюзовый
            Color.parseColor("#FFEB3B"), // жёлтый
            Color.parseColor("#607D8B")  // серый
        )
        val paletteRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        // R/G/B ползунки — объявляем заранее, чтобы палитра могла их обновлять
        val redSeek = SeekBar(this).apply { max = 255 }
        val greenSeek = SeekBar(this).apply { max = 255 }
        val blueSeek = SeekBar(this).apply { max = 255 }

        fun currentRgbColor() = Color.rgb(redSeek.progress, greenSeek.progress, blueSeek.progress)

        redSeek.progress = Color.red(savedColor)
        greenSeek.progress = Color.green(savedColor)
        blueSeek.progress = Color.blue(savedColor)

        val rgbListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) updatePreview(currentRgbColor())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        redSeek.setOnSeekBarChangeListener(rgbListener)
        greenSeek.setOnSeekBarChangeListener(rgbListener)
        blueSeek.setOnSeekBarChangeListener(rgbListener)

        for (c in palette) {
            val swatch = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(c)
                }
                setOnClickListener {
                    updatePreview(c)
                    redSeek.progress = Color.red(c)
                    greenSeek.progress = Color.green(c)
                    blueSeek.progress = Color.blue(c)
                }
            }
            paletteRow.addView(swatch, LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                marginEnd = dp(10)
            })
        }
        layout.addView(paletteRow)

        layout.addView(label("Свой цвет (R/G/B):"))
        layout.addView(redSeek)
        layout.addView(greenSeek)
        layout.addView(blueSeek)

        // Промпт
        layout.addView(label("Промпт (что спрашивать у AI):"))
        val promptField = EditText(this).apply {
            setText(prefs.getString("prompt", "Опиши что на экране. Если ошибка - предложи решение."))
            minLines = 3
        }
        layout.addView(promptField)

        // Текст на кнопке
        layout.addView(label("Текст на плавающей кнопке:"))
        val buttonLabel = EditText(this).apply {
            setText(prefs.getString("button_label", "AI"))
        }
        layout.addView(buttonLabel)

        // Сохранить
        val saveBtn = Button(this).apply {
            text = "Сохранить"
            setOnClickListener {
                prefs.edit().apply {
                    putString("provider", providers[providerSpinner.selectedItemPosition])
                    putString("gemini_key", geminiKey.text.toString().trim())
                    val chosenGeminiModel = if (geminiModelSpinner.selectedItemPosition == geminiModels.size - 1)
                        geminiModelCustom.text.toString().trim().ifEmpty { "gemini-3.6-flash" }
                    else geminiModels[geminiModelSpinner.selectedItemPosition]
                    putString("gemini_model", chosenGeminiModel)
                    putString("claude_key", claudeKey.text.toString().trim())
                    putString("claude_model", claudeModel.text.toString().trim().ifEmpty { "claude-sonnet-4-5" })
                    putString("openai_key", openaiKey.text.toString().trim())
                    putString("openai_model", openaiModel.text.toString().trim().ifEmpty { "gpt-4o" })
                    putString("prompt", promptField.text.toString().trim())
                    putString("button_label", buttonLabel.text.toString().trim().ifEmpty { "AI" })
                    putInt("button_size_dp", 40 + sizeSeekBar.progress)
                    putInt("button_color", currentRgbColor())
                    apply()
                }
                Toast.makeText(this@SettingsActivity, "Сохранено", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        layout.addView(saveBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = 40
        })
    }
}
