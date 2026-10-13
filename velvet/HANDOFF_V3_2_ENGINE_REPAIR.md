# Velvet Atelier Studio v3.2 — Generation Engine Upgrade

## Status
Native Android source implemented and tested, APK packaging workflow `.github/workflows/velvet-generation32.yml`. Native generation and visual face/anatomy quality still require Samsung Galaxy Note20 Ultra on-device testing; Java compile alone cannot establish visually correct inference.

## Actual diffusion changes, rather than just new prompt controls
1. **Targeted model-based repair.** `FaceRepairStudioActivity.java` runs MeinaHentai SD1.5 via the existing on-device `BackendService`, calling the native `POST /generate` img2img endpoint. The user taps the region of interest in a saved image. A clipped crop is enlarged to 512×512; the existing MNN model samples with low denoising, and the patch is returned to original size with a feathered border. Outside-region original pixels are unchanged.
2. **Multiple repair targets:** Face and eyes, hands/fingers, full body proportions and adult anatomy proportions. Each uses a concise target-specific positive/negative recipe (see `RepairPromptTemplates.java`); the latter has natural/smaller/larger options. This is not a guaranteed surgical or pose-preserving edit: diffusion can change local appearance.
3. **Avoid model reload between consecutive repairs:** Keep the model backend alive while the repair Activity is open; stop it when the studio is destroyed, and never spawn a second copy of the full model. Reusing the existing process eliminates repeated cold-load work in a multi-repair session. Speedups are **not yet measured on the phone**.
4. **Native CLIP prioritization:** `PromptBuilder.java` now places adult status, identity, face geometry, desired exposure/scene, and major proportion cues early, then optional visual detail. The most consequential face, anatomy and size negatives now precede long generic negative tags. This prevents important constraints from being first in the truncated part of a longer prompt.
5. **Guided import no longer dead-ends on a long prompt:** `GuidedStudioActivity.java` offers a fast prioritized first-pass prompt, experimental automatic multi-pass img2img through the native tokenizer, or manual segment review. Exact native BPE token count is verified in the automatic runner; the fast first-pass option uses conservative word budgeting.
6. **GPU experimentation retained opt-in only:** OpenCL can sometimes accelerate MNN SD1.5 but is not enabled by default; stability, peak RAM, driver and thermal performance must be measured on the user's actual device.

## Related pure Java code and tests
- `FacePatchEngine.java`: safe crop bounds, alpha/feather blending, no mutation of original pixel array.
- `FacePatchEngineTest.java`: boundary cases, source immutability, smooth edges and old/new patch behavior.
- `RepairPromptTemplates.java`: model compatible short face/hand/body/size repair modes with no contradicting negatives for large-size mode.
- Previous `QualityTuningTest.java`, `PromptBuilderTest.java`, `ImageResamplerTest.java`, `ModelArtifactVerifierTest.java` retained.
- GitHub Actions source test run `38389766596` PASS (2026-10-13): Android API35 Java compilation, repair tests and prompt/image regressions.

## Native engine API verified in upstream Local Dream v2.8.1
- `app/src/main/cpp/src/main.cpp`: `POST /generate` SSE events, `GET /health`, `POST /tokenize`.
- `app/src/main/cpp/src/RequestParser.hpp`: accepts base64-encoded PNG `image`, `denoise_strength`, `use_opencl`, `steps`, `width`/`height`, `output_format`.
- `app/src/main/java/io/github/xororz/localdream/service/BackendService.kt`: local MNN model lifecycle on port 8081.
- The original server does not accept more than 77 native SD1.5 CLIP positions in one pass; the existing experimental multi-pass runner does not change that native attention limit.
- No standalone learned upscaler or new GGUF/LCM/LoRA weights were bundled into v3.2: installing several unverified models would degrade storage and phone reliability.

## One-install migration caveat
v3.2 remains one combined APK with the original generator, Guided Studio, Pixel Studio, CPU upscaler, repair tools and model bootstrap. Android package ID stays `org.velvetatelier.studio`.
Previous Studio v3.0 and v3.1 releases used freshly generated keys. Because the original private keystore was not saved and was not configured as a GitHub Actions secret, the v3.2 APK signed with another temporary certificate CANNOT be installed in-place as an update. User must export profiles from prior Studio, uninstall it, then install v3.2 and import JSON after first model conversion. Keep the original installation until backup verified.
To get real future seamless upgrades, create a private persistent signing keystore, back it up offline and configure `VELVET_SIGNING_KEYSTORE_B64` and `VELVET_SIGNING_PASSWORD` in GitHub Actions repository secrets before the next permanent release. Never commit private keys.

## Next engineering work based on measured weaknesses
1. Actual phone-first QA: one 512×512 text-to-image; face repair of that PNG; verify Model READY and `/generate` response / storage.
2. Compare cold vs warm model load, same seed and step count, CPU/OpenCL performance and throttle; run at multiple resolutions.
3. Evaluate compact **Real-ESRGAN anime 6B** NCNN/Vulkan as an *optional* learned upscaler pack, after license audit and Android device capability validation.
4. Evaluate quantization of converted MNN tensors only after A/B checking face/anatomy fidelity, memory and speed; avoid unverified claims.
5. Test/implement real native CLIP multi-chunk attention via rebuilding `libstable_diffusion_core.so`; separate that from currently supported sequential img2img passes.

Upstream Local Dream is CC BY-NC; MeinaHentai original weights follow OpenRAIL-M.
