package com.nexora.smartpanel

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    const val MODE_AUTO = 0
    const val MODE_FULLSCREEN = 1
    const val MODE_FREEFORM = 2

    const val SIDE_LEFT = 0
    const val SIDE_RIGHT = 1

    const val THEME_SYSTEM = 0
    const val THEME_BLACK = 1
    const val THEME_WHITE = 2

    private fun sp(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("smart_panel", Context.MODE_PRIVATE)

    fun enabled(c: Context): Boolean = sp(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) {
        sp(c).edit().putBoolean("enabled", v).apply()
    }

    fun side(c: Context): Int = sp(c).getInt("side", SIDE_RIGHT)
    fun setSide(c: Context, v: Int) {
        sp(c).edit().putInt("side", v).apply()
    }

    /** 0 = otomatis (menyesuaikan DPI/ukuran layar). */
    fun widthDp(c: Context): Int = sp(c).getInt("width_dp", 0)
    fun setWidthDp(c: Context, v: Int) {
        sp(c).edit().putInt("width_dp", v).apply()
    }

    fun mode(c: Context): Int = sp(c).getInt("mode", MODE_AUTO)
    fun setMode(c: Context, v: Int) {
        sp(c).edit().putInt("mode", v).apply()
    }

    fun theme(c: Context): Int = sp(c).getInt("theme", THEME_SYSTEM)
    fun setTheme(c: Context, v: Int) {
        sp(c).edit().putInt("theme", v).apply()
    }

    /** Jalan otomatis setelah HP menyala. */
    fun autostart(c: Context): Boolean = sp(c).getBoolean("autostart", true)
    fun setAutostart(c: Context, v: Boolean) {
        sp(c).edit().putBoolean("autostart", v).apply()
    }

    /** Hidupkan lagi otomatis kalau panel ke-kill. */
    fun keepAlive(c: Context): Boolean = sp(c).getBoolean("keep_alive", true)
    fun setKeepAlive(c: Context, v: Boolean) {
        sp(c).edit().putBoolean("keep_alive", v).apply()
    }

    /** Posisi vertikal garis/panel dalam piksel, -1 = belum pernah digeser. */
    fun handleY(c: Context): Int = sp(c).getInt("handle_y", -1)
    fun setHandleY(c: Context, v: Int) {
        sp(c).edit().putInt("handle_y", v).apply()
    }

    // ------------------------------------------------------------------ aplikasi di panel

    fun apps(c: Context): List<String> {
        val raw = sp(c).getString("apps", "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split(",").filter { it.isNotBlank() }
    }

    private fun saveApps(c: Context, l: List<String>) {
        sp(c).edit().putString("apps", l.joinToString(",")).apply()
    }

    fun hasApp(c: Context, pkg: String): Boolean = apps(c).contains(pkg)

    fun addApp(c: Context, pkg: String) {
        val l = apps(c).toMutableList()
        if (!l.contains(pkg)) {
            l.add(pkg)
            saveApps(c, l)
        }
    }

    fun removeApp(c: Context, pkg: String) {
        saveApps(c, apps(c).filter { it != pkg })
        removeRunning(c, pkg)
    }

    // ------------------------------------------------------------------ aplikasi yang sedang berjalan (dibuka lewat panel)

    fun running(c: Context): Set<String> {
        val s = sp(c).getStringSet("running", null) ?: return emptySet()
        return HashSet(s)
    }

    private fun saveRunning(c: Context, s: Set<String>) {
        sp(c).edit().putStringSet("running", HashSet(s)).apply()
    }

    fun addRunning(c: Context, pkg: String) {
        val s = HashSet(running(c))
        s.add(pkg)
        saveRunning(c, s)
    }

    fun removeRunning(c: Context, pkg: String) {
        val s = HashSet(running(c))
        if (s.remove(pkg)) saveRunning(c, s)
    }

    fun setRunning(c: Context, s: Set<String>) {
        saveRunning(c, s)
    }

    fun clearRunning(c: Context) {
        saveRunning(c, emptySet())
    }

    // ------------------------------------------------------------------ lebar

    /** Lebar otomatis: sekitar 16% sisi terpendek layar, dibatasi 52..84 dp. Ikut DPI/ukuran layar. */
    fun autoWidthDp(c: Context): Int {
        val conf = c.resources.configuration
        val base = minOf(conf.screenWidthDp, conf.screenHeightDp)
        return (base * 0.16f).toInt().coerceIn(52, 84)
    }

    fun effectiveWidthDp(c: Context): Int {
        val w = widthDp(c)
        return if (w > 0) w else autoWidthDp(c)
    }
}
