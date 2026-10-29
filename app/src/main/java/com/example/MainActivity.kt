/**
 * Universal Capture - Main Activity
 *
 * Author: Faysal Alibrahim
 * GitHub: https://github.com/FAYSAL-Q8
 */

package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.example.ui.components.AppFooter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import com.example.models.ExtractedBlock
import com.example.models.OcrMode
import com.example.services.CaptureAccessibilityService
import com.example.services.CaptureManager
import com.example.services.FloatingTriggerService
import com.example.services.QuickCaptureTileService
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainAppScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val captureManager = remember { CaptureManager(context) }

    // Service & Permission States
    val isAccessibilityActive by CaptureAccessibilityService.isRunning.collectAsState()
    var isOverlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isNotificationGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            } else true
        )
    }

    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    var isBatteryOptimizationIgnored by remember {
        mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false)
    }

    var selectedOcrMode by remember { mutableStateOf(OcrMode.OFFLINE_ONLY) }
    var isProcessingOcr by remember { mutableStateOf(false) }
    var showHowToUseDialog by remember { mutableStateOf(false) }

    // Refresh permissions whenever app comes to foreground
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        isOverlayGranted = Settings.canDrawOverlays(context)
        isNotificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        } else true
        isBatteryOptimizationIgnored = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    // Permission launchers
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isNotificationGranted = granted
        if (granted) {
            CaptureAccessibilityService.instance?.showNotification()
        }
    }

    // Photo picker for OCR on images
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isProcessingOcr = true
                try {
                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                            decoder.isMutableRequired = true
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    }

                    val ocrResult = captureManager.recognizeText(bitmap, selectedOcrMode)
                    val intent = Intent(context, ResultActivity::class.java).apply {
                        putExtra(ResultActivity.EXTRA_TEXT, ocrResult.fullText)
                        putExtra(ResultActivity.EXTRA_SOURCE, ocrResult.source)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Error processing image: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isProcessingOcr = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Universal Capture",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "النسخ الشامل",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showHowToUseDialog = true },
                        modifier = Modifier.testTag("btn_how_to_use")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "How to use"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Status Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hero_status_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isAccessibilityActive)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    else
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isAccessibilityActive) Color(0xFF22C55E) else Color(0xFFEF4444)
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isAccessibilityActive) "Service Ready & Running" else "Service Not Activated",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isAccessibilityActive)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.error
                            )
                        }

                        if (!isAccessibilityActive) {
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("btn_enable_accessibility")
                            ) {
                                Text("Activate", fontSize = 12.sp)
                            }
                        }
                    }

                    Text(
                        text = if (isAccessibilityActive)
                            "Universal Capture is monitoring screen text. Pull down your notification bar anytime to extract text from any app!"
                        else
                            "To copy text from non-selectable apps, enable 'Universal Capture' in your phone's Accessibility Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Permissions Checklist Section
            Text(
                text = "Required Permissions",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Permission 1: Accessibility
            PermissionItemCard(
                title = "1. Accessibility Service",
                desc = "Reads text hierarchy on screen (Arabic & English)",
                isGranted = isAccessibilityActive,
                actionLabel = "Enable in Settings",
                onAction = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                },
                testTag = "perm_accessibility_card"
            )

            // Permission 2: Overlay
            PermissionItemCard(
                title = "2. Display Over Other Apps",
                desc = "Draws the interactive selection overlay",
                isGranted = isOverlayGranted,
                actionLabel = "Grant Overlay",
                onAction = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                },
                testTag = "perm_overlay_card"
            )

            // Permission 3: Notifications
            PermissionItemCard(
                title = "3. Quick Notifications",
                desc = "Quick status bar toggle to trigger capture instantly",
                isGranted = isNotificationGranted,
                actionLabel = "Allow",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        Toast.makeText(context, "Notifications already enabled", Toast.LENGTH_SHORT).show()
                    }
                },
                testTag = "perm_notification_card"
            )

            // Permission 4: Battery Optimization Exemption
            PermissionItemCard(
                title = "4. Keep Service Active (Unrestricted Battery)",
                desc = "Prevents Android and battery savers from turning off the service over time",
                isGranted = isBatteryOptimizationIgnored,
                actionLabel = "Unrestrict",
                onAction = {
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        try {
                            val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(fallbackIntent)
                        } catch (ex: Exception) {
                            Toast.makeText(context, "Please set Battery to Unrestricted in App Info", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                testTag = "perm_battery_card"
            )

            // Why Services Turn Off Guidance Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth().testTag("card_keep_alive_tips")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Why Accessibility Services Disable Over Time",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Battery Savers: Phone manufacturers (Samsung, Xiaomi, Huawei, Oppo) kill background services to save battery.\n• Solution 1: Tap 'Unrestrict' above to exempt from battery sleep.\n• Solution 2: Lock the app in your phone's Recent Apps screen (padlock icon) or enable Autostart.\n• Note: Using the Notification Toolbar tile will work reliably even if the app was recently idle.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        lineHeight = 17.sp
                    )
                }
            }

            // Notification Toolbar Shortcut Card (Stable alternative to notification standby)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_quick_settings_tile"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Notification Toolbar Tile",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val statusBarManager = context.getSystemService(StatusBarManager::class.java)
                                    val componentName = ComponentName(context, QuickCaptureTileService::class.java)
                                    val tileIcon = Icon.createWithResource(context, R.drawable.ic_quick_capture)
                                    statusBarManager?.requestAddTileService(
                                        componentName,
                                        "Universal Capture",
                                        tileIcon,
                                        context.mainExecutor
                                    ) { resultCode ->
                                        if (resultCode == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED) {
                                            Toast.makeText(context, "Shortcut added to Notification Toolbar!", Toast.LENGTH_SHORT).show()
                                        } else if (resultCode == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                                            Toast.makeText(context, "Shortcut already in Notification Toolbar", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Swipe down twice from top of screen, tap the pencil icon, and drag 'Universal Capture' into your tools bar tab!",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("btn_add_toolbar_tile")
                        ) {
                            Text("Add to Toolbar", fontSize = 11.sp)
                        }
                    }

                    Text(
                        text = "A stable alternative to persistent notification standby! Access Universal Capture directly from the quick settings tools bar tab in your notification panel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Notification Standby Toggle (Allow disabling persistent notification when using toolbar shortcut)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_notification_standby"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Notification Standby",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Keep persistent standby notification in notification list. You can turn this off and use the Toolbar shortcut instead for a cleaner panel.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    var isStandbyOn by remember {
                        val prefs = context.getSharedPreferences("universal_capture_prefs", Context.MODE_PRIVATE)
                        mutableStateOf(prefs.getBoolean("notification_standby_enabled", true))
                    }

                    Switch(
                        checked = isStandbyOn,
                        onCheckedChange = { enable ->
                            isStandbyOn = enable
                            CaptureAccessibilityService.instance?.setStandbyEnabled(enable) ?: run {
                                val prefs = context.getSharedPreferences("universal_capture_prefs", Context.MODE_PRIVATE)
                                prefs.edit().putBoolean("notification_standby_enabled", enable).apply()
                            }
                            Toast.makeText(
                                context,
                                if (enable) "Notification Standby enabled" else "Notification Standby disabled. Use Toolbar shortcut to scan!",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.testTag("switch_notification_standby")
                    )
                }
            }

            // Floating Bubble Toggle (Avoid opening notification shade!)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_floating_bubble"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TouchApp, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Floating Trigger Bubble", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "Tap a floating on-screen bubble to scan instantly without touching the notification shade!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    var isBubbleEnabled by remember { mutableStateOf(FloatingTriggerService.isRunning) }
                    Switch(
                        checked = isBubbleEnabled,
                        onCheckedChange = { enable ->
                            if (enable) {
                                if (Settings.canDrawOverlays(context)) {
                                    context.startService(Intent(context, FloatingTriggerService::class.java))
                                    isBubbleEnabled = true
                                    Toast.makeText(context, "Floating bubble enabled on screen!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Please grant overlay permission first", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                context.stopService(Intent(context, FloatingTriggerService::class.java))
                                isBubbleEnabled = false
                            }
                        }
                    )
                }
            }

            // Quick Gesture Tips Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "⚡ Overlay Gestures & Controls",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Double-tap text: Instantly copies that text to clipboard\n• Hold (long press) text: Preview & edit internally without closing overlay\n• Movable Floating Bar: Drag the top handle or bar anywhere on screen (double-tap to reset)\n• 🔄 Rescan: Refreshes and updates screen text\n• 📷 Live OCR & QR: Scans screen image for text & QR codes (aligned 1:1 on text)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }

            // Action Buttons
            Text(
                text = "Capture & OCR Tools",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Button 1: Screen Text Capture
                Button(
                    onClick = {
                        val service = CaptureAccessibilityService.instance
                        if (service != null) {
                            service.triggerCapture()
                        } else {
                            Toast.makeText(
                                context,
                                "Please enable the Accessibility Service first!",
                                Toast.LENGTH_LONG
                            ).show()
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("btn_trigger_screen_capture"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Screenshot, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Capture Screen", fontSize = 13.sp)
                }

                // Button 2: OCR Image Picker
                FilledTonalButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("btn_ocr_image_picker"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.ImageSearch, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("OCR Image", fontSize = 13.sp)
                }
            }

            if (isProcessingOcr) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Extracting text and URLs...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // OCR Engine Configuration Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ocr_mode_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "OCR Engine Mode",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedOcrMode == OcrMode.OFFLINE_ONLY,
                            onClick = { selectedOcrMode = OcrMode.OFFLINE_ONLY },
                            label = { Text("Offline (On-Device)") },
                            leadingIcon = {
                                Icon(Icons.Default.WifiOff, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.weight(1f)
                        )

                        FilterChip(
                            selected = selectedOcrMode == OcrMode.ONLINE_CLOUD,
                            onClick = { selectedOcrMode = OcrMode.ONLINE_CLOUD },
                            label = { Text("Online (Cloud)") },
                            leadingIcon = {
                                Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(
                        text = if (selectedOcrMode == OcrMode.OFFLINE_ONLY)
                            "• On-device ML Kit: 100% offline, zero internet required, instant speed."
                        else
                            "• Cloud OCR: Maximum accuracy for complex Arabic script and untrimmed URLs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Interactive Live Test Sandbox
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("test_sandbox_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🧪 Live Sandbox Test Card",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )

                        TextButton(
                            onClick = {
                                val sampleText = """
                                    النسخ الشامل - Universal Capture
                                    استخراج النصوص والروابط بدقة عالية بدون مسافات زائدة
                                    Official link: https://github.com/universal-capture/android?lang=ar&source=mobile
                                    Support code: #984210
                                """.trimIndent()

                                val intent = Intent(context, ResultActivity::class.java).apply {
                                    putExtra(ResultActivity.EXTRA_TEXT, sampleText)
                                    putExtra(ResultActivity.EXTRA_SOURCE, "SANDBOX_TEST")
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.testTag("btn_test_extract_sandbox")
                        ) {
                            Text("Test Extract")
                        }
                    }

                    Text(
                        text = "النسخ الشامل - Universal Capture\nاستخراج النصوص والروابط بدقة عالية بدون مسافات زائدة\nOfficial link: https://github.com/universal-capture/android?lang=ar&source=mobile\nSupport code: #984210",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            AppFooter()
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // How to Use Dialog
    if (showHowToUseDialog) {
        AlertDialog(
            onDismissRequest = { showHowToUseDialog = false },
            title = {
                Text("How to Activate & Use Universal Capture", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "1. Enable Accessibility & Unrestrict Battery:\nTurn ON 'Universal Capture' in Accessibility Settings and tap 'Unrestrict' on Battery to prevent Android from disabling it over time.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "2. Quick Notification & Toolbar Tile:\nTrigger screen scan anytime via the Notification Standby or the Notification Toolbar Tile (Quick Settings).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "3. Easy Gestures:\n• Single-tap any block to select it\n• Double-tap any block to copy it instantly\n• Hold (long press) any block to preview & edit internally without leaving your app\n• Drag the top grab handle to move the floating bar anywhere on screen (double-tap to reset)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "4. Live OCR & QR Scanner:\nSwitch to 'OCR / QR' mode to scan images, non-text views, games, and QR codes with precise 1:1 text bounds.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showHowToUseDialog = false },
                    modifier = Modifier.testTag("btn_close_how_to_use")
                ) {
                    Text("Got it")
                }
            }
        )
    }
}

@Composable
fun PermissionItemCard(
    title: String,
    desc: String,
    isGranted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    testTag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = if (isGranted) Color(0xFF22C55E) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, start = 24.dp)
                )
            }

            if (!isGranted) {
                FilledTonalButton(
                    onClick = onAction,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(actionLabel, fontSize = 11.sp)
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF22C55E).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "Active",
                        color = Color(0xFF15803D),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
