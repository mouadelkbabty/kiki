package com.kiki.tv

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.TextView

/** A huge emoji in the middle of the screen for 4 seconds. Never takes the remote's focus. Main thread only. */
class EmojiOverlay(private val context: Context) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var view: TextView? = null
    private val remove = Runnable { hide() }

    fun show(emoji: String) {
        if (!Settings.canDrawOverlays(context)) return
        hide()
        val text = TextView(context).apply {
            this.text = emoji
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 160f)
            scaleX = 0.3f
            scaleY = 0.3f
            alpha = 0f
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.CENTER }
        windowManager.addView(text, params)
        view = text
        text.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(450).setInterpolator(OvershootInterpolator(2.5f)).start()
        handler.postDelayed(remove, SHOW_MS)
    }

    private fun hide() {
        handler.removeCallbacks(remove)
        val current = view ?: return
        view = null
        current.animate().cancel()
        current.animate().alpha(0f).scaleX(1.4f).scaleY(1.4f).setDuration(300).withEndAction {
            if (current.isAttachedToWindow) windowManager.removeView(current)
        }.start()
    }

    private companion object {
        const val SHOW_MS = 4_000L
    }
}
