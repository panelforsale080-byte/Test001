package com.test001.autoclicker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Foreground service that hosts the floating control panel and the
 * transparent full-screen recording layer.
 *
 * Flow: Record ON -> transparent capture layer (FLAG_NOT_TOUCH_MODAL) collects
 * raw MotionEvents -> Record OFF -> layer removed -> Save -> JSON file.
 */
class OverlayService : Service() {

    companion object { const val ACTION_STOP = "com.test001.autoclicker.STOP" }

    private lateinit var wm: WindowManager
    private var panel: View? = null
    private var bubble: View? = null
    private var recLayer: View? = null
    private var recLp: WindowManager.LayoutParams? = null
    @Volatile private var injecting = false
    private var lastGesture: Gesture? = null
    private var status: TextView? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(1, notif)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        showPanel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }

    override fun onDestroy() {
        removeRecLayer()
        try { panel?.let { wm.removeView(it) } } catch (_: Throwable) {}
        try { bubble?.let { wm.removeView(it) } } catch (_: Throwable) {}
        panel = null; bubble = null
        super.onDestroy()
    }

    /** Collapse the panel into a tiny draggable bubble. Tap = restore. Long-press = stop service. */
    private fun collapseToBubble() {
        try { panel?.let { wm.removeView(it) } } catch (_: Throwable) {}
        panel = null
        if (bubble != null) return
        val b = TextView(this).apply {
            text = "T1"; textSize = 13f; setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0xDD6B4DBB.toInt())
            }
        }
        val lp = WindowManager.LayoutParams(120, 120,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 30; y = 200 }
        b.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0; var sy = 0; var px = 0f; var py = 0f; var moved = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        sx = lp.x; sy = lp.y; px = e.rawX; py = e.rawY; moved = false
                        v.setOnLongClickListener { stopSelf(); true }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val nx = sx + (e.rawX - px).toInt(); val ny = sy + (e.rawY - py).toInt()
                        if (Math.abs(nx - sx) > 14 || Math.abs(ny - sy) > 14) moved = true
                        lp.x = nx; lp.y = ny
                        try { wm.updateViewLayout(v, lp) } catch (_: Throwable) {}
                    }
                    MotionEvent.ACTION_UP -> { if (!moved) restorePanel() }
                }
                return false  // let long-click fire
            }
        })
        try { wm.addView(b, lp); bubble = b } catch (_: Throwable) {}
    }

    private fun restorePanel() {
        try { bubble?.let { wm.removeView(it) } } catch (_: Throwable) {}
        bubble = null
        if (panel == null) showPanel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------------- Floating panel ----------------

    private fun showPanel() {
        val v = LayoutInflater.from(this).inflate(R.layout.overlay_panel, null)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 40; y = 120 }

        status = v.findViewById(R.id.ov_status)
        val loopsEt = v.findViewById<EditText>(R.id.ov_loops)
        val speedEt = v.findViewById<EditText>(R.id.ov_speed)

        // drag panel by title bar
        v.findViewById<View>(R.id.ov_title).setOnTouchListener(object : View.OnTouchListener {
            var sx = 0; var sy = 0; var px = 0f; var py = 0f
            override fun onTouch(view: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { sx = lp.x; sy = lp.y; px = e.rawX; py = e.rawY }
                    MotionEvent.ACTION_MOVE -> {
                        lp.x = sx + (e.rawX - px).toInt(); lp.y = sy + (e.rawY - py).toInt()
                        try { wm.updateViewLayout(v, lp) } catch (_: Throwable) {}
                    }
                }
                return true
            }
        })

        v.findViewById<View>(R.id.ov_record).setOnClickListener {
            if (GestureRecorder.recording) {
                lastGesture = GestureRecorder.stop("gesture_${System.currentTimeMillis() % 100000}")
                removeRecLayer()
                setStatus("recorded ${lastGesture?.points?.size ?: 0} pts, ${lastGesture?.duration ?: 0}ms — Save or Play")
            } else {
                GestureRecorder.start()
                addRecLayer()
                setStatus("recording… touches pass through (re-dispatched live)")
            }
        }

        v.findViewById<View>(R.id.ov_play).setOnClickListener {
            val g = lastGesture
            if (g == null) { setStatus("nothing recorded"); return@setOnClickListener }
            val loops = loopsEt.text.toString().toIntOrNull() ?: 1
            val speed = speedEt.text.toString().toFloatOrNull()?.coerceIn(0.25f, 4f) ?: 1f
            GesturePlayer.play(g, AnchorStore.anchor, loops, speed) { s -> setStatus(s) }
        }

        v.findViewById<View>(R.id.ov_stop).setOnClickListener {
            GesturePlayer.stop(); setStatus("stopped")
        }

        v.findViewById<View>(R.id.ov_save).setOnClickListener {
            val g = lastGesture
            if (g == null) { setStatus("nothing to save"); return@setOnClickListener }
            val f = GestureStorage.save(this, g)
            setStatus("saved ${f.name}")
        }

        v.findViewById<View>(R.id.ov_anchor).setOnClickListener {
            AnchorPicker.pick(this) { p -> setStatus("anchor set ${p.x.toInt()},${p.y.toInt()}") }
        }

        v.findViewById<View>(R.id.ov_anchor_clear).setOnClickListener {
            AnchorStore.anchor = null; setStatus("anchor cleared (absolute replay)")
        }

        v.findViewById<View>(R.id.ov_close).setOnClickListener { collapseToBubble() }

        try { wm.addView(v, lp) } catch (t: Throwable) {
            Toast.makeText(this, "Overlay failed: ${t.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
        panel = v
        setStatus("ready")
    }

    // ---------------- Transparent recording layer ----------------

    private fun addRecLayer() {
        removeRecLayer()
        val dot = GradientDrawable().apply {
            shape = GradientDrawable.OVAL; setColor(0x66FF3B30); setSize(8, 8)
        }
        val tv = TextView(this).apply {
            text = "● REC"; setTextColor(0xFFFF3B30.toInt()); textSize = 13f
            setPadding(24, 12, 0, 0); background = null
        }
        val layer = object : FrameLayout(this) {
            private var dT0 = 0L
            private val path = android.graphics.Path()

            override fun onTouchEvent(e: MotionEvent): Boolean {
                if (injecting) return true   // swallow leftovers while mirror is replaying
                GestureRecorder.onEvent(e)
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { dT0 = e.eventTime; path.reset(); path.moveTo(e.x, e.y) }
                    MotionEvent.ACTION_MOVE -> path.lineTo(e.x, e.y)
                    MotionEvent.ACTION_UP -> {
                        path.lineTo(e.x, e.y)
                        mirrorToGame(path, (e.eventTime - dT0).coerceIn(1L, 59000L))
                    }
                }
                return true
            }
        }
        layer.addView(tv)
        val lp = WindowManager.LayoutParams(-1, -1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT)
        recLp = lp
        try { wm.addView(layer, lp); recLayer = layer } catch (_: Throwable) {}
        if (AutoClickAccessibilityService.instance == null) {
            setStatus("REC layer on but accessibility OFF — touches will be blocked!")
        }
        dot // (kept for future visual indicator)
    }

    /**
     * Replay the just-recorded touch into the app below. KEY: while the mirror gesture
     * runs, the recording layer flips to NOT_TOUCHABLE — otherwise the injected gesture
     * lands on OUR OWN overlay (topmost window) and the game never receives it.
     * That was the "can't touch anything" bug.
     */
    private fun mirrorToGame(path: android.graphics.Path, dur: Long) {
        val svc = AutoClickAccessibilityService.instance ?: return
        val layer = recLayer ?: return
        val lp = recLp ?: return
        injecting = true
        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        try { wm.updateViewLayout(layer, lp) } catch (_: Throwable) {}
        val gd = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0L, dur))
            .build()
        val done = object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
            private fun restore() {
                injecting = false
                val l = recLayer ?: return
                val p = recLp ?: return
                p.flags = p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                try { wm.updateViewLayout(l, p) } catch (_: Throwable) {}
            }
            override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) { restore() }
            override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) { restore() }
        }
        if (!svc.dispatchOnMain(gd, done)) {
            injecting = false
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            try { wm.updateViewLayout(layer, lp) } catch (_: Throwable) {}
            setStatus("inject failed — ROM may block a11y gestures")
        }
    }

    private fun removeRecLayer() {
        try { recLayer?.let { wm.removeView(it) } } catch (_: Throwable) {}
        recLayer = null
    }

    private fun setStatus(s: String) { status?.post { status?.text = s } }

    // ---------------- Notification ----------------

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel("test001_overlay_channel") == null) {
                nm.createNotificationChannel(NotificationChannel(
                    "test001_overlay_channel", "Test001 Overlay", NotificationManager.IMPORTANCE_LOW))
            }
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(this, 0,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "test001_overlay_channel")
            .setContentTitle("Test001 Auto-Clicker Running")
            .setContentText("Floating panel active")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .addAction(Notification.Action.Builder(null, "Stop service", stopIntent).build())
            .build()
    }
}
