# Velvet Atelier — Pixel Art & Animation Track (v2.2 design)

## Product goal
Fully offline, adult-character 2D pixel illustration and short loop creation on Android.
No cloud, no subscription, keep intermediate frames editable. The existing SD1.5 adult
anime engine and its license credits remain intact.

## Prioritised delivery
1. **Make external storage dependable**: verify microSD detection, native file-path
   loading, out-of-space recovery, and SD removal/reinsertion. Preserve internal
   fallback. Avoid deleting user work on model conversion failures.
2. **Static pixel art**: SD1.5 checkpoint + optionally compatible pixel-art LoRA;
   produce a master character pose, normalize all frames to one grid and one
   coherent palette. Expose logical sizes 32, 48, 64, 96, 128px; nearest-neighbor
   scaling only (no smoothing). Transparent background is optional.
3. **Animation editor**: timeline of 2–16 editable frames, onion-skin previews,
   frame hold/duplicate/flip, per-frame duration, seed and prompt lock, auto-loop.
4. **Consistency-first motion**:
   - deterministic transforms for blink, breathing, bob, bounce, camera drift;
   - optional editable layered rig (head/torso/arms/legs/hair/clothes);
   - frame-level img2img with low denoising only for selected parts, preserving
     pose, silhouette, viewpoint and shared palette;
   - keep a common neutral keyframe and support manual frame replacement.
5. **Exports**: lossless numbered PNGs, spritesheet PNG + JSON metadata, GIF,
   animated WebP/APNG where Android export support is verified. Optionally MP4.
   In each export preserve the pixel grid and consistent canvas origin.
6. **Real temporal diffusion**: optional experimental AnimateDiff motion adapter
   if a device-capability test succeeds; likely too slow/memory heavy for
   default use on a 2020-era Snapdragon phone. Do not promise real-time video.

## Modes
- Single character sprite portrait or full body.
- Loop: idle / breathing / blinking / waving / turn / sway / walk / custom.
- Scene: camera pan, foreground/background parallax.
- Sprite-sheet factory: fixed resolution, 4–12 frames, palette locked.

## Architecture
ModelProvider -> GenerationQueue -> SourceImage -> PixelGridNormalizer ->
SharedPaletteQuantizer -> Timeline / LayeredMotion -> FrameStore ->
FrameRenderer -> SpriteSheet + AnimatedImageExporters.

The default animation path deliberately does NOT run 16 full SD inference passes:
expensive diffusion generates one quality keyframe; deterministic rig/offsets
and small corrections produce the cheap intermediate frames. Separate animation
rendering from model inference, making the timeline usable while offline and
without continuous GPU/NPU inference.

## Quality invariants
- Every frame has the same logical WxH, pivot, and base sprite scale.
- No interpolated/bilinear pixels in final game sprite output.
- Shared palette, no random palette flicker.
- Model identity (visual consistency), seed and preset saved per project.
- Loop seam checked: frame N -> frame 0 should not teleport/displace.
- Supports pause/cancel and persistent progress to survive Android lifecycle.
- Model path resolved through ModelStorage, not hardwired Context.filesDir.
- Bundled adult-themed checkpoint stays attributed; pixel LoRAs require verified
  provenance/license before distribution.

## Relevant mining references
- Local Dream (Android MNN/SD1.5): https://github.com/xororz/local-dream
- AI Sprite Animation (pixel-preserving rig strategies):
  https://github.com/JatCool/AI-Sprite-Animation
- Aseprite AI Generator (palette quantization/LoRA workflows):
  https://github.com/TangTaksin/Aseprite-AI-Generator
- Diffusers AnimateDiff documentation (optional heavyweight experimental mode):
  https://github.com/huggingface/diffusers/blob/main/docs/source/en/api/pipelines/animatediff.md

## Not yet implemented
This is a design checkpoint. Pixel LoRA inference, scene-specific motion presets,
timeline GUI and animation encoders are not part of the v2.1 SD-card APK.
