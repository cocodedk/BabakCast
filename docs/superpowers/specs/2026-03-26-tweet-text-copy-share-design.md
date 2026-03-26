# Tweet Text Copy & Share — Design Spec

**Date:** 2026-03-26
**Branch target:** `feature/tweet-text-copy-share`
**Status:** Approved

---

## Overview

Allow users to paste an X/Twitter URL and, with a single tap, copy the tweet's full text to the clipboard or open the Android share sheet with that text — without having to download any media.

---

## Context

The X syndication API already returns `TweetMediaResult.text` (the full tweet text) when BabakCast fetches tweet media. However, this text is never surfaced to the user independently. The existing `ShareHelper.buildShareTextChooser()` and the clipboard copy pattern (used for AI summaries in `MainScreen`) provide all the infrastructure needed.

---

## Architecture

### Data flow

```
User pastes X URL
      ↓
updateUrl() detects X platform → uiState.platform = X
      ↓
UI shows "Copy Text" and "Share Text" buttons
      ↓
User taps either button
      ↓
ViewModel.fetchAndCopyTweetText() / ViewModel.fetchAndShareTweetText()
      ↓
XSyndicationClient.fetchTweetMedia(tweetId)  [existing method]
      ↓
TweetMediaResult.text extracted
      ↓
Copy path: ClipboardManager.setPrimaryClip() + uiState.tweetText set
Share path: ShareHelper.buildShareTextChooser(text) launched
```

### UiState changes

Add two fields to `MainUiState`:

```kotlin
val tweetText: String? = null
val isFetchingTweetText: Boolean = false
```

`tweetText` is cleared when the URL changes. `isFetchingTweetText` drives a loading indicator on the buttons.

### ViewModel

Add to `MainViewModel`:

```kotlin
fun fetchAndCopyTweetText()
fun fetchAndShareTweetText()
```

Both share a private `suspend fun fetchTweetText(): Result<String>` that:
1. Reads `uiState.url`, returns early if not an X URL.
2. Extracts tweet ID via `XUrlParser.extractTweetId()`.
3. Calls `xSyndicationClient.fetchTweetMedia(tweetId)`.
4. Returns `Result.success(result.text)` or `Result.failure(...)`.

`fetchAndCopyTweetText` then copies via `ClipboardManager`; `fetchAndShareTweetText` launches `ShareHelper.buildShareTextChooser(text)`.

Both set `isFetchingTweetText = true` at the start and `false` on completion, and set `uiState.error` on failure.

### UI (MainScreen)

Two buttons shown only when `uiState.platform == Platform.X` and a valid URL is present:

- **"Copy Text"** — calls `viewModel.fetchAndCopyTweetText()`. Shows a snackbar "Tweet text copied" on success.
- **"Share Text"** — calls `viewModel.fetchAndShareTweetText()`. Opens Android share sheet.

Both buttons show a loading spinner while `isFetchingTweetText` is true. Positioned near the existing "Download All Media" button for X URLs.

`tweetText` is cleared in `updateUrl()` whenever the URL changes.

---

## Error handling

- Non-X URL: guard clause exits silently (buttons not shown anyway).
- Missing tweet ID: `uiState.error = AppError.InvalidUrl` (reuse existing type).
- Network / API failure: `uiState.error = AppError.NetworkError` (reuse existing type).

---

## Testing

All tests follow the existing TDD pattern (companion objects for pure functions, ViewModel tests via `TestCoroutineDispatcher`).

### Unit tests — ViewModel (`MainViewModelTweetTextTest.kt`)

| # | Scenario | Expected |
|---|----------|----------|
| 1 | `fetchAndCopyTweetText()` with valid X URL | `tweetText` set, `isFetchingTweetText = false` |
| 2 | `fetchAndCopyTweetText()` with non-X URL | returns early, no state change |
| 3 | `fetchAndCopyTweetText()` with unparseable tweet ID | sets `error`, `tweetText` null |
| 4 | `fetchAndCopyTweetText()` on API failure | sets `error`, `tweetText` null |
| 5 | `isFetchingTweetText` is true during fetch | loading flag set while coroutine running |
| 6 | `tweetText` cleared on `updateUrl()` call | `tweetText = null` after URL change |
| 7 | `fetchAndShareTweetText()` with valid X URL | `tweetText` set, share intent launched |

---

## Files affected

| File | Change |
|------|--------|
| `MainUiState` (in `MainViewModel.kt`) | Add `tweetText`, `isFetchingTweetText` |
| `MainViewModel.kt` | Add `fetchAndCopyTweetText()`, `fetchAndShareTweetText()`, private `fetchTweetText()` |
| `MainScreen.kt` | Add "Copy Text" and "Share Text" buttons for X URLs; snackbar on copy |
| `MainViewModelTweetTextTest.kt` (new) | 7 unit tests |

No new utility classes needed — all required infrastructure already exists.

---

## Out of scope

- Editing tweet text before sharing.
- Persisting tweet text across sessions.
- Auto-prefetching on URL paste.
- Support for platforms other than X/Twitter.
