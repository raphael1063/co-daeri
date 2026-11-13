package com.lrlr.codaeri

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import com.lrlr.codaeri.app.TripBanner

/**
 * 내비게이션 위에 띄우는 한 줄(다른 앱 위에 표시). 누르면 코-대리 화면이 열린다.
 * "다른 앱 위에 표시" 권한이 없으면 아무것도 띄우지 않는다. 내비게이션의 회전 안내(왼쪽 위)와 겹치지 않게 오른쪽 위에 두고, 밝은 지도와 어두운 안내 어디서나 보이게 색을 넣는다.
 */
class OverlayBanner(private val context: Context) : TripBanner {
    private val wm = context.getSystemService(WindowManager::class.java)
    private var view: TextView? = null

    override fun available(): Boolean = try { Settings.canDrawOverlays(context) } catch (_: Exception) { false }

    override fun show(text: String) {
        if (!available()) return
        try {
            val existing = view
            if (existing != null) { existing.text = text; return }
            val pad = dp(18f)
            val v = TextView(context).apply {
                this.text = text
                setTextColor(0xFFFFFFFF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                maxLines = 1
                setPadding(pad, pad / 2, pad, pad / 2)
                background = GradientDrawable().apply {
                    setColor(0xFF0F766E.toInt()); cornerRadius = dp(28f).toFloat(); setStroke(dp(2f), 0xFFFFFFFF.toInt())
                }
                elevation = dp(6f).toFloat()
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
            ).apply { gravity = Gravity.TOP or Gravity.END; x = dp(110f); y = dp(64f) }
            wm.addView(v, params)
            view = v
        } catch (e: Exception) {
            Log.w("OverlayBanner", "show failed", e)
            view = null
        }
    }

    override fun hide() {
        val v = view ?: return
        view = null
        try { wm.removeView(v) } catch (_: Exception) {}
    }

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics).toInt()
}
