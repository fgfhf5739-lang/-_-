package com.example.gametapper

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {
    private lateinit var sp: SharedPreferences
    private lateinit var sw: Switch

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        sp = getSharedPreferences("cfg", MODE_PRIVATE)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(48), dp(20), dp(24)) }

        fun text(t: String, size: Float, bold: Boolean = false, top: Int = 0) = TextView(this).apply {
            text = t; textSize = size; setPadding(0, dp(top), 0, 0)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        fun button(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }

        // ── Tiêu đề
        root.addView(text("GameTapper", 24f, true))
        root.addView(text("Tự bấm theo màn hình + tự mở lại game", 14f))

        // ── Điều khiển chính
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(16), 0, dp(8)) }
        val lp = { LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        row.addView(button("▶ BẮT ĐẦU") { startAll() }, lp())
        row.addView(button("■ DỪNG") {
            stopService(Intent(this, CaptureService::class.java))
            sw.isChecked = false
        }, lp())
        root.addView(row)

        // ── Tuỳ chọn
        root.addView(text("Tuỳ chọn", 16f, true, 12))
        sw = Switch(this).apply { text = "Tự mở lại game (mỗi 30 giây)"; isChecked = sp.getBoolean("wd_on", false) }
        sw.setOnCheckedChangeListener { _, on ->
            if (on && packageManager.getLaunchIntentForPackage(DEFAULT_PKG) == null) {
                Toast.makeText(this, "Chưa cài game $DEFAULT_PKG", Toast.LENGTH_SHORT).show()
                sw.isChecked = false
            } else sp.edit().putBoolean("wd_on", on).apply()
        }
        root.addView(sw)
        val swBoot = Switch(this).apply { text = "Tự chạy khi bật máy"; isChecked = sp.getBoolean("boot_on", true) }
        swBoot.setOnCheckedChangeListener { _, on -> sp.edit().putBoolean("boot_on", on).apply() }
        root.addView(swBoot)

        // ── Cấp quyền thủ công (ẩn mặc định; máy root không cần)
        val group = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = android.view.View.GONE }
        group.addView(button("Bật Trợ năng") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        group.addView(button("Usage Access") { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) })
        group.addView(button("Hiển thị trên ứng dụng khác") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        group.addView(button("Tắt tối ưu pin") {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        })
        val toggle = text("Cấp quyền thủ công (máy không root)  ▼", 14f, true, 20)
        toggle.setOnClickListener {
            val open = group.visibility == android.view.View.VISIBLE
            group.visibility = if (open) android.view.View.GONE else android.view.View.VISIBLE
            toggle.text = "Cấp quyền thủ công (máy không root)  " + if (open) "▼" else "▲"
        }
        root.addView(toggle)
        root.addView(group)

        setContentView(ScrollView(this).apply { addView(root) })
        requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 0)
        if (intent.getBooleanExtra("boot", false)) startAll()
    }

    /** Chạy lệnh root; không có root thì bỏ qua. */
    private fun su(script: String) {
        try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", script))
            p.waitFor()
        } catch (e: Exception) {}
    }

    /** 1 lần bấm: (root) tự cấp quyền, bật tự mở game, rồi bắt đầu ghi màn hình. */
    private fun startAll() {
        sw.isChecked = true
        val pkg = packageName
        val svc = "$pkg/$pkg.ClickService"
        Thread {
            su("appops set $pkg PROJECT_MEDIA allow; appops set $pkg GET_USAGE_STATS allow; " +
               "appops set $pkg SYSTEM_ALERT_WINDOW allow; dumpsys deviceidle whitelist +$pkg")
            su("cur=\$(settings get secure enabled_accessibility_services); " +
               "case \"\$cur\" in *$svc*) ;; null|\"\") settings put secure enabled_accessibility_services $svc;; " +
               "*) settings put secure enabled_accessibility_services \"\$cur:$svc\";; esac; " +
               "settings put secure accessibility_enabled 1")
            runOnUiThread {
                val m = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                startActivityForResult(m.createScreenCaptureIntent(), 1)
            }
        }.start()
    }

    override fun onActivityResult(rc: Int, res: Int, d: Intent?) {
        if (rc == 1 && res == RESULT_OK && d != null) {
            startForegroundService(Intent(this, CaptureService::class.java).putExtra("code", res).putExtra("data", d))
            packageManager.getLaunchIntentForPackage(DEFAULT_PKG)?.let { startActivity(it) }
        }
    }
}
