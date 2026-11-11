package com.nexora.smartpanel

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.provider.Settings

/**
 * Membuka aplikasi dalam mode layar penuh atau freeform (mengambang).
 * Freeform butuh dukungan dari sistem (lihat tutorial di halaman utama).
 */
object AppLauncher {
    private var counter = 0

    /** Freeform dianggap siap kalau HP mendukungnya atau pengaturannya sudah dinyalakan. */
    fun freeformReady(ctx: Context): Boolean {
        val feature = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)
        val flag = try {
            Settings.Global.getInt(ctx.contentResolver, "enable_freeform_support", 0) == 1
        } catch (e: Exception) {
            false
        }
        return feature || flag
    }

    private fun setWindowing(opts: ActivityOptions, freeform: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                // mode jendela: 5 = freeform, 1 = layar penuh
                ActivityOptions::class.java
                    .getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                    .invoke(opts, if (freeform) 5 else 1)
            } else {
                // Android 7.x: id stack, 2 = freeform, 1 = layar penuh
                ActivityOptions::class.java
                    .getMethod("setLaunchStackId", Int::class.javaPrimitiveType)
                    .invoke(opts, if (freeform) 2 else 1)
            }
        } catch (e: Throwable) {
            // diabaikan; freeform tetap dicoba lewat launch bounds
        }
    }

    fun launch(ctx: Context, pkg: String, wantFreeform: Boolean): Boolean {
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val freeform = wantFreeform && freeformReady(ctx)
        val opts = ActivityOptions.makeBasic()
        setWindowing(opts, freeform)

        if (freeform) {
            val dm = ctx.resources.displayMetrics
            val sw = dm.widthPixels
            val sh = dm.heightPixels
            val w = (sw * 0.74f).toInt()
            val h = (sh * 0.56f).toInt()
            val step = (dm.density * 28).toInt()
            val n = counter % 5 - 2
            val left = ((sw - w) / 2 + n * step).coerceIn(0, maxOf(0, sw - w))
            val top = ((sh - h) / 3 + n * step).coerceIn(0, maxOf(0, sh - h))
            opts.setLaunchBounds(Rect(left, top, left + w, top + h))
            counter++
        }

        return try {
            ctx.startActivity(intent, opts.toBundle())
            Prefs.addRunning(ctx, pkg)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Menutup aplikasi seperti dihapus dari Recents. Butuh Shizuku.
     * Mengembalikan true kalau berhasil lewat Shizuku. Jangan dipanggil dari main thread.
     */
    fun kill(ctx: Context, pkg: String): Boolean {
        Prefs.removeRunning(ctx, pkg)
        val ok = Priv.kill(ctx, pkg)
        if (!ok) {
            // tanpa Shizuku hanya bisa mematikan aplikasi yang sudah di latar belakang
            try {
                (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).killBackgroundProcesses(pkg)
            } catch (e: Exception) {
                // diabaikan
            }
        }
        return ok
    }
}
