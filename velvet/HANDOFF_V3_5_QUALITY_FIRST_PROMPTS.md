# Velvet Atelier Studio v3.5 — Quality-First CLIP Conditioning

Date: 2026-10-14 (KST). GitHub branch: `velvet-atelier-apk`.

## Changes implemented
- `velvet/PromptBuilder.java`: moved core anatomy/body tags including effective independent length and girth ahead of optional long `settings.userPrompt` descriptions. Previously, a long user prompt could displace critical size instructions outside the current native SD1.5 77-position CLIP window, leading to oversized or distorted results despite selecting smaller proportions.
- Moved built-in and editable style-card tags earlier in positive conditioning (including the user-requested **Boukoku Daitoryou inspired** approximate visual-novel style). The style was previously appended after clothing, hair, eyes, full framing and optional descriptions and could be truncated. It is still ONLY an unverified prompt-level approximation, not a trained style LoRA or a faithful copy of the original character designs.
- Collapsed duplicated nude/exposure tags, retaining the regression-tested `(nude:1.35)` and clearly specified scene mode at the beginning of the text. When explicitly selected, two-adult scenes retain `consensual adult sex scene`, adult status and framing, without contradictory solo tags. For clothed scenes, nude tags are still absent. These changes make better use of 75 content tokens without changing the model weights.
- `velvet/QualityTuningTest.java`: new ordering/regression checks ensure size and style precede optional user scene prose and the main exposure weight appears only once.
- CI workflow now explicitly listens for both `velvet/PromptBuilder.java` and `velvet/PromptBuilderTest.java` edits, fixing a source QA trigger omission.
- **Long-prompt import kept lossless/unblocked** from v3.4: Guided Studio passes the full positive and negative text to the original generator even above 77 positions, with advisory native truncation and an optional separate multi-pass img2img mode. Do not block normal generation or claim native multi-chunk cross-attention works.

## Verification
- Full `Velvet v3.5 CLIP Priority QA` GitHub Actions run `38549726266`: PASS (native model readiness test, anatomy matrix/prompt tests, 1,200-tag import test, face/hand/body repair regression, compositing pixel-identity, image resizing, Android API compile).
- APK workflow `.github/workflows/velvet-generation35.yml` builds one all-in-one `org.velvetatelier.studio` v3.5 package with bundled full Meina checkpoint. Verify job and actual GitHub Release asset before offering a direct installer link.
- Actual Samsung Galaxy Note20 Ultra generation/inference quality, speed, thermal behavior, prompt priority effect on image realism and OpenCL stability remain **NOT TESTED**. No new ML checkpoint or model retraining in this pass.

## Research / upcoming backend work
Prior `velvet/HANDOFF_V3_4_UNBLOCKED_PROMPTS_NATIVE_CONTEXT.md` confirms the real native 77-position limitation and exact-BPE chunk proof-of-concept `velvet/native/ClipChunker.hpp`; prototype pure C++ tests passed, but native implementation needs dynamic MNN UNet attention reshaping and ARM64 build from source. Reuse mobile-ML research from `POCKET_CANVAS_v0.9_ENGINEERING_HANDOFF.md` for Real-ESRGAN anime 6B, native tiling and quantization benchmarks. Do NOT bundle PyTorch .pth as if MNN could load it.
Practical next: compare same seed/prompt/steps before/after v3.5 at 512 square; compare short and long scene inputs, test saved style fidelity, repair image identity, capture LocalPerformanceLog CSV from device. ADetailer-like anime face detection and tiled neural upscaling can be optional packs only after model/runtime compatibility and mobile speed tests.

## Update/signing limitation
Existing v3.x builds use short-lived per-run Android signing keys if the two private signing secrets are absent. New APK cannot upgrade a previous signed APK with same package ID. Back up character/style JSON *before* uninstalling an older Studio; then install newer version and prepare included model once. For future genuine updates, configure `VELVET_SIGNING_KEYSTORE_B64` and `VELVET_SIGNING_PASSWORD` securely, keep keystore offline, use same certificate for all future releases. Never commit keys.
