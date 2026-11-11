package com.nexora.smartpanel

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper

/** Daftar aplikasi yang bisa dibuka (punya ikon launcher). Dimuat sekali lalu disimpan. */
object AppIndex {
    class Entry(val pkg: String, val label: String, val icon: Drawable)

    @Volatile
    private var cache: List<Entry>? = null

    fun invalidate() {
        cache = null
    }

    fun load(ctx: Context, done: (List<Entry>) -> Unit) {
        val c = cache
        if (c != null) {
            done(c)
            return
        }
        val app = ctx.applicationContext
        val main = Handler(Looper.getMainLooper())
        Thread {
            val pm = app.packageManager
            val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val out = ArrayList<Entry>()
            val seen = HashSet<String>()
            for (r in pm.queryIntentActivities(q, 0)) {
                val pkg = r.activityInfo.packageName
                if (pkg == app.packageName || !seen.add(pkg)) continue
                try {
                    out.add(Entry(pkg, r.loadLabel(pm).toString(), r.loadIcon(pm)))
                } catch (e: Exception) {
                    // lewati aplikasi yang ikonnya gagal dimuat
                }
            }
            out.sortBy { it.label.lowercase() }
            cache = out
            main.post { done(out) }
        }.start()
    }
}
