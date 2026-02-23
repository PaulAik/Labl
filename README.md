# Labl – AR Appliance Symbol Identifier

Point your phone at any appliance control panel or care label and Labl will identify every symbol in real time, telling you exactly what each one means and what to do.

Supports: ovens, washing machines, tumble dryers, dishwashers, irons, and any care label.

## How it works

1. **CameraX** captures live frames from the back camera.
2. Every 3 seconds a frame is encoded as JPEG and sent to the **Claude Vision API**.
3. Claude identifies every symbol and returns structured JSON with the name, category, meaning, and instructions for each.
4. **Jetpack Compose** renders AR-style overlay cards on top of the camera preview, positioned to match the location of the symbols in frame.
5. Tap any card for a full-screen detail sheet.

## Getting started

### Prerequisites

- Android Studio Hedgehog or newer
- Android device with API 26+ (Android 8.0)
- A Claude API key from [console.anthropic.com](https://console.anthropic.com)

### Setup

```bash
git clone <repo>
cd Labl

# Copy the example local.properties
cp local.properties.example local.properties

# Edit local.properties and set:
#   sdk.dir=<path to your Android SDK>
#   CLAUDE_API_KEY=sk-ant-...      ← optional; can also be entered in-app
```

Open the project in Android Studio, sync Gradle, run on a physical device (camera required).

> **API key**: if you leave `CLAUDE_API_KEY` blank in `local.properties`, the app will show a settings sheet on first launch where you can enter the key. It is stored in DataStore on the device only.

## Project structure

```
app/src/main/java/com/paulaik/labl/
├── MainActivity.kt
├── api/
│   └── ClaudeApiClient.kt      # Anthropic Messages API (vision)
├── data/
│   ├── ApiKeyStore.kt          # DataStore persistence
│   └── model/
│       └── Symbol.kt           # Data models
├── ml/
│   └── SymbolAnalyzer.kt       # CameraX ImageAnalysis.Analyzer
├── viewmodel/
│   └── CameraViewModel.kt
└── ui/
    ├── screens/
    │   ├── CameraScreen.kt     # Main AR screen
    │   └── SettingsSheet.kt    # API key entry
    ├── components/
    │   ├── SymbolOverlay.kt    # Scanning frame + symbol cards
    │   └── SymbolDetailSheet.kt
    └── theme/
        ├── Color.kt
        ├── Theme.kt
        └── Type.kt
```

## Dependencies

| Library | Purpose |
|---------|---------|
| CameraX 1.3 | Camera preview + image capture |
| Jetpack Compose (BOM 2024.05) | UI |
| Accompanist Permissions | Runtime camera permission |
| OkHttp 4 | HTTP to Claude API |
| DataStore Preferences | Persist API key locally |
| Kotlin Coroutines | Async analysis pipeline |

## Privacy

- Camera frames are sent to Anthropic's API for analysis.
- No images are stored or logged.
- The API key is stored locally in Android DataStore only.
