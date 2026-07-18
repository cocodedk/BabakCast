# Share Translation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Opt-in AI translation to Persian of all shared text (tweet text, media captions, summaries), sharing original + translation together.

**Architecture:** A new `data/ai/ShareTranslator` orchestrator wraps the existing (currently caller-less) `AIRepository.translate()`, returning a sealed `ShareTranslationResult` so every branch ends in a share. Provider resolution is extracted from `MainViewModel.generateSummary()` into a shared `ProviderResolver`. A transient toggle in `MainUiState` gates the behavior and auto-resets after each share.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, kotlinx-coroutines, JUnit4 (no mocking library — testable logic goes in companion-object functions that take lambdas, per this repo's convention).

**Spec:** `docs/superpowers/specs/2026-07-18-share-translation-design.md`

## Global Constraints

- 200-line maximum per code file.
- Conventional Commits; the pre-commit hook runs `buildSmoke` on every commit (slow but mandatory — never `--no-verify`).
- Tests call production code directly (companion objects / injected lambdas); never duplicate production logic inside tests.
- UI strings in `ActionButtonsSection` are hardcoded inline `Text("...")` — follow that existing convention for the toggle label.
- Target language is hardcoded `"Persian"`; temperature `0.2`; translation timeout `15_000` ms.
- All work on branch `feat/share-translation` (already exists, spec committed on it).
- Every `ShareTranslationResult` branch MUST end in a share — translation failure never blocks sharing.
- `DownloadsViewModel.shareText(item.displayName, ...)` is explicitly OUT of scope (shares a filename, not prose).
- `ShareHelper.shareVideos()` carries no text — out of scope.

---

### Task 1: `TranslatedShareText` pure utility

**Files:**
- Create: `app/src/main/java/com/cocode/babakcast/util/TranslatedShareText.kt`
- Test: `app/src/test/java/com/cocode/babakcast/util/TranslatedShareTextTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `TranslatedShareText.combine(original: String, translated: String): String` — used by Task 3.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.cocode.babakcast.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslatedShareTextTest {

    @Test
    fun combinePutsOriginalThenSeparatorThenRtlMarkThenTranslation() {
        val result = TranslatedShareText.combine("Hello world", "سلام دنیا")
        assertEquals("Hello world\n\n———\n\n‏سلام دنیا", result)
    }

    @Test
    fun combineKeepsMultilineOriginalIntact() {
        val original = "line one\nline two"
        val result = TranslatedShareText.combine(original, "ترجمه")
        assertTrue(result.startsWith("line one\nline two\n\n———\n\n"))
    }

    @Test
    fun combineInsertsRtlMarkExactlyOnceBeforeTranslation() {
        val result = TranslatedShareText.combine("a", "ب")
        assertEquals(1, result.count { it == '‏' })
        assertEquals("‏ب", result.substringAfterLast("\n"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.cocode.babakcast.util.TranslatedShareTextTest" --no-daemon`
Expected: FAIL — unresolved reference `TranslatedShareText`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.cocode.babakcast.util

/**
 * Builds the combined original + Persian translation text for sharing.
 * The U+200F (right-to-left mark) makes bidi renderers (e.g. WhatsApp)
 * lay out the Persian block correctly after the LTR original.
 */
object TranslatedShareText {
    private const val SEPARATOR = "\n\n———\n\n"
    private const val RTL_MARK = "‏"

    fun combine(original: String, translated: String): String =
        original + SEPARATOR + RTL_MARK + translated
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.cocode.babakcast.util.TranslatedShareTextTest" --no-daemon`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cocode/babakcast/util/TranslatedShareText.kt app/src/test/java/com/cocode/babakcast/util/TranslatedShareTextTest.kt
git commit -m "feat(share): add TranslatedShareText combine utility"
```

---

### Task 2: `ProviderResolver`

**Files:**
- Create: `app/src/main/java/com/cocode/babakcast/data/ai/ProviderResolver.kt` (new package `data/ai/`)
- Test: `app/src/test/java/com/cocode/babakcast/data/ai/ProviderResolverTest.kt`

**Interfaces:**
- Consumes: `SettingsRepository.settings: Flow<AppSettings>` (field `defaultProviderId: String?`), `ProviderRepository.hasApiKey(String): Boolean`, `ProviderRepository.providers: StateFlow<List<Provider>>`, `ProviderRepository.getProviderWithSelectedModel(String): Provider?` — all existing.
- Produces:
  - `class ProviderResolver @Inject constructor(...)` with `suspend fun resolve(): Provider?` — used by Tasks 3 and 4.
  - `ProviderResolver.pickProviderId(defaultProviderId: String?, hasApiKey: (String) -> Boolean, allProviderIds: List<String>): String?` (companion) — the pure decision function.

- [ ] **Step 1: Write the failing test** (tests target the pure companion function — no mocking library exists in this repo)

```kotlin
package com.cocode.babakcast.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderResolverTest {

    @Test
    fun picksDefaultProviderWhenItHasAnApiKey() {
        val picked = ProviderResolver.pickProviderId(
            defaultProviderId = "openrouter",
            hasApiKey = { it == "openrouter" },
            allProviderIds = listOf("openai", "openrouter")
        )
        assertEquals("openrouter", picked)
    }

    @Test
    fun fallsBackToFirstProviderWithKeyWhenDefaultHasNoKey() {
        val picked = ProviderResolver.pickProviderId(
            defaultProviderId = "openai",
            hasApiKey = { it == "anthropic" },
            allProviderIds = listOf("openai", "anthropic", "openrouter")
        )
        assertEquals("anthropic", picked)
    }

    @Test
    fun fallsBackToFirstProviderWithKeyWhenNoDefaultIsSet() {
        val picked = ProviderResolver.pickProviderId(
            defaultProviderId = null,
            hasApiKey = { it == "gemini" },
            allProviderIds = listOf("openai", "gemini")
        )
        assertEquals("gemini", picked)
    }

    @Test
    fun returnsNullWhenNoProviderHasAnApiKey() {
        val picked = ProviderResolver.pickProviderId(
            defaultProviderId = "openai",
            hasApiKey = { false },
            allProviderIds = listOf("openai", "anthropic")
        )
        assertNull(picked)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.cocode.babakcast.data.ai.ProviderResolverTest" --no-daemon`
Expected: FAIL — unresolved reference `ProviderResolver`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.cocode.babakcast.data.ai

import com.cocode.babakcast.data.local.SettingsRepository
import com.cocode.babakcast.data.model.Provider
import com.cocode.babakcast.data.repository.ProviderRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves which AI provider to use: the settings default if it has an API
 * key, otherwise the first configured provider with a key. Shared by the
 * summary and share-translation flows.
 */
@Singleton
class ProviderResolver @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val providerRepository: ProviderRepository
) {
    suspend fun resolve(): Provider? {
        val defaultProviderId = settingsRepository.settings.first().defaultProviderId
        val providerId = pickProviderId(
            defaultProviderId = defaultProviderId,
            hasApiKey = providerRepository::hasApiKey,
            allProviderIds = providerRepository.providers.value.map { it.id }
        ) ?: return null
        return providerRepository.getProviderWithSelectedModel(providerId)
    }

    companion object {
        fun pickProviderId(
            defaultProviderId: String?,
            hasApiKey: (String) -> Boolean,
            allProviderIds: List<String>
        ): String? = when {
            defaultProviderId != null && hasApiKey(defaultProviderId) -> defaultProviderId
            else -> allProviderIds.firstOrNull(hasApiKey)
        }
    }
}
```

Note: if `AppSettings`/`Provider` import paths differ from the above (check `SettingsRepository.kt` around line 39 for where `AppSettings` actually lives), fix imports — do not change the logic.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.cocode.babakcast.data.ai.ProviderResolverTest" --no-daemon`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cocode/babakcast/data/ai/ProviderResolver.kt app/src/test/java/com/cocode/babakcast/data/ai/ProviderResolverTest.kt
git commit -m "feat(ai): add ProviderResolver for shared provider selection"
```

---

### Task 3: `ShareTranslationResult` + `ShareTranslator`

**Files:**
- Create: `app/src/main/java/com/cocode/babakcast/data/ai/ShareTranslationResult.kt`
- Create: `app/src/main/java/com/cocode/babakcast/data/ai/ShareTranslator.kt`
- Test: `app/src/test/java/com/cocode/babakcast/data/ai/ShareTranslatorTest.kt`

**Interfaces:**
- Consumes: `ProviderResolver.resolve(): Provider?` (Task 2), `TranslatedShareText.combine(String, String): String` (Task 1), existing `AIRepository.translate(text: String, providerId: String, targetLanguage: String, temperature: Double): Result<String>`.
- Produces:
  - `sealed class ShareTranslationResult` with `Translated(combinedText: String)`, `Skipped`, `Failed(originalText: String)` — used by Tasks 5–7.
  - `class ShareTranslator @Inject constructor(...)` with `suspend fun translateIfEnabled(text: String, enabled: Boolean): ShareTranslationResult`.
  - `ShareTranslator.run(text, enabled, resolveProviderId: suspend () -> String?, translate: suspend (String) -> Result<String>, timeoutMs: Long): ShareTranslationResult` (companion) — the testable core.

- [ ] **Step 1: Write `ShareTranslationResult`**

```kotlin
package com.cocode.babakcast.data.ai

/** Outcome of an optional pre-share translation. Every branch carries shareable text. */
sealed class ShareTranslationResult {
    data class Translated(val combinedText: String) : ShareTranslationResult()
    object Skipped : ShareTranslationResult()
    data class Failed(val originalText: String) : ShareTranslationResult()
}
```

- [ ] **Step 2: Write the failing tests** (plain `runBlocking` + lambdas; timeout case uses a small real timeout against a longer `delay`)

```kotlin
package com.cocode.babakcast.data.ai

import com.cocode.babakcast.util.TranslatedShareText
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareTranslatorTest {

    @Test
    fun disabledToggleSkipsWithoutResolvingOrTranslating() = runBlocking {
        var resolved = false
        val result = ShareTranslator.run(
            text = "hello",
            enabled = false,
            resolveProviderId = { resolved = true; "p1" },
            translate = { Result.success("سلام") },
            timeoutMs = 1_000
        )
        assertEquals(ShareTranslationResult.Skipped, result)
        assertTrue(!resolved)
    }

    @Test
    fun noConfiguredProviderFailsWithOriginalText() = runBlocking {
        val result = ShareTranslator.run(
            text = "hello",
            enabled = true,
            resolveProviderId = { null },
            translate = { Result.success("unused") },
            timeoutMs = 1_000
        )
        assertEquals(ShareTranslationResult.Failed("hello"), result)
    }

    @Test
    fun successReturnsCombinedText() = runBlocking {
        val result = ShareTranslator.run(
            text = "hello",
            enabled = true,
            resolveProviderId = { "p1" },
            translate = { Result.success("سلام") },
            timeoutMs = 1_000
        )
        assertEquals(
            ShareTranslationResult.Translated(TranslatedShareText.combine("hello", "سلام")),
            result
        )
    }

    @Test
    fun aiErrorFailsWithOriginalText() = runBlocking {
        val result = ShareTranslator.run(
            text = "hello",
            enabled = true,
            resolveProviderId = { "p1" },
            translate = { Result.failure(RuntimeException("boom")) },
            timeoutMs = 1_000
        )
        assertEquals(ShareTranslationResult.Failed("hello"), result)
    }

    @Test
    fun timeoutFailsWithOriginalText() = runBlocking {
        val result = ShareTranslator.run(
            text = "hello",
            enabled = true,
            resolveProviderId = { "p1" },
            translate = { delay(500); Result.success("late") },
            timeoutMs = 50
        )
        assertEquals(ShareTranslationResult.Failed("hello"), result)
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.cocode.babakcast.data.ai.ShareTranslatorTest" --no-daemon`
Expected: FAIL — unresolved reference `ShareTranslator`.

- [ ] **Step 4: Write the implementation**

```kotlin
package com.cocode.babakcast.data.ai

import com.cocode.babakcast.data.repository.AIRepository
import com.cocode.babakcast.util.TranslatedShareText
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the optional pre-share AI translation to Persian.
 * Never throws toward the caller: every outcome is a [ShareTranslationResult]
 * carrying shareable text, so a failed translation can never block a share.
 */
@Singleton
class ShareTranslator @Inject constructor(
    private val aiRepository: AIRepository,
    private val providerResolver: ProviderResolver
) {
    suspend fun translateIfEnabled(text: String, enabled: Boolean): ShareTranslationResult =
        run(
            text = text,
            enabled = enabled,
            resolveProviderId = { providerResolver.resolve()?.id },
            translate = { providerId ->
                aiRepository.translate(text, providerId, TARGET_LANGUAGE, TEMPERATURE)
            },
            timeoutMs = TIMEOUT_MS
        )

    companion object {
        const val TARGET_LANGUAGE = "Persian"
        const val TEMPERATURE = 0.2
        const val TIMEOUT_MS = 15_000L

        suspend fun run(
            text: String,
            enabled: Boolean,
            resolveProviderId: suspend () -> String?,
            translate: suspend (String) -> Result<String>,
            timeoutMs: Long
        ): ShareTranslationResult {
            if (!enabled) return ShareTranslationResult.Skipped
            val providerId = resolveProviderId() ?: return ShareTranslationResult.Failed(text)
            val translated = withTimeoutOrNull(timeoutMs) {
                translate(providerId).getOrNull()
            }
            return if (translated != null) {
                ShareTranslationResult.Translated(TranslatedShareText.combine(text, translated))
            } else {
                ShareTranslationResult.Failed(text)
            }
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.cocode.babakcast.data.ai.ShareTranslatorTest" --no-daemon`
Expected: PASS (5 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/cocode/babakcast/data/ai/ShareTranslationResult.kt app/src/main/java/com/cocode/babakcast/data/ai/ShareTranslator.kt app/src/test/java/com/cocode/babakcast/data/ai/ShareTranslatorTest.kt
git commit -m "feat(ai): add ShareTranslator orchestrator with sealed result"
```

---

### Task 4: Rewire `generateSummary()` to `ProviderResolver`

**Files:**
- Modify: `app/src/main/java/com/cocode/babakcast/ui/main/MainViewModel.kt` (~lines 468–490 and the constructor)

**Interfaces:**
- Consumes: `ProviderResolver.resolve(): Provider?` (Task 2).
- Produces: no new API — pure refactor; existing summary behavior must be unchanged.

- [ ] **Step 1: Add `providerResolver` to the constructor**

Add `import com.cocode.babakcast.data.ai.ProviderResolver` and a constructor parameter `private val providerResolver: ProviderResolver,` next to the existing `aiRepository`/`providerRepository` params (Hilt constructor injection — no module change needed).

- [ ] **Step 2: Replace the inline resolution block**

Replace this block inside `generateSummary()`'s `onSuccess` (currently ~lines 468–490):

```kotlin
val defaultProviderId = settingsRepository.settings.first().defaultProviderId
val providerId = when {
    defaultProviderId != null && providerRepository.hasApiKey(defaultProviderId) ->
        defaultProviderId
    else ->
        providerRepository.providers.value.firstOrNull {
            providerRepository.hasApiKey(it.id)
        }?.id
}

val defaultProvider = providerId?.let {
    providerRepository.getProviderWithSelectedModel(it)
} ?: run {
```

with:

```kotlin
val defaultProvider = providerResolver.resolve() ?: run {
```

(keep the existing `run { ... return@launch }` error block exactly as is — it already sets `AppError.ProviderMisconfigured("No AI provider configured")`).

- [ ] **Step 3: Run the full unit test suite (regression gate)**

Run: `./gradlew testDebugUnitTest --no-daemon`
Expected: PASS — same test count as before this task, 0 failures.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/cocode/babakcast/ui/main/MainViewModel.kt
git commit -m "refactor(summary): use ProviderResolver for provider selection"
```

---

### Task 5: Toggle state + UI

**Files:**
- Modify: `app/src/main/java/com/cocode/babakcast/ui/main/MainUiState.kt`
- Modify: `app/src/main/java/com/cocode/babakcast/ui/main/MainViewModel.kt`
- Modify: `app/src/main/java/com/cocode/babakcast/ui/main/ActionButtonsSection.kt`
- Modify: `app/src/main/java/com/cocode/babakcast/ui/main/MainScreen.kt` (~line 279 `ActionButtonsSection(` call)
- Test: `app/src/test/java/com/cocode/babakcast/ui/main/MainUiStateTranslateTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces:
  - `MainUiState.translateBeforeShare: Boolean = false`, `MainUiState.isTranslatingForShare: Boolean = false` — used by Tasks 6–7.
  - `MainViewModel.setTranslateBeforeShare(enabled: Boolean)` — wired from UI.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.cocode.babakcast.ui.main

import org.junit.Assert.assertFalse
import org.junit.Test

class MainUiStateTranslateTest {

    @Test
    fun translateBeforeShareDefaultsToOff() {
        assertFalse(MainUiState().translateBeforeShare)
    }

    @Test
    fun isTranslatingForShareDefaultsToOff() {
        assertFalse(MainUiState().isTranslatingForShare)
    }
}
```

Run: `./gradlew testDebugUnitTest --tests "com.cocode.babakcast.ui.main.MainUiStateTranslateTest" --no-daemon`
Expected: FAIL — unresolved references.

- [ ] **Step 2: Add the two fields to `MainUiState`**

After `val isFetchingTweetText: Boolean = false,` add:

```kotlin
    val translateBeforeShare: Boolean = false,
    val isTranslatingForShare: Boolean = false,
```

Run the test again — expected: PASS.

- [ ] **Step 3: Add the setter to `MainViewModel`**

Next to the other small `updateX` functions (e.g. near `clearError()`):

```kotlin
    fun setTranslateBeforeShare(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(translateBeforeShare = enabled)
    }
```

- [ ] **Step 4: Add the toggle row to `ActionButtonsSection`**

Add parameter `onTranslateToggle: (Boolean) -> Unit,` after `onSummaryLengthChange: (SummaryLength) -> Unit`. Add imports: `androidx.compose.material3.Switch`, `androidx.compose.foundation.layout.Spacer`, `androidx.compose.foundation.layout.weight` as needed. Insert this Row as the FIRST child of the section's `Column` (above the "Download Video" button):

```kotlin
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Translate to Persian",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    if (uiState.isTranslatingForShare) "Translating…" else "Adds a Persian translation to the next share",
                    style = MaterialTheme.typography.bodySmall,
                    color = BabakCastColors.PrimaryAccent.copy(alpha = 0.7f)
                )
            }
            Switch(
                checked = uiState.translateBeforeShare,
                onCheckedChange = onTranslateToggle,
                enabled = !uiState.isTranslatingForShare
            )
        }
```

Then gate the shared `downloadEnabled` val on the in-flight flag:

```kotlin
val downloadEnabled = uiState.downloadEngineReady && !uiState.isLoading &&
    uiState.url.isNotBlank() && !uiState.isTranslatingForShare
```

Also add `&& !uiState.isTranslatingForShare` to the `enabled` expression of every button in this file that is NOT already using `downloadEnabled` (the tweet copy/share and summary share buttons further down the file — find each `enabled =` and extend it). This prevents concurrent share taps while a translation is in flight.

**File-size check:** `ActionButtonsSection.kt` is 329 lines and already over the 200-line max. Extract the new Row into its own file `app/src/main/java/com/cocode/babakcast/ui/main/TranslateToggleRow.kt` as `@Composable internal fun TranslateToggleRow(uiState: MainUiState, onToggle: (Boolean) -> Unit)` containing the Row above, and call `TranslateToggleRow(uiState, onTranslateToggle)` from `ActionButtonsSection` — do not grow the oversized file further.

- [ ] **Step 5: Wire `MainScreen`**

In the `ActionButtonsSection(` call (~line 279) add:

```kotlin
                    onTranslateToggle = viewModel::setTranslateBeforeShare,
```

- [ ] **Step 6: Build + full test run**

Run: `./gradlew buildSmoke --no-daemon`
Expected: BUILD SUCCESSFUL, 0 test failures, lint clean.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/cocode/babakcast/ui/main/ app/src/test/java/com/cocode/babakcast/ui/main/MainUiStateTranslateTest.kt
git commit -m "feat(share): add translate-to-Persian toggle state and UI"
```

---

### Task 6: Wire tweet-text and media-caption share paths

**Files:**
- Modify: `app/src/main/java/com/cocode/babakcast/ui/main/MainViewModel.kt` (`fetchTweetTextAndEmit` ~line 230, `downloadAllXMedia` success branch ~line 215, `shareAudioFiles` ~line 724)

**Interfaces:**
- Consumes: `ShareTranslator.translateIfEnabled(text, enabled): ShareTranslationResult` (Task 3), `MainUiState.translateBeforeShare` / `isTranslatingForShare` (Task 5).
- Produces: `private suspend fun MainViewModel.textForShare(text: String, enabled: Boolean): String` — reused in Task 7.

- [ ] **Step 1: Add `shareTranslator` to the constructor**

Constructor parameter `private val shareTranslator: ShareTranslator,` + imports `com.cocode.babakcast.data.ai.ShareTranslator` and `com.cocode.babakcast.data.ai.ShareTranslationResult`.

- [ ] **Step 2: Add the shared mapping helpers**

Add near the bottom of `MainViewModel` (above `renameForShare`):

```kotlin
    /** Maps a translation result to shareable text; Failed also surfaces a toast-style error. */
    private suspend fun textForShare(text: String, enabled: Boolean): String =
        when (val result = shareTranslator.translateIfEnabled(text, enabled)) {
            is ShareTranslationResult.Translated -> result.combinedText
            is ShareTranslationResult.Skipped -> text
            is ShareTranslationResult.Failed -> {
                _uiState.value = _uiState.value.copy(
                    error = AppError.NetworkError("Translation failed — sharing original text")
                )
                result.originalText
            }
        }

    /** Runs [block] with the in-flight flag set; always clears it and auto-resets the toggle. */
    private suspend fun withShareTranslation(block: suspend () -> Unit) {
        _uiState.value = _uiState.value.copy(isTranslatingForShare = true)
        try {
            block()
        } finally {
            _uiState.value = _uiState.value.copy(
                isTranslatingForShare = false,
                translateBeforeShare = false
            )
        }
    }
```

- [ ] **Step 3: Wire `fetchTweetTextAndEmit`** (covers both Copy and Share tweet-text actions)

Replace the success `else` branch body:

```kotlin
                    } else {
                        val enabled = _uiState.value.translateBeforeShare
                        withShareTranslation {
                            val shareText = textForShare(text, enabled)
                            _uiState.value = _uiState.value.copy(isFetchingTweetText = false, tweetText = text)
                            _tweetTextEvents.emit(event(shareText))
                        }
                    }
```

(`uiState.tweetText` keeps the ORIGINAL text; only the emitted event carries the combined text.)

- [ ] **Step 4: Wire `downloadAllXMedia` caption**

Replace:

```kotlin
                    val caption = result.text.ifBlank { null }
                    shareHelper.shareMixedMedia(result.allFiles, caption)
```

with:

```kotlin
                    val enabled = _uiState.value.translateBeforeShare
                    withShareTranslation {
                        val caption = result.text.ifBlank { null }
                            ?.let { textForShare(it, enabled) }
                        shareHelper.shareMixedMedia(result.allFiles, caption)
                    }
```

- [ ] **Step 5: Wire `shareAudioFiles` caption**

Replace the `caption =` line in the `ShareRequest.Audio(` construction:

```kotlin
        val enabled = _uiState.value.translateBeforeShare
        withShareTranslation {
            val request = ShareRequest.Audio(
                caption = textForShare(AudioShareCaption.build(videoInfo.title, shareFiles.size), enabled),
                files = shareFiles,
                mimeType = "audio/mpeg",
                title = "Share audio"
            )
            // Non-blank caption means a two-stage share: hold the files for after the
            // title is shared (see MainScreen's ON_RESUME observer).
            if (request.caption.isNotBlank()) {
                _pendingAudioFiles.value = request
            }
            _shareRequests.emit(request)
        }
```

(keep the surrounding logging / file-deletion lines exactly where they are — only the request construction and emit move inside `withShareTranslation`).

- [ ] **Step 6: Build + full test run**

Run: `./gradlew buildSmoke --no-daemon`
Expected: BUILD SUCCESSFUL, 0 failures.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/cocode/babakcast/ui/main/MainViewModel.kt
git commit -m "feat(share): translate tweet text and media captions when toggle is on"
```

---

### Task 7: Wire summary share paths

**Files:**
- Modify: `app/src/main/java/com/cocode/babakcast/ui/main/MainViewModel.kt` (`shareSummary` ~line 553, `shareSummaryAsFile` ~line 567)

**Interfaces:**
- Consumes: `textForShare` / `withShareTranslation` (Task 6), `ShareTextChunker.splitForShare(text)` (existing).
- Produces: no new API.

**Design note (resolves a spec wrinkle):** summary chunks are precomputed at generation time, but the toggle is read at share time. When the toggle is ON at share-tap, `shareSummary()` translates the FULL summary, re-chunks the combined text, replaces `summaryShareChunks`/`summaryShareIndex` in state, and shares the first combined chunk; subsequent taps (toggle now auto-reset to off) cycle through the bilingual chunks. When the toggle is OFF, behavior is exactly as today.

- [ ] **Step 1: Rewrite `shareSummary()`**

```kotlin
    fun shareSummary() {
        val state = _uiState.value
        val enabled = state.translateBeforeShare
        if (!enabled) {
            val chunks = state.summaryShareChunks
            if (chunks != null) {
                val index = state.summaryShareIndex.coerceIn(0, chunks.size - 1)
                val nextIndex = if (index + 1 >= chunks.size) 0 else index + 1
                _uiState.value = state.copy(summaryShareIndex = nextIndex)
                shareHelper.shareText(chunks[index], "Share Summary")
                return
            }
            val summary = state.summary ?: return
            shareHelper.shareText(summary, "Share Summary")
            return
        }
        val summary = state.summary ?: return
        viewModelScope.launch {
            withShareTranslation {
                val combined = textForShare(summary, true)
                val chunks = ShareTextChunker.splitForShare(combined)
                _uiState.value = _uiState.value.copy(
                    summaryShareChunks = chunks.takeIf { it.size > 1 },
                    summaryShareIndex = if (chunks.size > 1) 1 else 0
                )
                shareHelper.shareText(chunks.first(), "Share Summary")
            }
        }
    }
```

- [ ] **Step 2: Rewrite `shareSummaryAsFile()`**

```kotlin
    fun shareSummaryAsFile() {
        val summary = _uiState.value.summary ?: return
        val enabled = _uiState.value.translateBeforeShare
        viewModelScope.launch {
            withShareTranslation {
                val text = textForShare(summary, enabled)
                shareHelper.shareLongText(text, "Share Summary", forceFile = true)
            }
        }
    }
```

- [ ] **Step 3: Build + full test run**

Run: `./gradlew buildSmoke --no-daemon`
Expected: BUILD SUCCESSFUL, 0 failures.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/cocode/babakcast/ui/main/MainViewModel.kt
git commit -m "feat(share): translate summaries on share with re-chunking"
```

---

### Task 8: Doubled-length chunking regression test

**Files:**
- Test: `app/src/test/java/com/cocode/babakcast/util/ShareTextChunkerBilingualTest.kt`

**Interfaces:**
- Consumes: `ShareTextChunker.splitForShare(text)`, `ShareTextChunker.DEFAULT_MAX_CHUNK_CHARS` (existing), `TranslatedShareText.combine` (Task 1).
- Produces: nothing — regression guard for the spec's "doubled-length summary still chunks validly" requirement.

- [ ] **Step 1: Write the test** (should pass immediately — it guards existing behavior against bilingual-length input)

```kotlin
package com.cocode.babakcast.util

import org.junit.Assert.assertTrue
import org.junit.Test

class ShareTextChunkerBilingualTest {

    @Test
    fun combinedOriginalPlusTranslationStillChunksWithinLimit() {
        val original = buildString {
            repeat(60) { appendLine("Sentence number $it of a long English summary.") }
        }
        val translated = buildString {
            repeat(60) { appendLine("جمله شماره $it از یک خلاصه طولانی فارسی.") }
        }
        val combined = TranslatedShareText.combine(original, translated)
        val chunks = ShareTextChunker.splitForShare(combined)
        assertTrue(chunks.isNotEmpty())
        assertTrue(chunks.all { it.length <= ShareTextChunker.DEFAULT_MAX_CHUNK_CHARS })
        assertTrue(chunks.size > 1)
    }
}
```

- [ ] **Step 2: Run it**

Run: `./gradlew testDebugUnitTest --tests "com.cocode.babakcast.util.ShareTextChunkerBilingualTest" --no-daemon`
Expected: PASS. If a chunk exceeds the limit, that is a real `ShareTextChunker` bug surfaced by bilingual input — STOP and report it rather than adjusting the test.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/cocode/babakcast/util/ShareTextChunkerBilingualTest.kt
git commit -m "test(share): guard chunking against bilingual-length summaries"
```

---

### Task 9: Docs + website

**Files:**
- Modify: `README.md` (feature list — add one bullet)
- Modify: `website/index.html` (features section — add one entry, English)
- Modify: `website/fa/index.html` (features section — add one entry, Persian)

**Interfaces:** none — documentation only.

- [ ] **Step 1: README** — add to the features list, matching the existing bullet style:

```markdown
- **Optional Persian translation on share** — flip the "Translate to Persian" toggle and the next share (tweet text, captions, or summaries) includes an AI-generated Persian translation below the original.
```

- [ ] **Step 2: Website EN** — add a feature entry to `website/index.html` following the exact markup pattern of the existing feature items (inspect neighboring entries and copy their structure):

Title: `Persian translation on share` — Body: `Optionally translate shared text to Persian with AI — original and translation go out together.`

- [ ] **Step 3: Website FA** — same entry in `website/fa/index.html` in Persian:

Title: `ترجمه فارسی هنگام اشتراک‌گذاری` — Body: `متن اشتراکی را به‌صورت اختیاری با هوش مصنوعی به فارسی ترجمه کنید — متن اصلی و ترجمه با هم ارسال می‌شوند.`

- [ ] **Step 4: Commit**

```bash
git add README.md website/index.html website/fa/index.html
git commit -m "docs: document Persian share-translation feature"
```

---

### Task 10: Final verification + PR

**Files:** none new.

- [ ] **Step 1: Full suite** — `./gradlew buildSmoke --no-daemon` → BUILD SUCCESSFUL, 0 test failures (expected new-test total: 428 pre-existing + 15 new = 443; verify the exact count in the run output).
- [ ] **Step 2: Push** — `git push -u origin feat/share-translation`. GitHub auth note: scope credentials per command with `export GH_TOKEN=$(gh auth token --user cocodedk)` — do NOT `gh auth switch` (another agent shares the active-account state on this machine).
- [ ] **Step 3: PR** — `gh pr create` titled `feat(share): optional AI translation to Persian on share`, body summarizing: toggle UX, all wired call sites, ProviderResolver refactor, failure-never-blocks-share policy, and test coverage. Wait for CI green before merging; merge with `--squash --delete-branch` only after CI passes.

---

## Self-Review (done at planning time)

- **Spec coverage:** toggle state/UI (T5), tweet text incl. copy (T6), mixed-media caption (T6), audio caption (T6), summary + summary-as-file with pre-chunk translation (T7), ProviderResolver extraction with tests-before-rewire (T2→T4 order), sealed result + 15s timeout + RLM combine (T1/T3), finally-based reset + in-flight disable (T5/T6), chunking guard (T8), README/website EN+FA (T9). Out-of-scope items match the spec.
- **Type consistency:** `translateIfEnabled(text, enabled)`, `ShareTranslationResult.{Translated(combinedText), Skipped, Failed(originalText)}`, `pickProviderId(defaultProviderId, hasApiKey, allProviderIds)`, `textForShare(text, enabled)`, `withShareTranslation(block)` — names used identically across tasks.
- **Placeholders:** none; every code step shows the code. Two intentional look-and-match instructions remain (website markup pattern in T9, button `enabled =` expressions in T5) because the implementer must mirror surrounding code they can see.
