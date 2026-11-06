# G.I KO KO MOVIE RECAP STUDIO

**Powered By I KO KO** · Android 10+ · ARMv7 target · Gemini AI + On-device Video Studio

G.I KO KO MOVIE RECAP STUDIO combines the existing Movie Recap workflow and its editing tools into one organized Android studio. Gemini handles supported cloud AI tasks; the phone handles video composition and export locally wherever device resources allow.

## Core architecture
- **Gemini API (cloud AI):** video understanding where supported, recap planning, narration scripts, translation and structured subtitle text.
- **On-device rendering:** the current app uses AndroidX Media3 Transformer for local video composition, overlays and export. This is not a separate cloud render server.
- **Voice:** Gemini TTS and optional Edge TTS integration, depending on settings and network availability.
- **Local tools:** subtitle burn-in, SRT workflows, watermark/logo overlays, video merge, crop/portrait tools, compression, slideshow, voice-over and BGM tools.
- **Build:** GitHub Actions builds the Android APK; GitHub is the source/build host, not the runtime rendering cloud.

## Existing features retained
- YouTube URL and local video-file recap entry points.
- Gemini video upload/analysis and recap planning.
- Recap duration, segment density, narration style and language options.
- Multiple Gemini voice choices, voice preview, speed controls and captions.
- Landscape 16:9 and vertical 9:16 output options.
- Intro/outro, watermark/logo options and BGM controls.
- Plan confirmation, transcript/ASR fallback settings and custom OpenAI-compatible provider settings.
- History, queue, result sharing, retry and saved outputs.
- Tools: Studio, Voice Lab, History, Crash Log, Watermark tools, SRT Export, Thumbnail Maker, AI Studio, Video Queue, Merger, Gallery, Cleaner, Video Lab, Text Recap, Slideshow, Voice-over, BGM Library, Compress, Subtitle Burn, Portrait and Watermark Burn.
- Settings for API keys, models, TTS engine, themes, language and usage tracking.

## Primary workflow
1. Select a YouTube link or a video file you own or are authorized to edit.
2. Choose recap length, segments, aspect ratio, narration style, voice and captions.
3. Press **START**.
4. Gemini performs the supported AI planning tasks.
5. The app composes the result on the Android device using Media3 Transformer.
6. Review, share or save the exported video and subtitle files.

## Device and rendering notes
- Target compatibility: Android 10 and 32-bit ARMv7 (armeabi-v7a) devices.
- The Redmi 9A has limited memory and thermal headroom. 480p/720p and shorter projects are recommended; 1080p may be slow or fail depending on source codec, storage and device temperature.
- Hardware encoding is device/codec dependent. The app must fall back to supported software paths where available.
- Local rendering requires free storage and battery/thermal headroom. Keep the app active during long exports if background execution is restricted.
- AI video upload and cloud analysis still require internet and are subject to Gemini API quotas, model availability and file limits.
- Offline editing is limited to tools that do not require a cloud AI/TTS request.

## Roadmap — additions, not claims of current availability
- A dedicated Local/Auto render selector and render diagnostics.
- Better job persistence, pause/resume and low-memory safeguards.
- Unified Short Drama workflow, subtitle styling/editor, episode continuity and batch project management.
- Optional AI lip-sync and advanced enhancement through separately configured providers.
- More robust local media processing and device-specific performance testing.

## API key setup
1. Create a Gemini API key at https://aistudio.google.com/apikey.
2. Open the app's Settings and enter the key.
3. Use the key test function before starting an AI recap.
4. Keep the key private. Do not publish it in screenshots, source code or public issue reports.

Gemini API pricing and quotas vary by model and account: https://ai.google.dev/gemini-api/docs/pricing

## Copyright and content permissions
Only import, edit, remove or replace watermarks on videos you own or are authorized to modify. Do not bypass DRM, login requirements, platform download restrictions or creator attribution.

## Build
GitHub Actions workflow: .github/workflows/android-build.yml
Repository: https://github.com/gikoko611/G-IkokoRecap

## Developer
I Ko Ko — **Powered By I KO KO**
