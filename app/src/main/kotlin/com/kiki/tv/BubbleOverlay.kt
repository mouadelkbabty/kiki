package com.kiki.tv

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
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
    private val density = context.resources.displayMetrics.density
    private var root: LinearLayout? = null
    private lateinit var time: TextView
    private lateinit var body: TextView

    override fun show(message: NtfyMessage) {
        if (!Settings.canDrawOverlays(context)) {
            Log.w("kiki", "overlay permission missing, message not shown")
            return
        }
        val fresh = root == null
        val view = root ?: build().also { attach(it) }
        time.text = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(message.time * 1000))
        body.text = message.text
        view.animate().cancel()
        if (fresh) view.translationX = -dp(40)
        view.animate().alpha(1f).translationX(0f).setDuration(FADE_MS).setInterpolator(DecelerateInterpolator()).start()
        view.requestFocus()
    }

    override fun hide() {
        val view = root ?: return
        root = null
        view.animate().cancel()
        view.animate().alpha(0f).translationX(-dp(40)).setDuration(FADE_MS).withEndAction {
            if (view.isAttachedToWindow) windowManager.removeView(view)
        }.start()
    }

    private fun build(): LinearLayout {
        val avatar = TextView(context).apply {
            text = "k"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#5AA0FF"), Color.parseColor("#2F6BFF")),
            ).apply { shape = GradientDrawable.OVAL }
            layoutParams = LinearLayout.LayoutParams(dp(44).toInt(), dp(44).toInt())
        }
        val title = TextView(context).apply {
            text = "kiki"
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        }
        time = TextView(context).apply {
            setTextColor(Color.parseColor("#8C9BB5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        val names = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14).toInt(), 0, 0, 0)
            addView(title)
            addView(time)
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(avatar)
            addView(names)
        }
        body = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setLineSpacing(0f, 1.15f)
            setPadding(0, dp(18).toInt(), 0, dp(20).toInt())
        }
        val footer = TextView(context).apply {
            text = "OK · fermer"
            setTextColor(Color.parseColor("#CFE0FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(14).toInt(), dp(6).toInt(), dp(14).toInt(), dp(6).toInt())
            background = GradientDrawable().apply {
                cornerRadius = dp(20)
                setColor(Color.parseColor("#262F80FF"))
                setStroke(dp(1).toInt(), Color.parseColor("#662F80FF"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24).toInt(), dp(24).toInt(), dp(28).toInt(), dp(24).toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(header)
            addView(body)
            addView(footer)
        }
        val accent = View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#5AA0FF"), Color.parseColor("#2F6BFF")),
            ).apply { cornerRadius = dp(3) }
            layoutParams = LinearLayout.LayoutParams(dp(5).toInt(), LinearLayout.LayoutParams.MATCH_PARENT).apply {
                setMargins(dp(14).toInt(), dp(22).toInt(), 0, dp(22).toInt())
            }
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#EB1B2335"), Color.parseColor("#EB0A0C12")),
            ).apply {
                cornerRadius = dp(28)
                setStroke(dp(1).toInt(), Color.parseColor("#402F80FF"))
            }
            alpha = 0f
            isFocusable = true
            isFocusableInTouchMode = true
            setOnKeyListener { _, _, event ->
                if (event.action == KeyEvent.ACTION_DOWN) onKey()
                true
            }
            addView(accent)
            addView(content)
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

    private fun dp(value: Int): Float = value * density

    private companion object {
        const val WIDTH_PX = 680
        const val FADE_MS = 250L
    }
}
