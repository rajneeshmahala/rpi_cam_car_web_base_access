package com.rajneeshmahala.picar

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : Activity() {
    private val bg = Color.rgb(11, 17, 27)
    private val panel = Color.rgb(19, 28, 42)
    private val mint = Color.rgb(82, 224, 196)
    private val ink = Color.rgb(236, 243, 250)
    private val muted = Color.rgb(142, 158, 177)
    private val red = Color.rgb(255, 103, 120)
    private val ui = Handler(Looper.getMainLooper())
    private val driveQueue = Executors.newSingleThreadExecutor()
    private val statusQueue = Executors.newSingleThreadExecutor()
    private val statusBusy = AtomicBoolean(false)
    private val prefs by lazy { getSharedPreferences("pi_rover", MODE_PRIVATE) }
    private var baseUrl: String? = null
    private var piOnline = false
    private var cameraOnline = false
    private var heldAction: String? = null
    private val driveHeartbeat = object : Runnable {
        override fun run() {
            heldAction?.let { action ->
                sendCommand(action)
                ui.postDelayed(this, 250)
            }
        }
    }
    private lateinit var address: EditText
    private lateinit var connectionText: TextView
    private lateinit var connectionDot: View
    private lateinit var cameraText: TextView
    private lateinit var cameraDot: View
    private lateinit var feed: WebView
    private lateinit var driveButtons: List<TextView>
    private lateinit var stopButton: TextView

    private val poll = object : Runnable {
        override fun run() {
            baseUrl?.let { checkStatus(it) }
            ui.postDelayed(this, 2500)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        buildUi()
        val saved = prefs.getString("pi_url", "").orEmpty()
        address.setText(saved)
        if (saved.isNotBlank()) connectToPi()
    }

    private fun buildUi() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(14))
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(label("KAVYACAR", 22f, ink, true, "sans-serif-black"))
        brand.addView(label("CAMERA CAR CONTROL", 10f, muted, true, "sans-serif-medium").apply { letterSpacing = 0.12f; setPadding(0, dp(3), 0, 0) })
        top.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        val stopChip = label("■  STOP", 12f, red, true, "sans-serif-bold").apply {
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(48, 24, 33), dp(14))
            setPadding(dp(15), dp(10), dp(15), dp(10))
            setOnClickListener { stopDrive() }
            contentDescription = "Emergency stop"
        }
        top.addView(stopChip)
        page.addView(top)

        val scroll = ScrollView(this).apply { clipToPadding = false; isFillViewport = false }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(2), dp(18), dp(24))
        }
        scroll.addView(content)

        val connectionCard = card()
        connectionCard.addView(sectionTitle("CONNECTION", "Local Wi-Fi link to your Raspberry Pi"))
        address = EditText(this).apply {
            setSingleLine(true)
            textSize = 14f
            setTextColor(ink)
            setHintTextColor(Color.rgb(103, 119, 139))
            hint = "Pi address  •  raspberrypi.local:5000"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_GO
            setPadding(dp(14), 0, dp(10), 0)
            background = rounded(Color.rgb(11, 18, 29), dp(12), Color.rgb(43, 57, 76))
            setOnEditorActionListener { _, action, _ ->
                if (action == android.view.inputmethod.EditorInfo.IME_ACTION_GO) { connectToPi(); true } else false
            }
        }
        val connectRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        connectRow.addView(address, LinearLayout.LayoutParams(0, dp(50), 1f))
        val connect = actionButton("CONNECT", mint, Color.rgb(11, 35, 39), 12f)
        connect.setOnClickListener { connectToPi() }
        connectRow.addView(connect, LinearLayout.LayoutParams(dp(106), dp(50)).apply { leftMargin = dp(9) })
        connectionCard.addView(connectRow)
        val connectionStatus = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(13), 0, 0)
        }
        connectionDot = dot(Color.rgb(105, 120, 138))
        connectionStatus.addView(connectionDot, LinearLayout.LayoutParams(dp(8), dp(8)).apply { rightMargin = dp(8) })
        connectionText = label("Not connected", 12f, muted, false)
        connectionStatus.addView(connectionText)
        connectionStatus.addView(label("  ·  ", 12f, muted, false))
        cameraDot = dot(Color.rgb(105, 120, 138))
        connectionStatus.addView(cameraDot, LinearLayout.LayoutParams(dp(7), dp(7)).apply { rightMargin = dp(6) })
        cameraText = label("Camera idle", 12f, muted, false)
        connectionStatus.addView(cameraText)
        connectionCard.addView(connectionStatus)
        content.addView(connectionCard, cardParams())

        val videoCard = card().apply { setPadding(dp(8), dp(8), dp(8), dp(8)) }
        val videoHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(2), dp(6), dp(8))
        }
        videoHeader.addView(label("LIVE CAMERA", 11f, ink, true, "sans-serif-bold"), LinearLayout.LayoutParams(0, -2, 1f))
        videoHeader.addView(label("MJPEG  ·  LIVE", 9f, mint, true, "sans-serif-medium").apply { letterSpacing = 0.08f })
        videoCard.addView(videoHeader)
        feed = WebView(this).apply {
            setBackgroundColor(Color.rgb(5, 9, 14))
            settings.javaScriptEnabled = false
            settings.domStorageEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.loadsImagesAutomatically = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.setSupportZoom(false)
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    view?.evaluateJavascript("document.getElementById('stream').src", null)
                }
            }
        }
        videoCard.addView(feed, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(208)))
        content.addView(videoCard, cardParams())

        val controlCard = card()
        controlCard.addView(sectionTitle("DRIVE", "Hold a direction to move · release to stop"))
        val pad = GridLayout(this).apply {
            rowCount = 3
            columnCount = 3
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
            setPadding(dp(4), dp(3), dp(4), dp(3))
        }
        fun emptyCell() = View(this).apply { isClickable = false }
        fun addCell(view: View, row: Int, col: Int, width: Int = 0, height: Int = dp(70)) {
            val lp = GridLayout.LayoutParams(GridLayout.spec(row, 1f), GridLayout.spec(col, 1f)).apply {
                this.width = if (width == 0) 0 else dp(width)
                this.height = height
                setMargins(dp(5), dp(5), dp(5), dp(5))
            }
            pad.addView(view, lp)
        }
        addCell(emptyCell(), 0, 0)
        val forward = driveButton("↑", "Forward", "forward")
        addCell(forward, 0, 1)
        addCell(emptyCell(), 0, 2)
        val left = driveButton("←", "Left", "left")
        addCell(left, 1, 0)
        stopButton = label("STOP", 12f, red, true, "sans-serif-black").apply {
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(54, 25, 35), dp(22), Color.rgb(114, 51, 66))
            setOnClickListener { stopDrive() }
            contentDescription = "Stop car"
        }
        addCell(stopButton, 1, 1)
        val right = driveButton("→", "Right", "right")
        addCell(right, 1, 2)
        addCell(emptyCell(), 2, 0)
        val backward = driveButton("↓", "Reverse", "backward")
        addCell(backward, 2, 1)
        addCell(emptyCell(), 2, 2)
        controlCard.addView(pad)
        driveButtons = listOf(forward, left, right, backward)
        setDriveEnabled(false)
        content.addView(controlCard, cardParams())

        val note = label("For safety, motion stops when you release a control or the Pi stops receiving commands.", 11f, muted, false)
        note.setPadding(dp(4), dp(2), dp(4), dp(8))
        content.addView(note)
        page.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(page)
    }

    private fun connectToPi() {
        val normalized = normalizeBaseUrl(address.text.toString())
        if (normalized == null) {
            address.error = "Enter a Pi hostname or IP, such as 192.168.1.25:5000"
            return
        }
        try { (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(address.windowToken, 0) } catch (_: Exception) {}
        address.error = null
        if (baseUrl != null && baseUrl != normalized) sendCommand("stop")
        baseUrl = normalized
        prefs.edit().putString("pi_url", normalized).apply()
        connectionText.text = "Connecting…"
        connectionDot.setBackgroundColor(Color.rgb(244, 186, 85))
        setDriveEnabled(false)
        loadFeed(normalized)
        checkStatus(normalized)
    }

    private fun loadFeed(base: String) {
        val html = """
            <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
            <style>html,body{margin:0;width:100%;height:100%;background:#05090e;overflow:hidden}img{width:100%;height:100%;object-fit:contain}</style>
            </head><body><img id="stream" src="/video" alt="Live camera"></body></html>
        """.trimIndent()
        feed.loadDataWithBaseURL("$base/", html, "text/html", "UTF-8", null)
    }

    private fun checkStatus(base: String) {
        if (!statusBusy.compareAndSet(false, true)) return
        statusQueue.execute {
            var online = false
            var camera = false
            try {
                val connection = (URL("$base/status").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 1400
                    readTimeout = 1400
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    online = connection.responseCode in 200..299
                    if (online) camera = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).optBoolean("camera", false)
                } finally { connection.disconnect() }
            } catch (_: Exception) { }
            statusBusy.set(false)
            ui.post {
                if (baseUrl != base || isFinishing) return@post
                piOnline = online
                cameraOnline = camera
                if (!online) stopDrive()
                connectionText.text = if (online) "Pi connected" else "Pi not responding"
                connectionText.setTextColor(if (online) mint else muted)
                connectionDot.setBackgroundColor(if (online) mint else red)
                cameraText.text = if (camera) "Camera live" else if (online) "Camera offline" else "Camera idle"
                cameraText.setTextColor(if (camera) mint else muted)
                cameraDot.setBackgroundColor(if (camera) mint else if (online) red else Color.rgb(105, 120, 138))
                setDriveEnabled(online)
            }
        }
    }

    private fun sendCommand(action: String) {
        val base = baseUrl ?: return
        if (action != "stop" && !piOnline) return
        driveQueue.execute {
            try {
                val connection = (URL("$base/control").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 900
                    readTimeout = 900
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    connection.outputStream.use { it.write(JSONObject().put("action", action).toString().toByteArray(Charsets.UTF_8)) }
                    val code = connection.responseCode
                    if (code !in 200..299) throw IllegalStateException("Control request failed: $code")
                } finally { connection.disconnect() }
            } catch (_: Exception) {
                if (action != "stop") ui.post { connectionText.text = "Link lost · stopping" }
            }
        }
    }

    private fun driveButton(icon: String, title: String, action: String): TextView {
        return label("$icon\n$title", 14f, ink, true, "sans-serif-medium").apply {
            gravity = Gravity.CENTER
            setLineSpacing(dp(2).toFloat(), 1f)
            background = rounded(Color.rgb(25, 39, 56), dp(18), Color.rgb(43, 62, 84))
            contentDescription = title
            isFocusable = true
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        if (piOnline) {
                            view.isPressed = true
                            heldAction = action
                            ui.removeCallbacks(driveHeartbeat)
                            sendCommand(action)
                            ui.postDelayed(driveHeartbeat, 250)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_UP -> {
                        view.isPressed = false
                        stopDrive()
                        if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
                        true
                    }
                    else -> true
                }
            }
        }
    }

    private fun stopDrive() {
        heldAction = null
        ui.removeCallbacks(driveHeartbeat)
        sendCommand("stop")
    }

    private fun setDriveEnabled(enabled: Boolean) {
        if (!::driveButtons.isInitialized) return
        driveButtons.forEach { button ->
            button.isEnabled = enabled
            button.alpha = if (enabled) 1f else 0.42f
        }
        stopButton.isEnabled = baseUrl != null
        stopButton.alpha = if (baseUrl != null) 1f else 0.5f
    }

    private fun normalizeBaseUrl(value: String): String? {
        val input = value.trim().trimEnd('/')
        if (input.isEmpty()) return null
        val withScheme = if (input.startsWith("http://", true) || input.startsWith("https://", true)) input else "http://$input"
        val uri = Uri.parse(withScheme)
        if (uri.host.isNullOrBlank() || uri.path.orEmpty().isNotEmpty() && uri.path != "/") return null
        if (uri.scheme != "http" && uri.scheme != "https") return null
        return withScheme
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(15))
        background = rounded(panel, dp(20), Color.rgb(31, 44, 62))
    }

    private fun sectionTitle(title: String, subtitle: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(label(title, 11f, ink, true, "sans-serif-bold").apply { letterSpacing = 0.08f })
        addView(label(subtitle, 11f, muted, false).apply { setPadding(0, dp(4), 0, dp(13)) })
    }

    private fun cardParams() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false, family: String = "sans-serif"): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        typeface = Typeface.create(family, if (bold) Typeface.BOLD else Typeface.NORMAL)
        includeFontPadding = true
    }

    private fun actionButton(text: String, foreground: Int, fill: Int, size: Float) = label(text, size, foreground, true, "sans-serif-bold").apply {
        gravity = Gravity.CENTER
        background = rounded(fill, dp(13))
        isClickable = true
        isFocusable = true
    }

    private fun dot(color: Int) = View(this).apply {
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
    }

    private fun rounded(fill: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius.toFloat()
        stroke?.let { setStroke(dp(1), it) }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        ui.removeCallbacks(poll)
        ui.post(poll)
    }

    override fun onPause() {
        stopDrive()
        ui.removeCallbacks(poll)
        super.onPause()
    }

    override fun onDestroy() {
        stopDrive()
        if (::feed.isInitialized) feed.destroy()
        driveQueue.shutdown()
        statusQueue.shutdown()
        super.onDestroy()
    }
}
