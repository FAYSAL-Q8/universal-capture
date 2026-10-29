/**
 * Universal Capture - Floating Trigger Bubble Service
 *
 * Author: Faysal Alibrahim
 * GitHub: https://github.com/FAYSAL-Q8
 */

package com.example.services

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.R

class FloatingTriggerService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: FrameLayout? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        instance = this
        createFloatingBubble()
        isRunning = true
    }

    fun setBubbleVisible(visible: Boolean) {
        floatingView?.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingBubble() {
        if (floatingView != null) return

        val container = FrameLayout(this)

        val iconView = ImageView(this).apply {
            setImageResource(R.drawable.ic_quick_capture)
            setColorFilter(0xFFFFFFFF.toInt())
            setPadding(26, 26, 26, 26)
            elevation = 16f
        }

        // Circular background
        val shapeDrawable = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(0xFF4338CA.toInt())
            setStroke(4, 0xFF38BDF8.toInt())
        }
        iconView.background = shapeDrawable

        val iconParams = FrameLayout.LayoutParams(130, 130)
        container.addView(iconView, iconParams)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = 20
            y = 350
        }

        // Handle dragging and tap
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = false

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val diffX = (event.rawX - initialTouchX).toInt()
                    val diffY = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(diffX) > 10 || Math.abs(diffY) > 10) {
                        isClick = false
                    }
                    params.x = initialX + diffX
                    params.y = initialY + diffY
                    try {
                        windowManager.updateViewLayout(container, params)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        // User tapped floating bubble! Trigger capture without pulling notification shade!
                        val service = CaptureAccessibilityService.instance
                        if (service != null) {
                            service.triggerCapture(delayBeforeScanMs = 100L)
                        } else {
                            Toast.makeText(this, "Please enable Accessibility Service first", Toast.LENGTH_SHORT).show()
                        }
                    }
                    true
                }
                else -> false
            }
        }

        floatingView = container
        try {
            windowManager.addView(container, params)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    override fun onDestroy() {
        isRunning = false
        floatingView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        floatingView = null
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        var isRunning: Boolean = false
        var instance: FloatingTriggerService? = null
            private set
    }
}
