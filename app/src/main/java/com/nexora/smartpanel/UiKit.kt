package com.nexora.smartpanel

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
            // v1.6: tema permanen putih-hitam, tidak mengikuti mode sistem.
            return Pal(
                false,
                0xFFFFFFFF.toInt(),
                0xFFFFFFFF.toInt(),
                0xFF000000.toInt(),
                0xFF666666.toInt(),
                0xFFE2E2E2.toInt()
            )
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

/** Judul modern dan tegas, konsisten dengan tampilan sistem. */
fun Context.serif(text: String, sp: Float, color: Int): TextView {
    val t = tv(text, sp, color, true)
    t.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
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

/** Tombol solid monokrom. strong = tombol utama. */
fun Context.solidButton(p: Pal, label: String, strong: Boolean = false, onClick: () -> Unit): TextView {
    val t = tv(label, 13.5f, if (strong) p.bg else p.text, true)
    t.gravity = Gravity.CENTER
    t.minimumHeight = dp(38)
    t.setPadding(dp(14), dp(9), dp(14), dp(9))
    t.background = if (strong) {
        roundRect(p.text, dp(13).toFloat())
    } else {
        roundRect(p.surface, dp(13).toFloat(), p.line, maxOf(1, dp(1)))
    }
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
        const val GEAR = 5
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
            GEAR -> {
                val ring = s * 0.17f
                c.drawCircle(cx, cy, ring, p)
                for (i in 0 until 8) {
                    val a = Math.PI * 2.0 * i / 8.0
                    val x1 = cx + (Math.cos(a) * s * 0.27f).toFloat()
                    val y1 = cy + (Math.sin(a) * s * 0.27f).toFloat()
                    val x2 = cx + (Math.cos(a) * s * 0.38f).toFloat()
                    val y2 = cy + (Math.sin(a) * s * 0.38f).toFloat()
                    c.drawLine(x1, y1, x2, y2, p)
                }
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
