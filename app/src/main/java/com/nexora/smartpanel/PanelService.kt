package com.nexora.smartpanel

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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
import android.util.DisplayMetrics
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
    private var handleY = -1

    // satu jendela berisi: [daftar aplikasi (picker)] + [panel]
    private var panelWin: PanelRoot? = null
    private var panelBody: LinearLayout? = null
    private var pickerHolder: View? = null
    private var pickerOpen = false
    private var winLp: WindowManager.LayoutParams? = null
    private var yShift = 0

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
    private fun overlayParams(w: Int, h: Int, watchOutside: Boolean): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (watchOutside) flags = flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val lp = WindowManager.LayoutParams(w, h, type, flags, PixelFormat.TRANSLUCENT)
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
        pill.background = roundRect(withAlpha(p.text, if (p.dark) 0x99 else 0x88), dp(2).toFloat())
        view.addView(pill, FrameLayout.LayoutParams(dp(4), dp(46), edge).apply {
            leftMargin = dp(3)
            rightMargin = dp(3)
        })

        if (handleY < 0) {
            val saved = Prefs.handleY(this)
            handleY = if (saved >= 0) saved else sh / 5
        }
        handleY = handleY.coerceIn(0, max(0, sh - h))

        val lp = overlayParams(w, h, false)
        lp.gravity = Gravity.TOP or Gravity.LEFT
        lp.x = if (side == Prefs.SIDE_RIGHT) sw - w else 0
        lp.y = handleY

        attachHandleTouch(view, lp, h)
        wm.addView(view, lp)
        handle = view
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachHandleTouch(view: View, lp: WindowManager.LayoutParams, h: Int) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startY = 0
        var mode = 0 // 0 = belum jelas, 1 = geser horizontal, 2 = geser vertikal, 3 = panel terbuka

        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startY = lp.y
                    mode = 0
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (mode == 0) {
                        if (abs(dx) > slop && abs(dx) > abs(dy)) {
                            mode = 1
                        } else if (abs(dy) > slop) {
                            mode = 2
                        }
                    }
                    if (mode == 2) {
                        lp.y = (startY + dy.toInt()).coerceIn(0, max(0, screenH() - h))
                        wm.updateViewLayout(v, lp)
                    } else if (mode == 1) {
                        val need = dp(20)
                        val inward = if (Prefs.side(this) == Prefs.SIDE_RIGHT) dx < -need else dx > need
                        if (inward) {
                            mode = 3
                            handleY = lp.y
                            openPanel()
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (mode == 0) {
                        handleY = lp.y
                        openPanel()
                    } else if (mode == 2) {
                        handleY = lp.y
                        Prefs.setHandleY(this, handleY)
                    }
                }
            }
            true
        }
    }

    // ------------------------------------------------------------------ jendela panel

    private fun removePanelViews() {
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

        val root = PanelRoot(this) { ui.post { closePanel() } }
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
        root.animate().translationX(0f).setDuration(180).start()

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
                    handleY = lp.y
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
        val pad = dp(4)
        val tile = (wPx - pad * 2).coerceAtLeast(dp(36))
        val iconSz = (tile * 0.66f).toInt().coerceIn(dp(22), dp(46))
        val badge = (tile * 0.38f).toInt().coerceIn(dp(15), dp(20))
        val mode = Prefs.mode(this)
        val ffReady = AppLauncher.freeformReady(this)
        val running = Prefs.running(this)

        panelBody?.let { root.removeView(it) }

        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        val r = min(wPx / 2f, dp(30).toFloat())
        body.background = GlassDrawable(
            p, r, if (p.dark) 0xB8 else 0xCC, max(1, dp(1)).toFloat()
        )
        body.setPadding(pad, 0, pad, dp(6))

        // garis pegangan: tekan lalu geser untuk memindahkan panel
        val grip = FrameLayout(this)
        val line = View(this)
        line.background = roundRect(withAlpha(p.text, 0x80), dp(2).toFloat())
        grip.addView(line, FrameLayout.LayoutParams(min(dp(26), tile), dp(4), Gravity.CENTER))
        body.addView(grip, LinearLayout.LayoutParams(MATCH, dp(24)))
        attachGripDrag(grip, root, lp)

        // daftar aplikasi
        val scroll = MaxHeightScroll(this, (screenH() * 0.52f).toInt())
        scroll.isVerticalScrollBarEnabled = false
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        for (pkg in Prefs.apps(this)) {
            val icon = iconFor(pkg)
            if (icon == null) {
                Prefs.removeApp(this, pkg) // aplikasi sudah di-uninstall
                continue
            }
            val tl = LinearLayout.LayoutParams(tile, tile)
            tl.bottomMargin = dp(4)
            list.addView(makeTile(p, pkg, icon, iconSz, badge, mode, ffReady, running.contains(pkg)), tl)
        }
        scroll.addView(list, FrameLayout.LayoutParams(MATCH, WRAP))
        body.addView(scroll, LinearLayout.LayoutParams(MATCH, WRAP))

        // pemisah tipis
        val divider = View(this)
        divider.setBackgroundColor(p.line)
        val dl = LinearLayout.LayoutParams(tile / 2, max(1, dp(1)))
        dl.gravity = Gravity.CENTER_HORIZONTAL
        dl.topMargin = dp(2)
        dl.bottomMargin = dp(8)
        body.addView(divider, dl)

        // tambah (jadi centang saat daftar terbuka) dan pengaturan
        val addBtn = actionButton(p, if (pickerOpen) Glyph.CHECK else Glyph.PLUS, tile) { togglePicker() }
        body.addView(addBtn, LinearLayout.LayoutParams(tile, (tile * 0.82f).toInt()))
        val menuBtn = actionButton(p, Glyph.MENU, tile) {
            closePanel()
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        body.addView(menuBtn, LinearLayout.LayoutParams(tile, (tile * 0.82f).toInt()))

        val idx = if (side == Prefs.SIDE_RIGHT) root.childCount else 0
        val bl = LinearLayout.LayoutParams(wPx, WRAP)
        bl.gravity = Gravity.CENTER_VERTICAL
        if (side == Prefs.SIDE_RIGHT) bl.rightMargin = gapPx() else bl.leftMargin = gapPx()
        root.addView(body, idx, bl)
        panelBody = body
    }

    private fun makeTile(
        p: Pal, pkg: String, icon: Drawable, iconSz: Int, badge: Int,
        mode: Int, ffReady: Boolean, isRunning: Boolean
    ): View {
        val f = FrameLayout(this)
        f.setOnClickListener { launchApp(pkg, mode == Prefs.MODE_FREEFORM) }
        f.pressScale()

        val iv = ImageView(this)
        iv.setImageDrawable(fresh(icon))
        f.addView(iv, FrameLayout.LayoutParams(iconSz, iconSz, Gravity.CENTER))

        if (isRunning) {
            // titik kecil di bawah ikon = sedang berjalan
            val dot = View(this)
            dot.background = ovalBg(p.text)
            f.addView(dot, FrameLayout.LayoutParams(dp(4), dp(4), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))

            // tombol kill
            val kill = FrameLayout(this)
            kill.background = ovalBg(p.accent, withAlpha(p.bg, 0xFF), max(1, dp(1)))
            kill.addView(
                Glyph(this, Glyph.CLOSE, p.onAccent, 0.16f),
                FrameLayout.LayoutParams(badge, badge, Gravity.CENTER)
            )
            kill.setOnClickListener { killApp(pkg) }
            f.addView(kill, FrameLayout.LayoutParams(badge, badge, Gravity.TOP or Gravity.RIGHT))
        }

        if (mode == Prefs.MODE_AUTO && ffReady) {
            // logo freeform kecil: ketuk untuk buka mengambang
            val ff = FrameLayout(this)
            ff.background = ovalBg(p.accent, withAlpha(p.bg, 0xFF), max(1, dp(1)))
            ff.addView(
                Glyph(this, Glyph.WINDOW, p.onAccent),
                FrameLayout.LayoutParams(badge, badge, Gravity.CENTER)
            )
            ff.setOnClickListener { launchApp(pkg, true) }
            f.addView(ff, FrameLayout.LayoutParams(badge, badge, Gravity.BOTTOM or Gravity.RIGHT))
        }
        return f
    }

    private fun actionButton(p: Pal, kind: Int, tile: Int, onClick: () -> Unit): View {
        val f = FrameLayout(this)
        val circle = (tile * 0.62f).toInt().coerceAtLeast(dp(26))
        val c = FrameLayout(this)
        c.background = ovalBg(withAlpha(p.text, 0x22))
        c.addView(
            Glyph(this, kind, p.text),
            FrameLayout.LayoutParams(circle, circle, Gravity.CENTER)
        )
        f.addView(c, FrameLayout.LayoutParams(circle, circle, Gravity.CENTER))
        f.setOnClickListener { onClick() }
        f.pressScale()
        return f
    }

    private fun launchApp(pkg: String, freeform: Boolean) {
        closePanel()
        if (!AppLauncher.launch(this, pkg, freeform)) {
            Toast.makeText(this, "Gagal membuka aplikasi", Toast.LENGTH_SHORT).show()
        }
    }

    private fun killApp(pkg: String) {
        closePanel()
        Thread {
            val ok = AppLauncher.kill(this, pkg)
            ui.post {
                if (!ok) {
                    Toast.makeText(
                        this,
                        "Untuk menutup seperti di Recents, aktifkan Shizuku di pengaturan Smart Panel",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    // ------------------------------------------------------------------ geser panel (garis di atas)

    @SuppressLint("ClickableViewAccessibility")
    private fun attachGripDrag(grip: View, root: View, lp: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0

        grip.setOnTouchListener { _, e ->
            val sw = screenW()
            val sh = screenH()
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = lp.x
                    startY = lp.y
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = (startX + (e.rawX - downX).toInt()).coerceIn(0, max(0, sw - lp.width))
                    lp.y = (startY + (e.rawY - downY).toInt()).coerceIn(0, max(0, sh - root.height))
                    try {
                        wm.updateViewLayout(root, lp)
                    } catch (ex: Exception) {
                        // diabaikan
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handleY = lp.y
                    Prefs.setHandleY(this, handleY)
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
        val pw = dp(60)
        val itemPx = dp(48)
        val ph = min(itemPx * 5, (screenH() * 0.7f).toInt())

        val wheel = WheelPicker(
            this, p, ph, itemPx, dp(40),
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
