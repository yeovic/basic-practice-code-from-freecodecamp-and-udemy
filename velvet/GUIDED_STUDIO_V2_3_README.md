# Velvet Atelier 2.3 — Guided Image Studio

Personal, noncommercial Android arm64 application. Based on Local Dream v2.8.1 by xororz and contributors (CC BY-NC 4.0) with a bundled MeinaHentai v2.1 checkpoint (OpenRAIL-M).

## Contents
- Original offline Stable Diffusion image generator and freeform controls.
- Guided Studio with 9 scene presets, 7 adult-character identities and body, chest, hips, anatomy, presentation, hair, eyes, clothing and traits controls.
- Adult-only (18+) character cards saved locally in app preferences. Retain draft between sessions.
- User can always bypass positive prompt templates with Freeform Only.
- Model-aware SD1.5 style prompts, anatomy-aware negative prompts, configurable quality and seed.
- Guided preview recipe sends lower steps and instructions for the existing generator's batch count = 4.
- Refine recipe sends higher steps and anatomy tags; actual localized fixes use original generator's img2img/inpaint tools.
- Clipboard handoff uses Local Dream ParamShare JSON: _localdream_params=true, v=1; app prompts the user to import after selecting a model.
- Pixel Loop Studio from v2.2: pixel quantization, deterministic animation loops, timeline editing, GIF and sprite exports.
- MicroSD-preferred model directory with internal fallback, automatic model extraction/conversion, status and Retry.

## Guided workflow
1. Open the APK. On the hub, check Included AI Model Setup. If model says PREPARING or CONVERTING, it is not yet selectable in the underlying generator. If FAILED, note the message and use Retry.
2. Open Guided Studio. Pick a scene/character preset. Change body/anatomy and add freeform details. Save the character card if useful.
3. Tap Open Generator + Import Preset. This copies the supported parameters to the Android clipboard.
4. Tap Open AI Generator in the dialog. Choose MeinaHentaiV21 under Custom Models once its first-run conversion is READY. Accept the import dialog in Local Dream.
5. Generate. For 4 preview images use the Preview ×4 button and set Local Dream's batch count to four. Preview ×4 in Guided Studio is NOT a direct four-inference execution.
6. For refinement copy the Refine recipe. Use original generator's img2img/inpaint when you need actual local repairs; a same-seed prompt rerender does not guarantee repair.

## Limitations
- Actual generation/model conversion on the user's Samsung Galaxy has not been physically verified.
- Presets cannot guarantee good anatomy or consistent specific character identity. For Yuki, use a saved detailed character card and a compatible character LoRA if available; names alone aren't reliable visual identifiers.
- The shipped model is an adult-anime SD1.5 model, not a dedicated pixel model. Pixel animation is deterministic 2D sprite motion, not full text-to-video diffusion.
- On Android, preferExternal requests but does not enforce APK installation to microSD. Model files prefer app-specific removable storage when mounted.
- Model weights embedded inside APK may still require several GB free during first-run conversion.

## Tests in GitHub Actions
- GIF89a 9-frame and alpha roundtrip tests.
- Prompt builder matrix (scene x identity), adult-only checks, freeform, seed, preview and refinement tests.
- Android API35 Java compilation, Apktool remapping, DEX class presence, model SHA-256, native ARM64 core, APK signing verification.
- Note: These tests verify the build, not hardware-level inference.

## Files
velvet/GuidedStudioActivity.java, velvet/PromptBuilder.java, velvet/PromptBuilderTest.java,
velvet/VelvetHubActivity.java, velvet/PixelStudioActivity.java, velvet/ModelStorage.java,
velvet/IncludedModelProvider.java, .github/workflows/velvet-guided23.yml.