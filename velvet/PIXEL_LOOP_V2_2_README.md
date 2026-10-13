# Velvet Atelier v2.2 — Offline Pixel Loop Studio

Personal/noncommercial Android fork of Local Dream with a bundled MeinaHentai v2.1 checkpoint,
native Pixel Loop Studio, and a first-launch model status/repair dashboard.

## Features
- Open the existing local Stable Diffusion image generator from the new home screen.
- View first-run bundled model extraction and conversion status, latest error, and Retry.
- Prioritize removable SD-card application storage when available, internal fallback.
- Import an image to pixelate at 32, 48, 64, 96, 128, or 192 pixels with one shared palette.
- Build 4–24-frame loops: Breathing, Sway, Bounce, Walk, Float or Pulse.
- Edit individual frames by pixel painting, mirroring, duplicating and deletion.
- Save animated GIF, sprite-sheet PNG, a single-frame PNG or sprite ZIP with JSON.
- A copied pixel-art prompt can be used with the existing local anime model.

## Model installation
Although the model checkpoint bytes are INSIDE the APK, Android must convert them
to a compatible local MNN format before they appear under Custom Models.
Watch the home-screen status. READY means conversion finished; FAILED includes an error
and a Retry option. Reopen Open AI Generator after READY to refresh its model list.
Look for MeinaHentaiV21 in the custom models section.
SD-card extraction and conversion require substantial free space. Do not eject the card
during conversion. If it fails, note the status message.

## Verification and limitations
The build checks Java compilation, GIF pixel/alpha round trips, package signature,
ARM64 inference libraries and the included checkpoint. It has NOT been installed
or inference-tested on the user's physical Samsung device.
Animation is deterministic pixel motion on source artwork: it is not AnimateDiff,
dedicated pixel-style fine-tuning, or true text-to-video.

## Credits
Local Dream by xororz and contributors (CC BY-NC 4.0):
https://github.com/xororz/local-dream
MeinaHentai v2.1 checkpoint (CreativeML Open RAIL-M):
https://huggingface.co/sinkinai/MeinaHentai
Pixel tools and diagnostic additions are independent noncommercial modifications.

## Source
PixelEngine.java, GifWriter.java, GifWriterTest.java, PixelStudioActivity.java,
VelvetHubActivity.java, ModelStorage.java, IncludedModelProvider.java,
and .github/workflows/velvet-pixel22.yml