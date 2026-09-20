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
    private var recLayer: View? = null
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
        panel = null
        super.onDestroy()
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
                setStatus("recording… touch the screen")
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

        v.findViewById<View>(R.id.ov_close).setOnClickListener { stopSelf() }

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
            override fun onTouchEvent(e: MotionEvent): Boolean {
                GestureRecorder.onEvent(e)
                // NOT_TOUCH_MODAL means touches pass through to apps outside this window;
                // this window itself still receives the stream for recording.
                return false
            }
        }
        layer.addView(tv)
        val lp = WindowManager.LayoutParams(-1, -1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT)
        try { wm.addView(layer, lp); recLayer = layer } catch (_: Throwable) {}
        dot // (kept for future visual indicator)
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
