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

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var mediaProjection: MediaProjection
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    private lateinit var imageReader: ImageReader
    private val scope = CoroutineScope(Dispatchers.IO)
    private val GEMINI_API_KEY = "AQ.Ab8RN6JKkwMUwdrnTyfmJNOP-dg3rsMZSOOhBLeNp1gPJ_Zzug"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
        val btn = Button(this).apply {
            text = "AI"
            setOnClickListener { captureAndSend() }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.END
        params.x = 20
        params.y = 200
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
            scope.launch { sendToGemini(bitmap) }
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

    private fun sendToGemini(bitmap: Bitmap) {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
        val b64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)

        val json = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray()
                    .put(JSONObject().put("text", "Опиши что на экране. Если ошибка - предложи решение."))
                    .put(JSONObject().put("inline_data", JSONObject()
                        .put("mime_type", "image/png").put("data", b64))))
            }))
        }

        val client = OkHttpClient()
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$GEMINI_API_KEY")
            .post(body).build()

        client.newCall(req).execute().use { resp ->
            val respBody = resp.body?.string() ?: "{}"
            val text = try {
                JSONObject(respBody).getJSONArray("candidates")
                    .getJSONObject(0).getJSONObject("content")
                    .getJSONArray("parts").getJSONObject(0).getString("text")
            } catch (e: Exception) { "Ошибка: ${e.message}" }

            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this@OverlayService, text, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        virtualDisplay?.release()
        mediaProjection.stop()
    }
}
