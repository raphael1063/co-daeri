package com.lrlr.codaeri

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
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
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
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
    private var root: FrameLayout? = null
    private var card: LinearLayout? = null
    private var motion: ValueAnimator? = null
    /** 지금 보이는 모양. null 은 아직 모양을 정하지 않았다는 뜻이다. */
    private var shown: Boolean? = null
    private var fraction = 0f
    private var icon: ImageView? = null
    private var title: TextView? = null
    private var dots: TextView? = null
    private var detail: TextView? = null
    private var detailBox: FrameLayout? = null
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
            motion?.cancel(); motion = null
            root = null; card = null; shown = null
        }
    }

    /**
     * 접힌 모양(한 줄, 내용만큼의 너비)과 펼친 모양(두 줄, 빈 자리 전체) 사이를 오간다.
     * 창 크기를 프레임마다 바꾸면 어긋나 보이므로, 움직이는 동안에는 창을 펼친 크기로 두고
     * 그 안에서 오른쪽 위에 붙은 카드만 늘이고 줄인다. 다 접힌 뒤에 창을 카드 크기로 줄인다.
     */
    private fun resize(expanded: Boolean) {
        val p = params ?: return
        val v = root ?: return
        val c = card ?: return
        if (shown == expanded) return
        val first = shown == null
        shown = expanded
        motion?.cancel()
        motion = null
        if (first || !c.isLaidOut || c.width == 0) { settle(expanded); return }
        val full = fullWidth()
        // 접힌 너비는 접힌 모양으로 한 번 재어 본다.
        val from = fraction
        style(0f)
        c.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(dp(FOLDED_H), View.MeasureSpec.EXACTLY))
        val folded = minOf(c.measuredWidth, full)
        style(from)
        p.width = full
        p.height = dp(OPEN_H)
        wm.updateViewLayout(v, p)
        motion = ValueAnimator.ofFloat(from, if (expanded) 1f else 0f).apply {
            duration = 320
            interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
            addUpdateListener { a ->
                val f = a.animatedValue as Float
                style(f)
                c.layoutParams = FrameLayout.LayoutParams(
                    folded + ((full - folded) * f).toInt(), dp(FOLDED_H) + ((dp(OPEN_H) - dp(FOLDED_H)) * f).toInt(), Gravity.TOP or Gravity.END,
                )
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) { if (!cancelled && root === v) settle(expanded) }
            })
            start()
        }
    }

    /** 움직임 없이 그 모양으로 둔다. 접힌 모양에서는 창도 카드만큼만 차지해 아래 화면을 가리지 않는다. */
    private fun settle(expanded: Boolean) {
        val p = params ?: return
        val v = root ?: return
        val c = card ?: return
        style(if (expanded) 1f else 0f)
        c.layoutParams = FrameLayout.LayoutParams(
            if (expanded) FrameLayout.LayoutParams.MATCH_PARENT else FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.MATCH_PARENT, Gravity.TOP or Gravity.END,
        )
        p.width = if (expanded) fullWidth() else WindowManager.LayoutParams.WRAP_CONTENT
        p.height = dp(if (expanded) OPEN_H else FOLDED_H)
        wm.updateViewLayout(v, p)
    }

    /** 0 = 접힌 모양, 1 = 펼친 모양. 아이콘과 글자 크기도 그 사이에서 함께 바뀐다. */
    private fun style(f: Float) {
        fraction = f
        val iconSize = dp(64f + 32f * f)
        icon?.layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply { marginEnd = dp(16f + 8f * f) }
        val titleSize = 32f + 10f * f
        title?.setTextSize(TypedValue.COMPLEX_UNIT_SP, titleSize)
        dots?.setTextSize(TypedValue.COMPLEX_UNIT_SP, titleSize)
        // 둘째 줄은 자리의 높이가 줄면서 첫째 줄 아래로 밀려 올라가 사라진다(아래에 붙여 두어 위쪽부터 잘린다).
        val box = detailBox
        val line = detail
        if (box != null && line != null) {
            val h = line.lineHeight + dp(6f)
            line.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, h, Gravity.BOTTOM)
            box.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (h * f).toInt())
            box.visibility = if (f > 0f) View.VISIBLE else View.GONE
            // 옅은 글자라 그대로 비례해 줄이면 너무 일찍 안 보인다. 끝까지 보이다가 마지막에 옅어지게 한다.
            line.alpha = kotlin.math.sqrt(f)
        }
    }

    private fun fullWidth(): Int = minOf(dp(931f), context.resources.displayMetrics.widthPixels - dp(184f))

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
        val box = FrameLayout(context).apply { addView(detailView) }
        val head = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(titleView); addView(dotsView)
        }
        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(head); addView(box)
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
        ).apply {
            gravity = Gravity.TOP or Gravity.END; x = dp(160f); y = dp(24f)
            // 접히고 펼칠 때 창이 미끄러지듯 움직여 보이지 않게, 창 이동과 등장 애니메이션을 끈다.
            windowAnimations = 0
            if (android.os.Build.VERSION.SDK_INT >= 34) setCanPlayMoveAnimation(false)
        }
        val frame = FrameLayout(context).apply {
            addView(v, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.TOP or Gravity.END))
        }
        wm.addView(frame, p)
        root = frame; card = v; shown = null; fraction = 0f; icon = iconView; title = titleView; dots = dotsView; detail = detailView; detailBox = box; params = p; night = dark
    }

    override fun hide() {
        handler.removeCallbacks(animate)
        val v = root ?: return
        motion?.cancel(); motion = null
        root = null; card = null; shown = null; icon = null; title = null; dots = null; detail = null; detailBox = null; params = null
        try { wm.removeView(v) } catch (_: Exception) {}
    }

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics).toInt()

    private companion object {
        const val FOLDED_H = 100f
        const val OPEN_H = 150f
        val DOTS = arrayOf("   ", ".  ", ".. ", "...")
    }
}
