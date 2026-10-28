# ToolBox: 100+ Tools, AI & PDF

A native Android app (Kotlin + Jetpack Compose + Material 3) with **102 genuinely functional tools**:
PDF 35 · AI 7 (on-device ML Kit) · Image 18 · Video 11 · Audio 9 · Text 12 · Calculators 10.
No server, no account, no paid APIs. Files never leave the device (AdMob is the only network user).

## About Pushpendra Tech

ToolBox is made by **Pushpendra Singh** of [Pushpendra Tech](https://pushpendra.tech) — AI tools, apps, websites and daily tech updates:

- Website: https://pushpendra.tech
- 17 free online tools (PDF, image & AI — no sign-up, files never upload): https://pushpendra.tech/tools/
- AI Image Prompts gallery (220+ copy-paste prompts): https://pushpendra.tech/ai-image-prompts/
- YouTube: https://youtube.com/@pushpendra.tech4
- Instagram: https://www.instagram.com/pushpendra.tech

## Open & build (Android Studio)
1. Install Android Studio (Ladybug or newer) with SDK Platform 35 + Build-Tools 35.
2. `File → Open` → select this folder. Let Gradle sync (first sync downloads AGP/Kotlin/Compose/Media3/PDFBox/ML Kit — needs internet).
3. Run on a device/emulator (min Android 8.0 / API 26, arm64).
4. CLI alternative:
   ```
   ./gradlew testDebugUnitTest      # JVM unit tests (text + calculators)
   ./gradlew assembleDebug          # debug APK -> app/build/outputs/apk/debug/
   ./gradlew connectedAndroidTest   # instrumented PDF tests (needs a device/emulator)
   ```

## Release (AAB for Google Play)
1. Create a keystore: Android Studio → Build → Generate Signed App Bundle/APK, or
   `keytool -genkey -v -keystore toolbox.jks -alias toolbox -keyalg RSA -keysize 2048 -validity 10000`
2. Copy `keystore.properties.example` → `keystore.properties`, fill in your values (this file is git-ignored — never commit it).
3. `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`

## Configure YOUR AdMob IDs (currently Google's official TEST IDs)
In `gradle.properties` replace:
- `ADMOB_APP_ID` — also matches the manifest meta-data automatically.
- `ADMOB_BANNER_ID`, `ADMOB_INTERSTITIAL_ID`
Test IDs in use (safe, earn nothing): App `ca-app-pub-3940256099942544~3347511713`, Banner `.../6300978111`, Interstitial `.../1033173712`.
Never click your own live ads. Interstitials fire at most once / 3 min, only from the interstitial helper — by default the app calls it sparingly; banner sits above the bottom tab bar on the browse tabs.

## Architecture
- `data/Tools.kt` — the 102-tool registry.
- `data/Prefs.kt` — DataStore: favorites + recents (tool ids only).
- `core/TextTools.kt`, `core/Calculators.kt` — pure Kotlin, JVM-unit-tested.
- `core/PdfTools.kt` — PDFBox-Android. `core/ImageTools.kt` — Bitmap/EXIF. `core/MediaTools.kt` — MediaExtractor/Muxer (copy ops), Media3 Transformer (re-encode), MediaCodec PCM (audio). `core/AnimatedGifEncoder.kt` — dependency-free GIF89a writer (256-colour palette).
- `ui/` — Compose screens. Every file tool: SAF picker → validate → process on IO thread → honest result panel (Save via CreateDocument / Share / Open via FileProvider). Originals are never overwritten.

## Honest limitations (also shown in-app)
- **No MP3 encoding**: Android ships no MP3 encoder and LAME brings licensing duties. "Video to MP3" extracts the audio track losslessly to `.m4a` and says so — it never fakes an `.mp3`.
- **Video/audio speed & volume**: WAV/PCM-based; speed uses resampling (pitch shifts).
- **PDF round-trips**: PDFBox load→save can drop some advanced features (interactive forms, digital signatures) of complex source files. Encryption is real 128-bit Standard security.
- **GIF**: 256-colour uniform palette — colours approximate.
- **Passport photo**: size is exact for the listed presets; official acceptance also depends on background/dress/recency rules of each authority.
- **Audio merge / trim** use stream copy into MP4/M4A: inputs should share a codec; incompatible files fail with a clear message instead of a broken output.

## Licenses (see LICENSES.md)
PDFBox-Android Apache-2.0 · AndroidX/Media3/Compose Apache-2.0 · ML Kit (Google terms) · ZXing Apache-2.0 · Google Mobile Ads SDK (Google terms) · Kotlin Apache-2.0. No FFmpeg, no GPL/LGPL components.

## Privacy
See `PRIVACY_POLICY.md` — hosted for Play Console at https://pushpendra.tech/toolbox-app-privacy-policy/ . The policy matches the code: no backend, no uploads, AdMob only.

## More docs
- `PLAY_LISTING.md` — Play Store title & descriptions · `IMPLEMENTATION_STATUS.md` — honest per-area status · `play-assets/` — Play icon (512×512) + feature graphic (1024×500)

---
© 2026 Pushpendra Singh (Pushpendra Tech) — https://pushpendra.tech
