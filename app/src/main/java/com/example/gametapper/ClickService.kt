package com.example.gametapper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

const val DEFAULT_PKG = "cn.global.xddq.t10p"

class ClickService : AccessibilityService() {
    companion object { var instance: ClickService? = null }

    private val h = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            try { watch() } catch (e: Exception) {}
            h.postDelayed(this, 30000) // 30 giây
        }
    }

    override fun onServiceConnected() {
        instance = this
        h.removeCallbacks(tick); h.postDelayed(tick, 5000)
    }
    override fun onUnbind(i: Intent?): Boolean {
        instance = null; h.removeCallbacks(tick); return super.onUnbind(i)
    }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun tap(x: Float, y: Float) {
        val p = Path().apply { moveTo(x, y) }
        dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, 60)).build(), null, null)
    }

    /** Nếu game không ở foreground thì mở lại. */
    private fun watch() {
        val sp = getSharedPreferences("cfg", MODE_PRIVATE)
        if (!sp.getBoolean("wd_on", false)) return
        if (DEFAULT_PKG != foregroundPackage()) launchGame(DEFAULT_PKG)
    }

    private fun foregroundPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val ev = usm.queryEvents(now - 6 * 3600 * 1000L, now)
        val e = UsageEvents.Event()
        var last: String? = null
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) last = e.packageName
            else if (e.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND && e.packageName == last) last = null
        }
        return last
    }

    private fun launchGame(pkg: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }
}
