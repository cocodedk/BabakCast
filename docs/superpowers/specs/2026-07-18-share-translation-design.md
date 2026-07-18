# Share Translation — Design Spec

**Date:** 2026-07-18
**Status:** Approved (brainstormed with user; reviewed twice by Fable 5 advisor)

## Overview

Add an opt-in, AI-driven translation step to the share flow. When the user enables
the toggle before a share action, the shared text (tweet text, audio/video caption,
or AI summary) is translated to Persian and shared as *original + translation*
together. Translation reuses the existing, currently-unused
`AIRepository.translate()` and the existing provider/API-key infrastructure.

## Product decisions (fixed)

| Decision | Choice |
|---|---|
| Scope | All shared text: tweet text, captions, summaries |
| Opt-in UX | One global toggle near the action buttons, off by default |
| Toggle lifetime | Transient (not persisted); auto-resets to off after each share |
| Share content | Original text + separator + Persian translation in one share |
| Target language | Persian only, hardcoded (v1; no language picker) |
| Preview | None — straight to the share sheet |
| Failure policy | Never block the share: fall back to original text + brief toast |
| Out of scope | `DownloadsViewModel.shareText(item.displayName)` — filename, not prose; other languages; preview/edit step |

## Architecture

### New: `data/ai/ProviderResolver.kt`
Extracts the provider-resolution logic currently inlined in
`MainViewModel.generateSummary()` into one small injectable class:

- `suspend fun resolve(): Provider?` — returns the settings default provider if it
  has an API key, else the first provider with an API key, else `null`.
- Used by both `ShareTranslator` and the refactored `generateSummary()` flow.
- Gets its own unit tests **before** `generateSummary()` is rewired to it.

### New: `data/ai/ShareTranslator.kt` (Hilt `@Singleton`)
Orchestrator (deliberately not named/placed as a repository). Single public method:

```kotlin
suspend fun translateIfEnabled(text: String, enabled: Boolean): ShareTranslationResult
```

Behavior:
1. `enabled == false` → `Skipped` immediately (no provider lookup, no network).
2. Resolve provider via `ProviderResolver`; none configured → `Failed(text)`.
3. Call `aiRepository.translate(text, providerId, targetLanguage = "Persian", temperature = 0.2)`
   wrapped in `withTimeoutOrNull(15_000)`.
4. Success → `Translated(TranslatedShareText.combine(text, translated))`.
5. Error or timeout → `Failed(text)`.

### New: `ShareTranslationResult` (sealed)
```kotlin
sealed class ShareTranslationResult {
    data class Translated(val combinedText: String) : ShareTranslationResult()
    object Skipped : ShareTranslationResult()
    data class Failed(val originalText: String) : ShareTranslationResult()
}
```
Every branch carries shareable text — all three MUST end in a share at the call site.

### New: `util/TranslatedShareText.kt`
Pure function: `combine(original, translated)` →
`"$original\n\n———\n\n‏$translated"`.
The U+200F (right-to-left mark) after the separator makes bidi renderers
(WhatsApp etc.) lay out the Persian block correctly.

### Changed: `MainUiState`
- `translateBeforeShare: Boolean = false` — the toggle (transient).
- `isTranslatingForShare: Boolean = false` — in-flight indicator.

### Changed: `ActionButtonsSection`
- `Switch` + "Translate to Persian" label near the existing action buttons,
  wired via `onTranslateToggle: (Boolean) -> Unit` (existing callback convention).
- All share-triggering buttons are disabled while `isTranslatingForShare` is true
  (a single boolean cannot represent two concurrent in-flight shares, so concurrent
  share taps are prevented rather than tracked).

### Changed: `MainViewModel` share call sites
Tweet text share/copy, audio/video caption share, summary share each:
1. Read `translateBeforeShare` **once** into a local `val` at tap time (passed into
   the coroutine — a mid-flight toggle flip cannot affect an in-progress share).
2. Set `isTranslatingForShare = true`.
3. Call `shareTranslator.translateIfEnabled(text, enabled)` and map the result:
   - `Translated(combined)` → share combined text
   - `Skipped` → share original
   - `Failed(original)` → toast "Translation failed — sharing original", share original
4. In a `finally` block: clear `isTranslatingForShare` and reset
   `translateBeforeShare` to false (auto-reset even if the coroutine throws).

For summaries, translation runs **before** `ShareTextChunker.splitForShare()`, so
chunking operates on the final combined text.

No changes to `ShareHelper`, `AIClient`, `AIRepository`, or `ProviderRepository`.

## Data flow

```
share tap
  → val enabled = uiState.translateBeforeShare   (read once)
  → isTranslatingForShare = true (share buttons disable)
  → shareTranslator.translateIfEnabled(text, enabled)
      → ProviderResolver.resolve()
      → aiRepository.translate(...) with 15s timeout
  → Translated / Skipped / Failed  → share sheet (always)
  → finally: isTranslatingForShare = false; translateBeforeShare = false
```

## Testing

- **ProviderResolver:** default-with-key, fallback-to-first-with-key,
  none-configured → null. Written before rewiring `generateSummary()`.
- **ShareTranslator:** toggle off → `Skipped` with no AI call; success → combined
  output; AI error → `Failed(original)`; timeout → `Failed(original)`.
- **TranslatedShareText:** exact output shape incl. separator and U+200F.
- **Chunking:** a doubled-length (original + translation) summary through
  `ShareTextChunker` still produces valid chunks.
- **Regression:** existing summary tests stay green after the resolver extraction.
- Tests call production code directly (companion objects / injected classes) —
  never duplicate logic in tests.

## Risks / notes

- Shares that were previously instant gain a network round-trip when the toggle is
  on; the in-flight indicator and 15s timeout bound this.
- App UI strings are currently hardcoded inline in Compose (no `res/values-fa`);
  the toggle label follows the existing convention. Full app localization is a
  separate concern, untouched here.
