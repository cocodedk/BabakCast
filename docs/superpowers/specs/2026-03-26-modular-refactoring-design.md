# Modular Refactoring Design

> **For agentic workers:** This spec drives a layer-by-layer refactoring. Each layer is an independent implementation cycle. Start with Layer 1 (Repository), verify tests pass, then proceed to the next layer.

**Goal:** Split large multi-concern files into small, single-context files so each file is fully understandable without irrelevant noise.

**Constraint:** All existing tests must pass after every layer. No behavior changes. No public interface changes. Only mechanical splits — move code, update imports.

**Architecture:** Four independent layers tackled in order: Repository → ViewModel → UI → Utilities. Each layer produces a set of new focused files and shrinks the originals into thin orchestrators or scaffolds.

---

## Layer 1: Repository

### MediaRepository (534 lines → ~200 lines)

**Problem:** One file mixes yt-dlp integration, OkHttp X downloads, file management, progress parsing, and platform orchestration.

**Split:**

- **`data/repository/YoutubeDlWrapper.kt`** (~200 lines) — internal class. Everything that touches `YoutubeDLManager`: request building, progress-parsing regex, `getInfo`, `download`. `MediaRepository` instantiates and delegates to this.
- **`data/repository/XDirectDownloader.kt`** (~130 lines) — internal class. OkHttp-based X downloads: photo fetch, direct MP4 download. File-naming and URL-extraction helpers move here from the companion object (but the companion object forwarding functions stay in `MediaRepository` so existing test call sites are unaffected).
- **`data/repository/MediaRepository.kt`** (~200 lines) — public orchestrator. All public methods stay. Companion object stays with `guardTweetTextFetch`, `categorizeTweetMedia`, `videoFileName`, `extractDirectVideoUrl` (these may delegate internally but remain callable at existing call sites).

### ProviderRepository (384 lines → ~180 lines)

**Problem:** Provider state management mixed with four provider-specific model-listing API calls and their JSON parsing.

**Split:**

- **`data/repository/AiModelFetcher.kt`** (~150 lines) — internal class. The `fetchModels` logic: OpenAI, Anthropic, Gemini, OpenRouter API calls + model list JSON parsing.
- **`data/repository/ProviderRepository.kt`** (~180 lines) — StateFlow, provider loading, active provider/model state, persistence. Delegates model fetching to `AiModelFetcher`.

---

## Layer 2: ViewModel

### MainViewModel (742 lines → ~380 lines)

**Problem:** Four sealed/data classes defined inline bloat the file.

**Extract to `ui/main/`:**

- **`MainUiState.kt`** — `MainUiState` data class only.
- **`ShareRequest.kt`** — `ShareRequest` sealed class only.
- **`TweetTextEvent.kt`** — `TweetTextEvent` sealed class only.
- **`PendingSplitRequest.kt`** — `PendingSplitRequest` sealed class only.

`MainViewModel.kt` becomes the ViewModel class only (~380 lines).

### SettingsViewModel (256 lines → ~180 lines)

**Same pattern:** Extract any inline state/event classes to their own files in `ui/settings/`.

---

## Layer 3: UI

### MainScreen (861 lines → ~180 lines)

**Problem:** One file contains the scaffold, URL input, all action buttons, summary display, error dialog, and split mode dialog.

**Extract to `ui/main/` (all `internal`):**

- **`UrlInputSection.kt`** — URL text field + clear button composable.
- **`ActionButtonsSection.kt`** — all action buttons: Download Video, Download All Media, Copy Text, Share Text, Download Audio, Summarize.
- **`SummarySection.kt`** — summary display + length selector.
- **`ErrorDialog.kt`** — error alert dialog composable.
- **`SplitModeDialog.kt`** — split mode chooser dialog composable.

`MainScreen.kt` becomes the scaffold that composes these sections (~180 lines).

### SettingsScreen (799 lines → ~180 lines)

**Extract to `ui/settings/` (all `internal`):**

- **`ProviderCard.kt`** — per-provider API key + model config card.
- **`ModelSelectorSection.kt`** — model list + custom model input.
- **`GeneralSettingsSection.kt`** — language, style, temperature, length preferences.

`SettingsScreen.kt` becomes the scaffold (~180 lines).

### DownloadsTab (395 lines → ~180 lines)

**Extract to `ui/downloads/` (all `internal`):**

- **`DownloadItemCard.kt`** — single download file row (share + play + delete actions).
- **`DownloadGroupHeader.kt`** — date group header.

`DownloadsTab.kt` becomes the list scaffold (~180 lines).

---

## Layer 4: Utilities

### Model extraction

- **`data/model/TweetMedia.kt`** — move `TweetMedia` sealed class and `TweetMediaResult` out of `XSyndicationClient.kt`. Client stays focused on HTTP.
- **`util/AppError.kt`** — move `AppError` sealed class out of `ErrorHandler.kt`. `ErrorHandler` becomes pure mapping logic.

### FFmpeg deduplication

- **`domain/FfmpegCommands.kt`** — extract shared FFmpeg command-building logic duplicated between `AudioSplitter.kt` and `VideoSplitter.kt`. Both drop ~80 lines.

### URL parser reorganization

Move all URL-related files into a `util/urlparsing/` subpackage (no renames, just directory):

- `YouTubeUrlParser.kt`
- `YouTubeUrlExtractor.kt`
- `XUrlParser.kt`
- `XUrlExtractor.kt`
- `InstagramUrlParser.kt`
- `InstagramUrlExtractor.kt`
- `LinkedInUrlParser.kt`
- `LinkedInUrlExtractor.kt`
- `MediaUrlExtractor.kt`

All callers update their import path. No interface changes.

---

## File Size Targets

| File (before) | Lines before | Lines after |
|---|---|---|
| MediaRepository | 534 | ~200 |
| ProviderRepository | 384 | ~180 |
| MainViewModel | 742 | ~380 |
| MainScreen | 861 | ~180 |
| SettingsScreen | 799 | ~180 |
| DownloadsTab | 395 | ~180 |
| AudioSplitter | 316 | ~220 |
| VideoSplitter | 307 | ~210 |

New files are all under 200 lines.

---

## Testing

No test changes required for Layers 1, 2, 4. Companion object methods stay at their existing call sites; sealed class moves only require import updates in test files.

Layer 3 (UI composables) has no unit tests — extraction is purely cosmetic reorganization of private composable functions.

Run `./gradlew test` after each layer to verify.
