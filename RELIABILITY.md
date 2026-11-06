# G.I KO KO MOVIE RECAP STUDIO — Reliability Engine

This document describes the reliability and recovery behavior added to the Android app.

## Processing architecture

- AI analysis and speech generation use the configured cloud providers.
- Video composition and Media3 rendering remain on the Android device.
- No custom cloud backend is required.
- Single-video jobs and sequential queues use Android WorkManager with a foreground notification and a connected-network constraint.

## Recovery behavior

### Gemini API and model selection

- Gemini model discovery checks the account's available generateContent models before a Flash-model job starts.
- If the configured Flash model is not available, the app selects an available Flash model from the API response.
- Transient HTTP/network failures use bounded retries with exponential delays and jitter.
- HTTP 503 after retries can trigger one alternate available Flash model.
- YouTube transcript fallback is attempted when enabled and a transcript-provider key is configured.
- Invalid API keys, unsupported inputs, and permanent permission errors are not blindly retried.

### Upload and File API checkpoints

- Gemini resumable upload uses adaptive 100 KiB–1 MiB chunks.
- The acknowledged upload offset and session URL are stored in the app's no-backup private directory.
- After process restart, the app queries the upload session and resumes from the server-acknowledged byte offset.
- A completed Gemini File API reference is checkpointed for up to 47 hours (the File API stores uploaded files temporarily).
- Expired or unavailable file references are cleared and uploaded again.

### AI plan, voice, frame, and queue checkpoints

- A successful AI plan is saved locally for up to 7 days.
- TTS WAV files are cached by text, voice, language, speed, TTS engine, and model.
- YouTube/frame-based recap images are cached by plan and visual settings.
- Cache cleanup removes expired items and caps TTS/frame caches to help protect internal storage.
- Queue jobs save a per-video state file. Completed videos are skipped after restart or retry.
- Series queues pause after a failed chapter so later chapters do not silently continue with the wrong previous-part context.

### Background execution and duplicate protection

- WorkManager persists supported single-video, text-recap, and queue jobs across Activity destruction and ordinary process restarts.
- The foreground notification opens the processing screen again.
- Only one heavy background recap job is active at a time to protect low-RAM devices.
- Matching duplicate requests attach to the existing job; a different request is rejected while another job is active. Use Queue for multiple videos.
- If Android/MIUI battery management is aggressive, set the app battery usage to Unrestricted for more reliable background execution.

## Device and output protection

- Input file existence/readability and minimum storage are checked before processing.
- Low available RAM or severe thermal status selects a lower render resolution where supported.
- Rendered MP4 output is checked for a video track, an audio track, and a valid duration.
- SRT output is checked for the expected number of subtitle cues.
- Shorts and podcast outputs are checked for valid media tracks before success is reported.
- Failure details are retained in the processing log and app diagnostic log.

## Important limitations

- These protections improve recovery; they cannot guarantee 100% success when a provider is unavailable, a quota is exhausted, a video is inaccessible, or Android forcibly stops the app.
- A running Media3 export is not resumed at the exact encoded frame after process death. The app reuses saved AI plans, TTS audio, and cached frames, then reruns the final composition.
- A Gemini request that fails before a complete plan is saved may need to be sent again.
- Transcript fallback depends on the configured transcript provider and whether a transcript is available.
- YouTube and Gemini analysis require Internet access. Local Media3 composition is performed on-device.
- The in-app Drama Browser imports public direct video files/HTML5 video sources; DRM-protected, login-only, blob/MSE, and restricted streams are not bypassed.

## Recommended Redmi 9A settings

- Keep enough internal storage free for the source video and rendered output; the app blocks rendering below its minimum free-space threshold.
- Close memory-heavy apps before long renders.
- Prefer 720p for long videos on the Helio G25 device.
- Keep the app battery mode unrestricted if you want WorkManager jobs to continue reliably while the screen is off.
