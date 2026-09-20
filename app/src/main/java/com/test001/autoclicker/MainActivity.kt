package com.test001.autoclicker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var overlayStatus: TextView
    private lateinit var a11yStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        overlayStatus = findViewById(R.id.status_overlay)
        a11yStatus = findViewById(R.id.status_a11y)

        findViewById<Button>(R.id.btn_overlay).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
        }
        findViewById<Button>(R.id.btn_a11y).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btn_start_panel).setOnClickListener {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
                overlayStatus.text = "Overlay: ❌ grant it first"; return@setOnClickListener
            }
            val i = Intent(this, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        }
        findViewById<Button>(R.id.btn_saved).setOnClickListener {
            startActivity(Intent(this, SavedGesturesActivity::class.java))
        }
        findViewById<Button>(R.id.btn_open_panel).setOnClickListener {
            // restart the service → panel re-appears even after ✖-stop
            try { stopService(Intent(this, OverlayService::class.java)) } catch (_: Throwable) {}
            val i = Intent(this, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        }

        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val overlayOk = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)
        overlayStatus.text = "Overlay: ${if (overlayOk) "✅" else "❌"}"
        a11yStatus.text = "Accessibility: ${if (AutoClickAccessibilityService.isEnabled(this)) "✅" else "❌"}"
    }

    /** Android 13+ requires runtime POST_NOTIFICATIONS for the foreground-service notice. */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }
}
