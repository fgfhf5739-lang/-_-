package com.example.gametapper

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 96, 48, 48) }
        fun btn(t: String, f: () -> Unit) = l.addView(Button(this).apply { text = t; setOnClickListener { f() } })
        btn("1. Bật Trợ năng (GameTapper)") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        btn("2. Bắt đầu (cấp quyền ghi màn hình)") {
            val m = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(m.createScreenCaptureIntent(), 1)
        }
        btn("Dừng") { stopService(Intent(this, CaptureService::class.java)) }
        setContentView(l)
        requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 0)
    }
    override fun onActivityResult(rc: Int, res: Int, d: Intent?) {
        if (rc == 1 && res == RESULT_OK && d != null)
            startForegroundService(Intent(this, CaptureService::class.java).putExtra("code", res).putExtra("data", d))
    }
}
