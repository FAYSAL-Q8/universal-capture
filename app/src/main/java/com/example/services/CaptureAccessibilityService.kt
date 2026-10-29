/**
 * Universal Capture - Accessibility Service
 *
 * Author: Faysal Alibrahim
 * GitHub: https://github.com/FAYSAL-Q8
 */

package com.example.services

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.ResultActivity
import com.example.models.CaptureResult
import com.example.models.ExtractedBlock
import com.example.utils.UrlExtractor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

class CaptureAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var captureManager: CaptureManager

    override fun onCreate() {
        super.onCreate()
        captureManager = CaptureManager(this)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isRunning.value = true
        Log.d(TAG, "CaptureAccessibilityService connected")
        if (isStandbyEnabled()) {
            showNotification()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Passive observation
    }

    override fun onInterrupt() {
        Log.w(TAG, "CaptureAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isRunning.value = false
        serviceScope.cancel()
        dismissNotification()
        Log.d(TAG, "CaptureAccessibilityService destroyed")
    }

    /**
     * Triggers screen text extraction after ensuring the notification shade is collapsed.
     */
    fun triggerCapture(delayBeforeScanMs: Long = 450L) {
        // 1. Immediately dismiss/collapse the notification shade so it doesn't scan the notification panel
        collapseNotificationShade()

        // 2. Wait for the shade close animation to finish so the underlying targeted app is active
        serviceScope.launch {
            if (delayBeforeScanMs > 0) {
                delay(delayBeforeScanMs)
            }
            performScan()
        }
    }

    /**
     * Collapses the notification panel so the targeted app becomes the active window.
     */
    fun collapseNotificationShade() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            } else {
                performGlobalAction(GLOBAL_ACTION_BACK)
                @Suppress("DEPRECATION")
                sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not collapse notification shade: ${e.message}")
        }
    }

    private fun performScan() {
        try {
            FloatingTriggerService.instance?.setBubbleVisible(false)
            val blocks = extractScreenNodes()
            val fullText = blocks.joinToString("\n\n") { it.text }
            val urls = UrlExtractor.extractUrls(fullText)
            val result = CaptureResult(
                fullText = if (fullText.isNotBlank()) fullText else "No text detected on the current screen.",
                blocks = blocks,
                urls = urls,
                source = "ACCESSIBILITY_NODES"
            )
            latestResult.value = result

            // Launch the floating clear overlay if permitted
            if (Settings.canDrawOverlays(this)) {
                val overlayIntent = Intent(this, OverlayService::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startService(overlayIntent)
            } else {
                // Direct fallback: Open ResultActivity
                val resultIntent = Intent(this, ResultActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(ResultActivity.EXTRA_TEXT, result.fullText)
                    putExtra(ResultActivity.EXTRA_SOURCE, result.source)
                }
                startActivity(resultIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during screen capture", e)
            Toast.makeText(this, "Capture error: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            FloatingTriggerService.instance?.setBubbleVisible(true)
        }
    }

    /**
     * Live screen OCR and QR code scanner: takes screenshot and runs OCR + QR recognition.
     */
    fun triggerLiveOcrAndQr(onComplete: (() -> Unit)? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            serviceScope.launch {
                // Briefly hide overlay UI & floating bubble so screenshot only captures clean underlying app
                OverlayService.instance?.setOverlayVisible(false)
                FloatingTriggerService.instance?.setBubbleVisible(false)
                delay(80L)

                try {
                    takeScreenshot(
                        Display.DEFAULT_DISPLAY,
                        mainExecutor,
                        object : TakeScreenshotCallback {
                            override fun onSuccess(screenshotResult: ScreenshotResult) {
                                OverlayService.instance?.setOverlayVisible(true)
                                FloatingTriggerService.instance?.setBubbleVisible(true)
                                serviceScope.launch(Dispatchers.Default) {
                                    val hwBuffer = screenshotResult.hardwareBuffer
                                    val colorSpace = screenshotResult.colorSpace
                                    val hwBitmap = Bitmap.wrapHardwareBuffer(hwBuffer, colorSpace)
                                    val softwareBitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                                    hwBuffer.close()

                                    if (softwareBitmap != null) {
                                        val ocrResult = captureManager.analyzeLiveScreenBitmap(softwareBitmap)
                                        withContext(Dispatchers.Main) {
                                            latestResult.value = ocrResult
                                            Toast.makeText(
                                                this@CaptureAccessibilityService,
                                                "Live OCR: ${ocrResult.blocks.size} blocks, ${ocrResult.qrCodes.size} QR codes found",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            onComplete?.invoke()
                                        }
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(this@CaptureAccessibilityService, "Could not process screenshot", Toast.LENGTH_SHORT).show()
                                            onComplete?.invoke()
                                        }
                                    }
                                }
                            }

                            override fun onFailure(errorCode: Int) {
                                OverlayService.instance?.setOverlayVisible(true)
                                FloatingTriggerService.instance?.setBubbleVisible(true)
                                Log.e(TAG, "Screenshot capture failed with code: $errorCode")
                                Toast.makeText(this@CaptureAccessibilityService, "Screenshot failed (Code $errorCode), falling back to normal scan", Toast.LENGTH_SHORT).show()
                                performScan()
                                onComplete?.invoke()
                            }
                        }
                    )
                } catch (e: Exception) {
                    OverlayService.instance?.setOverlayVisible(true)
                    FloatingTriggerService.instance?.setBubbleVisible(true)
                    Log.e(TAG, "Live OCR screenshot exception", e)
                    performScan()
                    onComplete?.invoke()
                }
            }
        } else {
            Toast.makeText(this, "Live OCR screenshot requires Android 11+. Running normal scan.", Toast.LENGTH_SHORT).show()
            performScan()
            onComplete?.invoke()
        }
    }

    /**
     * Traverses the AccessibilityNodeInfo tree and extracts tight, precise text blocks.
     * Prioritizes leaf text views and eliminates giant container bounding boxes.
     */
    fun extractScreenNodes(): List<ExtractedBlock> {
        val candidates = mutableListOf<ExtractedBlock>()

        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null) return

            // Filter out system UI package (notification shade elements)
            val pkgName = node.packageName?.toString() ?: ""
            if (pkgName == "com.android.systemui" && node.isImportantForAccessibility.not()) {
                return
            }

            val text = node.text?.toString()?.trim()
            val desc = node.contentDescription?.toString()?.trim()
            val candidate = if (!text.isNullOrEmpty()) text else desc

            if (!candidate.isNullOrBlank() && candidate.length > 1) {
                val rect = Rect()
                node.getBoundsInScreen(rect)

                if (rect.width() > 6 && rect.height() > 6) {
                    val isUrl = candidate.startsWith("http://", ignoreCase = true) ||
                            candidate.startsWith("https://", ignoreCase = true) ||
                            candidate.startsWith("www.", ignoreCase = true)
                    val hasArabic = UrlExtractor.containsArabic(candidate)

                    // Tighten bounds for single-line text that filled parent (match_parent)
                    val tightenedRect = Rect(rect)
                    if (!candidate.contains('\n') && candidate.length <= 40 && rect.height() in 20..180) {
                        val approxCharWidth = rect.height() * 0.65f
                        val expectedWidth = (candidate.length * approxCharWidth).toInt() + 24
                        if (tightenedRect.width() > expectedWidth * 2.2f) {
                            if (hasArabic) {
                                tightenedRect.left = maxOf(tightenedRect.left, tightenedRect.right - expectedWidth)
                            } else {
                                tightenedRect.right = minOf(tightenedRect.right, tightenedRect.left + expectedWidth)
                            }
                        }
                    }

                    candidates.add(
                        ExtractedBlock(
                            text = candidate,
                            bounds = tightenedRect,
                            isUrl = isUrl,
                            hasArabic = hasArabic
                        )
                    )
                }
            }

            for (i in 0 until node.childCount) {
                traverse(node.getChild(i))
            }
        }

        // Check rootInActiveWindow
        val root = rootInActiveWindow
        if (root != null) {
            traverse(root)
        }

        // Also check secondary windows if available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                for (window in windows) {
                    val windowRoot = window.root
                    if (windowRoot != null && windowRoot != root) {
                        traverse(windowRoot)
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Could not inspect secondary windows: ${e.message}")
            }
        }

        // DEDUPLICATION & CONTAINER FILTERING:
        // When parent container and child text node both contain the same text,
        // keep ONLY the smaller one (the actual text, not the container)!
        val filtered = mutableListOf<ExtractedBlock>()
        for (candidate in candidates) {
            val cRect = candidate.bounds ?: continue
            val cArea = cRect.width().toLong() * cRect.height().toLong()

            // Check if there is another candidate with same text that is smaller and inside this one
            val hasSmallerDuplicate = candidates.any { other ->
                if (other === candidate) return@any false
                val oRect = other.bounds ?: return@any false
                val oArea = oRect.width().toLong() * oRect.height().toLong()
                other.text == candidate.text && oArea < cArea && cRect.contains(oRect)
            }

            if (!hasSmallerDuplicate) {
                // Ensure no exact identical duplicate already added
                val alreadyAdded = filtered.any { existing ->
                    val eRect = existing.bounds ?: return@any false
                    existing.text == candidate.text &&
                            Math.abs(existing.bounds.left - cRect.left) < 15 &&
                            Math.abs(existing.bounds.top - cRect.top) < 15
                }
                if (!alreadyAdded) {
                    filtered.add(candidate)
                }
            }
        }

        return filtered
    }

    fun isStandbyEnabled(): Boolean {
        val prefs = getSharedPreferences("universal_capture_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("notification_standby_enabled", true)
    }

    fun setStandbyEnabled(enabled: Boolean) {
        val prefs = getSharedPreferences("universal_capture_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("notification_standby_enabled", enabled).apply()
        if (enabled) {
            showNotification()
        } else {
            dismissNotification()
        }
    }

    /**
     * Creates and displays the persistent quick-access notification in the notification bar.
     */
    fun showNotification() {
        if (!isStandbyEnabled()) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Universal Capture Mode",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Quick-access notification to trigger screen text capture"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        val triggerIntent = Intent(this, CaptureActionReceiver::class.java).apply {
            action = CaptureActionReceiver.ACTION_TRIGGER_CAPTURE
        }
        val triggerPendingIntent = PendingIntent.getBroadcast(
            this,
            2001,
            triggerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⚡ Universal Capture")
            .setContentText("Tap to copy text or QR codes from screen")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(triggerPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_send,
                "Extract Screen",
                triggerPendingIntent
            )
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    fun dismissNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }

    companion object {
        const val TAG = "CaptureAccessibility"
        const val CHANNEL_ID = "capture_mode_channel"
        const val NOTIFICATION_ID = 1001

        var instance: CaptureAccessibilityService? = null
            private set

        val isRunning = MutableStateFlow(false)
        val latestResult = MutableStateFlow<CaptureResult?>(null)
    }
}
