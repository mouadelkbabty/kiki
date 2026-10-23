package com.kiki.tv

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.kiki.core.BubbleDisplay
import com.kiki.core.NtfyMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The bubble drawn on the left of the screen, over any app. Main thread only. */
class BubbleOverlay(private val context: Context) : BubbleDisplay {
    /** Called when any remote key is pressed while the bubble has focus. */
    var onKey: () -> Unit = {}

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var root: LinearLayout? = null
    private lateinit var header: TextView
    private lateinit var body: TextView

    override fun show(message: NtfyMessage) {
        if (!Settings.canDrawOverlays(context)) {
            Log.w("kiki", "overlay permission missing, message not shown")
            return
        }
        val view = root ?: build().also { attach(it) }
        header.text = "💬 kiki · " + SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(message.time * 1000))
        body.text = message.text
        view.animate().cancel()
        view.animate().alpha(1f).setDuration(FADE_MS).start()
        view.requestFocus()
    }

    override fun hide() {
        val view = root ?: return
        root = null
        view.animate().cancel()
        view.animate().alpha(0f).setDuration(FADE_MS).withEndAction {
            if (view.isAttachedToWindow) windowManager.removeView(view)
        }.start()
    }

    private fun build(): LinearLayout {
        val density = context.resources.displayMetrics.density
        header = TextView(context).apply {
            setTextColor(Color.parseColor("#B3FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        }
        body = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            setPadding(0, (8 * density).toInt(), 0, (12 * density).toInt())
        }
        val footer = TextView(context).apply {
            text = "OK pour fermer"
            setTextColor(Color.parseColor("#80FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * density).toInt()
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#CC000000"))
                cornerRadius = 24 * density
                setStroke((2 * density).toInt(), Color.parseColor("#2F80FF"))
            }
            alpha = 0f
            isFocusable = true
            isFocusableInTouchMode = true
            setOnKeyListener { _, _, event ->
                if (event.action == KeyEvent.ACTION_DOWN) onKey()
                true
            }
            addView(header)
            addView(body)
            addView(footer)
        }
    }

    private fun attach(view: LinearLayout) {
        val params = WindowManager.LayoutParams(
            WIDTH_PX,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            x = 48
        }
        windowManager.addView(view, params)
        root = view
    }

    private companion object {
        const val WIDTH_PX = 640
        const val FADE_MS = 200L
    }
}
