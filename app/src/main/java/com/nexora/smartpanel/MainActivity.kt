package com.nexora.smartpanel

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku
import kotlin.math.max

class MainActivity : Activity() {

    companion object {
        private const val SAWERIA = "https://saweria.co/nexora1738"
        private const val BREVENT_PKG = "me.piebridge.brevent"
    }

    private lateinit var p: Pal
    private var sv: ScrollView? = null
    private var tutorialAnchor: View? = null

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private val permListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        runOnUiThread { render() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            Shizuku.addRequestPermissionResultListener(permListener)
        } catch (t: Throwable) {
            // Shizuku belum siap; status dicek ulang saat layar dibuka
        }
    }

    override fun onDestroy() {
        try {
            Shizuku.removeRequestPermissionResultListener(permListener)
        } catch (t: Throwable) {
            // diabaikan
        }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        render()
    }

    // ------------------------------------------------------------------ util

    private fun lp(w: Int, h: Int, top: Int = 0): LinearLayout.LayoutParams {
        val l = LinearLayout.LayoutParams(w, h)
        l.topMargin = top
        return l
    }

    private fun hair(col: LinearLayout, top: Int = 0) {
        val v = View(this)
        v.setBackgroundColor(p.line)
        col.addView(v, lp(MATCH, max(1, dp(1) / 2), top))
    }

    /** Judul bagian. Mengembalikan view-nya supaya bisa dijadikan tujuan scroll. */
    private fun section(col: LinearLayout, text: String): View {
        val t = tv(text.uppercase(), 11f, p.sub, true)
        t.letterSpacing = 0.14f
        col.addView(t, lp(MATCH, WRAP, dp(34)))
        hair(col, dp(8))
        return t
    }

    private fun row(
        col: LinearLayout, title: String, desc: String?, trailing: View?,
        onClick: (() -> Unit)? = null
    ): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(0, dp(14), 0, dp(14))
        val left = LinearLayout(this)
        left.orientation = LinearLayout.VERTICAL
        left.addView(tv(title, 15f, p.text, true))
        if (desc != null) left.addView(tv(desc, 12.5f, p.sub), lp(MATCH, WRAP, dp(2)))
        r.addView(left, LinearLayout.LayoutParams(0, WRAP, 1f))
        if (trailing != null) {
            r.addView(trailing, LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(14) })
        }
        if (onClick != null) {
            r.setOnClickListener { onClick() }
            r.pressFade()
        }
        col.addView(r, lp(MATCH, WRAP))
        hair(col)
        return r
    }

    private fun note(col: LinearLayout, text: String, top: Int = 12): TextView {
        val t = tv(text, 12.5f, p.sub)
        col.addView(t, lp(MATCH, WRAP, dp(top)))
        return t
    }

    private fun checked(): View {
        val m = Mark(this, p)
        m.on = true
        m.layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
        return m
    }

    private fun seg(options: List<String>, sel: Int, onSel: (Int) -> Unit): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.HORIZONTAL
        c.background = roundRect(0, dp(20).toFloat(), p.line, max(1, dp(1)))
        c.setPadding(dp(3), dp(3), dp(3), dp(3))
        options.forEachIndexed { i, s ->
            val t = tv(s, 13f, if (i == sel) p.bg else p.text, true)
            t.gravity = Gravity.CENTER
            t.setPadding(dp(14), dp(7), dp(14), dp(7))
            if (i == sel) t.background = roundRect(p.text, dp(17).toFloat())
            t.setOnClickListener { onSel(i) }
            c.addView(t, LinearLayout.LayoutParams(WRAP, WRAP))
        }
        return c
    }

    private fun refreshService() {
        if (Prefs.enabled(this)) {
            startService(Intent(this, PanelService::class.java).setAction(PanelService.ACTION_REFRESH))
        }
    }

    private fun startPanel() {
        val i = Intent(this, PanelService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    private fun notifGranted(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "Tidak bisa membuka tautan", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openStore(pkg: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
        } catch (e: Exception) {
            openUrl("https://play.google.com/store/apps/details?id=$pkg")
        }
    }

    private fun copyText(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("perintah", text))
        Toast.makeText(this, "Disalin", Toast.LENGTH_SHORT).show()
    }

    private fun sideLabel(): String = if (Prefs.side(this) == Prefs.SIDE_RIGHT) "Kanan" else "Kiri"

    private fun modeLabel(): String = when (Prefs.mode(this)) {
        Prefs.MODE_FULLSCREEN -> "Layar penuh"
        Prefs.MODE_FREEFORM -> "Freeform"
        else -> "Auto"
    }

    // ------------------------------------------------------------------ halaman

    private fun render() {
        p = Pal.of(this)
        val keepY = sv?.scrollY ?: 0

        val root = FrameLayout(this)
        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(22), dp(18), dp(22), dp(120))
        scroll.addView(col, ViewGroup.LayoutParams(MATCH, WRAP))
        root.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))

        buildHeader(col)
        buildPermissions(col)
        buildPosition(col)
        buildMode(col)
        buildApps(col)
        buildAuto(col)
        buildKill(col)
        buildTheme(col)
        tutorialAnchor = buildTutorial(col)
        buildDonate(col)

        val ver = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            ""
        }
        val footer = tv("Dibuat oleh Nexora", 13f, p.text, true)
        footer.gravity = Gravity.CENTER
        col.addView(footer, lp(MATCH, WRAP, dp(44)))
        val fv = tv("Smart Panel $ver  ·  Android 7 – 15", 11.5f, p.sub)
        fv.gravity = Gravity.CENTER
        col.addView(fv, lp(MATCH, WRAP, dp(4)))

        val bar = buildBottomBar(scroll)
        root.addView(bar, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(16)
        })

        setContentView(root)
        edgeToEdge(p, root) { l, t, r, b ->
            col.setPadding(dp(22) + l, dp(18) + t, dp(22) + r, dp(120) + b)
            (bar.layoutParams as FrameLayout.LayoutParams).bottomMargin = dp(16) + b
            bar.requestLayout()
        }
        sv = scroll
        scroll.post { scroll.scrollTo(0, keepY) }
    }

    private fun buildHeader(col: LinearLayout) {
        val title = tv("Smart Panel", 34f, p.text, true)
        title.letterSpacing = -0.02f
        col.addView(title, lp(MATCH, WRAP, dp(8)))
        col.addView(tv("Panel aplikasi di tepi layar.", 14f, p.sub), lp(MATCH, WRAP, dp(2)))

        val on = Prefs.enabled(this) && Settings.canDrawOverlays(this)
        hair(col, dp(26))

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(0, dp(18), 0, dp(18))
        val left = LinearLayout(this)
        left.orientation = LinearLayout.VERTICAL
        left.addView(tv(if (on) "Aktif" else "Mati", 24f, p.text, true))
        left.addView(
            tv("${sideLabel()}  ·  ${Prefs.effectiveWidthDp(this)} dp  ·  ${modeLabel()}", 12.5f, p.sub),
            lp(MATCH, WRAP, dp(2))
        )
        head.addView(left, LinearLayout.LayoutParams(0, WRAP, 1f))
        head.addView(Toggle(this, p, on) { checked ->
            if (checked) {
                if (!Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "Izinkan \"Tampil di atas aplikasi lain\" dulu", Toast.LENGTH_LONG).show()
                    openOverlaySettings()
                } else {
                    Prefs.setEnabled(this, true)
                    startPanel()
                }
            } else {
                Prefs.setEnabled(this, false)
                stopService(Intent(this, PanelService::class.java))
            }
            render()
        })
        col.addView(head, lp(MATCH, WRAP))
        hair(col)
        note(col, "Setelah aktif, garis kecil muncul di tepi layar. Geser ke dalam untuk membuka panel. Tekan garis paling atas di panel lalu geser untuk memindahkannya ke kiri atau kanan.", 12)
    }

    private fun buildPermissions(col: LinearLayout) {
        section(col, "Izin")
        row(
            col, "Tampil di atas aplikasi lain", "Wajib, untuk menampilkan panel dan garis di tepi layar.",
            if (Settings.canDrawOverlays(this)) checked()
            else glassBtn(p, "Izinkan") { openOverlaySettings() }
        )
        if (Build.VERSION.SDK_INT >= 33) {
            row(
                col, "Notifikasi", "Agar panel tetap hidup di latar belakang.",
                if (notifGranted()) checked()
                else glassBtn(p, "Izinkan") {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                }
            )
        }
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        row(
            col, "Abaikan hemat baterai", "Opsional, supaya panel tidak dimatikan sistem.",
            if (pm.isIgnoringBatteryOptimizations(packageName)) checked()
            else glassBtn(p, "Atur") {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        )
    }

    private fun buildPosition(col: LinearLayout) {
        section(col, "Posisi dan lebar")
        row(
            col, "Sisi panel", null,
            seg(listOf("Kiri", "Kanan"), if (Prefs.side(this) == Prefs.SIDE_LEFT) 0 else 1) { i ->
                Prefs.setSide(this, if (i == 0) Prefs.SIDE_LEFT else Prefs.SIDE_RIGHT)
                refreshService()
                render()
            }
        )

        val et = EditText(this)
        et.inputType = InputType.TYPE_CLASS_NUMBER
        et.setSingleLine(true)
        et.gravity = Gravity.CENTER
        et.hint = Prefs.autoWidthDp(this).toString()
        et.setTextColor(p.text)
        et.setHintTextColor(p.sub)
        et.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
        et.background = roundRect(0, dp(14).toFloat(), p.line, max(1, dp(1)))
        et.setPadding(dp(12), dp(8), dp(12), dp(8))
        if (Prefs.widthDp(this) > 0) et.setText(Prefs.widthDp(this).toString())
        val auto = Prefs.widthDp(this) <= 0
        row(
            col, "Lebar panel (dp)",
            if (auto) "Otomatis, sekarang ${Prefs.effectiveWidthDp(this)} dp mengikuti DPI layar. Isi angka, misalnya 40, untuk mengunci."
            else "Dikunci ${Prefs.widthDp(this)} dp. Kosongkan lalu pilih Otomatis untuk mengikuti DPI.",
            null
        )
        val line = LinearLayout(this)
        line.orientation = LinearLayout.HORIZONTAL
        line.gravity = Gravity.CENTER_VERTICAL
        line.setPadding(0, dp(12), 0, dp(14))
        line.addView(et, LinearLayout.LayoutParams(dp(84), WRAP))
        line.addView(glassBtn(p, "Simpan", true) {
            val v = et.text.toString().toIntOrNull()
            if (v == null || v <= 0) {
                Toast.makeText(this, "Isi angka dulu, contoh 40", Toast.LENGTH_SHORT).show()
            } else {
                val fixed = v.coerceIn(28, 200)
                Prefs.setWidthDp(this, fixed)
                refreshService()
                Toast.makeText(this, "Lebar panel: $fixed dp", Toast.LENGTH_SHORT).show()
                render()
            }
        }, LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(12) })
        line.addView(glassBtn(p, "Otomatis") {
            Prefs.setWidthDp(this, 0)
            refreshService()
            render()
        }, LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(8) })
        col.addView(line, lp(MATCH, WRAP))
        hair(col)
    }

    private fun choice(
        col: LinearLayout, mode: Int, title: String, desc: String, enabled: Boolean
    ) {
        val sel = Prefs.mode(this) == mode
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(0, dp(14), 0, dp(14))
        r.addView(Radio(this, p, sel), LinearLayout.LayoutParams(dp(20), dp(20)))
        val txt = LinearLayout(this)
        txt.orientation = LinearLayout.VERTICAL
        txt.addView(tv(title, 15f, p.text, true))
        txt.addView(tv(desc, 12.5f, p.sub), lp(MATCH, WRAP, dp(2)))
        r.addView(txt, LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = dp(16) })
        if (enabled) {
            r.setOnClickListener {
                Prefs.setMode(this, mode)
                refreshService()
                render()
            }
            r.pressFade()
        } else {
            r.alpha = 0.4f
            r.setOnClickListener {
                Toast.makeText(this, "Freeform belum aktif di HP ini. Lihat tutorial di bawah.", Toast.LENGTH_LONG).show()
            }
        }
        col.addView(r, lp(MATCH, WRAP))
        hair(col)
    }

    private fun buildMode(col: LinearLayout) {
        section(col, "Mode buka aplikasi")
        val ff = AppLauncher.freeformReady(this)
        choice(
            col, Prefs.MODE_AUTO, "Auto",
            if (ff) "Ketuk ikon untuk layar penuh. Logo freeform kecil di ikon untuk buka mengambang."
            else "Ketuk ikon untuk layar penuh. Logo freeform muncul setelah freeform aktif.",
            true
        )
        choice(col, Prefs.MODE_FULLSCREEN, "Layar penuh", "Ketuk ikon langsung buka layar penuh.", true)
        choice(
            col, Prefs.MODE_FREEFORM, "Freeform",
            if (ff) "Ketuk ikon langsung buka mengambang, tanpa ketuk logo."
            else "Belum aktif di HP ini. Ikuti tutorial Freeform di bawah.",
            ff
        )
        note(col, "Aplikasi yang dibuka lewat panel diberi titik kecil dan tombol × untuk menutupnya.")
    }

    private fun buildApps(col: LinearLayout) {
        val list = Prefs.apps(this)
        section(col, "Aplikasi di panel")
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(0, dp(14), 0, dp(16))
        box.addView(tv("${list.size} aplikasi ditambahkan", 15f, p.text, true))
        if (list.isNotEmpty()) {
            val ic = LinearLayout(this)
            ic.orientation = LinearLayout.HORIZONTAL
            ic.gravity = Gravity.CENTER_VERTICAL
            for (pkg in list.take(7)) {
                try {
                    val iv = ImageView(this)
                    iv.setImageDrawable(packageManager.getApplicationIcon(pkg))
                    ic.addView(iv, LinearLayout.LayoutParams(dp(38), dp(38)).apply { rightMargin = dp(10) })
                } catch (e: Exception) {
                    // aplikasi sudah dihapus
                }
            }
            if (list.size > 7) ic.addView(tv("+${list.size - 7}", 14f, p.sub, true))
            box.addView(ic, lp(WRAP, WRAP, dp(12)))
        }
        box.addView(
            glassBtn(p, "Tambah aplikasi", true) {
                startActivity(Intent(this, AddAppsActivity::class.java))
            },
            lp(WRAP, WRAP, dp(14))
        )
        col.addView(box, lp(MATCH, WRAP))
        hair(col)
        note(col, "Bisa juga lewat tombol + di dalam panel: daftar aplikasi muncul mengambang di samping panel tanpa keluar dari aplikasi yang sedang dipakai.")
    }

    private fun buildAuto(col: LinearLayout) {
        section(col, "Otomatis")
        row(
            col, "Jalan saat HP menyala", "Panel hidup sendiri setelah HP restart.",
            Toggle(this, p, Prefs.autostart(this)) { Prefs.setAutostart(this, it) }
        )
        row(
            col, "Hidupkan lagi kalau dimatikan",
            "Kalau panel ke-kill atau Smart Panel dihapus dari Recents, panel nyala lagi sendiri.",
            Toggle(this, p, Prefs.keepAlive(this)) { Prefs.setKeepAlive(this, it) }
        )
        note(col, "Di beberapa HP (Tecno, Infinix, itel, Xiaomi, Oppo, Vivo) izinkan juga \"Mulai otomatis\" atau \"Autostart\" untuk Smart Panel di pengaturan HP. Tanpa itu sistem bisa menahannya.")
        col.addView(
            glassBtn(p, "Buka info aplikasi") {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                )
            },
            lp(WRAP, WRAP, dp(12))
        )
    }

    private fun buildKill(col: LinearLayout) {
        section(col, "Tutup aplikasi (kill)")
        when (Priv.state(this)) {
            Priv.STATE_READY -> row(
                col, "Kill seperti Recents",
                "Aktif. Tombol × di panel menghapus aplikasi dari Recents lalu mematikannya.",
                checked()
            )
            Priv.STATE_NO_PERMISSION -> row(
                col, "Izinkan Smart Panel di Shizuku",
                "Shizuku sudah jalan. Beri izin supaya tombol × bisa menutup aplikasi.",
                glassBtn(p, "Izinkan", true) { Priv.requestPermission() }
            )
            Priv.STATE_NOT_RUNNING -> row(
                col, "Jalankan Shizuku",
                "Shizuku sudah terpasang tapi belum aktif. Buka Shizuku lalu mulai lewat Debugging nirkabel.",
                glassBtn(p, "Buka", true) {
                    val i = packageManager.getLaunchIntentForPackage(Priv.SHIZUKU_PKG)
                    if (i != null) startActivity(i) else openStore(Priv.SHIZUKU_PKG)
                }
            )
            else -> row(
                col, "Pasang Shizuku",
                "Agar tombol × bisa menutup aplikasi seperti geser di Recents, dibutuhkan Shizuku (gratis).",
                glassBtn(p, "Unduh", true) { openStore(Priv.SHIZUKU_PKG) }
            )
        }
        note(
            col,
            "Android biasa tidak mengizinkan aplikasi menutup aplikasi lain yang masih tampil. Shizuku memberi izin itu lewat Debugging nirkabel (Android 11 ke atas), caranya sama seperti Brevent. Android 7 sampai 10 harus dijalankan lewat komputer (adb). Tanpa Shizuku, tombol × hanya bisa mematikan aplikasi yang sudah di latar belakang."
        )
    }

    private fun buildTheme(col: LinearLayout) {
        section(col, "Tampilan")
        val r = LinearLayout(this)
        r.orientation = LinearLayout.VERTICAL
        r.setPadding(0, dp(14), 0, dp(16))
        r.addView(tv("Tema", 15f, p.text, true))
        r.addView(tv("Hitam dan putih saja. Ikut HP mengikuti mode gelap.", 12.5f, p.sub), lp(MATCH, WRAP, dp(2)))
        r.addView(
            seg(listOf("Ikut HP", "Hitam", "Putih"), Prefs.theme(this)) { i ->
                Prefs.setTheme(this, i)
                refreshService()
                render()
            },
            lp(WRAP, WRAP, dp(12))
        )
        col.addView(r, lp(MATCH, WRAP))
        hair(col)
    }

    private fun stepRow(col: LinearLayout, n: Int, text: String, extra: View? = null) {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        val num = tv(n.toString(), 12f, p.text, true)
        num.gravity = Gravity.CENTER
        num.background = ovalBg(0, p.sub, max(1, dp(1)))
        r.addView(num, LinearLayout.LayoutParams(dp(24), dp(24)))
        val txt = LinearLayout(this)
        txt.orientation = LinearLayout.VERTICAL
        txt.addView(tv(text, 13.5f, p.text))
        if (extra != null) txt.addView(extra, lp(WRAP, WRAP, dp(10)))
        r.addView(txt, LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = dp(14) })
        col.addView(r, lp(MATCH, WRAP, dp(18)))
    }

    private fun codeBlock(text: String): View {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        val box = tv(text, 12.5f, p.text)
        box.typeface = Typeface.MONOSPACE
        box.setTextIsSelectable(true)
        box.setPadding(dp(14), dp(12), dp(14), dp(12))
        box.background = roundRect(p.surface, dp(14).toFloat(), p.line, max(1, dp(1)))
        c.addView(box, lp(MATCH, WRAP))
        c.addView(glassBtn(p, "Salin") { copyText(text) }, lp(WRAP, WRAP, dp(8)))
        return c
    }

    private fun buildTutorial(col: LinearLayout): View {
        val anchor = section(col, "Aktifkan Freeform")
        val ff = AppLauncher.freeformReady(this)

        val status = LinearLayout(this)
        status.orientation = LinearLayout.HORIZONTAL
        status.gravity = Gravity.CENTER_VERTICAL
        status.setPadding(0, dp(14), 0, dp(14))
        val s1 = LinearLayout(this)
        s1.orientation = LinearLayout.VERTICAL
        s1.addView(tv(if (ff) "Freeform aktif di HP ini" else "Freeform belum aktif di HP ini", 15f, p.text, true))
        s1.addView(
            tv(
                if (ff) "Mode Freeform dan logo freeform di ikon sudah bisa dipakai."
                else "Mode Freeform belum bisa dipakai. Ikuti langkah di bawah.",
                12.5f, p.sub
            ),
            lp(MATCH, WRAP, dp(2))
        )
        status.addView(s1, LinearLayout.LayoutParams(0, WRAP, 1f))
        if (ff) status.addView(checked())
        col.addView(status, lp(MATCH, WRAP))
        hair(col)

        note(col, "Untuk HP yang belum mendukung freeform (Android 7 sampai 15):", 14)
        stepRow(
            col, 1, "Unduh Brevent dari Play Store.",
            glassBtn(p, "Buka Play Store", true) { openStore(BREVENT_PKG) }
        )
        stepRow(
            col, 2,
            "Buka Brevent, lalu hubungkan lewat debugging nirkabel: Pengaturan > Opsi pengembang > Debugging nirkabel > nyalakan, lalu ikuti petunjuk pairing di Brevent sampai statusnya terhubung."
        )
        stepRow(
            col, 3,
            "Di Brevent buka menu titik tiga di pojok kanan atas, cari menu untuk menjalankan perintah (shell), lalu masukkan dua kode ini satu per satu. Nama menunya bisa sedikit beda tergantung versi Brevent."
        )
        val codes = LinearLayout(this)
        codes.orientation = LinearLayout.VERTICAL
        codes.addView(codeBlock("settings put global enable_freeform_support 1"), lp(MATCH, WRAP))
        codes.addView(codeBlock("settings put global force_resizable_activities 1"), lp(MATCH, WRAP, dp(10)))
        col.addView(codes, lp(MATCH, WRAP, dp(12)).apply { leftMargin = dp(38) })
        stepRow(col, 4, "Restart HP.")
        stepRow(col, 5, "Buka Smart Panel lagi. Status di atas berubah menjadi \"Freeform aktif\".")
        note(
            col,
            "Kalau setelah semua langkah di atas freeform tetap belum aktif, berarti HP kamu memang tidak mendukung freeform. Mode Freeform tidak bisa dipakai, tapi mode Auto dan Layar penuh tetap jalan.",
            20
        )
        return anchor
    }

    private fun buildDonate(col: LinearLayout) {
        section(col, "Donasi")
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(0, dp(14), 0, dp(16))
        box.addView(tv("Smart Panel gratis.", 15f, p.text, true))
        box.addView(
            tv("Kalau terbantu, dukung pengembangannya lewat Saweria.", 12.5f, p.sub),
            lp(MATCH, WRAP, dp(2))
        )
        box.addView(glassBtn(p, "Donasi via Saweria", true) { openUrl(SAWERIA) }, lp(WRAP, WRAP, dp(14)))
        col.addView(box, lp(MATCH, WRAP))
        hair(col)
    }

    private fun buildBottomBar(scroll: ScrollView): View {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.background = GlassDrawable(p, dp(30).toFloat(), if (p.dark) 0xB4 else 0xE2, max(1, dp(1)).toFloat())
        bar.setPadding(dp(8), dp(6), dp(8), dp(6))

        fun item(label: String, last: Boolean, onClick: () -> Unit) {
            val t = tv(label, 14f, p.text, true)
            t.gravity = Gravity.CENTER
            t.setPadding(dp(20), dp(12), dp(20), dp(12))
            t.setOnClickListener { onClick() }
            t.pressFade()
            bar.addView(t, LinearLayout.LayoutParams(WRAP, WRAP))
            if (!last) {
                val d = View(this)
                d.setBackgroundColor(p.line)
                bar.addView(d, LinearLayout.LayoutParams(max(1, dp(1) / 2), dp(16)))
            }
        }
        item("Tambah", false) { startActivity(Intent(this, AddAppsActivity::class.java)) }
        item("Tutorial", false) {
            val a = tutorialAnchor
            if (a != null) scroll.smoothScrollTo(0, max(0, a.top - dp(16)))
        }
        item("Donasi", true) { openUrl(SAWERIA) }
        return bar
    }
}
