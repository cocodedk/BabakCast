# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --- youtubedl-android 0.18.1 (io.github.junkfood02.youtubedl-android:library) ---
# Verified by unzipping the AAR from the Gradle cache: it ships no proguard.txt or
# consumer-rules.txt, so R8 gets no guidance from the library itself. YoutubeDL.getInfo()
# binds yt-dlp's `--dump-json` output into these classes through Jackson's
# @JsonProperty/@JsonIgnoreProperties reflection, matched by field name. This app's
# YoutubeDlWrapper only calls execute()/updateYoutubeDL() today, not getInfo(), so these
# classes are currently unreached and R8 would otherwise strip them entirely — kept
# anyway so a future call to getInfo() doesn't silently lose every field to renaming.
-keep class com.yausername.youtubedl_android.mapper.** { *; }

# Jackson resolves parameterized fields (e.g. ArrayList<VideoFormat> in mapper.VideoInfo)
# from each field's generic Signature attribute at runtime. proguard-android-optimize.txt
# keeps annotations and source info but not Signature, so without this the mapper keep
# above still deserializes every list element to a raw LinkedHashMap instead of the typed
# class.
-keepattributes Signature

# --- Jackson (com.fasterxml.jackson:jackson-databind / -core / -annotations) ---
# Plain jars, not AARs, so AGP has no proguard.txt/consumer-rules.txt to merge from them
# automatically (verified in the Gradle cache). YoutubeDLUpdater — the github flavor's
# runtime self-update path, data/repository/YoutubeDLReady.kt — parses GitHub's releases
# API via ObjectMapper.readTree()/JsonNode at runtime, and Jackson's own internals use
# reflection regardless of what the app does with the parsed tree. The fdroid flavor never
# calls updateYoutubeDL() (BuildConfig.YTDLP_SELF_UPDATE is a compile-time false there, so
# R8 folds the call away), making this dead weight in that flavor rather than a live risk.
-keep class com.fasterxml.jackson.** { *; }
# Confirmed by an actual R8 run (missing_rules.txt), not guessed: jackson-databind 2.11's
# Java7SupportImpl probes java.beans.ConstructorProperties/Transient in a try/catch and
# degrades gracefully when they're absent, and DOMSerializer's constructor references
# org.w3c.dom.bootstrap.DOMImplementationRegistry the same way. Neither exists on Android.
-dontwarn java.beans.ConstructorProperties
-dontwarn java.beans.Transient
-dontwarn org.w3c.dom.bootstrap.DOMImplementationRegistry

# commons-compress (youtubedl-android unzips its bundled Python with it) registers every
# ZipExtraField implementation in ExtraFieldUtils' static initializer by calling
# Class.newInstance(). R8 sees no direct construction and strips the no-arg constructors,
# so YoutubeDL.init() crashed at startup with "AsiExtraField is not a concrete class".
-keep class * implements org.apache.commons.compress.archivers.zip.ZipExtraField { public <init>(); }

# security-crypto (Tink), DataStore Preferences and OkHttp all ship their own
# proguard.txt/consumer-rules.txt (verified in the Gradle cache) covering their
# protobuf-lite field binding and native-method surfaces, so no rules are added for them
# here. kotlinx-serialization-core also ships its own R8 rules (META-INF/com.android.tools/
# r8/kotlinx-serialization-*.pro, verified in the Gradle cache), and this app's
# @Serializable models (Provider, AppSettings, the OpenAI/Anthropic/Gemini/OpenRouter
# model-list responses) are all decoded through the reified decodeFromString<T>() API,
# which the compiler plugin resolves to a directly referenced serializer at compile time
# — no reflection, no extra rule needed. Hilt/KSP ship consumer rules the same way.