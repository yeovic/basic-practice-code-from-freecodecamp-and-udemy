# Velvet Atelier Studio v3.8 — Native Masked Repair & Long-Context Compatibility

Date: 2026-11-02 (Asia/Seoul). Repository: `yeovic/basic-practice-code-from-freecodecamp-and-udemy`, branch `velvet-atelier-apk`.

**Status: SOURCE IMPLEMENTED AND QA PASSED. NO v3.8 INSTALLABLE APK WAS PUBLISHED IN THIS PASS.** The latest published combined APK remains v3.7. Do not advertise the v3.8 features as already installed on the user's phone.

## What was implemented

1. `RepairMask.java`: generated 512² native inpainting masks (opaque RGB grayscale, white center editable, black edge protected, smoothstep feather). Validated bounds and center/edge coverage; no extra inference model.
2. `FaceRepairStudioActivity.java`: repair uses Local Dream's existing base64 PNG `mask` field with the input image. Optional switch defaults on, adjustable coverage 40–95%, feather 3–30%, and final blend strength 50–100%. Switching it off preserves previous full-square cropped img2img behavior. Reuses Meina SD1.5 already bundled. This gives a more constrained crop than unmasked repainting, but **actual identity/quality improvement is not yet measured**.
3. `RegionSelectionView.java`: previews the approximate mask center/feather boundary over the selected crop, without creating a second full-size Bitmap; click and sliders update the overlay.
4. `NativeBackendConfiguration.java`: a feature initiated by the user now enables `app_prefs.use_img2img` if it was disabled, and restarts Local Dream's backend once to load the VAE image encoder. Repair and the multi-pass runner call the helper. The setting stays enabled afterward, explicitly described in the UI. Underlying upstream Kotlin `BackendService.kt` uses `--no_img2img` when disabled, so this fixes a real launch-condition failure.
5. `NativeContextPolicy.java`: detects actual native long-context capability at runtime from `POST /tokenize`'s `max_length` and `count`, without guessing. Legacy Local Dream 2.8.1 reports `max_length=77` and remains on sequential img2img. New Local Dream 3.0 MNN native backends can report `max_length=0` for dynamic 77-token chunk concatenation; after an opt-in checkbox, the Studio may run one true longer-context call, limited to 231 positions on phone. A fixed 154-position backend is supported as well. Any unknown/overflow capability falls back to tested v3.7 passes.
6. `LongPromptRunnerActivity.java`: integrates the runtime capability check and remains backwards compatible with legacy MNN, with explicit native-versus-sequential mode text. Actual true native longer-context inference **cannot run in the current v2.8.1 APK**.
7. `RepairMaskTest.java`, `NativeContextPolicyTest.java`: pure Java coverage and fallback checks. Existing GIF, model-integrity, face/undo, prompt, image upscale, and Android Java tests preserved.

## CI verification

Workflow: `.github/workflows/velvet-v26-upscale-checks.yml`, renamed `Velvet v3.8 Native Capability QA`.
Latest successful run: https://github.com/yeovic/basic-practice-code-from-freecodecamp-and-udemy/actions/runs/41321905100
- PASS soft mask geometry, protected edges and grayscale + coverage validation.
- PASS v2.8 fallback, 154/231 native multi-chunk detection, phone cap.
- PASS original native token packing, portrait/landscape support, low-memory local repair/undo, conditional prompts and resampler.
- PASS Android API35 Java compilation with all newly added classes.
- NO device screenshot, emulator generation, real Meina inference, thermal comparison, Android installation or visual fidelity evaluation.

## Upstream native code research: substantial new upgrade opportunity

Verified current upstream Local Dream release **v3.0.0-alpha.7**, published Nov 1, 2026: https://github.com/xororz/local-dream/releases/tag/v3.0.0-alpha.7.

Source-level verified differences to currently embedded v2.8.1:
- `app/src/main/cpp/src/TextEncoder.hpp`: accepts `max_chunks`, segments 75-token prompt portions, computes multiple 77-position windows.
- `app/src/main/cpp/src/PipelineSd15Cpu.hpp`: runs fixed-size MNN CLIP separately for each chunk, concatenates hidden embeddings, and dynamically resizes UNet `encoder_hidden_states` to `{batch, tokens, 768}`.
- `app/src/main/cpp/src/main.cpp`: `max_chunks=0` for MNN models, `/tokenize` advertises `max_length=0` for dynamically sized contexts.
- These changes provide real single-generation longer attention context, not the older sequential low-denoise img2img approximation.
- **Do not simply swap the bundled binary without validation.** Native 3.0 is pre-release and the current packaging workflow rebundles/repatches Local Dream 2.8.1. Need source/native ABI audit, MNN version compatibility, model conversion compatibility, Android 13 and Adreno 650 benchmark, conditional behavior, licensing review, rollback before shipping. New flagship DiT features (Z-Image, FLUX, Qwen) are NOT assumed supported by older Snapdragon device.

## Third-party model research retained
- Real-ESRGAN anime 6B: https://github.com/xinntao/Real-ESRGAN. Compact trained anime SR, but `.pth` or NCNN model weights **cannot be loaded directly by Velvet's current MNN text-to-image backend**. A separate validated native upscaler bridge, tiled allocation, model hash/attribution/license audit, and Android tests are required.
- Do not mislabel bilinear/Lanczos as learned ML upscaling.
- Face repair with a native masked SD1.5 model is a preferable immediate integration because it uses already-supported generation weights.

## Remaining blockers and priority order

1. **Physical-device QA:** Generate 512×512 Meina image; select face, compare native mask on/off at the same denoise/steps, verify unmodified outer pixels and local likeness. Repeat hand and body repairs; gather `LocalPerformanceLog` timing/PSS/thermal CSV.
2. **Build full v3.8 only if install needed** and signing/billing constraints are addressed. Current v3.7 had a temporary, per-build signing key; new same-package APK cannot in-place update it. Back up style/character cards before uninstalling.
3. **Investigate native engine update** through an experimental branch/CI once a stable key and actual phone checks exist. v3.0 alpha's native chunk feature is not installed yet.
4. Add optional small neural upscaler only after compiling an Android compatible runtime and verifying weights.
5. Benchmark cold/warm CPU vs OpenCL and model memory to choose real speed changes; never claim test results from code inspection alone.

Third-party provenance: Local Dream (CC BY-NC 4.0 at bundled source version); MeinaHentai model (CreativeML OpenRAIL-M). Noncommercial use still requires respecting license obligations.
