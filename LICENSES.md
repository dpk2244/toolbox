# Dependency & licensing notes

| Component | Use | License / terms | Notes |
|---|---|---|---|
| Kotlin + kotlinx-coroutines | Language / async | Apache-2.0 | |
| Jetpack Compose, Material 3, Navigation, DataStore, ExifInterface | UI / storage / EXIF | Apache-2.0 (AOSP) | |
| AndroidX Media3 Transformer/Effect/Muxer 1.5.1 | Video compress + resolution re-encode | Apache-2.0 | Google-maintained successor path to MediaCodec APIs |
| PDFBox-Android 2.0.27.0 (`com.tom-roush:pdfbox-android`) | All 35 PDF tools incl. 128-bit encryption | Apache-2.0 | Android port of Apache PDFBox 2.0.27 |
| ZXing core 3.5.3 (`com.google.zxing:core`) | QR & Barcode Generator tool (QR, Code 128, EAN-13 encoding) | Apache-2.0 | Encoding only; barcode scanning uses ML Kit |
| Google Mobile Ads SDK 23.6.0 + UMP 3.0.0 | AdMob banner/interstitial + consent | Google AdMob terms / Play policies | Only test ad IDs are in the repo |
| JUnit 4 | Tests | EPL-1.0 (test-only, not shipped) | |

**Deliberately NOT used**
- **FFmpeg / ffmpeg-kit**: ffmpeg-kit was retired/archived by Arthenica in 2025, and FFmpeg builds commonly pull LGPL/GPL codec obligations (incl. libmp3lame). Avoided entirely — media work uses Android platform codecs.
- Any cloud processing API or backend: none.

APK size impact (approx.): PDFBox ~4–6 MB, Media3 ~2–3 MB, Ads SDK ~3–4 MB. No native codec binaries are bundled.
