## 1. BabakCast App — Look & Feel

### Design goals (anchor these)

* Tool, not social app
* Calm, focused, technical
* No visual noise
* Looks trustworthy with API keys

Think: **“developer utility that happens to be on Android.”**

---

## 2. Color Palette (Dark-first, light optional)

### Primary Theme (recommended)

**Background**

* Primary: `#0F1115` (near-black, not pure black)
* Surface: `#161A22`
* Card: `#1E2430`

**Primary Accent**

* **Amber-gold:** `#F4B860`

  * Warm, readable
  * Stands out without screaming
  * Works well with dark backgrounds

**Secondary Accent**

* Cool blue: `#4DA3FF`

  * Used sparingly (links, secondary actions)

**Text**

* Primary: `#E6E8EB`
* Secondary: `#A0A4AB`
* Disabled: `#6B7280`

**Status colors**

* Success: `#4CAF50`
* Warning: `#F59E0B`
* Error: `#EF4444`

**Why this works**

* Neutral
* Long-session friendly
* Doesn’t feel like a downloader app
* Fits AI + dev tooling

---

## 3. Typography

### Font choice (Android-safe)

**Primary:**

* **Inter**

  * Clean
  * Neutral
  * Excellent readability

**Fallback:**

* Roboto (system default)

### Type scale (simple)

* Screen title: 20sp, semi-bold
* Section header: 16sp, medium
* Body text: 14sp, regular
* Helper text: 12sp

No fancy fonts. This isn’t a brand campaign.

---

## 4. Iconography

### Icon style

* Line icons
* Rounded corners
* Consistent stroke width

**Recommended icon set**

* Material Symbols (Outlined)

---

### App Icon (Important)

**Concept**

* Circular icon
* Dark background
* Stylized **broadcast waves** or **signal arcs**
* Center letter: **B**

**Colors**

* Background: `#161A22`
* Icon: `#F4B860`

**Why**

* Recognizable at small sizes
* Doesn’t reference YouTube explicitly
* Avoids copyright risk

---

## 5. App UI Layout

### Main Screen

**Top**

* App name: *BabakCast*
* Settings icon (top right)

**Body**

* URL input field (large, obvious)
* Two primary buttons:

  * 🎥 **Download Video**
  * 📝 **Summarize Transcript**

Buttons:

* Full width
* Rounded corners (12dp)
* Primary color for main action

---

### Settings Screen

Sections:

1. **AI Providers**
2. **Defaults**

   * Language
   * Summary style
3. **Storage**

   * Clear cache
4. **About**

   * GitHub link
   * Disclaimer

No clutter. No hidden menus.

---

## 6. Micro-UX Details (These matter)

* Show estimated token usage before AI call
* Disable buttons during processing
* Subtle loading indicator (not spinners everywhere)
* Toasts only for success/failure

Avoid animations unless they add clarity.

---

## 7. GitHub Page Design

This matters because this app lives on GitHub.

---

### Repository Name

```
babalcast
```

Lowercase. Clean.

---

### README.md Structure

#### Header

```
# BabakCast

A local-first Android tool to download YouTube videos,
summarize transcripts using your own AI provider,
and share results instantly.
```

---

### Badges (minimal)

* Android
* Kotlin
* Local-first
* BYO-AI

No CI badge unless you actually maintain it.

---

### Screenshots Section

* 3 images max

  * Main screen
  * Summary result
  * Provider settings

Dark theme screenshots only.

---

### Features Section (Short)

* YouTube video download & split
* Transcript summarization & translation
* Bring-your-own AI provider
* No backend
* No tracking

---

### Philosophy Section (This is important)

```
BabakCast is a personal-use tool.
It does not ship with API keys, ads, analytics, or accounts.
You control your data and your AI provider.
```

This builds trust immediately.

---

### Installation

* APK
* F-Droid (optional later)

Avoid Play Store language here.

---

### License

* MIT or Apache 2.0
  Don’t overthink it.

---

## 8. Visual Identity Summary (One-liner)

> **BabakCast looks like a quiet, sharp tool you trust with credentials.**

Not fun. Not flashy. **Serious.**
