# Privacy Policy — BabakCast

**App:** BabakCast (`com.cocode.babakcast`)
**Developer:** CoCode.dk — Babak Bandpey
**Last updated:** 7 October 2026

> The canonical, always-current version of this policy is published at
> **https://cast.cocode.dk/privacy/**

**BabakCast keeps your files on your phone and has no server of its own: the developer operates no server and receives no data about you. However, BabakCast is not an offline app. When you ask it to download media or summarize a transcript, it sends the content and links you provide directly to the third-party services you choose, over the internet.**

This policy explains exactly what leaves your device, where it goes, and what stays on your phone. BabakCast has no user accounts, no analytics, no advertising, and no tracking, and it does not ship with any API keys.

## 1. Content you send to AI providers

When you use the **summarize** or translate features, BabakCast sends the text you are processing — the video transcript (for example, YouTube captions) or other text, together with a fixed instruction prompt — to the **AI provider you have configured in Settings**. That content leaves your device and is transmitted, over HTTPS, to that provider's servers, where it is processed under *their* privacy policy and data-retention practices.

You choose the provider. BabakCast lists the following in Settings and sends your content only to the one you select. In this version OpenAI and OpenRouter work. Azure OpenAI, Anthropic and Google Gemini are listed but not finished yet.

- **OpenAI** — `api.openai.com`
- **Azure OpenAI** (Microsoft) — `*.openai.azure.com`. Not finished yet: this version cannot save the address of your own Azure resource, so the app uses a placeholder address instead.
- **Anthropic** — `api.anthropic.com` (not finished yet)
- **Google Gemini** — `generativelanguage.googleapis.com` (not finished yet)
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
- **X (Twitter)** — media downloads and tweet-text retrieval use X's public service for embedded posts (`cdn.syndication.twimg.com`) and X media servers; only the public tweet ID from your link is sent, with no login
- **Instagram / Meta** — video downloads
- **LinkedIn** — video downloads from public posts

Only the public link you provide is used; BabakCast does not log in to these platforms or send them your credentials. Each platform handles the resulting request under its own privacy policy:
[Google/YouTube](https://policies.google.com/privacy),
[X](https://x.com/en/privacy),
[Instagram](https://privacycenter.instagram.com/policy), and
[LinkedIn](https://www.linkedin.com/legal/privacy-policy).

## 3. Your API keys

You supply your own API key for each AI provider you enable. Keys are stored **on your device**, encrypted with Android's `EncryptedSharedPreferences` (AES-256, with the master key held in the Android Keystore). A key is **never sent to the developer**. It is transmitted only to its own provider's API, as the authorization credential, over HTTPS, when you list models or make a request. If Android backup is on, Android may also copy the encrypted preferences file into your own Google backup (see section 8). Keys are shown masked in the app and can be deleted at any time in Settings.

## 4. Updates

BabakCast does not check for updates to itself. The **See the latest version** button on the About screen only opens a web page in your browser when you tap it (see section 9).

The version you download from GitHub does keep its media downloader up to date. That downloader is yt-dlp, the open-source tool that fetches the videos. When the app starts, it asks GitHub whether there is a newer yt-dlp and may download it. After a successful check it does not ask again until the next day. The request carries no links, no content and no personal data beyond the standard connection information (such as your IP address) that any web request includes. The F-Droid version never does this on its own. It asks GitHub for a newer yt-dlp only when you tap **Update yt-dlp** in Settings, and the request carries the same standard connection information and nothing else. Without that tap, its downloader stays the one that came with the app. GitHub's handling is covered by the [GitHub Privacy Statement](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).

## 5. Data stored on your device

Everything BabakCast creates is stored locally, in the app's own storage, and is removed when you uninstall the app or clear its data:

- **Downloaded media and transcripts** — videos, images, audio, and extracted transcript files, saved in the app's private external files directory.
- **Settings** — your preferences (default provider, language, summary style and length, theme, temperature) in a local preferences store. These contain no personal content.
- **API keys** — encrypted, as described above.

BabakCast keeps no analytics database and no usage history. It writes diagnostic messages to Android's system log on your phone. These can include the media links you paste, tweet IDs, and short excerpts of the text sent to your AI provider and of its replies. BabakCast does not send them to anyone, and Android overwrites old entries as new ones arrive. BabakCast does not read files elsewhere on your device except the links and shared text you explicitly give it.

When you share a file, BabakCast gives the app you pick permission to read it. That app may keep its own copy. BabakCast cannot delete that copy, and uninstalling BabakCast does not remove it.

## 6. Permissions

- **Internet** and **network state** — required to reach the services above and to detect your connection type.
- No location, contacts, camera, or microphone permissions are requested.

## 7. No tracking, no accounts, no ads

- No analytics, crash reporting, or advertising SDKs are included.
- No cookies, no advertising identifiers, and no user accounts.
- The developer runs no server and receives none of your data.

## 8. Device backup

BabakCast does not switch Android backup off or leave anything out of it. If backup is on for your phone, Android may include the app's settings, the encrypted file that holds your API keys, and the downloaded media and transcripts in your own personal Google backup. This is controlled entirely by you and Google, and the developer has no access to it. See [Google's Privacy Policy](https://policies.google.com/privacy) for details.

## 9. External links

BabakCast opens a web page only when you tap a link in the app, and it hands the page to your web browser. The About screen has buttons for these pages: the latest version (the GitHub releases page, or the F-Droid page once the app is listed there), this privacy policy, the BabakCast website ([cast.cocode.dk](https://cast.cocode.dk)), the source code and the problem tracker on GitHub, and [cocode.dk](https://cocode.dk). BabakCast itself does not connect to these pages: your browser does, and each site is governed by its own privacy policy.

## 10. Children

The app does not knowingly collect data from anyone, including children.

## 11. Changes

If this policy changes, the updated version will be posted here and on the website with a new "last updated" date.

## 12. Contact

Questions about this policy can be sent to **bb@cocode.dk** (CoCode.dk, developer: Babak Bandpey).
