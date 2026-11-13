package com.nexora.smartpanel

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku
import kotlin.math.max

class MainActivity : Activity() {

    companion object {
        private const val SAWERIA = "https://saweria.co/nexora1738"
        private const val BREVENT_PKG = "me.piebridge.brevent"
        private val TABS = listOf("Utama", "Panel", "Aplikasi", "Bantuan")
    }

    private lateinit var p: Pal
    private var sv: ScrollView? = null
    private var tab = 0
    private var renderedTab = -1

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


    /** Slider dengan nilai di kanan judul. Nilai disimpan saat jari dilepas. */
    private fun slider(
        card: LinearLayout, title: String, min: Int, max: Int, value: Int, unit: String,
        onCommit: (Int) -> Unit
    ) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(tv(title, 14.5f, p.text, true), LinearLayout.LayoutParams(0, WRAP, 1f))
        val valueText = tv("${value.coerceIn(min, max)} $unit", 13f, p.sub, true)
        head.addView(valueText, LinearLayout.LayoutParams(WRAP, WRAP))
        box.addView(head, lp(MATCH, WRAP))

        val sb = SeekBar(this)
        sb.max = max - min
        sb.progress = value.coerceIn(min, max) - min
        sb.progressTintList = ColorStateList.valueOf(p.text)
        sb.thumbTintList = ColorStateList.valueOf(p.text)
        sb.progressBackgroundTintList = ColorStateList.valueOf(0xFFD0D0D0.toInt())
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, progress: Int, fromUser: Boolean) {
                valueText.text = "${progress + min} $unit"
            }

            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {
                onCommit(s.progress + min)
            }
        })
        box.addView(sb, lp(MATCH, WRAP, dp(6)))
        free(card, box)
    }

    // ------------------------------------------------------------------ halaman

    private fun render() {
        p = Pal.of(this)
        val keepY = if (renderedTab == tab) (sv?.scrollY ?: 0) else 0
        renderedTab = tab
        val root = FrameLayout(this).apply { setBackgroundColor(p.bg) }
        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        scroll.clipToPadding = false
        scroll.isFillViewport = false
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.isBaselineAligned = false
        col.setPadding(dp(18), dp(12), dp(18), dp(84))
        scroll.addView(col, LinearLayout.LayoutParams(MATCH, WRAP))
        root.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))

        when (tab) {
            0 -> buildHome(col)
            1 -> buildPanelTab(col)
            2 -> buildAppsTab(col)
            else -> buildHelp(col)
        }

        val nav = buildNav()
        root.addView(nav, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))
        setContentView(root)
        edgeToEdge(p, root) { l, t, r, b ->
            col.setPadding(dp(18) + l, dp(10) + t, dp(18) + r, dp(84) + b)
            nav.setPadding(l, 0, r, b)
        }
        sv = scroll
        scroll.post { scroll.scrollTo(0, keepY) }
    }

    private fun title(col: LinearLayout, text: String, sub: String) {
        col.addView(serif(text, 28f, p.text), lp(MATCH, WRAP, dp(8)))
        col.addView(tv(sub, 13f, p.sub), lp(MATCH, WRAP, dp(2)))
    }

    // ---- Tab 1: Utama

    private fun buildHome(col: LinearLayout) {
        title(col, "Smart Panel", "Panel aplikasi minimalis putih-hitam")
        val on = Prefs.enabled(this) && Settings.canDrawOverlays(this)
        val card = group(col, null)
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.minimumHeight = dp(78)
        head.setPadding(dp(17), dp(12), dp(17), dp(12))
        val left = LinearLayout(this)
        left.orientation = LinearLayout.VERTICAL
        left.addView(serif(if (on) "Aktif" else "Mati", 22f, p.text))
        left.addView(
            tv("${sideLabel()} · ${Prefs.effectiveWidthDp(this)} dp · ${Prefs.gridRows(this)} baris", 12f, p.sub),
            lp(MATCH, WRAP, dp(2))
        )
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
        note(col, "Geser garis di tepi layar ke dalam untuk membuka panel. Di panel, geser garis \"--\" di atas ke kiri atau kanan untuk pindah sisi.", 9)

        val c = group(col, "Izin")
        permissionRow(c, "Tampil di atas aplikasi lain", "Wajib untuk menampilkan panel.", Settings.canDrawOverlays(this), "Izinkan") {
            openOverlaySettings()
        }
        if (Build.VERSION.SDK_INT >= 33) {
            permissionRow(c, "Notifikasi", "Menjaga layanan panel tetap aktif.", notifGranted(), "Izinkan") {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        permissionRow(c, "Abaikan hemat baterai", "Opsional, agar panel tidak mudah dihentikan.", pm.isIgnoringBatteryOptimizations(packageName), "Atur") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }

        val a = group(col, "Otomatis")
        row(
            a, "Jalan saat HP menyala", "Panel hidup sendiri setelah restart.",
            Toggle(this, p, Prefs.autostart(this)) { Prefs.setAutostart(this, it) }
        )
        row(
            a, "Hidupkan lagi kalau dimatikan", "Panel nyala lagi kalau ke-kill atau dihapus dari Recents.",
            Toggle(this, p, Prefs.keepAlive(this)) { Prefs.setKeepAlive(this, it) }
        )
        row(
            a, "Autostart di pengaturan HP", "Tecno, Infinix, itel, Xiaomi, Oppo, Vivo: izinkan juga Autostart.",
            solidButton(p, "Buka") {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        )

        val ver = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "" }
        val footer = tv("Dibuat oleh Nexora · v$ver", 11.5f, p.sub)
        footer.gravity = Gravity.CENTER
        col.addView(footer, lp(MATCH, WRAP, dp(26)))
    }

    // ---- Tab 2: Panel

    private fun buildPanelTab(col: LinearLayout) {
        title(col, "Panel", "Posisi, ukuran, dan perilaku")

        val pos = group(col, "Posisi")
        row(
            pos, "Sisi panel", null,
            seg(listOf("Kiri", "Kanan"), if (Prefs.side(this) == Prefs.SIDE_LEFT) 0 else 1) { i ->
                Prefs.setSide(this, if (i == 0) Prefs.SIDE_LEFT else Prefs.SIDE_RIGHT)
                refreshService()
                render()
            }
        )
        slider(pos, "Posisi garis (naik-turun)", 0, 100, Prefs.handlePos(this), "%") {
            Prefs.setHandlePos(this, it); refreshService()
        }
        slider(pos, "Tinggi garis", 10, 50, Prefs.handleHeight(this), "dp") {
            Prefs.setHandleHeight(this, it); refreshService()
        }

        val size = group(col, "Ukuran")
        slider(size, "Lebar panel", 100, 220, Prefs.effectiveWidthDp(this), "dp") {
            Prefs.setWidthDp(this, it); refreshService(); render()
        }
        slider(size, "Lebar daftar tambah (+)", 44, 100, Prefs.pickerWidth(this), "dp") {
            Prefs.setPickerWidth(this, it); refreshService()
        }
        slider(size, "Tinggi daftar tambah (+)", 3, 7, Prefs.pickerItems(this), "ikon") {
            Prefs.setPickerItems(this, it); refreshService()
        }
        row(
            size, "Jumlah baris", "Isi panel lebih banyak dari ini? Geser atas-bawah.",
            seg(listOf("1", "2", "3"), Prefs.gridRows(this) - 1) { i ->
                Prefs.setGridRows(this, i + 1); refreshService(); render()
            }
        )

        val beh = group(col, "Perilaku")
        row(
            beh, "Cara buka aplikasi",
            when (Prefs.mode(this)) {
                Prefs.MODE_FULLSCREEN -> "Ketuk ikon: layar penuh."
                Prefs.MODE_FREEFORM -> "Ketuk ikon: mengambang."
                else -> "Ketuk ikon: layar penuh. Logo kecil di ikon: mengambang."
            },
            seg(listOf("Auto", "Penuh", "Freeform"), Prefs.mode(this)) { i ->
                if (i == Prefs.MODE_FREEFORM && !AppLauncher.freeformReady(this)) {
                    Toast.makeText(this, "Freeform belum aktif di HP ini. Lihat tab Bantuan.", Toast.LENGTH_LONG).show()
                } else {
                    Prefs.setMode(this, i); refreshService(); render()
                }
            }
        )
        row(
            beh, "Papan klip", "Teks yang disalin muncul di panel, sebelum tombol +.",
            Toggle(this, p, Prefs.clipEnabled(this)) { Prefs.setClipEnabled(this, it); refreshService() }
        )
        note(
            col,
            "Papan klip: ketuk = salin lagi, tahan lalu seret = tempel ke aplikasi yang menerima, geser kiri/kanan = hapus. Dari aplikasi lain: tahan teks yang dipilih sampai mengambang, tahan, buka panel dengan jari lain (atau seret ke garis tepi), lalu lepas di panel. Ketuk aplikasi yang sedang freeform untuk Tutup. Tekan lama ikon untuk Buka, Atur Freeform, atau Hapus dari Panel."
        )
    }

    // ---- Tab 3: Aplikasi

    private fun buildAppsTab(col: LinearLayout) {
        val list = Prefs.apps(this)
        title(col, "Aplikasi", "${list.size} aplikasi di panel")
        val c = group(col, null)
        if (list.isEmpty()) {
            free(c, tv("Belum ada aplikasi. Tekan Tambah aplikasi di bawah.", 13.5f, p.sub))
        }
        for (pkg in list) {
            val icon = (try { packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null }) ?: continue
            val label = try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) { pkg }
            if (c.childCount > 0) divider(c)
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            r.gravity = Gravity.CENTER_VERTICAL
            r.setPadding(dp(16), dp(8), dp(10), dp(8))
            val iv = android.widget.ImageView(this)
            iv.setImageDrawable(icon)
            r.addView(iv, LinearLayout.LayoutParams(dp(34), dp(34)))
            val name = tv(label, 14.5f, p.text, true)
            name.maxLines = 1
            name.ellipsize = android.text.TextUtils.TruncateAt.END
            r.addView(name, LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = dp(14) })
            r.addView(solidButton(p, "Hapus") {
                Prefs.removeApp(this, pkg); refreshService(); render()
            })
            c.addView(r, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        col.addView(
            solidButton(p, "Tambah aplikasi", true) { startActivity(Intent(this, AddAppsActivity::class.java)) },
            lp(MATCH, WRAP, dp(16))
        )
        note(col, "Bisa juga lewat tombol + di dalam panel.")
    }

    // ---- Tab 4: Bantuan (freeform + donasi)

    private fun stepRow(parent: LinearLayout, n: Int, text: String, top: Int, extra: View? = null) {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        val num = tv(n.toString(), 12f, p.bg, true)
        num.gravity = Gravity.CENTER
        num.background = ovalBg(p.text)
        r.addView(num, LinearLayout.LayoutParams(dp(24), dp(24)))
        val txt = LinearLayout(this)
        txt.orientation = LinearLayout.VERTICAL
        txt.addView(tv(text, 13.5f, p.text))
        if (extra != null) txt.addView(extra, lp(MATCH, WRAP, dp(10)))
        r.addView(txt, LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = dp(12) })
        parent.addView(r, lp(MATCH, WRAP, top))
    }

    /** Kode di kiri, tombol Salin di kanan (satu baris). */
    private fun codeBlock(text: String): View {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.background = roundRect(0xFFF4F4F4.toInt(), dp(14).toFloat(), p.line, hairPx())
        r.setPadding(dp(12), dp(8), dp(8), dp(8))
        val box = tv(text, 11.5f, p.text)
        box.typeface = Typeface.MONOSPACE
        box.setTextIsSelectable(true)
        r.addView(box, LinearLayout.LayoutParams(0, WRAP, 1f))
        r.addView(solidButton(p, "Salin", true) { copyText(text) }, LinearLayout.LayoutParams(WRAP, WRAP).apply {
            leftMargin = dp(8)
        })
        return r
    }

    private fun buildHelp(col: LinearLayout) {
        title(col, "Bantuan", "Freeform dan dukungan")
        val ff = AppLauncher.freeformReady(this)

        val c = group(col, "Aktifkan Freeform")
        val s1 = LinearLayout(this)
        s1.orientation = LinearLayout.VERTICAL
        s1.addView(tv(if (ff) "Freeform aktif di HP ini" else "Freeform belum aktif di HP ini", 15f, p.text, true))
        s1.addView(
            tv(
                if (ff) "Mode Freeform dan menu Atur Freeform sudah bisa dipakai."
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
        stepRow(
            steps, 1, "Unduh Brevent dari Play Store.", 0,
            solidButton(p, "Buka Play Store", true) { openStore(BREVENT_PKG) }
        )
        stepRow(
            steps, 2,
            "Buka Brevent, hubungkan lewat debugging nirkabel: Pengaturan > Opsi pengembang > Debugging nirkabel > nyalakan, lalu ikuti pairing di Brevent.",
            dp(16)
        )
        val codes = LinearLayout(this)
        codes.orientation = LinearLayout.VERTICAL
        codes.addView(codeBlock("settings put global enable_freeform_support 1"), lp(MATCH, WRAP))
        codes.addView(codeBlock("settings put global force_resizable_activities 1"), lp(MATCH, WRAP, dp(8)))
        stepRow(
            steps, 3,
            "Di Brevent buka menu titik tiga, cari menu untuk menjalankan perintah (shell), lalu masukkan dua kode ini satu per satu.",
            dp(16), codes
        )
        stepRow(steps, 4, "Restart HP, lalu buka Smart Panel lagi.", dp(16))
        free(c, steps)
        note(col, "Kalau setelah itu freeform tetap tidak aktif, HP tidak mendukungnya. Mode Auto dan Layar penuh tetap jalan.", 12)

        val d = group(col, "Dukung Nexora")
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.addView(tv("Smart Panel gratis.", 15f, p.text, true))
        box.addView(tv("Kalau terbantu, dukung pengembangannya lewat Saweria.", 13f, p.sub), lp(MATCH, WRAP, dp(3)))
        box.addView(solidButton(p, "Donasi via Saweria", true) { openUrl(SAWERIA) }, lp(WRAP, WRAP, dp(14)))
        free(d, box)
    }

    // ---- Navigasi bawah

    private fun buildNav(): View {
        val wrap = LinearLayout(this)
        wrap.orientation = LinearLayout.VERTICAL
        wrap.setBackgroundColor(p.bg)
        val line = View(this)
        line.setBackgroundColor(p.line)
        wrap.addView(line, LinearLayout.LayoutParams(MATCH, hairPx()))
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        TABS.forEachIndexed { i, name ->
            val on = i == tab
            val item = LinearLayout(this)
            item.orientation = LinearLayout.VERTICAL
            item.gravity = Gravity.CENTER_HORIZONTAL
            item.setPadding(0, dp(10), 0, dp(10))
            val t = tv(name, 12.5f, if (on) p.text else p.sub, on)
            t.gravity = Gravity.CENTER
            item.addView(t, LinearLayout.LayoutParams(WRAP, WRAP))
            val bar2 = View(this)
            bar2.background = roundRect(if (on) p.text else 0, dp(1).toFloat())
            item.addView(bar2, LinearLayout.LayoutParams(dp(18), dp(2)).apply { topMargin = dp(5) })
            item.setOnClickListener {
                if (tab != i) {
                    tab = i
                    render()
                }
            }
            bar.addView(item, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        wrap.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP))
        return wrap
    }
}
