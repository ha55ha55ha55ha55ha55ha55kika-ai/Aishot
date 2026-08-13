package com.example.screenai

import android.app.Activity
import android.content.Context
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
}            "gemini-3.5-flash-lite",
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
