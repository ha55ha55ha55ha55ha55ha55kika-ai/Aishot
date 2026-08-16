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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(60, 60, 60, 60)
        }

        val logoSizePx = (120 * resources.displayMetrics.density).toInt()
        val logo = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
        }
        layout.addView(logo, LinearLayout.LayoutParams(logoSizePx, logoSizePx).apply {
            bottomMargin = (16 * resources.displayMetrics.density).toInt()
        })

        val title = TextView(this).apply {
            text = "ScreenAI"
            textSize = 22f
            gravity = Gravity.CENTER
        }
        layout.addView(title, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = (30 * resources.displayMetrics.density).toInt()
        })

        val startBtn = Button(this).apply {
            text = "Запустить плавающую кнопку"
            setOnClickListener { checkOverlayAndStart() }
        }
        val settingsBtn = Button(this).apply {
            text = "Настройки AI"
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        }

        layout.addView(startBtn)
        layout.addView(settingsBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = 30
        })

        setContentView(layout)
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

    private fun proceedAfterOverlay() {
        val prefs = getSharedPreferences("screenai_prefs", Context.MODE_PRIVATE)
        val mode = prefs.getString("capture_mode", "screenshot") ?: "screenshot"
        val needsCamera = mode == "camera" || mode == "mix"
        val needsScreenshot = mode == "screenshot" || mode == "mix"

        if (needsCamera && ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), 3)
            return
        }

        if (needsScreenshot) {
            startActivityForResult(projectionManager.createScreenCaptureIntent(), 2)
        } else {
            // Режим "только камера" — запись экрана вообще не нужна, запускаем сервис сразу.
            startOverlayService(resultCode = -1, data = null)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 3) {
            proceedAfterOverlay()
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
        if (reqCode == 2 && resultCode == RESULT_OK && data != null) {
            startOverlayService(resultCode, data)
        }
    }
}
