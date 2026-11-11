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
        pill.background = roundRect(p.text, dp(2).toFloat())
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
        root.alpha = 0.88f
        root.animate().translationX(0f).alpha(1f).setDuration(190).start()

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
        val horizontalPad = dp(12)
        val contentW = (wPx - horizontalPad * 2).coerceAtLeast(dp(180))
        val cellW = contentW / 4
        val iconSz = dp(40)
        val badge = dp(17)
        val mode = Prefs.mode(this)
        val ffReady = AppLauncher.freeformReady(this)
        val running = Prefs.running(this)
        val apps = Prefs.apps(this).distinct()

        panelBody?.let { root.removeView(it) }

        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.isBaselineAligned = false
        body.background = roundRect(p.surface, dp(26).toFloat(), p.line, max(1, dp(1)))
        body.setPadding(horizontalPad, dp(4), horizontalPad, dp(12))

        // Grip bergaya edge panel. Area sentuh dibuat lebih tinggi agar mudah digeser.
        val grip = FrameLayout(this)
        val gripLine = View(this)
        gripLine.background = roundRect(p.sub, dp(3).toFloat())
        grip.addView(gripLine, FrameLayout.LayoutParams(dp(30), dp(4), Gravity.CENTER))
        body.addView(grip, LinearLayout.LayoutParams(MATCH, dp(24)))
        attachGripDrag(grip, root, lp)

        // Header ringkas seperti panel sistem.
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(4), dp(2), dp(4), dp(8))
        val heading = LinearLayout(this)
        heading.orientation = LinearLayout.VERTICAL
        heading.addView(tv("Aplikasi", 15f, p.text, true))
        heading.addView(tv("${apps.size} pintasan", 10.5f, p.sub), LinearLayout.LayoutParams(MATCH, WRAP).apply {
            topMargin = dp(1)
        })
        header.addView(heading, LinearLayout.LayoutParams(0, WRAP, 1f))
        body.addView(header, LinearLayout.LayoutParams(MATCH, WRAP))

        // Grid 4 kolom. Ikon asli aplikasi tetap dipertahankan, chrome panel monokrom.
        val scroll = MaxHeightScroll(this, (screenH() * 0.48f).toInt().coerceAtLeast(dp(170)))
        scroll.isVerticalScrollBarEnabled = false
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        scroll.clipToPadding = false
        val grid = LinearLayout(this)
        grid.orientation = LinearLayout.VERTICAL
        grid.isBaselineAligned = false

        val validApps = ArrayList<String>()
        for (pkg in apps) {
            if (iconFor(pkg) == null) {
                Prefs.removeApp(this, pkg)
            } else {
                validApps.add(pkg)
            }
        }

        if (validApps.isEmpty()) {
            val empty = tv("Belum ada aplikasi\nTekan + untuk menambahkan pintasan", 12f, p.sub)
            empty.gravity = Gravity.CENTER
            empty.setLineSpacing(dp(3).toFloat(), 1f)
            empty.setPadding(dp(12), dp(24), dp(12), dp(24))
            grid.addView(empty, LinearLayout.LayoutParams(MATCH, WRAP))
        } else {
            validApps.chunked(4).forEach { rowPkgs ->
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.isBaselineAligned = false
                rowPkgs.forEach { pkg ->
                    val icon = iconFor(pkg) ?: return@forEach
                    row.addView(
                        makeTile(p, pkg, icon, cellW, iconSz, badge, mode, ffReady, running.contains(pkg)),
                        LinearLayout.LayoutParams(cellW, dp(76))
                    )
                }
                repeat((4 - rowPkgs.size).coerceAtLeast(0)) {
                    row.addView(View(this), LinearLayout.LayoutParams(cellW, dp(76)))
                }
                grid.addView(row, LinearLayout.LayoutParams(MATCH, dp(76)))
            }
        }
        scroll.addView(grid, FrameLayout.LayoutParams(MATCH, WRAP))
        body.addView(scroll, LinearLayout.LayoutParams(MATCH, WRAP))

        // Footer dua aksi: tambah pintasan dan pengaturan.
        val divider = View(this)
        divider.setBackgroundColor(p.line)
        val dividerLp = LinearLayout.LayoutParams(MATCH, max(1, dp(1)))
        dividerLp.topMargin = dp(7)
        dividerLp.bottomMargin = dp(9)
        body.addView(divider, dividerLp)

        val footer = LinearLayout(this)
        footer.orientation = LinearLayout.HORIZONTAL
        footer.gravity = Gravity.CENTER_VERTICAL

        val add = TextView(this)
        add.text = if (pickerOpen) "✓   Selesai" else "+   Tambah aplikasi"
        add.gravity = Gravity.CENTER
        add.setTextColor(p.text)
        add.textSize = 12.5f
        add.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        add.background = roundRect(p.bg, dp(14).toFloat(), p.line, max(1, dp(1)))
        add.setOnClickListener { togglePicker() }
        add.pressFade()
        footer.addView(add, LinearLayout.LayoutParams(0, dp(44), 1f))

        val menu = FrameLayout(this)
        menu.background = roundRect(p.bg, dp(14).toFloat(), p.line, max(1, dp(1)))
        menu.addView(
            Glyph(this, Glyph.MENU, p.text, 0.11f),
            FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER)
        )
        menu.setOnClickListener {
            closePanel()
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        menu.pressScale()
        val menuLp = LinearLayout.LayoutParams(dp(44), dp(44))
        menuLp.leftMargin = dp(8)
        footer.addView(menu, menuLp)
        body.addView(footer, LinearLayout.LayoutParams(MATCH, WRAP))

        val idx = if (side == Prefs.SIDE_RIGHT) root.childCount else 0
        val bl = LinearLayout.LayoutParams(wPx, WRAP)
        bl.gravity = Gravity.CENTER_VERTICAL
        if (side == Prefs.SIDE_RIGHT) bl.rightMargin = gapPx() else bl.leftMargin = gapPx()
        root.addView(body, idx, bl)
        panelBody = body
    }

    private fun appLabel(pkg: String): String {
        return try {
            val info = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            pkg.substringAfterLast('.')
        }
    }

    private fun makeTile(
        p: Pal, pkg: String, icon: Drawable, cellW: Int, iconSz: Int, badge: Int,
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
                FrameLayout.LayoutParams(dp(5), dp(5), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            )
            val kill = FrameLayout(this)
            kill.background = ovalBg(p.accent, p.surface, max(1, dp(1)))
            kill.addView(
                Glyph(this, Glyph.CLOSE, p.onAccent, 0.16f),
                FrameLayout.LayoutParams(badge, badge, Gravity.CENTER)
            )
            kill.setOnClickListener { killApp(pkg) }
            iconBox.addView(kill, FrameLayout.LayoutParams(badge, badge, Gravity.TOP or Gravity.RIGHT))
        }

        if (mode == Prefs.MODE_AUTO && ffReady) {
            val ff = FrameLayout(this)
            ff.background = ovalBg(p.accent, p.surface, max(1, dp(1)))
            ff.addView(
                Glyph(this, Glyph.WINDOW, p.onAccent),
                FrameLayout.LayoutParams(badge, badge, Gravity.CENTER)
            )
            ff.setOnClickListener { launchApp(pkg, true) }
            iconBox.addView(ff, FrameLayout.LayoutParams(badge, badge, Gravity.BOTTOM or Gravity.RIGHT))
        }

        cell.addView(iconBox, LinearLayout.LayoutParams(MATCH, dp(49)))
        val label = tv(appLabel(pkg), 10.5f, p.text)
        label.gravity = Gravity.CENTER
        label.maxLines = 1
        label.ellipsize = android.text.TextUtils.TruncateAt.END
        cell.addView(label, LinearLayout.LayoutParams(MATCH, dp(18)))

        cell.setOnClickListener { launchApp(pkg, mode == Prefs.MODE_FREEFORM) }
        cell.contentDescription = appLabel(pkg)
        cell.pressScale()
        return cell
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
