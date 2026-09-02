package com.example.screenai

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.*
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Callback
import okhttp3.Call
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class SettingsActivity : Activity() {

    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("screenai_prefs", Context.MODE_PRIVATE)

        fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

        val scroll = ScrollView(this).apply {
            background = Ui.screenBackground()
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        scroll.addView(layout)
        setContentView(scroll)

        fun label(text: String) = TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(Color.parseColor(Ui.TEXT_SECONDARY))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(22), 0, dp(8))
        }

        // Небольшая обёртка-секция: свой LinearLayout, который можно целиком
        // показать/спрятать одним вызовом — так на экране остаются видны
        // только поля, реально относящиеся к выбранному провайдеру/режиму.
        fun section(): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // ---- Провайдер ----
        layout.addView(label("Активный AI провайдер:"))
        val providerSpinner = Spinner(this)
        val providers = arrayOf("Gemini", "Claude", "OpenAI", "Grok", "DeepSeek", "Mistral", "OpenRouter")
        providerSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, providers)
        val currentProvider = prefs.getString("provider", "Gemini")
        providerSpinner.setSelection(providers.indexOf(currentProvider).coerceAtLeast(0))
        layout.addView(providerSpinner)

        // ---- Режим захвата ----
        layout.addView(label("Режим кнопки:"))
        val captureModes = arrayOf("Скриншот", "Фронтальная камера", "Микс (обе кнопки)", "🔴 ИИ-камера Live", "🎤 Слушатель (голос)")
        val captureModeKeys = arrayOf("screenshot", "camera", "mix", "live", "listen")
        val captureModeSpinner = Spinner(this)
        captureModeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, captureModes)
        val savedCaptureMode = prefs.getString("capture_mode", "screenshot") ?: "screenshot"
        captureModeSpinner.setSelection(captureModeKeys.indexOf(savedCaptureMode).coerceAtLeast(0))
        layout.addView(captureModeSpinner)
        layout.addView(TextView(this).apply {
            text = "Микс — две кнопки. ИИ-камера Live — трансляция экрана в реальном времени (только vision-провайдеры: Gemini, Claude, OpenAI, Grok, Mistral, OpenRouter)."
            textSize = 12f
            setTextColor(Color.parseColor(Ui.TEXT_SECONDARY))
            setPadding(0, dp(6), 0, dp(10))
        })

        // Названия кнопок для режима "Микс" — видно только когда он выбран
        val mixSection = section()
        mixSection.addView(label("Название левой кнопки (скриншот):"))
        val mixLabelScreenshot = EditText(this).apply {
            setText(prefs.getString("mix_label_screenshot", "Скрин"))
            hint = "Скрин"
        }
        mixSection.addView(mixLabelScreenshot)
        mixSection.addView(label("Название правой кнопки (камера):"))
        val mixLabelCamera = EditText(this).apply {
            setText(prefs.getString("mix_label_camera", "Камера"))
            hint = "Камера"
        }
        mixSection.addView(mixLabelCamera)
        layout.addView(mixSection)

        // --- Live mode info note ---
        val liveNoteSection = section()
        liveNoteSection.addView(TextView(this).apply {
            text = "🔴 Режим ИИ-камера Live\n\n" +
                "• Кружок запускает/останавливает трансляцию\n" +
                "• Поверх экрана появится панель с 3 полями:\n" +
                "  1. Задача — что нужно сделать\n" +
                "  2. Помощь — что уже знаешь / затруднение\n" +
                "  3. Ответ ИИ — что он видит и советует\n" +
                "• Работает только с vision-провайдерами: Gemini, Claude, OpenAI, Grok, Mistral, OpenRouter\n" +
                "• DeepSeek недоступен в Live-режиме"
            textSize = 13f
            setTextColor(Color.parseColor(Ui.TEXT_SECONDARY))
            setPadding(0, dp(10), 0, dp(10))
        })
        layout.addView(liveNoteSection)

        // ---- Настройки режима "Слушатель" (голос) ----
        val listenSection = section()
        listenSection.addView(TextView(this).apply {
            text = "🎤 Режим «Слушатель»\n\n" +
                "Нажатие на кружок запускает/останавливает прослушивание (видна иконка 🎤 " +
                "пока активно — приложение никогда не слушает скрытно). Распознанный вопрос " +
                "отправляется выбранному AI-провайдеру, ответ показывается в панели на экране " +
                "(можно закрепить 📌, скопировать 📋 или закрыть ✕, как и в остальных режимах)."
            textSize = 13f
            setTextColor(Color.parseColor(Ui.TEXT_SECONDARY))
            setPadding(0, dp(10), 0, dp(10))
        })

        val requireTriggerSwitch = Switch(this).apply {
            text = "Требовать слово-триггер перед вопросом"
            isChecked = prefs.getBoolean("listen_require_trigger", true)
            setTextColor(Color.parseColor(Ui.TEXT_PRIMARY))
        }
        listenSection.addView(requireTriggerSwitch)

        val triggerWordsLabel = label("Слова-триггеры (через запятую):")
        listenSection.addView(triggerWordsLabel)
        val triggerWordsField = EditText(this).apply {
            setText(prefs.getString("listen_trigger_words", ""))
            hint = "Например: Табаков, Паша"
        }
        listenSection.addView(triggerWordsField)
        listenSection.addView(TextView(this).apply {
            text = "Сказанное после слова-триггера (в той же фразе или в следующей) " +
                "считается вопросом. Если триггер выключен — приложение слушает постоянно " +
                "и само определяет, похожа ли фраза на вопрос."
            textSize = 12f
            setTextColor(Color.parseColor(Ui.TEXT_SECONDARY))
            setPadding(0, dp(4), 0, dp(10))
        })

        listenSection.addView(label("Автоскрытие ответа (секунд, 0 = не скрывать):"))
        val autoHideField = EditText(this).apply {
            setText(prefs.getInt("listen_autohide_sec", 60).toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "60"
        }
        listenSection.addView(autoHideField)

        listenSection.addView(label("Язык распознавания речи:"))
        val listenLanguages = arrayOf("Русский", "Українська")
        val listenLanguageKeys = arrayOf("ru-RU", "uk-UA")
        val listenLanguageSpinner = Spinner(this)
        listenLanguageSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listenLanguages)
        val savedListenLanguage = prefs.getString("listen_language", "ru-RU") ?: "ru-RU"
        listenLanguageSpinner.setSelection(listenLanguageKeys.indexOf(savedListenLanguage).coerceAtLeast(0))
        listenSection.addView(listenLanguageSpinner)

        fun updateTriggerWordsVisibility() {
            val visible = requireTriggerSwitch.isChecked
            triggerWordsLabel.visibility = if (visible) View.VISIBLE else View.GONE
            triggerWordsField.visibility = if (visible) View.VISIBLE else View.GONE
        }
        requireTriggerSwitch.setOnCheckedChangeListener { _, _ -> updateTriggerWordsVisibility() }
        updateTriggerWordsVisibility()

        layout.addView(listenSection)

        // ---- Ключи и модели по провайдерам (каждый — своя секция) ----

        val geminiSection = section()
        geminiSection.addView(label("Gemini API ключ:"))
        val geminiKey = EditText(this).apply { setText(prefs.getString("gemini_key", "")) }
        geminiSection.addView(geminiKey)
        geminiSection.addView(label("Модель Gemini:"))
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
        geminiSection.addView(geminiModelSpinner)
        geminiSection.addView(geminiModelCustom)
        layout.addView(geminiSection)

        val claudeSection = section()
        claudeSection.addView(label("Claude API ключ:"))
        val claudeKey = EditText(this).apply { setText(prefs.getString("claude_key", "")) }
        claudeSection.addView(claudeKey)
        claudeSection.addView(label("Модель Claude:"))
        val claudeModel = EditText(this).apply {
            setText(prefs.getString("claude_model", "claude-sonnet-4-5"))
            hint = "Например: claude-sonnet-4-5, claude-opus-4-8"
        }
        claudeSection.addView(claudeModel)
        layout.addView(claudeSection)

        val openaiSection = section()
        openaiSection.addView(label("OpenAI API ключ:"))
        val openaiKey = EditText(this).apply { setText(prefs.getString("openai_key", "")) }
        openaiSection.addView(openaiKey)
        openaiSection.addView(label("Модель OpenAI:"))
        val openaiModel = EditText(this).apply {
            setText(prefs.getString("openai_model", "gpt-4o"))
            hint = "Например: gpt-4o, gpt-5"
        }
        openaiSection.addView(openaiModel)
        layout.addView(openaiSection)

        val grokSection = section()
        grokSection.addView(label("Grok (xAI) API ключ:"))
        val grokKey = EditText(this).apply { setText(prefs.getString("grok_key", "")) }
        grokSection.addView(grokKey)
        grokSection.addView(label("Модель Grok:"))
        val grokModel = EditText(this).apply {
            setText(prefs.getString("grok_model", "grok-4"))
            hint = "Например: grok-4, grok-2-vision-1212"
        }
        grokSection.addView(grokModel)
        layout.addView(grokSection)

        val deepseekSection = section()
        deepseekSection.addView(label("DeepSeek API ключ:"))
        val deepseekKey = EditText(this).apply { setText(prefs.getString("deepseek_key", "")) }
        deepseekSection.addView(deepseekKey)
        deepseekSection.addView(label("Модель DeepSeek (без поддержки скриншотов):"))
        val deepseekModel = EditText(this).apply {
            setText(prefs.getString("deepseek_model", "deepseek-chat"))
            hint = "Например: deepseek-chat, deepseek-reasoner"
        }
        deepseekSection.addView(deepseekModel)
        layout.addView(deepseekSection)

        val mistralSection = section()
        mistralSection.addView(label("Mistral API ключ:"))
        val mistralKey = EditText(this).apply { setText(prefs.getString("mistral_key", "")) }
        mistralSection.addView(mistralKey)
        mistralSection.addView(label("Модель Mistral:"))
        val mistralModel = EditText(this).apply {
            setText(prefs.getString("mistral_model", "pixtral-large-latest"))
            hint = "Например: pixtral-large-latest"
        }
        mistralSection.addView(mistralModel)
        layout.addView(mistralSection)

        val openrouterSection = section()
        openrouterSection.addView(label("OpenRouter API ключ (доступ ко многим моделям одним ключом):"))
        val openrouterKey = EditText(this).apply { setText(prefs.getString("openrouter_key", "")) }
        openrouterSection.addView(openrouterKey)
        openrouterSection.addView(label("Модель OpenRouter:"))
        val openrouterModel = EditText(this).apply {
            setText(prefs.getString("openrouter_model", "anthropic/claude-3.5-sonnet"))
            hint = "Например: anthropic/claude-3.5-sonnet, x-ai/grok-2-vision-1212"
        }
        openrouterSection.addView(openrouterModel)
        layout.addView(openrouterSection)

        val providerSections = mapOf(
            "Gemini" to geminiSection,
            "Claude" to claudeSection,
            "OpenAI" to openaiSection,
            "Grok" to grokSection,
            "DeepSeek" to deepseekSection,
            "Mistral" to mistralSection,
            "OpenRouter" to openrouterSection
        )

        fun updateProviderVisibility() {
            val selected = providers[providerSpinner.selectedItemPosition]
            providerSections.forEach { (name, sec) ->
                sec.visibility = if (name == selected) View.VISIBLE else View.GONE
            }
        }

        fun updateModeVisibility() {
            val selected = captureModeKeys[captureModeSpinner.selectedItemPosition]
            mixSection.visibility = if (selected == "mix") View.VISIBLE else View.GONE
            liveNoteSection.visibility = if (selected == "live") View.VISIBLE else View.GONE
            listenSection.visibility = if (selected == "listen") View.VISIBLE else View.GONE
        }

        providerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateProviderVisibility()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        captureModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateModeVisibility()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        updateProviderVisibility()
        updateModeVisibility()

        // ---- Внешний вид плавающей кнопки ----

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

        val palette = intArrayOf(
            Color.parseColor("#2196F3"),
            Color.parseColor("#4CAF50"),
            Color.parseColor("#F44336"),
            Color.parseColor("#FF9800"),
            Color.parseColor("#9C27B0"),
            Color.parseColor("#00BCD4"),
            Color.parseColor("#FFEB3B"),
            Color.parseColor("#607D8B")
        )
        val paletteRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

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

        layout.addView(label("Промпт (что спрашивать у AI):"))
        val promptField = EditText(this).apply {
            setText(prefs.getString("prompt", "Опиши что на экране. Если ошибка - предложи решение."))
            minLines = 3
        }
        layout.addView(promptField)

        layout.addView(label("Текст на плавающей кнопке:"))
        val buttonLabel = EditText(this).apply {
            setText(prefs.getString("button_label", "AI"))
        }
        layout.addView(buttonLabel)

        val saveBtn = Button(this).apply {
            text = "Сохранить"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = Ui.accentButtonBackground(this@SettingsActivity)
            setPadding(dp(24), dp(16), dp(24), dp(16))
            stateListAnimator = null
            setOnClickListener {
                prefs.edit().apply {
                    putString("provider", providers[providerSpinner.selectedItemPosition])
                    putString("capture_mode", captureModeKeys[captureModeSpinner.selectedItemPosition])
                    putString("mix_label_screenshot", mixLabelScreenshot.text.toString().trim().ifEmpty { "Скрин" })
                    putString("mix_label_camera", mixLabelCamera.text.toString().trim().ifEmpty { "Камера" })
                    putString("gemini_key", geminiKey.text.toString().trim())
                    val chosenGeminiModel = if (geminiModelSpinner.selectedItemPosition == geminiModels.size - 1)
                        geminiModelCustom.text.toString().trim().ifEmpty { "gemini-3.6-flash" }
                    else geminiModels[geminiModelSpinner.selectedItemPosition]
                    putString("gemini_model", chosenGeminiModel)
                    putString("claude_key", claudeKey.text.toString().trim())
                    putString("claude_model", claudeModel.text.toString().trim().ifEmpty { "claude-sonnet-4-5" })
                    putString("openai_key", openaiKey.text.toString().trim())
                    putString("openai_model", openaiModel.text.toString().trim().ifEmpty { "gpt-4o" })
                    putString("grok_key", grokKey.text.toString().trim())
                    putString("grok_model", grokModel.text.toString().trim().ifEmpty { "grok-4" })
                    putString("deepseek_key", deepseekKey.text.toString().trim())
                    putString("deepseek_model", deepseekModel.text.toString().trim().ifEmpty { "deepseek-chat" })
                    putString("mistral_key", mistralKey.text.toString().trim())
                    putString("mistral_model", mistralModel.text.toString().trim().ifEmpty { "pixtral-large-latest" })
                    putString("openrouter_key", openrouterKey.text.toString().trim())
                    putString("openrouter_model", openrouterModel.text.toString().trim().ifEmpty { "anthropic/claude-3.5-sonnet" })
                    putBoolean("listen_require_trigger", requireTriggerSwitch.isChecked)
                    putString("listen_trigger_words", triggerWordsField.text.toString().trim())
                    putInt("listen_autohide_sec", autoHideField.text.toString().trim().toIntOrNull()?.coerceIn(0, 600) ?: 60)
                    putString("listen_language", listenLanguageKeys[listenLanguageSpinner.selectedItemPosition])
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
            topMargin = dp(28)
            bottomMargin = dp(20)
        })

        fun styleAllFields(v: View) {
            if (v is EditText) Ui.styleField(this, v)
            if (v is ViewGroup) for (i in 0 until v.childCount) styleAllFields(v.getChildAt(i))
        }
        styleAllFields(layout)
    }
}
