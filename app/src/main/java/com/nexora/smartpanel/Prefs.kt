package com.nexora.smartpanel

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

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


    /** Jumlah baris ikon pada panel (1–3). */
    fun gridRows(c: Context): Int = sp(c).getInt("grid_rows", 2).coerceIn(1, 3)
    fun setGridRows(c: Context, v: Int) { sp(c).edit().putInt("grid_rows", v.coerceIn(1, 3)).apply() }

    /** Tinggi handle vertikal dalam dp (10–50). */
    fun handleHeight(c: Context): Int = sp(c).getInt("handle_height", 30).coerceIn(10, 50)
    fun setHandleHeight(c: Context, v: Int) { sp(c).edit().putInt("handle_height", v.coerceIn(10, 50)).apply() }

    /** Tinggi/lebar strip panel yang dapat diatur (100–220 dp). */
    fun panelSize(c: Context): Int = sp(c).getInt("panel_size", 160).coerceIn(100, 220)
    fun setPanelSize(c: Context, v: Int) { sp(c).edit().putInt("panel_size", v.coerceIn(100, 220)).apply() }


    // ------------------------------------------------------------------ papan klip

    const val CLIP_MAX = 12

    fun clipEnabled(c: Context): Boolean = sp(c).getBoolean("clip_enabled", true)
    fun setClipEnabled(c: Context, v: Boolean) { sp(c).edit().putBoolean("clip_enabled", v).apply() }

    fun clips(c: Context): List<String> {
        val raw = sp(c).getString("clips", "[]") ?: "[]"
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map { a.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveClips(c: Context, l: List<String>) {
        val a = JSONArray()
        l.forEach { a.put(it) }
        sp(c).edit().putString("clips", a.toString()).apply()
    }

    /** Teks terbaru ditaruh paling depan; duplikat dipindah ke depan. */
    fun addClip(c: Context, text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val l = clips(c).filter { it != t }.toMutableList()
        l.add(0, t.take(2000))
        saveClips(c, l.take(CLIP_MAX))
    }

    fun removeClip(c: Context, text: String) {
        saveClips(c, clips(c).filter { it != text })
    }

    /** Posisi vertikal handle dalam persen tinggi layar (0-100). Handle tidak bisa digeser bebas. */
    fun handlePos(c: Context): Int = sp(c).getInt("handle_pos", 25).coerceIn(0, 100)
    fun setHandlePos(c: Context, v: Int) { sp(c).edit().putInt("handle_pos", v.coerceIn(0, 100)).apply() }

    /** Lebar panel 100-220 dp. */
    fun panelWidth(c: Context): Int = effectiveWidthDp(c)

    // ------------------------------------------------------------------ lebar

    /** Lebar otomatis panel grid ala edge panel; sisakan ruang untuk picker di sisi panel. */
    fun autoWidthDp(c: Context): Int {
        val screenW = c.resources.configuration.screenWidthDp.coerceAtLeast(300)
        val fit = (screenW - 92).coerceAtLeast(220)
        return minOf((screenW * 0.48f).toInt().coerceIn(140, 200), fit)
    }

    fun effectiveWidthDp(c: Context): Int {
        val screenW = c.resources.configuration.screenWidthDp.coerceAtLeast(240)
        val fit = (screenW - 12).coerceAtLeast(100)
        val stored = widthDp(c)
        val requested = if (stored > 0) stored.coerceIn(100, 220) else panelSize(c)
        return requested.coerceIn(100, minOf(220, fit))
    }
}
