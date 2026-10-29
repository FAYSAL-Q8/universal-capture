/**
 * Universal Capture - Interactive Capture Overlay
 *
 * Author: Faysal Alibrahim
 * GitHub: https://github.com/FAYSAL-Q8
 */

package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt
import com.example.ResultActivity
import com.example.models.CaptureResult
import com.example.models.ExtractedBlock
import com.example.services.CaptureAccessibilityService

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CaptureOverlay(
    result: CaptureResult?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val blocks = remember(result) { result?.blocks ?: emptyList() }
    val fullText = remember(result) { result?.fullText ?: "" }
    val urls = remember(result) { result?.urls ?: emptyList() }
    val qrCodes = remember(result) { result?.qrCodes ?: emptyList() }
    val isLiveOcrSource = result?.source == "LIVE_SCREEN_OCR"

    // Multi-selection state
    val selectedBlockIds = remember { mutableStateListOf<String>() }

    // User-requested list sheet (hidden by default)
    var showListSheet by remember { mutableStateOf(false) }

    // Currently focused single block for quick tooltip action
    var focusedBlockId by remember { mutableStateOf<String?>(null) }

    // Internal In-Screen Preview & Edit state (keeps overlay session active!)
    var previewEditingText by remember { mutableStateOf<String?>(null) }

    // Live OCR loading state
    var isLiveOcrScanning by remember { mutableStateOf(false) }

    // Movable Floating Toolbar State (allows dragging floating bar anywhere on screen)
    var toolbarOffsetX by remember { mutableFloatStateOf(0f) }
    var toolbarOffsetY by remember { mutableFloatStateOf(0f) }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        val previewSnippet = text.take(30).replace("\n", " ")
        Toast.makeText(context, "Copied: \"$previewSnippet${if (text.length > 30) "..." else ""}\"", Toast.LENGTH_SHORT).show()
    }

    fun openUrl(url: String) {
        try {
            val formatted = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formatted)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open URL", Toast.LENGTH_SHORT).show()
        }
    }

    fun copySelected(closeAfter: Boolean = false) {
        if (selectedBlockIds.isEmpty()) {
            if (fullText.isNotBlank()) {
                copyToClipboard("Screen Text", fullText)
                if (closeAfter) onDismiss()
            }
        } else {
            val selectedText = blocks
                .filter { it.id in selectedBlockIds }
                .joinToString("\n\n") { it.text }
            if (selectedText.isNotBlank()) {
                copyToClipboard("Selected Text", selectedText)
                if (closeAfter) onDismiss()
            }
        }
    }

    fun toggleBlock(block: ExtractedBlock) {
        if (block.id in selectedBlockIds) {
            selectedBlockIds.remove(block.id)
            if (focusedBlockId == block.id) focusedBlockId = null
        } else {
            selectedBlockIds.add(block.id)
            focusedBlockId = block.id
        }
    }

    fun triggerRescan() {
        val service = CaptureAccessibilityService.instance
        if (service != null) {
            if (isLiveOcrSource) {
                isLiveOcrScanning = true
                service.triggerLiveOcrAndQr { isLiveOcrScanning = false }
            } else {
                service.triggerCapture(delayBeforeScanMs = 0L)
            }
            Toast.makeText(context, "Screen rescanned", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleLiveOcrMode() {
        val service = CaptureAccessibilityService.instance
        if (service != null) {
            if (!isLiveOcrSource) {
                isLiveOcrScanning = true
                service.triggerLiveOcrAndQr { isLiveOcrScanning = false }
            } else {
                service.triggerCapture(delayBeforeScanMs = 0L)
            }
        }
    }

    // Root clear container: transparent background so user sees their actual screen
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.06f))
            .testTag("clear_capture_overlay_root")
    ) {
        val containerMaxWidth = maxWidth
        val containerMaxHeight = maxHeight
        val screenWidthPx = with(density) { containerMaxWidth.toPx() }
        val screenHeightPx = with(density) { containerMaxHeight.toPx() }

        // Precise coordinate scaling factor between OCR screenshot resolution and window viewport
        val scaleX = if (result?.bitmapWidth != null && result.bitmapWidth > 0) {
            screenWidthPx / result.bitmapWidth.toFloat()
        } else 1f

        val scaleY = if (result?.bitmapHeight != null && result.bitmapHeight > 0) {
            screenHeightPx / result.bitmapHeight.toFloat()
        } else 1f

        val maxDragX = with(density) { (containerMaxWidth.toPx() / 2f) - 30.dp.toPx() }
        val minDragY = with(density) { -(containerMaxHeight.toPx() - 110.dp.toPx()) }
        val maxDragY = with(density) { 10.dp.toPx() }

        // 1. On-Screen Visual Bounding Boxes Over Text & QR (Strict LTR coordinates)
        fun getBlockArea(block: ExtractedBlock): Long {
            val r = block.bounds ?: return 0L
            return (kotlin.math.abs(r.width().toLong()) * kotlin.math.abs(r.height().toLong())).coerceAtLeast(0L)
        }

        // Sort: Large blocks FIRST (drawn in background), Smaller blocks LAST (drawn on top in foreground)
        val sortedDisplayBlocks = remember(blocks) {
            blocks.sortedWith(
                compareByDescending<ExtractedBlock> { getBlockArea(it) }
                    .thenBy { it.isQrCode.not() }
            )
        }

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(modifier = Modifier.fillMaxSize()) {
                sortedDisplayBlocks.forEachIndexed { index, block ->
                    val rect = block.bounds
                    if (rect != null && kotlin.math.abs(rect.width()) > 4 && kotlin.math.abs(rect.height()) > 4) {
                        val rectLeft = minOf(rect.left, rect.right).toFloat()
                        val rectTop = minOf(rect.top, rect.bottom).toFloat()
                        val rectWidth = kotlin.math.abs(rect.width()).toFloat()
                        val rectHeight = kotlin.math.abs(rect.height()).toFloat()

                        // Apply resolution scaling for OCR screenshots to align perfectly with text
                        val scaledLeft = rectLeft * scaleX
                        val scaledTop = rectTop * scaleY
                        val scaledWidth = rectWidth * scaleX
                        val scaledHeight = rectHeight * scaleY

                        val leftDp = with(density) { scaledLeft.toDp() }
                        val topDp = with(density) { scaledTop.toDp() }
                        // Tight bounds matching actual text size - minimal padding
                        val widthDp = with(density) { maxOf(scaledWidth.toDp(), 8.dp) }
                        val heightDp = with(density) { maxOf(scaledHeight.toDp(), 6.dp) }

                        val isSelected = block.id in selectedBlockIds
                        val isQr = block.isQrCode
                        val area = getBlockArea(block)
                        val isLargeContainer = area > 100000L

                        // Explicit zIndex: smaller blocks have strictly higher z-order than larger blocks
                        val blockZIndex = if (isQr) {
                            2500f
                        } else {
                            1f + (index.toFloat() * 10f)
                        }

                        Box(
                            modifier = Modifier
                                .zIndex(blockZIndex)
                                .absoluteOffset(x = leftDp, y = topDp)
                                .size(width = widthDp, height = heightDp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    when {
                                        isQr -> Color(0xFFA855F7).copy(alpha = if (isSelected) 0.5f else 0.25f)
                                        isSelected -> Color(0xFF0284C7).copy(alpha = 0.45f)
                                        isLargeContainer -> Color(0xFF38BDF8).copy(alpha = 0.05f) // subtle for large background blocks
                                        else -> Color(0xFF38BDF8).copy(alpha = 0.15f) // crisp for smaller foreground blocks
                                    }
                                )
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = when {
                                        isQr -> Color(0xFFA855F7)
                                        isSelected -> Color(0xFF0284C7)
                                        isLargeContainer -> Color(0xFF38BDF8).copy(alpha = 0.35f)
                                        else -> Color(0xFF38BDF8).copy(alpha = 0.75f)
                                    },
                                    shape = RoundedCornerShape(3.dp)
                                )
                                .combinedClickable(
                                    // 1. Single click = toggle selection & show action pill
                                    onClick = {
                                        toggleBlock(block)
                                    },
                                    // 2. Double click = directly copy it without closing screen so user can copy more!
                                    onDoubleClick = {
                                        copyToClipboard("Snippet", block.text)
                                    },
                                    // 3. Hold (long press) = preview and edit internally without closing overlay!
                                    onLongClick = {
                                        previewEditingText = block.text
                                    }
                                )
                                .testTag("bounding_box_${block.id}")
                        ) {
                            if (isQr) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFFA855F7),
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(block.qrType ?: "QR", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Quick Floating Action Pill for Focused Selection
                val focusedBlock = blocks.find { it.id == focusedBlockId }
                if (focusedBlock != null && !showListSheet && previewEditingText == null) {
                    val rect = focusedBlock.bounds
                    val targetTop = if (rect != null) {
                        with(density) {
                            val scaledRectTop = (minOf(rect.top, rect.bottom) * scaleY).toDp()
                            if (scaledRectTop > 60.dp) scaledRectTop - 50.dp else scaledRectTop + (kotlin.math.abs(rect.height()) * scaleY).toDp() + 8.dp
                        }
                    } else 120.dp
                    val rawLeft = if (rect != null) {
                        with(density) { (minOf(rect.left, rect.right) * scaleX).toDp() }
                    } else 24.dp
                    val clampedLeft = rawLeft.coerceIn(12.dp, maxOf(12.dp, containerMaxWidth - 280.dp))

                    Surface(
                        modifier = Modifier
                            .zIndex(5000f)
                            .absoluteOffset(x = clampedLeft, y = targetTop)
                            .shadow(12.dp, RoundedCornerShape(20.dp))
                            .testTag("floating_quick_action_pill"),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Copy (keeps overlay open so user can copy more!)
                            FilledTonalButton(
                                onClick = {
                                    copyToClipboard("Snippet", focusedBlock.text)
                                    focusedBlockId = null
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy", fontSize = 12.sp)
                            }

                            // Preview & Edit internally without shutting down overlay
                            IconButton(
                                onClick = { previewEditingText = focusedBlock.text },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Preview & Edit", modifier = Modifier.size(16.dp))
                            }

                            // Open link if URL or QR URL
                            if (focusedBlock.isUrl || focusedBlock.text.startsWith("http")) {
                                FilledTonalIconButton(
                                    onClick = { openUrl(focusedBlock.text) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open Link", modifier = Modifier.size(14.dp))
                                }
                            }

                            IconButton(
                                onClick = { focusedBlockId = null },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss pill", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }

        // Loading banner for Live OCR
        if (isLiveOcrScanning) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp)
                    .shadow(8.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Scanning live screen & QR codes...", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // 3. Floating Movable Toolbar
        if (!showListSheet && previewEditingText == null) {
            Surface(
                modifier = Modifier
                    .zIndex(6000f)
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp, start = 8.dp, end = 8.dp)
                    .offset { IntOffset(toolbarOffsetX.roundToInt(), toolbarOffsetY.roundToInt()) }
                    .shadow(16.dp, RoundedCornerShape(24.dp))
                    .testTag("floating_bottom_toolbar"),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                tonalElevation = 6.dp
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    // Top Drag Grab Bar - clearly indicates the floating bar is movable!
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    toolbarOffsetX = (toolbarOffsetX + dragAmount.x).coerceIn(-maxDragX, maxDragX)
                                    toolbarOffsetY = (toolbarOffsetY + dragAmount.y).coerceIn(minDragY, maxDragY)
                                }
                            }
                            .combinedClickable(
                                onClick = {},
                                onDoubleClick = {
                                    // Double-tap snaps floating bar back to default center-bottom
                                    toolbarOffsetX = 0f
                                    toolbarOffsetY = 0f
                                    Toast.makeText(context, "Position reset", Toast.LENGTH_SHORT).show()
                                }
                            )
                            .testTag("toolbar_top_grab_bar"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        )
                    }

                    // Main Controls Row
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Movable Drag Indicator Handle
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        toolbarOffsetX = (toolbarOffsetX + dragAmount.x).coerceIn(-maxDragX, maxDragX)
                                        toolbarOffsetY = (toolbarOffsetY + dragAmount.y).coerceIn(minDragY, maxDragY)
                                    }
                                }
                                .testTag("toolbar_drag_handle"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DragIndicator,
                                contentDescription = "Drag to move floating bar",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Close / Exit Overlay session
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("btn_toolbar_close")
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Exit overlay",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Rescan Button
                        IconButton(
                            onClick = { triggerRescan() },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("btn_toolbar_rescan")
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Rescan Screen",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Switch Mode: Normal Hierarchy vs Live Screen OCR & QR
                        FilterChip(
                            selected = isLiveOcrSource,
                            onClick = { toggleLiveOcrMode() },
                            label = {
                                Text(
                                    if (isLiveOcrSource) "Live OCR" else "OCR / QR",
                                    fontSize = 11.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (isLiveOcrSource) Icons.Default.DocumentScanner else Icons.Default.QrCodeScanner,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            modifier = Modifier.height(34.dp)
                        )

                        // Select All Toggle
                        IconButton(
                            onClick = {
                                if (selectedBlockIds.size == blocks.size) {
                                    selectedBlockIds.clear()
                                } else {
                                    selectedBlockIds.clear()
                                    selectedBlockIds.addAll(blocks.map { it.id })
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("btn_toolbar_select_all")
                        ) {
                            Icon(
                                imageVector = if (selectedBlockIds.size == blocks.size && blocks.isNotEmpty())
                                    Icons.Default.SelectAll
                                else
                                    Icons.Default.Deselect,
                                contentDescription = "Toggle Select All",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Copy Action Button (copies and keeps screen alive!)
                        Button(
                            onClick = { copySelected(closeAfter = false) },
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("btn_toolbar_copy"),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (selectedBlockIds.isNotEmpty()) "Copy (${selectedBlockIds.size})" else "Copy All",
                                fontSize = 12.sp
                            )
                        }

                        // "View List" Button - ONLY displays copyable list when explicitly clicked!
                        FilledTonalButton(
                            onClick = { showListSheet = true },
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("btn_toolbar_show_list"),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("List", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 4. In-Screen Preview & Edit Panel
        AnimatedVisibility(
            visible = previewEditingText != null,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
            modifier = Modifier
                .zIndex(7000f)
                .align(Alignment.BottomCenter)
        ) {
            val textToEdit = previewEditingText ?: ""
            var tempEditedText by remember(previewEditingText) { mutableStateOf(textToEdit) }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 16.dp)
                    .shadow(24.dp, RoundedCornerShape(24.dp))
                    .testTag("internal_preview_edit_card"),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 10.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.EditNote,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Preview & Edit Text",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${tempEditedText.length} chars · Overlay stays active",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Close ONLY this preview card (keeps scan screen active!)
                        IconButton(
                            onClick = { previewEditingText = null },
                            modifier = Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close preview", modifier = Modifier.size(16.dp))
                        }
                    }

                    // Editable Content Field
                    OutlinedTextField(
                        value = tempEditedText,
                        onValueChange = { tempEditedText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 220.dp),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Action Buttons Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Copy & Stay on screen (User requested: access multiple texts without shutdown!)
                        Button(
                            onClick = {
                                copyToClipboard("Edited Text", tempEditedText)
                                previewEditingText = null // Close only the preview panel, screen stays alive!
                            },
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy & Keep Screen", fontSize = 12.sp)
                        }

                        // 2. Copy & Exit (if done)
                        FilledTonalButton(
                            onClick = {
                                copyToClipboard("Edited Text", tempEditedText)
                                previewEditingText = null
                                onDismiss() // Done with session
                            },
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text("Copy & Exit", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 5. Extracted Content List Sheet
        AnimatedVisibility(
            visible = showListSheet,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .zIndex(8000f)
                .align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .testTag("user_requested_list_sheet"),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Extracted Content List",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${blocks.size} elements · ${qrCodes.size} QR · ${urls.size} links",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { showListSheet = false },
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                .testTag("btn_close_list_sheet")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close list")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action buttons inside sheet
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (fullText.isNotBlank()) {
                                    copyToClipboard("Screen Text", fullText)
                                    // Stays alive so user can continue!
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy All", fontSize = 12.sp)
                        }

                        if (urls.isNotEmpty()) {
                            FilledTonalButton(
                                onClick = {
                                    val allUrls = urls.joinToString("\n")
                                    copyToClipboard("Extracted URLs", allUrls)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("URLs (${urls.size})", fontSize = 12.sp)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                val intent = Intent(context, ResultActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    putExtra(ResultActivity.EXTRA_TEXT, fullText)
                                    putExtra(ResultActivity.EXTRA_SOURCE, result?.source ?: "ACCESSIBILITY")
                                }
                                context.startActivity(intent)
                                onDismiss()
                            },
                            modifier = Modifier.height(40.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open Editor", modifier = Modifier.size(16.dp))
                        }
                    }

                    // QR Codes Row if detected
                    if (qrCodes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Detected QR Codes:",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFA855F7),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(qrCodes) { qr ->
                                SuggestionChip(
                                    onClick = {
                                        copyToClipboard("QR Code", qr)
                                    },
                                    label = {
                                        Text(qr, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                                    },
                                    icon = {
                                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFA855F7))
                                    }
                                )
                            }
                        }
                    }

                    // Extracted URLs Row if available
                    if (urls.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Detected URLs:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(urls) { url ->
                                SuggestionChip(
                                    onClick = {
                                        copyToClipboard("URL", url)
                                    },
                                    label = {
                                        Text(url, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                                    },
                                    icon = {
                                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp))
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Tap to copy · Edit button to modify internally:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Full List
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(blocks) { block ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            copyToClipboard("Snippet", block.text)
                                        },
                                        onDoubleClick = {
                                            copyToClipboard("Snippet", block.text)
                                        },
                                        onLongClick = {
                                            previewEditingText = block.text
                                            showListSheet = false
                                        }
                                    ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (block.isQrCode)
                                        Color(0xFFA855F7).copy(alpha = 0.15f)
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = block.text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 4,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.padding(top = 4.dp)
                                        ) {
                                            if (block.isQrCode) {
                                                Text(
                                                    text = "QR Code (${block.qrType ?: "Code"})",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFFA855F7),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            if (block.hasArabic) {
                                                Text(
                                                    text = "العربية / Arabic",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Row {
                                        IconButton(
                                            onClick = {
                                                previewEditingText = block.text
                                                showListSheet = false
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                copyToClipboard("Snippet", block.text)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy snippet",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    AppFooter(modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}
