package com.example.screenai

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.*
import android.view.Gravity

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

        layout.addView(label("Claude API ключ:"))
        val claudeKey = EditText(this).apply { setText(prefs.getString("claude_key", "")) }
        layout.addView(claudeKey)

        layout.addView(label("OpenAI API ключ:"))
        val openaiKey = EditText(this).apply { setText(prefs.getString("openai_key", "")) }
        layout.addView(openaiKey)

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
                    putString("claude_key", claudeKey.text.toString().trim())
                    putString("openai_key", openaiKey.text.toString().trim())
                    putString("prompt", promptField.text.toString().trim())
                    putString("button_label", buttonLabel.text.toString().trim().ifEmpty { "AI" })
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
