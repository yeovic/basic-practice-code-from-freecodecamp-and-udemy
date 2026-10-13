# Velvet Atelier v2.5 — optimization source checkpoint

Status: source implemented and QA-passed. NO new v2.5 APK was published.
The existing v2.4 APK does not contain these changes.

## UX
- Character cards have conditional size controls for selected visible anatomy;
- Size choices: Very small / Small / Average (default) / Large / Very large.
- Independent state/visibility settings and clothed bulge control.
- Size settings saved/restored on adult character cards.
- No specific size prompt when anatomy is not selected, tucked or concealed.
- For small/average sizes, add complementary negatives discouraging extreme proportions.
- Actual output depends on the checkpoint and seed; this does not guarantee exact measurements.

## Faster sampling, not benchmarked inference
- Fast: 10 preview steps / 18 final steps; suggests 384x384.
- Balanced: 16 preview steps / up to 28 final steps; suggests 512x512.
- High detail: 22 preview steps / 38+ final steps; suggests 512x512.
- Refine: 25, 36 or 44 steps depending on mode.
- Local Dream's JSON parameter importer supports step count, CFG, prompt, seed and scheduler.
- CRITICAL: The importer does NOT apply width/height; resolution must be chosen manually.
- Preview x4 still uses the upstream generator's existing batch setting, not a new parallel inference engine.
- Local Dream BackendService already keeps an existing backend alive for unchanged configurations.
  No redundant second backend process has been added. Changes in sampling steps usually affect
  work roughly linearly, but total latency depends on the device and model.

## Storage
- Auto: prefer writable removable microSD; fall back to internal.
- Speed: prefer internal files directory.
- Space: prefer removable microSD; fall back to internal.
- User can change preference on home screen; blocked during active model setup.
- Changing location DOES NOT migrate model files: conversion is needed if no ready model exists
  in the newly selected directory. Previous location is left intact.

## QA
Workflow: https://github.com/yeovic/basic-practice-code-from-freecodecamp-and-udemy/actions/runs/38348813573
Results: pure Java prompt/body/scene/size/negative/preview/repair tests PASS.
Android API35 Java compilation PASS.
Not tested on physical Samsung hardware: installation, model conversion, render times or image quality.

## Source
velvet/PromptBuilder.java
velvet/GuidedStudioActivity.java
velvet/ModelStorage.java
velvet/VelvetHubActivity.java
velvet/IncludedModelProvider.java
velvet/PromptBuilderTest.java
.github/workflows/velvet-v25-tests.yml