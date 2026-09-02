package com.example.screenai

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*

class KeyActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Если доступ уже валиден — сразу в MainActivity
        if (KeyManager.isAccessValid(this)) {
            goMain()
            return
        }

        val dp = { n: Int -> (n * resources.displayMetrics.density).toInt() }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0D0D0D"))
            setPadding(dp(32), 0, dp(32), 0)
        }

        // Карточка
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1A1A"))
                cornerRadius = dp(20).toFloat()
                setStroke(1, Color.parseColor("#2A2A2A"))
            }
            setPadding(dp(28), dp(36), dp(28), dp(36))
        }

        // Иконка
        val logo = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
        }
        card.addView(logo, LinearLayout.LayoutParams(dp(72), dp(72)).apply {
            bottomMargin = dp(16)
            gravity = Gravity.CENTER_HORIZONTAL
        })

        val title = TextView(this).apply {
            text = "ScreenAI"
            textSize = 24f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        card.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(6) })

        val subtitle = TextView(this).apply {
            text = "Введите ключ доступа"
            textSize = 14f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
        }
        card.addView(subtitle, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(28) })

        // Поле ввода
        val input = EditText(this).apply {
            hint = "XXXXXXXXXXXX"
            setHintTextColor(Color.parseColor("#444444"))
            setTextColor(Color.WHITE)
            textSize = 18f
            letterSpacing = 0.15f
            gravity = Gravity.CENTER
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_DONE
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#111111"))
                cornerRadius = dp(12).toFloat()
                setStroke(1, Color.parseColor("#333333"))
            }
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        card.addView(input, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) })

        // Сообщение об ошибке
        val errorView = TextView(this).apply {
            text = ""
            textSize = 13f
            setTextColor(Color.parseColor("#FF5252"))
            gravity = Gravity.CENTER
            visibility = android.view.View.GONE
        }
        card.addView(errorView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) })

        // Кнопка
        val btn = Button(this).apply {
            text = "Войти"
            isAllCaps = false
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            stateListAnimator = null
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#6C63FF"))
                cornerRadius = dp(12).toFloat()
            }
            setPadding(dp(24), dp(14), dp(24), dp(14))
        }
        card.addView(btn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        root.addView(card, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        setContentView(root)

        fun tryKey() {
            val text = input.text.toString().trim()
            if (text.isEmpty()) return
            val seconds = KeyManager.validate(this, text)
            if (seconds == null) {
                // Неверный или уже использованный
                errorView.text = "Ключ недействителен или уже использован"
                errorView.visibility = android.view.View.VISIBLE
                input.setBackgroundDrawable(GradientDrawable().apply {
                    setColor(Color.parseColor("#111111"))
                    cornerRadius = dp(12).toFloat()
                    setStroke(1, Color.parseColor("#FF5252"))
                })
            } else {
                KeyManager.activate(this, seconds)
                val label = KeyManager.periodLabel(seconds)
                Toast.makeText(this, "✓ Доступ открыт на $label", Toast.LENGTH_SHORT).show()
                goMain()
            }
        }

        btn.setOnClickListener { tryKey() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { tryKey(); true } else false
        }
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
