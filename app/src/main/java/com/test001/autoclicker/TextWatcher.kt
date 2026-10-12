package com.test001.autoclicker

import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.util.concurrent.atomic.AtomicBoolean

/**
 * "Detect text on screen → auto-click" core. Holds one MediaProjection, an
 * ImageReader + VirtualDisplay, polls the latest frame at fixed cadence, sends
 * it to [OcrEngine] and on keyword hit dispatches a tap via
 * [AutoClickAccessibilityService] at the text bounding box centre.
 *
 * Started/stopped by the floating panel (📷 = grant, 🔍 = run). Stays alive
 * only while the panel wants it. Always safe to call stop() repeatedly.
 */
object TextWatcher {

    private const val TAG = "T1WATCH"
    private const val POLL_MS = 1200L
    private const val COOLDOWN_MS = 1800L

    @Volatile var running: Boolean = false
        private set
    @Volatile var keyword: String = "OK"

    private var projection: MediaProjection? = null
    private var vdisp: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var hthread: HandlerThread? = null
    private var h: Handler? = null
    private var ctx: Context? = null
    private val main = Handler(Looper.getMainLooper())
    private var lastFireMs = 0L

    /**
     * Asks the user to grant MediaProjection by launching the small trampoline
     * activity. The actual capture will begin once the user accepts and
     * [ProjectionGrant.consume] returns a non-null pair (handled by tick()).
     */
    fun ask(ctx: Context) {
        val i = Intent(ctx, MediaProjectionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    fun start(ctx: Context) {
        if (running) return
        this.ctx = ctx.applicationContext
        running = true
        hthread = HandlerThread("t1watch").also { it.start() }
        h = Handler(hthread!!.looper)
        Log.i(TAG, "watcher started, keyword='$keyword'")
    }

    fun stop() {
        if (!running) return
        running = false
        h?.removeCallbacksAndMessages(null)
        hthread?.quitSafely()
        hthread = null; h = null
        try { vdisp?.release() } catch (_: Throwable) {}
        try { projection?.stop() } catch (_: Throwable) {}
        try { reader?.close() } catch (_: Throwable) {}
        vdisp = null; projection = null; reader = null
        ctx = null
        Log.i(TAG, "watcher stopped")
    }

    /** Called by the floating panel button — sets the keyword live. */
    fun setKeyword(s: String) {
        keyword = s.trim()
        Log.i(TAG, "keyword set: '$keyword'")
    }

    /** Triggered by the panel once the user has pressed 🔍 START; starts the camera loop. */
    fun ensureProjection(ctx: Context) {
        if (projection != null) { startCameraLoop(); return }
        ask(ctx)
        // poll for the grant, then start
        h?.postDelayed(object : Runnable {
            override fun run() {
                val grant = ProjectionGrant.consume() ?: return h?.postDelayed(this, 250) ?: Unit
                try {
                    val mpm = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    projection = mpm.getMediaProjection(grant.first, grant.second)
                    startCameraLoop()
                } catch (t: Throwable) {
                    Log.w(TAG, "projection init fail: ${t.message}")
                }
            }
        }, 300)
    }

    private fun startCameraLoop() {
        val c = ctx ?: return
        val r = reader
        if (r != null) { scheduleNextPoll(0); return }
        val wm = c.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION") wm.defaultDisplay.getMetrics(dm)
        val reader2 = ImageReader.newInstance(dm.widthPixels, dm.heightPixels, PixelFormat.RGBA_8888, 2)
        reader = reader2
        try {
            vdisp = projection?.createVirtualDisplay(
                "t1watch", dm.widthPixels, dm.heightPixels, dm.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader2.surface, null, null
            )
            Log.i(TAG, "vdisp created ${dm.widthPixels}x${dm.heightPixels}")
        } catch (t: Throwable) {
            Log.w(TAG, "vdisp fail: ${t.message}")
            return
        }
        scheduleNextPoll(0)
    }

    private fun scheduleNextPoll(delayMs: Long) {
        val handler = h ?: return
        handler.postDelayed({ if (running) tick() else Unit }, delayMs)
    }

    private fun tick() {
        try {
            val r = reader ?: return
            val img = try { r.acquireLatestImage() } catch (_: Throwable) { null }
            if (img == null) { scheduleNextPoll(POLL_MS); return }
            val w = img.width; val h = img.height
            val buf = img.planes[0].buffer
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(buf)
            img.close()
            val kw = keyword
            OcrEngine.recognize(bmp, kw) { hits ->
                scheduleNextPoll(POLL_MS)
                if (hits.isEmpty()) return@recognize
                val now = System.currentTimeMillis()
                if (now - lastFireMs < COOLDOWN_MS) return@recognize
                val first = hits.first()
                val cx = first.box.exactCenterX()
                val cy = first.box.exactCenterY()
                fireTap(cx, cy, first.text)
                lastFireMs = now
            }
        } catch (t: Throwable) {
            Log.w(TAG, "tick fail: ${t.message}")
            scheduleNextPoll(POLL_MS)
        }
    }

    private fun fireTap(x: Float, y: Float, matched: String) {
        val svc = AutoClickAccessibilityService.instance ?: run {
            Log.w(TAG, "fireTap: a11y OFF"); return
        }
        val p = Path().apply { moveTo(x, y); lineTo(x, y) }
        val gd = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0L, 80L)).build()
        val cb = object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {}
        val ok = try { svc.dispatchGesture(gd, cb, null) } catch (_: Throwable) { false }
        Log.i(TAG, "tap '${matched}' at ${x.toInt()},${y.toInt()} → $ok")
    }
}
