package com.shox.kod
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.*

class MainActivity : Activity() {
    private lateinit var pm: MediaProjectionManager
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(50, 80, 50, 50) }
        val info = TextView(this).apply { text = "1. Ruxsat bering\n2. Ishga tushiring\n(02:00-04:00 pauza)"; textSize = 16f }
        val b1 = Button(this).apply { text = "1. Ekran ustida ruxsat" }
        val b2 = Button(this).apply { text = "2. Ramkani yoqish" }
        val b3 = Button(this).apply { text = "Toxtatish" }
        layout.addView(info); layout.addView(b1); layout.addView(b2); layout.addView(b3)
        setContentView(layout)
        pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        b1.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            else Toast.makeText(this, "Ruxsat bor!", Toast.LENGTH_SHORT).show()
        }
        b2.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) { Toast.makeText(this, "Avval 1-tugma!", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            startActivityForResult(pm.createScreenCaptureIntent(), 100)
        }
        b3.setOnClickListener { stopService(Intent(this, OcrService::class.java)) }
    }
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 100 && res == Activity.RESULT_OK && data != null) {
            startForegroundService(Intent(this, OcrService::class.java).apply { putExtra("code", res); putExtra("data", data) })
            moveTaskToBack(true)
        }
    }
}
