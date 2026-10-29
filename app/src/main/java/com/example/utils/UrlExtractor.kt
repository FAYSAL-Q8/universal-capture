/**
 * Universal Capture - URL & Arabic Text Extraction Utility
 *
 * Author: Faysal Alibrahim
 * GitHub: https://github.com/FAYSAL-Q8
 */

package com.example.utils

import java.util.regex.Pattern

object UrlExtractor {

    // Regex pattern matching standard http/https and www links
    private val URL_PATTERN = Pattern.compile(
        """(?i)\b(?:https?://|www\.)[a-z0-9\-._~:/?#\[\]@!$&'()*+,;=%]+""",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Extracts all URLs from raw text, repairing common OCR spaces or line breaks,
     * stripping trailing punctuation, and validating formatting.
     */
    fun extractUrls(rawText: String): List<String> {
        if (rawText.isBlank()) return emptyList()

        // Step 1: Pre-clean potential broken URLs created by OCR (e.g. "https:// example . com")
        val preCleaned = sanitizeOcrUrls(rawText)

        val matcher = URL_PATTERN.matcher(preCleaned)
        val urls = mutableListOf<String>()

        while (matcher.find()) {
            var url = matcher.group().trim()
            // Strip trailing punctuation that might attach to the URL in sentences
            while (url.isNotEmpty() && (url.endsWith(".") || url.endsWith(",") || url.endsWith(";") || url.endsWith(")") || url.endsWith("]"))) {
                url = url.substring(0, url.length - 1)
            }
            if (url.startsWith("www.", ignoreCase = true)) {
                url = "https://$url"
            }
            if (url.length > 7 && !urls.contains(url)) {
                urls.add(url)
            }
        }

        return urls
    }

    /**
     * Repairs common OCR artifacts where whitespace was inserted around dots, slashes, or schemes.
     */
    fun sanitizeOcrUrls(text: String): String {
        return text
            .replace(Regex("""https?\s*:\s*//\s*""", RegexOption.IGNORE_CASE), "https://")
            .replace(Regex("""www\s*\.\s*""", RegexOption.IGNORE_CASE), "www.")
            .replace(Regex("""([a-zA-Z0-9])\s*\.\s*([a-zA-Z0-9])"""), "$1.$2")
            .replace(Regex("""([a-zA-Z0-9])\s*/\s*([a-zA-Z0-9])"""), "$1/$2")
    }

    /**
     * Checks if a string contains Arabic characters.
     */
    fun containsArabic(text: String): Boolean {
        return text.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' }
    }
}
