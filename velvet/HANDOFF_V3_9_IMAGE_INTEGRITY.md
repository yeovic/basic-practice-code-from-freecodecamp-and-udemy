# Velvet Atelier Studio v3.9 — Image Integrity, Lossless Transport and Native Research

Date: 2026-11-02 (Asia/Seoul). Branch `velvet-atelier-apk` of `yeovic/basic-practice-code-from-freecodecamp-and-udemy`.

## Status
**SOURCE TESTS AND FULL COMBINED APK RELEASE VERIFIED.** Full v3.9 build run `41396084187` completed successfully and the signed APK is published at https://github.com/yeovic/basic-practice-code-from-freecodecamp-and-udemy/releases/tag/velvet-atelier-studio-3-9 . Filename `Velvet_Atelier_Studio_v3_9_Image_Integrity.apk`, 2,026,865,965 bytes, SHA-256 `b4523b71425aae45aab8244653a5b4b700f8c531b99cbfbe7606c5dcd2918fdc`. Android package `org.velvetatelier.studio`, versionCode `390`, versionName `3.9.0`; APK signature scheme v3 verified. Embedded Meina checkpoint validated as 16 archive parts totaling 2,132,651,194 bytes. Separate source QA run `41395868369` passed all JVM regressions and Android API 35 Java compilation. The earlier `41395789268` test failed due to a test-only lambda capture, then was fixed. **Physical-device model conversion and generation remain untested.**

## Changes from v3.8

1. **Lossless native generation transport.**
   - Local Dream `main.cpp` v2.8.1 `encodeResultImage` accepts `output_format=png` and sends base64 in the SSE `complete` event with `format`, `width`, `height`.
   - `LongPromptRunnerActivity.java` now requests `output_format=png` instead of `raw` and decodes through `BitmapFactory.decodeByteArray`. This avoids constructing a full-size Java ARGB int array from raw RGB. PNG compression is lossless, unlike JPEG; generation quality is unchanged.
   - `NativeImageTransportPolicy.java` checks PNG magic, IHDR, expected dimensions, declared dimensions, and response byte limit *before* native bitmap decode. `NativeImageTransportPolicyTest.java` exercises valid/invalid/corrupted headers and size bounds.
   - Trade-off: PNG encoding/decoding may use more CPU than raw bytes for noisy images, though the memory/transfer-size savings should help typical anime images. **No phone timing claim without benchmark**.

2. **EXIF orientation in selective repair.**
   - `BitmapFactory.decodeStream` may not apply a JPEG/HEIF camera's orientation metadata. Regions tapped on the displayed image could be misaligned with source pixels, particularly on 90° rotation and mirrored selfie images.
   - `ImageOrientationPolicy.java` provides deterministic affine transforms for all 8 EXIF orientations, including transpose/transverse.
   - `ImageOrientationLoader.java` reads EXIF from Android content URI via `android.media.ExifInterface(InputStream)`, rotates/mirrors only when needed, and avoids extra bitmap allocation for normal images. `FaceRepairStudioActivity.java` normalizes the loaded bitmap before image-space selection, mask preview, img2img crop and PNG export.
   - `ImageOrientationPolicyTest.java` verifies all eight orientation transforms cover the correct output dimensions without out-of-bounds or duplicate image pixels.

3. **Retained v3.8 (included in new full-build manifest/dependencies)**
   - Genuine native `mask` PNG inpaint region selection and visual preview, sliders for coverage/feather/blend, undo, existing `NativeBackendConfiguration` VAE img2img enable/restart.
   - `NativeContextPolicy` recognizes newer engine true multi-chunk support by runtime `/tokenize.max_length`, while continuing the older 77-context sequential img2img mode. v3.9 still bundles old 2.8.1, so native >77 one-pass support remains **disabled** on the actual APK.
   - All prior style/character cards, portrait/landscape, native prompt compaction, GPU optional switch, pixel animation, CPU image resizing, model-storage recovery and diagnostics.

## QA and release
- Source QA: https://github.com/yeovic/basic-practice-code-from-freecodecamp-and-udemy/actions/runs/41395868369 — PASS including `EXIF 1–8 rotations...`, `lossless native PNG contract...`, `v3.9 ... Android compilation`, native masked inpaint tests, dynamic context checks and older tests.
- Combined workflow `.github/workflows/velvet-generation39.yml`: uses verified v2.8.1 Android local backend and bundled 2,132,651,194-byte MeinaHentai checkpoint; includes new + v3.8 Java classes; runs additional regressions, patches APK identity with versionCode=390/versionName=3.9.0, signs APK, verifies DEX classes and model chunks before GitHub release. Release published and verified by CI; separately confirm phone installation and image production.
- Repository retains support for GitHub Actions secrets `VELVET_SIGNING_KEYSTORE_B64` and `VELVET_SIGNING_PASSWORD`. No private signing key was configured for previous releases. If v3.9 uses a generated temporary certificate, it cannot be installed over an older Studio with the same package ID but a different certificate. Export character/style cards JSON, verify it, and uninstall old app before installing v3.9; first-run model setup is needed again.
- CI is NOT proof of phone installation, actual diffusion image production, visual improvement, OpenCL speed, battery or thermal results.

## Native upgrade research and next objective
Upstream Local Dream v3.0.0-alpha.7 published Nov 1 2026: https://github.com/xororz/local-dream/releases/tag/v3.0.0-alpha.7
- Alpha series supports longer SD1.5 CLIP contexts via real 75-token chunked encoder windows and concatenated UNet attention embeddings. Current bundled v2.8.1 fixes context to 77. Never claim v3.9 installed the new native backend.
- **Proposed experimental separate build:** extract upstream alpha.7 APK, compare native ABI, model-file formats, model conversion and Android manifests with v2.8.1, and compile/run tests without replacing stable v3.9. Verify MNN attention shape and model compatibility. Only promote after physical Note20 Ultra checks of actual long-context prompt, 512-square generation, 384×512 resolution, masked repair, crash/thermal state and source checksum.
- Compact optional learned anime superresolution: Real-ESRGAN 6B requires a compatible native bridge (e.g. NCNN/Vulkan, separate upscaler runtime); the current Lanczos upscaler is not an ML upscaler. Do not include unchecked PTH weights in a nominally working APK.
- Model acceleration: compare measured SD1.5 CPU versus experimental MNN OpenCL, cold/warm load, constrained memory, native attention chunk sizes and thermal throttle. Tune steps/CFG and apply LCM/LoRA only with compatible backend/model and quality A/B.
- Improve native backend session handshakes (GET /health alone proves process alive, not correct model/config). Native service already exposes state internally; a future safe API should report model ID, img2img availability and live inference resolution.
- Preserve third-party provenance; Local Dream CC BY-NC and MeinaHentai OpenRAIL-M restrictions still apply to personal-use forks.

## Regression audit targets
- EXIF orientation applied before crop. Inspect Android 13 Google Photos camera images, portrait+mirror and PNG shared from Gallery.
- Validate native event PNG decoder on real generated data (not synthetic PNG header only).
- Be mindful that a 2+GB APK takes significant storage for the extracted checkpoint and converted MNN model, and updates require signing continuity.
