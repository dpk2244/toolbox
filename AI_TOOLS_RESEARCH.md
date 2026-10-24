# Offline AI Tools — research & integration plan (2026-10-23)

Pushpendra's request: add as many OFFLINE (on-device) AI tools as possible.
Chosen stack: Google ML Kit standalone (bundled-model) SDKs — genuinely
on-device, free, no API key, no account. Speech-to-text uses Android's
built-in SpeechRecognizer (device language packs; not ML Kit).

## Artifacts (Google Maven, versions from the features' own POMs)
Downloaded to ~/toolchains/verify/deps4/ (31 AARs + okhttp/okio jars):
- text-recognition 16.0.1 (+ bundled-common 17.0.0) → OCR Latin
- text-recognition-devanagari 16.0.1 → OCR Devanagari (also carries Bengali)
- face-detection 16.1.7 → Face Blur / face detect
- image-labeling 17.0.9 (+ common/default-common, vision-internal-vkp 18.2.3)
- barcode-scanning 17.3.0 (+ scanning-common) → QR/barcode scan
- segmentation-selfie 16.0.0-beta6 (+ mediapipe-internal, odml image) → BG remover
- translate 17.0.3 → offline translator (per-language model download in-app)
- language-id 17.0.6 (+ language-id-common) → translator source detection
- shared: mlkit common 18.11.0, vision-common 17.3.0, vision-interfaces,
  play-services-mlkit-* bases, play-services-base/tasks/basement (already in),
  datatransport api/runtime/backend-cct, firebase-components/encoders(-json)/
  annotations, okhttp 3.14.9 + okio (translate model download)

## Native libraries — arm64-v8a ONLY (decision)
Every feature AAR ships 4 ABIs; including only arm64-v8a (all modern phones,
incl. budget Indian devices since ~2017; minSdk 26 already) cuts native size
~4x. arm64 .so sizes: libxeno_native 21.1 MB (segmentation),
libtranslate_jni 16.0 MB, libmlkit_google_ocr_pipeline 10.8 MB,
libmlkitcommonpipeline 10.7 MB (labeling), libface_detector_v2_jni 8.3 MB,
libbarhopper_v3 4.8 MB → ~71.7 MB raw; .so compress ~40–50% in the APK.
Manifest must set android:extractNativeLibs="true" so compressed .so work.
Estimated final APK: ~60–75 MB (from 24 MB). Exact figure after the build.

## Model assets (bundled, ship in assets/)
OCR Latin+Devanagari model files (~1–2 MB total), image-labeling default
model 2.9 MB, language-id model 0.3 MB, selfie segmentation model 0.24 MB,
barcode models ~0.9 MB, face models ~3 MB. Translate language packs are
downloaded per language inside the app (~30 MB each, user-triggered,
offline forever after) — disclosed in the tool's UI copy.

## Manifest merge requirements
New components from the AAR manifests must merge (meta-data children must
ACCUMULATE, not first-wins): MlKitInitProvider (provider),
MlKitComponentDiscoveryService (firebase registrar meta-data from common,
vision-common, translate, and each feature), feature pipeline services,
INTERNET + ACCESS_NETWORK_STATE permissions (translate downloads; INTERNET
already present via AdMob). Re-apply the ProcessLifecycleInitializer
meta-data patch after any re-merge.

## Tool list (new "AI Tools" category; 52 → 59 tools)
1. ai_bg_remover — selfie segmentation: transparent PNG or solid/blur BG
2. ai_ocr — image → text, Latin + Devanagari recognizers, copy/share result
3. ai_face_blur — detect faces, blur/pixelate them, save
4. ai_labeler — image labels with confidence
5. ai_translator — offline translate (download pack once), EN/HI + majors
6. ai_voice_text — SpeechRecognizer dictation (honest offline caveat)
7. ai_barcode_scan — gallery image (and later camera) barcode/QR decode

## Honest limits (to state in UI + to Pushpendra)
- Selfie segmentation is tuned for people/portraits, not arbitrary objects.
- Translator/voice offline quality depends on downloaded packs/device.
- Bundled = larger APK (see size accounting above); Play App Bundle splits
  would fix this at publish time, a single sideload APK cannot.
