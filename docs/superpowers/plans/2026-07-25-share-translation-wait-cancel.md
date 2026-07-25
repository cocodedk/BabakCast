# Share Translation: Wait-Until-Done + "Share Now" Cancel — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a pre-share Persian translation run to completion (~3-minute HTTP bound) instead of timing out at 60s, with a "Share now" button as the user-controlled escape hatch that shares the original text immediately, without an error toast.

**Architecture:** Three layers change. (1) `ShareTranslationRunner` runs the translation in a cancellable child coroutine and exposes `cancelActiveTranslation()`; user-cancel maps to a new `ShareTranslationResult.Cancelled` (original text, no error state). (2) The translation HTTP call gets a dedicated 180s read timeout via a derived OkHttp client (`newBuilder()` — shares pool/dispatcher, cheap); the in-app `withTimeoutOrNull` becomes a 300s pure backstop that only fires if the HTTP layer itself hangs. (3) `TranslateToggleRow` swaps the Switch for a "Share now" `TextButton` while `isTranslatingForShare` is true, wired through `ActionButtonsSection` → `MainViewModel.cancelShareTranslation()`.

**Tech Stack:** Kotlin coroutines (`coroutineScope`/`async`/`ensureActive`), OkHttp derived clients, Jetpack Compose Material3, plain JUnit4 + `runBlocking` (no mocking libraries — repo convention).

## Global Constraints

- **No mocking libraries.** Testable logic goes through companion objects or injected lambdas; tests drive production code directly.
- **200-line max per code file.** `AIClient.kt` (223), `MainViewModel.kt` (818), `ActionButtonsSection.kt` (335), `MainScreen.kt` (414) are pre-existing violators: each may grow by ≤5 lines here, no restructuring in this plan.
- **Conventional Commits**, enforced by commit-msg hook; pre-commit runs `buildSmoke` (~2 min). Never `--no-verify`.
- Exact constants: `HTTP_READ_TIMEOUT_MS = 180_000L`, `TIMEOUT_MS = 300_000L` (backstop), both in `ShareTranslator.companion`.
- Failure toast text stays exactly: `"Translation failed — sharing original text"`. User-cancel produces NO error state.
- Default OkHttp client in `di/AppModule.kt` (30s/60s/60s) is NOT modified — only the translation call path gets the longer read timeout.

---

### Task 1: Cancellable translation await in ShareTranslationRunner

**Files:**
- Modify: `app/src/main/java/com/cocode/babakcast/data/ai/ShareTranslationResult.kt`
- Modify: `app/src/main/java/com/cocode/babakcast/ui/main/ShareTranslationRunner.kt`
- Test: `app/src/test/java/com/cocode/babakcast/ui/main/ShareTranslationRunnerTest.kt`

**Interfaces:**
- Consumes: existing `ShareTranslationResult` sealed class; existing runner constructor pair (unchanged).
- Produces: `ShareTranslationResult.Cancelled(originalText: String)`; `fun cancelActiveTranslation()` on `ShareTranslationRunner` (Task 3's ViewModel hook calls exactly this name). `textForShare(text, enabled)` signature unchanged.

- [ ] **Step 1: Write the failing tests**

Append to `ShareTranslationRunnerTest.kt` (add imports `kotlinx.coroutines.CompletableDeferred`, `kotlinx.coroutines.async`, `kotlinx.coroutines.awaitCancellation`, `kotlinx.coroutines.launch` to the existing import block):

```kotlin
    @Test
    fun textForShare_cancelledResult_returnsOriginal_withoutError() = runBlocking {
        var state = MainUiState()
        val r = runner(
            state = { state },
            setState = { state = it },
            translate = { _, _ -> ShareTranslationResult.Cancelled("hello") }
        )

        val result = r.textForShare("hello", enabled = true)

        assertEquals("hello", result)
        assertNull(state.error)
    }

    @Test
    fun cancelActiveTranslation_returnsOriginal_withoutError() = runBlocking {
        var state = MainUiState()
        val started = CompletableDeferred<Unit>()
        val r = runner(
            state = { state },
            setState = { state = it },
            translate = { _, _ ->
                started.complete(Unit)
                awaitCancellation()
            }
        )

        val share = async { r.textForShare("hello", enabled = true) }
        started.await()
        r.cancelActiveTranslation()

        assertEquals("hello", share.await())
        assertNull(state.error)
    }

    @Test
    fun textForShare_outerCancellation_propagates_asCancelled() = runBlocking {
        var state = MainUiState()
        val started = CompletableDeferred<Unit>()
        val r = runner(
            state = { state },
            setState = { state = it },
            translate = { _, _ ->
                started.complete(Unit)
                awaitCancellation()
            }
        )

        val share = launch { r.textForShare("hello", enabled = true) }
        started.await()
        share.cancel()
        share.join()

        assertTrue(share.isCancelled)
        assertNull(state.error)
    }
```

Note on determinism: `runBlocking` is single-threaded, so `activeTranslation` is always assigned before `started.await()` resumes the outer coroutine — no race.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --no-daemon --tests "com.cocode.babakcast.ui.main.ShareTranslationRunnerTest"`
Expected: FAIL — compilation errors (`Cancelled` and `cancelActiveTranslation` unresolved). Compile failure is the RED state here.

- [ ] **Step 3: Implement**

`ShareTranslationResult.kt` — add one variant:

```kotlin
sealed class ShareTranslationResult {
    data class Translated(val combinedText: String) : ShareTranslationResult()
    object Skipped : ShareTranslationResult()
    data class Failed(val originalText: String) : ShareTranslationResult()
    data class Cancelled(val originalText: String) : ShareTranslationResult()
}
```

`ShareTranslationRunner.kt` — full new content (constructors and `withTranslation` unchanged; `textForShare` rewritten):

```kotlin
package com.cocode.babakcast.ui.main

import com.cocode.babakcast.data.ai.ShareTranslator
import com.cocode.babakcast.data.ai.ShareTranslationResult
import com.cocode.babakcast.util.AppError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive

/**
 * Wraps a share flow with the translate-toggle lifecycle: sets the in-flight
 * flag, maps the translation result to shareable text, and always clears the
 * flag and auto-resets the toggle when the flow ends — even on exception.
 * The in-flight translation is cancellable ("Share now"): user-cancel shares
 * the original text with no error surfaced.
 */
internal class ShareTranslationRunner(
    private val updateState: ((MainUiState) -> MainUiState) -> Unit,
    private val translate: suspend (String, Boolean) -> ShareTranslationResult
) {
    constructor(
        shareTranslator: ShareTranslator,
        updateState: ((MainUiState) -> MainUiState) -> Unit
    ) : this(updateState, shareTranslator::translateIfEnabled)

    @Volatile
    private var activeTranslation: Deferred<ShareTranslationResult>? = null

    /** Cancels the in-flight translation, if any; the share proceeds with the original text. */
    fun cancelActiveTranslation() {
        activeTranslation?.cancel()
    }

    suspend fun withTranslation(block: suspend () -> Unit) {
        updateState { it.copy(isTranslatingForShare = true) }
        try {
            block()
        } finally {
            updateState {
                it.copy(isTranslatingForShare = false, translateBeforeShare = false)
            }
        }
    }

    suspend fun textForShare(text: String, enabled: Boolean): String = coroutineScope {
        val translation = async { translate(text, enabled) }
        activeTranslation = translation
        val result = try {
            translation.await()
        } catch (e: CancellationException) {
            // A cancelled child means the user tapped Share now; if the whole
            // share flow is being torn down instead, keep propagating.
            ensureActive()
            ShareTranslationResult.Cancelled(text)
        } finally {
            activeTranslation = null
        }
        when (result) {
            is ShareTranslationResult.Translated -> result.combinedText
            is ShareTranslationResult.Skipped -> text
            is ShareTranslationResult.Cancelled -> result.originalText
            is ShareTranslationResult.Failed -> {
                updateState {
                    it.copy(error = AppError.NetworkError("Translation failed — sharing original text"))
                }
                result.originalText
            }
        }
    }
}
```

- [ ] **Step 4: Run the test class to verify all pass**

Run: `./gradlew testDebugUnitTest --no-daemon --tests "com.cocode.babakcast.ui.main.ShareTranslationRunnerTest"`
Expected: PASS — 8 tests (5 existing + 3 new).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cocode/babakcast/data/ai/ShareTranslationResult.kt \
        app/src/main/java/com/cocode/babakcast/ui/main/ShareTranslationRunner.kt \
        app/src/test/java/com/cocode/babakcast/ui/main/ShareTranslationRunnerTest.kt
git commit -m "feat(share): make in-flight translation cancellable"
```

---

### Task 2: Raise both timeout layers on the translation path

**Files:**
- Modify: `app/src/main/java/com/cocode/babakcast/data/remote/AIClient.kt` (makeRequest signature + client selection; file is 223 lines pre-existing — keep the addition ≤5 lines)
- Modify: `app/src/main/java/com/cocode/babakcast/data/repository/AIRepository.kt` (translate signature, lines 138–174)
- Modify: `app/src/main/java/com/cocode/babakcast/data/ai/ShareTranslator.kt`
- Test: `app/src/test/java/com/cocode/babakcast/data/ai/ShareTranslatorTest.kt`

**Interfaces:**
- Consumes: nothing from Task 1 (independent of cancellation).
- Produces: `AIClient.makeRequest(provider, messages, temperature, maxTokens, readTimeoutMs: Long? = null)`; `AIRepository.translate(text, providerId, targetLanguage, temperature, readTimeoutMs: Long? = null)`; constants `ShareTranslator.HTTP_READ_TIMEOUT_MS = 180_000L` and `ShareTranslator.TIMEOUT_MS = 300_000L`. Default `null` keeps every other caller (summaries, chunk merges) byte-for-byte on the shared 60s client.

- [ ] **Step 1: Replace the timeout-floor test (failing first)**

In `ShareTranslatorTest.kt`, replace the existing `timeoutCapAccommodatesObservedProviderLatency` test with:

```kotlin
    @Test
    fun waitPolicy_httpLayerIsGenerous_andBackstopOutlivesIt() {
        // "Wait until done": the HTTP read timeout is the working bound; the
        // in-app backstop must only fire if the HTTP layer itself hangs, so it
        // has to outlive connect (30s) + write (60s) + read phases combined.
        assertTrue(ShareTranslator.HTTP_READ_TIMEOUT_MS >= 120_000L)
        assertTrue(ShareTranslator.TIMEOUT_MS >= ShareTranslator.HTTP_READ_TIMEOUT_MS + 90_000L)
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew testDebugUnitTest --no-daemon --tests "com.cocode.babakcast.data.ai.ShareTranslatorTest"`
Expected: FAIL — compilation error, `HTTP_READ_TIMEOUT_MS` unresolved (RED).

- [ ] **Step 3: Implement**

`AIClient.kt` — add `import java.util.concurrent.TimeUnit`; change `makeRequest` signature and the call execution line:

```kotlin
    suspend fun makeRequest(
        provider: Provider,
        messages: List<AIMessage>,
        temperature: Double,
        maxTokens: Int,
        readTimeoutMs: Long? = null
    ): Result<AIResponse> = withContext(Dispatchers.IO) {
```

and replace `val response = okHttpClient.newCall(request).execute()` with:

```kotlin
            // Derived client shares the pool/dispatcher; only the read timeout differs.
            val client = if (readTimeoutMs == null) okHttpClient
            else okHttpClient.newBuilder().readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS).build()
            val response = client.newCall(request).execute()
```

`AIRepository.kt` — `translate` gains the pass-through parameter:

```kotlin
    suspend fun translate(
        text: String,
        providerId: String,
        targetLanguage: String,
        temperature: Double,
        readTimeoutMs: Long? = null
    ): Result<String> = withContext(Dispatchers.IO) {
```

and its `aiClient.makeRequest(...)` call becomes:

```kotlin
            val response = aiClient.makeRequest(
                providerWithModel,
                messages,
                temperature,
                providerWithModel.limits.max_output_tokens,
                readTimeoutMs
            )
```

`ShareTranslator.kt` — constants and the translate lambda:

```kotlin
    suspend fun translateIfEnabled(text: String, enabled: Boolean): ShareTranslationResult =
        run(
            text = text,
            enabled = enabled,
            resolveProviderId = { providerResolver.resolve()?.id },
            translate = { providerId ->
                aiRepository.translate(text, providerId, TARGET_LANGUAGE, TEMPERATURE, HTTP_READ_TIMEOUT_MS)
            },
            timeoutMs = TIMEOUT_MS
        ).also { result ->
            if (result is ShareTranslationResult.Failed) {
                Log.w(TAG, "Translation failed; sharing original (readTimeout=${HTTP_READ_TIMEOUT_MS}ms, backstop=${TIMEOUT_MS}ms)")
            }
        }

    companion object {
        private const val TAG = "ShareTranslator"
        const val TARGET_LANGUAGE = "Persian"
        const val TEMPERATURE = 0.2
        const val HTTP_READ_TIMEOUT_MS = 180_000L
        const val TIMEOUT_MS = 300_000L
```

(The companion `run` function is unchanged.)

- [ ] **Step 4: Run the test class, then the full suite**

Run: `./gradlew testDebugUnitTest --no-daemon --tests "com.cocode.babakcast.data.ai.ShareTranslatorTest"`
Expected: PASS — 8 tests.
Run: `./gradlew testDebugUnitTest --no-daemon`
Expected: BUILD SUCCESSFUL, pristine output.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cocode/babakcast/data/remote/AIClient.kt \
        app/src/main/java/com/cocode/babakcast/data/repository/AIRepository.kt \
        app/src/main/java/com/cocode/babakcast/data/ai/ShareTranslator.kt \
        app/src/test/java/com/cocode/babakcast/data/ai/ShareTranslatorTest.kt
git commit -m "feat(share): wait up to 3 minutes for translation via dedicated read timeout"
```

---

### Task 3: "Share now" UI, ViewModel hook, and docs

**Files:**
- Modify: `app/src/main/java/com/cocode/babakcast/ui/main/MainViewModel.kt` (near `setTranslateBeforeShare`, ~line 555)
- Modify: `app/src/main/java/com/cocode/babakcast/ui/main/TranslateToggleRow.kt`
- Modify: `app/src/main/java/com/cocode/babakcast/ui/main/ActionButtonsSection.kt` (signature ~line 34, call ~line 52)
- Modify: `app/src/main/java/com/cocode/babakcast/ui/main/MainScreen.kt` (~line 291)
- Modify: `README.md` (line 29 bullet)
- Modify: `docs/superpowers/specs/2026-07-18-share-translation-design.md` (timeout references and behavior list)

**Interfaces:**
- Consumes: `ShareTranslationRunner.cancelActiveTranslation()` from Task 1.
- Produces: `MainViewModel.cancelShareTranslation()`; `TranslateToggleRow(uiState, onToggle, onShareNow)`; `ActionButtonsSection` gains trailing param `onShareNow: () -> Unit`.

- [ ] **Step 1: ViewModel hook**

In `MainViewModel.kt`, directly after the `setTranslateBeforeShare` function:

```kotlin
    fun cancelShareTranslation() = shareTranslationRunner.cancelActiveTranslation()
```

- [ ] **Step 2: TranslateToggleRow — swap Switch for Share now while in flight**

Full new content of `TranslateToggleRow.kt`:

```kotlin
package com.cocode.babakcast.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.cocode.babakcast.ui.theme.BabakCastColors

@Composable
internal fun TranslateToggleRow(
    uiState: MainUiState,
    onToggle: (Boolean) -> Unit,
    onShareNow: () -> Unit
) {
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
                if (uiState.isTranslatingForShare) "Translating…"
                else "Adds a Persian translation to the next share",
                style = MaterialTheme.typography.bodySmall,
                color = BabakCastColors.PrimaryAccent.copy(alpha = 0.7f)
            )
        }
        if (uiState.isTranslatingForShare) {
            TextButton(onClick = onShareNow) { Text("Share now") }
        } else {
            Switch(
                checked = uiState.translateBeforeShare,
                onCheckedChange = onToggle
            )
        }
    }
}
```

- [ ] **Step 3: Thread the callback through**

`ActionButtonsSection.kt` — add trailing parameter after `onTranslateToggle: (Boolean) -> Unit`:

```kotlin
    onTranslateToggle: (Boolean) -> Unit,
    onShareNow: () -> Unit
```

and change the call at ~line 52 to:

```kotlin
        TranslateToggleRow(uiState, onTranslateToggle, onShareNow)
```

`MainScreen.kt` — add after `onTranslateToggle = viewModel::setTranslateBeforeShare`:

```kotlin
                    onShareNow = viewModel::cancelShareTranslation
```

- [ ] **Step 4: Docs**

`README.md` line 29 — replace the bullet with:

```markdown
- **Optional Persian translation on share** — flip the "Translate to Persian" toggle and the next share (tweet text, captions, or summaries) includes an AI-generated Persian translation below the original. Slow providers get up to 3 minutes; while it runs, a **Share now** button lets you skip the wait and share the original immediately.
```

`docs/superpowers/specs/2026-07-18-share-translation-design.md`:
- Behavior step 3 (~line 49): `wrapped in withTimeoutOrNull(60_000).` → `with a dedicated 180_000 ms HTTP read timeout, wrapped in withTimeoutOrNull(300_000) as a hang backstop.`
- Add behavior step 6 after step 5 (`Error or timeout → Failed(text).`):
  `6. User taps "Share now" while in flight → the translation coroutine is cancelled → Cancelled(text) → share original, no error surfaced.`
- Flow diagram (~line 105): `→ aiRepository.translate(...) with 60s timeout` → `→ aiRepository.translate(...) with 180s HTTP read timeout (300s backstop; "Share now" cancels)`
- Risks (~line 126): `the in-flight indicator and 60s timeout bound this.` → `the in-flight indicator, the "Share now" cancel affordance, and the 180s HTTP read timeout (300s backstop) bound this.`

- [ ] **Step 5: Verify**

Run: `./gradlew testDebugUnitTest --no-daemon`
Expected: BUILD SUCCESSFUL (no unit tests cover Compose; this catches the compile of all wired signatures).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/cocode/babakcast/ui/main/MainViewModel.kt \
        app/src/main/java/com/cocode/babakcast/ui/main/TranslateToggleRow.kt \
        app/src/main/java/com/cocode/babakcast/ui/main/ActionButtonsSection.kt \
        app/src/main/java/com/cocode/babakcast/ui/main/MainScreen.kt \
        README.md docs/superpowers/specs/2026-07-18-share-translation-design.md
git commit -m "feat(share): add Share now escape hatch while translating"
```
