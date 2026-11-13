package com.nexora.smartpanel

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import kotlin.math.abs
import kotlin.math.min

/**
 * Daftar aplikasi vertikal tanpa kotak: ikon di tengah paling besar,
 * mengecil dan memudar ke arah atas dan bawah. Berhenti otomatis tepat di tengah.
 */
class WheelPicker(
    ctx: Context,
    private val pal: Pal,
    private val viewportPx: Int,
    private val itemPx: Int,
    private val iconPx: Int,
    private val isAdded: (String) -> Boolean,
    private val onToggle: (String) -> Unit,
    private val onCenter: (String) -> Unit
) : ScrollView(ctx) {

    private val col = LinearLayout(ctx)
    private val items = ArrayList<FrameLayout>()
    private val badges = ArrayList<View>()
    private val pkgs = ArrayList<String>()
    private val labels = ArrayList<String>()
    private var touching = false
    private var lastCenter = -1
    private val snap = Runnable { snapNow() }

    init {
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        col.orientation = LinearLayout.VERTICAL
        addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    fun setApps(list: List<AppIndex.Entry>) {
        col.removeAllViews()
        items.clear()
        badges.clear()
        pkgs.clear()
        labels.clear()
        lastCenter = -1

        val spacer = ((viewportPx - itemPx) / 2).coerceAtLeast(0)
        col.addView(View(context), LinearLayout.LayoutParams(1, spacer))

        val badgeSz = (iconPx * 0.36f).toInt().coerceAtLeast(context.dp(14))
        for (e in list) {
            val f = FrameLayout(context)
            val iv = ImageView(context)
            iv.setImageDrawable(fresh(e.icon))
            f.addView(iv, FrameLayout.LayoutParams(iconPx, iconPx, Gravity.CENTER))

            val badge = FrameLayout(context)
            badge.background = ovalBg(pal.accent, pal.bg, context.dp(2))
            val g = Glyph(context, Glyph.CHECK, pal.onAccent, 0.13f)
            badge.addView(g, FrameLayout.LayoutParams(badgeSz, badgeSz, Gravity.CENTER))
            val bl = FrameLayout.LayoutParams(badgeSz, badgeSz, Gravity.CENTER)
            f.addView(badge, bl)
            badge.translationX = iconPx * 0.36f
            badge.translationY = iconPx * 0.36f
            badge.visibility = if (isAdded(e.pkg)) View.VISIBLE else View.GONE

            f.setOnClickListener {
                onToggle(e.pkg)
                badge.visibility = if (isAdded(e.pkg)) View.VISIBLE else View.GONE
            }
            col.addView(f, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, itemPx))
            items.add(f)
            badges.add(badge)
            pkgs.add(e.pkg)
            labels.add(e.label)
        }
        col.addView(View(context), LinearLayout.LayoutParams(1, spacer))
        post { applyEffect() }
    }

    private fun applyEffect() {
        if (items.isEmpty()) return
        val half = viewportPx / 2f
        val vc = scrollY + half
        var best = -1
        var bestD = Float.MAX_VALUE
        for (i in items.indices) {
            val v = items[i]
            val c = v.top + v.height / 2f
            val dist = abs(c - vc)
            if (dist < bestD) {
                bestD = dist
                best = i
            }
            val d = dist / half
            if (d > 1.5f) continue
            val t = min(1f, d)
            val k = 1f - t * t
            val s = 0.34f + 0.66f * k
            v.scaleX = s
            v.scaleY = s
            v.alpha = 0.22f + 0.78f * k
        }
        if (best >= 0 && best != lastCenter) {
            lastCenter = best
            onCenter(labels[best])
        }
    }

    private fun snapNow() {
        if (touching || items.isEmpty()) return
        val half = viewportPx / 2f
        val vc = scrollY + half
        var best = 0
        var bestD = Float.MAX_VALUE
        for (i in items.indices) {
            val v = items[i]
            val d = abs(v.top + v.height / 2f - vc)
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        val v = items[best]
        val target = (v.top + v.height / 2f - half).toInt()
        if (abs(target - scrollY) > 1) smoothScrollTo(0, target)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        applyEffect()
        removeCallbacks(snap)
        postDelayed(snap, 110)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touching = true
                removeCallbacks(snap)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touching = false
                removeCallbacks(snap)
                postDelayed(snap, 160)
            }
        }
        return super.onTouchEvent(ev)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(snap)
        super.onDetachedFromWindow()
    }
}
