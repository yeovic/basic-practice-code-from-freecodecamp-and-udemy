# Velvet Atelier Studio v3.0 — All-in-One Architecture and ML Evaluation

**Scope:** One Android APK containing a single copy of the bundled MeinaHentai SD1.5 checkpoint and every locally implemented studio. No companion apps are needed. Previous separately signed packages cannot be silently replaced by this package.

## Combined Android modules
- VelvetHubActivity — model status, storage, diagnostics and studio navigation.
- Local Dream v2.8.1 MainActivity — genuine SD1.5 MNN on-device diffusion backend.
- IncludedModelProvider + ModelArtifactVerifier + ModelStorage — bundled model conversion, verification, retry and storage preference.
- GuidedStudioActivity + PromptBuilder + QualityTuning — adult character presets/cards, freeform, face priority, separate size controls and saved editable style cards.
- LongPromptRunnerActivity + LongPromptPlanner — **experimental sequential native BPE-verified image-to-image passes**, not native single-pass 154/231 token CLIP attention.
- PixelStudioActivity + PixelEngine + GifWriter — offline pixel animation and sprite/GIF export.
- ImageUpscaleActivity + ImageResampler — fast bilinear, Lanczos3 and nearest-neighbor PNG enlargement. These are **not ML super-resolution**.
- ModelDiagnostics — copyable device RAM, storage and converted-model readiness.
- Profile export/import JSON — migrate styles and adult character cards manually from older installed releases.

## User migration
Velvet Studio v3.0 uses Android package `org.velvetatelier.studio`.
Prior app package names `org.velvetatelier.quality28`, `org.velvetatelier.upscale26`, etc. have different package IDs AND signing certificates.
To transfer profiles: in v2.8 Guided Studio select Export Cards and save JSON to the shared Documents folder. Install v3.0, then Guided Studio > Import Cards.
Old converted model files are stored inside each previous Android app-specific directory and are NOT migrated or accessed automatically. Android scoped-storage rules prevent transparent cross-package access; the new single app will prepare its own bundled model. Once v3.0 has been successfully tested and profiles are imported, the user may independently uninstall obsolete versions to reclaim their models. DO NOT instruct uninstall before exporting profiles.
An APK can update an existing installed package only when package ID and signing certificate match. The v3 workflow accepts a private GitHub Actions signing keystore secret. Without that secret, CI creates an ephemeral *non-upgradable* signing key; this is suitable only for a prototype and is labeled accordingly. Do not put private keystores in source control.
Required secrets for stable private signing: `VELVET_SIGNING_KEYSTORE_B64` and `VELVET_SIGNING_PASSWORD`; alias `velvet`. Generate keystore privately, keep offline encrypted backup, then configure the base64-encoded keystore and password in GitHub Actions secrets. A stable key must be configured *before* the first long-lived release to support future in-place updates. Current v3 release may use ephemeral key if no secrets exist.

## External ML candidate audit (not included in v3)
| Job | Candidate | Mobile viability | Integration status |
|---|---|---|---|
| Anime 2x/4x learned super-resolution | Real-ESRGAN x4plus-anime-6B via NCNN/Vulkan | Promising compact anime model; needs Android native ABI / Vulkan support, thermal/VRAM profiling, model-weight license audit | **Investigated, not bundled** |
| Anime denoise/enlarge | RealCUGAN / Waifu2x (RealSR-NCNN-Android) | Android implementations exist; licensing and shipped binary provenance need review | **Investigated, not bundled** |
| Realistic portrait face restoration | GFPGAN / CodeFormer | Trained primarily for photographic faces and may distort anime character identity, especially distinctive stylized features | **Not appropriate as default** |
| Few-step latent diffusion | LCM / LCM-LoRA SD1.5 | Could reduce steps; requires scheduler, LoRA support and checkpoint compatibility with this MNN backend; visual benchmark mandatory | **Research only, not enabled** |
| Pose/depth/segmentation | ControlNet/OpenPose/MobileSAM | Useful for difficult interactions and selective correction; often too much memory/storage for this single-model phone package | **Future optional component** |
| Face landmarks | MediaPipe/BlazeFace | More practical first step for targeted face cropping; small inference graphs possible | **Future optional component** |
| Video frame interpolation | RIFE/FILM | Nontrivial inference storage/latency; Pixel Loop is currently deterministic frame motion | **Not bundled** |

References:
- https://github.com/xinntao/Real-ESRGAN
- https://github.com/tumuyan/RealSR-NCNN-Android
- https://github.com/TencentARC/GFPGAN
- https://github.com/sczhou/CodeFormer
- https://github.com/luosiallen/latent-consistency-model
- https://developer.android.com/guide/app-bundle/faq

## Concrete optimization changes in this pass
1. All existing modules combined in one package/version instead of independent installations for feature subsets.
2. Shared-image intake for the in-app upscaler via Android SEND/VIEW image intents.
3. Optional OpenCL GPU computation in experimental long-prompt runner, default off because Galaxy Note20 Ultra backend stability/driver behavior is unknown.
4. Fixed a bitmap lifecycle race: do not recycle intermediate-pass Bitmaps still shown by an ImageView.
5. Place user-requested scene tags before low-priority visual decoration in SD1.5's limited text-conditioning window.
6. Retain first-run SHA-256 verified extraction reuse and full MNN artifact integrity checks.
7. Keep model count exactly one bundled full checkpoint (MeinaHentai); optional additional ML model packs require native validation and documented licenses before inclusion.

## Testing truth
GitHub CI checks JVM unit tests, Android API35 compilation, APK package structure, SHA-256 of embedded model and APK signing. **No successful on-device generation, AI upscaling, native multi-chunk attention, measured runtime acceleration, or stable signing continuity is implied by CI.** Phone testing and a permanent private signing certificate remain critical.

All retained third-party code/model permissions must be observed, regardless of personal/noncommercial use.
