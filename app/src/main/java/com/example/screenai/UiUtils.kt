package com.example.screenai

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.EditText

/** Общая цветовая палитра и оформление, чтобы MainActivity и SettingsActivity
 *  выглядели как единое, аккуратно сделанное приложение. */
object Ui {
    const val BG_TOP = "#0F172A"
    const val BG_BOTTOM = "#1E293B"
    const val CARD = "#1E2A3F"
    const val ACCENT_A = "#38BDF8" // голубой
    const val ACCENT_B = "#6366F1" // индиго
    const val TEXT_PRIMARY = "#F1F5F9"
    const val TEXT_SECONDARY = "#94A3B8"

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    /** Фон экрана: тёмный вертикальный градиент. */
    fun screenBackground(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.parseColor(BG_TOP), Color.parseColor(BG_BOTTOM))
    )

    /** Акцентная кнопка-пилюля с градиентом. */
    fun accentButtonBackground(context: Context): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(Color.parseColor(ACCENT_A), Color.parseColor(ACCENT_B))
    ).apply {
        cornerRadius = dp(context, 28).toFloat()
    }

    /** Полупрозрачная "карточка" для группировки контента. */
    fun cardBackground(context: Context): GradientDrawable = GradientDrawable().apply {
        setColor(Color.parseColor(CARD))
        cornerRadius = dp(context, 20).toFloat()
        setStroke(dp(context, 1), Color.parseColor("#2E3F58"))
    }

    /** Скруглённый фон для полей ввода. */
    fun fieldBackground(context: Context): GradientDrawable = GradientDrawable().apply {
        setColor(Color.parseColor("#15202E"))
        cornerRadius = dp(context, 14).toFloat()
        setStroke(dp(context, 1), Color.parseColor("#2E3F58"))
    }

    fun styleField(context: Context, field: EditText) {
        field.setTextColor(Color.parseColor(TEXT_PRIMARY))
        field.setHintTextColor(Color.parseColor(TEXT_SECONDARY))
        field.background = fieldBackground(context)
        val pad = dp(context, 14)
        field.setPadding(pad, pad, pad, pad)
    }
}
