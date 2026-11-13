package com.nexora.smartpanel

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.DisplayMetrics
import android.view.DragEvent
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** Isi grid panel: aplikasi, teks papan klip, lalu tombol tambah (+) di paling akhir. */
private sealed class PCell {
    class App(val pkg: String) : PCell()
    class Clip(val text: String) : PCell()
    object Add : PCell()
}

/** Root jendela panel: mendeteksi sentuhan di luar jendela untuk menutupnya. */
private class PanelRoot(ctx: Context, val onOutside: () -> Unit) : LinearLayout(ctx) {
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            onOutside()
            return true
        }
        return super.dispatchTouchEvent(ev)
    }
}

/** ScrollView dengan tinggi maksimum. */
private class MaxHeightScroll(ctx: Context, val maxH: Int) : ScrollView(ctx) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST))
    }
}

class PanelService : Service() {

    companion object {
        const val ACTION_STOP = "com.nexora.smartpanel.STOP"
        const val ACTION_REFRESH = "com.nexora.smartpanel.REFRESH"
        private const val CHANNEL = "smart_panel"
        private const val NOTIF_ID = 1001
    }

    private lateinit var wm: WindowManager
    private val ui = Handler(Looper.getMainLooper())
    private var pal: Pal? = null

    private var handle: View? = null
    private var handleY = 0

    // satu jendela berisi: [daftar aplikasi (picker)] + [panel]
    private var panelWin: PanelRoot? = null
    private var panelBody: LinearLayout? = null
    private var pickerHolder: View? = null
    private var pickerOpen = false
    private var winLp: WindowManager.LayoutParams? = null
    private var yShift = 0

    // menu tekan-lama (jendela terpisah di atas panel)
    private var menuView: View? = null
    private var menuClosedAt = 0L
    private var handleLp: WindowManager.LayoutParams? = null

    // papan klip
    private var clipListener: ClipboardManager.OnPrimaryClipChangedListener? = null

    private val icons = HashMap<String, Drawable>()

    override fun onBind(intent: Intent?): IBinder? = null

    // ------------------------------------------------------------------ siklus hidup

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Smart Panel", NotificationManager.IMPORTANCE_MIN)
            )
        }
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }

        if (!Settings.canDrawOverlays(this)) {
            Prefs.setEnabled(this, false)
            stopSelf()
            return
        }
        showHandle()
        registerClipListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            Prefs.setEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_STOP -> {
                Prefs.setEnabled(this, false)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH -> rebuild()
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // aplikasi dihapus dari Recents: hidupkan lagi panelnya
        Revive.schedule(this, 1500)
        super.onTaskRemoved(rootIntent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        rebuild()
    }

    override fun onDestroy() {
        unregisterClipListener()
        removePanelViews()
        removeHandleView()
        // kalau bukan dimatikan oleh pengguna, jadwalkan hidup lagi
        Revive.schedule(this, 2000)
        super.onDestroy()
    }

    // ------------------------------------------------------------------ util

    @Suppress("DEPRECATION")
    private fun realSize(): IntArray {
        if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            return intArrayOf(b.width(), b.height())
        }
        val dm = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(dm)
        return intArrayOf(dm.widthPixels, dm.heightPixels)
    }

    private fun screenW(): Int = realSize()[0]
    private fun screenH(): Int = realSize()[1]

    private fun widthPx(): Int = dp(Prefs.effectiveWidthDp(this))

    /** Panel melayang, sedikit berjarak dari tepi layar. */
    private fun gapPx(): Int = dp(6)

    private fun winW(): Int = widthPx() + gapPx()

    private fun edgeX(totalW: Int): Int =
        if (Prefs.side(this) == Prefs.SIDE_RIGHT) screenW() - totalW else 0

    @Suppress("DEPRECATION")
    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

    private fun overlayParams(w: Int, h: Int, watchOutside: Boolean): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (watchOutside) flags = flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        val lp = WindowManager.LayoutParams(w, h, overlayType(), flags, PixelFormat.TRANSLUCENT)
        if (Build.VERSION.SDK_INT >= 30) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        return lp
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(): Notification {
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, PanelService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val b = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL)
        } else {
            Notification.Builder(this).setPriority(Notification.PRIORITY_MIN)
        }
        return b.setSmallIcon(R.drawable.ic_stat_panel)
            .setContentTitle("Smart Panel aktif")
            .setContentText("Geser garis di tepi layar untuk membuka panel")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Matikan", stop).build())
            .setOngoing(true)
            .build()
    }

    /** Memuat ulang semuanya setelah pengaturan berubah. */
    private fun rebuild() {
        val wasOpen = panelWin != null
        closePanel()
        if (wasOpen) openPanel()
        // pengaturan papan klip bisa berubah
        unregisterClipListener()
        registerClipListener()
    }

    private fun iconFor(pkg: String): Drawable? {
        icons[pkg]?.let { return it }
        return try {
            val d = packageManager.getApplicationIcon(pkg)
            icons[pkg] = d
            d
        } catch (e: Exception) {
            null
        }
    }

    private fun appLabel(pkg: String): String {
        return try {
            val info = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            pkg.substringAfterLast('.')
        }
    }

    // ------------------------------------------------------------------ papan klip (deteksi teks yang disalin)

    private fun registerClipListener() {
        if (clipListener != null || !Prefs.clipEnabled(this)) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val l = ClipboardManager.OnPrimaryClipChangedListener { onClipChanged() }
        try {
            cm.addPrimaryClipChangedListener(l)
            clipListener = l
        } catch (e: Exception) {
            // diabaikan
        }
    }

    private fun unregisterClipListener() {
        val l = clipListener ?: return
        try {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).removePrimaryClipChangedListener(l)
        } catch (e: Exception) {
            // diabaikan
        }
        clipListener = null
    }

    private fun readClipNow(): String? = try {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val c = cm.primaryClip
        if (c != null && c.itemCount > 0) c.getItemAt(0).coerceToText(this)?.toString() else null
    } catch (e: Exception) {
        null
    }

    private fun onClipChanged() {
        if (!Prefs.clipEnabled(this)) return
        val direct = readClipNow()
        if (!direct.isNullOrBlank()) {
            storeClip(direct)
        } else if (Build.VERSION.SDK_INT >= 29) {
            // Android 10+: aplikasi di latar belakang tidak boleh membaca papan klip.
            // Jendela 1 piksel yang tidak tersentuh dipinjam sebentar agar mendapat fokus, lalu dibaca.
            readClipWithFocus()
        }
    }

    private fun readClipWithFocus() {
        val v = View(this)
        val lp = WindowManager.LayoutParams(
            1, 1, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.LEFT
        try {
            wm.addView(v, lp)
        } catch (e: Exception) {
            return
        }
        var done = false
        fun finish() {
            if (done) return
            done = true
            val t = readClipNow()
            try {
                wm.removeView(v)
            } catch (e: Exception) {
                // sudah terlepas
            }
            if (!t.isNullOrBlank()) storeClip(t)
        }
        v.viewTreeObserver.addOnWindowFocusChangeListener { has ->
            if (has) v.post { finish() }
        }
        ui.postDelayed({ finish() }, 600)
    }

    private fun storeClip(text: String) {
        Prefs.addClip(this, text)
        if (panelWin != null) {
            buildBody()
            fit()
        }
    }

    // ------------------------------------------------------------------ garis di tepi layar

    private fun removeHandleView() {
        handle?.let {
            try {
                wm.removeView(it)
            } catch (e: Exception) {
                // sudah terlepas
            }
        }
        handle = null
    }

    /** Garis tipis di tepi. Posisi vertikalnya tetap (diatur lewat pengaturan), tidak bisa digeser bebas. */
    private fun showHandle() {
        removeHandleView()
        val p = Pal.of(this)
        val w = dp(22)
        val h = dp(96)
        val sw = screenW()
        val sh = screenH()
        val side = Prefs.side(this)

        val view = FrameLayout(this)
        val edge = (if (side == Prefs.SIDE_RIGHT) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL
        val pill = View(this)
        // garis putih dengan pinggir hitam tipis supaya terlihat di latar terang maupun gelap
        pill.background = roundRect(p.bg, dp(2).toFloat(), p.text, max(1, dp(1) / 2))
        view.addView(pill, FrameLayout.LayoutParams(dp(5), dp(Prefs.handleHeight(this)), edge).apply {
            leftMargin = dp(3)
            rightMargin = dp(3)
        })

        handleY = ((sh - h) * Prefs.handlePos(this) / 100).coerceIn(0, max(0, sh - h))

        val lp = overlayParams(w, h, false)
        lp.gravity = Gravity.TOP or Gravity.LEFT
        lp.x = if (side == Prefs.SIDE_RIGHT) sw - w else 0
        lp.y = handleY

        attachHandleTouch(view)
        attachHandleDrag(view, lp, w, sw, side)
        wm.addView(view, lp)
        handle = view
        handleLp = lp
    }

    private fun textOf(ev: DragEvent): String? {
        val cd = ev.clipData ?: return null
        if (cd.itemCount == 0) return null
        return cd.getItemAt(0).coerceToText(this)?.toString()
    }

    private fun isTextDrag(ev: DragEvent): Boolean {
        val d = ev.clipDescription ?: return false
        return d.hasMimeType("text/*") && d.label?.toString() != "Smart Panel"
    }

    /** Teks yang sedang diseret dari aplikasi lain: area handle melebar, menyentuhnya membuka panel. */
    private fun attachHandleDrag(view: View, lp: WindowManager.LayoutParams, w: Int, sw: Int, side: Int) {
        view.setOnDragListener { _, ev ->
            when (ev.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    if (!isTextDrag(ev)) return@setOnDragListener false
                    val wide = dp(56)
                    lp.width = wide
                    lp.x = if (side == Prefs.SIDE_RIGHT) sw - wide else 0
                    try { wm.updateViewLayout(view, lp) } catch (e: Exception) { }
                    true
                }
                DragEvent.ACTION_DRAG_ENTERED -> {
                    openPanel()
                    true
                }
                DragEvent.ACTION_DROP -> {
                    val t = textOf(ev)
                    if (!t.isNullOrBlank()) {
                        Prefs.addClip(this, t)
                        Toast.makeText(this, "Disimpan ke papan klip", Toast.LENGTH_SHORT).show()
                        true
                    } else false
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    lp.width = w
                    lp.x = if (side == Prefs.SIDE_RIGHT) sw - w else 0
                    try { wm.updateViewLayout(view, lp) } catch (e: Exception) { }
                    true
                }
                else -> true
            }
        }
    }

    /** Panel menerima teks yang dijatuhkan: masuk ke papan klip. */
    private fun attachDropTarget(root: View) {
        root.setOnDragListener { _, ev ->
            when (ev.action) {
                DragEvent.ACTION_DRAG_STARTED -> isTextDrag(ev)
                DragEvent.ACTION_DROP -> {
                    val t = textOf(ev)
                    if (!t.isNullOrBlank()) {
                        Prefs.addClip(this, t)
                        ui.post {
                            if (panelWin != null) {
                                buildBody()
                                fit()
                            }
                        }
                        true
                    } else false
                }
                else -> true
            }
        }
    }

    /** Ketuk atau geser ke arah dalam membuka panel. Geser atas-bawah diabaikan. */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachHandleTouch(view: View) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var mode = 0 // 0 = belum jelas, 1 = geser horizontal, 3 = panel terbuka

        view.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    mode = 0
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (mode == 0 && abs(dx) > slop && abs(dx) > abs(dy)) mode = 1
                    if (mode == 1) {
                        val need = dp(20)
                        val inward = if (Prefs.side(this) == Prefs.SIDE_RIGHT) dx < -need else dx > need
                        if (inward) {
                            mode = 3
                            openPanel()
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (mode == 0 && abs(e.rawY - downY) <= slop) openPanel()
                }
            }
            true
        }
    }

    // ------------------------------------------------------------------ jendela panel

    private fun removePanelViews() {
        dismissMenu()
        panelWin?.let {
            try {
                wm.removeView(it)
            } catch (e: Exception) {
                // sudah terlepas
            }
        }
        panelWin = null
        panelBody = null
        pickerHolder = null
        pickerOpen = false
        winLp = null
        yShift = 0
    }

    private fun closePanel() {
        removePanelViews()
        showHandle()
    }

    private fun openPanel() {
        if (panelWin != null) return
        pal = Pal.of(this)
        val w = winW()
        val sh = screenH()

        val root = PanelRoot(this) {
            ui.post {
                val recentMenu = System.currentTimeMillis() - menuClosedAt < 400
                if (menuView == null && !recentMenu) closePanel()
            }
        }
        attachDropTarget(root)
        root.orientation = LinearLayout.HORIZONTAL

        val lp = overlayParams(w, WRAP, true)
        lp.gravity = Gravity.TOP or Gravity.LEFT
        lp.x = edgeX(w)
        lp.y = handleY.coerceIn(0, max(0, sh - dp(160)))
        winLp = lp
        panelWin = root

        buildBody()

        handle?.visibility = View.GONE
        wm.addView(root, lp)

        root.translationX = if (Prefs.side(this) == Prefs.SIDE_RIGHT) w.toFloat() else -w.toFloat()
        root.alpha = 0.88f
        root.animate().translationX(0f).alpha(1f).setDuration(190).start()

        // sejajarkan tengah panel dengan tengah handle, lalu jaga agar tidak keluar layar
        root.post {
            val l = winLp
            if (l != null && panelWin === root) {
                val center = handleY + dp(48)
                l.y = (center - root.height / 2).coerceIn(0, max(0, screenH() - root.height))
                try {
                    wm.updateViewLayout(root, l)
                } catch (e: Exception) {
                    // diabaikan
                }
            }
        }
        fit()
        pruneRunning()
    }

    /** Pastikan jendela tidak keluar dari bawah layar. */
    private fun fit() {
        val root = panelWin ?: return
        root.post {
            val lp = winLp
            if (lp != null && panelWin === root) {
                val over = lp.y + root.height - screenH()
                if (over > 0) {
                    lp.y = max(0, lp.y - over)
                    try {
                        wm.updateViewLayout(root, lp)
                    } catch (e: Exception) {
                        // diabaikan
                    }
                }
            }
        }
    }

    /** Hapus tanda "sedang berjalan" untuk aplikasi yang sebenarnya sudah tidak ada. */
    private fun pruneRunning() {
        val run = Prefs.running(this)
        if (run.isEmpty() || !Priv.ready()) return
        Thread {
            val live = Priv.livePackages()
            if (live != null) {
                val keep = run.filter { live.contains(it) }.toSet()
                if (keep.size != run.size) {
                    ui.post {
                        Prefs.setRunning(this, keep)
                        if (panelWin != null) buildBody()
                    }
                }
            }
        }.start()
    }

    // ------------------------------------------------------------------ isi panel

    private fun buildBody() {
        val root = panelWin ?: return
        val lp = winLp ?: return
        val p = pal ?: Pal.of(this)
        val side = Prefs.side(this)
        val wPx = widthPx()
        val pad = dp(8)
        val cellW = ((wPx - pad * 2) / 2).coerceAtLeast(dp(40))
        val tileH = dp(68)
        val iconSz = min(dp(36), cellW - dp(12))
        val badge = dp(15)
        val mode = Prefs.mode(this)
        val ffReady = AppLauncher.freeformReady(this)
        val running = Prefs.running(this)
        val rowsLimit = Prefs.gridRows(this)

        panelBody?.let { root.removeView(it) }

        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.isBaselineAligned = false
        body.background = roundRect(p.surface, dp(22).toFloat(), p.line, max(1, dp(1)))
        body.setPadding(pad, dp(2), pad, dp(4))

        // "--" di atas: tekan lalu geser ke kiri atau kanan untuk memindah sisi panel.
        val grip = FrameLayout(this)
        val gripLine = View(this)
        gripLine.background = roundRect(p.sub, dp(2).toFloat())
        grip.addView(gripLine, FrameLayout.LayoutParams(dp(28), dp(3), Gravity.CENTER))
        body.addView(grip, LinearLayout.LayoutParams(MATCH, dp(22)))
        attachGripDrag(grip, root, lp)

        // isi grid: aplikasi -> teks papan klip -> tombol +
        val cells = ArrayList<PCell>()
        for (pkg in Prefs.apps(this).distinct()) {
            if (iconFor(pkg) == null) Prefs.removeApp(this, pkg) else cells.add(PCell.App(pkg))
        }
        if (Prefs.clipEnabled(this)) {
            Prefs.clips(this).forEach { cells.add(PCell.Clip(it)) }
        }
        cells.add(PCell.Add)

        val scroll = MaxHeightScroll(this, tileH * rowsLimit)
        scroll.isVerticalScrollBarEnabled = false
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        val grid = LinearLayout(this)
        grid.orientation = LinearLayout.VERTICAL
        grid.isBaselineAligned = false

        cells.chunked(2).forEach { rowCells ->
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.isBaselineAligned = false
            rowCells.forEach { c ->
                val v: View = when (c) {
                    is PCell.App -> makeTile(
                        p, c.pkg, iconFor(c.pkg)!!, iconSz, badge, mode, ffReady, running.contains(c.pkg)
                    )
                    is PCell.Clip -> makeClipTile(p, c.text)
                    PCell.Add -> makeAddTile(p)
                }
                row.addView(v, LinearLayout.LayoutParams(cellW, tileH))
            }
            repeat(2 - rowCells.size) {
                row.addView(View(this), LinearLayout.LayoutParams(cellW, tileH))
            }
            grid.addView(row, LinearLayout.LayoutParams(MATCH, tileH))
        }
        scroll.addView(grid, FrameLayout.LayoutParams(MATCH, WRAP))
        body.addView(scroll, LinearLayout.LayoutParams(MATCH, WRAP))

        // satu-satunya tombol di bawah: pengaturan
        val footer = FrameLayout(this)
        val gear = Glyph(this, Glyph.GEAR, p.sub)
        footer.addView(gear, FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER))
        footer.contentDescription = "Pengaturan"
        footer.setOnClickListener {
            closePanel()
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        footer.pressFade()
        body.addView(footer, LinearLayout.LayoutParams(MATCH, dp(30)))

        val idx = if (side == Prefs.SIDE_RIGHT) root.childCount else 0
        val bl = LinearLayout.LayoutParams(wPx, WRAP)
        bl.gravity = Gravity.CENTER_VERTICAL
        if (side == Prefs.SIDE_RIGHT) bl.rightMargin = gapPx() else bl.leftMargin = gapPx()
        root.addView(body, idx, bl)
        panelBody = body
    }

    // ------------------------------------------------------------------ tile

    private fun makeAddTile(p: Pal): View {
        val cell = LinearLayout(this)
        cell.orientation = LinearLayout.VERTICAL
        cell.gravity = Gravity.CENTER
        val ring = FrameLayout(this)
        ring.background = ovalBg(p.bg, p.text, max(1, dp(1)))
        ring.addView(
            Glyph(this, if (pickerOpen) Glyph.CHECK else Glyph.PLUS, p.text),
            FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER)
        )
        cell.addView(ring, LinearLayout.LayoutParams(dp(34), dp(34)))
        val label = tv(if (pickerOpen) "Selesai" else "Tambah", 10f, p.sub)
        label.gravity = Gravity.CENTER
        cell.addView(label, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(4) })
        cell.setOnClickListener { togglePicker() }
        cell.contentDescription = "Tambah aplikasi"
        cell.pressScale()
        return cell
    }

    private fun makeTile(
        p: Pal, pkg: String, icon: Drawable, iconSz: Int, badge: Int,
        mode: Int, ffReady: Boolean, isRunning: Boolean
    ): View {
        val cell = LinearLayout(this)
        cell.orientation = LinearLayout.VERTICAL
        cell.gravity = Gravity.CENTER
        cell.setPadding(dp(2), dp(2), dp(2), dp(1))

        val iconBox = FrameLayout(this)
        val iv = ImageView(this)
        iv.setImageDrawable(fresh(icon))
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        iconBox.addView(iv, FrameLayout.LayoutParams(iconSz, iconSz, Gravity.CENTER))

        if (isRunning) {
            val dot = View(this)
            dot.background = ovalBg(p.text)
            iconBox.addView(
                dot,
                FrameLayout.LayoutParams(dp(4), dp(4), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            )
        }

        if (mode == Prefs.MODE_AUTO && ffReady) {
            val ff = FrameLayout(this)
            ff.background = ovalBg(p.accent, p.surface, max(1, dp(1)))
            ff.addView(
                Glyph(this, Glyph.WINDOW, p.onAccent),
                FrameLayout.LayoutParams(badge, badge, Gravity.CENTER)
            )
            ff.setOnClickListener { launchApp(pkg, AppLauncher.SIZE_STANDARD) }
            iconBox.addView(ff, FrameLayout.LayoutParams(badge, badge, Gravity.BOTTOM or Gravity.RIGHT))
        }

        cell.addView(iconBox, LinearLayout.LayoutParams(MATCH, dp(44)))
        val label = tv(appLabel(pkg), 10f, p.text)
        label.gravity = Gravity.CENTER
        label.maxLines = 1
        label.ellipsize = TextUtils.TruncateAt.END
        cell.addView(label, LinearLayout.LayoutParams(MATCH, dp(18)))

        cell.setOnClickListener {
            if (isRunning) {
                // aplikasi yang sedang freeform: tawarkan Tutup
                showMenu(
                    cell, appLabel(pkg),
                    listOf(
                        "Tutup" to { closeApp(pkg) },
                        "Buka" to { launchApp(pkg, AppLauncher.SIZE_STANDARD) },
                        "Atur Freeform" to { showFreeformMenu(cell, pkg) }
                    )
                )
            } else {
                launchApp(pkg, if (mode == Prefs.MODE_FREEFORM) AppLauncher.SIZE_STANDARD else AppLauncher.SIZE_FULL)
            }
        }
        cell.setOnLongClickListener {
            showMenu(
                cell, appLabel(pkg),
                listOf(
                    "Buka aplikasi" to { launchApp(pkg, AppLauncher.SIZE_FULL) },
                    "Atur Freeform" to { showFreeformMenu(cell, pkg) },
                    "Hapus dari Panel" to {
                        Prefs.removeApp(this, pkg)
                        buildBody()
                        fit()
                    }
                )
            )
            true
        }
        cell.contentDescription = appLabel(pkg)
        cell.pressScale()
        return cell
    }

    /** Tile teks papan klip: putih transparan. Ketuk = salin lagi, tahan = seret, geser kiri/kanan = hapus. */
    private fun makeClipTile(p: Pal, text: String): View {
        val wrap = FrameLayout(this)
        wrap.setPadding(dp(3), dp(3), dp(3), dp(3))
        val t = tv(text, 9.5f, p.text)
        t.maxLines = 4
        t.ellipsize = TextUtils.TruncateAt.END
        t.gravity = Gravity.CENTER_VERTICAL
        t.setPadding(dp(6), dp(4), dp(6), dp(4))
        t.background = roundRect(0xCCF2F2F2.toInt(), dp(12).toFloat(), p.line, max(1, dp(1)))
        wrap.addView(t, FrameLayout.LayoutParams(MATCH, MATCH))
        wrap.contentDescription = "Papan klip: $text"
        attachClipTouch(wrap, text)
        return wrap
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachClipTouch(v: View, text: String) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var swiping = false
        var dragging = false
        val startDrag = Runnable {
            if (!swiping) {
                dragging = true
                startClipDrag(v, text)
            }
        }

        fun reset() {
            v.animate().translationX(0f).alpha(1f).setDuration(120).start()
        }

        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    swiping = false
                    dragging = false
                    ui.postDelayed(startDrag, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!swiping && abs(dx) > slop && abs(dx) > abs(dy)) {
                        swiping = true
                        ui.removeCallbacks(startDrag)
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                    } else if (!swiping && abs(dy) > slop) {
                        ui.removeCallbacks(startDrag)
                    }
                    if (swiping) {
                        view.translationX = dx
                        view.alpha = 1f - min(1f, abs(dx) / max(1, view.width).toFloat())
                    }
                }
                MotionEvent.ACTION_UP -> {
                    ui.removeCallbacks(startDrag)
                    if (swiping) {
                        if (abs(view.translationX) > view.width * 0.4f) {
                            val dir = if (view.translationX > 0) 1f else -1f
                            view.animate().translationX(dir * view.width).alpha(0f).setDuration(120)
                                .withEndAction {
                                    Prefs.removeClip(this, text)
                                    if (panelWin != null) {
                                        buildBody()
                                        fit()
                                    }
                                }.start()
                        } else {
                            reset()
                        }
                    } else if (!dragging && abs(e.rawX - downX) <= slop && abs(e.rawY - downY) <= slop) {
                        copyBack(text)
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    ui.removeCallbacks(startDrag)
                    if (swiping) reset()
                }
            }
            true
        }
    }

    private fun copyBack(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        try {
            cm.setPrimaryClip(ClipData.newPlainText("Smart Panel", text))
            Toast.makeText(this, "Disalin", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            // diabaikan
        }
    }

    /** Seret teks ke aplikasi lain (butuh aplikasi tujuan yang menerima drag teks, mis. mode freeform atau layar terbagi). */
    private fun startClipDrag(v: View, text: String) {
        val data = ClipData.newPlainText("Smart Panel", text)
        v.setOnDragListener { _, ev ->
            if (ev.action == DragEvent.ACTION_DRAG_ENDED && ev.result) ui.post { closePanel() }
            true
        }
        val ok = try {
            v.startDragAndDrop(data, View.DragShadowBuilder(v), null, View.DRAG_FLAG_GLOBAL)
        } catch (e: Exception) {
            false
        }
        if (!ok) copyBack(text)
    }

    private fun launchApp(pkg: String, size: Int) {
        if (size != AppLauncher.SIZE_FULL && !AppLauncher.freeformReady(this)) {
            Toast.makeText(this, "Freeform belum aktif di HP ini, dibuka layar penuh", Toast.LENGTH_SHORT).show()
        }
        closePanel()
        if (!AppLauncher.launch(this, pkg, size)) {
            Toast.makeText(this, "Gagal membuka aplikasi", Toast.LENGTH_SHORT).show()
        }
    }

    /** Menutup aplikasi (hapus dari Recents). Penuh hanya kalau Shizuku aktif; tanpa itu hanya yang di latar belakang. */
    private fun closeApp(pkg: String) {
        Prefs.removeRunning(this, pkg)
        Thread {
            val full = AppLauncher.kill(this, pkg)
            ui.post {
                if (!full && !Priv.ready()) {
                    Toast.makeText(this, "Tanpa Shizuku, hanya bisa menutup aplikasi di latar belakang", Toast.LENGTH_SHORT).show()
                }
                if (panelWin != null) {
                    buildBody()
                    fit()
                }
            }
        }.start()
        if (panelWin != null) buildBody()
    }

    private fun showFreeformMenu(anchor: View, pkg: String) {
        showMenu(
            anchor,
            "Freeform: " + appLabel(pkg),
            listOf(
                "Layar penuh" to { launchApp(pkg, AppLauncher.SIZE_FULL) },
                "Standar" to { launchApp(pkg, AppLauncher.SIZE_STANDARD) },
                "Maksimal" to { launchApp(pkg, AppLauncher.SIZE_MAXIMIZED) },
                "Potret" to { launchApp(pkg, AppLauncher.SIZE_PORTRAIT) }
            )
        )
    }

    // ------------------------------------------------------------------ menu tekan-lama

    private fun dismissMenu() {
        menuView?.let {
            try {
                wm.removeView(it)
            } catch (e: Exception) {
                // sudah terlepas
            }
            menuClosedAt = System.currentTimeMillis()
        }
        menuView = null
    }

    /**
     * Menu kecil di samping panel, sejajar dengan ikon yang ditekan. Tidak menutupi layar.
     * Sentuh di luar menu menutupnya.
     */
    private fun showMenu(anchor: View, title: String, items: List<Pair<String, () -> Unit>>) {
        dismissMenu()
        val p = pal ?: Pal.of(this)
        val card = PanelRoot(this) { ui.post { dismissMenu() } }
        card.orientation = LinearLayout.VERTICAL
        card.background = roundRect(p.surface, dp(16).toFloat(), p.line, max(1, dp(1)))
        val head = tv(title, 11f, p.sub, true)
        head.maxLines = 1
        head.ellipsize = TextUtils.TruncateAt.END
        head.setPadding(dp(14), dp(10), dp(14), dp(4))
        card.addView(head, LinearLayout.LayoutParams(MATCH, WRAP))
        items.forEachIndexed { i, (label, action) ->
            if (i > 0) {
                val d = View(this)
                d.setBackgroundColor(p.line)
                card.addView(d, LinearLayout.LayoutParams(MATCH, max(1, dp(1) / 2)).apply {
                    leftMargin = dp(14)
                    rightMargin = dp(14)
                })
            }
            val item = tv(label, 13.5f, p.text, true)
            item.setPadding(dp(14), dp(11), dp(14), dp(11))
            item.setOnClickListener {
                dismissMenu()
                action()
            }
            item.pressFade()
            card.addView(item, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        card.addView(View(this), LinearLayout.LayoutParams(MATCH, dp(4)))

        val mw = dp(160)
        card.measure(
            View.MeasureSpec.makeMeasureSpec(mw, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val mh = card.measuredHeight
        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        val wl = winLp
        val onRight = Prefs.side(this) == Prefs.SIDE_RIGHT
        val x = if (wl != null) {
            if (onRight) wl.x - mw - dp(4) else wl.x + wl.width + dp(4)
        } else {
            if (onRight) screenW() - mw else 0
        }
        val y = (loc[1] + anchor.height / 2 - mh / 2).coerceIn(0, max(0, screenH() - mh))

        val lp = overlayParams(mw, WRAP, true)
        lp.gravity = Gravity.TOP or Gravity.LEFT
        lp.x = x.coerceIn(0, max(0, screenW() - mw))
        lp.y = y
        try {
            wm.addView(card, lp)
            menuView = card
        } catch (e: Exception) {
            // diabaikan
        }
    }

    // ------------------------------------------------------------------ geser "--" untuk pindah sisi

    private fun attachGripDrag(grip: View, root: View, lp: WindowManager.LayoutParams) {
        var downX = 0f
        var startX = 0

        grip.setOnTouchListener { _, e ->
            val sw = screenW()
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    startX = lp.x
                }
                MotionEvent.ACTION_MOVE -> {
                    // hanya horizontal
                    lp.x = (startX + (e.rawX - downX).toInt()).coerceIn(0, max(0, sw - lp.width))
                    try {
                        wm.updateViewLayout(root, lp)
                    } catch (ex: Exception) {
                        // diabaikan
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val center = lp.x + lp.width / 2
                    val newSide = if (center < sw / 2) Prefs.SIDE_LEFT else Prefs.SIDE_RIGHT
                    if (newSide != Prefs.side(this)) {
                        Prefs.setSide(this, newSide)
                        ui.post { rebuild() }
                    } else {
                        lp.x = edgeX(lp.width)
                        try {
                            wm.updateViewLayout(root, lp)
                        } catch (ex: Exception) {
                            // diabaikan
                        }
                    }
                }
            }
            true
        }
    }

    // ------------------------------------------------------------------ daftar tambah aplikasi (mengambang)

    private fun togglePicker() {
        if (pickerOpen) closePicker() else openPicker()
    }

    private fun toggleApp(pkg: String) {
        if (Prefs.hasApp(this, pkg)) Prefs.removeApp(this, pkg) else Prefs.addApp(this, pkg)
        buildBody()
        fit()
    }

    private fun openPicker() {
        val root = panelWin ?: return
        val lp = winLp ?: return
        if (pickerOpen) return
        pickerOpen = true

        val p = pal ?: Pal.of(this)
        val side = Prefs.side(this)
        val pw = dp(Prefs.pickerWidth(this))
        val itemPx = dp(48)
        val ph = min(itemPx * Prefs.pickerItems(this), (screenH() * 0.7f).toInt())

        val wheel = WheelPicker(
            this, p, ph, itemPx, min(dp(40), pw - dp(10)),
            { pkg -> Prefs.hasApp(this, pkg) },
            { pkg -> toggleApp(pkg) },
            { _ -> }
        )
        val hl = LinearLayout.LayoutParams(pw, ph)
        hl.gravity = Gravity.CENTER_VERTICAL
        val idx = if (side == Prefs.SIDE_RIGHT) 0 else root.childCount
        root.addView(wheel, idx, hl)
        pickerHolder = wheel

        lp.width = winW() + pw
        lp.x = edgeX(lp.width)

        // panel dibangun ulang (tombol + berubah jadi centang), lalu daftar disejajarkan dengan tengah panel
        buildBody()
        val body = panelBody
        var d = 0
        if (body != null) {
            body.measure(
                View.MeasureSpec.makeMeasureSpec(widthPx(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            d = max(0, (ph - body.measuredHeight) / 2)
        }
        val oldY = lp.y
        lp.y = max(0, lp.y - d)
        yShift = oldY - lp.y
        try {
            wm.updateViewLayout(root, lp)
        } catch (e: Exception) {
            // diabaikan
        }
        fit()

        AppIndex.load(this) { list ->
            if (pickerOpen && pickerHolder === wheel) wheel.setApps(list)
        }
    }

    private fun closePicker() {
        if (!pickerOpen) return
        pickerOpen = false
        val root = panelWin ?: return
        val lp = winLp ?: return
        pickerHolder?.let { root.removeView(it) }
        pickerHolder = null
        lp.width = winW()
        lp.x = edgeX(lp.width)
        lp.y += yShift
        yShift = 0
        try {
            wm.updateViewLayout(root, lp)
        } catch (e: Exception) {
            // diabaikan
        }
        buildBody()
        fit()
    }
}
