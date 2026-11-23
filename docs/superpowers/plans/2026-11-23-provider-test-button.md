# Provider Test Button Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Test button in the provider dialog that sends one short chat request with the typed key, model and endpoint and says in plain words whether it works. Also fixes the Anthropic, Gemini and Azure requests that the test would otherwise show failing.

**Architecture:** The test goes through `AIClient.makeRequest`, the code path summaries and translation use, with the key passed in instead of read from storage. A pure `ChatRequestBody` builds each provider's request format. A pure classifier turns the result into an outcome the UI maps to a string. New code goes in new files; `SettingsScreen.kt` and `SettingsViewModel.kt` get only the wiring.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, OkHttp, kotlinx.serialization, JUnit4.

**Spec:** `docs/superpowers/specs/2026-11-23-provider-test-button-design.md`

## Global Constraints

- Code files under 200 lines. Don't grow `SettingsScreen.kt` (345) or `SettingsViewModel.kt` (232) beyond wiring.
- No hardcoded UI strings. Every new string goes in `values/strings.xml` and `values-da/strings.xml`.
- Tests call production code (companion objects / internal objects), never copies of it.
- Never log an API key or an auth header value (`AIClient.headerNames` logs names only).
- Gradle needs `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`. Run tests with `./gradlew testGithubDebugUnitTest --no-daemon --tests '<class>'`.
- Conventional Commits, ending with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Pasted key with a trailing space or newline.** The test and Save must both trim it. Otherwise the test is green (or crashes in OkHttp) while summaries use a different string. Owned by Task 2 (Save trims) and Task 3 (`ProviderChecker` trims before sending).
2. **Malformed Azure endpoint** (no scheme, typo). OkHttp throws `IllegalArgumentException`; the outcome must be "can't reach, check the endpoint", not a crash. Owned by Task 3.
3. **Dialog closed while a test runs.** No result may show when it reopens. Owned by Task 4 (reset on dispose).
4. **Key, model or endpoint edited after a test.** The old result must hide. Owned by Task 4 (`outcomeFor` test).
5. **Gemini reply with no text part** (a thinking model that hits the cap). The result is "connected, reply unreadable", not "works". Owned by Task 3 (classify test).

---

### Task 1: Provider request formats (Anthropic, Gemini)

**Files:**
- Create: `app/src/main/java/com/cocode/babakcast/data/remote/ChatRequestBody.kt`
- Modify: `app/src/main/java/com/cocode/babakcast/data/remote/AIClient.kt` (replace `buildRequest`; add extra headers)
- Test: `app/src/test/java/com/cocode/babakcast/data/remote/ChatRequestBodyTest.kt`

**Interfaces:**
- Produces: `internal object ChatRequestBody { fun build(provider: Provider, messages: List<AIMessage>, temperature: Double, maxTokens: Int): JsonObject; fun extraHeaders(provider: Provider): Map<String, String> }`

- [ ] **Step 1: Write the failing tests** (use the predefined providers from `ProviderRepository`; expose them as `ProviderRepository.PREDEFINED` in its companion if needed, so no copy lives in the test)
  - `openAiStyle_keepsTodayShape`: openai → `model`, `messages` = [{role:"system",content}, {role:"user",content}], `temperature`, `max_tokens` at top level.
  - `anthropic_movesSystemToTopLevel`: `system` == the system text; `messages` has only the user message; `model`, `max_tokens`, `temperature` present.
  - `anthropic_sendsVersionHeader`: `extraHeaders(anthropic)` == `mapOf("anthropic-version" to "2023-06-01")`; empty for openai, openrouter, azure, gemini.
  - `gemini_usesPartsAndModelRole`: `contents` == [{role:"user", parts:[{text}]}]; an assistant message gets role `"model"`; `systemInstruction` == {parts:[{text: system}]}.
  - `gemini_nestsGenerationConfig`: `generationConfig.temperature` and `generationConfig.maxOutputTokens` are nested; there is no literal `"generationConfig.temperature"` key and no `model` key.
- [ ] **Step 2: Run, expect FAIL** (unresolved `ChatRequestBody`).
- [ ] **Step 3: Implement.** Pick the format by `provider.id`: `"anthropic"`, `"google-gemini"`, anything else stays OpenAI-style (today's behaviour). Write dotted paths (`generationConfig.temperature`) as nested objects. In `AIClient`, encode `ChatRequestBody.build(...)` and add each `extraHeaders` entry to the request.
- [ ] **Step 4: Run, expect PASS.** Also run `AiResponseParserTest`.
- [ ] **Step 5: Commit** `fix(ai): send Anthropic and Gemini requests in their own format`.

### Task 2: Saved settings (Azure endpoint, model, trimmed key)

**Files:**
- Modify: `app/src/main/java/com/cocode/babakcast/data/repository/ProviderRepository.kt` (endpoint save/get/delete, applied in `getProviderWithSelectedModel`)
- Modify: `app/src/main/java/com/cocode/babakcast/ui/settings/SettingsViewModel.kt` (`selectProvider`, `saveProviderConfig`, `deleteProviderApiKey`)
- Test: `app/src/test/java/com/cocode/babakcast/data/repository/ProviderOverridesTest.kt`

**Interfaces:**
- Produces: `ProviderRepository.saveEndpoint(providerId: String, url: String)`, `deleteEndpoint(providerId: String)`, and `companion fun withOverrides(provider: Provider, model: String?, endpoint: String?): Provider`. Blank or null leaves the field as it is.

- [ ] **Step 1: Write the failing tests**
  - `withOverrides_appliesModelAndEndpoint`
  - `withOverrides_blankKeepsDefaults`
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement.** Store the endpoint in `modelPrefs` under `"url_$providerId"`. `getProviderWithSelectedModel` uses `withOverrides`. In `SettingsViewModel`:
  - `selectProvider` fills `editingApiUrl` from the overridden provider.
  - `saveProviderConfig` saves `apiKey.trim()`; saves the endpoint only for `azure-openai` when it is non-blank and not the default (otherwise deletes it); saves the model when it differs from `provider.model` and calls `deleteSelectedModel` when it equals it.
  - `deleteProviderApiKey` also deletes the endpoint.
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** `fix(settings): save the Azure endpoint and the chosen model`.

### Task 3: Check outcome (key override, HTTP code, classifier, checker)

**Files:**
- Modify: `app/src/main/java/com/cocode/babakcast/data/remote/AIClient.kt`: `makeRequest(..., apiKey: String? = null)`, used instead of storage when non-null; on a non-2xx, pass `AiHttpStatus(code)` as the `cause` of the existing `AppErrorException`.
- Create: `app/src/main/java/com/cocode/babakcast/data/remote/ProviderCheckOutcome.kt` (sealed outcome + `AiHttpStatus` + `classify`)
- Create: `app/src/main/java/com/cocode/babakcast/data/remote/ProviderChecker.kt`
- Test: `app/src/test/java/com/cocode/babakcast/data/remote/ProviderCheckOutcomeTest.kt`

**Interfaces:**
- Produces:
  - `class AiHttpStatus(val code: Int) : Exception()`
  - `sealed interface ProviderCheckOutcome { data class Works(val elapsedMs: Long); data object Unreadable; data object KeyRejected; data object NotFound; data object UsageLimit; data class Refused(val code: Int); data object Unreachable }`, with `companion fun classify(result: Result<AIResponse>, elapsedMs: Long): ProviderCheckOutcome`
  - `@Singleton class ProviderChecker @Inject constructor(aiClient: AIClient) { suspend fun check(provider: Provider, apiKey: String): ProviderCheckOutcome }`. It sends `PromptTemplates.SYSTEM_PROMPT` plus one user message asking for a one-word reply, with `ShareTranslator.TEMPERATURE` and `provider.limits.max_output_tokens` (the same shape as a translation, so green means translation works), using `apiKey.trim()`.

- [ ] **Step 1: Write the failing tests**
  - success → `Works(elapsedMs)`
  - `AppErrorException(AiResponseUnreadable)` → `Unreadable`
  - cause 401 and 403 → `KeyRejected`; 404 → `NotFound`
  - 429, and `AppError.ApiQuotaExceeded` with any code → `UsageLimit`
  - 400 and 500 → `Refused(code)`
  - `UnknownHostException`, `SocketTimeoutException`, `ConnectException` and `IllegalArgumentException("unexpected url")` → `Unreachable`
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement** `classify`, `AiHttpStatus` and the AIClient changes. Check that `ErrorHandler` still maps an `AppErrorException` by its `error`; it does, since the cause is ignored. Implement `ProviderChecker` by timing `makeRequest` and calling `classify`.
- [ ] **Step 4: Run, expect PASS**, plus `ErrorHandlerTest`.
- [ ] **Step 5: Commit** `feat(ai): classify a provider check into a plain outcome`.

### Task 4: Test state and view model

**Files:**
- Create: `app/src/main/java/com/cocode/babakcast/ui/settings/ProviderTestViewModel.kt` (state + VM)
- Test: `app/src/test/java/com/cocode/babakcast/ui/settings/ProviderTestStateTest.kt`

**Interfaces:**
- Consumes: `ProviderChecker.check`, `ProviderCheckOutcome`, `ProviderRepository.withOverrides`
- Produces:
  - `data class ProviderTestInputs(val providerId: String, val apiKey: String, val model: String, val endpoint: String)`
  - `data class ProviderTestState(val inputs: ProviderTestInputs? = null, val running: Boolean = false, val outcome: ProviderCheckOutcome? = null) { fun outcomeFor(current: ProviderTestInputs): ProviderCheckOutcome? }`
  - `ProviderTestViewModel.state: StateFlow<ProviderTestState>`, `test(provider: Provider, inputs: ProviderTestInputs)` (ignored while running), `reset()` (cancels the job, clears state)

- [ ] **Step 1: Write the failing tests**
  - `outcomeFor_sameInputs_returnsOutcome`
  - `outcomeFor_changedKeyModelOrEndpoint_returnsNull`
  - `outcomeFor_whileRunning_returnsNull`
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement.** The VM builds the provider with `withOverrides(provider, inputs.model, inputs.endpoint)`, then calls `checker.check(..., inputs.apiKey)` on `Dispatchers.IO`.
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** `feat(settings): provider test state`.

### Task 5: Test button in the provider dialog

**Files:**
- Create: `app/src/main/java/com/cocode/babakcast/ui/settings/ProviderTestRow.kt` (button + result text + `ProviderCheckOutcome` → string)
- Modify: `app/src/main/java/com/cocode/babakcast/ui/settings/SettingsScreen.kt`: pass `ProviderTestRow` into `ProviderConfigDialog` above the Cancel/Save row; `DisposableEffect` resets the test VM when the dialog leaves.
- Modify: `values/strings.xml`, `values-da/strings.xml`

**Interfaces:**
- Consumes: `ProviderTestViewModel`, `ProviderTestInputs`, `ProviderTestState.outcomeFor` (Task 4)

- [ ] **Step 1: Implement the row.**
  - A "Test connection" button, disabled when the key is blank or a test runs, showing "Testing…" while running.
  - Under it, `outcomeFor(current)` as text: `BabakCastColors.Success` for `Works`, the error colour otherwise.
  - Strings (EN; DA alongside):
    - `provider_test` "Test connection"
    - `provider_test_running` "Testing…"
    - `provider_test_works` "Works. The provider answered in %1$s s."
    - `provider_test_unreadable` "Connected, but the reply couldn't be read. Try another model."
    - `provider_test_key_rejected` "The provider rejected this API key."
    - `provider_test_not_found` "The provider couldn't find this model or endpoint."
    - `provider_test_usage_limit` "Your provider's usage limit has been reached."
    - `provider_test_refused` "The provider refused the request (code %1$d). Check the model."
    - `provider_test_unreachable` "Can't reach the provider. Check your internet connection and the endpoint."
- [ ] **Step 2: Wire it** into the dialog as listed under Files.
- [ ] **Step 3: Run** `./gradlew testGithubDebugUnitTest testFdroidDebugUnitTest lintGithubDebug --no-daemon`, expect PASS.
- [ ] **Step 4: On the phone** (side-by-side debug build, `applicationIdSuffix` added temporarily and never committed; `adb install -r --user 0`):
  - With a fake key on OpenRouter, the result reads "rejected this API key".
  - Screenshot the dialog.
- [ ] **Step 5: Commit** `feat(settings): test the AI provider from its dialog`.

### Task 6: Docs

**Files:**
- Modify: `README.md`, `website/index.html`, `website/da/index.html`, `website/fa/index.html` (the AI provider setup step)

- [ ] **Step 1: Write it.** Say that *Test connection* checks the key, model and endpoint before saving, and list Anthropic, Google Gemini and Azure OpenAI next to OpenAI and OpenRouter, in each language.
- [ ] **Step 2: Check** with `rg -n "Test connection" README.md website`: one hit per page.
- [ ] **Step 3: Commit** `docs: provider test and the providers that now work`, then push and open the PR.
