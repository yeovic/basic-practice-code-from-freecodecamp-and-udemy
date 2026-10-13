# Velvet Atelier v2.4 — Adult Scene Preset Repair

## Root cause fixed
In v2.3 the Guided Studio scenes called Solo adult scene and Two-adult scene only
expressed composition and character count. Neither added any nudity or sex-specific
tags. MeinaHentai was allowed to infer fully clothed pictures without contradicting
the prompt. The issue was *prompt composition*, not evidence of model censorship.

## New guided controls
- **Adult-content level:** Clothed / general (default), Adult nude, Consensual adult sexual scene.
- **Camera / framing:** automatic, face, waist-up, full figure, two-adult framing.
- Nude content adds explicit unclothed/nude tags and content-scoped clothing negatives.
- Adult sexual scene adds adult interaction and sexual-scene tags, with explicit adult subjects.
- Clothing tags saved in character cards are **not** appended to unclothed presets.
- Two-adult framing avoids the contradictory `1girl, solo` tag.
- Freeform Only remains genuinely freeform; no forced positive tags.
- Content/camera selections persist in the local draft, not in the character's fixed appearance profile.

## Important usage details
1. Open Guided Studio and choose desired adult-content level explicitly. Merely
   selecting Adult trans woman, Adult futanari or another body type does not
   automatically mean nude or sexual content.
2. Choose Solo adult scene or Two-adult scene and the requested framing.
3. Tap Build / Update Prompt. Verify that the Positive prompt contains `nude`
   (for Adult nude) or `sex` (for the adult sexual scene) and that negative
   prompt only excludes clothing when in nude mode.
4. Tap Open Generator + Import Preset. Select MeinaHentaiV21 and accept
   Local Dream's Import Parameters dialog. Confirm the prompt appears in its
   editable generator box. Generating without accepting import uses OLD settings.
5. If the output is clothed despite the correct positive prompt, try a different
   seed, less confusing scene text, check that an outfit description isn't in
   your user-added freeform prompt, and confirm the model really is MeinaHentai.

## Verified software provenance
Local Dream by xororz, unfiltered ARMv8a v2.8.1 release:
https://github.com/xororz/local-dream/releases/tag/v2.8.1
The upstream developer describes releases ending with `_with_filter` as NSFW-blocking
and releases without that suffix as unfiltered. Our source pins
`LocalDream_armv8a_2.8.1.apk` (no filter suffix).
MeinaHentai v2.1 model bytes are bundled, checksum-verified and converted locally
when setup reaches READY. Local Dream upstream CC BY-NC 4.0; checkpoint OpenRAIL-M.

## Tests and caveats
- PromptBuilderTest checks each character identity in clothed, nude and adult
  sexual-scene configurations, including prevention of outfit contradictions.
- Separate packaging pipeline runs JVM prompt tests, GIF tests, Android compilation,
  native-model asset checks, APK signature verification and release publishing.
- **Not verified:** real-life installation, conversion/inference quality and
  specific scene fidelity on Samsung hardware. Neither unfiltered engine nor
  explicit prompts guarantee anatomical correctness or any particular output.

Build: .github/workflows/velvet-content24.yml