package com.example.gametapper

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val sp = getSharedPreferences("cfg", MODE_PRIVATE)
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 96, 48, 48) }
        fun btn(t: String, f: () -> Unit) = l.addView(Button(this).apply { text = t; setOnClickListener { f() } })

        btn("1. Bật Trợ năng (GameTapper)") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        btn("2. Bắt đầu (cấp quyền ghi màn hình)") {
            val m = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(m.createScreenCaptureIntent(), 1)
        }
        btn("Dừng") { stopService(Intent(this, CaptureService::class.java)) }

        btn("3. Cấp quyền Usage Access") { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        btn("4. Cấp quyền Hiển thị trên ứng dụng khác") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        btn("5. Tắt tối ưu pin") {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }
        val sw = Switch(this).apply { text = "Tự mở lại game (mỗi 30 giây)"; isChecked = sp.getBoolean("wd_on", false) }
        sw.setOnCheckedChangeListener { _, on ->
            if (on && packageManager.getLaunchIntentForPackage(DEFAULT_PKG) == null) {
                Toast.makeText(this, "Chưa cài game $DEFAULT_PKG", Toast.LENGTH_SHORT).show()
                sw.isChecked = false
            } else {
                sp.edit().putBoolean("wd_on", on).apply()
                if (on) packageManager.getLaunchIntentForPackage(DEFAULT_PKG)?.let { startActivity(it) }
            }
        }
        l.addView(sw)

        setContentView(ScrollView(this).apply { addView(l) })
        requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 0)
    }

    override fun onActivityResult(rc: Int, res: Int, d: Intent?) {
        if (rc == 1 && res == RESULT_OK && d != null)
            startForegroundService(Intent(this, CaptureService::class.java).putExtra("code", res).putExtra("data", d))
    }
}
