package com.nexora.smartpanel

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.TextView

/** Palet hitam / putih. Tidak ada warna lain. */
class Pal(
    val dark: Boolean,
    val bg: Int,
    val surface: Int,
    val text: Int,
    val sub: Int,
    val line: Int
) {
    val accent: Int get() = text
    val onAccent: Int get() = bg

    companion object {
        fun of(c: Context): Pal {
            val dark = when (Prefs.theme(c)) {
                Prefs.THEME_BLACK -> true
                Prefs.THEME_WHITE -> false
                else -> (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
            }
            return if (dark) {
                Pal(true, 0xFF0D0D0D.toInt(), 0xFF1B1B1B.toInt(), 0xFFF4F4F4.toInt(), 0xFF9B9B9B.toInt(), 0x24FFFFFF)
            } else {
                Pal(false, 0xFFF5F5F3.toInt(), 0xFFFFFFFF.toInt(), 0xFF141414.toInt(), 0xFF7A7A7A.toInt(), 0x16000000)
            }
        }
    }
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

fun withAlpha(color: Int, a: Int): Int = (color and 0x00FFFFFF) or (a shl 24)

fun roundRect(color: Int, radiusPx: Float, strokeColor: Int = 0, strokePx: Int = 0): GradientDrawable {
    val g = GradientDrawable()
    g.setColor(color)
    g.cornerRadius = radiusPx
    if (strokePx > 0) g.setStroke(strokePx, strokeColor)
    return g
}

fun ovalBg(color: Int, strokeColor: Int = 0, strokePx: Int = 0): GradientDrawable {
    val g = GradientDrawable()
    g.shape = GradientDrawable.OVAL
    g.setColor(color)
    if (strokePx > 0) g.setStroke(strokePx, strokeColor)
    return g
}

/** Salinan drawable supaya bisa dipakai di beberapa view sekaligus. */
fun fresh(d: Drawable): Drawable = d.constantState?.newDrawable()?.mutate() ?: d

fun Context.tv(text: String, sp: Float, color: Int, medium: Boolean = false): TextView {
    val t = TextView(this)
    t.text = text
    t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    t.setTextColor(color)
    t.typeface = Typeface.create(if (medium) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
    return t
}

/** Teks judul besar dengan huruf serif, supaya terasa lembut dan elegan. */
fun Context.serif(text: String, sp: Float, color: Int): TextView {
    val t = tv(text, sp, color)
    t.typeface = Typeface.create("serif", Typeface.NORMAL)
    return t
}

/** Efek tekan: sedikit memudar saat disentuh. */
@SuppressLint("ClickableViewAccessibility")
fun View.pressFade() {
    setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> v.animate().alpha(0.55f).setDuration(60).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().alpha(1f).setDuration(120).start()
        }
        false
    }
}

/** Efek tekan: mengecil sedikit saat disentuh. */
@SuppressLint("ClickableViewAccessibility")
fun View.pressScale() {
    setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        }
        false
    }
}

// ---------------------------------------------------------------------- liquid glass

/**
 * Latar kaca: isi tembus pandang, kilau di bagian atas, dan bingkai bergradasi
 * (terang di sudut kiri atas dan kanan bawah, redup di tengah).
 */
class GlassDrawable(
    private val pal: Pal,
    private val radii: FloatArray,
    private val baseAlpha: Int,
    private val rimPx: Float
) : Drawable() {

    constructor(pal: Pal, radius: Float, baseAlpha: Int, rimPx: Float) :
        this(pal, FloatArray(8) { radius }, baseAlpha, rimPx)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    init {
        rim.style = Paint.Style.STROKE
        rim.strokeWidth = rimPx
    }

    override fun onBoundsChange(b: Rect) {
        super.onBoundsChange(b)
        val half = rimPx / 2f
        rect.set(b.left + half, b.top + half, b.right - half, b.bottom - half)
        path.reset()
        path.addRoundRect(rect, radii, Path.Direction.CW)

        if (pal.dark) {
            fill.color = Color.argb(baseAlpha, 0, 0, 0)
            tint.color = Color.argb(0x1C, 255, 255, 255)
        } else {
            fill.color = Color.argb(baseAlpha, 255, 255, 255)
            tint.color = Color.argb(0x0C, 0, 0, 0)
        }

        val h = rect.height().coerceAtLeast(1f)
        val sheenTop = if (pal.dark) Color.argb(0x40, 255, 255, 255) else Color.argb(0xC0, 255, 255, 255)
        sheen.shader = LinearGradient(
            0f, rect.top, 0f, rect.top + h * 0.6f,
            sheenTop, Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP
        )

        val c1: Int
        val c2: Int
        val c3: Int
        if (pal.dark) {
            c1 = Color.argb(0xC0, 255, 255, 255)
            c2 = Color.argb(0x14, 255, 255, 255)
            c3 = Color.argb(0x78, 255, 255, 255)
        } else {
            c1 = Color.argb(0x4A, 0, 0, 0)
            c2 = Color.argb(0x0C, 0, 0, 0)
            c3 = Color.argb(0x30, 0, 0, 0)
        }
        rim.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(c1, c2, c3), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(path, fill)
        canvas.drawPath(path, tint)
        canvas.save()
        canvas.clipPath(path)
        canvas.drawRect(rect, sheen)
        canvas.restore()
        canvas.drawPath(path, rim)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** Tombol kaca berbentuk kapsul. strong = isi lebih pekat. */
fun Context.glassBtn(p: Pal, label: String, strong: Boolean = false, onClick: () -> Unit): TextView {
    val t = tv(label, 14f, p.text, true)
    t.gravity = Gravity.CENTER
    t.setPadding(dp(18), dp(11), dp(18), dp(11))
    val base = if (strong) (if (p.dark) 0x66 else 0xE0) else (if (p.dark) 0x2A else 0x90)
    t.background = GlassDrawable(p, dp(24).toFloat(), base, dp(1).toFloat().coerceAtLeast(1f))
    t.setOnClickListener { onClick() }
    t.pressFade()
    return t
}

// ---------------------------------------------------------------------- ikon garis

/** Ikon sederhana yang digambar langsung, supaya tajam di ukuran berapa pun. */
class Glyph(ctx: Context, var kind: Int, var color: Int, private val weight: Float = 0.11f) : View(ctx) {
    companion object {
        const val PLUS = 0
        const val CLOSE = 1
        const val CHECK = 2
        const val MENU = 3
        const val WINDOW = 4
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    init {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val s = minOf(w, h)
        val cx = w / 2f
        val cy = h / 2f
        p.color = color
        p.strokeWidth = maxOf(s * weight, 1.5f)
        when (kind) {
            PLUS -> {
                val r = s * 0.26f
                c.drawLine(cx - r, cy, cx + r, cy, p)
                c.drawLine(cx, cy - r, cx, cy + r, p)
            }
            CLOSE -> {
                val r = s * 0.2f
                c.drawLine(cx - r, cy - r, cx + r, cy + r, p)
                c.drawLine(cx - r, cy + r, cx + r, cy - r, p)
            }
            CHECK -> {
                path.reset()
                path.moveTo(cx - s * 0.22f, cy + s * 0.02f)
                path.lineTo(cx - s * 0.06f, cy + s * 0.18f)
                path.lineTo(cx + s * 0.24f, cy - s * 0.16f)
                c.drawPath(path, p)
            }
            MENU -> {
                val r = s * 0.24f
                val gap = s * 0.2f
                c.drawLine(cx - r, cy - gap, cx + r, cy - gap, p)
                c.drawLine(cx - r, cy, cx + r, cy, p)
                c.drawLine(cx - r, cy + gap, cx + r, cy + gap, p)
            }
            WINDOW -> {
                // dua jendela bertumpuk
                val a = s * 0.26f
                val b = s * 0.2f
                val back = RectF(cx - a, cy - a, cx + b, cy + b)
                val front = RectF(cx - b, cy - b, cx + a, cy + a)
                p.strokeWidth = maxOf(s * 0.09f, 1.2f)
                c.drawRoundRect(back, s * 0.06f, s * 0.06f, p)
                c.drawRoundRect(front, s * 0.06f, s * 0.06f, p)
            }
        }
    }
}

/** Saklar hitam-putih. */
class Toggle(ctx: Context, private val pal: Pal, var on: Boolean, private val onChange: (Boolean) -> Unit) : View(ctx) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    init {
        setOnClickListener {
            on = !on
            invalidate()
            onChange(on)
        }
        pressFade()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(context.dp(50), context.dp(30))
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val sw = context.dp(1).toFloat().coerceAtLeast(1f) * 1.5f
        rect.set(sw / 2f, sw / 2f, w - sw / 2f, h - sw / 2f)
        if (on) {
            track.style = Paint.Style.FILL
            track.color = pal.text
            c.drawRoundRect(rect, h / 2f, h / 2f, track)
            knob.color = pal.bg
        } else {
            track.style = Paint.Style.STROKE
            track.strokeWidth = sw
            track.color = pal.sub
            c.drawRoundRect(rect, h / 2f, h / 2f, track)
            knob.color = pal.sub
        }
        val r = h * 0.28f
        val cx = if (on) w - h / 2f else h / 2f
        c.drawCircle(cx, h / 2f, r, knob)
    }
}

/** Lingkaran pilihan (radio). */
class Radio(ctx: Context, private val pal: Pal, private val selected: Boolean) : View(ctx) {
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val s = minOf(width, height).toFloat()
        val sw = context.dp(2).toFloat()
        ring.style = Paint.Style.STROKE
        ring.strokeWidth = sw
        ring.color = if (selected) pal.text else pal.sub
        c.drawCircle(width / 2f, height / 2f, s / 2f - sw / 2f, ring)
        if (selected) {
            dot.color = pal.text
            c.drawCircle(width / 2f, height / 2f, s * 0.2f, dot)
        }
    }
}

/** Tanda centang: lingkaran penuh dengan centang, atau lingkaran kosong. */
class Mark(ctx: Context, private val pal: Pal) : View(ctx) {
    var on: Boolean = false
        set(v) {
            field = v
            invalidate()
        }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chk = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    init {
        chk.style = Paint.Style.STROKE
        chk.strokeCap = Paint.Cap.ROUND
        chk.strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(c: Canvas) {
        val s = minOf(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        if (on) {
            fillP.color = pal.text
            c.drawCircle(cx, cy, s / 2f, fillP)
            chk.color = pal.bg
            chk.strokeWidth = s * 0.1f
            path.reset()
            path.moveTo(cx - s * 0.2f, cy + s * 0.01f)
            path.lineTo(cx - s * 0.05f, cy + s * 0.16f)
            path.lineTo(cx + s * 0.22f, cy - s * 0.14f)
            c.drawPath(path, chk)
        } else {
            val sw = context.dp(2).toFloat()
            ring.style = Paint.Style.STROKE
            ring.strokeWidth = sw
            ring.color = pal.sub
            c.drawCircle(cx, cy, s / 2f - sw / 2f, ring)
        }
    }
}

// ---------------------------------------------------------------------- tepi layar (edge-to-edge)

/**
 * Menggambar sampai ke belakang status bar / navigation bar, lalu memberi tahu besar inset-nya.
 * Panggil SETELAH setContentView.
 */
@Suppress("DEPRECATION")
fun Activity.edgeToEdge(p: Pal, root: View, onInsets: (l: Int, t: Int, r: Int, b: Int) -> Unit) {
    val w = window
    w.setBackgroundDrawable(ColorDrawable(p.bg))
    if (Build.VERSION.SDK_INT >= 30) {
        w.setDecorFitsSystemWindows(false)
        w.statusBarColor = Color.TRANSPARENT
        w.navigationBarColor = Color.TRANSPARENT
        val c = w.insetsController
        if (c != null) {
            val m = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            c.setSystemBarsAppearance(if (p.dark) 0 else m, m)
        }
    } else {
        var f = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        if (!p.dark) {
            if (Build.VERSION.SDK_INT >= 23) f = f or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            if (Build.VERSION.SDK_INT >= 26) f = f or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        w.decorView.systemUiVisibility = f
        w.statusBarColor = Color.TRANSPARENT
        w.navigationBarColor = Color.TRANSPARENT
    }
    root.setOnApplyWindowInsetsListener { _, ins ->
        if (Build.VERSION.SDK_INT >= 30) {
            val i = ins.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            onInsets(i.left, i.top, i.right, i.bottom)
        } else {
            onInsets(
                ins.systemWindowInsetLeft, ins.systemWindowInsetTop,
                ins.systemWindowInsetRight, ins.systemWindowInsetBottom
            )
        }
        ins
    }
    root.requestApplyInsets()
}
