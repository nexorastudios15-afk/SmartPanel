package com.nexora.smartpanel

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * Akses khusus lewat Shizuku. Dipakai untuk menutup aplikasi seperti geser di Recents
 * (menghapus task-nya lalu mematikan prosesnya). Tanpa Shizuku, aplikasi biasa tidak bisa
 * mematikan aplikasi lain yang masih tampil di layar.
 */
object Priv {
    const val STATE_NOT_INSTALLED = 0
    const val STATE_NOT_RUNNING = 1
    const val STATE_NO_PERMISSION = 2
    const val STATE_READY = 3

    const val SHIZUKU_PKG = "moe.shizuku.privileged.api"
    const val REQUEST_CODE = 1001

    private fun installed(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo(SHIZUKU_PKG, 0)
        true
    } catch (e: Exception) {
        false
    }

    fun binderAlive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        false
    }

    fun ready(): Boolean = try {
        Shizuku.pingBinder() && !Shizuku.isPreV11() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    fun state(ctx: Context): Int {
        if (!binderAlive()) {
            return if (installed(ctx)) STATE_NOT_RUNNING else STATE_NOT_INSTALLED
        }
        return if (ready()) STATE_READY else STATE_NO_PERMISSION
    }

    fun requestPermission() {
        try {
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (t: Throwable) {
            // diabaikan
        }
    }

    // ------------------------------------------------------------------ refleksi

    private fun field(o: Any, name: String): Any? {
        var c: Class<*>? = o.javaClass
        while (c != null) {
            try {
                val f = c.getDeclaredField(name)
                f.isAccessible = true
                return f.get(o)
            } catch (e: NoSuchFieldException) {
                c = c.superclass
            } catch (t: Throwable) {
                return null
            }
        }
        return null
    }

    private fun wrapped(name: String): IBinder? = try {
        ShizukuBinderWrapper(SystemServiceHelper.getSystemService(name))
    } catch (t: Throwable) {
        null
    }

    private fun taskManager(): Any? {
        val q = Build.VERSION.SDK_INT >= 29
        val b = wrapped(if (q) "activity_task" else "activity") ?: return null
        val stub = Class.forName(
            if (q) "android.app.IActivityTaskManager\$Stub" else "android.app.IActivityManager\$Stub"
        )
        return stub.getMethod("asInterface", IBinder::class.java).invoke(null, b)
    }

    private fun activityManager(): Any? {
        val b = wrapped("activity") ?: return null
        val stub = Class.forName("android.app.IActivityManager\$Stub")
        return stub.getMethod("asInterface", IBinder::class.java).invoke(null, b)
    }

    private class Task(val id: Int, val pkg: String?)

    private fun pkgOf(t: Any): String? {
        val bi = field(t, "baseIntent") as? Intent
        val comp = bi?.component
            ?: field(t, "origActivity") as? ComponentName
            ?: field(t, "baseActivity") as? ComponentName
            ?: field(t, "topActivity") as? ComponentName
        return comp?.packageName ?: bi?.getPackage()
    }

    private fun recentTasks(): List<Task> {
        val svc = taskManager() ?: return emptyList()
        val m = svc.javaClass.getMethod(
            "getRecentTasks",
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
        )
        // flags 3 = ikut tasks yang dikecualikan dari recents + abaikan yang tidak tersedia
        val slice = m.invoke(svc, 100, 3, 0) ?: return emptyList()
        val list = slice.javaClass.getMethod("getList").invoke(slice) as? List<*> ?: return emptyList()
        val out = ArrayList<Task>()
        for (t in list) {
            if (t == null) continue
            val id = (field(t, "taskId") ?: field(t, "persistentId") ?: field(t, "id")) as? Int ?: continue
            out.add(Task(id, pkgOf(t)))
        }
        return out
    }

    /** Paket yang masih punya task (sedang berjalan / ada di recents). null = tidak bisa dicek. */
    fun livePackages(): Set<String>? {
        if (!ready()) return null
        return try {
            val s = HashSet<String>()
            for (t in recentTasks()) t.pkg?.let { s.add(it) }
            s
        } catch (t: Throwable) {
            null
        }
    }

    /** Menghapus semua task milik paket ini, seperti menggeser kartunya di Recents. */
    private fun removeTasks(pkg: String): Int {
        val svc = taskManager() ?: return 0
        val remove = svc.javaClass.getMethod("removeTask", Int::class.javaPrimitiveType)
        var n = 0
        for (t in recentTasks()) {
            if (t.pkg == pkg) {
                try {
                    remove.invoke(svc, t.id)
                    n++
                } catch (e: Throwable) {
                    // lanjut ke task berikutnya
                }
            }
        }
        return n
    }

    private fun forceStop(pkg: String) {
        val am = activityManager() ?: return
        val userId = android.os.Process.myUid() / 100000
        am.javaClass.getMethod(
            "forceStopPackage", String::class.java, Int::class.javaPrimitiveType
        ).invoke(am, pkg, userId)
    }

    /**
     * Menutup aplikasi. Mengembalikan true kalau berhasil lewat Shizuku.
     * Jangan dipanggil dari main thread.
     */
    fun kill(ctx: Context, pkg: String): Boolean {
        if (!ready()) return false
        var ok = false
        var removed = 0
        try {
            removed = removeTasks(pkg)
            ok = removed > 0
        } catch (t: Throwable) {
            // lanjut ke cara berikutnya
        }
        // beri waktu sistem menutup jendelanya, lalu matikan prosesnya
        try {
            Thread.sleep(350)
        } catch (e: InterruptedException) {
            // diabaikan
        }
        try {
            (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).killBackgroundProcesses(pkg)
        } catch (t: Throwable) {
            // diabaikan
        }
        if (removed == 0) {
            // task tidak ditemukan: matikan paksa supaya tetap tertutup
            try {
                forceStop(pkg)
                ok = true
            } catch (t: Throwable) {
                // diabaikan
            }
        }
        return ok
    }
}
