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

    private fun hairPx(): Int = max(1, dp(1) / 2)

    /** Judul kelompok (kecil, abu-abu) lalu kartu bulat berisi baris-baris pengaturan. */
    private fun group(col: LinearLayout, title: String?): LinearLayout {
        var top = dp(10)
        if (title != null) {
            val t = tv(title, 12.5f, p.sub, true)
            t.setPadding(dp(4), 0, 0, 0)
            col.addView(t, lp(MATCH, WRAP, dp(16)))
            top = dp(7)
        }
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.isBaselineAligned = false
        c.setPadding(0, 0, 0, 0)
        c.background = roundRect(p.surface, dp(20).toFloat(), p.line, hairPx())
        c.layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = top }
        col.addView(c)
        return c
    }

    /** Garis pemisah di dalam kartu (tidak sampai ke tepi). */
    private fun divider(card: LinearLayout) {
        val v = View(this)
        v.setBackgroundColor(p.line)
        val l = lp(MATCH, hairPx())
        l.leftMargin = dp(16)
        l.rightMargin = dp(16)
        card.addView(v, l)
    }

    private fun row(
        card: LinearLayout, title: String, desc: String?, trailing: View?,
        onClick: (() -> Unit)? = null
    ): LinearLayout {
        if (card.childCount > 0) divider(card)
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.minimumHeight = dp(64)
        r.setPadding(dp(16), dp(11), dp(16), dp(11))
        val left = LinearLayout(this)
        left.orientation = LinearLayout.VERTICAL
        left.addView(tv(title, 14.5f, p.text, true))
        if (desc != null) left.addView(tv(desc, 12.5f, p.sub), lp(MATCH, WRAP, dp(3)))
        r.addView(left, LinearLayout.LayoutParams(0, WRAP, 1f))
        if (trailing != null) r.addView(trailing, LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(12) })
        if (onClick != null) {
            r.setOnClickListener { onClick() }
            r.pressFade()
        }
        card.addView(r, LinearLayout.LayoutParams(MATCH, WRAP))
        return r
    }


    /** Baris izin dengan ukuran konten eksplisit; tidak memakai tinggi layar. */
    private fun permissionRow(card: LinearLayout, title: String, desc: String, granted: Boolean, actionLabel: String, onAction: () -> Unit) {
        if (card.childCount > 0) divider(card)
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.minimumHeight = dp(72)
        r.setPadding(dp(16), dp(10), dp(16), dp(10))
        val labels = LinearLayout(this)
        labels.orientation = LinearLayout.VERTICAL
        labels.addView(tv(title, 14f, p.text, true))
        labels.addView(tv(desc, 12f, p.sub), lp(MATCH, WRAP, dp(3)))
        r.addView(labels, LinearLayout.LayoutParams(0, WRAP, 1f))
        val action: View = if (granted) {
            TextView(this).apply {
                text = "✓"; textSize = 17f; typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER; setTextColor(p.bg); background = ovalBg(p.text)
            }
        } else solidButton(p, actionLabel, true, onAction)
        val actionSize = if (granted) LinearLayout.LayoutParams(dp(30), dp(30)) else LinearLayout.LayoutParams(WRAP, dp(38))
        actionSize.leftMargin = dp(10)
        r.addView(action, actionSize)
        card.addView(r, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    /** Baris bebas (isi sendiri) di dalam kartu. */
    private fun free(card: LinearLayout, content: View) {
        if (card.childCount > 0) divider(card)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(13), dp(16), dp(14))
        box.addView(content, lp(MATCH, WRAP))
        card.addView(box, lp(MATCH, WRAP))
    }

    private fun note(col: LinearLayout, text: String, top: Int = 12): TextView {
        val t = tv(text, 12.5f, p.sub)
        t.setPadding(dp(8), 0, dp(8), 0)
        t.setLineSpacing(0f, 1.1f)
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
        c.background = roundRect(p.bg, dp(20).toFloat())
        c.setPadding(dp(3), dp(3), dp(3), dp(3))
        options.forEachIndexed { i, s ->
            val t = tv(s, 13f, if (i == sel) p.bg else p.sub, true)
            t.gravity = Gravity.CENTER
            t.setPadding(dp(14), dp(8), dp(14), dp(8))
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
        val root = FrameLayout(this).apply { setBackgroundColor(p.bg) }
        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        scroll.clipToPadding = false
        scroll.isFillViewport = false
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.isBaselineAligned = false
        col.setPadding(dp(18), dp(12), dp(18), dp(92))
        scroll.addView(col, LinearLayout.LayoutParams(MATCH, WRAP))
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

        val ver = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "" }
        val footer = tv("Dibuat oleh Nexora", 12.5f, p.text, true)
        footer.gravity = Gravity.CENTER
        col.addView(footer, lp(MATCH, WRAP, dp(30)))
        val fv = tv("Smart Panel $ver · Android 7–15", 11f, p.sub)
        fv.gravity = Gravity.CENTER
        col.addView(fv, lp(MATCH, WRAP, dp(3)))

        val bar = buildBottomBar(scroll)
        root.addView(bar, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(10)
        })
        setContentView(root)
        edgeToEdge(p, root) { l, t, r, b ->
            col.setPadding(dp(18) + l, dp(10) + t, dp(18) + r, dp(92) + b)
            (bar.layoutParams as FrameLayout.LayoutParams).bottomMargin = dp(10) + b
            bar.requestLayout()
        }
        sv = scroll
        scroll.post { scroll.scrollTo(0, keepY) }
    }

    private fun buildHeader(col: LinearLayout) {
        val title = serif("Smart Panel", 31f, p.text)
        col.addView(title, lp(MATCH, WRAP, dp(8)))
        col.addView(tv("Panel aplikasi bergaya edge panel", 13f, p.sub), lp(MATCH, WRAP, dp(2)))
        val on = Prefs.enabled(this) && Settings.canDrawOverlays(this)
        val card = group(col, null)
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.minimumHeight = dp(82)
        head.setPadding(dp(17), dp(12), dp(17), dp(12))
        val left = LinearLayout(this)
        left.orientation = LinearLayout.VERTICAL
        left.addView(serif(if (on) "Aktif" else "Mati", 23f, p.text))
        left.addView(tv("${sideLabel()} · ${Prefs.effectiveWidthDp(this)} dp · ${modeLabel()}", 12f, p.sub), lp(MATCH, WRAP, dp(2)))
        head.addView(left, LinearLayout.LayoutParams(0, WRAP, 1f))
        head.addView(Toggle(this, p, on) { checked ->
            if (checked) {
                if (!Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "Izinkan Tampil di atas aplikasi lain dulu", Toast.LENGTH_LONG).show()
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
        card.addView(head, LinearLayout.LayoutParams(MATCH, WRAP))
        note(col, "Geser garis di tepi layar ke arah dalam untuk membuka panel grid aplikasi. Tahan garis atas panel untuk memindahkan posisinya.", 9)
    }

    private fun buildPermissions(col: LinearLayout) {
        val c = group(col, "IZIN")
        permissionRow(c, "Tampil di atas aplikasi lain", "Wajib untuk menampilkan panel di atas aplikasi.", Settings.canDrawOverlays(this), "Izinkan") {
            openOverlaySettings()
        }
        if (Build.VERSION.SDK_INT >= 33) {
            permissionRow(c, "Notifikasi", "Menjaga layanan panel tetap aktif.", notifGranted(), "Izinkan") {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        permissionRow(c, "Abaikan hemat baterai", "Opsional agar sistem tidak mudah menghentikan panel.", pm.isIgnoringBatteryOptimizations(packageName), "Atur") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun buildPosition(col: LinearLayout) {
        val c = group(col, "Posisi dan lebar")
        row(
            c, "Sisi panel", null,
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
        et.background = roundRect(p.bg, dp(16).toFloat())
        et.setPadding(dp(12), dp(10), dp(12), dp(10))
        if (Prefs.widthDp(this) > 0) et.setText(Prefs.widthDp(this).toString())
        val auto = Prefs.widthDp(this) <= 0

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.addView(tv("Lebar panel (dp)", 15.5f, p.text, true))
        box.addView(
            tv(
                if (auto) "Otomatis, sekarang ${Prefs.effectiveWidthDp(this)} dp. Isi angka 220–380 untuk mengunci lebar kartu panel."
                else "Target ${Prefs.widthDp(this)} dp (menyesuaikan lebar layar). Pilih Otomatis untuk kembali ke ukuran rekomendasi.",
                13f, p.sub
            ),
            lp(MATCH, WRAP, dp(3))
        )
        val line = LinearLayout(this)
        line.orientation = LinearLayout.HORIZONTAL
        line.gravity = Gravity.CENTER_VERTICAL
        line.addView(et, LinearLayout.LayoutParams(dp(84), WRAP))
        line.addView(solidButton(p, "Simpan", true) {
            val v = et.text.toString().toIntOrNull()
            if (v == null || v <= 0) {
                Toast.makeText(this, "Isi angka 220–380 dp", Toast.LENGTH_SHORT).show()
            } else {
                val fixed = v.coerceIn(220, 380)
                Prefs.setWidthDp(this, fixed)
                refreshService()
                Toast.makeText(this, "Lebar panel: $fixed dp", Toast.LENGTH_SHORT).show()
                render()
            }
        }, LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(12) })
        line.addView(solidButton(p, "Otomatis") {
            Prefs.setWidthDp(this, 0)
            refreshService()
            render()
        }, LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(8) })
        box.addView(line, lp(MATCH, WRAP, dp(14)))
        free(c, box)
    }

    private fun choice(
        card: LinearLayout, mode: Int, title: String, desc: String, enabled: Boolean
    ) {
        if (card.childCount > 0) divider(card)
        val sel = Prefs.mode(this) == mode
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(dp(20), dp(16), dp(20), dp(16))
        r.addView(Radio(this, p, sel), LinearLayout.LayoutParams(dp(20), dp(20)))
        val txt = LinearLayout(this)
        txt.orientation = LinearLayout.VERTICAL
        txt.addView(tv(title, 15.5f, p.text, true))
        txt.addView(tv(desc, 13f, p.sub), lp(MATCH, WRAP, dp(3)))
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
        card.addView(r, lp(MATCH, WRAP))
    }

    private fun buildMode(col: LinearLayout) {
        val c = group(col, "Cara buka aplikasi")
        val ff = AppLauncher.freeformReady(this)
        choice(
            c, Prefs.MODE_AUTO, "Auto",
            if (ff) "Ketuk ikon untuk layar penuh. Logo freeform kecil di ikon untuk buka mengambang."
            else "Ketuk ikon untuk layar penuh. Logo freeform muncul setelah freeform aktif.",
            true
        )
        choice(c, Prefs.MODE_FULLSCREEN, "Layar penuh", "Ketuk ikon langsung buka layar penuh.", true)
        choice(
            c, Prefs.MODE_FREEFORM, "Freeform",
            if (ff) "Ketuk ikon langsung buka mengambang, tanpa ketuk logo."
            else "Belum aktif di HP ini. Ikuti tutorial Freeform di bawah.",
            ff
        )
        note(col, "Aplikasi yang dibuka lewat panel diberi titik kecil dan tombol × untuk menutupnya.")
    }

    private fun buildApps(col: LinearLayout) {
        val list = Prefs.apps(this)
        val c = group(col, "Aplikasi di panel")
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.addView(tv("${list.size} aplikasi ditambahkan", 15.5f, p.text, true))
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
            box.addView(ic, lp(WRAP, WRAP, dp(14)))
        }
        box.addView(
            solidButton(p, "Tambah aplikasi", true) {
                startActivity(Intent(this, AddAppsActivity::class.java))
            },
            lp(WRAP, WRAP, dp(16))
        )
        free(c, box)
        note(col, "Bisa juga lewat tombol + di dalam panel: daftar kecil muncul di samping panel tanpa keluar dari aplikasi yang sedang dipakai.")
    }

    private fun buildAuto(col: LinearLayout) {
        val c = group(col, "Otomatis")
        row(
            c, "Jalan saat HP menyala", "Panel hidup sendiri setelah HP restart.",
            Toggle(this, p, Prefs.autostart(this)) { Prefs.setAutostart(this, it) }
        )
        row(
            c, "Hidupkan lagi kalau dimatikan",
            "Kalau panel ke-kill atau Smart Panel dihapus dari Recents, panel nyala lagi sendiri.",
            Toggle(this, p, Prefs.keepAlive(this)) { Prefs.setKeepAlive(this, it) }
        )
        row(
            c, "Mulai otomatis di pengaturan HP",
            "Di Tecno, Infinix, itel, Xiaomi, Oppo, Vivo izinkan juga \"Autostart\" untuk Smart Panel.",
            solidButton(p, "Buka") {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                )
            }
        )
    }

    private fun buildKill(col: LinearLayout) {
        val c = group(col, "Tutup aplikasi (kill)")
        when (Priv.state(this)) {
            Priv.STATE_READY -> row(
                c, "Kill seperti Recents",
                "Aktif. Tombol × di panel menghapus aplikasi dari Recents lalu mematikannya.",
                checked()
            )
            Priv.STATE_NO_PERMISSION -> row(
                c, "Izinkan Smart Panel di Shizuku",
                "Shizuku sudah jalan. Beri izin supaya tombol × bisa menutup aplikasi.",
                solidButton(p, "Izinkan", true) { Priv.requestPermission() }
            )
            Priv.STATE_NOT_RUNNING -> row(
                c, "Jalankan Shizuku",
                "Shizuku sudah terpasang tapi belum aktif. Buka Shizuku lalu mulai lewat Debugging nirkabel.",
                solidButton(p, "Buka", true) {
                    val i = packageManager.getLaunchIntentForPackage(Priv.SHIZUKU_PKG)
                    if (i != null) startActivity(i) else openStore(Priv.SHIZUKU_PKG)
                }
            )
            else -> row(
                c, "Pasang Shizuku",
                "Agar tombol × bisa menutup aplikasi seperti geser di Recents, dibutuhkan Shizuku (gratis).",
                solidButton(p, "Unduh", true) { openStore(Priv.SHIZUKU_PKG) }
            )
        }
        note(
            col,
            "Android biasa tidak mengizinkan aplikasi menutup aplikasi lain yang masih tampil. Shizuku memberi izin itu lewat Debugging nirkabel (Android 11 ke atas), caranya sama seperti Brevent. Android 7 sampai 10 harus dijalankan lewat komputer (adb). Tanpa Shizuku, tombol × hanya bisa mematikan aplikasi yang sudah di latar belakang."
        )
    }

    private fun buildTheme(col: LinearLayout) {
        val c = group(col, "TAMPILAN")
        val badge = tv("OTOMATIS", 11f, p.bg, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = roundRect(p.text, dp(10).toFloat())
        }
        row(c, "Tema sistem", "Mengikuti mode terang atau gelap Android secara otomatis.", badge)
    }

    private fun stepRow(parent: LinearLayout, n: Int, text: String, top: Int, extra: View? = null) {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        val num = tv(n.toString(), 12f, p.text, true)
        num.gravity = Gravity.CENTER
        num.background = ovalBg(p.bg)
        r.addView(num, LinearLayout.LayoutParams(dp(26), dp(26)))
        val txt = LinearLayout(this)
        txt.orientation = LinearLayout.VERTICAL
        txt.addView(tv(text, 13.5f, p.text))
        if (extra != null) txt.addView(extra, lp(WRAP, WRAP, dp(12)))
        r.addView(txt, LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = dp(14) })
        parent.addView(r, lp(MATCH, WRAP, top))
    }

    private fun codeBlock(text: String): View {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        val box = tv(text, 12.5f, p.text)
        box.typeface = Typeface.MONOSPACE
        box.setTextIsSelectable(true)
        box.setPadding(dp(14), dp(12), dp(14), dp(12))
        box.background = roundRect(p.bg, dp(14).toFloat())
        c.addView(box, lp(MATCH, WRAP))
        c.addView(solidButton(p, "Salin") { copyText(text) }, lp(WRAP, WRAP, dp(8)))
        return c
    }

    private fun buildTutorial(col: LinearLayout): View {
        val ff = AppLauncher.freeformReady(this)

        val label = tv("Aktifkan Freeform", 13f, p.sub, true)
        label.setPadding(dp(8), 0, 0, 0)
        col.addView(label, lp(MATCH, WRAP, dp(28)))
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = roundRect(p.surface, dp(26).toFloat(), p.line, hairPx())
        col.addView(c, lp(MATCH, WRAP, dp(10)))

        val s1 = LinearLayout(this)
        s1.orientation = LinearLayout.VERTICAL
        s1.addView(tv(if (ff) "Freeform aktif di HP ini" else "Freeform belum aktif di HP ini", 15.5f, p.text, true))
        s1.addView(
            tv(
                if (ff) "Mode Freeform dan logo freeform di ikon sudah bisa dipakai."
                else "Mode Freeform belum bisa dipakai. Ikuti langkah di bawah.",
                13f, p.sub
            ),
            lp(MATCH, WRAP, dp(3))
        )
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(s1, LinearLayout.LayoutParams(0, WRAP, 1f))
        if (ff) head.addView(checked())
        free(c, head)

        val steps = LinearLayout(this)
        steps.orientation = LinearLayout.VERTICAL
        steps.addView(tv("Untuk HP yang belum mendukung freeform (Android 7 sampai 15):", 13f, p.sub))
        stepRow(
            steps, 1, "Unduh Brevent dari Play Store.", dp(18),
            solidButton(p, "Buka Play Store", true) { openStore(BREVENT_PKG) }
        )
        stepRow(
            steps, 2,
            "Buka Brevent, lalu hubungkan lewat debugging nirkabel: Pengaturan > Opsi pengembang > Debugging nirkabel > nyalakan, lalu ikuti petunjuk pairing di Brevent sampai statusnya terhubung.",
            dp(18)
        )
        val codes = LinearLayout(this)
        codes.orientation = LinearLayout.VERTICAL
        codes.addView(codeBlock("settings put global enable_freeform_support 1"), lp(MATCH, WRAP))
        codes.addView(codeBlock("settings put global force_resizable_activities 1"), lp(MATCH, WRAP, dp(10)))
        stepRow(
            steps, 3,
            "Di Brevent buka menu titik tiga di pojok kanan atas, cari menu untuk menjalankan perintah (shell), lalu masukkan dua kode ini satu per satu. Nama menunya bisa sedikit beda tergantung versi Brevent.",
            dp(18), codes
        )
        stepRow(steps, 4, "Restart HP.", dp(18))
        stepRow(steps, 5, "Buka Smart Panel lagi. Status di atas berubah menjadi \"Freeform aktif\".", dp(18))
        free(c, steps)

        note(
            col,
            "Kalau setelah semua langkah di atas freeform tetap belum aktif, berarti HP kamu memang tidak mendukung freeform. Mode Freeform tidak bisa dipakai, tapi mode Auto dan Layar penuh tetap jalan.",
            14
        )
        return label
    }

    private fun buildDonate(col: LinearLayout) {
        val c = group(col, "Dukung Nexora")
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.addView(tv("Smart Panel gratis.", 15.5f, p.text, true))
        box.addView(
            tv("Kalau terbantu, dukung pengembangannya lewat Saweria.", 13f, p.sub),
            lp(MATCH, WRAP, dp(3))
        )
        box.addView(solidButton(p, "Donasi via Saweria", true) { openUrl(SAWERIA) }, lp(WRAP, WRAP, dp(16)))
        free(c, box)
    }

    private fun buildBottomBar(scroll: ScrollView): View {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.background = roundRect(p.surface, dp(22).toFloat(), p.line, max(1, dp(1)))
        bar.setPadding(dp(5), dp(4), dp(5), dp(4))
        fun item(label: String, last: Boolean, onClick: () -> Unit) {
            val t = tv(label, 12.5f, p.text, true)
            t.gravity = Gravity.CENTER
            t.setPadding(dp(15), dp(10), dp(15), dp(10))
            t.setOnClickListener { onClick() }
            t.pressFade()
            bar.addView(t, LinearLayout.LayoutParams(WRAP, WRAP))
            if (!last) {
                val d = View(this)
                d.setBackgroundColor(p.line)
                bar.addView(d, LinearLayout.LayoutParams(max(1, hairPx()), dp(17)))
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
