# Velvet Atelier Studio v3.7 — Real generation efficiency + Repair Undo

Date: 2026-10-20 KST
Branch: `velvet-atelier-apk`
Source test run: [Velvet v3.7 Repair and Token Packing QA](https://github.com/yeovic/basic-practice-code-from-freecodecamp-and-udemy/actions/runs/39421006395) **PASSED** Android API35 compilation and all targeted unit tests.
Installer workflow: `.github/workflows/velvet-generation37.yml`, single full-model APK. Verify the workflow completion and published release asset before recommending any APK.

## Changes in source

**One-step face/hand/body region repair undo, memory bounded**
- `RepairUndoPatch.java`: snapshot only the original crop's ARGB data (e.g., a 256×256 patch occupies ~256 KiB) instead of cloning a full-resolution image. Stores its own copy; validates dimensions; lets the user retrieve a copy for one-step undo.
- `FaceRepairStudioActivity.java`: adds "Undo last region repair" directly after "Repair selected region locally". After successful native SD1.5 img2img patch + feather blend, preserve exactly one previous crop, which can be restored without losing earlier edits elsewhere. Reset history on loading a different image.
- Fixed a UI concurrency race: the worker previously flipped `busy=false` before the `main.post` callback actually applied its result Bitmap. Now the busy lock releases from the UI thread after result application, preventing another repair from racing against Bitmap recycling.

**Fewer expensive long-prompt generations**
- `NativePromptCompactor.java`: pure Java exact-token prompt packing. Existing `LongPromptPlanner` rough 36-word chunks are first verified with the native `POST /tokenize` endpoint; the compactor greedily merges adjacent safe chunks where the **actual** SD1.5 BPE count remains <=77. The merged text stays in the original order and is reverified.
- `LongPromptRunnerActivity.java` uses the resulting list to drive the number of native image-to-image passes. This may save full-image passes compared with rigid word-count segmentation; actual speed/quality changes still depend on the input prompt and must be measured.
- No change to the hardcoded native SD1.5 UNet cross-attention sequence length (77 positions). NOT equivalent to AUTOMATIC1111-style concatenated longer contexts. All model weights unchanged.

**Tests**
- `NativePromptCompactorTest.java`: verifies that three short chunks compact into two safe native-budget passes, preserves order, rejects an oversized chunk and honors max-passes.
- `RepairUndoPatchTest.java`: verifies memory-sized region snapshots, immutable ownership, restore fidelity and malformed-shape rejection.
- Full Android source compilation and retained portrait/landscape, negative prompt, face repair, upscaler, GIF, model integrity and prompt tests passed on run 39421006395.

## From other mobile ML research
- [Real-ESRGAN x4plus anime 6B](https://github.com/xinntao/Real-ESRGAN) is a compact anime-specific model. A preconverted NCNN/Vulkan format exists but is not an Android inference runtime by itself; native Vulkan/NCNN integration, Android ABI, tile overlap, thermal limits and licensing remain to validate.
- Live source review confirms upstream Local Dream 2.8.1's `/health` returns HTTP 200 without reporting model ID/resolution. Backend readiness after a rapid model or resolution switch could theoretically observe the old service. A future robust fix should corroborate Kotlin BackendService's servingModelId/servingResolution with /health rather than treating any HTTP 200 as the exact target model, and test on the actual device.
- For *true* >77-token native attention, first rebuild MNN ARM64 engine and test dynamic seq_len in CLIP/UNet. Existing C++ chunk proof-of-concept is source-only.
- More useful device-level tasks: repair selected-face comparisons at same original image and denoise, sample 512x512 & 512x768 after multiple passes, compare CPU vs experimental OpenCL, actual PSS and thermal data via `LocalPerformanceLog` CSV. No device results yet.

## Shipping caveats
- Prior Studio APK builds typically use independently generated signing keys. Without a private persistent key in `VELVET_SIGNING_KEYSTORE_B64` + `VELVET_SIGNING_PASSWORD` GitHub Actions secrets, the new APK **cannot install as an in-place update** over an older Studio of the same package ID. Export existing character/style cards to JSON, verify the backup, then uninstall older Studio and install new.
- Local Dream CC BY-NC 4.0 and MeinaHentai CreativeML OpenRAIL-M license/provenance must remain in release notes.
- CI verifies source and package structure, not perceptual image quality, hardware performance, actual native model conversion or Android runtime stability.
