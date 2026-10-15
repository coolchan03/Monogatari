# Monogatari 0.13.10 — in-app entertainment podcast and video playback

Built from verified 0.13.9 on Android-only `devel`.

## What is implemented

- Play public HTTPS podcast and video-RSS enclosures *in Monogatari* using the
  existing AndroidX Media3 ExoPlayer instead of requiring an external app.
- The feed screen has an explicit Play action per playable episode; its item
  menu still offers the official page and another media player.
- Saved playback position is restored on reopening, with a SHA-256 key instead
  of storing episode stream URLs in history preferences.
- Chapter lists come from Podcasting 2.0 JSON and chapter taps seek the active
  player to the selected timestamp. Invalid or oversized lists show an error.
- HTTPS caption sidecars (.vtt, .srt, .ttml, .ssa/.ass) attach to Media3 as
  actual subtitle tracks, with browser links retained for transcripts.
- Missing RSS artwork gets an original book-style generated cover differentiated
  by AUDIO or VIDEO rather than the reused generic source image.
- Controls adapt to narrow screens via a horizontally scrollable 48dp action row,
  and video-only screen-awake behavior applies while actually playing.
- Player navigation uses the shared Monogatari toolbar, with normal Android
  pause/release on leaving. Full background podcast playback via the app's
  separate MediaSessionService is still a later enhancement.

## Verification requirements

- Unit tests for allowed URLs, subtitle types and chapter-seek positions.
- GitHub-hosted unit tests, debug build, optimized release build, Android cold
  startup. No emulator or SDK installation on any of the user's devices.
- On-device test suggested fiction podcast with `podcast:chapters`, a feed
  with actual VTT captions, rotation, sound interruption and seeking.
- DRM, private authentication, and region-restricted streams remain under
  the original content providers' rules; unsupported URLs open official sites.

## Roadmap

Requirement #60 is implemented in code. Physical feed compatibility and
background playback improvements remain tracked separately from the core
in-player caption and chapter requirements. Other 25 partially implemented
Android items remain open.

\n