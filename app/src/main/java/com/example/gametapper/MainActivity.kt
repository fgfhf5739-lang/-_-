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

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        sp = getSharedPreferences("cfg", MODE_PRIVATE)
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 96, 48, 48) }
        fun btn(t: String, f: () -> Unit) = l.addView(Button(this).apply { text = t; setOnClickListener { f() } })

        btn("1. Bật Trợ năng (GameTapper)") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        btn("2. Cấp quyền Usage Access") { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        btn("3. Cấp quyền Hiển thị trên ứng dụng khác") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        btn("4. Tắt tối ưu pin") {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }

        sw = Switch(this).apply { text = "Tự mở lại game (mỗi 30 giây)"; isChecked = sp.getBoolean("wd_on", false) }
        sw.setOnCheckedChangeListener { _, on ->
            if (on && packageManager.getLaunchIntentForPackage(DEFAULT_PKG) == null) {
                Toast.makeText(this, "Chưa cài game $DEFAULT_PKG", Toast.LENGTH_SHORT).show()
                sw.isChecked = false
            } else sp.edit().putBoolean("wd_on", on).apply()
        }
        l.addView(sw)

        val swBoot = Switch(this).apply { text = "Tự chạy khi bật máy"; isChecked = sp.getBoolean("boot_on", true) }
        swBoot.setOnCheckedChangeListener { _, on -> sp.edit().putBoolean("boot_on", on).apply() }
        l.addView(swBoot)

        btn("▶ BẮT ĐẦU (cả tự bấm + tự mở game)") { startAll() }
        btn("Dừng") {
            stopService(Intent(this, CaptureService::class.java))
            sw.isChecked = false
        }

        setContentView(ScrollView(this).apply { addView(l) })
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
