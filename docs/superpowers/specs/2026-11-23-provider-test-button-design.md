# Provider test button, and the provider fixes it exposes — design

Date: 2026-11-23. Approved by the owner in conversation ("yes, fix").

## Intent

The owner wants a button under Settings that sends a short query to the AI provider they set up,
so they know at once that the key and the endpoint are right, instead of finding out when a
summary or a translation fails.

Reading the code showed three providers that a test would fail today. The owner asked to fix
them in the same change:

- **Anthropic**: chat calls send no `anthropic-version` header (required) and put the system
  prompt inside `messages` (Anthropic wants a top-level `system`).
- **Google Gemini**: messages go out as `{role, content}` (Gemini wants `{role, parts:[{text}]}`,
  roles `user`/`model`, the system prompt in `systemInstruction`), and the paths
  `generationConfig.temperature` / `generationConfig.maxOutputTokens` are written as literal
  dotted keys, not nested objects.
- **Azure OpenAI**: the endpoint field in the dialog is never saved, so the placeholder URL stays.
- **Model**: Save skips the model when it equals the provider default, so an older saved model
  survives.

## Goals

- A green result means summaries and translation will work with these settings.
- A red result says, in plain words, what is wrong: the key, the model or endpoint, the usage
  limit, the network, or a reply the app can't read.
- Anthropic, Gemini and Azure work for summaries and translation.

## Non-goals

- No test from the provider list itself; the test lives where the values are typed.
- No change to timeouts, retries or the translation flow.
- No new providers, no custom-provider UI.

## Anti-goals

- A green result while summaries would still fail (a false green). So the test goes through the
  same request code as summaries, and it tests only values that Save persists.
- The test saving anything or switching the default provider.
- An API key in a log line.

## Approaches

1. **Chat request through `AIClient.makeRequest` (chosen).** One short message with the typed key,
   model and (Azure) endpoint. Same code path as summaries, so green means summaries work.
2. **The models-list endpoint.** Rejected: OpenRouter lists models without a key, and a working
   list proves nothing about chat requests.
3. **Test saved values from the provider list.** Rejected: the owner wants to check before relying
   on it; testing what is typed gives feedback before Save.

## Design

- **Where:** a "Test" button in the provider dialog, with the result in plain words under it.
- **What it sends:** one user message asking for a one-word reply, through `AIClient` with a key
  passed in (not read from storage), the typed model and the typed endpoint. Small output cap.
  Nothing is saved; the default provider is unchanged.
- **Result:**
  - HTTP 2xx and a readable reply → works, with the time it took.
  - HTTP 2xx, reply unreadable → connected, but the reply couldn't be read; try another model.
  - 401/403 → key rejected. 404 → model or endpoint not found. 429 or "quota" → usage limit.
    Other 4xx/5xx → the provider refused the request. Unknown host, timeout, no connection →
    can't reach the server.
- **Honest result:** the result belongs to the values it tested; it is hidden as soon as the key,
  model or endpoint changes, and cleared (and any running test cancelled) when the dialog closes.
- **Provider fixes:** a pure request builder per provider format (OpenAI-style, Anthropic, Gemini)
  plus Anthropic's version header; the Azure endpoint is persisted and applied like the selected
  model; Save stores the model even when it equals the default (or clears the override).
- **Files:** new code in new files. `SettingsScreen.kt` and `SettingsViewModel.kt` are already
  over 200 lines; they get only the wiring, not a refactor.
- **Docs:** strings in English and Danish; README; website EN, DA, FA.

## Left to the builder

- The exact prompt text, the output cap, and the HTTP-code-to-message table beyond the cases above.
- How the HTTP code reaches the classifier (for example as the cause of the existing
  `AppErrorException`), as long as `ErrorHandler` keeps mapping summaries' errors as today.

## Can't be verified here

No Anthropic, Gemini or Azure key is on this machine. Their request shapes are checked by unit
tests against the providers' documented formats; the owner's phone confirms them live.
