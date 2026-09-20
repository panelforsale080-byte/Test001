package com.test001.autoclicker

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import android.os.Handler
import android.os.Looper

/**
 * Replays a recorded gesture through the accessibility service.
 * Supports SMART replay: every recorded point is translated by
 * (anchor - recordedStart) so the gesture lands relative to a new anchor.
 */
object GesturePlayer {
    @Volatile var playing = false
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var loopsLeft = 0
    private var pending: Gesture? = null
    private var anchor: PointF? = null
    private var speed = 1f

    /**
     * @param loops  number of repetitions; -1 = infinite
     * @param speed  0.5 / 1 / 2 — divides the total duration
     */
    fun play(g: Gesture, anchor: PointF?, loops: Int, speed: Float,
             onStatus: (String) -> Unit) {
        val svc = AutoClickAccessibilityService.instance
        if (svc == null) { onStatus("Enable accessibility first"); return }
        if (g.points.size < 2) { onStatus("Empty gesture"); return }
        stop()
        playing = true
        pending = g; this.anchor = anchor; this.speed = speed
        loopsLeft = loops
        dispatchOnce(svc, onStatus)
    }

    private fun dispatchOnce(svc: AutoClickAccessibilityService, onStatus: (String) -> Unit) {
        val g = pending ?: run { playing = false; return }
        val path = Path()
        val dx = (anchor?.x ?: g.startX) - g.startX
        val dy = (anchor?.y ?: g.startY) - g.startY
        g.points.forEachIndexed { i, p ->
            if (i == 0) path.moveTo(p.x + dx, p.y + dy) else path.lineTo(p.x + dx, p.y + dy)
        }
        val dur = (g.duration / speed).toLong().coerceIn(1L, 59000L)
        val stroke = GestureDescription.StrokeDescription(path, 0L, dur)
        val gd = GestureDescription.Builder().addStroke(stroke).build()
        val ok = svc.dispatchGesture(gd, object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (!playing) return
                if (loopsLeft == -1 || --loopsLeft > 0) {
                    handler.postDelayed({ dispatchOnce(svc, onStatus) }, 200)
                } else {
                    playing = false
                    onStatus("done")
                }
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                playing = false
                onStatus("cancelled")
            }
        }, handler)
        if (!ok) { playing = false; onStatus("dispatch rejected") }
        else onStatus("playing ${g.name}${if (loopsLeft == -1) " (∞)" else ""}")
    }

    fun stop() {
        playing = false
        loopsLeft = 0
        handler.removeCallbacksAndMessages(null)
    }
}
