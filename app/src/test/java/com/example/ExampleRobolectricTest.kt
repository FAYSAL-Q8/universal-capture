/**
 * Universal Capture - Unit & Robolectric Tests
 *
 * Author: Faysal Alibrahim
 * GitHub: https://github.com/FAYSAL-Q8
 */

package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Universal Capture", appName)
  }

  @Test
  fun `test url extraction and arabic detection`() {
    val sample = "Check out https://github.com/universal-capture and مرحبا"
    val urls = com.example.utils.UrlExtractor.extractUrls(sample)
    assertEquals(1, urls.size)
    assertEquals("https://github.com/universal-capture", urls[0])
    val hasArabic = com.example.utils.UrlExtractor.containsArabic(sample)
    assertEquals(true, hasArabic)
  }
}
