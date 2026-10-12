package com.test001.autoclicker

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle

/**
 * Translucent trampoline that captures a MediaProjection grant and stashes the
 * result code + Intent into [ProjectionGrant] so [OverlayService] / [TextWatcher]
 * can pick it up. The user sees only the system's "Start now / Cancel" dialog.
 */
class MediaProjectionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ && resultCode == RESULT_OK && data != null) {
            ProjectionGrant.put(resultCode, data)
        }
        finish()
    }

    companion object { private const val REQ = 8411 }
}

/** Static holder so the trampoline can hand the grant back without IPC. */
object ProjectionGrant {
    @Volatile private var code: Int = 0
    @Volatile private var data: Intent? = null
    @Volatile private var pending: Boolean = false

    fun put(c: Int, d: Intent) { code = c; data = d; pending = true }

    /** Returns true exactly once if a fresh grant was waiting. */
    fun consume(): Pair<Int, Intent>? {
        if (!pending) return null
        pending = false
        val d = data ?: return null
        return code to d
    }
}
