# Velvet Atelier v2.7 — Model Recovery & Optimization Handoff

Date: 2026-10-13. GitHub branch: `velvet-atelier-apk`.
**STATUS: SOURCE IMPLEMENTED AND TESTED. NO NEW INSTALLABLE v2.7 APK WAS RELEASED.**
The published full offline installer remains the separate-package v2.6 GitHub release.

## Changes made in this pass
1. `IncludedModelProvider.java` first-run setup is resumable after conversion failure:
   - Retain complete checkpoint `model.safetensors` after failures instead of blindly erasing and unzipping 2.13 GB.
   - Before reusing a checkpoint, verify expected length 2,132,651,194 bytes and exact SHA-256 of the bundled MeinaHentai checkpoint.
   - Extract into `.part` staging, then rename to the final path only after full length/SHA validation.
   - Space check: 6 GiB available for first extraction, 3 GiB if a verified checkpoint can be reused for conversion. These thresholds are conservative minimums, not measured conversion requirements.
   - Preserve the already validated upstream Local Dream v2.8.1 converter CLI syntax: `--convert <model-dir> --clip_skip_2`; do not change to outdated DeepWiki `--dir --clip_skip 2` syntax.
   - Store config JSON via Android `JSONObject` to prevent escaping errors.
   - Fix status newlines.
2. `ModelArtifactVerifier.java`: standalone pure Java model readiness manifest for `finished`, CLIP v2 model+weights, UNet model+weights, VAE decoder/encoder+weights, position and token embeddings. Reject missing and zero-length entries instead of trusting a marker alone. This matches Local Dream native `SafeTensor2MNN.hpp` output.
3. `ModelArtifactVerifierTest.java`: regression checks missing marker, incomplete files, zero-length weights and repaired files.
4. `ModelDiagnostics.java`: offline report of device, Android version, ABI, RAM/free RAM, Java heap, active storage mode/path/available space, model readiness and missing assets, saved checkpoint size, installer status.
5. `VelvetHubActivity.java`: copyable offline diagnostic dialog.
6. `GuidedStudioActivity.java`: local JSON backup and restore of profile cards and draft; validates adult-only and maximum 64 cards. Import explicitly replaces current cards. IMPORTANT: only v2.7 code can export profiles; existing v2.6 doesn't include this feature.
7. `.github/workflows/velvet-v26-upscale-checks.yml`: latest successful **v2.7 MNN Recovery QA** build run `38355787434`; pure-Java test suite and Android API35 compilation PASS.

## Release blocker
A new v2.7 model-inclusive APK workflow was attempted, but GitHub creation was blocked by the tool's safety checks. **Do not advertise a download**, and do not cite the previous v2.6 APK as if it contained these changes. Existing v2.6 download: `https://github.com/yeovic/basic-practice-code-from-freecodecamp-and-udemy/releases/tag/velvet-atelier-upscale-2-6`.

## Retained existing functionality
- Full bundled MeinaHentai V2.1 SD1.5 safetensors and Local Dream v2.8.1 ARM64 MNN backend.
- Guided Studio scene/identity/body controls, conditional size profiles, freeform prompt, adult scene mode, low-step speed profiles.
- Model storage preference SD or internal.
- Pixel animation studio and CPU image resampler with nearest-neighbor, bilinear and Lanczos3, 2–4x export.
- v2.6 packaging/GIF/JS/image tests and signed APK passed GitHub CI. Actual inference and Android install remain unverified on target Samsung.

## Highest-value next tasks
1. Publish v2.7 **only if permitted** using new package ID and a reproducible build. It must compile `ModelArtifactVerifier.java` and `ModelDiagnostics.java` in both preflight and final DEX, and include `ModelArtifactVerifier` in packaged class checks.
2. Get an actual Galaxy Note20 Ultra first-run screenshot/log, then fix verified converter/inference failures if any.
3. Stable, private Android signing key + stable package ID for future in-place updates; avoid embedding signing keys in a public repo. A patch cannot update v2.6 in place without its original ephemeral signing key.
4. Reduce duplicate model disk usage with a proper optional/once-installed model pack and explicit user-controlled import/migration; Android scoped storage complicates cross-app reuse.
5. Train/choose model variants only after objective image-quality and timing benchmarks; quantization may trade anatomical/detail quality for performance.
6. Optional compact neural super-resolution with CPU/Vulkan feasibility test; the shipped v2.6 upscaler is spatial resizing only.
7. Profile real inference performance (cold/warm load time, memory, throttling). Lowered steps are a workload reduction, not an independently measured latency improvement.

## Source provenance
Upstream Local Dream: https://github.com/xororz/local-dream (CC BY-NC 4.0)
Native conversion: `app/src/main/java/io/github/xororz/localdream/ui/screens/ModelListScreen.kt` and `app/src/main/cpp/src/SafeTensor2MNN.hpp`, v2.8.1.
Model weights: original MeinaHentai checkpoint (OpenRAIL-M), SHA256 `deb40a068adf41aa2466dc8e76b516216af1c1afe721dfd23fc2c1108b4d152f`.
Always retain model and upstream attribution, comply with license requirements.

This handoff is an implementation checkpoint, not proof of successful physical phone inference.
