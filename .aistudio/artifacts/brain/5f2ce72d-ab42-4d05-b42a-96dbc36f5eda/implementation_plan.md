# Implementation Plan: Universal Capture

Universal Capture is a lightweight Android application designed to extract text from any screen, even from non-selectable UI elements or images. It features a fast on-device OCR engine with an optional online mode for high-accuracy requirements, supporting both English and Arabic.

### User Review & Critical Decisions

> [!IMPORTANT]
> The app is designed to be minimal and icon-driven, relying on system accessibility services for text detection.

- **Confirmed Decision 1**: Activation via Quick-access Notification Toggle.
- **Confirmed Decision 2**: OCR support for English and Arabic (both on-device offline and cloud online options).
- **Confirmed Decision 3**: URL extraction from images must be accurate.

### 1. Overview & Core Concept

- **What It Does**: Allows users to pull text from any app on the screen, providing tools to copy, share, or translate (future extension).
- **Target Audience**: Power users who need to extract text from restrictive UIs, images, or documents quickly.
- **Key Value**: Speed and convenience by reducing manual entry.

### 2. User Experience & Visual Design

- **Key User Flows**:
    1.  User taps the notification toggle to enter "Capture Mode".
    2.  Screen overlay becomes active; user selects a region.
    3.  App processes the selection (Accessibility Service or OCR).
    4.  Captured text is presented in a simple, scrollable overlay for copying/sharing.
- **Visual Identity & Theme**:
    - *Aesthetic Direction*: Minimalist, functional, utilitarian.
    - *Color Palette & Mood*: High-contrast dark mode for easy reading; subtle accents for actions.
    - *Component Styling*: M3 cards, icon buttons, no explanatory text.
- **Interactive Feedback**: Ripple effects on all interactions, subtle loading indicators during OCR processing.

### 3. Key Product Decisions & Trade-Offs

- **Decision 1**: Using Android Accessibility Service vs. Screen Recording.
    - *Chosen Approach*: Accessibility Service.
    - *Why*: Privacy-first, lower power consumption, faster response for text detection in view hierarchies.
- **Decision 2**: On-device OCR (ML Kit) vs. Cloud OCR.
    - *Chosen Approach*: Both.
    - *Why*: Provides the best balance of speed (offline) and precision (online) as requested.

### 4. Technical Architecture & Data Strategy

```
┌───────────────────────────┐
│ AccessibilityService      │ <───> [Screen UI Traversal]
└─────────────┬─────────────┘
              │
┌─────────────▼─────────────┐
│ CaptureManager            │ <───> [ML Kit Text Recognition]
└─────────────┬─────────────┘       (On-device & Cloud)
              │
┌─────────────▼─────────────┐
│ Overlay/Result Activity   │ <───> [DataStore (Settings)]
└─────────────────────────────┘
```

- **Data Model & State**: `CaptureState` (Idle, Capturing, Processing, Success, Error).
- **Key Sequences**:
    1.  Accessibility Service traverses the hierarchy.
    2.  CaptureManager detects region.
    3.  If text is found in view: extract directly.
    4.  If not: capture bitmap, pass to ML Kit (Offline first, then Online fallback if prompted).
    5.  Result is displayed in a minimal overlay.
