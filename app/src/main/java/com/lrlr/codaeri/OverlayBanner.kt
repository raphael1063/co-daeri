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
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.lrlr.codaeri.app.BannerLine
import com.lrlr.codaeri.app.TripBanner

/**
 * 내비게이션 위에 띄우는 작은 표시(다른 앱 위에 표시). 누르면 코-대리 화면이 열린다.
 * 앱 화면과 같은 바탕·글자색에 앱 아이콘을 붙여 코-대리의 표시임을 알 수 있게 한다.
 * 내비게이션의 회전 안내(왼쪽 위)와 겹치지 않게 오른쪽 위(2D 단추 왼쪽)에, 그 단추와 같은 높이·모양으로 둔다.
 * "다른 앱 위에 표시" 권한이 없으면 아무것도 띄우지 않는다.
 */
class OverlayBanner(private val context: Context) : TripBanner {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var root: LinearLayout? = null
    private var title: TextView? = null
    private var dots: TextView? = null
    private var detail: TextView? = null
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

    override fun show(line: BannerLine) {
        if (!available()) return
        try {
            val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            if (root != null && night != dark) hide()
            if (root == null) build(dark)
            title?.text = line.title
            detail?.text = line.detail
            handler.removeCallbacks(animate)
            if (line.ongoing) {
                tick = 0
                dots?.text = DOTS[0]
                dots?.visibility = TextView.VISIBLE
                handler.postDelayed(animate, 500)
            } else {
                dots?.visibility = TextView.GONE
            }
        } catch (e: Exception) {
            Log.w("OverlayBanner", "show failed", e)
            root = null
        }
    }

    private fun build(dark: Boolean) {
        // 앱 화면(Theme.kt)과 같은 색: 카드 바탕, 글자, 구분선.
        val surface = if (dark) 0xFF313236.toInt() else 0xFFFFFFFF.toInt()
        val ink = if (dark) 0xFFFFFFFF.toInt() else 0xFF131417.toInt()
        val sub = if (dark) 0xA3FFFFFF.toInt() else 0xA3131417.toInt()
        val line = if (dark) 0xFF44464E.toInt() else 0xFFBDBDBD.toInt() // 밝은 화면에서는 내비게이션 단추의 테두리와 같은 색

        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_app_clear)
            layoutParams = LinearLayout.LayoutParams(dp(56f), dp(56f)).apply { marginEnd = dp(14f) }
        }
        val titleView = TextView(context).apply {
            setTextColor(ink); setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f); typeface = Typeface.DEFAULT_BOLD; maxLines = 1
        }
        val dotsView = TextView(context).apply {
            setTextColor(ink); setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f); typeface = Typeface.MONOSPACE; maxLines = 1
            setPadding(dp(4f), 0, 0, 0)
        }
        val detailView = TextView(context).apply {
            setTextColor(sub); setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f); maxLines = 1
            maxWidth = dp(520f); ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val head = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(titleView); addView(dotsView)
        }
        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(head); addView(detailView)
        }
        val v = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(12f), dp(24f), dp(12f))
            // 내비게이션의 단추와 카드처럼 모서리가 각지고 그림자 없이 가는 테두리만 둔다.
            background = GradientDrawable().apply { setColor(surface); setStroke(dp(1f), line) }
            minimumHeight = dp(100f)
            addView(icon); addView(texts)
            setOnClickListener {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                )
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.END; x = dp(160f); y = dp(24f) }
        wm.addView(v, params)
        root = v; title = titleView; dots = dotsView; detail = detailView; night = dark
    }

    override fun hide() {
        handler.removeCallbacks(animate)
        val v = root ?: return
        root = null; title = null; dots = null; detail = null
        try { wm.removeView(v) } catch (_: Exception) {}
    }

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics).toInt()

    private companion object {
        val DOTS = arrayOf("   ", ".  ", ".. ", "...")
    }
}
