# Universal Capture (النسخ الشامل)

Universal Capture is a high-performance Android utility engineered to extract, copy, and edit unselectable on-screen text, URLs, and QR codes across any third-party app without root access. By combining Android Accessibility node hierarchy inspection with on-device hardware-accelerated ML Kit OCR, it provides an interactive, non-intrusive overlay with pixel-perfect bounding box alignment.

---

## Core Features

- **Universal Screen Text Extraction**: Seamlessly extracts text from apps that disable native selection (e.g., social media feeds, banking dialogs, system menus, games, and images).
- **Interactive Visual Overlay**: Highlights detected text segments in real time with precise 1:1 bounding boxes, allowing instant single-tap selection, multi-block batch copying, and inline text editing.
- **Movable Floating Toolbar**: Fluidly drag and position the scanning control bar anywhere across the screen to avoid obstructing underlying UI elements, featuring quick reset via double-tap.
- **Live Screen OCR & QR Scanner**: Utilizes Google ML Kit Vision on-device to read text and decode barcodes/QR codes directly from live screenshot hardware buffers with zero latency and complete privacy.
- **Bi-directional (Arabic & LTR) Script Precision**: Purpose-built coordinate mapping engine that eliminates mirroring bugs and guarantees accurate bounding box rendering across RTL (Arabic, Hebrew) and LTR languages.
- **Deep URL & Metadata Sanitization**: Automatically reconstructs broken links, eliminates unwanted OCR whitespace or punctuation artifacts, and counts script characters (Arabic, Latin, digits) in real time.
- **Multiple Non-Intrusive Triggers**: Trigger instant scans via a draggable floating bubble, persistent notification controls, or a dedicated Android Quick Settings Tile.
- **Background Persistence & Battery Exemption Guide**: In-app one-tap diagnosis and battery optimization exemptions to prevent OEM background process termination.

---

## Tech Stack & Architecture

- **Platform**: Android (Kotlin, Min SDK 24, Target SDK 36)
- **UI Framework**: Modern Jetpack Compose with Material Design 3 (M3)
- **Architecture**: MVVM / Clean Architecture with unidirectional state flow (`StateFlow`, `Coroutines`)
- **Computer Vision & ML**: Google ML Kit Text Recognition v2 & Barcode Scanning (100% on-device offline processing)
- **Cloud Fallback (Optional)**: Google Gemini API integration for specialized optical character recognition
- **System Services**:
  - `AccessibilityService` (`CaptureAccessibilityService`): Traverses window node hierarchies and captures screenshot hardware buffers.
  - `WindowManager` Overlay (`OverlayService` & `FloatingTriggerService`): System-level interactive overlay with strict layout gravity handling.
  - `TileService` (`QuickCaptureTileService`): Quick Settings notification shade tile.
  - `BroadcastReceiver` (`CaptureActionReceiver`): System-wide capture intent routing.

---

## Prerequisites & Setup

### Prerequisites
- Android Studio Ladybug | 2024.2.1 or newer
- JDK 17 or higher
- Android SDK 36 (Android 15+) with build tools installed
- Physical Android device or emulator running Android 7.0 (API 24) or higher (Android 11+ recommended for screenshot OCR)

### Installation Steps

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/FAYSAL-Q8/universal-capture.git
   cd universal-capture
   ```

2. **Environment Configuration (Optional)**:
   Universal Capture runs 100% offline using on-device ML Kit. If you wish to enable the optional cloud fallback OCR engine:
   ```bash
   cp .env.example .env
   # Add your Gemini API key inside .env:
   # GEMINI_API_KEY=your_key_here
   ```

3. **Build the Project**:
   ```bash
   gradle assembleDebug
   ```

4. **Install onto Device**:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

---

## Usage Guide

1. **Grant Necessary Permissions**:
   - Open **Universal Capture** from the app launcher.
   - Tap **Enable in Settings** to turn on the Accessibility Service.
   - Grant **Display Over Other Apps** (System Alert Window) to enable the interactive overlay.
   - Tap **Unrestrict** under Battery Optimization to ensure uninterrupted background standby.

2. **Capturing Text On-Screen**:
   - **Method A (Floating Bubble)**: Tap the floating screen bubble on any screen to trigger an immediate overlay.
   - **Method B (Notification)**: Pull down the notification drawer and tap **Capture Screen**.
   - **Method C (Quick Settings)**: Tap the **Universal Capture** tile in your Quick Settings shade.

3. **Interacting with Extracted Text**:
   - **Single Tap**: Toggle selection on any block or QR code.
   - **Double Tap**: Instantly copy that individual block directly to your clipboard.
   - **Long Press**: Open internal preview and editor to edit or append text without closing the overlay.
   - **Drag Floating Bar**: Drag the top grab handle of the bottom bar to reposition it anywhere on screen.
   - **Copy All / View List**: Copy all selected blocks at once or open the detailed full-screen extraction list.

---

## Author

**Faysal Alibrahim**  
GitHub: [https://github.com/FAYSAL-Q8](https://github.com/FAYSAL-Q8)

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
