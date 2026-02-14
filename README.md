# BabakCast

A **local-first** Android app to download YouTube videos, summarize transcripts with your own AI provider, and share results instantly.

![Android](https://img.shields.io/badge/Android-3DDC84?style=flat&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=flat&logo=kotlin&logoColor=white)
![Local-first](https://img.shields.io/badge/Local--first-161A22?style=flat)
![BYO-AI](https://img.shields.io/badge/BYO--AI-FFB860?style=flat)

## Website
- [English](https://cocodedk.github.io/BabakCast/)
- [فارسی (Persian)](https://cocodedk.github.io/BabakCast/fa/)

---

## Features

- **YouTube video download** — Paste a URL, get shareable video files (auto-split at 16 MB for sharing limits).
- **YouTube audio download** — Extract audio (MP3) from a video and share audio files (auto-split at 16 MB for sharing limits).
- **Transcript summarization** — Extract captions and summarize with your chosen AI model (bullet points, paragraph, or TL;DR).
- **Bring-your-own AI** — OpenAI, Azure OpenAI, Anthropic, Google Gemini, OpenRouter. Configure API key and model in Settings.
- **No backend** — Everything runs on your device. No accounts, no analytics, no tracking.
- **Encrypted API keys** — Stored locally with Android's EncryptedSharedPreferences.

---

## Philosophy

> **BabakCast is a personal-use tool.**
> It does not ship with API keys, ads, analytics, or accounts.
> You control your data and your AI provider.

---

## Screenshots

| Main screen | Summary | Provider settings |
|-------------|---------|-------------------|
| *Add screenshot* | *Add screenshot* | *Add screenshot* |

*Dark theme. Add your own screenshots to `docs/screenshots/` and link here.*

---

## Installation

### From source

1. Clone the repo:
   ```bash
   git clone https://github.com/cocodedk/BabakCast.git
   cd BabakCast
   ```
2. Open in Android Studio and run on a device or emulator (API 26+).

### APK (when available)

- Download the latest release from [Releases](https://github.com/cocodedk/BabakCast/releases) and install on your Android device.

### Requirements

- Android 8.0 (API 26) or higher.
- For summarization: an API key from at least one supported provider (OpenAI, Anthropic, Gemini, OpenRouter, or Azure OpenAI).

---

## Usage

1. **Download video** — Paste a YouTube URL, tap *Download Video*. The app downloads the video, splits it if needed, and opens the share sheet.
2. **Download audio** — Paste a YouTube URL, tap *Download Audio*. The app extracts audio, splits it if needed, and opens the share sheet.
3. **Summarize transcript** — Paste a YouTube URL, tap *Summarize Transcript*. Configure an AI provider and model in **Settings → AI Providers** first. Your API key is stored locally and never sent anywhere except the provider you choose.

---

## Supported AI Providers

| Provider        | Config in app                          |
|----------------|----------------------------------------|
| OpenAI         | API key + model (e.g. gpt-4o-mini)     |
| Azure OpenAI  | API key + endpoint URL + model         |
| Anthropic      | API key + model (e.g. Claude 3.5)      |
| Google Gemini | API key + model (e.g. gemini-1.5-flash)|
| OpenRouter     | API key + model (e.g. openai/gpt-4o)   |

You can pick from suggested models or enter a custom model name.

---

## Release build (CI)

On every **push or merge to `main`**, GitHub Actions builds a **signed release APK** and uploads it as a workflow artifact.

To enable signing, add these **repository secrets** (Settings → Secrets and variables → Actions):

| Secret | Description |
|--------|-------------|
| `KEYSTORE_BASE64` | Your release keystore file, base64-encoded (e.g. `base64 -w 0 release.keystore`) |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

Create a keystore locally (once) with:

```bash
keytool -genkey -v -keystore release.keystore -alias my-key -keyalg RSA -keysize 2048 -validity 10000
```

Then encode it for `KEYSTORE_BASE64`: e.g. `base64 -w 0 release.keystore` (Linux) or `base64 -i release.keystore` (macOS). Do **not** commit the keystore file.

If the secrets are not set, the workflow will fail at the build step; set all four to get a signed APK from the [Actions](https://github.com/cocodedk/BabakCast/actions) run.

---

## Development

A pre-commit hook runs unit tests before each commit. Enable it once:

```bash
git config core.hooksPath .githooks
```

Or run `./scripts/install-hooks.sh`. Commits will be blocked if `./gradlew test` fails.

---

## Tech stack

- **Kotlin** + **Jetpack Compose**
- **Hilt** for dependency injection
- **youtubedl-android** for YouTube download & transcript
- **FFmpegKit** for video splitting
- **EncryptedSharedPreferences** for API key storage

---

## License

[Apache License 2.0](LICENSE). Use it, modify it, share it.

© 2026 [Cocode](https://cocode.dk) | BabakCast
