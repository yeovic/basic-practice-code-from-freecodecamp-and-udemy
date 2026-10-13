# Velvet Atelier v2.8 — Quality, Profiles, and Long Prompt Engineering Handoff

Date: October 13, 2026. Source branch: `velvet-atelier-apk`.

## Implemented and tested
- `QualityTuning.java`: explicit face-priority / natural proportions / scene complexity modules. Puts high-value prompts earlier than optional rendering tags to mitigate truncation. Distortion negatives for eyes/jaw/mouth.
- `PromptBuilder.java`: independent anatomy length/girth/testicle size; preserves older single-size card semantics if dimensions are untouched; moves face/proportion cues earlier; corrects contradictory negatives; supports original freeform-only behavior.
- `GuidedStudioActivity.java`: displays controls; locally stores style cards and lets you edit any rendering properties. Preset "Boukoku Daitoryou inspired" is clearly an UNVERIFIED style approximation, not a guaranteed exact look; a reference-derived custom card is preferred. Adds long prompt planner and experimental local automatic multi-pass button. Style cards are included with exported/imported profile backups.
- `LongPromptPlanner.java`: deterministic semantic splitter with no discarded clauses. It only estimates token budget; **exact** SD1.5 BPE token count comes from Local Dream native endpoint.
- `LongPromptRunnerActivity.java`: new local-only in-app runner uses **Local Dream's actual native API**: starts `BackendService` with `sd15cpu`, probes `http://127.0.0.1:8081/health`, checks chunks using `POST /tokenize` (native CLIP tokenization), runs `POST /generate` SSE response and passes prior PNG as img2img input with denoise ~0.28 for additional chunks; saves final PNG. No Internet/cloud use. This feature is NOT yet tested on Samsung hardware and may be slow, alter composition or fail.
- `QualityTuningTest.java`: face, size, style, freeform and long-pass preservation regressions.
- Standalone QA: run 38381177262 passed quality regressions and Android API35 compile. Expanded QA with actual Android local API runner: run 38381771843 passed.

## Native 77-token hard limit and honest workaround
The Local Dream v2.8.1 source fixes CLIP text sequence and the SD1.5 CPU UNet attention tensor to 77 positions:
- `app/src/main/cpp/src/TextEncoder.hpp`: `processWeightedPrompt(...,max_len=77)`, `processPromptPair(...,max_len=77)`.
- `app/src/main/cpp/src/PipelineSd15Cpu.hpp`: CLIP input and UNet context tensor resized to `{1,77,768}` and `{2,77,768}`.
- `app/src/main/cpp/src/Pipeline.hpp`: `textSeqLen()=77`, cache/prompt buffers use this.
- `app/src/main/cpp/src/main.cpp`: `POST /tokenize` native exact token count and `POST /generate` event-stream generation; GET `/health`.
A single native pass STILL has 77 positions (75 content tokens + BOS/EOS). **The new multi-pass runner truly processes extra prompt chunks via additional img2img renders**. It does NOT concatenate a 154+/231+ CLIP sequence into a single denoising pass, so fidelity is not equivalent to AUTOMATIC1111's native long-prompt conditioning. Claiming the native limit was removed is false. Experimental full-native solution should rebuild `libstable_diffusion_core.so` with either conditional embedding fusion (keeps UNet 77) or dynamic concatenated context only after native MNN compatibility testing.
Reference Hugging Face community long prompt pipeline: https://github.com/huggingface/diffusers/blob/main/examples/community/lpw_stable_diffusion.py. A 75-token chunk plus BOS/EOS, individually encode and concatenate CLIP outputs; MNN model tensors may be incompatible with doubled attention context.

## Other Image Generation Research chat
Personal-context retrieval on 2026-10-13 confirms Pocket Canvas and related local research: SD1.5 MNN conversion, GGUF quantization trials, warm backend reuse, LCM and SDXS lightweight model candidates; alternative OpenCL / Vulkan paths remain experimental and there is no phone performance benchmark. Do not claim unverified backend speedups or silently replace Meina model weights with quantized data; measure before choosing storage/quality trade-offs. Prior source-only QA is not proof of actual Samsung inference.

## Source provenance and next
Local Dream upstream (CC BY-NC 4.0): https://github.com/xororz/local-dream
Meina checkpoint (OpenRAIL-M): https://huggingface.co/sinkinai/MeinaHentai
User target device: Samsung Galaxy Note20 Ultra (Android 13), CPU/GPU path.
Keep everything on-device, personal noncommercial; maintain licenses/provenance. Characters clearly adults only.

Immediate next engineering: test actual conversion/inference on Galaxy; inspect CLIP token count and long-runner backend service lifecycle on device; native contextual multi-chunk support, stable signing key/package and optional lightweight upscaler pack; compare warm/cold RSS/performance and native resolutions.
