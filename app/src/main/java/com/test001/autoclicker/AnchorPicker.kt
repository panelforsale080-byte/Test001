package com.test001.autoclicker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PointF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Holds the current smart-replay anchor (null = replay at recorded position). */
object AnchorStore { @Volatile var anchor: PointF? = null }

/**
 * Full-screen overlay with a draggable crosshair; user drags it over the current
 * in-game starting point (e.g. joystick center) and taps SET.
 */
object AnchorPicker {
    fun pick(ctx: Context, onPicked: (PointF) -> Unit) {
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = ctx.resources.displayMetrics
        var cx = dm.widthPixels / 2f
        var cy = dm.heightPixels / 2f

        val cross = object : View(ctx) {
            private val p = Paint().apply { color = Color.GREEN; strokeWidth = 5f; isAntiAlias = true }
            override fun onDraw(c: Canvas) {
                c.drawCircle(cx, cy, 46f, p)
                c.drawLine(cx - 70, cy, cx + 70, cy, p)
                c.drawLine(cx, cy - 70, cx, cy + 70, p)
            }
        }
        val wrap = object : FrameLayout(ctx) {
            override fun onTouchEvent(e: MotionEvent): Boolean {
                if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE) {
                    cx = e.rawX; cy = e.rawY; cross.invalidate()
                }
                return true
            }
        }
        wrap.setBackgroundColor(0x22000000)

        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xEE101014.toInt()); gravity = Gravity.CENTER
        }
        val set = Button(ctx).apply { text = "SET ANCHOR" }
        val cancel = Button(ctx).apply { text = "CANCEL" }
        bar.addView(set); bar.addView(cancel)
        bar.addView(TextView(ctx).apply {
            text = "  drag crosshair, then SET"; setTextColor(Color.WHITE); textSize = 12f
        })

        wrap.addView(cross, FrameLayout.LayoutParams(-1, -1))
        wrap.addView(bar, FrameLayout.LayoutParams(-1, 130, Gravity.BOTTOM))

        val lp = WindowManager.LayoutParams(-1, -1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
        fun close() { try { wm.removeView(wrap) } catch (_: Throwable) {} }
        set.setOnClickListener {
            AnchorStore.anchor = PointF(cx, cy)
            onPicked(PointF(cx, cy)); close()
        }
        cancel.setOnClickListener { close() }
        wm.addView(wrap, lp)
    }
}
