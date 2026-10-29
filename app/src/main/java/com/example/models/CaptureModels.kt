/**
 * Universal Capture - Data Models
 *
 * Author: Faysal Alibrahim
 * GitHub: https://github.com/FAYSAL-Q8
 */

package com.example.models

import android.graphics.Rect
import java.util.UUID

data class ExtractedBlock(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val bounds: Rect? = null,
    val isUrl: Boolean = false,
    val hasArabic: Boolean = false,
    val isQrCode: Boolean = false,
    val qrType: String? = null // e.g. "URL", "WIFI", "TEXT"
)

data class CaptureResult(
    val fullText: String,
    val blocks: List<ExtractedBlock>,
    val urls: List<String>,
    val qrCodes: List<String> = emptyList(),
    val source: String, // "ACCESSIBILITY_NODES" | "LIVE_SCREEN_OCR" | "ONLINE_OCR"
    val timestamp: Long = System.currentTimeMillis(),
    val bitmapWidth: Int = 0,
    val bitmapHeight: Int = 0
) {
    val arabicCharCount: Int get() = fullText.count { it in '\u0600'..'\u06FF' }
    val englishCharCount: Int get() = fullText.count { it in 'A'..'Z' || it in 'a'..'z' }
    val digitCount: Int get() = fullText.count { it.isDigit() || it in '\u0660'..'\u0669' }
}

enum class OcrMode {
    OFFLINE_ONLY,
    ONLINE_CLOUD
}

enum class CaptureEngineMode {
    TEXT_HIERARCHY, // Normal screen inspection
    LIVE_OCR_AND_QR // Live screen screenshot with OCR & QR code scanning
}
