# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**url_porn_autoblocklist** is an Android app that monitors browser URLs in real-time via Accessibility Service and blocks pornographic content using on-device TensorFlow Lite ML models. No cloud communication — fully offline inference.

- **Platform**: Android (Kotlin + Jetpack Compose + Material 3)
- **DI**: Dagger Hilt 2.54
- **ML Runtime**: LiteRT (TensorFlow Lite) 1.4.1
- **compileSdk / targetSdk / minSdk**: 35 / 35 / 26

---

## Build Commands

```bash
# Debug APK
./gradlew assembleDebug

# Release APK (minified)
./gradlew assembleRelease

# Full build (compile + lint + test)
./gradlew build
```

**No automated tests exist** — the project has JUnit and Espresso dependencies configured but no test files under `app/src/test/` or `app/src/androidTest/`. Model accuracy is evaluated in-app via `EvaluationScreen`, not CI.

**Logcat tags to monitor during development**:
- `UrlBlockerAccessibilityService` — URL detection events
- `UrlClassifier` — CNN-1D input dtype, input shape, score
- `RandomForestClassifier` — feature values, score
- `ClassifierManager` — which model file was loaded

---

## Architecture

```
Browser URL Bar (16+ supported browsers)
        ↓
UrlBlockerAccessibilityService       ← AccessibilityEvent listener
        ↓
ClassifierManager                    ← active model selector
        ↓
UrlClassifier (CNN-1D)   OR   RandomForestClassifier (RF)
        ↓                                    ↓
Char tokenization                   7 lexical feature extraction
        ↓                                    ↓
url_classifier.tflite               url_classifier_rf.tflite
(CNN1D.tflite fallback)             (RandomForest.tflite fallback)
        ↓
score > 0.7 ?
   ├── Yes → BlockOverlayActivity (red block screen)
   └── No  → DetectionInfoOverlayActivity (debug info, 15s auto-close)
```

All records are persisted via `BlocklistRepository` (SharedPreferences-backed JSON). `HomeScreen` displays statistics and allows live model switching without restart.

---

## Key Source Files

| File | Role |
|------|------|
| `service/UrlBlockerAccessibilityService.kt` | Core URL monitoring service |
| `data/classifier/ClassifierManager.kt` | Loads and switches between CNN/RF |
| `data/classifier/UrlClassifier.kt` | CNN-1D TFLite inference |
| `data/classifier/RandomForestClassifier.kt` | RF TFLite inference + feature extraction |
| `domain/repository/IUrlClassifier.kt` | `UrlClassificationResult` data class + classifier interface |
| `data/model/BlockedDomainRecord.kt` | `LexicalFeatures` Parcelable/Serializable data class |
| `data/repository/BlocklistRepository.kt` | History + statistics persistence |
| `presentation/ui/DetectionInfoOverlayActivity.kt` | Debug overlay for safe URLs |

---

## TFLite Model Assets

| File | Size | Type | Notes |
|------|------|------|-------|
| `url_classifier.tflite` | ~274 KB | CNN-1D | Primary CNN model, 4.5M training URLs |
| `CNN1D.tflite` | ~75 KB | CNN-1D | Fallback CNN, 100K training URLs |
| `url_classifier_rf.tflite` | ~128 KB | Random Forest | Primary RF model, works reliably |
| `RandomForest.tflite` | ~2.9 MB | Random Forest | Fallback RF — may throw GATHER errors during inference (not during load) |

**Model load order** (`ClassifierManager.kt`):
- CNN-1D: `url_classifier.tflite` first, then `CNN1D.tflite`
- RF: `url_classifier_rf.tflite` first, then `RandomForest.tflite`

---

## Critical Non-Obvious Constraints

### CNN-1D Input Format
The model may expect **INT32** (for Embedding lookup) instead of FLOAT32. `UrlClassifier.loadModel()` logs the actual `dataType()` from the model tensor — always check `Input dtype` in logcat after loading. Sending the wrong type causes a silent exception → score=0.0 → all URLs classified as safe. The input buffer is built adaptively using `putInt()` or `putFloat()` based on the loaded model's actual dtype.

**Tokenization**: domain is normalized to the core name only (e.g., `play.google.com` → `google`), then each character is encoded:
- `a–z` → indices 2–27
- `0–9` → indices 28–37
- `.` → 38, `-` → 39, `_` → 40
- PAD → 0, UNK → 1
- Max length comes from the model tensor shape, not a hardcoded constant.

### Random Forest Features (F1–F7)
No feature scaling is applied (training used `X_train_scaled = X_train`). The feature vector must exactly match the Python training code:

| Code | Field | Type | Description |
|------|-------|------|-------------|
| F1 | `domainLength` | Int | Total length of domain string |
| F2 | `numDigits` | Int | Count of digit characters |
| F3 | `numDots` | Int | Count of `.` characters |
| F4 | `numDelimiters` | Int | Count of non-alphanumeric chars except `.` |
| F5 | `suspiciousWordCount` | **Int count** | How many suspicious words found (NOT boolean) |
| F6 | `digitToLetterRatio` | Float | Ratio of digits to letters |
| F7 | `maxSequentialDigits` | **Int max length** | Longest run of consecutive digits (NOT boolean) |

F5 and F7 are integers (counts/lengths), not booleans. Sending boolean 0/1 causes `gather index out of bounds` errors in `RandomForest.tflite`. The RF classifier operates on the **full domain** (e.g., `youporn.com`), unlike CNN which strips subdomains.

### `LexicalFeatures` is `@Parcelize` + `@Serializable`
Changing field names or types in `BlockedDomainRecord.LexicalFeatures` can break deserialization of data already stored in SharedPreferences. Handle with care.

### GATHER Error in `RandomForest.tflite`
The error `gather index out of bounds. Node number 13 (GATHER) failed to invoke` occurs at inference time, not load time. If the model loads successfully, all future inference calls still route to it and will fail. This is why `url_classifier_rf.tflite` is the primary RF model.

### `DetectionInfoOverlayActivity` is a debug tool
It shows a 15-second overlay for every safe URL that passes through the classifier. Disable it in release builds to avoid annoying users.

---

## Browser Support

The service recognizes URL bars in 16+ browser variants by package name and view ID: Chrome (stable/beta/dev/canary), Samsung Internet (stable/beta), Firefox (both versions), Edge, Opera, Brave, Vivaldi, Kiwi, Bromite, DuckDuckGo.

---

## Hilt DI Setup

`AppModule.kt` provides:
- `Context` (application)
- `ClassifierManager` singleton
- `BlocklistRepository` singleton

`UrlBlockerAccessibilityService` uses field injection (`@AndroidEntryPoint`). `MainActivity` and both overlay activities are also `@AndroidEntryPoint`.
