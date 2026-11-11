package com.nexora.smartpanel

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import kotlin.math.max

/** Daftar aplikasi terpasang; ketuk untuk menambah atau melepas dari panel. */
class AddAppsActivity : Activity() {

    private lateinit var p: Pal
    private val all = ArrayList<AppIndex.Entry>()
    private val shown = ArrayList<AppIndex.Entry>()
    private lateinit var adapter: BaseAdapter
    private lateinit var empty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        p = Pal.of(this)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(22), dp(18), dp(22), dp(8))

        val title = serif("Tambah ke panel", 32f, p.text)
        root.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(8) })
        root.addView(
            tv("Ketuk aplikasi untuk menambah atau melepas.", 13.5f, p.sub),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(2) }
        )

        val search = EditText(this)
        search.hint = "Cari aplikasi"
        search.inputType = InputType.TYPE_CLASS_TEXT
        search.setSingleLine(true)
        search.setTextColor(p.text)
        search.setHintTextColor(p.sub)
        search.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
        search.background = roundRect(p.surface, dp(20).toFloat(), p.line, max(1, dp(1) / 2))
        search.setPadding(dp(18), dp(12), dp(18), dp(12))
        root.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(18) })

        empty = tv("Memuat aplikasi…", 13.5f, p.sub)
        empty.gravity = Gravity.CENTER
        root.addView(empty, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(18) })

        val list = ListView(this)
        list.divider = null
        list.dividerHeight = 0
        list.selector = ColorDrawable(0)
        list.isVerticalScrollBarEnabled = false
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            .apply { topMargin = dp(8) })

        adapter = object : BaseAdapter() {
            override fun getCount(): Int = shown.size
            override fun getItem(position: Int): Any = shown[position]
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val row = (convertView as? LinearLayout) ?: buildRow()
                val item = shown[position]
                (row.getChildAt(0) as ImageView).setImageDrawable(fresh(item.icon))
                (row.getChildAt(1) as TextView).text = item.label
                (row.getChildAt(2) as Mark).on = Prefs.hasApp(this@AddAppsActivity, item.pkg)
                return row
            }
        }
        list.adapter = adapter

        list.setOnItemClickListener { _, _, pos, _ ->
            val item = shown[pos]
            if (Prefs.hasApp(this, item.pkg)) Prefs.removeApp(this, item.pkg) else Prefs.addApp(this, item.pkg)
            adapter.notifyDataSetChanged()
            if (Prefs.enabled(this)) {
                startService(Intent(this, PanelService::class.java).setAction(PanelService.ACTION_REFRESH))
            }
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filter(s?.toString() ?: "")
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        setContentView(root)
        edgeToEdge(p, root) { l, t, r, b ->
            root.setPadding(dp(22) + l, dp(18) + t, dp(22) + r, dp(8) + b)
        }

        AppIndex.load(this) { list2 ->
            all.clear()
            all.addAll(list2)
            filter(search.text?.toString() ?: "")
        }
    }

    private fun filter(q: String) {
        shown.clear()
        shown.addAll(all.filter { q.isBlank() || it.label.contains(q, ignoreCase = true) })
        empty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        empty.text = if (all.isEmpty()) "Memuat aplikasi…" else "Tidak ada aplikasi yang cocok"
        adapter.notifyDataSetChanged()
    }

    private fun buildRow(): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(9), 0, dp(9))

        row.addView(ImageView(this), LinearLayout.LayoutParams(dp(42), dp(42)))

        val name = tv("", 15f, p.text, true)
        name.maxLines = 1
        name.ellipsize = android.text.TextUtils.TruncateAt.END
        row.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = dp(16)
            rightMargin = dp(12)
        })

        row.addView(Mark(this, p), LinearLayout.LayoutParams(dp(24), dp(24)))
        return row
    }
}
