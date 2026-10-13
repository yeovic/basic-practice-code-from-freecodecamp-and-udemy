# Velvet Atelier — Engineering Handoff v2.6 (2026-10-13)

## Current implementation
The established Android line is a noncommercial Local Dream v2.8.1 fork (original source license CC BY-NC 4.0) repackaged with MeinaHentai v2.1 SD1.5 safetensors (CreativeML Open RAIL-M), an ARM64 MNN converter, SD-card preference, a guided prompt/character-card UI and a deterministic pixel animation studio.

### v2.6 additions
- `ImageResampler.java`: pure-Java CPU resampler supporting pixel-perfect nearest-neighbor, premultiplied alpha bilinear and Lanczos3, fixed 2×–4× scales, source row cache <=12 rows, no full-resolution float bitmap.
- `ImageUpscaleActivity.java`: native Android editor with file-picker image import, async decode/upscale/full-resolution PNG export, progress/cancel, checks for >4096 side and >12MP, optional smoothing vs pixel preservation.
- `VelvetHubActivity.java`: integrated Upscale Studio launcher button alongside the model generator, Guided Studio and Pixel Loop Studio.
- `ImageResamplerTest.java`: round-trip nearest-neighbor, uniform-color, transparency, pixel-boundary, cancellation and randomized data tests.
- `.github/workflows/velvet-v26-upscale-checks.yml`: fast source QA; run 38350385092 **PASSED** Android API35 Java compilation, resampler tests and previous prompt tests.
- `.github/workflows/velvet-upscale26.yml`: reproducible 2GB model-inclusive APK build with the new class manifest registrations, checksum validation and signature checks.

### Important honest distinctions
- The v2.6 in-app Upscale Studio provides **non-ML spatial resampling** (real pixel count grows). It does NOT contain a Real-ESRGAN neural upscaler or reconstruct missing detail.
- Upstream Local Dream supports 2×/3×/4× learned upscaling but its result-page upscaler requires Snapdragon NPU; our currently imported CPU-based MeinaHentai model cannot use that NPU route. UltraFix tiled diffusion is SDXL-only in upstream v2.8.1 and not available for the SD1.5 imported model.
- The source build and packaging checks do NOT prove hardware model conversion or visual image quality; no end-to-end testing on the user's Galaxy Note20 Ultra.
- The underlying bundled APK still requires ~2GB download and first-run conversion. The Android app package is independently signed each build, so each release uses a different package ID; multiple installations consume duplicate model space. Fix with reusable private signing key and a stable package ID before claiming seamless updates.
- Local Dream's parameter clipboard importer does **not** import image width/height; Guided Studio's resolution suggestion must be set manually inside the real generator.
- Feature naming should never claim actual 'AI upscale' for non-ML bilinear/Lanczos processing.

## Android hardware target
Samsung Galaxy Note20 Ultra SM-N986U1, Android 13; prioritize SD1.5 CPU/GPU performance and thermal protection. Upstream Local Dream NPU accelerated path is for newer Snapdragon Hexagon V68+; confirm device capabilities before advertising NPU-based acceleration.

## Source references
Local Dream upstream: https://github.com/xororz/local-dream
Image SR candidate: https://github.com/tumuyan/RealSR-NCNN-Android
Anime-specific model: https://github.com/xinntao/Real-ESRGAN-ncnn-vulkan
Local Dream's built-in upscale limitations: app/src/main/java/io/github/xororz/localdream/ui/screens/ModelRunScreen.kt (v2.8.1).
Original Meina checkpoint: https://huggingface.co/sinkinai/MeinaHentai

## Next major improvements (not delivered in v2.6)
1. A proper native **AI** anime upscaler (RealESRGAN anime NCNN/Vulkan) with optional model pack, per-device Vulkan feature check, tile streaming and fallbacks. Audit GPL/third-party license of any reused native code or assets first.
2. Preconvert/quantize compatible MNN model weights in build pipeline, and distribute compressed/optional model packs to avoid 2GB model data bundled into every APK update. Benchmark model quality and ARM64 inference on physical hardware.
3. Replace per-release random signing with persistent protected signing key and stable package identifier; migrate user profiles and model pack storage. Don't embed an insecure signing secret in the public repository.
4. Hardware profiling: first-load vs warm generation time, RSS peaks, power/thermal throttling, SD vs internal read speed and resolution scaling. Avoid unverified claims about speedup.
5. Add explicit support for approved high-resolution inference and neural upscaling; keep quality/preview presets model-aware.
6. Create actual animation frame consistency control. Current Pixel Loop Studio uses deterministic warps, not video diffusion.
7. Investigate user's separate Local Image Generation Research chat handoff; direct retrieval was unavailable in this conversation and Library semantic search did not locate a relevant file.

## Safety/reliability
Keep all generated character content adult-only and fictional. Avoid impersonation or nonconsensual sexual depictions of real people.
Keep copyright notices, original licenses, and source provenance in release artifacts.
