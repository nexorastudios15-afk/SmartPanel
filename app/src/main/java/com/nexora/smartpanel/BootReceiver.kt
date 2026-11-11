package com.nexora.smartpanel

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings

/** Fungsi bantu untuk menyalakan dan menghidupkan lagi panel. */
object Revive {
    private const val ACTION = "com.nexora.smartpanel.RESTART"

    fun start(ctx: Context) {
        if (!Prefs.enabled(ctx) || !Settings.canDrawOverlays(ctx)) return
        try {
            val i = Intent(ctx, PanelService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        } catch (e: Exception) {
            // sistem menolak start dari latar belakang; coba lagi nanti lewat alarm
            schedule(ctx, 5000)
        }
    }

    /** Menjadwalkan start ulang. Hanya jika panel aktif dan pengaturan "hidupkan lagi" menyala. */
    fun schedule(ctx: Context, delayMs: Long) {
        if (!Prefs.enabled(ctx) || !Prefs.keepAlive(ctx)) return
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getBroadcast(
                ctx, 77,
                Intent(ctx, RestartReceiver::class.java).setAction(ACTION),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delayMs, pi)
        } catch (e: Exception) {
            // diabaikan
        }
    }
}

/** Menyalakan lagi panel setelah HP restart atau aplikasi diperbarui. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val a = intent.action ?: return
        if (a == Intent.ACTION_BOOT_COMPLETED || a == "android.intent.action.QUICKBOOT_POWERON") {
            // setelah restart semua aplikasi sudah tertutup
            Prefs.clearRunning(context)
            if (Prefs.autostart(context)) Revive.start(context)
        } else if (a == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Revive.start(context)
        }
    }
}

/** Dipanggil alarm: menyalakan lagi panel yang ke-kill. */
class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Revive.start(context)
    }
}
