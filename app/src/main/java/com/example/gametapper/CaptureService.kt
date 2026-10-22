package com.example.gametapper

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import kotlin.math.abs

/** Vùng mẫu cắt từ màn hình gốc 360x640; (tx,ty) là điểm chạm. */
class Step(val name: String, val l: Int, val t: Int, val r: Int, val b: Int,
           val tx: Int, val ty: Int, val cooldown: Long) { var sig = FloatArray(0) }

class CaptureService : Service() {
    // Toạ độ theo khung game 360x640 (game full chiều ngang, căn giữa dọc)
    private val RW = 360f; private val RH = 640f
    private val N = 24; private val THRESH = 22f
    private val steps = listOf(
        Step("announce", 115, 96, 245, 118, 308, 131, 3000),
        Step("login", 95, 468, 262, 492, 190, 520, 6000),
        Step("pickup", 110, 216, 212, 238, 340, 180, 2500),
        Step("auto", 140, 98, 218, 120, 194, 478, 3000),
        Step("main", 232, 548, 262, 585, 246, 568, 60000)
    )
    private val last = HashMap<String, Long>()
    private val h = Handler(Looper.getMainLooper())
    private var proj: MediaProjection? = null
    private var vd: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var on = false

    private fun sig(bm: Bitmap): FloatArray {
        val s = Bitmap.createScaledBitmap(bm, N, N, true)
        val px = IntArray(N * N); s.getPixels(px, 0, N, 0, 0, N, N)
        return FloatArray(N * N) { val c = px[it]; 0.3f * Color.red(c) + 0.59f * Color.green(c) + 0.11f * Color.blue(c) }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val ch = "cap"
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(ch, "GameTapper", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, ch).setContentTitle("GameTapper đang chạy")
            .setSmallIcon(android.R.drawable.ic_menu_view).build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        @Suppress("DEPRECATION")
        val data = i?.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY
        val m = getSystemService(MediaProjectionManager::class.java)
        proj = m.getMediaProjection(i.getIntExtra("code", Activity.RESULT_OK), data)
        proj!!.registerCallback(object : MediaProjection.Callback() { override fun onStop() { stopSelf() } }, h)
        val dm = resources.displayMetrics
        val w = dm.widthPixels; val hh = dm.heightPixels
        reader = ImageReader.newInstance(w, hh, PixelFormat.RGBA_8888, 2)
        vd = proj!!.createVirtualDisplay("cap", w, hh, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, h)
        for (s in steps) s.sig = sig(BitmapFactory.decodeStream(assets.open(s.name + ".png")))
        on = true
        h.postDelayed(loop, 2000)
        return START_NOT_STICKY
    }

    private val loop = object : Runnable {
        override fun run() {
            if (!on) return
            try { check() } catch (e: Exception) {}
            h.postDelayed(this, 1500)
        }
    }

    private fun check() {
        val img = reader?.acquireLatestImage() ?: return
        val bm: Bitmap
        try {
            val pl = img.planes[0]
            val rowPx = pl.rowStride / pl.pixelStride
            val tmp = Bitmap.createBitmap(rowPx, img.height, Bitmap.Config.ARGB_8888)
            tmp.copyPixelsFromBuffer(pl.buffer)
            bm = Bitmap.createBitmap(tmp, 0, 0, img.width, img.height)
        } finally { img.close() }
        val k = bm.width / RW; val oy = (bm.height - RH * k) / 2f
        val now = SystemClock.elapsedRealtime()
        val dbg = StringBuilder()
        dbg.append(if (ClickService.instance == null) "TRỢ NĂNG TẮT! " else "TN ok ")
        for (s in steps) {
            val l0 = (s.l * k).toInt().coerceIn(0, bm.width - 2)
            val t0 = (oy + s.t * k).toInt().coerceIn(0, bm.height - 2)
            val w0 = ((s.r - s.l) * k).toInt().coerceIn(1, bm.width - l0)
            val h0 = ((s.b - s.t) * k).toInt().coerceIn(1, bm.height - t0)
            val c0 = sig(Bitmap.createBitmap(bm, l0, t0, w0, h0))
            var d0 = 0f; for (i in c0.indices) d0 += abs(c0[i] - s.sig[i])
            dbg.append(s.name.take(3)).append('=').append((d0 / c0.size).toInt()).append(' ')
        }
        dbg.append(" px=").append(Color.red(bm.getPixel(bm.width / 2, bm.height / 2)))
        getSystemService(NotificationManager::class.java).notify(1,
            Notification.Builder(this, "cap").setContentTitle("GameTapper (ngưỡng $THRESH)")
                .setContentText(dbg.toString()).setStyle(Notification.BigTextStyle().bigText(dbg.toString()))
                .setSmallIcon(android.R.drawable.ic_menu_view).build())
        for (s in steps) {
            if (now - (last[s.name] ?: 0L) < s.cooldown) continue
            val l = (s.l * k).toInt().coerceIn(0, bm.width - 2)
            val t = (oy + s.t * k).toInt().coerceIn(0, bm.height - 2)
            val w = ((s.r - s.l) * k).toInt().coerceIn(1, bm.width - l)
            val hh = ((s.b - s.t) * k).toInt().coerceIn(1, bm.height - t)
            val cur = sig(Bitmap.createBitmap(bm, l, t, w, hh))
            var d = 0f; for (i in cur.indices) d += abs(cur[i] - s.sig[i])
            if (d / cur.size < THRESH) {
                last[s.name] = now
                ClickService.instance?.tap(s.tx * k, oy + s.ty * k)
                break
            }
        }
    }

    override fun onDestroy() {
        on = false; h.removeCallbacksAndMessages(null)
        vd?.release(); reader?.close(); proj?.stop()
    }
}
