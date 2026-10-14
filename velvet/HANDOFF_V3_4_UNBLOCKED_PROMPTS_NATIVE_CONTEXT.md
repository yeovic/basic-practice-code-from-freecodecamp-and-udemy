# Velvet Atelier Studio v3.4 — Prompt Overflow Fix and Native Context Investigation

## User-visible behavior now implemented
- Guided Studio's **Open Generator + Import Preset** never blocks at 77 CLIP positions. It copies the **entire** positive and negative prompt into Local Dream's recognized parameter clipboard JSON and launches the original generator immediately. A brief Toast warns that *single-pass native SD1.5 may ignore later tokens*, but users can proceed.
- The former modal prompt chooser (Fast prioritized core / Automatic multipass / Review segments) has been removed from the normal Generate action. The separate automatic multi-pass tool remains available as an explicit option.
- `buildAndDisplay()` catches multi-pass planner caps instead of treating an overlong description as an invalid preset, so **even thousands of tags do not prevent opening the generator**.
- `PromptImportPolicy.java` is a pure-Java contract preserving every character of both prompts, including Unicode, with advisory-only warnings. `PromptImportPolicyTest.java` verifies >1200 long tags and long negative prompts survive `PromptBuilder.build()` and import unchanged.
- `PromptBuilder.java` raises the old 12,000-character freeform and extra-negative limits to 64KiB **safety bounds**, which are independent from the model attention-window count. Native single-pass still ignores tokens past 77.
- QA GitHub Actions `Velvet v3.4 Unblocked Prompts QA` last success run: `38493796873`. JVM tests and Android API35 Java compilation passed.
- Combined v3.4 packaging workflow `.github/workflows/velvet-generation34.yml`, one APK with original image generator, multi-pass, face repair, pixel animation, upscaling, guided profiles, storage/diagnostics and telemetry.

## Why simply raising 77 inside MNN is not safe
Verified against actual upstream Local Dream v2.8.1:
1. `app/src/main/cpp/src/TextEncoder.hpp`: `processWeightedPrompt(...,max_len=77)` explicitly stops at 75 content tokens plus BOS/EOS.
2. `app/src/main/cpp/src/Pipeline.hpp`: `Conditioning.seq_len=77` and `textSeqLen()=77`; cache keys depend on context size.
3. `app/src/main/cpp/src/PipelineSd15Cpu.hpp`: MNN text encoder tensor fixed at `{1,77,768}`, and MNN UNet cross-attention encoder_hidden_states tensor at `{2,77,768}`. Simply bypassing text-input truncation would overflow allocated buffers or mismatch MNN tensor shapes.
4. `app/src/main/cpp/CMakeLists.txt` hardcodes Qualcomm QNN SDK at `/data/qairt/2.39.0.250926` and imports Qualcomm SampleApp + libraries alongside MNN dependencies. A verified replacement ARM64 `libstable_diffusion_core.so` requires a licensed QNN SDK installation and complete native build or a substantial MNN-only build refactor. Current GitHub Actions repackages the prebuilt upstream executable; no native recompilation currently runs.

### What other engines do (research, not shipped native)
AUTOMATIC1111's web UI implements >75 content tokens by independently encoding each ~75-token segment through CLIP and concatenating hidden embeddings. With a two-chunk SD1.5 prompt this gives `(1,154,768)` instead of `(1,77,768)`. Its UNet must dynamically accept the larger sequence length, and positive/negative contexts must be padded to compatible lengths. This is a **single diffusion denoising pass** with larger attention context and extra cross-attention compute/memory, unlike Velvet's current sequential img2img workaround.

References:
- https://github.com/AUTOMATIC1111/stable-diffusion-webui/wiki/Features#infinite-prompt-length
- https://github.com/xororz/local-dream (tag v2.8.1)

### Real native implementation path (NOT yet accomplished)
- Establish a reproducible MNN-only Android ARM64 build of Local Dream core or a fully licensed native QNN build; do not patch binary strings or ship native code that fails device tests.
- Implement dynamic `seq_len=77*N` in TextEncoder -> PromptPair -> Conditioning -> PromptCache -> PipelineSd15Cpu. Divide each prompt into **exact native BPE** groups of 75 content tokens with proper BOS/EOS/weight handling; encode chunks with the existing 77-input CLIP model and concatenate 77*768 hidden rows.
- Resize UNet conditioning input to `{2,77*N,768}` and confirm the converted MNN UNet graph does not bake 77 into internal reshape/attention shape operators. If incompatible, re-export/reconvert with dynamic sequence axes (and potentially update the MNN converter). Run CPU and OpenCL tests on actual target hardware.
- Add opt-in capped chunk choices (1,2,3,4), thermal/PS S tests, cache-version separation, proper negative padding and graceful fallback to one native pass. Do not market this as working until a phone-render comparison verifies late words.
- Use our current **automatic sequential img2img pass renderer** when N>1 and dynamic native attention is unavailable. Users can *always* still generate a normal render with the entire text imported; overflow may not affect native output.

## Signing and upgrades
Current v3.x releases used temporary per-run signing keys because private keystore secrets were not configured. `org.velvetatelier.studio` v3.4 cannot update a differently signed old Studio package. Export profiles first, uninstall the older installed Studio only after verifying backup, then install and reconvert the model.
Configure a persistent encrypted signing keystore via GitHub Actions secrets before promoting a future release to permanent update continuity.

## Quality/performance truth
Source tests and signed APK structural checks do not establish real-image quality or actual inference speed on Galaxy Note20 Ultra; performance telemetry is already available in Studio v3.3+. Changes made here primarily restore UX and preserve input detail, not an unmeasured increase in native single-pass context.
