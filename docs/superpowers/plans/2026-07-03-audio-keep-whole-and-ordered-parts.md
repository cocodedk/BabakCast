# Keep-whole Audio + Order-proof Split Parts — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give audio extraction a "keep as one file" option (parity with video) and make split audio parts self-describing so recipients can tell the order even when a messaging app scrambles delivery.

**Architecture:** Add a whole-file audio path in `MainViewModel` reusing the existing `SplitMode.NONE` engine; write ID3 "Part n of N" tags onto split parts in a best-effort post-split pass; state the part count in the shared caption. New logic lives in small pure/injectable units so it is unit-testable; FFmpeg execution is verified on-device.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, ffmpeg-kit, JUnit4 (`org.junit.Assert.*`).

## Global Constraints

- Kotlin, Clean Architecture: `ui/`→`domain/` only; `domain/` imports neither `ui/` nor `data/`.
- 200-line max per **new** file. `MainViewModel.kt` (740) / `AudioSplitter.kt` (318) / `ActionButtonsSection.kt` (365) are pre-existing violations — do NOT broadly refactor them; keep additions minimal and push new logic into new files.
- Immutable models; `copy()` for state mutation; single `MainUiState` source of truth.
- Conventional Commits (commit-msg hook). Pre-commit hook runs `./gradlew buildSmoke --no-daemon` — every commit builds + tests.
- Match the existing hardcoded-UI-string pattern in `ActionButtonsSection` (the codebase does not use string resources for these labels); a11n/i18n of these labels is out of scope.
- Split size default 16 MB (`SplitSize.DEFAULT_MB`); slider range 5–100 MB.
- Filenames unchanged: keep the `_partNNNN` scheme (`DownloadFileParser`).

**Test command (single class):**
`./gradlew testDebugUnitTest --no-daemon --tests "com.cocode.babakcast.<pkg>.<Class>"`

---

### Task 1: Share caption with part count

**Files:**
- Create: `app/src/main/java/com/cocode/babakcast/util/AudioShareCaption.kt`
- Test: `app/src/test/java/com/cocode/babakcast/util/AudioShareCaptionBuilderTest.kt`

**Interfaces:**
- Produces: `AudioShareCaption.build(title: String, partCount: Int): String`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.cocode.babakcast.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioShareCaptionBuilderTest {
    @Test fun singlePart_returnsPlainTitle() {
        assertEquals("My Episode", AudioShareCaption.build("My Episode", 1))
    }

    @Test fun multiPart_appendsCountAndOrderHint() {
        assertEquals("My Episode — 5 parts, play in order", AudioShareCaption.build("My Episode", 5))
    }

    @Test fun blankTitle_fallsBackToAudio() {
        assertEquals("Audio", AudioShareCaption.build("   ", 1))
        assertEquals("Audio — 3 parts, play in order", AudioShareCaption.build("", 3))
    }

    @Test fun zeroOrNegativeParts_treatedAsSingle() {
        assertEquals("My Episode", AudioShareCaption.build("My Episode", 0))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --no-daemon --tests "com.cocode.babakcast.util.AudioShareCaptionBuilderTest"`
Expected: FAIL — unresolved reference `AudioShareCaption`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.cocode.babakcast.util

/** Builds the human-facing caption shared alongside extracted audio. */
object AudioShareCaption {
    fun build(title: String, partCount: Int): String {
        val base = title.ifBlank { "Audio" }
        return if (partCount > 1) "$base — $partCount parts, play in order" else base
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --no-daemon --tests "com.cocode.babakcast.util.AudioShareCaptionBuilderTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cocode/babakcast/util/AudioShareCaption.kt \
        app/src/test/java/com/cocode/babakcast/util/AudioShareCaptionBuilderTest.kt
git commit -m "feat(audio): add part-count share caption builder"
```

---

### Task 2: FFmpeg metadata command + sanitizer

**Files:**
- Modify: `app/src/main/java/com/cocode/babakcast/domain/FfmpegCommands.kt`
- Test: `app/src/test/java/com/cocode/babakcast/domain/FfmpegCommandsMetadataTest.kt`

**Interfaces:**
- Produces: `FfmpegCommands.buildAddMetadataCommand(inputFile: File, outputFile: File, title: String, track: String, album: String): String`
- Produces: `FfmpegCommands.sanitizeMetadataValue(value: String): String`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.cocode.babakcast.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FfmpegCommandsMetadataTest {
    @Test fun buildAddMetadataCommand_includesAllTagsAndCopyCodec() {
        val cmd = FfmpegCommands.buildAddMetadataCommand(
            inputFile = File("/in/part.mp3"),
            outputFile = File("/out/part.mp3"),
            title = "Ep (Part 1 of 3)",
            track = "1/3",
            album = "Ep"
        )
        assertTrue(cmd.contains("-c copy"))
        assertTrue(cmd.contains("-metadata title=\"Ep (Part 1 of 3)\""))
        assertTrue(cmd.contains("-metadata track=\"1/3\""))
        assertTrue(cmd.contains("-metadata album=\"Ep\""))
        assertTrue(cmd.contains("\"/out/part.mp3\""))
    }

    @Test fun blankTag_omitsThatMetadataFlag() {
        val cmd = FfmpegCommands.buildAddMetadataCommand(
            inputFile = File("/in/p.mp3"),
            outputFile = File("/out/p.mp3"),
            title = "T", track = "1/2", album = "  "
        )
        assertFalse(cmd.contains("album"))
    }

    @Test fun sanitize_stripsQuotesBackslashesAndNewlines() {
        assertEquals("It's a 'test' line", FfmpegCommands.sanitizeMetadataValue("It\"s a \"test\"\nline"))
        assertEquals("a b", FfmpegCommands.sanitizeMetadataValue("a\\b"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --no-daemon --tests "com.cocode.babakcast.domain.FfmpegCommandsMetadataTest"`
Expected: FAIL — unresolved reference `buildAddMetadataCommand`.

- [ ] **Step 3: Write minimal implementation**

Add inside `object FfmpegCommands` (after `buildCopySegmentCommand`):

```kotlin
    fun buildAddMetadataCommand(
        inputFile: File,
        outputFile: File,
        title: String,
        track: String,
        album: String
    ): String {
        return "-i \"${inputFile.absolutePath}\" " +
            "-map_metadata 0 " +
            "-c copy " +
            "-id3v2_version 3 " +
            metadataArg("title", title) +
            metadataArg("track", track) +
            metadataArg("album", album) +
            "-y " +
            "\"${outputFile.absolutePath}\""
    }

    private fun metadataArg(key: String, value: String): String {
        val clean = sanitizeMetadataValue(value)
        return if (clean.isBlank()) "" else "-metadata $key=\"$clean\" "
    }

    fun sanitizeMetadataValue(value: String): String {
        return value
            .replace("\\", " ")
            .replace("\"", "'")
            .replace("\n", " ")
            .replace("\r", " ")
            .trim()
    }
```

Note: `FfmpegCommands` is `internal`; the test in the same module and package can access it.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --no-daemon --tests "com.cocode.babakcast.domain.FfmpegCommandsMetadataTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cocode/babakcast/domain/FfmpegCommands.kt \
        app/src/test/java/com/cocode/babakcast/domain/FfmpegCommandsMetadataTest.kt
git commit -m "feat(audio): add ffmpeg metadata command + value sanitizer"
```

---

### Task 3: AudioPartTagger (pure metadata + best-effort tag writer)

**Files:**
- Create: `app/src/main/java/com/cocode/babakcast/domain/audio/AudioPartTagger.kt`
- Test: `app/src/test/java/com/cocode/babakcast/domain/audio/AudioPartTaggerMetadataTest.kt`

**Interfaces:**
- Consumes: `FfmpegCommands.buildAddMetadataCommand(...)` (Task 2)
- Produces: `AudioPartTagger.partMetadata(displayTitle: String, partIndex: Int, totalParts: Int): AudioPartTagger.PartMetadata` (pure, tested)
- Produces: `AudioPartTagger.tagParts(files: List<File>, displayTitle: String): List<File>` (I/O, device-verified)

- [ ] **Step 1: Write the failing test** (pure part only)

```kotlin
package com.cocode.babakcast.domain.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioPartTaggerMetadataTest {
    @Test fun partMetadata_buildsTitleTrackAlbum() {
        val m = AudioPartTagger.partMetadata("My Episode", 2, 5)
        assertEquals("My Episode (Part 2 of 5)", m.title)
        assertEquals("2/5", m.track)
        assertEquals("My Episode", m.album)
    }

    @Test fun partMetadata_blankTitle_titleIsLabelOnlyAndAlbumBlank() {
        val m = AudioPartTagger.partMetadata("  ", 1, 3)
        assertEquals("Part 1 of 3", m.title)
        assertEquals("1/3", m.track)
        assertEquals("", m.album)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --no-daemon --tests "com.cocode.babakcast.domain.audio.AudioPartTaggerMetadataTest"`
Expected: FAIL — unresolved reference `AudioPartTagger`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.cocode.babakcast.domain.audio

import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.cocode.babakcast.domain.FfmpegCommands
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Writes ID3 "Part n of N" tags onto split audio parts. Best-effort: a failed
 *  write leaves that part untagged rather than failing the whole share. */
@Singleton
class AudioPartTagger @Inject constructor() {

    data class PartMetadata(val title: String, val track: String, val album: String)

    companion object {
        private const val TAG = "AudioPartTagger"

        fun partMetadata(displayTitle: String, partIndex: Int, totalParts: Int): PartMetadata {
            val album = displayTitle.trim()
            val label = "Part $partIndex of $totalParts"
            val title = if (album.isBlank()) label else "$album ($label)"
            return PartMetadata(title = title, track = "$partIndex/$totalParts", album = album)
        }
    }

    fun tagParts(files: List<File>, displayTitle: String): List<File> {
        val total = files.size
        files.forEachIndexed { index, file ->
            val meta = partMetadata(displayTitle, index + 1, total)
            runCatching { writeTags(file, meta) }
                .onFailure { Log.w(TAG, "tagParts failed name=${file.name}", it) }
        }
        return files
    }

    private fun writeTags(file: File, meta: PartMetadata) {
        val dir = file.parentFile ?: return
        val temp = File(dir, "${file.nameWithoutExtension}.tagged.${file.extension}")
        val command = FfmpegCommands.buildAddMetadataCommand(
            inputFile = file,
            outputFile = temp,
            title = meta.title,
            track = meta.track,
            album = meta.album
        )
        val session = FFmpegKit.execute(command)
        val ok = ReturnCode.isSuccess(session.returnCode) && temp.exists() && temp.length() > 0
        if (ok && file.delete()) {
            temp.renameTo(file)
        } else {
            temp.delete()
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --no-daemon --tests "com.cocode.babakcast.domain.audio.AudioPartTaggerMetadataTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cocode/babakcast/domain/audio/AudioPartTagger.kt \
        app/src/test/java/com/cocode/babakcast/domain/audio/AudioPartTaggerMetadataTest.kt
git commit -m "feat(audio): add AudioPartTagger for ID3 part-of-N tags"
```

---

### Task 4: Tag split parts inside AudioSplitter

**Files:**
- Modify: `app/src/main/java/com/cocode/babakcast/domain/audio/AudioSplitter.kt`

**Interfaces:**
- Consumes: `AudioPartTagger.tagParts(...)` (Task 3)
- Produces: `AudioSplitter.splitAudioIfNeeded(audioFile, chapterHints, splitMode, chunkSizeBytes, displayTitle: String = "", onProgress)` — new `displayTitle` param (defaulted → existing callers/tests unaffected).

- [ ] **Step 1: Inject the tagger.** Change the constructor:

```kotlin
class AudioSplitter @Inject constructor(
    private val partTagger: AudioPartTagger
) {
```

- [ ] **Step 2: Add the `displayTitle` param** to `splitAudioIfNeeded` (insert before `onProgress`):

```kotlin
        chunkSizeBytes: Long = MAX_CHUNK_SIZE_BYTES,
        displayTitle: String = "",
        onProgress: ((currentPart: Int, totalParts: Int) -> Unit)? = null
```

- [ ] **Step 3: Tag after a successful multi-part split.** Replace the `when (splitMode) { ... }` block (the last expression in the `try`) with:

```kotlin
            val splitResult = when (splitMode) {
                SplitMode.NONE -> error("NONE should have been skipped by SplitDecision")
                SplitMode.CHAPTERS -> splitByChapters(
                    audioFile = audioFile,
                    outputDir = outputDir,
                    baseName = baseName,
                    outputExtension = outputExtension,
                    sourceSize = sourceSize,
                    duration = duration,
                    chapterHints = chapterHints,
                    onProgress = onProgress
                )
                SplitMode.BY_SIZE -> splitBySize(
                    audioFile = audioFile,
                    outputDir = outputDir,
                    baseName = baseName,
                    outputExtension = outputExtension,
                    sourceSize = sourceSize,
                    duration = duration,
                    bytesPerSecond = bytesPerSecond,
                    chunkSizeBytes = chunkSizeBytes,
                    onProgress = onProgress
                )
            }
            splitResult.map { files ->
                if (files.size > 1) partTagger.tagParts(files, displayTitle) else files
            }
```

- [ ] **Step 4: Verify existing splitter tests still compile/pass** (the new ctor arg + defaulted param):

Run: `./gradlew testDebugUnitTest --no-daemon --tests "com.cocode.babakcast.domain.audio.*" --tests "com.cocode.babakcast.domain.split.*"`
Expected: PASS (Hilt supplies `AudioPartTagger`; unit tests that construct `AudioSplitter()` directly, if any, will need `AudioSplitter(AudioPartTagger())` — update them if the compile fails).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cocode/babakcast/domain/audio/AudioSplitter.kt
git commit -m "feat(audio): tag split parts with Part n of N metadata"
```

---

### Task 5: MainViewModel — whole vs split audio + ordered caption

**Files:**
- Modify: `app/src/main/java/com/cocode/babakcast/ui/main/MainViewModel.kt`

**Interfaces:**
- Consumes: `AudioShareCaption.build(...)` (Task 1); `AudioSplitter.splitAudioIfNeeded(... displayTitle ...)` (Task 4).
- Produces: `MainViewModel.downloadAudio()` (whole), `MainViewModel.downloadSplitAudio()` (split).

- [ ] **Step 1: Add import** near the other util imports:

```kotlin
import com.cocode.babakcast.util.AudioShareCaption
```

- [ ] **Step 2: Replace the whole `fun downloadAudio() { ... }` method** (current lines ~252–360) with the split entry points + shared prologue:

```kotlin
    fun downloadAudio() = startAudioDownload { videoInfo, videoFile, audioFile ->
        splitAndShareAudio(videoInfo, videoFile, audioFile, SplitMode.NONE)
    }

    fun downloadSplitAudio() = startAudioDownload { videoInfo, videoFile, audioFile ->
        val audioNeedsSplit = !SplitDecision.skipFor(
            mode = SplitMode.BY_SIZE,
            fileSizeBytes = audioFile.length(),
            chunkSizeBytes = _uiState.value.splitSizeBytes
        )
        if (audioNeedsSplit && videoInfo.chapters.isNotEmpty()) {
            pendingSplitRequest = PendingSplitRequest.Audio(videoInfo, videoFile, audioFile)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isDownloadingAudio = false,
                loadingMessage = null,
                isProgressIndeterminate = false,
                splitChoicePrompt = SplitChoicePrompt(
                    mediaType = SplitChoiceMediaType.AUDIO,
                    chapterCount = videoInfo.chapters.size
                )
            )
        } else {
            splitAndShareAudio(videoInfo, videoFile, audioFile, SplitMode.BY_SIZE)
        }
    }

    private fun startAudioDownload(onAudioReady: suspend (VideoInfo, File, File) -> Unit) {
        val url = _uiState.value.url
        if (!_uiState.value.downloadEngineReady) return
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(
                error = AppError.InvalidUrl("Please enter a YouTube, X, or Instagram URL")
            )
            return
        }

        pendingSplitRequest = null
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                progress = 0f,
                isDownloading = false,
                isSummarizing = false,
                isDownloadingAudio = true,
                loadingMessage = "Downloading source video...",
                isProgressIndeterminate = false,
                splitChoicePrompt = null
            )

            mediaRepository.downloadVideo(url) { progress ->
                _uiState.value = _uiState.value.copy(progress = progress)
            }.fold(
                onSuccess = { videoInfo ->
                    val videoFile = videoInfo.file
                    if (videoFile == null || !videoFile.exists()) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = AppError.DownloadFailed("Downloaded video file not found"),
                            isDownloadingAudio = false,
                            loadingMessage = null,
                            isProgressIndeterminate = false
                        )
                        return@fold
                    }
                    _uiState.value = _uiState.value.copy(
                        loadingMessage = "Extracting audio...",
                        isProgressIndeterminate = true
                    )
                    audioExtractor.extractAudio(videoFile).fold(
                        onSuccess = { audioFile -> onAudioReady(videoInfo, videoFile, audioFile) },
                        onFailure = { error ->
                            Log.e(tag, "downloadAudio extract failed", error)
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = AppError.AudioExtractFailed(error.message ?: "Audio extraction failed"),
                                isDownloadingAudio = false,
                                loadingMessage = null,
                                isProgressIndeterminate = false
                            )
                        }
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = ErrorHandler.handleException(error),
                        isDownloadingAudio = false,
                        loadingMessage = null,
                        isProgressIndeterminate = false
                    )
                }
            )
        }
    }
```

- [ ] **Step 3: Replace `splitAndShareAudio` body** to short-circuit `NONE`/under-cap and route all sharing through a helper. Replace the current `private suspend fun splitAndShareAudio(...) { ... }` (lines ~637–720) with:

```kotlin
    private suspend fun splitAndShareAudio(
        videoInfo: VideoInfo,
        videoFile: File,
        audioFile: File,
        splitMode: SplitMode
    ) {
        val chunkSizeBytes = _uiState.value.splitSizeBytes
        if (SplitDecision.skipFor(splitMode, audioFile.length(), chunkSizeBytes)) {
            shareAudioFiles(videoInfo, videoFile, listOf(audioFile))
            return
        }

        _uiState.value = _uiState.value.copy(
            progress = 0f,
            loadingMessage = splitMode.splittingMessage("audio"),
            isProgressIndeterminate = false
        )

        audioSplitter.splitAudioIfNeeded(
            audioFile = audioFile,
            chapterHints = videoInfo.chapters,
            splitMode = splitMode,
            chunkSizeBytes = chunkSizeBytes,
            displayTitle = videoInfo.title
        ) { currentPart, totalParts ->
            val denominator = max(totalParts, currentPart).toFloat().coerceAtLeast(1f)
            _uiState.value = _uiState.value.copy(
                progress = (currentPart / denominator).coerceIn(0f, 1f),
                loadingMessage = splitMode.splittingProgressMessage("audio", currentPart, totalParts)
            )
        }.fold(
            onSuccess = { audioFiles -> shareAudioFiles(videoInfo, videoFile, audioFiles) },
            onFailure = { error ->
                Log.e(tag, "downloadAudio split failed", error)
                if (splitMode == SplitMode.CHAPTERS && isChapterTooLargeError(error)) {
                    pendingSplitRequest = PendingSplitRequest.Audio(videoInfo, videoFile, audioFile)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = ErrorHandler.handleException(error),
                        isDownloadingAudio = false,
                        loadingMessage = null,
                        isProgressIndeterminate = false,
                        splitChoicePrompt = SplitChoicePrompt(
                            mediaType = SplitChoiceMediaType.AUDIO,
                            chapterCount = videoInfo.chapters.size
                        )
                    )
                    return@fold
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = ErrorHandler.handleException(error),
                    isDownloadingAudio = false,
                    loadingMessage = null,
                    isProgressIndeterminate = false,
                    splitChoicePrompt = null
                )
            }
        )
    }

    private suspend fun shareAudioFiles(
        videoInfo: VideoInfo,
        videoFile: File,
        audioFiles: List<File>
    ) {
        val details = audioFiles.joinToString { "${it.name}:${it.length()}" }
        Log.d(tag, "downloadAudio share partCount=${audioFiles.size} parts=[$details]")
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            isDownloadingAudio = false,
            loadingMessage = null,
            isProgressIndeterminate = false,
            splitChoicePrompt = null
        )
        _shareRequests.emit(
            ShareRequest.AudioTwoStep(
                caption = AudioShareCaption.build(videoInfo.title, audioFiles.size),
                files = audioFiles,
                mimeType = "audio/mpeg",
                title = "Share audio"
            )
        )
        if (videoFile.exists()) {
            videoFile.delete()
        }
    }
```

- [ ] **Step 4: Build to verify compilation**

Run: `./gradlew testDebugUnitTest --no-daemon --tests "com.cocode.babakcast.util.*"`
Expected: PASS (and MainViewModel compiles). No new ViewModel unit test — its logic now delegates to the tested pure helpers (`AudioShareCaption`, `SplitDecision`, `AudioPartTagger`); wiring is device-verified in Task 8.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cocode/babakcast/ui/main/MainViewModel.kt
git commit -m "feat(audio): add keep-whole audio path + ordered-part caption"
```

---

### Task 6: UI — "Download Audio Split" button

**Files:**
- Create: `app/src/main/java/com/cocode/babakcast/ui/main/AudioActionButtons.kt`
- Modify: `app/src/main/java/com/cocode/babakcast/ui/main/ActionButtonsSection.kt`
- Modify: `app/src/main/java/com/cocode/babakcast/ui/main/MainScreen.kt`

**Interfaces:**
- Consumes: `MainViewModel.downloadAudio()`, `MainViewModel.downloadSplitAudio()` (Task 5).
- Produces: `ActionButtonsSection(... onDownloadSplitAudio: () -> Unit ...)`; `AudioActionButtons(uiState, onDownloadAudio, onDownloadSplitAudio)`.

**Note:** Invoke `frontend-design:frontend-design` before editing — the new button must mirror the existing "Download Split (X MB)" video button (outlined, `PrimaryAccent`, 52.dp). Extracting the audio block into `AudioActionButtons` keeps `ActionButtonsSection` from growing past its already-large size.

- [ ] **Step 1: Create `AudioActionButtons.kt`** — the two audio buttons (whole + split), lifted verbatim from the current "Download Audio" button plus the new split button:

```kotlin
package com.cocode.babakcast.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocode.babakcast.ui.theme.BabakCastColors

@Composable
internal fun AudioActionButtons(
    uiState: MainUiState,
    onDownloadAudio: () -> Unit,
    onDownloadSplitAudio: () -> Unit
) {
    val audioEnabled = uiState.downloadEngineReady && !uiState.isLoading && uiState.url.isNotBlank()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = onDownloadAudio,
            enabled = audioEnabled,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            ),
            border = ButtonDefaults.outlinedButtonBorder(enabled = audioEnabled).copy(
                brush = SolidColor(
                    if (audioEnabled) MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.isDownloadingAudio) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = BabakCastColors.PrimaryAccent
                    )
                }
                Text(
                    if (uiState.isDownloadingAudio) "Downloading Audio…" else "Download Audio",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                )
            }
        }

        OutlinedButton(
            onClick = onDownloadSplitAudio,
            enabled = audioEnabled,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = BabakCastColors.PrimaryAccent,
                disabledContentColor = BabakCastColors.PrimaryAccent.copy(alpha = 0.3f)
            ),
            border = ButtonDefaults.outlinedButtonBorder(enabled = audioEnabled).copy(
                brush = SolidColor(
                    if (audioEnabled) BabakCastColors.PrimaryAccent.copy(alpha = 0.5f)
                    else BabakCastColors.PrimaryAccent.copy(alpha = 0.2f)
                )
            )
        ) {
            Text(
                "Download Audio Split (${uiState.splitSizeMb} MB)",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            )
        }
    }
}
```

- [ ] **Step 2: In `ActionButtonsSection.kt`, add the parameter** to the signature (after `onDownloadAudio`):

```kotlin
    onDownloadAudio: () -> Unit,
    onDownloadSplitAudio: () -> Unit,
```

- [ ] **Step 3: In `ActionButtonsSection.kt`, replace the whole `val audioEnabled = ... OutlinedButton(onClick = onDownloadAudio) { ... }` block** (the current single audio button, ~lines 216–257) with:

```kotlin
        AudioActionButtons(
            uiState = uiState,
            onDownloadAudio = onDownloadAudio,
            onDownloadSplitAudio = onDownloadSplitAudio
        )
```

Remove any now-unused imports flagged by the compiler.

- [ ] **Step 4: In `MainScreen.kt`, wire the callback** in the `ActionButtonsSection(...)` call (after `onDownloadAudio = viewModel::downloadAudio,`):

```kotlin
                    onDownloadAudio = viewModel::downloadAudio,
                    onDownloadSplitAudio = viewModel::downloadSplitAudio,
```

- [ ] **Step 5: Build + run the suite**

Run: `./gradlew testDebugUnitTest --no-daemon` then `./gradlew assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/cocode/babakcast/ui/main/AudioActionButtons.kt \
        app/src/main/java/com/cocode/babakcast/ui/main/ActionButtonsSection.kt \
        app/src/main/java/com/cocode/babakcast/ui/main/MainScreen.kt
git commit -m "feat(audio): add Download Audio Split button (keep-whole default)"
```

---

### Task 7: Docs — README + website (EN + FA)

**Files:**
- Modify: `README.md`
- Modify: website source (locate first: `docs/` Pages site and its FA counterpart — grep for the current audio feature copy).

- [ ] **Step 1: Locate the website copy**

Run: `grep -rniE "download audio|extract audio|split" README.md docs --include=*.md --include=*.html -l`

- [ ] **Step 2: Update README** feature list to describe: (a) "Download Audio" keeps one MP3; (b) "Download Audio Split (X MB)" splits and tags each part "Part n of N" so recipients can play them in order.

- [ ] **Step 3: Update the English website** with the same two-line description.

- [ ] **Step 4: Update the Farsi website** with a matching translation (mirror the EN change 1:1; keep the "Part n of N" concept).

- [ ] **Step 5: Commit**

```bash
git add README.md docs
git commit -m "docs(audio): document keep-whole audio + ordered split parts (EN/FA)"
```

---

### Task 8: Verify, review, ship

- [ ] **Step 1: Full suite + smoke**

Run: `./gradlew testDebugUnitTest --no-daemon && ./gradlew buildSmoke --no-daemon`
Expected: all green; no regressions in `AudioShareOrderTest`, `AudioShareCaptionTest`, `DownloadFileParserPartNameTest`, `SplitModeTest`, `SplitDecisionTest`, `VideoSplitterNoSplitTest`.

- [ ] **Step 2: Install on the connected device**

Run: `./gradlew installDebug --no-daemon` (confirm `adb devices` shows the device first).

- [ ] **Step 3: Device — whole path.** Paste a long YouTube URL → tap **Download Audio** → expect a single MP3 in the share sheet, no split, caption = title.

- [ ] **Step 4: Device — split path.** Same URL → tap **Download Audio Split (16 MB)** → expect multiple parts; open two parts in a player and confirm each shows "Part n of N" (ID3 title/track); caption reads "… — N parts, play in order".

- [ ] **Step 5: Code review.** Invoke `superpowers:requesting-code-review`; address findings via `superpowers:receiving-code-review`.

- [ ] **Step 6: Simplify.** Run `/simplify` (or `simplify` skill) over the diff; apply quality fixes.

- [ ] **Step 7: Verification-before-completion.** Invoke `superpowers:verification-before-completion` — paste the passing test + build output as evidence.

- [ ] **Step 8: Push + PR.**

```bash
git push -u origin feat/audio-whole-and-ordered-parts
gh pr create --fill --base main
```
(Ensure `gh` is authenticated as `cocodedk` first.)

---

## Self-Review

**Spec coverage:**
- Part 1 keep-whole → Tasks 5 (VM paths) + 6 (button). ✓
- Part 2 ID3 tags → Tasks 2 (command) + 3 (tagger) + 4 (wiring). ✓
- Part 2 caption count → Tasks 1 + 5. ✓
- Filenames unchanged → no task touches `DownloadFileParser`/naming. ✓
- Audio-only scope → no task touches `VideoSplitter`. ✓
- Docs (README + EN/FA) → Task 7. Tests → Tasks 1–4. Device verify + review + PR → Task 8. ✓

**Placeholder scan:** none — every code step has complete code.

**Type consistency:** `build(title, partCount)`, `buildAddMetadataCommand(inputFile, outputFile, title, track, album)`, `sanitizeMetadataValue(value)`, `partMetadata(displayTitle, partIndex, totalParts)`, `PartMetadata(title, track, album)`, `tagParts(files, displayTitle)`, `splitAudioIfNeeded(... displayTitle ...)`, `downloadSplitAudio()` — used consistently across tasks. ✓
