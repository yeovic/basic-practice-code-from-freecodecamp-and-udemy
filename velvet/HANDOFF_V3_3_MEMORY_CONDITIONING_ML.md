# Velvet Atelier Studio v3.3 — generation engineering and other-ML mining

Date: October 14, 2026, Asia/Seoul. Branch `velvet-atelier-apk`.

## Changes that actually landed

### Less RAM in local crop repairs
- `FacePatchEngine.compositePatch` operates on two crop-sized ARGB arrays, returning just the crop; the previous compositor copied the **entire** source+result image pixel arrays into Java memory before reconstituting another Bitmap.
- `FaceRepairStudioActivity` retains only `input.copy(Bitmap.Config.ARGB_8888,true)` as the final full-size Bitmap and pastes the blended crop into its own location. For a 2048×1536 original with a 512×512 crop, this avoids temporary full-size `int[]` copies (about 12 MiB each) and replaces them with crop-sized arrays (about 1 MiB each). This is a Java-array memory reduction calculation, not measured device RSS.
- `RegionSelectionView` draws the region on the live image instead of allocating a copy of the full image on every tap/slider update; the image coordinates are mapped using the actual ImageView matrix.
- `FaceRepairStudioActivity` uses the repaired bitmap as the next input, preserving previous repairs when fixing multiple faces, hands or anatomical regions. ImageView avoids showing recycled outline bitmaps.
- `FacePatchEngineTest` proves the new crop compositor is pixel-for-pixel identical to the old full-image compositor on equivalent inputs, and retains feathered edges.

### Fewer redundant SD1.5 inference passes
- `LongPromptRunnerActivity` previously used `max(positiveChunks,negativeChunks)` to choose diffusion pass count. A long negative prompt could trigger multiple redundant full generations with the same positive prompt, causing style/identity drift and unnecessary heat.
- `GenerationPassPolicy.requiredPasses` now counts positive chunks ONLY.
- Every pass retains the first/high-priority negative segment, which `PromptBuilder` now places in model-relevant order (face/size/coherence before generic low-quality tags).
- Later positive prompt chunks are automatically re-anchored to existing character identity and composition ONLY when the **actual on-device CLIP tokenizer** reports that the anchored prompt still fits 77 positions. If too long, fallback to the original chunk. This is sequential img2img, not native concatenated 154-token attention.
- `GenerationPassPolicyTest` covers 1 positive / 5 negative (now one pass), positive multi-pass count, invariant negative priorities and identity anchors. It does NOT prove image perceptual quality.

### Measured device behavior, not guessed speedups
- `LocalPerformanceLog` writes small bounded CSV records locally only: operation, steps, width/height, end-to-end HTTP/native model timing (where supplied), GPU toggle, PSS and Android thermal status. No prompts/images/network/PII.
- Instrumented `LongPromptRunnerActivity` and `FaceRepairStudioActivity` for load readiness and each actual diffusion render using native event `generation_time_ms`.
- The home screen now includes **Measured local generation performance** with a Copy CSV action. Use to compare warmed CPU/OpenCL, cold-start delays, 512-square vs different size, and body/face repair runs on the Galaxy Note20 Ultra.
- No speed, RAM, quality, or thermal improvements are claimed from physical device benchmarks, which have **not** yet been run.

### Existing features retained
One offline Android package with Local Dream v2.8.1 MNN SD1.5 model, MeinaHentai v2.1 full checkpoint, conditional Guided Studio size / anatomy / face priority modes, custom style cards, prompt chunking, experimental OpenCL, manual selection + img2img repair, pixel animation, simple CPU upscale modes (Lanczos3, bilinear, nearest-neighbor), model integrity checks, optional SD card storage and profile backups.

## Other ML handoff consulted
Read `POCKET_CANVAS_v0.9_ENGINEERING_HANDOFF.md` and `QA_SUMMARY_v0.9.md` from Library.
- Pocket Canvas **source-only** implements an ESRGAN path using pinned `stable-diffusion.cpp`, `new_upscaler_ctx` and tiled processing, with 96px tile proposal; native host mock tests pass but **no arm64 Android build/device inference was verified**.
- RealESRGAN x4plus anime 6B: about **17.9 MB** PyTorch PTH model, BSD-3-Clause, 4x; model source https://huggingface.co/amd/realesrgan-x4plus-anime-6b. It is NOT directly loadable by the existing Velvet MNN backend. Need integrate a separate compatible native implementation or new backend and verify model hash/license before bundling. Current v3.3 does not falsely label Lanczos as AI upscale.
- Next model-level research: mobile compatible Real-ESRGAN 6B with properly licensed native bridge; CPU/OpenCL on Adreno 650; ADetailer-style small anime face detection and mask/inpaint; LCM/SDXS low-step alternatives; stable-diffusion.cpp Q4/Q5 quantization and Flash Attention benchmarks; ControlNet/IP-Adapter only if RAM/storage allows.
- A different ML model should NOT replace Meina until its outputs are compared at fixed seeds/prompts/steps and actual phone memory/thermal timing.

## Validation
`Velvet v3.3 Generation Benchmarks QA` run `38485266506`: PASS (Android API35 Java compilation, pure JVM image/face repair/prompt and model verifier tests). Crop-only compositor regression passed after fixing test color fixture.
Packaging workflow `.github/workflows/velvet-generation33.yml` is based on proven combined v3.2 packaging and emits a single signed `org.velvetatelier.studio` APK, versionCode=330, versionName=3.3.0. Runtime generation and real visual quality still untested.

## Android signing warning
The v3.x public builds were signed with per-run ephemeral keys because private signing secrets were not configured. Even though package ID remains stable, **v3.3 cannot update v3.2 in place** if the signing certificate differs. Export character/style JSON first; uninstall older Studio only after saving backup, then install v3.3 and reconvert the model. Configure the two existing GitHub Actions private signing secrets + back up permanent keystore **before** making a long-term upgradable release. Never place private signing key in repository.

## Code provenance
Local Dream https://github.com/xororz/local-dream — CC BY-NC 4.0 fork.
MeinaHentai weights — CreativeML OpenRAIL-M.
ESRGAN upstream https://github.com/xinntao/Real-ESRGAN — verify assets' actual license; no weights bundled.
