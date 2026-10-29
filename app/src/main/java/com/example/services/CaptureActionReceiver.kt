/**
 * Universal Capture - Capture Action Broadcast Receiver
 *
 * Author: Faysal Alibrahim
 * GitHub: https://github.com/FAYSAL-Q8
 */

package com.example.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.example.MainActivity

class CaptureActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TRIGGER_CAPTURE -> {
                val service = CaptureAccessibilityService.instance
                if (service != null) {
                    service.triggerCapture()
                } else {
                    Toast.makeText(
                        context,
                        "Please enable Universal Capture in Accessibility Settings first",
                        Toast.LENGTH_LONG
                    ).show()
                    val launchIntent = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    context.startActivity(launchIntent)
                }
            }
            ACTION_STOP_NOTIFICATION -> {
                CaptureAccessibilityService.instance?.dismissNotification()
            }
        }
    }

    companion object {
        const val ACTION_TRIGGER_CAPTURE = "com.example.ACTION_TRIGGER_CAPTURE"
        const val ACTION_STOP_NOTIFICATION = "com.example.ACTION_STOP_NOTIFICATION"
    }
}
