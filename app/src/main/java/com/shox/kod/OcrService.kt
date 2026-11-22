package com.shox.kod
import android.app.*
import android.content.*
import android.graphics.*
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.*
import android.os.*
import android.view.*
import android.widget.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.net.*
import java.util.Calendar
import java.util.regex.Pattern

class OcrService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var box: FrameLayout
    private lateinit var p: WindowManager.LayoutParams
    private var proj: MediaProjection? = null
    private var reader: ImageReader? = null
    private val ocr = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val h = Handler(Looper.getMainLooper())
    private var lastSent = ""; private var cand = ""; private var cnt = 0

    override fun onBind(i: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("k", "Kod", NotificationManager.IMPORTANCE_LOW))
        startForeground(1, Notification.Builder(this, "k").setContentTitle("Kod Oquvchi faol").setSmallIcon(android.R.drawable.ic_menu_camera).build())
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        p = WindowManager.LayoutParams(650, 220, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = 100; y = 500 }
        box = object : FrameLayout(this) {
            override fun dispatchDraw(c: Canvas) {
                super.dispatchDraw(c)
                c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { color = Color.GREEN; style = Paint.Style.STROKE; strokeWidth = 6f })
                c.drawCircle(width.toFloat(), height.toFloat(), 35f, Paint().apply { color = Color.YELLOW })
            }
        }
        box.addView(TextView(this).apply { text = "Surish / Kattalashtirish"; setTextColor(Color.GREEN); textSize = 11f; setPadding(10, 5, 0, 0) })
        var ix = 0; var iy = 0; var iw = 0; var ih = 0; var tx = 0f; var ty = 0f; var rs = false
        box.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { tx = e.rawX; ty = e.rawY; ix = p.x; iy = p.y; iw = p.width; ih = p.height; rs = (e.x > box.width - 80 && e.y > box.height - 80); true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - tx).toInt(); val dy = (e.rawY - ty).toInt()
                    if (rs) { p.width = maxOf(200, iw + dx); p.height = maxOf(80, ih + dy) } else { p.x = ix + dx; p.y = iy + dy }
                    wm.updateViewLayout(box, p); true
                }
                else -> false
            }
        }
        wm.addView(box, p)
    }
    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val c = i?.getIntExtra("code", Activity.RESULT_CANCELED) ?: return START_NOT_STICKY
        val d = i.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY
        proj = (getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).getMediaProjection(c, d)
        val dm = resources.displayMetrics
        reader = ImageReader.newInstance(dm.widthPixels, dm.heightPixels, PixelFormat.RGBA_8888, 2)
        proj?.createVirtualDisplay("K", dm.widthPixels, dm.heightPixels, dm.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader?.surface, null, null)
        h.postDelayed(task, 1000); return START_STICKY
    }
    private val task = object : Runnable {
        override fun run() {
            if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) !in 2..3) scan()
            h.postDelayed(this, 700)
        }
    }
    private fun scan() {
        val img = reader?.acquireLatestImage() ?: return
        try {
            val pl = img.planes[0]; val bmp = Bitmap.createBitmap(img.width + (pl.rowStride - pl.pixelStride * img.width) / pl.pixelStride, img.height, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(pl.buffer)
            val loc = IntArray(2); box.getLocationOnScreen(loc)
            val x = maxOf(0, loc[0]); val y = maxOf(0, loc[1])
            val w = minOf(box.width, bmp.width - x); val h = minOf(box.height, bmp.height - y)
            if (w <= 20 || h <= 20) return
            val crop = Bitmap.createBitmap(bmp, x, y, w, h)
            val px = IntArray(w * h); crop.getPixels(px, 0, w, 0, 0, w, h); val hsv = FloatArray(3)
            for (i in px.indices) {
                Color.colorToHSV(px[i], hsv)
                val gold = (hsv[0] in 28f..68f) && (hsv[1] > 0.35f) && (hsv[2] > 0.45f)
                val white = (hsv[1] < 0.25f) && (hsv[2] > 0.75f)
                px[i] = if (gold || white) Color.BLACK else Color.WHITE
            }
            ocr.process(InputImage.fromBitmap(Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888), 0)).addOnSuccessListener { t ->
                val raw = t.text.uppercase()
                val ok = raw.contains("MILLIY") || raw.contains("RAQAM") || raw.contains("BOTGA") || raw.contains("YUBOR")
                val m = Pattern.compile("\\b(\\d{4})\\b").matcher(raw)
                if (ok && m.find()) {
                    val f = m.group(1) ?: ""
                    if (f == cand) cnt++ else { cand = f; cnt = 1 }
                    if (cnt >= 2 && f != lastSent) { lastSent = f; send(f) }
                }
            }
        } catch (_: Exception) {} finally { img.close() }
    }
    private fun send(c: String) {
        Thread {
            try {
                val conn = (URL("http://127.0.0").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; setRequestProperty("Content-Type", "application/json"); doOutput = true
                }
                conn.outputStream.write("""{"code":"$c"}""".toByteArray()); conn.responseCode; conn.disconnect()
            } catch (_: Exception) {}
        }.start()
    }
    override fun onDestroy() { h.removeCallbacks(task); proj?.stop(); wm.removeView(box); super.onDestroy() }
}
