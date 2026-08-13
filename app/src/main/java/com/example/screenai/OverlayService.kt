package com.example.screenai

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.abs

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var mediaProjection: MediaProjection
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    private lateinit var imageReader: ImageReader
    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var btn: Button

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        prefs = getSharedPreferences("screenai_prefs", Context.MODE_PRIVATE)
        startForeground(1, buildNotification())

        val resultCode = intent!!.getIntExtra("resultCode", -1)
        val data = intent.getParcelableExtra<Intent>("data")!!
        val pm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = pm.getMediaProjection(resultCode, data)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        showButton()
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val channelId = "screenai"
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(channelId, "ScreenAI", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        return Notification.Builder(this, channelId)
            .setContentTitle("ScreenAI активен")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }

    private fun showButton() {
        val label = prefs.getString("button_label", "AI") ?: "AI"

        btn = Button(this).apply {
            text = label
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 20
        params.y = 200

        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var isDrag = false

        btn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    isDrag = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) isDrag = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(btn, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDrag) captureAndSend()
                    true
                }
                else -> false
            }
        }

        windowManager.addView(btn, params)
    }

    private fun captureAndSend() {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val w = metrics.widthPixels
        val h = metrics.heightPixels
        val dpi = metrics.densityDpi

        imageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenAI", w, h, dpi,
            android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, null
        )

        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            val bitmap = imageToBitmap(image, w, h)
            image.close()
            virtualDisplay?.release()
            scope.launch { sendToAI(bitmap) }
        }, null)
    }

    private fun imageToBitmap(image: android.media.Image, w: Int, h: Int): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * w
        val bitmap = Bitmap.createBitmap(w + rowPadding / pixelStride, h, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        return Bitmap.createBitmap(bitmap, 0, 0, w, h)
    }

    private fun sendToAI(bitmap: Bitmap) {
        val provider = prefs.getString("provider", "Gemini") ?: "Gemini"
        val prompt = prefs.getString("prompt", "Опиши что на экране.") ?: "Опиши что на экране."

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
        val b64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)

        showResult("⏳ Спрашиваю $provider...")

        val req: Request = when (provider) {
            "Claude" -> buildClaudeRequest(b64, prompt)
            "OpenAI" -> buildOpenAIRequest(b64, prompt)
            else -> buildGeminiRequest(b64, prompt)
        }

        val client = OkHttpClient()
        client.newCall(req).execute().use { resp ->
            val respBody = resp.body?.string() ?: "{}"
            val text = try {
                when (provider) {
                    "Claude" -> JSONObject(respBody).getJSONArray("content").getJSONObject(0).getString("text")
                    "OpenAI" -> JSONObject(respBody).getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").getString("content")
                    else -> JSONObject(respBody).getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                }
            } catch (e: Exception) { "Ошибка ($provider): $respBody" }

            showResult(text)
        }
    }

    private fun buildGeminiRequest(b64: String, prompt: String): Request {
        val key = prefs.getString("gemini_key", "") ?: ""
        val json = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray()
                    .put(JSONObject().put("text", prompt))
                    .put(JSONObject().put("inline_data", JSONObject()
                        .put("mime_type", "image/png").put("data", b64))))
            }))
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        return Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$key")
            .post(body).build()
    }

    private fun buildClaudeRequest(b64: String, prompt: String): Request {
        val key = prefs.getString("claude_key", "") ?: ""
        val json = JSONObject().apply {
            put("model", "claude-sonnet-4-5")
            put("max_tokens", 1024)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray()
                    .put(JSONObject().apply {
                        put("type", "image")
                        put("source", JSONObject().apply {
                            put("type", "base64")
                            put("media_type", "image/png")
                            put("data", b64)
                        })
                    })
                    .put(JSONObject().apply {
                        put("type", "text")
                        put("text", prompt)
                    }))
            }))
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        return Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", key)
            .addHeader("anthropic-version", "2023-06-01")
            .post(body).build()
    }

    private fun buildOpenAIRequest(b64: String, prompt: String): Request {
        val key = prefs.getString("openai_key", "") ?: ""
        val json = JSONObject().apply {
            put("model", "gpt-4o")
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray()
                    .put(JSONObject().apply { put("type", "text"); put("text", prompt) })
                    .put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().put("url", "data:image/png;base64,$b64"))
                    }))
            }))
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        return Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $key")
            .post(body).build()
    }

    private fun showResult(text: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this@OverlayService, text, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        virtualDisplay?.release()
        mediaProjection.stop()
    }
}
