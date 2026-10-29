/**
 * Universal Capture - Quick Settings Tile Service
 *
 * Author: Faysal Alibrahim
 * GitHub: https://github.com/FAYSAL-Q8
 */

package com.example.services

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import com.example.MainActivity

import android.graphics.drawable.Icon
import com.example.R

class QuickCaptureTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isServiceActive = CaptureAccessibilityService.instance != null
        tile.state = if (isServiceActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Universal Capture"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_quick_capture)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isServiceActive) "Tap to extract screen" else "Service disabled"
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        Log.d(TAG, "Quick Settings Tile clicked")

        val accessibilityService = CaptureAccessibilityService.instance
        if (accessibilityService != null) {
            // 1. Collapse the Quick Settings shade
            collapseAndScan(accessibilityService)
        } else {
            // Accessibility service is not running; open MainActivity to guide user
            Toast.makeText(this, "Please enable Universal Capture in Accessibility Settings", Toast.LENGTH_LONG).show()
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ requires PendingIntent for starting activity from TileService
                val pendingIntent = android.app.PendingIntent.getActivity(
                    this,
                    0,
                    mainIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(mainIntent)
            }
        }
    }

    private fun collapseAndScan(service: CaptureAccessibilityService) {
        // Trigger capture with 450ms delay to allow QS panel to fully collapse
        service.triggerCapture(delayBeforeScanMs = 450L)
    }

    companion object {
        private const val TAG = "QuickCaptureTile"
    }
}
