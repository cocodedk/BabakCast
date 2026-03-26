# Tweet Text Copy & Share — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users paste an X/Twitter URL and copy or share the tweet text with a single tap, without downloading any media.

**Architecture:** Add `fetchTweetText()` to `MediaRepository` (calls existing `XSyndicationClient.fetchTweetMedia()`), expose two public ViewModel actions (`fetchAndCopyTweetText`, `fetchAndShareTweetText`) that emit one-shot events via a new `SharedFlow<TweetTextEvent>`, and add two buttons to the X-specific section of `MainScreen` that react to those events.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, kotlinx-coroutines, JUnit 4 (pure function tests only — no coroutines-test dependency needed)

---

## File Map

| File | What changes |
|------|-------------|
| `app/src/main/java/com/cocode/babakcast/data/repository/MediaRepository.kt` | Add `guardTweetTextFetch()` companion + `fetchTweetText()` suspend fun |
| `app/src/main/java/com/cocode/babakcast/ui/main/MainViewModel.kt` | Add `TweetTextEvent`, `tweetTextEvents` SharedFlow, `fetchAndCopyTweetText()`, `fetchAndShareTweetText()`; update `updateUrl()` to clear `tweetText`; add `tweetText` + `isFetchingTweetText` to `MainUiState` |
| `app/src/main/java/com/cocode/babakcast/ui/main/MainScreen.kt` | Add `LaunchedEffect` for `tweetTextEvents`; add "Copy Text" + "Share Text" buttons inside existing `AnimatedVisibility(visible = isXUrl)` block |
| `app/src/test/java/com/cocode/babakcast/ui/main/MainViewModelTweetTextTest.kt` | **NEW** — 6 pure function tests |

---

## Task 1: Add `guardTweetTextFetch` companion function to `MediaRepository` (TDD)

**Files:**
- Modify: `app/src/main/java/com/cocode/babakcast/data/repository/MediaRepository.kt`
- Create: `app/src/test/java/com/cocode/babakcast/ui/main/MainViewModelTweetTextTest.kt`

The pure guard function extracts and validates the tweet ID from a URL. Writing tests first.

**Note on test scope:** The spec lists 8 test scenarios, but this plan covers only 6. The remaining 2 spec tests (ViewModel coroutine behaviour: loading flag, API failure state) require `kotlinx-coroutines-test` which is not in the project's test dependencies. Those scenarios are covered manually at runtime. The 6 tests here cover all pure/deterministic logic.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/cocode/babakcast/ui/main/MainViewModelTweetTextTest.kt`:

```kotlin
package com.cocode.babakcast.ui.main

import com.cocode.babakcast.data.repository.MediaRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class MainViewModelTweetTextTest {

    // --- guardTweetTextFetch ---

    @Test
    fun guardTweetTextFetch_validXUrl_returnsTweetId() {
        val result = MediaRepository.guardTweetTextFetch(
            "https://x.com/user/status/1234567890123456789"
        )
        assertEquals("1234567890123456789", result)
    }

    @Test
    fun guardTweetTextFetch_nonXUrl_returnsNull() {
        assertNull(MediaRepository.guardTweetTextFetch("https://youtube.com/watch?v=abc"))
    }

    @Test
    fun guardTweetTextFetch_xUrlWithoutStatusPath_returnsNull() {
        assertNull(MediaRepository.guardTweetTextFetch("https://x.com/home"))
    }

    @Test
    fun guardTweetTextFetch_blankUrl_returnsNull() {
        assertNull(MediaRepository.guardTweetTextFetch(""))
    }

    // --- MainUiState defaults ---

    @Test
    fun mainUiState_default_tweetTextIsNull() {
        assertNull(MainUiState().tweetText)
    }

    @Test
    fun mainUiState_default_isFetchingTweetTextIsFalse() {
        assertFalse(MainUiState().isFetchingTweetText)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail (compilation error)**

```bash
cd /home/cocodedk/0-projects/BabakCast
./gradlew testDebugUnitTest --tests "com.cocode.babakcast.ui.main.MainViewModelTweetTextTest" 2>&1 | tail -30
```

Expected: FAILED — `guardTweetTextFetch` does not exist yet, `tweetText`/`isFetchingTweetText` not in `MainUiState`.

- [ ] **Step 3: Add `guardTweetTextFetch` to `MediaRepository.Companion`**

In `MediaRepository.kt`, find the `companion object {` block (where `categorizeTweetMedia`, `guessImageExtension`, etc. live) and add:

```kotlin
fun guardTweetTextFetch(url: String): String? {
    if (!XUrlExtractor.isXUrl(url)) return null
    return XUrlParser.extractTweetId(url)
}
```

Ensure `XUrlParser` is imported (it already is in the file).

- [ ] **Step 4: Add `tweetText` and `isFetchingTweetText` to `MainUiState`**

In `MainViewModel.kt`, find the `data class MainUiState(` declaration (around line 654) and add two fields after `summaryLength`:

```kotlin
val tweetText: String? = null,
val isFetchingTweetText: Boolean = false
```

The full updated signature ends with:
```kotlin
val supportsSummarize: Boolean = true,
val summaryLength: SummaryLength = SummaryLength.MEDIUM,
val tweetText: String? = null,
val isFetchingTweetText: Boolean = false
)
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
./gradlew testDebugUnitTest --tests "com.cocode.babakcast.ui.main.MainViewModelTweetTextTest" 2>&1 | tail -20
```

Expected: 6 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/cocode/babakcast/data/repository/MediaRepository.kt \
        app/src/main/java/com/cocode/babakcast/ui/main/MainViewModel.kt \
        app/src/test/java/com/cocode/babakcast/ui/main/MainViewModelTweetTextTest.kt
git commit -m "$(cat <<'EOF'
feat: add guardTweetTextFetch companion + UiState fields for tweet text

RED → GREEN for 6 unit tests covering guard function and UiState defaults.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Add `fetchTweetText()` to `MediaRepository` and ViewModel actions

**Files:**
- Modify: `app/src/main/java/com/cocode/babakcast/data/repository/MediaRepository.kt`
- Modify: `app/src/main/java/com/cocode/babakcast/ui/main/MainViewModel.kt`

- [ ] **Step 1: Add `fetchTweetText()` to `MediaRepository`**

Add this suspend function to `MediaRepository` (alongside other public methods like `downloadVideo`, `downloadAllXMedia`):

```kotlin
suspend fun fetchTweetText(url: String): Result<String> {
    val tweetId = guardTweetTextFetch(url)
        ?: return Result.failure(Exception("No tweet ID found in URL"))
    return xSyndicationClient.fetchTweetMedia(tweetId).map { it.text }
}
```

Note: `xSyndicationClient` is already a field in `MediaRepository`. `guardTweetTextFetch` is the companion function just added.

- [ ] **Step 2: Add `TweetTextEvent` sealed class to `MainViewModel.kt`**

At the bottom of `MainViewModel.kt` (after the other sealed classes like `ShareRequest`, `PendingSplitRequest`), add:

```kotlin
sealed class TweetTextEvent {
    data class Copied(val text: String) : TweetTextEvent()
    data class Share(val text: String) : TweetTextEvent()
}
```

- [ ] **Step 3: Add `tweetTextEvents` SharedFlow to `MainViewModel`**

In the `MainViewModel` class body, after the existing `_shareRequests` / `shareRequests` declarations, add:

```kotlin
private val _tweetTextEvents = MutableSharedFlow<TweetTextEvent>(extraBufferCapacity = 1)
val tweetTextEvents: SharedFlow<TweetTextEvent> = _tweetTextEvents.asSharedFlow()
```

- [ ] **Step 4: Update `updateUrl()` to clear `tweetText`**

Find `updateUrl()` in `MainViewModel.kt` (around line 69). Change the `.copy(...)` call to also reset `tweetText`:

```kotlin
fun updateUrl(url: String) {
    _uiState.value = _uiState.value.copy(
        url = url,
        tweetText = null,
        supportsSummarize = !XUrlExtractor.isXUrl(url) &&
            !InstagramUrlExtractor.isInstagramUrl(url) &&
            !LinkedInUrlExtractor.isLinkedInUrl(url)
    )
}
```

- [ ] **Step 5: Add `fetchAndCopyTweetText()` to `MainViewModel`**

Add after `downloadAllXMedia()`:

```kotlin
fun fetchAndCopyTweetText() {
    viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isFetchingTweetText = true, error = null)
        mediaRepository.fetchTweetText(_uiState.value.url).fold(
            onSuccess = { text ->
                if (text.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isFetchingTweetText = false,
                        error = AppError.InvalidUrl("This tweet has no text to copy")
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isFetchingTweetText = false,
                        tweetText = text
                    )
                    _tweetTextEvents.emit(TweetTextEvent.Copied(text))
                }
            },
            onFailure = { error ->
                _uiState.value = _uiState.value.copy(
                    isFetchingTweetText = false,
                    error = ErrorHandler.handleException(error)
                )
            }
        )
    }
}
```

- [ ] **Step 6: Add `fetchAndShareTweetText()` to `MainViewModel`**

Add right after `fetchAndCopyTweetText()`:

```kotlin
fun fetchAndShareTweetText() {
    viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isFetchingTweetText = true, error = null)
        mediaRepository.fetchTweetText(_uiState.value.url).fold(
            onSuccess = { text ->
                if (text.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isFetchingTweetText = false,
                        error = AppError.InvalidUrl("This tweet has no text to share")
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isFetchingTweetText = false,
                        tweetText = text
                    )
                    _tweetTextEvents.emit(TweetTextEvent.Share(text))
                }
            },
            onFailure = { error ->
                _uiState.value = _uiState.value.copy(
                    isFetchingTweetText = false,
                    error = ErrorHandler.handleException(error)
                )
            }
        )
    }
}
```

- [ ] **Step 7: Run all tests to verify nothing is broken**

```bash
./gradlew testDebugUnitTest 2>&1 | tail -20
```

Expected: all existing tests PASS, plus the 6 new tests.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/cocode/babakcast/data/repository/MediaRepository.kt \
        app/src/main/java/com/cocode/babakcast/ui/main/MainViewModel.kt
git commit -m "$(cat <<'EOF'
feat: add fetchTweetText, TweetTextEvent, and ViewModel actions for tweet text copy/share

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Add UI buttons to `MainScreen`

**Files:**
- Modify: `app/src/main/java/com/cocode/babakcast/ui/main/MainScreen.kt`

- [ ] **Step 1: Add `LaunchedEffect` for `tweetTextEvents`**

In `MainScreen`, after the existing `LaunchedEffect` blocks (there is one for audio share requests), add a new one to handle tweet text events. Find the import block and ensure `TweetTextEvent` is accessible (it's in the same package).

Find the `LaunchedEffect` that handles `shareRequests` (search for `viewModel.shareRequests`). After that block, add:

```kotlin
LaunchedEffect(Unit) {
    viewModel.tweetTextEvents.collect { event ->
        when (event) {
            is TweetTextEvent.Copied -> {
                // setPlainText is a private suspend extension on Clipboard defined at the
                // bottom of MainScreen.kt — call it with a plain String, not a ClipEntry
                clipboardManager.setPlainText(event.text)
                snackbarHostState.showSnackbar(
                    message = "Tweet text copied",
                    duration = SnackbarDuration.Short
                )
            }
            is TweetTextEvent.Share -> {
                // Use the existing textShareLauncher — same pattern as the audio share path.
                // Do NOT use context.startActivity(); do NOT add FLAG_ACTIVITY_NEW_TASK.
                val intent = shareHelper.buildShareTextChooser(event.text, "Share Tweet Text")
                textShareLauncher.launch(intent)
            }
        }
    }
}
```

Note: `clipboardManager` is `LocalClipboard.current` (already declared at line 57 as `val clipboardManager = LocalClipboard.current`). `setPlainText` is a private `suspend` extension function on `Clipboard` defined at the bottom of `MainScreen.kt` — it takes a plain `String`. Do NOT call `setPlainText(ClipEntry(...))`. The `LaunchedEffect` coroutine already provides the right suspend context, so no extra `scope.launch` is needed for the copy call. `ClipData` and `ClipEntry` imports are already present in the file; no new imports needed for the copy path.

- [ ] **Step 2: Add "Copy Text" and "Share Text" buttons**

Find the existing `AnimatedVisibility(visible = isXUrl, ...)` block in `MainScreen` (around line 316-350). It currently contains only the "Download All Media" `OutlinedButton`. Add two new buttons **inside the same `AnimatedVisibility` block**, after the "Download All Media" button.

Replace the closing of that `AnimatedVisibility` with a `Column` containing all three X-specific buttons:

```kotlin
// Download All Media Button - X/Twitter only
val isXUrl = XUrlExtractor.isXUrl(uiState.url)
AnimatedVisibility(
    visible = isXUrl,
    enter = fadeIn(),
    exit = fadeOut()
) {
    val isXActionEnabled = !uiState.isLoading && uiState.url.isNotBlank()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val isAllMediaEnabled = uiState.downloadEngineReady && isXActionEnabled
        OutlinedButton(
            onClick = viewModel::downloadAllXMedia,
            enabled = isAllMediaEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = BabakCastColors.PrimaryAccent,
                disabledContentColor = BabakCastColors.PrimaryAccent.copy(alpha = 0.3f)
            ),
            border = ButtonDefaults.outlinedButtonBorder(enabled = isAllMediaEnabled).copy(
                brush = androidx.compose.ui.graphics.SolidColor(
                    if (isAllMediaEnabled)
                        BabakCastColors.PrimaryAccent.copy(alpha = 0.5f)
                    else
                        BabakCastColors.PrimaryAccent.copy(alpha = 0.2f)
                )
            )
        ) {
            Text(
                "Download All Media",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            )
        }

        // Copy Tweet Text / Share Tweet Text — row of two text buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = viewModel::fetchAndCopyTweetText,
                enabled = isXActionEnabled && !uiState.isFetchingTweetText,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            ) {
                if (uiState.isFetchingTweetText) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = BabakCastColors.PrimaryAccent
                    )
                } else {
                    Text(
                        "Copy Text",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    )
                }
            }

            OutlinedButton(
                onClick = viewModel::fetchAndShareTweetText,
                enabled = isXActionEnabled && !uiState.isFetchingTweetText,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = BabakCastColors.SecondaryAccent,
                    disabledContentColor = BabakCastColors.SecondaryAccent.copy(alpha = 0.3f)
                )
            ) {
                Text(
                    "Share Text →",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                )
            }
        }
    }
}
```

**Important:** This replaces the existing `AnimatedVisibility` block (lines ~316-350). The old block had one `OutlinedButton` directly as a child. The new block wraps everything in a `Column`.

**CRITICAL — do NOT re-declare `val isXUrl`:** Line 315 in the original file reads `val isXUrl = XUrlExtractor.isXUrl(uiState.url)` and lives *outside* the `AnimatedVisibility` block. The replacement snippet above starts with `val isXUrl = ...` for readability context only. When making the edit, start the replacement *at* the `AnimatedVisibility(` line, not at the `val isXUrl` line. Duplicating `val isXUrl` in the same scope will cause a compile error.

- [ ] **Step 3: Run all tests**

```bash
./gradlew testDebugUnitTest 2>&1 | tail -20
```

Expected: all tests PASS (UI changes don't affect unit tests).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/cocode/babakcast/ui/main/MainScreen.kt
git commit -m "$(cat <<'EOF'
feat: add Copy Text and Share Text buttons for X/Twitter URLs in MainScreen

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Final verification, push, and PR

- [ ] **Step 1: Confirm branch**

```bash
git branch --show-current
```

Expected: `feature/tweet-text-copy-share` (create it if not already on it: `git checkout -b feature/tweet-text-copy-share`)

- [ ] **Step 2: Run full test suite**

```bash
./gradlew testDebugUnitTest 2>&1 | tail -30
```

Expected: all tests PASS.

- [ ] **Step 3: Push**

```bash
# Switch to cocodedk GitHub account if needed
gh auth status
# If not cocodedk: gh auth switch --user cocodedk
git push -u origin feature/tweet-text-copy-share
```

- [ ] **Step 4: Create PR**

```bash
gh pr create \
  --title "Add tweet text copy & share for X/Twitter URLs" \
  --body "$(cat <<'EOF'
## Summary
- Adds \"Copy Text\" and \"Share Text\" buttons that appear for X/Twitter URLs
- Fetches tweet text from the syndication API on demand (no download required)
- Copy path: text goes to clipboard with a snackbar confirmation
- Share path: opens Android share sheet with tweet text
- Blank/media-only tweets show an error instead of copying empty string

## Test plan
- [ ] 6 new unit tests pass (guard function + UiState defaults; ViewModel coroutine scenarios covered manually below)
- [ ] Paste an X URL → two new buttons appear
- [ ] Tap \"Copy Text\" → loading spinner → clipboard populated → snackbar \"Tweet text copied\"
- [ ] Tap \"Share Text\" → loading spinner → Android share sheet opens with tweet text
- [ ] Paste non-X URL → buttons disappear
- [ ] Paste a media-only tweet → error shown (no blank copy)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```
