# Design — Keep-whole audio + order-proof split parts

Date: 2026-07-03
Branch: `feat/audio-whole-and-ordered-parts`

## Problem

Two user complaints about the **Extract/Download Audio** flow:

1. **No way to avoid splitting.** "Download Audio" always splits by size once the
   extracted MP3 exceeds the split-size slider (default 16 MB ≈ 17 min at 128 kbps).
   Video has a whole-file button (`SplitMode.NONE`) *and* a split button; audio only
   has the split path. `SplitMode.NONE` already works end-to-end — it is simply never
   offered to the audio flow.

2. **Shared parts arrive in mixed order; unclear where to start.** The app already
   sorts parts correctly before sharing (`ShareHelper.normalizeShareFileOrder` +
   zero-padded `_partNNNN` names). The scramble happens **after** the handoff:
   `ACTION_SEND_MULTIPLE` order is not honored by receivers — WhatsApp uploads in
   parallel and delivers by upload-completion, ignoring both `EXTRA_STREAM` order and
   filenames. Compounding it, the parts carry **no visible "Part n of N" marker**: the
   ordinal is buried at the end of the filename as `part0001`, and the segments are cut
   with `-c copy` and **no ID3 tags**, so a player shows nothing useful either.

## Approach

### Part 1 — "Keep whole" audio (mirror the video buttons)

- `MainViewModel.downloadAudio()` becomes the **whole-file** action: download → extract
  → share one MP3 via `SplitMode.NONE` (no size check, no split prompt).
- New `MainViewModel.downloadSplitAudio()` carries **today's** behavior (size check →
  chapter dialog vs `BY_SIZE`).
- Extract the shared "download video + extract audio" prologue into a private helper
  `downloadAndExtractAudio(onReady)` so both entry points reuse it (DRY).
- `splitAndShareAudio` gains the same **`skipFor` short-circuit** that
  `splitAndShareVideo` already has, so `NONE` (and under-cap) shares the single file
  without ever reaching the splitter or the `NONE`-illegal "Splitting…" message.
- UI (`ActionButtonsSection`): primary **Download Audio** (whole) + a new outlined
  **Download Audio Split (X MB)** below it, reusing the existing split-size slider.
  Thread a new `onDownloadSplitAudio` callback through `MainScreen`.
- The chapter dialog is unchanged: it only appears on the explicit split path, where
  "keep whole" would be contradictory, so no third option is needed there.

### Part 2 — Order-proof split parts (audio only)

- **ID3 tags on every part** (both size and chapter modes), written in a **post-split
  pass** over the produced files so `n/N` is always correct regardless of the size-mode
  retry loop that can add a part beyond the pre-split estimate:
  - `track = "n/N"`, `title = "<episode> (Part n of N)"`, `album = "<episode>"`.
  - New injectable `AudioPartTagger` (keeps new logic out of the already-long
    `AudioSplitter.kt`; the splitter only gains a `displayTitle` param and one call).
  - New `FfmpegCommands.buildAddMetadataCommand(input, output, title, track, album)`
    using `-c copy -id3v2_version 3`, with a `sanitizeMetadataValue` helper that strips
    `"`/`\`/newlines so titles can't break the command string.
  - **Best-effort**: if a tag write fails, keep the original untagged file — tagging
    must never break the share.
- **Caption states the count.** New pure `AudioShareCaption.build(title, partCount)`:
  single part → just the title; multi-part → `"<title> — N parts, play in order"`.
  `MainViewModel` uses it when emitting `ShareRequest.AudioTwoStep`.
- **Filenames unchanged.** `_partNNNN` is load-bearing (Downloads screen groups,
  orders, and labels by it via `DownloadFileParser`; the share sorter relies on it) and
  already sorts correctly. Order-scrambling receivers ignore filenames anyway, so ID3
  is what actually helps them. Renaming = high risk (four call sites + tests), low gain.

### Scope boundaries

- **Audio only.** Video splitting is untouched (not the complaint).
- **No broad refactor.** `AudioSplitter.kt` is already >200 lines (pre-existing); new
  logic lives in new compliant files rather than growing it. A `/split-200` pass on the
  splitter is out of scope here.

## Data flow

```
Download Audio (whole):
  downloadAudio → downloadAndExtractAudio → splitAndShareAudio(NONE)
    → skipFor(NONE)=true → shareAudioFiles([audio]) → AudioTwoStep(caption=title)

Download Audio Split (X MB):
  downloadSplitAudio → downloadAndExtractAudio → needsSplit?
    → chapters present → SplitModeDialog (CHAPTERS | BY_SIZE)
    → else → splitAndShareAudio(BY_SIZE)
  splitAndShareAudio(mode) → AudioSplitter.splitAudioIfNeeded(mode, displayTitle)
    → per-mode segment cut → AudioPartTagger.tag(parts, title)  (n/N)
    → shareAudioFiles(parts) → AudioTwoStep(caption="title — N parts, play in order")
```

## Error handling

- Extraction / split failures: unchanged (existing `AppError` + chapter-too-large
  fallback to the split dialog).
- Tag-write failure: logged, original file kept untagged; share proceeds.
- Metadata values sanitized before entering the FFmpeg command string.

## Testing

Unit (JVM, pure — call production code, no duplicated logic):
- `AudioShareCaption.build` — single vs multi-part strings, blank title → "Audio".
- `FfmpegCommands.buildAddMetadataCommand` — includes `-metadata track/title/album`,
  `-c copy`, correct output path; `sanitizeMetadataValue` strips `"`/`\`/newline.
- Whole-vs-split decision: `downloadAudio` path never splits; existing
  `SplitDecision`/`SplitMode` tests stay green.

Existing suites to keep green: `AudioShareOrderTest`, `AudioShareCaptionTest`,
`DownloadFileParserPartNameTest`, `SplitChoiceDialogTest`, `SplitModeTest`,
`SplitDecisionTest`, `VideoSplitterNoSplitTest`.

Device (connected): (a) whole → one MP3, no split; (b) split on a >16 MB episode →
parts each showing "Part n of N" in a player, caption naming the count.

## Definition of done (per feature-shipping checklist)

Tests pass · README updated · website EN + FA updated · `buildSmoke` green ·
device-verified · code review · commit + push + PR.
