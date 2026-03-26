# Modular Refactoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split large multi-concern Kotlin files into small, single-context files — one responsibility per file — without changing any behavior.

**Architecture:** Four sequential layers: Repository → ViewModel → UI → Utilities. Each layer is independent and ends with `./gradlew test` to verify nothing broke. All public interfaces, companion object methods, and test call sites remain unchanged. Only code moves; no logic changes.

**Tech Stack:** Kotlin, Hilt (DI), Jetpack Compose, Android. Test runner: `./gradlew test`.

---

## Baseline

Before starting any task:

- [ ] **Step 0: Verify baseline**

```bash
cd /home/cocodedk/0-projects/BabakCast
./gradlew test
```

Expected: all tests pass. If any fail, stop and investigate before proceeding.

---

## Layer 1: Repository

---

### Task 1: Extract YoutubeDlWrapper from MediaRepository

This task extracts everything that touches `YoutubeDLManager` out of `MediaRepository` into a new internal class `YoutubeDlWrapper`. `MediaRepository` will instantiate and delegate to it.

**Files:**
- Create: `app/src/main/java/com/cocode/babakcast/data/repository/YoutubeDlWrapper.kt`
- Modify: `app/src/main/java/com/cocode/babakcast/data/repository/MediaRepository.kt`

**What moves to YoutubeDlWrapper:**
- The `progressPercentRegex` and `lastLoggedProgressBucket` properties (lines 42–43)
- `normalizeProgress()` private method (lines 413–428)
- `logProgressIfNeeded()` private method (lines 430–438)
- `buildInfoRequest()` companion method (lines 508–517)
- `buildDownloadRequest()` companion method (lines 519–530)
- The yt-dlp info and download calls inside `getVideoInfo()` and `downloadVideo()` — extract the core YoutubeDL execution blocks into methods on `YoutubeDlWrapper`
- The transcript extraction logic including `extractTranscript()`, `parseTranscriptFromOutput()`, `parseTranscriptFromVtt()`, `looksLikeYtdlpLog()` (lines 304–411)

**Important constraints:**
- `buildInfoRequest` and `buildDownloadRequest` are tested via `MediaRequestBuilderTest` which calls `MediaRepository.buildInfoRequest(...)` and `MediaRepository.buildDownloadRequest(...)`. These companion methods **must stay in `MediaRepository`'s companion object** but can delegate to `YoutubeDlWrapper.buildInfoRequest(...)` internally.
- `MediaRepository` keeps all its public method signatures intact.

- [ ] **Step 1: Create YoutubeDlWrapper.kt**

```kotlin
package com.cocode.babakcast.data.repository

import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

/**
 * Wraps all YoutubeDL/yt-dlp interactions: request building, progress parsing,
 * info fetching, downloading, and transcript extraction.
 *
 * This is an internal implementation detail of [MediaRepository].
 */
internal class YoutubeDlWrapper(private val tag: String) {
    // Move progressPercentRegex and lastLoggedProgressBucket here
    // Move normalizeProgress(), logProgressIfNeeded() here
    // Move buildInfoRequest(), buildDownloadRequest() here (keep them internal)
    // Move the core YoutubeDL execution logic from getVideoInfo() and downloadVideo() here as methods
    // Move extractTranscript(), parseTranscriptFromOutput(), parseTranscriptFromVtt(), looksLikeYtdlpLog() here
}
```

Copy the following from `MediaRepository.kt` into `YoutubeDlWrapper`:
- Lines 42–43 as instance properties
- Lines 413–438 as private methods
- Lines 508–530 as internal methods (not companion — companion stays in MediaRepository)
- The yt-dlp execution blocks from `getVideoInfo` (lines 83–103) and `downloadVideo` (lines 135–165) as dedicated methods (e.g., `fetchInfo(request)` and `executeDownload(request, progressCallback)`)
- Lines 304–411 as methods

- [ ] **Step 2: Update MediaRepository to instantiate and delegate**

In `MediaRepository.kt`:
1. Add `private val ytDl = YoutubeDlWrapper(tag)` as a property
2. Replace the inline yt-dlp execution blocks in `getVideoInfo()` and `downloadVideo()` with calls to `ytDl.fetchInfo(...)` and `ytDl.executeDownload(...)`
3. Replace the inline transcript logic in `extractTranscript()` with a call to `ytDl.extractTranscript(...)`
4. Remove `progressPercentRegex`, `lastLoggedProgressBucket`, `normalizeProgress()`, `logProgressIfNeeded()`, `parseTranscriptFromOutput()`, `parseTranscriptFromVtt()`, `looksLikeYtdlpLog()` from MediaRepository (they now live in YoutubeDlWrapper)
5. In the companion object, keep `buildInfoRequest()` and `buildDownloadRequest()` but have them delegate: `return YoutubeDlWrapper.buildInfoRequest(url, outputDir)` (make the wrapper methods `internal` accessible from companion, or keep the implementations in companion and remove from wrapper — your choice, as long as tests pass)

- [ ] **Step 3: Run tests**

```bash
./gradlew test
```

Expected: all tests pass. Fix any compile errors from missing imports before proceeding.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/cocode/babakcast/data/repository/
git commit -m "refactor: extract YoutubeDlWrapper from MediaRepository"
```

---

### Task 2: Extract XDirectDownloader from MediaRepository

Extract all OkHttp-based X/Twitter download logic into a new internal class `XDirectDownloader`.

**Files:**
- Create: `app/src/main/java/com/cocode/babakcast/data/repository/XDirectDownloader.kt`
- Modify: `app/src/main/java/com/cocode/babakcast/data/repository/MediaRepository.kt`

**What moves to XDirectDownloader:**
- The OkHttp image download helper used inside `downloadAllXMedia()` (the inline lambda that downloads a photo file)
- The direct MP4 download helper used for `TweetMedia.Video` with a non-null URL
- `guessImageExtension()` logic (but its companion forwarding stub stays in `MediaRepository.companion`)

**Important constraints:**
- `MediaRepository.guessImageExtension(url)` is tested by `MediaRepositoryXMediaTest`. Keep the companion method in `MediaRepository` — it can delegate internally to `XDirectDownloader.guessImageExtension(url)` or keep its implementation directly (it's only 7 lines; if simpler, leave it in companion and don't move it).
- `downloadAllXMedia()` stays as a public method in `MediaRepository`. Only its implementation delegates to `XDirectDownloader`.

- [ ] **Step 1: Create XDirectDownloader.kt**

```kotlin
package com.cocode.babakcast.data.repository

import android.util.Log
import com.cocode.babakcast.data.model.TweetMedia
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Handles direct OkHttp-based file downloads for X/Twitter media:
 * photos (JPEG/PNG/WebP), direct MP4 video URLs.
 *
 * This is an internal implementation detail of [MediaRepository].
 */
internal class XDirectDownloader(
    private val okHttpClient: OkHttpClient,
    private val tag: String
) {
    // Move the photo download helper here
    // Move the direct MP4 download helper here
    // guessImageExtension() can live here as an internal method
}
```

Extract the file-download lambdas/helpers from `downloadAllXMedia()` (lines 185–286) into named methods on this class.

- [ ] **Step 2: Update MediaRepository.downloadAllXMedia() to delegate**

Replace the inline download logic with calls to the `xDownloader` instance property:
```kotlin
private val xDownloader = XDirectDownloader(okHttpClient, tag)
```

`downloadAllXMedia()` keeps its public signature but delegates the actual HTTP work.

- [ ] **Step 3: Run tests**

```bash
./gradlew test
```

Expected: all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/cocode/babakcast/data/repository/
git commit -m "refactor: extract XDirectDownloader from MediaRepository"
```

---

### Task 3: Extract AiModelFetcher from ProviderRepository

Extract the four provider-specific model-fetching API calls into a new internal class.

**Files:**
- Create: `app/src/main/java/com/cocode/babakcast/data/repository/AiModelFetcher.kt`
- Modify: `app/src/main/java/com/cocode/babakcast/data/repository/ProviderRepository.kt`

**What moves to AiModelFetcher:**
- `fetchModelsForProvider()` dispatcher (lines 285–291)
- `fetchOpenAIModels()` (lines 294–314)
- `fetchAnthropicModels()` (lines 317–338)
- `fetchGeminiModels()` (lines 341–360)
- `fetchOpenRouterModels()` (lines 363–383)

- [ ] **Step 1: Create AiModelFetcher.kt**

```kotlin
package com.cocode.babakcast.data.repository

import android.util.Log
import com.cocode.babakcast.data.model.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Fetches the list of available models from each AI provider's API.
 *
 * This is an internal implementation detail of [ProviderRepository].
 */
internal class AiModelFetcher(
    private val okHttpClient: OkHttpClient,
    private val tag: String
) {
    // Move fetchModelsForProvider(), fetchOpenAIModels(), fetchAnthropicModels(),
    // fetchGeminiModels(), fetchOpenRouterModels() here verbatim.
}
```

- [ ] **Step 2: Update ProviderRepository to delegate**

1. Add `private val modelFetcher = AiModelFetcher(okHttpClient, tag)` as a property
2. Remove the five fetch methods from `ProviderRepository`
3. In `ProviderRepository`, call `modelFetcher.fetchModelsForProvider(provider)` wherever `fetchModelsForProvider` was called

- [ ] **Step 3: Run tests**

```bash
./gradlew test
```

Expected: all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/cocode/babakcast/data/repository/
git commit -m "refactor: extract AiModelFetcher from ProviderRepository"
```

---

## Layer 2: ViewModel

---

### Task 4: Extract inline types from MainViewModel

Move all inline data classes, sealed classes, and enums out of `MainViewModel.kt` into their own files. All new files go in the same `ui/main/` package — no import changes needed anywhere.

**Files:**
- Create: `app/src/main/java/com/cocode/babakcast/ui/main/MainUiState.kt`
- Create: `app/src/main/java/com/cocode/babakcast/ui/main/SplitChoiceState.kt`
- Create: `app/src/main/java/com/cocode/babakcast/ui/main/ShareRequest.kt`
- Create: `app/src/main/java/com/cocode/babakcast/ui/main/PendingSplitRequest.kt`
- Create: `app/src/main/java/com/cocode/babakcast/ui/main/TweetTextEvent.kt`
- Modify: `app/src/main/java/com/cocode/babakcast/ui/main/MainViewModel.kt`

- [ ] **Step 1: Create MainUiState.kt**

Copy lines 690–709 of `MainViewModel.kt` verbatim into a new file:

```kotlin
package com.cocode.babakcast.ui.main

// [paste the data class MainUiState(...) definition here verbatim from MainViewModel.kt lines 690-709]
```

- [ ] **Step 2: Create SplitChoiceState.kt**

Copy lines 711–719 of `MainViewModel.kt` verbatim:

```kotlin
package com.cocode.babakcast.ui.main

// [paste SplitChoicePrompt data class and SplitChoiceMediaType enum from lines 711-719]
```

- [ ] **Step 3: Create ShareRequest.kt**

Copy lines 721–728 of `MainViewModel.kt` verbatim:

```kotlin
package com.cocode.babakcast.ui.main

import java.io.File
// (add any imports that the sealed class uses)

// [paste sealed class ShareRequest from lines 721-728]
```

- [ ] **Step 4: Create PendingSplitRequest.kt**

Copy lines 730–737 verbatim:

```kotlin
package com.cocode.babakcast.ui.main

// [paste private sealed class PendingSplitRequest from lines 730-737]
// Note: keep visibility as-is (private to the file is fine — it was private in MainViewModel, it can be internal here since ViewModel references it)
// Change `private sealed class` to `internal sealed class` so MainViewModel can reference it
```

- [ ] **Step 5: Create TweetTextEvent.kt**

Copy lines 739–742 verbatim:

```kotlin
package com.cocode.babakcast.ui.main

// [paste sealed class TweetTextEvent from lines 739-742]
```

- [ ] **Step 6: Remove inline types from MainViewModel.kt**

Delete lines 690–742 from `MainViewModel.kt`. The Kotlin compiler will resolve all references via same-package lookup — no import statements needed anywhere.

- [ ] **Step 7: Run tests**

```bash
./gradlew test
```

Expected: all tests pass. If `PendingSplitRequest` is now `internal` instead of `private`, references inside `MainViewModel` still work.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/cocode/babakcast/ui/main/
git commit -m "refactor: extract inline types from MainViewModel into own files"
```

---

### Task 5: Extract inline types from SettingsViewModel

Same pattern as Task 4.

**Files:**
- Create: `app/src/main/java/com/cocode/babakcast/ui/settings/SettingsUiState.kt`
- Create: `app/src/main/java/com/cocode/babakcast/ui/settings/ProviderState.kt`
- Modify: `app/src/main/java/com/cocode/babakcast/ui/settings/SettingsViewModel.kt`

- [ ] **Step 1: Create SettingsUiState.kt**

Copy lines 233–249 of `SettingsViewModel.kt` verbatim:

```kotlin
package com.cocode.babakcast.ui.settings

// [paste data class SettingsUiState from lines 233-249]
```

- [ ] **Step 2: Create ProviderState.kt**

Copy lines 251–256 of `SettingsViewModel.kt` verbatim:

```kotlin
package com.cocode.babakcast.ui.settings

// [paste data class ProviderState from lines 251-256]
```

- [ ] **Step 3: Remove inline types from SettingsViewModel.kt**

Delete lines 233–256 from `SettingsViewModel.kt`. Same-package resolution handles all references.

- [ ] **Step 4: Run tests**

```bash
./gradlew test
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cocode/babakcast/ui/settings/
git commit -m "refactor: extract inline types from SettingsViewModel into own files"
```

---

## Layer 3: UI Composables

Layer 3 is purely cosmetic — extracting private `@Composable` functions into focused files. There are no unit tests for UI composables; verify with a clean build.

Run after each task:
```bash
./gradlew assembleDebug
```

---

### Task 6: Extract composables from MainScreen

Split the 861-line `MainScreen.kt` into focused composable files. All new files go in `ui/main/` and are `internal` — not visible outside the package.

**Files:**
- Create: `app/src/main/java/com/cocode/babakcast/ui/main/UrlInputSection.kt`
- Create: `app/src/main/java/com/cocode/babakcast/ui/main/ActionButtonsSection.kt`
- Create: `app/src/main/java/com/cocode/babakcast/ui/main/SummarySection.kt`
- Create: `app/src/main/java/com/cocode/babakcast/ui/main/ErrorDialog.kt`
- Create: `app/src/main/java/com/cocode/babakcast/ui/main/SplitModeDialog.kt`
- Modify: `app/src/main/java/com/cocode/babakcast/ui/main/MainScreen.kt`

Strategy: identify the top-level private composable functions in `MainScreen.kt` by searching for `@Composable` + `private fun` or large blocks that correspond to each section. Move each block to its target file. `MainScreen` replaces the inline block with a call to the extracted function.

- [ ] **Step 1: Identify composable section boundaries in MainScreen.kt**

Read `MainScreen.kt` and identify:
- The URL input `OutlinedTextField` block → `UrlInputSection()`
- The action buttons `Column` block (Download Video, Download All Media, Copy Text, Share Text →, Download Audio, Summarize) → `ActionButtonsSection()`
- The summary display block → `SummarySection()`
- The error `AlertDialog` block → `ErrorDialog()`
- The split mode `AlertDialog` block → `SplitModeDialog()`

- [ ] **Step 2: Create UrlInputSection.kt**

```kotlin
package com.cocode.babakcast.ui.main

import androidx.compose.runtime.Composable
// (copy all imports needed by this composable from MainScreen.kt)

@Composable
internal fun UrlInputSection(
    url: String,
    isLoading: Boolean,
    onUrlChange: (String) -> Unit,
    onClear: () -> Unit
) {
    // Move the URL input Column block here verbatim
}
```

Replace the inline block in `MainScreen.kt` with `UrlInputSection(url = uiState.url, ...)`.

- [ ] **Step 3: Create ActionButtonsSection.kt**

```kotlin
package com.cocode.babakcast.ui.main

import androidx.compose.runtime.Composable
// (copy all imports needed)

@Composable
internal fun ActionButtonsSection(
    uiState: MainUiState,
    onDownloadVideo: () -> Unit,
    onDownloadAllMedia: () -> Unit,
    onCopyTweetText: () -> Unit,
    onShareTweetText: () -> Unit,
    onDownloadAudio: () -> Unit,
    onSummarize: () -> Unit
) {
    // Move the action buttons Column block here verbatim
}
```

Replace the inline block in `MainScreen.kt` with `ActionButtonsSection(uiState = uiState, ...)`.

- [ ] **Step 4: Create SummarySection.kt**

```kotlin
package com.cocode.babakcast.ui.main

import androidx.compose.runtime.Composable
// (copy imports)

@Composable
internal fun SummarySection(
    summary: String,
    summaryLength: String,
    onShareSummary: () -> Unit,
    onShareSummaryAsFile: () -> Unit
) {
    // Move summary display block here
}
```

- [ ] **Step 5: Create ErrorDialog.kt**

```kotlin
package com.cocode.babakcast.ui.main

import androidx.compose.runtime.Composable
// (copy imports)

@Composable
internal fun ErrorDialog(
    error: com.cocode.babakcast.util.AppError,
    onDismiss: () -> Unit
) {
    // Move AlertDialog block for error display here
}
```

- [ ] **Step 6: Create SplitModeDialog.kt**

```kotlin
package com.cocode.babakcast.ui.main

import androidx.compose.runtime.Composable
// (copy imports)

@Composable
internal fun SplitModeDialog(
    prompt: SplitChoicePrompt,
    onChoice: (com.cocode.babakcast.domain.split.SplitMode) -> Unit,
    onDismiss: () -> Unit
) {
    // Move split mode AlertDialog block here
}
```

- [ ] **Step 7: Build to verify**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL. Fix any missing imports.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/cocode/babakcast/ui/main/
git commit -m "refactor: extract composable sections from MainScreen"
```

---

### Task 7: Extract composables from SettingsScreen

**Files:**
- Create: `app/src/main/java/com/cocode/babakcast/ui/settings/ProviderCard.kt`
- Create: `app/src/main/java/com/cocode/babakcast/ui/settings/ModelSelectorSection.kt`
- Create: `app/src/main/java/com/cocode/babakcast/ui/settings/GeneralSettingsSection.kt`
- Modify: `app/src/main/java/com/cocode/babakcast/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Create ProviderCard.kt**

```kotlin
package com.cocode.babakcast.ui.settings

import androidx.compose.runtime.Composable
// (copy imports)

@Composable
internal fun ProviderCard(
    provider: ProviderState,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    // Move the per-provider card composable block here
}
```

- [ ] **Step 2: Create ModelSelectorSection.kt**

```kotlin
package com.cocode.babakcast.ui.settings

import androidx.compose.runtime.Composable
// (copy imports)

@Composable
internal fun ModelSelectorSection(
    uiState: SettingsUiState,
    onModelChange: (String) -> Unit,
    onToggleDropdown: () -> Unit,
    onSelectModel: (String) -> Unit
) {
    // Move model list + custom model input block here
}
```

- [ ] **Step 3: Create GeneralSettingsSection.kt**

```kotlin
package com.cocode.babakcast.ui.settings

import androidx.compose.runtime.Composable
// (copy imports)

@Composable
internal fun GeneralSettingsSection(
    uiState: SettingsUiState,
    onLanguageChange: (String) -> Unit,
    onSummaryLengthChange: (String) -> Unit,
    onAdaptiveLengthChange: (Boolean) -> Unit,
    onAutoPlayChange: (Boolean) -> Unit
) {
    // Move general settings rows (language, style, temperature, length) here
}
```

- [ ] **Step 4: Build to verify**

```bash
./gradlew assembleDebug
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cocode/babakcast/ui/settings/
git commit -m "refactor: extract composable sections from SettingsScreen"
```

---

### Task 8: Extract composables from DownloadsTab

**Files:**
- Create: `app/src/main/java/com/cocode/babakcast/ui/downloads/DownloadItemCard.kt`
- Create: `app/src/main/java/com/cocode/babakcast/ui/downloads/DownloadGroupHeader.kt`
- Modify: `app/src/main/java/com/cocode/babakcast/ui/downloads/DownloadsTab.kt`

- [ ] **Step 1: Create DownloadItemCard.kt**

```kotlin
package com.cocode.babakcast.ui.downloads

import androidx.compose.runtime.Composable
// (copy imports)

@Composable
internal fun DownloadItemCard(
    file: java.io.File,
    onShare: () -> Unit,
    onPlay: (() -> Unit)?,
    onDelete: () -> Unit
) {
    // Move the per-file row composable block here
}
```

- [ ] **Step 2: Create DownloadGroupHeader.kt**

```kotlin
package com.cocode.babakcast.ui.downloads

import androidx.compose.runtime.Composable
// (copy imports)

@Composable
internal fun DownloadGroupHeader(label: String) {
    // Move the date group header composable here
}
```

- [ ] **Step 3: Build to verify**

```bash
./gradlew assembleDebug
```

- [ ] **Step 4: Run all tests**

```bash
./gradlew test
```

Expected: all tests pass. (This is the Layer 3 gate.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cocode/babakcast/ui/downloads/
git commit -m "refactor: extract composable cards from DownloadsTab"
```

---

## Layer 4: Utilities

---

### Task 9: Extract AppError and TweetMedia to dedicated model files

Two independent extractions; do them in one task since both are small and follow the same pattern.

**Files:**
- Create: `app/src/main/java/com/cocode/babakcast/util/AppError.kt`
- Modify: `app/src/main/java/com/cocode/babakcast/util/ErrorHandler.kt`
- Create: `app/src/main/java/com/cocode/babakcast/data/model/TweetMedia.kt`
- Modify: `app/src/main/java/com/cocode/babakcast/data/remote/XSyndicationClient.kt`
- Modify (test imports): `app/src/test/java/com/cocode/babakcast/data/repository/MediaRepositoryXMediaTest.kt`
- Modify (test imports): `app/src/test/java/com/cocode/babakcast/data/repository/XMediaDownloadFlowTest.kt`

**AppError extraction:**

- [ ] **Step 1: Create util/AppError.kt**

Copy lines 10–98 of `ErrorHandler.kt` verbatim:

```kotlin
package com.cocode.babakcast.util

/**
 * Sealed hierarchy of user-facing errors shown in the UI.
 */
sealed class AppError(
    val title: String,
    val message: String,
    val fixHint: String? = null
) {
    // [paste all subclass definitions here verbatim from ErrorHandler.kt lines 15-97]
}
```

- [ ] **Step 2: Remove AppError from ErrorHandler.kt**

Delete lines 10–98 (the `sealed class AppError` block) from `ErrorHandler.kt`. The `object ErrorHandler` remains. Add `import com.cocode.babakcast.util.AppError` at the top of `ErrorHandler.kt` — wait, they're in the same package, so no import needed.

**TweetMedia extraction:**

- [ ] **Step 3: Create data/model/TweetMedia.kt**

Copy lines 137–146 of `XSyndicationClient.kt` verbatim:

```kotlin
package com.cocode.babakcast.data.model

/**
 * Models for X/Twitter tweet media returned by the syndication API.
 */
data class TweetMediaResult(
    // [paste verbatim from XSyndicationClient.kt line 137-140]
)

sealed class TweetMedia {
    // [paste verbatim from XSyndicationClient.kt lines 142-146]
}
```

- [ ] **Step 4: Update XSyndicationClient.kt**

1. Delete lines 137–146 from `XSyndicationClient.kt`
2. Add import: `import com.cocode.babakcast.data.model.TweetMedia` and `import com.cocode.babakcast.data.model.TweetMediaResult`

- [ ] **Step 5: Update test imports**

In `MediaRepositoryXMediaTest.kt`, change:
```kotlin
import com.cocode.babakcast.data.remote.TweetMedia
```
to:
```kotlin
import com.cocode.babakcast.data.model.TweetMedia
```

In `XMediaDownloadFlowTest.kt`, change:
```kotlin
import com.cocode.babakcast.data.remote.TweetMedia
import com.cocode.babakcast.data.remote.TweetMediaResult
```
to:
```kotlin
import com.cocode.babakcast.data.model.TweetMedia
import com.cocode.babakcast.data.model.TweetMediaResult
```

- [ ] **Step 6: Run tests**

```bash
./gradlew test
```

Expected: all tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/cocode/babakcast/util/AppError.kt \
        app/src/main/java/com/cocode/babakcast/util/ErrorHandler.kt \
        app/src/main/java/com/cocode/babakcast/data/model/TweetMedia.kt \
        app/src/main/java/com/cocode/babakcast/data/remote/XSyndicationClient.kt \
        app/src/test/
git commit -m "refactor: extract AppError and TweetMedia to dedicated model files"
```

---

### Task 10: Extract Platform enum before URL reorganization

`Platform` is defined inside `MediaUrlExtractor.kt`. It must be extracted to its own file before `MediaUrlExtractor` is moved to the `urlparsing/` subpackage, so existing callers keep their `com.cocode.babakcast.util.Platform` import path.

**Files:**
- Create: `app/src/main/java/com/cocode/babakcast/util/Platform.kt`
- Modify: `app/src/main/java/com/cocode/babakcast/util/MediaUrlExtractor.kt`

- [ ] **Step 1: Create util/Platform.kt**

Copy lines 3 and 5 of `MediaUrlExtractor.kt` verbatim (the `enum class Platform` and `data class ExtractedUrl`):

```kotlin
package com.cocode.babakcast.util

enum class Platform { YOUTUBE, X, INSTAGRAM, LINKEDIN }

data class ExtractedUrl(val url: String, val platform: Platform)
```

(Adjust to match exact definitions in the file.)

- [ ] **Step 2: Remove Platform and ExtractedUrl from MediaUrlExtractor.kt**

Delete lines 3 and 5 from `MediaUrlExtractor.kt`. Since both files are in the same `util` package, no import is needed in `MediaUrlExtractor.kt`.

- [ ] **Step 3: Run tests**

```bash
./gradlew test
```

Expected: all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/cocode/babakcast/util/
git commit -m "refactor: extract Platform enum to util/Platform.kt"
```

---

### Task 11: Move URL parsers to urlparsing/ subpackage

Move 9 URL-related files into a new `util/urlparsing/` subdirectory and update their package declarations and all callers.

**Files to move** (change package from `com.cocode.babakcast.util` to `com.cocode.babakcast.util.urlparsing`):
- `YouTubeUrlParser.kt`
- `YouTubeUrlExtractor.kt`
- `XUrlParser.kt`
- `XUrlExtractor.kt`
- `InstagramUrlParser.kt`
- `InstagramUrlExtractor.kt`
- `LinkedInUrlParser.kt`
- `LinkedInUrlExtractor.kt`
- `MediaUrlExtractor.kt`

**Production files requiring import updates:**

| File | Old imports | New imports |
|------|-------------|-------------|
| `data/repository/MediaRepository.kt` | `util.InstagramUrlExtractor`, `util.InstagramUrlParser`, `util.LinkedInUrlExtractor`, `util.LinkedInUrlParser`, `util.XUrlExtractor`, `util.XUrlParser`, `util.YouTubeUrlParser` | same but with `util.urlparsing.*` |
| `ui/main/MainViewModel.kt` | `util.InstagramUrlExtractor`, `util.LinkedInUrlExtractor`, `util.XUrlExtractor` | same but `util.urlparsing.*` |
| `ui/main/MainScreen.kt` | `util.XUrlExtractor` | `util.urlparsing.XUrlExtractor` |
| `MainActivity.kt` | `util.MediaUrlExtractor` | `util.urlparsing.MediaUrlExtractor` |

- [ ] **Step 1: Create the directory**

```bash
mkdir -p app/src/main/java/com/cocode/babakcast/util/urlparsing
```

- [ ] **Step 2: Move and reclassify each file**

For each of the 9 files, either move the file to the new directory in Android Studio (which updates package automatically) or manually:
1. Copy file to `util/urlparsing/`
2. Update the `package` declaration from `com.cocode.babakcast.util` to `com.cocode.babakcast.util.urlparsing`
3. Delete the original file from `util/`

**For `MediaUrlExtractor.kt` specifically**, also update internal imports:
```kotlin
import com.cocode.babakcast.util.urlparsing.YouTubeUrlExtractor
import com.cocode.babakcast.util.urlparsing.XUrlExtractor
import com.cocode.babakcast.util.urlparsing.InstagramUrlExtractor
import com.cocode.babakcast.util.urlparsing.LinkedInUrlExtractor
```
(Previously these had no explicit import since they were in the same package. Now they do.)

- [ ] **Step 3: Update production file imports**

Update `MediaRepository.kt`, `MainViewModel.kt`, `MainScreen.kt`, and `MainActivity.kt` to import from `util.urlparsing` instead of `util`.

Note: `Platform` stays in `util.Platform` (extracted in Task 10). Do not change those imports.

- [ ] **Step 4: Run tests**

```bash
./gradlew test
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cocode/babakcast/
git commit -m "refactor: move URL parsers/extractors to util/urlparsing/ subpackage"
```

---

### Task 12: Extract shared FFmpeg command logic

Extract the duplicated FFmpeg command-building logic from `AudioSplitter` and `VideoSplitter` into a shared `FfmpegCommands` object in the `domain/` package.

**Files:**
- Create: `app/src/main/java/com/cocode/babakcast/domain/FfmpegCommands.kt`
- Modify: `app/src/main/java/com/cocode/babakcast/domain/audio/AudioSplitter.kt`
- Modify: `app/src/main/java/com/cocode/babakcast/domain/video/VideoSplitter.kt`

- [ ] **Step 1: Read both splitter files to identify duplicated logic**

Read `AudioSplitter.kt` and `VideoSplitter.kt` in full. Look for FFmpeg command strings, argument lists, duration-parsing helpers, and any other logic that appears in both files nearly identically.

- [ ] **Step 2: Create domain/FfmpegCommands.kt**

```kotlin
package com.cocode.babakcast.domain

/**
 * Shared FFmpeg command-building utilities used by AudioSplitter and VideoSplitter.
 */
internal object FfmpegCommands {
    // Move the duplicated FFmpeg command builders and argument helpers here.
    // Each method should be a pure function: input params → FFmpeg argument array/string.
}
```

- [ ] **Step 3: Update AudioSplitter and VideoSplitter**

Replace duplicated blocks in each file with calls to `FfmpegCommands.xxx(...)`.

- [ ] **Step 4: Run tests**

```bash
./gradlew test
```

Expected: all tests pass. This is the final Layer 4 gate.

- [ ] **Step 5: Commit and push**

```bash
git add app/src/main/java/com/cocode/babakcast/domain/
git commit -m "refactor: extract shared FFmpeg command logic to FfmpegCommands"
git push origin HEAD
```

---

## Final Verification

- [ ] **Run full test suite one last time**

```bash
./gradlew test
```

Expected: all tests pass.

- [ ] **Check file sizes**

```bash
find app/src/main/java -name "*.kt" | xargs wc -l | sort -rn | head -20
```

No file should exceed ~400 lines. If any file is still large, review whether another extraction was missed.
