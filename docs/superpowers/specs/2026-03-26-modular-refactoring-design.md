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
- **`data/repository/MediaRepository.kt`** (~200 lines) — public orchestrator. All public methods stay. Companion object stays with `guardTweetTextFetch`, `categorizeTweetMedia`, `videoFileName`, `extractDirectVideoUrl`, `buildInfoRequest`, `buildDownloadRequest` (these may delegate internally but remain callable at existing call sites).

### ProviderRepository (384 lines → ~180 lines)

**Problem:** Provider state management mixed with four provider-specific model-listing API calls and their JSON parsing.

**Split:**

- **`data/repository/AiModelFetcher.kt`** (~150 lines) — internal class. The `fetchModels` logic: OpenAI, Anthropic, Gemini, OpenRouter API calls + model list JSON parsing.
- **`data/repository/ProviderRepository.kt`** (~180 lines) — StateFlow, provider loading, active provider/model state, persistence. Delegates model fetching to `AiModelFetcher`.

---

## Layer 2: ViewModel

### MainViewModel (742 lines → ~350 lines)

**Problem:** Six inline types bloat the file.

**Extract to `ui/main/`:**

- **`MainUiState.kt`** — `MainUiState` data class only.
- **`ShareRequest.kt`** — `ShareRequest` sealed class only.
- **`TweetTextEvent.kt`** — `TweetTextEvent` sealed class only.
- **`PendingSplitRequest.kt`** — `PendingSplitRequest` sealed class only.
- **`SplitChoiceState.kt`** — `SplitChoicePrompt` data class and `SplitChoiceMediaType` enum. Both are referenced in `MainScreen.kt` (split mode dialog) and must move together.

`MainViewModel.kt` becomes the ViewModel class only (~350 lines).

### SettingsViewModel (256 lines → ~160 lines)

**Same pattern.** Extract two inline types to `ui/settings/`:

- **`SettingsUiState.kt`** — `SettingsUiState` data class only.
- **`ProviderState.kt`** — `ProviderState` data class only.

`SettingsViewModel.kt` becomes the ViewModel class only (~160 lines).

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

- **`data/model/TweetMedia.kt`** — move `TweetMedia` sealed class and `TweetMediaResult` out of `XSyndicationClient.kt`. Client stays focused on HTTP. **Test files that import `com.cocode.babakcast.data.remote.TweetMedia` must update their import to `com.cocode.babakcast.data.model.TweetMedia`:** `MediaRepositoryXMediaTest.kt` and `XMediaDownloadFlowTest.kt`.
- **`util/AppError.kt`** — move `AppError` sealed class out of `ErrorHandler.kt`. Same `util` package — no import changes needed anywhere. `ErrorHandler` becomes pure mapping logic.

### FFmpeg deduplication

- **`domain/FfmpegCommands.kt`** — extract shared FFmpeg command-building logic duplicated between `AudioSplitter.kt` and `VideoSplitter.kt`. Both drop ~80 lines.

### URL parser reorganization

Move URL parser and extractor files into a `util/urlparsing/` subpackage (no renames, just directory). This changes their package from `com.cocode.babakcast.util` to `com.cocode.babakcast.util.urlparsing`.

**Note:** `YouTubeMetadataParser.kt` is NOT moved — it is a yt-dlp JSON parser, not a URL parser, and belongs in `util/`.

Files to move:
- `YouTubeUrlParser.kt`
- `YouTubeUrlExtractor.kt`
- `XUrlParser.kt`
- `XUrlExtractor.kt`
- `InstagramUrlParser.kt`
- `InstagramUrlExtractor.kt`
- `LinkedInUrlParser.kt`
- `LinkedInUrlExtractor.kt`
- `MediaUrlExtractor.kt`

**Important — `Platform` enum:** `Platform` is defined inside `MediaUrlExtractor.kt` and is used by `MediaRepository`, `MainViewModel`, `MediaRepositoryXFullTextTest`, and `MediaRequestBuilderTest`. Before moving `MediaUrlExtractor.kt`, extract `Platform` (and `ExtractedUrl` if co-located) into a new **`util/Platform.kt`** file in the original `com.cocode.babakcast.util` package. This keeps all existing `Platform` import paths intact. `MediaUrlExtractor.kt` then imports `Platform` from `util.Platform` after the move.

**Important — `MediaUrlExtractor.kt` internal imports:** After the move, `MediaUrlExtractor` must update its imports of `YouTubeUrlExtractor`, `XUrlExtractor`, `InstagramUrlExtractor`, and `LinkedInUrlExtractor` to the new `urlparsing` package.

**Production files requiring import-path updates** (all URL extractor/parser imports change from `util.*` to `util.urlparsing.*`):

| File | Imports to update |
|------|-------------------|
| `data/repository/MediaRepository.kt` | `InstagramUrlExtractor`, `InstagramUrlParser`, `LinkedInUrlExtractor`, `LinkedInUrlParser`, `XUrlExtractor`, `XUrlParser`, `YouTubeUrlParser` |
| `ui/main/MainViewModel.kt` | `InstagramUrlExtractor`, `LinkedInUrlExtractor`, `XUrlExtractor` |
| `ui/main/MainScreen.kt` | `XUrlExtractor` |
| `MainActivity.kt` | `MediaUrlExtractor` |
| `data/repository/MediaUrlExtractor.kt` (internal) | `YouTubeUrlExtractor`, `XUrlExtractor`, `InstagramUrlExtractor`, `LinkedInUrlExtractor` |

No interface changes.

---

## File Size Targets

| File (before) | Lines before | Lines after |
|---|---|---|
| MediaRepository | 534 | ~200 |
| ProviderRepository | 384 | ~180 |
| MainViewModel | 742 | ~350 |
| SettingsViewModel | 256 | ~160 |
| MainScreen | 861 | ~180 |
| SettingsScreen | 799 | ~180 |
| DownloadsTab | 395 | ~180 |
| AudioSplitter | 316 | ~220 |
| VideoSplitter | 307 | ~210 |

New files are all under 200 lines.

---

## Testing

- **Layer 1:** No test changes. Companion object methods stay at existing call sites.
- **Layer 2:** No test changes. All extracted classes stay in the `ui/main/` or `ui/settings/` package, so same-package resolution means no import statements need adding or changing in any test file. (`MainViewModelTweetTextTest` is in `ui.main` and uses `MainUiState` without an explicit import — no change needed.)
- **Layer 3:** No unit tests for UI composables — purely cosmetic reorganization of private composable functions.
- **Layer 4:** Two test files require import updates for the `TweetMedia` move: `MediaRepositoryXMediaTest.kt` (update `data.remote.TweetMedia` → `data.model.TweetMedia`) and `XMediaDownloadFlowTest.kt` (update both `data.remote.TweetMedia` → `data.model.TweetMedia` and `data.remote.TweetMediaResult` → `data.model.TweetMediaResult`). All other Layer 4 moves are import-path-only changes in production files.

Run `./gradlew test` after each layer to verify.
