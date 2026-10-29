/**
 * Universal Capture - Capture Manager
 *
 * Author: Faysal Alibrahim
 * GitHub: https://github.com/FAYSAL-Q8
 */

package com.example.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.models.CaptureResult
import com.example.models.ExtractedBlock
import com.example.models.OcrMode
import com.example.utils.UrlExtractor
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class CaptureManager(private val context: Context) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val barcodeScanner = BarcodeScanning.getClient()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Scans for QR codes and barcodes within a Bitmap.
     */
    suspend fun scanBarcodesAndQr(bitmap: Bitmap): List<ExtractedBlock> = withContext(Dispatchers.Default) {
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val barcodes = barcodeScanner.process(image).await()
            barcodes.mapNotNull { barcode ->
                val rawValue = barcode.rawValue ?: barcode.displayValue
                if (!rawValue.isNullOrBlank()) {
                    val rawBounds = barcode.boundingBox ?: Rect()
                    val bounds = Rect(
                        minOf(rawBounds.left, rawBounds.right),
                        minOf(rawBounds.top, rawBounds.bottom),
                        maxOf(rawBounds.left, rawBounds.right),
                        maxOf(rawBounds.top, rawBounds.bottom)
                    )
                    val isUrl = barcode.valueType == Barcode.TYPE_URL ||
                            rawValue.startsWith("http://", ignoreCase = true) ||
                            rawValue.startsWith("https://", ignoreCase = true) ||
                            rawValue.startsWith("www.", ignoreCase = true)

                    val qrType = when (barcode.valueType) {
                        Barcode.TYPE_URL -> "URL"
                        Barcode.TYPE_WIFI -> "Wi-Fi"
                        Barcode.TYPE_CONTACT_INFO -> "Contact"
                        Barcode.TYPE_EMAIL -> "Email"
                        Barcode.TYPE_SMS -> "SMS"
                        else -> "QR / Barcode"
                    }

                    ExtractedBlock(
                        text = rawValue,
                        bounds = bounds,
                        isUrl = isUrl,
                        isQrCode = true,
                        qrType = qrType
                    )
                } else null
            }
        } catch (e: Exception) {
            Log.e("CaptureManager", "QR/Barcode scanning error", e)
            emptyList()
        }
    }

    /**
     * Performs live screen image analysis: runs QR code scanning + OCR.
     */
    suspend fun analyzeLiveScreenBitmap(
        bitmap: Bitmap,
        mode: OcrMode = OcrMode.OFFLINE_ONLY
    ): CaptureResult = withContext(Dispatchers.Default) {
        // 1. Scan QR codes & barcodes
        val qrBlocks = scanBarcodesAndQr(bitmap)
        val qrStrings = qrBlocks.map { it.text }

        // 2. Scan OCR text
        val ocrResult = recognizeText(bitmap, mode)

        // 3. Merge: QR blocks prioritized first, followed by text blocks
        val combinedBlocks = qrBlocks + ocrResult.blocks
        val allUrls = (qrBlocks.filter { it.isUrl }.map { it.text } + ocrResult.urls).distinct()

        val combinedFullText = buildString {
            if (qrStrings.isNotEmpty()) {
                append("--- Detected QR / Barcodes ---\n")
                qrStrings.forEach { append("$it\n") }
                append("\n--- Extracted Screen Text ---\n")
            }
            append(ocrResult.fullText)
        }

        CaptureResult(
            fullText = combinedFullText,
            blocks = combinedBlocks,
            urls = allUrls,
            qrCodes = qrStrings,
            source = "LIVE_SCREEN_OCR",
            bitmapWidth = bitmap.width,
            bitmapHeight = bitmap.height
        )
    }

    /**
     * Performs OCR on a Bitmap using either on-device ML Kit (offline) or Gemini Vision (online).
     */
    suspend fun recognizeText(
        bitmap: Bitmap,
        mode: OcrMode = OcrMode.OFFLINE_ONLY
    ): CaptureResult = withContext(Dispatchers.IO) {
        if (mode == OcrMode.ONLINE_CLOUD) {
            val onlineResult = tryOnlineGeminiOcr(bitmap)
            if (onlineResult != null) {
                return@withContext onlineResult
            }
        }

        // Fallback or default: On-device offline ML Kit
        recognizeOfflineMlKit(bitmap)
    }

    /**
     * On-device ML Kit Text Recognition (100% offline, private, instantaneous).
     * Extracts tight, line-level bounding boxes so blocks accurately reflect text size & position.
     */
    suspend fun recognizeOfflineMlKit(bitmap: Bitmap): CaptureResult = withContext(Dispatchers.Default) {
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val visionText = recognizer.process(image).await()
            val blocks = mutableListOf<ExtractedBlock>()

            for (block in visionText.textBlocks) {
                if (block.lines.isNotEmpty()) {
                    for (line in block.lines) {
                        val lineText = line.text.trim()
                        if (lineText.isNotEmpty()) {
                            val rawBounds = line.boundingBox ?: block.boundingBox ?: Rect()
                            val lineBounds = Rect(
                                minOf(rawBounds.left, rawBounds.right),
                                minOf(rawBounds.top, rawBounds.bottom),
                                maxOf(rawBounds.left, rawBounds.right),
                                maxOf(rawBounds.top, rawBounds.bottom)
                            )
                            val hasArabic = UrlExtractor.containsArabic(lineText)
                            val isUrl = lineText.startsWith("http://", ignoreCase = true) ||
                                    lineText.startsWith("https://", ignoreCase = true) ||
                                    lineText.startsWith("www.", ignoreCase = true)

                            blocks.add(
                                ExtractedBlock(
                                    text = lineText,
                                    bounds = lineBounds,
                                    isUrl = isUrl,
                                    hasArabic = hasArabic
                                )
                            )
                        }
                    }
                } else {
                    val text = block.text.trim()
                    if (text.isNotEmpty()) {
                        val rawBounds = block.boundingBox ?: Rect()
                        val bounds = Rect(
                            minOf(rawBounds.left, rawBounds.right),
                            minOf(rawBounds.top, rawBounds.bottom),
                            maxOf(rawBounds.left, rawBounds.right),
                            maxOf(rawBounds.top, rawBounds.bottom)
                        )
                        val hasArabic = UrlExtractor.containsArabic(text)
                        val isUrl = text.startsWith("http://", ignoreCase = true) ||
                                text.startsWith("https://", ignoreCase = true) ||
                                text.startsWith("www.", ignoreCase = true)

                        blocks.add(
                            ExtractedBlock(
                                text = text,
                                bounds = bounds,
                                isUrl = isUrl,
                                hasArabic = hasArabic
                            )
                        )
                    }
                }
            }

            val fullText = visionText.text.ifBlank { "No text detected in this image." }
            val urls = UrlExtractor.extractUrls(fullText)

            CaptureResult(
                fullText = fullText,
                blocks = blocks,
                urls = urls,
                source = "OFFLINE_OCR",
                bitmapWidth = bitmap.width,
                bitmapHeight = bitmap.height
            )
        } catch (e: Exception) {
            Log.e("CaptureManager", "ML Kit error", e)
            val isModuleDownloading = e.message?.contains("Waiting for the text optional module", ignoreCase = true) == true

            // If offline model is still downloading, try online OCR as an immediate fallback
            val onlineFallback = tryOnlineGeminiOcr(bitmap)
            if (onlineFallback != null) {
                return@withContext onlineFallback
            }

            val fallbackMsg = if (isModuleDownloading) {
                "Text recognition model is preparing in the background. Please try again in a few moments, or use Online Cloud mode."
            } else {
                "OCR scan failed: ${e.localizedMessage ?: "Unknown error"}"
            }

            CaptureResult(
                fullText = fallbackMsg,
                blocks = listOf(ExtractedBlock(text = fallbackMsg)),
                urls = emptyList(),
                source = "OFFLINE_OCR_ERROR"
            )
        }
    }

    /**
     * Online Cloud OCR using Gemini Vision for maximum precision with Arabic, English, and untrimmed URLs.
     */
    private suspend fun tryOnlineGeminiOcr(bitmap: Bitmap): CaptureResult? = withContext(Dispatchers.IO) {
        val apiKey = try {
            val field = BuildConfig::class.java.getField("GEMINI_API_KEY")
            (field.get(null) as? String) ?: ""
        } catch (e: Throwable) {
            ""
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY" || apiKey == "YOUR_GEMINI_API_KEY_HERE") {
            Log.d("CaptureManager", "Online OCR skipped: GEMINI_API_KEY not configured, falling back to offline ML Kit")
            return@withContext null
        }

        try {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            val imageBytes = outputStream.toByteArray()
            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

            val prompt = """
                Extract all text from this image exactly as written.
                The image may contain Arabic, English, numbers, and URLs.
                Rules:
                1. Preserve Arabic script and English text exactly as displayed.
                2. If there are URLs or web links, ensure they are cleanly formatted without unwanted internal spaces or line breaks.
                3. Return only the extracted text.
            """.trimIndent()

            val requestBodyJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                            put(JSONObject().apply {
                                val inlineData = JSONObject().apply {
                                    put("mime_type", "image/jpeg")
                                    put("data", base64Image)
                                }
                                put("inline_data", inlineData)
                            })
                        }
                        put("parts", parts)
                    }
                    put(contentObj)
                }
                put("contents", contents)
            }

            val request = Request.Builder()
                .url(url)
                .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w("CaptureManager", "Gemini OCR API error: ${response.code}")
                return@withContext null
            }

            val responseBody = response.body?.string() ?: return@withContext null
            val root = JSONObject(responseBody)
            val candidates = root.optJSONArray("candidates") ?: return@withContext null
            if (candidates.length() == 0) return@withContext null

            val candidate = candidates.getJSONObject(0)
            val content = candidate.optJSONObject("content") ?: return@withContext null
            val parts = content.optJSONArray("parts") ?: return@withContext null
            if (parts.length() == 0) return@withContext null

            val rawText = parts.getJSONObject(0).optString("text", "").trim()
            if (rawText.isBlank()) return@withContext null

            val blocks = rawText.lines()
                .filter { it.isNotBlank() }
                .map { line ->
                    ExtractedBlock(
                        text = line.trim(),
                        hasArabic = UrlExtractor.containsArabic(line),
                        isUrl = line.startsWith("http://", ignoreCase = true) ||
                                line.startsWith("https://", ignoreCase = true) ||
                                line.startsWith("www.", ignoreCase = true)
                    )
                }

            val urls = UrlExtractor.extractUrls(rawText)

            CaptureResult(
                fullText = rawText,
                blocks = blocks,
                urls = urls,
                source = "ONLINE_OCR"
            )
        } catch (e: Exception) {
            Log.e("CaptureManager", "Online OCR exception", e)
            null
        }
    }
}
