package com.example.gametapper

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle

/** Activity trong suốt, không có giao diện: xin quyền ghi màn hình rồi bật dịch vụ và tự đóng. */
class StarterActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
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

    private fun su(script: String) {
        try { Runtime.getRuntime().exec(arrayOf("su", "-c", script)).waitFor() } catch (e: Exception) {}
    }

    override fun onActivityResult(rc: Int, res: Int, d: Intent?) {
        if (rc == 1 && res == RESULT_OK && d != null) {
            startForegroundService(Intent(this, CaptureService::class.java).putExtra("code", res).putExtra("data", d))
            packageManager.getLaunchIntentForPackage(DEFAULT_PKG)?.let { startActivity(it) }
        }
        finish()
    }
}
