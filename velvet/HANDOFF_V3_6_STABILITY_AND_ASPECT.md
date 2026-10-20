# Velvet Atelier Studio v3.6 — Stability and Aspect-Ratio Handoff
Date: 2026-10-20 (Asia/Seoul). Branch: `velvet-atelier-apk`.
Status: committed source, GitHub JVM + Android compilation QA succeeded (run `39377217212`). Combined APK workflow `.github/workflows/velvet-generation36.yml` creates a separate, signed full-model APK. Confirm final asset status separately before linking it.

## Findings and source fixes
1. **Long-prompt canceled run memory leakage** (`LongPromptRunnerActivity.java`):
   - Previous worker allocated intermediate Bitmap results during sequential img2img generation but only recycled them on a successful next pass. A network failure/cancellation left the most recent full-resolution intermediate image alive.
   - Working Bitmap now has a single clear owner; `finally` recycles it on unsuccessful exit, while successful completion transfers ownership to the UI callback.
2. **PNG save concurrency / bitmap recycle race** (`LongPromptRunnerActivity.java`, `FaceRepairStudioActivity.java`):
   - Previously export queued PNG compression but did not mark the editor busy. Starting a new generation or loading another image could recycle the source bitmap during compression, causing a crash or bad PNG.
   - Export now takes exclusive ownership of the bitmap until the background save completes; UI controls reactivate on main thread.
3. **Native-exact first negative conditioning window** (`NativeTokenBudget.java`, `NativeTokenBudgetTest.java`):
   - The automatic img2img runner used to split/verify *all* negative-prompt chunks, despite only using the first. Long negative prompts could exceed the planner's 12-pass limit and fail even when a first short negative segment was valid.
   - New `firstVerifiedNegative` uses Local Dream `POST /tokenize` for the exact CLIP BPE count and its **UTF-16 overflow_offset**, clips at a preceding comma/space, and rechecks until the native 77-position budget is met. It never creates additional positive diffusion passes.
   - This does **not** increase the native 77-token MNN UNet context; for true multiple chunks see v3.4 native context handoff and `velvet/native/ClipChunker.hpp`.
4. **Guided portrait/landscape support in automatic multi-pass** (`GenerationDimensions.java`, `GenerationDimensionsTest.java`, `GuidedStudioActivity.java`, `LongPromptRunnerActivity.java`):
   - Previous multi-pass code read only the guided width and forced both dimensions to 384 or 512, incorrectly squashing portrait/landscape.
   - Pass width+height independently; preserve aspect within a limit of 768 pixels/side, 512*768 pixels, 64-pixel alignment (minimum 256 for each side). Extremely large inputs are conservatively downscaled; UI shows the actual render size.
   - The normal original Local Dream generator is unaffected.
5. **Inherited v3.5 quality-first CLIP behavior retained**: size/girth/face and style cues before optional long prose; unblocked lossless Guided Studio full prompt clipboard import; user-defined style cards including unverified Boukoku Daitoryou-inspired approximation.

## Verified outcomes
- `Velvet v3.6 Stability and Aspect QA`, run `39377217212`: PASS (native token prefix/long-negative test, portrait/draft/landscape/large dimensions test, inherited prompt/repair/compositor GIF/bitmap tests, Android API35 source compilation).
- `velvet-generation36.yml`: test, 2.13GB checkpoint hash, repackaging and APK v3 signing; not a phone inference test. Recheck actual GitHub Actions run for final conclusion.
- **Not verified on physical Galaxy Note20 Ultra**: first-run model conversion, actual CPU/OpenCL generation, visual face accuracy, real performance improvements, thermals and exact native output quality.

## Other local ML research to reuse later
Read `POCKET_CANVAS_v0.9_ENGINEERING_HANDOFF.md` / `QA_SUMMARY_v0.9.md` from Library and `HANDOFF_V3_5_QUALITY_FIRST_PROMPTS.md`.
- Compact anime ESRGAN 6B (~17.9 MB) + tile-based C++ backend is promising. Source-only Pocket Canvas implementation has host mock tests but no real Android inference, and the PyTorch .pth model cannot simply be loaded through Velvet's current MNN engine. Do not put a deadweight model in the APK without a compiled, licensed native upscaler + run-time checks.
- ADetailer-style anime face detection, LoRA and ControlNet, stable-diffusion.cpp GGUF quantization, SDXS/LCM fast previews are research tracks. No unverified outputs, weights or speed claims.
- Focus on actual on-device cold/warm RAM/PSS, quality-per-second at fixed seed/prompts, OpenCL thermals, and the device CSV logger before selecting replacements.

## Upgrade caveat
`org.velvetatelier.studio` has used ephemeral per-build signing certificates unless `VELVET_SIGNING_KEYSTORE_B64` and `VELVET_SIGNING_PASSWORD` private GitHub Actions secrets are configured. A newly signed v3.6 cannot update an existing v3.5 installation signed by another key even with the same package ID. Export profile/style JSON first, verify backups, then uninstall old Studio and install the newer APK; reconvert bundled model once. Do not create or store signing secrets in a public Git repository.

## Licenses
Local Dream 2.8.1: CC BY-NC 4.0; bundled MeinaHentai: CreativeML OpenRAIL-M; separate models and native libraries require provenance and license checks. This is a personal/noncommercial fork.
