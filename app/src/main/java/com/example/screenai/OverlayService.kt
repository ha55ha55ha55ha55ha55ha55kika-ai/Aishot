package com.example.screenai

import android.app.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Base64
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.EditText
import android.widget.FrameLayout
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
import kotlin.math.roundToInt

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    private lateinit var imageReader: ImageReader
    private var cameraDevice: android.hardware.camera2.CameraDevice? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var btn: View
    private val mainHandler = Handler(Looper.getMainLooper())

    // Текущая "живая" карточка с ответом ИИ, которая обновляется новыми ответами,
    // пока пользователь не закрепил её (📌) или не закрыл (✕).
    private var activePanelView: View? = null
    private var activePanelTextView: TextView? = null
    private var activePanelPinned: Boolean = false

    // Обязателен на Android 14+ (API 34): без зарегистрированного колбэка система
    // выбрасывает SecurityException при первой же попытке createVirtualDisplay(),
    // даже если mediaProjection != null. Также ловит момент, когда пользователь
    // сам остановил трансляцию из системной плашки — тогда токен больше не
    // рабочий, и это нужно отразить в состоянии сервиса, а не притворяться,
    // что всё ок.

    // ---- Режим Live: трансляция экрана в реальном времени ----
    @Volatile private var liveMode = false
    private var livePanelView: View? = null
    private var liveAnswerTextView: TextView? = null
    private var liveTaskField: EditText? = null
    private var liveHelpField: EditText? = null
    private val liveHandler = Handler(Looper.getMainLooper())
    private val liveIntervalMs = 4000L   // интервал между кадрами в ms
    private val liveRunnable: Runnable = object : Runnable {
        override fun run() {
            if (liveMode) {
                captureArmed = true
                liveHandler.postDelayed(this, liveIntervalMs)
            }
        }
    }

    // ---- Режим "Слушатель": распознавание речи и голосовые вопросы ----
    @Volatile private var listenMode = false
    private var speechRecognizer: SpeechRecognizer? = null
    // true после того как услышано слово-триггер без вопроса следом — значит
    // следующая распознанная фраза целиком считается вопросом.
    @Volatile private var awaitingQuestion = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            virtualDisplay?.release()
            virtualDisplay = null
            mediaProjection = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        prefs = getSharedPreferences("screenai_prefs", Context.MODE_PRIVATE)
        try {
            startForeground(1, buildNotification())
        } catch (e: Exception) {
            // На части прошивок (MIUI/HyperOS) может быть заблокирован запуск
            // foreground-сервиса с типом mediaProjection, если не включены доп.
            // разрешения прошивки. Раньше это крашило процесс без объяснений.
            android.widget.Toast.makeText(
                this,
                "Системе не разрешено запустить сервис захвата экрана. " +
                    "На MIUI/HyperOS: Настройки → Приложения → ScreenAI → " +
                    "Доп. разрешения → включите Автозапуск и Показ окон в фоне.",
                android.widget.Toast.LENGTH_LONG
            ).show()
            stopSelf()
            return START_NOT_STICKY
        }

        // intent может быть null, если систему перезапускает сервис сама
        // (START_STICKY после убийства процесса) — это нормальная ситуация,
        // а не повод падать. Старый токен MediaProjection в любом случае
        // невосстановим после смерти процесса, поэтому просто не трогаем
        // его здесь и не крашимся.
        if (intent != null) {
            val resultCode = intent.getIntExtra("resultCode", -1)
            val data = intent.getParcelableExtra<Intent>("data")
            if (resultCode == Activity.RESULT_OK && data != null) {
                val pm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjection?.unregisterCallback(projectionCallback)
                mediaProjection = pm.getMediaProjection(resultCode, data)
                mediaProjection?.registerCallback(projectionCallback, mainHandler)
            }
        }

        if (!::windowManager.isInitialized) {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            showButton()
        }
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

    private fun overlayWindowType() =
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

    private fun showButton() {
        val label = prefs.getString("button_label", "AI") ?: "AI"
        val sizeDp = prefs.getInt("button_size_dp", 56).coerceIn(36, 120)
        val color = prefs.getInt("button_color", Color.parseColor("#2196F3"))
        val sizePx = dpToPx(sizeDp)
        val mode = prefs.getString("capture_mode", "screenshot") ?: "screenshot"

        var rootView: View
        var isShortLabel: Boolean
        var widthPx: Int

        if (mode == "mix") {
            // Режим микс: два отдельных подписанных кружка рядом — один делает
            // скриншот, другой снимает с фронтальной камеры.
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            fun miniButton(text: String): TextView = TextView(this).apply {
                this.text = text
                setTextColor(Color.WHITE)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                textSize = (sizeDp * 0.20f).coerceIn(8f, 14f)
                gravity = Gravity.CENTER
                background = buildButtonDrawable(color, sizePx)
            }
            val labelScreenshot = prefs.getString("mix_label_screenshot", "Скрин") ?: "Скрин"
            val labelCamera = prefs.getString("mix_label_camera", "Камера") ?: "Камера"
            val screenshotBtn = miniButton("📷\n$labelScreenshot")
            val cameraBtn = miniButton("🤳\n$labelCamera")
            container.addView(screenshotBtn, LinearLayout.LayoutParams(sizePx, sizePx))
            container.addView(cameraBtn, LinearLayout.LayoutParams(sizePx, sizePx).apply { marginStart = dpToPx(6) })
            rootView = container
            isShortLabel = false
            widthPx = sizePx * 2 + dpToPx(6)
        } else {
            val singleBtn = Button(this).apply {
                text = label
                setTextColor(Color.WHITE)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                textSize = (sizeDp * 0.32f).coerceIn(11f, 26f)
                isAllCaps = false
                background = buildButtonDrawable(color, sizePx)
                elevation = dpToPx(6).toFloat()
                stateListAnimator = null
            }
            rootView = singleBtn
            isShortLabel = label.length <= 2
            widthPx = sizePx
        }
        btn = rootView

        params = WindowManager.LayoutParams(
            if (isShortLabel) widthPx else WindowManager.LayoutParams.WRAP_CONTENT,
            sizePx,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        if (!isShortLabel && mode != "mix" && btn is Button) {
            (btn as Button).setPadding(sizePx / 3, 0, sizePx / 3, 0)
        }
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 20
        params.y = 200

        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var isDrag = false
        var isLongPress = false
        var isHidden = false
        var upX = 0f

        // Долгое нажатие: переключает видимость кнопки(-ок). Становится прозрачной
        // (alpha = 0), но остаётся на том же месте и по-прежнему кликабельна.
        val longPressRunnable = Runnable {
            isLongPress = true
            isHidden = !isHidden
            btn.alpha = if (isHidden) 0f else 1f
        }

        btn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    isDrag = false
                    isLongPress = false
                    mainHandler.postDelayed(longPressRunnable, 500)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        isDrag = true
                        mainHandler.removeCallbacks(longPressRunnable)
                    }
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(btn, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    upX = event.x
                    if (!isDrag && !isLongPress) {
                        when (mode) {
                            "camera" -> captureFromCamera()
                            "live" -> {
                                if (liveMode) stopLiveMode() else startLiveMode()
                            }
                            "listen" -> {
                                if (listenMode) stopListenMode() else startListenMode()
                            }
                            "mix" -> {
                                // Левая половина контейнера — скриншот, правая — камера.
                                if (upX < (btn as View).width / 2f) captureAndSend() else captureFromCamera()
                            }
                            else -> captureAndSend()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    true
                }
                else -> false
            }
        }

        windowManager.addView(btn, params)
    }

    private var capturedW = 0
    private var capturedH = 0
    // VirtualDisplay зеркалит экран непрерывно (AUTO_MIRROR), поэтому кадры в
    // ImageReader сыпятся постоянно, а не только по нажатию кнопки. Этот флаг
    // "взводится" по нажатию и говорит слушателю: следующий пришедший кадр —
    // это и есть нужный скриншот, обработать и отправить в ИИ. Остальные кадры
    // просто закрываются без дальнейших действий.
    @Volatile private var captureArmed = false

    private fun captureAndSend() {
        val projection = mediaProjection
        if (projection == null) {
            showResult("Скриншот недоступен: разрешение на запись экрана не выдано")
            return
        }

        // VirtualDisplay создаём ОДИН раз и переиспользуем для всех последующих
        // скриншотов — пересоздание его на каждое нажатие (create/release по кругу)
        // на части прошивок (в частности HyperOS) приводит к тому, что система
        // считает разрешение на запись экрана "исчерпанным" уже после первого
        // кадра. Держим его живым до onDestroy().
        if (virtualDisplay == null) {
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)
            capturedW = metrics.widthPixels
            capturedH = metrics.heightPixels
            val dpi = metrics.densityDpi
            try {
                imageReader = ImageReader.newInstance(capturedW, capturedH, PixelFormat.RGBA_8888, 2)
                virtualDisplay = projection.createVirtualDisplay(
                    "ScreenAI", capturedW, capturedH, dpi,
                    android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.surface, null, null
                )
                imageReader.setOnImageAvailableListener({ reader ->
                    val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    if (!captureArmed) {
                        image.close()
                        return@setOnImageAvailableListener
                    }
                    captureArmed = false
                    val bitmap = imageToBitmap(image, capturedW, capturedH)
                    image.close()
                    if (liveMode) {
                        scope.launch { sendToAILive(bitmap) }
                    } else {
                        scope.launch { sendToAI(bitmap) }
                    }
                }, mainHandler)
            } catch (e: Exception) {
                virtualDisplay = null
                showResult("Не удалось начать съёмку экрана: ${e.message}")
                return
            }
        }

        captureArmed = true
    }


    // ---- Live Mode методы ----

    private fun startLiveMode() {
        if (mediaProjection == null) {
            showResult("Live: разрешение на запись экрана не выдано")
            return
        }
        liveMode = true
        showLivePanel()
        // Убеждаемся что VirtualDisplay создан (то же что в captureAndSend)
        captureAndSend()
        // После первого кадра запускаем цикл
        liveHandler.postDelayed(liveRunnable, liveIntervalMs)
        updateLiveButtonLook()
    }

    private fun stopLiveMode() {
        liveMode = false
        liveHandler.removeCallbacks(liveRunnable)
        captureArmed = false
        mainHandler.post {
            livePanelView?.let { windowManager.removeView(it) }
            livePanelView = null
            liveAnswerTextView = null
            liveTaskField = null
            liveHelpField = null
        }
        updateLiveButtonLook()
    }

    private fun updateLiveButtonLook() {
        mainHandler.post {
            val b = btn
            if (b is Button) {
                b.text = if (liveMode) "⏹ Live" else (prefs.getString("button_label", "AI") ?: "AI")
            }
        }
    }

    private fun showLivePanel() {
        mainHandler.post {
            // Закрываем старую панель если есть
            livePanelView?.let { windowManager.removeView(it) }

            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)

            val panelParams = WindowManager.LayoutParams(
                (metrics.widthPixels * 0.92f).roundToInt(),
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayWindowType(),
                // FLAG_NOT_FOCUSABLE снимаем чтобы EditText получал фокус
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            )
            panelParams.gravity = Gravity.TOP or Gravity.START
            panelParams.x = (metrics.widthPixels * 0.04f).roundToInt()
            panelParams.y = (metrics.heightPixels * 0.12f).roundToInt()

            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#F0111111"))
                cornerRadius = dpToPx(16).toFloat()
            }

            val root = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = bg
                setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(14))
            }

            // Заголовок
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val titleView = TextView(this).apply {
                text = "🔴 Live AI"
                setTextColor(Color.parseColor("#FF5252"))
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val stopBtn = Button(this).apply {
                text = "✕ Стоп"
                textSize = 12f
                setTextColor(Color.WHITE)
                isAllCaps = false
                minWidth = 0
                minimumWidth = 0
                setPadding(dpToPx(12), 0, dpToPx(12), 0)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#CC333333"))
                    cornerRadius = dpToPx(8).toFloat()
                }
                stateListAnimator = null
                setOnClickListener { stopLiveMode() }
            }
            header.addView(titleView)
            header.addView(stopBtn)
            root.addView(header)

            // Разделитель
            root.addView(View(this).apply {
                setBackgroundColor(Color.parseColor("#33FFFFFF"))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
                topMargin = dpToPx(8); bottomMargin = dpToPx(8)
            })

            fun fieldLabel(text: String) = TextView(this).apply {
                this.text = text
                setTextColor(Color.parseColor("#AAAAAA"))
                textSize = 11f
                setPadding(0, dpToPx(6), 0, dpToPx(3))
            }

            fun inputField(hint: String, lines: Int = 2) = EditText(this).apply {
                this.hint = hint
                setHintTextColor(Color.parseColor("#555555"))
                setTextColor(Color.WHITE)
                textSize = 14f
                minLines = lines
                maxLines = lines + 1
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1AFFFFFF"))
                    cornerRadius = dpToPx(8).toFloat()
                    setStroke(1, Color.parseColor("#33FFFFFF"))
                }
                setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
            }

            // Поле 1: задача
            root.addView(fieldLabel("1. Задача — что нужно сделать?"))
            val taskField = inputField("Например: найди как установить nginx на Ubuntu", 2)
            root.addView(taskField)

            // Поле 2: помощь / уточнение
            root.addView(fieldLabel("2. Помощь понять — что ты уже знаешь / затруднение"))
            val helpField = inputField("Например: уже пробовал apt install, не работает", 2)
            root.addView(helpField)

            // Разделитель перед ответом
            root.addView(View(this).apply {
                setBackgroundColor(Color.parseColor("#22FFFFFF"))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
                topMargin = dpToPx(10); bottomMargin = dpToPx(4)
            })

            // Поле 3: ответ ИИ
            root.addView(fieldLabel("3. ИИ смотрит экран и делает:"))
            val answerView = TextView(this).apply {
                text = "⏳ Жду первый кадр..."
                setTextColor(Color.parseColor("#DDDDDD"))
                textSize = 13f
                setTextIsSelectable(true)
                setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1A00FF88"))
                    cornerRadius = dpToPx(8).toFloat()
                    setStroke(1, Color.parseColor("#2200FF88"))
                }
            }
            val answerScroll = MaxHeightScrollView(this, (metrics.heightPixels * 0.3f).roundToInt()).apply {
                addView(answerView)
            }
            root.addView(answerScroll)

            // Перетаскивание за заголовок
            var initialX = 0; var initialY = 0
            var touchX = 0f; var touchY = 0f
            header.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = panelParams.x; initialY = panelParams.y
                        touchX = event.rawX; touchY = event.rawY; true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        panelParams.x = initialX + (event.rawX - touchX).toInt()
                        panelParams.y = initialY + (event.rawY - touchY).toInt()
                        windowManager.updateViewLayout(root, panelParams); true
                    }
                    else -> false
                }
            }

            windowManager.addView(root, panelParams)
            livePanelView = root
            liveAnswerTextView = answerView
            liveTaskField = taskField
            liveHelpField = helpField
        }
    }

    private fun sendToAILive(bitmap: Bitmap) {
        // Только vision-провайдеры
        val provider = prefs.getString("provider", "Gemini") ?: "Gemini"
        val visionProviders = setOf("Gemini", "Claude", "OpenAI", "Grok", "Mistral", "OpenRouter")
        if (provider !in visionProviders) {
            mainHandler.post {
                liveAnswerTextView?.text = "⚠️ $provider не поддерживает vision. Выберите: Gemini, Claude, OpenAI, Grok, Mistral или OpenRouter."
            }
            return
        }

        val task = liveTaskField?.text?.toString()?.trim() ?: ""
        val help = liveHelpField?.text?.toString()?.trim() ?: ""

        val taskPart = if (task.isNotEmpty()) "\nЗАДАЧА: $task" else ""
        val helpPart = if (help.isNotEmpty()) "\nКОНТЕКСТ/ЗАТРУДНЕНИЕ: $help" else ""
        val livePrompt = "Ты ИИ-помощник. Ты видишь экран пользователя в реальном времени.$taskPart$helpPart\n\nПосмотри на экран и кратко опиши что сейчас происходит. Если задача задана — скажи какой следующий шаг нужно сделать или что ты видишь важного для этой задачи. Отвечай четко и по делу, 2-4 предложения максимум."

        mainHandler.post { liveAnswerTextView?.text = "👁 Анализирую..." }

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        val b64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)

        val req: Request = when (provider) {
            "Claude" -> buildClaudeRequest(b64, livePrompt)
            "OpenAI" -> buildOpenAIRequest(b64, livePrompt)
            "Grok" -> buildGrokRequest(b64, livePrompt)
            "Mistral" -> buildMistralRequest(b64, livePrompt)
            "OpenRouter" -> buildOpenRouterRequest(b64, livePrompt)
            else -> buildGeminiRequest(b64, livePrompt)
        }

        val client = OkHttpClient()
        try {
            client.newCall(req).execute().use { resp ->
                val respBody = resp.body?.string() ?: "{}"
                val text = try {
                    when (provider) {
                        "Claude" -> JSONObject(respBody).getJSONArray("content").getJSONObject(0).getString("text")
                        "Gemini" -> JSONObject(respBody).getJSONArray("candidates").getJSONObject(0)
                            .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                        else -> JSONObject(respBody).getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("message").getString("content")
                    }
                } catch (e: Exception) {
                    "Ошибка ($provider): $respBody"
                }
                mainHandler.post { liveAnswerTextView?.text = text }
            }
        } catch (e: Exception) {
            mainHandler.post { liveAnswerTextView?.text = "Ошибка сети ($provider): ${e.message}" }
        }
    }

    // ---- конец Live Mode методов ----

    // Снимок с фронтальной камеры вместо экрана. Использует тот же sendToAI(bitmap),
    // так что дальнейшая отправка в ИИ не отличается от режима скриншота.
    // ---- Режим "Слушатель" методы ----

    private fun startListenMode() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showResult("Распознавание речи недоступно на этом устройстве")
            return
        }
        listenMode = true
        awaitingQuestion = false
        updateListenButtonLook()
        Toast.makeText(this, "🎤 Слушатель включён", Toast.LENGTH_SHORT).show()
        startListeningCycle()
    }

    private fun stopListenMode() {
        listenMode = false
        awaitingQuestion = false
        mainHandler.post {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
        updateListenButtonLook()
        Toast.makeText(this, "🎤 Слушатель выключен", Toast.LENGTH_SHORT).show()
    }

    private fun updateListenButtonLook() {
        mainHandler.post {
            val b = btn
            if (b is Button) {
                b.text = if (listenMode) "⏹🎤" else (prefs.getString("button_label", "AI") ?: "AI")
            }
        }
    }

    // Один цикл прослушивания: слушает одну фразу (до паузы в речи), обрабатывает
    // её и сразу запускает следующий цикл, пока listenMode не выключен вручную.
    // SpeechRecognizer в Android умеет слушать только одну "сессию" за раз — это
    // штатный способ сделать из него непрерывное прослушивание.
    private fun startListeningCycle() {
        if (!listenMode) return
        mainHandler.post {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer = recognizer
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, prefs.getString("listen_language", "ru-RU") ?: "ru-RU")
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500)
            }
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim().orEmpty()
                    try { recognizer.destroy() } catch (e: Exception) {}
                    speechRecognizer = null
                    if (text.isNotEmpty()) handleRecognizedSpeech(text)
                    if (listenMode) mainHandler.postDelayed({ startListeningCycle() }, 250)
                }
                override fun onError(error: Int) {
                    try { recognizer.destroy() } catch (e: Exception) {}
                    speechRecognizer = null
                    // ERROR_NO_MATCH / ERROR_SPEECH_TIMEOUT — обычная ситуация (тишина),
                    // просто слушаем дальше без задержки-паники.
                    val delay = if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) 200L else 1000L
                    if (listenMode) mainHandler.postDelayed({ startListeningCycle() }, delay)
                }
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            try {
                recognizer.startListening(intent)
            } catch (e: Exception) {
                speechRecognizer = null
                if (listenMode) mainHandler.postDelayed({ startListeningCycle() }, 1000)
            }
        }
    }

    private fun handleRecognizedSpeech(text: String) {
        val requireTrigger = prefs.getBoolean("listen_require_trigger", true)

        if (!requireTrigger) {
            // Без слова-триггера: слушает постоянно, но отвечает только на то,
            // что похоже на вопрос — обычные разговоры игнорируются.
            if (looksLikeQuestion(text)) askQuestion(text)
            return
        }

        val triggerWords = (prefs.getString("listen_trigger_words", "") ?: "")
            .split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (triggerWords.isEmpty()) return
        val lower = text.lowercase()

        if (awaitingQuestion) {
            awaitingQuestion = false
            askQuestion(text)
            return
        }

        val hit = triggerWords.firstOrNull { lower.contains(it) } ?: return
        val afterTrigger = lower.substringAfter(hit).trim(' ', ',', '.', '!', '?', ':', '-')
        if (afterTrigger.length > 2) {
            // Триггер и вопрос сказаны одной фразой: "Табаков, который час"
            askQuestion(afterTrigger)
        } else {
            // Услышали только имя — ждём вопрос следующей фразой
            awaitingQuestion = true
            showResult("🎤 Слушаю вопрос...")
        }
    }

    /** Простая эвристика "похоже ли это на вопрос" — по вопросительным словам в начале
     * фразы или вопросительному знаку (иногда распознаётся движком речи). */
    /** Простая эвристика "похоже ли это на вопрос" — по вопросительным словам (в начале
     * фразы или где-либо в ней) или вопросительному знаку (иногда распознаётся движком
     * речи). Поддерживает русский и украинский языки. */
    private fun looksLikeQuestion(text: String): Boolean {
        val lower = text.lowercase().trim()
        if (lower.endsWith("?")) return true

        // Слова, по которым фраза почти наверняка вопрос, даже если стоят не в начале
        // ("а где ключи", "ты знаешь, сколько это стоит").
        val questionWordsAnywhere = listOf(
            // Русский
            "что", "как", "почему", "отчего", "зачем", "когда", "где", "куда", "откуда",
            "кто", "кого", "кому", "кем", "чей", "чья", "чьё", "чьи",
            "сколько", "какой", "какая", "какое", "какие", "каков", "какова",
            "можно ли", "нужно ли", "правда ли", "верно ли", "не так ли",
            // Украинский
            "що", "як", "чому", "навіщо", "коли", "де", "куди", "звідки",
            "хто", "кого", "кому", "ким", "чий", "чия", "чиє", "чиї",
            "скільки", "який", "яка", "яке", "які", "чи можна", "чи потрібно", "чи правда"
        )
        if (questionWordsAnywhere.any { lower.contains(it) }) return true

        // Слово "чи" в начале фразы по-украински — как "ли" в вопросе ("чи прийдеш ти?")
        val leadingParticles = listOf("чи ", "ли ")
        return leadingParticles.any { lower.startsWith(it) }
    }

    private fun askQuestion(question: String) {
        val autoHideSec = prefs.getInt("listen_autohide_sec", 60)
        scope.launch { sendTextToAI(question, autoHideSec) }
    }

    private fun sendTextToAI(question: String, autoHideSec: Int) {
        val provider = prefs.getString("provider", "Gemini") ?: "Gemini"
        showResult("🎤 Вопрос: $question\n\n⏳ Спрашиваю $provider...")

        val req = buildTextRequest(provider, question)
        val client = OkHttpClient()
        try {
            client.newCall(req).execute().use { resp ->
                val respBody = resp.body?.string() ?: "{}"
                val text = try {
                    when (provider) {
                        "Claude" -> JSONObject(respBody).getJSONArray("content").getJSONObject(0).getString("text")
                        "Gemini" -> JSONObject(respBody).getJSONArray("candidates").getJSONObject(0)
                            .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                        else -> JSONObject(respBody).getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("message").getString("content")
                    }
                } catch (e: Exception) {
                    "Ошибка ($provider): $respBody"
                }
                showResult("🎤 Вопрос: $question\n\n$text", autoHideSec)
            }
        } catch (e: Exception) {
            showResult("Ошибка сети ($provider): ${e.message}", autoHideSec)
        }
    }

    /** Текстовые (без картинки) запросы к провайдерам — используются режимом "Слушатель". */
    private fun buildTextRequest(provider: String, prompt: String): Request = when (provider) {
        "Claude" -> buildClaudeTextRequest(prompt)
        "OpenAI" -> buildOpenAITextRequest(prompt)
        "Grok" -> buildGrokTextRequest(prompt)
        "DeepSeek" -> buildDeepSeekRequest(prompt)
        "Mistral" -> buildMistralTextRequest(prompt)
        "OpenRouter" -> buildOpenRouterTextRequest(prompt)
        else -> buildGeminiTextRequest(prompt)
    }

    private fun buildGeminiTextRequest(prompt: String): Request {
        val key = prefs.getString("gemini_key", "") ?: ""
        val model = prefs.getString("gemini_model", "gemini-3.6-flash") ?: "gemini-3.6-flash"
        val json = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            }))
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        return Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key")
            .post(body).build()
    }

    private fun buildClaudeTextRequest(prompt: String): Request {
        val key = prefs.getString("claude_key", "") ?: ""
        val model = prefs.getString("claude_model", "claude-sonnet-4-5") ?: "claude-sonnet-4-5"
        val json = JSONObject().apply {
            put("model", model)
            put("max_tokens", 1024)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user"); put("content", prompt)
            }))
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        return Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", key)
            .addHeader("anthropic-version", "2023-06-01")
            .post(body).build()
    }

    private fun buildOpenAITextRequest(prompt: String): Request {
        val key = prefs.getString("openai_key", "") ?: ""
        val model = prefs.getString("openai_model", "gpt-4o") ?: "gpt-4o"
        val json = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user"); put("content", prompt)
            }))
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        return Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $key")
            .post(body).build()
    }

    private fun buildGrokTextRequest(prompt: String): Request {
        val key = prefs.getString("grok_key", "") ?: ""
        val model = prefs.getString("grok_model", "grok-4") ?: "grok-4"
        val json = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user"); put("content", prompt)
            }))
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        return Request.Builder()
            .url("https://api.x.ai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $key")
            .post(body).build()
    }

    private fun buildMistralTextRequest(prompt: String): Request {
        val key = prefs.getString("mistral_key", "") ?: ""
        val model = prefs.getString("mistral_model", "pixtral-large-latest") ?: "pixtral-large-latest"
        val json = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user"); put("content", prompt)
            }))
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        return Request.Builder()
            .url("https://api.mistral.ai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $key")
            .post(body).build()
    }

    private fun buildOpenRouterTextRequest(prompt: String): Request {
        val key = prefs.getString("openrouter_key", "") ?: ""
        val model = prefs.getString("openrouter_model", "anthropic/claude-3.5-sonnet") ?: "anthropic/claude-3.5-sonnet"
        val json = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user"); put("content", prompt)
            }))
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        return Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .addHeader("Authorization", "Bearer $key")
            .addHeader("HTTP-Referer", "https://github.com/")
            .addHeader("X-Title", "ScreenAI")
            .post(body).build()
    }

    private fun captureFromCamera() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.CAMERA
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            showResult("Нет разрешения на камеру. Откройте приложение и разрешите доступ к камере.")
            return
        }
        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            var frontId: String? = null
            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                if (chars.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING) ==
                    android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT
                ) {
                    frontId = id
                    break
                }
            }
            if (frontId == null) {
                showResult("Фронтальная камера не найдена")
                return
            }
            val chars = cameraManager.getCameraCharacteristics(frontId)
            val map = chars.get(android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val sizes = map?.getOutputSizes(android.graphics.ImageFormat.JPEG)
            val size = sizes?.maxByOrNull { it.width.toLong() * it.height } ?: android.util.Size(1280, 960)
            val sensorOrientation = chars.get(android.hardware.camera2.CameraCharacteristics.SENSOR_ORIENTATION) ?: 90

            val cameraReader = ImageReader.newInstance(size.width, size.height, android.graphics.ImageFormat.JPEG, 1)
            cameraReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    image.close()
                    var bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    // Фронтальная камера отдаёт повёрнутое и зеркальное изображение — приводим к нормальному виду.
                    val matrix = android.graphics.Matrix().apply {
                        postRotate(sensorOrientation.toFloat())
                        postScale(-1f, 1f)
                    }
                    bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                    scope.launch { sendToAI(bmp) }
                }
                cameraDevice?.close()
                cameraDevice = null
                cameraReader.close()
            }, mainHandler)

            cameraManager.openCamera(frontId, object : android.hardware.camera2.CameraDevice.StateCallback() {
                override fun onOpened(camera: android.hardware.camera2.CameraDevice) {
                    cameraDevice = camera
                    val requestBuilder = camera.createCaptureRequest(android.hardware.camera2.CameraDevice.TEMPLATE_STILL_CAPTURE)
                    requestBuilder.addTarget(cameraReader.surface)
                    camera.createCaptureSession(listOf(cameraReader.surface), object : android.hardware.camera2.CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: android.hardware.camera2.CameraCaptureSession) {
                            session.capture(requestBuilder.build(), null, mainHandler)
                        }
                        override fun onConfigureFailed(session: android.hardware.camera2.CameraCaptureSession) {
                            showResult("Не удалось настроить камеру")
                        }
                    }, mainHandler)
                }
                override fun onDisconnected(camera: android.hardware.camera2.CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }
                override fun onError(camera: android.hardware.camera2.CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    showResult("Ошибка камеры (код $error)")
                }
            }, mainHandler)
        } catch (e: Exception) {
            showResult("Ошибка камеры: ${e.message}")
        }
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
            "Grok" -> buildGrokRequest(b64, prompt)
            "DeepSeek" -> buildDeepSeekRequest(prompt)
            "Mistral" -> buildMistralRequest(b64, prompt)
            "OpenRouter" -> buildOpenRouterRequest(b64, prompt)
            else -> buildGeminiRequest(b64, prompt)
        }

        val client = OkHttpClient()
        try {
            client.newCall(req).execute().use { resp ->
                val respBody = resp.body?.string() ?: "{}"
                val text = try {
                    when (provider) {
                        "Claude" -> JSONObject(respBody).getJSONArray("content").getJSONObject(0).getString("text")
                        "Gemini" -> JSONObject(respBody).getJSONArray("candidates").getJSONObject(0)
                            .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                        // OpenAI, Grok, DeepSeek, Mistral, OpenRouter все используют
                        // одинаковый OpenAI-совместимый формат ответа.
                        else -> JSONObject(respBody).getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("message").getString("content")
                    }
                } catch (e: Exception) {
                    "Ошибка ($provider): $respBody"
                }
                showResult(text)
            }
        } catch (e: Exception) {
            showResult("Ошибка сети ($provider): ${e.message}")
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
        val model = prefs.getString("gemini_model", "gemini-3.6-flash") ?: "gemini-3.6-flash"
        return Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key")
            .post(body).build()
    }

    private fun buildClaudeRequest(b64: String, prompt: String): Request {
        val key = prefs.getString("claude_key", "") ?: ""
        val model = prefs.getString("claude_model", "claude-sonnet-4-5") ?: "claude-sonnet-4-5"
        val json = JSONObject().apply {
            put("model", model)
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
        val model = prefs.getString("openai_model", "gpt-4o") ?: "gpt-4o"
        val json = JSONObject().apply {
            put("model", model)
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

    /** Grok (xAI) — API полностью совместим с форматом OpenAI, включая картинки. */
    private fun buildGrokRequest(b64: String, prompt: String): Request {
        val key = prefs.getString("grok_key", "") ?: ""
        val model = prefs.getString("grok_model", "grok-4") ?: "grok-4"
        val json = JSONObject().apply {
            put("model", model)
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
            .url("https://api.x.ai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $key")
            .post(body).build()
    }

    /**
     * DeepSeek — на данный момент публичное API не принимает изображения (нет vision-модели),
     * поэтому скриншот не отправляется, а модель отвечает только на текст промпта.
     */
    private fun buildDeepSeekRequest(prompt: String): Request {
        val key = prefs.getString("deepseek_key", "") ?: ""
        val model = prefs.getString("deepseek_model", "deepseek-chat") ?: "deepseek-chat"
        val json = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", "$prompt\n\n(Внимание: DeepSeek API пока не поддерживает изображения, скриншот не передан.)")
            }))
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        return Request.Builder()
            .url("https://api.deepseek.com/chat/completions")
            .addHeader("Authorization", "Bearer $key")
            .post(body).build()
    }

    /** Mistral — vision поддерживается моделями семейства Pixtral. */
    private fun buildMistralRequest(b64: String, prompt: String): Request {
        val key = prefs.getString("mistral_key", "") ?: ""
        val model = prefs.getString("mistral_model", "pixtral-large-latest") ?: "pixtral-large-latest"
        val json = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray()
                    .put(JSONObject().apply { put("type", "text"); put("text", prompt) })
                    .put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", "data:image/png;base64,$b64")
                    }))
            }))
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        return Request.Builder()
            .url("https://api.mistral.ai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $key")
            .post(body).build()
    }

    /**
     * OpenRouter — прокси-сервис с одним ключом на десятки моделей разных провайдеров
     * (Claude, GPT, Gemini, Grok, Llama и т.д.). Модель задаётся строкой вида "provider/model".
     */
    private fun buildOpenRouterRequest(b64: String, prompt: String): Request {
        val key = prefs.getString("openrouter_key", "") ?: ""
        val model = prefs.getString("openrouter_model", "anthropic/claude-3.5-sonnet") ?: "anthropic/claude-3.5-sonnet"
        val json = JSONObject().apply {
            put("model", model)
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
            .url("https://openrouter.ai/api/v1/chat/completions")
            .addHeader("Authorization", "Bearer $key")
            .addHeader("HTTP-Referer", "https://github.com/")
            .addHeader("X-Title", "ScreenAI")
            .post(body).build()
    }

    // ---- Панель с ответом ИИ (вместо Toast, который сам исчезал через пару секунд) ----

    private fun showResult(text: String, autoHideSec: Int = 0) {
        mainHandler.post {
            val liveTextView = activePanelTextView
            if (liveTextView != null && !activePanelPinned) {
                // Обновляем текущую (незакреплённую) карточку новым текстом
                liveTextView.text = text
            } else {
                // Либо карточки ещё нет, либо она закреплена — создаём новую поверх
                createResultPanel(text)
            }
            // Автоскрытие (используется режимом "Слушатель"): если за это время
            // пользователь не закрепил и не закрыл карточку сам — просто убираем её.
            if (autoHideSec > 0) {
                val panelToHide = activePanelView
                mainHandler.postDelayed({
                    if (activePanelView == panelToHide && !activePanelPinned) {
                        try {
                            panelToHide?.let { windowManager.removeView(it) }
                        } catch (e: Exception) {
                            // Могла быть уже удалена (например, пользователь нажал ✕)
                        }
                        activePanelView = null
                        activePanelTextView = null
                    }
                }, autoHideSec * 1000L)
            }
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).roundToInt()

    /** Красивый градиентный фон для плавающей кнопки: светлее сверху, темнее снизу + тонкая светлая обводка. */
    private fun buildButtonDrawable(baseColor: Int, sizePx: Int): GradientDrawable {
        val lighter = adjustColorBrightness(baseColor, 1.25f)
        val darker = adjustColorBrightness(baseColor, 0.8f)
        return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(lighter, baseColor, darker)).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = sizePx / 2f
            setStroke(dpToPx(1), adjustColorBrightness(baseColor, 1.5f))
        }
    }

    private fun adjustColorBrightness(color: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] * factor).coerceIn(0f, 1f)
        return Color.HSVToColor(Color.alpha(color), hsv)
    }

    private fun createResultPanel(initialText: String) {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val panelParams = WindowManager.LayoutParams(
            (metrics.widthPixels * 0.9f).roundToInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        panelParams.gravity = Gravity.TOP or Gravity.START
        panelParams.x = (metrics.widthPixels * 0.05f).roundToInt()
        panelParams.y = (metrics.heightPixels * 0.25f).roundToInt()

        val bg = GradientDrawable().apply {
            setColor(Color.parseColor("#F2222222"))
            cornerRadius = dpToPx(14).toFloat()
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg
            setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(14))
        }

        // Заголовок с кнопками: 📌 закрепить, 📋 копировать, ✕ закрыть
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "Ответ AI"
            setTextColor(Color.WHITE)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val pinButton = Button(this).apply {
            text = "📌"
            textSize = 14f
            minWidth = 0
            minimumWidth = 0
            setPadding(dpToPx(10), 0, dpToPx(10), 0)
        }

        val copyButton = Button(this).apply {
            text = "📋"
            textSize = 14f
            minWidth = 0
            minimumWidth = 0
            setPadding(dpToPx(10), 0, dpToPx(10), 0)
        }

        val closeButton = Button(this).apply {
            text = "✕"
            textSize = 14f
            minWidth = 0
            minimumWidth = 0
            setPadding(dpToPx(10), 0, dpToPx(10), 0)
        }

        header.addView(title)
        header.addView(pinButton)
        header.addView(copyButton)
        header.addView(closeButton)

        // Текст ответа: можно выделять и копировать вручную, окно скроллится
        val textView = TextView(this).apply {
            text = initialText
            setTextColor(Color.WHITE)
            textSize = 15f
            setTextIsSelectable(true)
            setPadding(0, dpToPx(8), 0, 0)
        }

        val scroll = MaxHeightScrollView(this, (metrics.heightPixels * 0.5f).roundToInt()).apply {
            addView(textView)
        }

        root.addView(header)
        root.addView(scroll)

        // Пометка "закреплено" прямо в заголовке
        fun refreshPinnedLook(pinned: Boolean) {
            title.text = if (pinned) "Ответ AI 📌 закреплено" else "Ответ AI"
            pinButton.alpha = if (pinned) 1f else 0.6f
        }
        refreshPinnedLook(false)

        // Перетаскивание карточки за заголовок
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        header.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = panelParams.x
                    initialY = panelParams.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    panelParams.x = initialX + (event.rawX - touchX).toInt()
                    panelParams.y = initialY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(root, panelParams)
                    true
                }
                else -> false
            }
        }

        pinButton.setOnClickListener {
            // Закрепляем именно эту карточку: она больше не будет перезаписываться
            // новыми ответами и останется на экране, пока её не закроют вручную.
            if (activePanelView == root) {
                activePanelPinned = true
                refreshPinnedLook(true)
                Toast.makeText(this, "Ответ закреплён — новый запрос откроет отдельную карточку", Toast.LENGTH_SHORT).show()
            } else {
                refreshPinnedLook(true)
            }
        }

        copyButton.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Ответ AI", textView.text.toString()))
            Toast.makeText(this, "Текст скопирован", Toast.LENGTH_SHORT).show()
        }

        closeButton.setOnClickListener {
            windowManager.removeView(root)
            if (activePanelView == root) {
                activePanelView = null
                activePanelTextView = null
                activePanelPinned = false
            }
        }

        windowManager.addView(root, panelParams)

        activePanelView = root
        activePanelTextView = textView
        activePanelPinned = false
    }

    /** ScrollView с ограничением по максимальной высоте, чтобы длинный ответ не выходил за экран. */
    private class MaxHeightScrollView(context: Context, private val maxHeightPx: Int) : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val capped = View.MeasureSpec.makeMeasureSpec(maxHeightPx, View.MeasureSpec.AT_MOST)
            super.onMeasure(widthMeasureSpec, capped)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLiveMode()
        stopListenMode()
        virtualDisplay?.release()
        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop()
        cameraDevice?.close()
        // Раньше кнопка не убиралась при остановке сервиса — из-за этого при
        // запуске новой плавающей кнопки старая оставалась висеть поверх экрана.
        try {
            if (::windowManager.isInitialized && ::btn.isInitialized) {
                windowManager.removeView(btn)
            }
        } catch (e: Exception) {
            // Вьюха уже могла быть удалена системой — не критично.
        }
    }
}
