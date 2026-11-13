package com.lrlr.codaeri

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.lrlr.codaeri.app.BannerLine
import com.lrlr.codaeri.app.TripBanner

/**
 * 내비게이션 위에 띄우는 작은 표시(다른 앱 위에 표시). 누르면 코-대리 화면이 열리고, ✕ 를 누르면 내려간다.
 * 앱 화면과 같은 바탕·글자색에 앱 아이콘을 붙여 코-대리의 표시임을 알 수 있게 하고,
 * 내비게이션의 단추와 카드처럼 모서리가 각지고 그림자 없이 가는 테두리만 둔다.
 *
 * 평소에는 오른쪽 위(2D 단추 왼쪽)에 그 단추와 같은 높이의 한 줄로 접혀 있고,
 * 알릴 내용이 있을 때만 왼쪽으로 늘어나 두 줄을 크게 보여 준다(회전 안내 카드와 2D 단추 사이, 회전 안내 카드의 구분선 높이까지).
 * 크기는 순정 내비게이션 화면(2560x1440) 배치에 맞춘 값이다.
 * "다른 앱 위에 표시" 권한이 없으면 아무것도 띄우지 않는다.
 */
class OverlayBanner(private val context: Context) : TripBanner {
    override var onDismiss: (() -> Unit)? = null

    private val wm = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var root: LinearLayout? = null
    private var icon: ImageView? = null
    private var title: TextView? = null
    private var dots: TextView? = null
    private var detail: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var night: Boolean? = null
    private var tick = 0
    private val animate = object : Runnable {
        override fun run() {
            // 자리가 흔들리지 않게 점 셋의 너비는 그대로 두고 보이는 개수만 바꾼다.
            tick = (tick + 1) % 4
            dots?.text = DOTS[tick]
            handler.postDelayed(this, 500)
        }
    }

    override fun available(): Boolean = try { Settings.canDrawOverlays(context) } catch (_: Exception) { false }

    override fun show(line: BannerLine, expanded: Boolean) {
        if (!available()) return
        try {
            val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            if (root != null && night != dark) hide()
            if (root == null) build(dark)
            title?.text = line.title
            detail?.text = line.detail
            resize(expanded)
            handler.removeCallbacks(animate)
            if (line.ongoing) {
                tick = 0
                dots?.text = DOTS[0]
                dots?.visibility = View.VISIBLE
                handler.postDelayed(animate, 500)
            } else {
                dots?.visibility = View.GONE
            }
        } catch (e: Exception) {
            Log.w("OverlayBanner", "show failed", e)
            root = null
        }
    }

    /** 접힌 모양(한 줄, 내용만큼의 너비)과 펼친 모양(두 줄, 빈 자리 전체) 사이를 오간다. */
    private fun resize(expanded: Boolean) {
        val p = params ?: return
        val v = root ?: return
        val full = minOf(dp(931f), context.resources.displayMetrics.widthPixels - dp(184f))
        val width = if (expanded) full else WindowManager.LayoutParams.WRAP_CONTENT
        val height = dp(if (expanded) 150f else 100f)
        val changed = p.width != width || p.height != height
        p.width = width
        p.height = height
        // 창 크기가 바뀌는 사이 한 프레임 동안 어긋난 자리에 그려지는 것이 보이지 않게, 잠깐 감췄다가 자리를 잡은 뒤 보인다.
        if (changed && v.isAttachedToWindow) {
            v.alpha = 0f
            handler.postDelayed({ root?.alpha = 1f }, 80)
        }
        val iconSize = dp(if (expanded) 96f else 64f)
        icon?.layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply { marginEnd = dp(if (expanded) 24f else 16f) }
        val titleSize = if (expanded) 42f else 32f
        title?.setTextSize(TypedValue.COMPLEX_UNIT_SP, titleSize)
        dots?.setTextSize(TypedValue.COMPLEX_UNIT_SP, titleSize)
        detail?.visibility = if (expanded) View.VISIBLE else View.GONE
        wm.updateViewLayout(v, p)
    }

    private fun build(dark: Boolean) {
        // 앱 화면(Theme.kt)과 같은 바탕과 글자색. 밝은 화면의 테두리는 내비게이션 단추의 테두리와 같은 색.
        val surface = if (dark) 0xFF313236.toInt() else 0xFFFFFFFF.toInt()
        val ink = if (dark) 0xFFFFFFFF.toInt() else 0xFF131417.toInt()
        val sub = if (dark) 0xA3FFFFFF.toInt() else 0xA3131417.toInt()
        val line = if (dark) 0xFF44464E.toInt() else 0xFFBDBDBD.toInt()

        val iconView = ImageView(context).apply { setImageResource(R.drawable.ic_app_clear) }
        val titleView = TextView(context).apply { setTextColor(ink); typeface = Typeface.DEFAULT_BOLD; maxLines = 1 }
        val dotsView = TextView(context).apply { setTextColor(ink); typeface = Typeface.MONOSPACE; maxLines = 1; setPadding(dp(4f), 0, 0, 0) }
        val detailView = TextView(context).apply {
            setTextColor(sub); setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        val head = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(titleView); addView(dotsView)
        }
        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(head); addView(detailView)
        }
        // 닫기: 누를 자리를 넉넉히 두고 가는 선으로 본문과 나눈다.
        val divider = View(context).apply {
            setBackgroundColor(line)
            layoutParams = LinearLayout.LayoutParams(dp(1f), LinearLayout.LayoutParams.MATCH_PARENT).apply { marginStart = dp(20f) }
        }
        val close = TextView(context).apply {
            text = "✕"
            contentDescription = "표시 닫기"
            setTextColor(sub); setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f); gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(96f), LinearLayout.LayoutParams.MATCH_PARENT)
            setOnClickListener { onDismiss?.invoke() }
        }
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24f), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            addView(iconView); addView(texts)
            setOnClickListener {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                )
            }
        }
        val v = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { setColor(surface); setStroke(dp(1f), line) }
            addView(body); addView(divider); addView(close)
        }
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, dp(100f),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.END; x = dp(160f); y = dp(24f) }
        wm.addView(v, p)
        root = v; icon = iconView; title = titleView; dots = dotsView; detail = detailView; params = p; night = dark
    }

    override fun hide() {
        handler.removeCallbacks(animate)
        val v = root ?: return
        root = null; icon = null; title = null; dots = null; detail = null; params = null
        try { wm.removeView(v) } catch (_: Exception) {}
    }

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics).toInt()

    private companion object {
        val DOTS = arrayOf("   ", ".  ", ".. ", "...")
    }
}
