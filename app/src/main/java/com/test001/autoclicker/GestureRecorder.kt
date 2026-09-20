package com.test001.autoclicker

import android.view.MotionEvent

/** Collects raw MotionEvents into a point list while recording is ON. */
object GestureRecorder {
    @Volatile var recording = false
        private set
    private val points = ArrayList<GesturePoint>()
    private var t0 = 0L
    private var sx = 0f
    private var sy = 0f

    fun start() {
        points.clear()
        t0 = System.currentTimeMillis()
        recording = true
    }

    fun onEvent(e: MotionEvent) {
        if (!recording) return
        val t = System.currentTimeMillis() - t0
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                sx = e.x; sy = e.y
                points.add(GesturePoint(e.x, e.y, t, "DOWN"))
            }
            MotionEvent.ACTION_MOVE -> {
                // Android batches movement: one MOVE event carries several historical
                // samples. Save EACH sample with its true timestamp, else replay is slow-mo.
                for (i in 0 until e.historySize) {
                    val ht = e.getHistoricalEventTime(i) - e.downTime + (e.eventTime - e.downTime)
                    val rt = e.getHistoricalEventTime(i) - System.currentTimeMillis() + System.currentTimeMillis()
                    val tRel = (e.getHistoricalEventTime(i) - t0).let { if (it < 0) t else it }
                    points.add(GesturePoint(e.getHistoricalX(i), e.getHistoricalY(i),
                        (e.getHistoricalEventTime(i) - t0).coerceAtLeast(0), "MOVE"))
                }
                points.add(GesturePoint(e.x, e.y, t, "MOVE"))
            }
            MotionEvent.ACTION_UP -> points.add(GesturePoint(e.x, e.y, t, "UP"))
        }
    }

    /** Stop and return the recorded gesture (null if unusable). */
    fun stop(name: String): Gesture? {
        recording = false
        if (points.size < 2) return null
        return Gesture(name, ArrayList(points), sx, sy)
    }
}
