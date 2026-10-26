package com.example.gametapper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Khi máy khởi động xong: nếu lần trước đang chạy thì tự chạy ngầm (không hiện giao diện). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val sp = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)
        if (!sp.getBoolean("boot_on", true) || !sp.getBoolean("wd_on", false)) return
        try {
            c.startActivity(Intent(c, StarterActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {}
    }
}
