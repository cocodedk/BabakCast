# Privacy Policy — BabakCast

**App:** BabakCast (`com.cocode.babakcast`)
**Developer:** CoCode.dk — Babak Bandpey
**Last updated:** 14 July 2026

> The canonical, always-current version of this policy is published at
> **https://cocodedk.github.io/BabakCast/privacy.html**

**BabakCast is a local-first tool with no backend of its own: the developer operates no server and receives no data about you. However, BabakCast is not an offline app — when you ask it to download media or summarize a transcript, it sends the content and links you provide directly to the third-party services you choose, over the internet.**

This policy explains exactly what leaves your device, where it goes, and what stays on your phone. BabakCast has no user accounts, no analytics, no advertising, and no tracking, and it does not ship with any API keys.

## 1. Content you send to AI providers

When you use the **summarize** or translate features, BabakCast sends the text you are processing — the video transcript (for example, YouTube captions) or other text, together with a fixed instruction prompt — to the **AI provider you have configured in Settings**. That content leaves your device and is transmitted, over HTTPS, to that provider's servers, where it is processed under *their* privacy policy and data-retention practices.

You choose the provider. BabakCast supports the following, and sends your content only to the one you select:

- **OpenAI** — `api.openai.com`
- **Azure OpenAI** (Microsoft) — your `*.openai.azure.com` resource
- **Anthropic** — `api.anthropic.com`
- **Google Gemini** — `generativelanguage.googleapis.com`
- **OpenRouter** — `openrouter.ai`

These providers are independent data controllers. Their handling of your content is governed by their own policies:
[Anthropic](https://www.anthropic.com/legal/privacy),
[OpenAI](https://openai.com/policies/privacy-policy),
[Microsoft (Azure)](https://privacy.microsoft.com/privacystatement),
[Google](https://policies.google.com/privacy), and
[OpenRouter](https://openrouter.ai/privacy).
Please review the policy of the provider you use before sending sensitive content.

## 2. Links you paste and media downloads

To download a video, image, audio track, or transcript, BabakCast contacts the **source platform directly** using the link you paste. This means your device connects to, and reveals its IP address and the requested item to, whichever of these services the link belongs to:

- **YouTube / Google** — video, audio, and transcript downloads
- **X (Twitter)** — media downloads and tweet-text retrieval use X's public syndication endpoint (`cdn.syndication.twimg.com`) and X media servers; only the public tweet ID from your link is sent, with no login
- **Instagram / Meta** — video downloads
- **LinkedIn** — video downloads from public posts

Only the public link you provide is used; BabakCast does not log in to these platforms or send them your credentials. Each platform handles the resulting request under its own privacy policy:
[Google/YouTube](https://policies.google.com/privacy),
[X](https://x.com/en/privacy),
[Instagram](https://privacycenter.instagram.com/policy), and
[LinkedIn](https://www.linkedin.com/legal/privacy-policy).

## 3. Your API keys

You supply your own API key for each AI provider you enable. Keys are stored **only on your device**, encrypted with Android's `EncryptedSharedPreferences` (AES-256, with the master key held in the Android Keystore). A key is **never sent to the developer**. It is transmitted only to its own provider's API, as the authorization credential, over HTTPS, when you list models or make a request. Keys are shown masked in the app and can be deleted at any time in Settings.

## 4. Update checks

BabakCast can check for a newer version by requesting the latest release from the GitHub API (`api.github.com`). This request carries no personal data beyond the standard connection information (such as your IP address) that any web request includes. GitHub's handling is covered by the [GitHub Privacy Statement](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).

## 5. Data stored on your device

Everything BabakCast creates is stored locally, in the app's own storage, and is removed when you uninstall the app or clear its data:

- **Downloaded media and transcripts** — videos, images, audio, and extracted transcript files, saved in the app's private external files directory.
- **Settings** — your preferences (default provider, language, summary style and length, theme, temperature) in a local preferences store. These contain no personal content.
- **API keys** — encrypted, as described above.

BabakCast keeps no analytics database and no usage history, and it does not read files elsewhere on your device except the links and shared text you explicitly give it.

## 6. Permissions

- **Internet** and **network state** — required to reach the services above and to detect your connection type.
- **Read/write external storage** (Android 12 / API 32 and older only) — legacy permission used to save and share downloaded files on older devices. It is not requested on newer Android versions.
- No location, contacts, camera, or microphone permissions are requested.

## 7. No tracking, no accounts, no ads

- No analytics, crash reporting, or advertising SDKs are included.
- No cookies, no advertising identifiers, and no user accounts.
- The developer runs no server and receives none of your data.

## 8. Device backup

If you have enabled Android Auto Backup or Google account backup, the operating system may include this app's local data in your own personal Google backup. This is controlled entirely by you and Google, and the developer has no access to it. See [Google's Privacy Policy](https://policies.google.com/privacy) for details.

## 9. External links

The app contains links to the developer's website ([cocode.dk](https://cocode.dk)) and the project's GitHub page. Selecting them opens your browser; those sites are governed by their own privacy policies.

## 10. Children

The app does not knowingly collect data from anyone, including children.

## 11. Changes

If this policy changes, the updated version will be posted here and on the website with a new "last updated" date.

## 12. Contact

Questions about this policy can be sent to **bb@cocode.dk** (CoCode.dk, developer: Babak Bandpey).
