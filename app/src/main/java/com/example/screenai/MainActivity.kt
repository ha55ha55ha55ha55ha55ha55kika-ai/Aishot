package com.example.screenai

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private val projectionManager by lazy {
        getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    // Результат разрешения на запись экрана, полученный на шаге 1, ждёт запуска
    // сервиса, пока (если нужно) не будет получено разрешение на камеру шагом 2.
    // Это разделение на два ПОСЛЕДОВАТЕЛЬНЫХ, а не вложенных друг в друга системных
    // диалога — чинит баг на MIUI/Xiaomi, где второй диалог, запущенный сразу
    // изнутри колбэка первого, закрывался сам собой без ответа пользователя.
    private var pendingResultCode: Int = -1
    private var pendingData: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = Ui.screenBackground()
            setPadding(Ui.dp(this@MainActivity, 32), 0, Ui.dp(this@MainActivity, 32), 0)
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = Ui.cardBackground(this@MainActivity)
            val pad = Ui.dp(this@MainActivity, 32)
            setPadding(pad, Ui.dp(this@MainActivity, 40), pad, Ui.dp(this@MainActivity, 40))
        }

        val logoSizePx = Ui.dp(this, 96)
        val logo = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
        }
        card.addView(logo, LinearLayout.LayoutParams(logoSizePx, logoSizePx).apply {
            bottomMargin = Ui.dp(this@MainActivity, 18)
        })

        val title = TextView(this).apply {
            text = "ScreenAI"
            textSize = 26f
            setTextColor(Color.parseColor(Ui.TEXT_PRIMARY))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        card.addView(title, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = Ui.dp(this@MainActivity, 6)
        })

        val subtitle = TextView(this).apply {
            text = "Спроси AI о том, что на экране"
            textSize = 14f
            setTextColor(Color.parseColor(Ui.TEXT_SECONDARY))
            gravity = Gravity.CENTER
        }
        card.addView(subtitle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = Ui.dp(this@MainActivity, 32)
        })

        val startBtn = Button(this).apply {
            text = "Запустить плавающую кнопку"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = Ui.accentButtonBackground(this@MainActivity)
            setPadding(Ui.dp(this@MainActivity, 24), Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 24), Ui.dp(this@MainActivity, 16))
            stateListAnimator = null
            setOnClickListener { checkOverlayAndStart() }
        }
        val settingsBtn = Button(this).apply {
            text = "Настройки AI"
            isAllCaps = false
            setTextColor(Color.parseColor(Ui.ACCENT_A))
            background = Ui.cardBackground(this@MainActivity)
            setPadding(Ui.dp(this@MainActivity, 24), Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 24), Ui.dp(this@MainActivity, 16))
            stateListAnimator = null
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        }

        card.addView(startBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        card.addView(settingsBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Ui.dp(this@MainActivity, 14)
        })

        root.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        setContentView(root)
    }

    private fun checkOverlayAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"))
            startActivityForResult(i, 1)
            return
        }
        proceedAfterOverlay()
    }

    private fun currentMode(): String {
        val prefs = getSharedPreferences("screenai_prefs", Context.MODE_PRIVATE)
        return prefs.getString("capture_mode", "screenshot") ?: "screenshot"
    }

    // Шаг 1 из 2: если режим требует скриншотов (screenshot/mix) — запрашиваем
    // системное разрешение на запись экрана. Если нужна только камера — сразу
    // переходим к шагу 2.
    private fun proceedAfterOverlay() {
        val mode = currentMode()
        val needsScreenshot = mode == "screenshot" || mode == "mix"
        if (needsScreenshot) {
            startActivityForResult(projectionManager.createScreenCaptureIntent(), 2)
        } else {
            requestCameraThenStart()
        }
    }

    // Шаг 2 из 2: запрашиваем разрешение на камеру, если оно нужно и ещё не выдано.
    // Этот диалог НИКОГДА не вызывается изнутри колбэка предыдущего системного
    // диалога напрямую — только после отдельного клика/события, что и чинит баг.
    private fun requestCameraThenStart() {
        val mode = currentMode()
        val needsCamera = mode == "camera" || mode == "mix"
        if (needsCamera && ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), 3)
            return
        }
        startOverlayService(pendingResultCode, pendingData)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 3) {
            startOverlayService(pendingResultCode, pendingData)
        }
    }

    private fun startOverlayService(resultCode: Int, data: Intent?) {
        val svc = Intent(this, OverlayService::class.java).apply {
            putExtra("resultCode", resultCode)
            if (data != null) putExtra("data", data)
        }
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc) else startService(svc)
        finish()
    }

    override fun onActivityResult(reqCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(reqCode, resultCode, data)
        if (reqCode == 1 && Settings.canDrawOverlays(this)) {
            proceedAfterOverlay()
            return
        }
        if (reqCode == 2) {
            if (resultCode == RESULT_OK && data != null) {
                pendingResultCode = resultCode
                pendingData = data
                requestCameraThenStart()
            }
            // Если пользователь отказал в записи экрана — просто ничего не делаем,
            // сервис не запускается (без обрезанного второго системного диалога).
        }
    }
}
