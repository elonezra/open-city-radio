# Implementation status and validation

Prepared 2026-09-11 against the supplied Open City Radio v0.2 specification and screenshot/sprite archive.

## Checks actually executed

| Check | Result |
|---|---|
| Production Java live-clock and ZIP utilities, compiled with Java 17 compiler module | PASS — 21 checks |
| Live advancement, looping, negative dates/offsets, zero duration rejection, deterministic station/day seeds, overflow arithmetic | Included in the 21 passing checks |
| ZIP traversal/absolute/Windows paths, canonical duplicate aliases, valid extraction, no escape write | Included in the 21 passing checks |
| Neutral demo pack, 3 worlds / 5 stations | PASS — metadata, all local references, WAV lengths |
| Private reference demo pack, 3 worlds / 5 stations | PASS — metadata, all local references, WAV lengths |
| Android source compilation, lint, and JUnit parser tests | NOT RUN — Android SDK and Gradle absent; download blocked |
| APK installation, rendered-screen inspection, actual playback and lifecycle integration | NOT RUN — no Android device or emulator |
| Bluetooth/headphone/head-unit button behavior | NOT RUN — hardware required |
| Remote downloads and format matrix on Android | NOT RUN — runtime/network validation required |

Do not treat the source checks as a successful Android build. The GitHub Actions workflow is the next validation gate and may reveal integration fixes needed before an APK is usable.

## Implemented in source

- Media3 foreground playback service, media session and station-oriented next/previous handling.
- Live re-tuning and Archive position saving; repeat-current-station playback.
- Audio focus handling, headphone-disconnect pause, wake lock, media notification supplied by Media3.
- Native responsive views, phone/car sizing, radio dial, fallback station artwork, supplied playback sprites.
- Worlds, station search, favorites, year/universe filters, world-scoped Road Trip, mini player.
- Built-in, external JSON, SAF folder and ZIP imports, reload, local-first remote asset resolution.
- Data-driven colors/logo/background, Coil image decoders and image fallback.
- Parked-use messaging, manual car lock, double-confirm library deletion, no telemetry.
- Demo audio generation, pack validator, parser tests, and GitHub APK workflow.

## Remaining verification and limitations

The native UI follows the layout/palette direction but is not a pixel-perfect recreation of the rendered reference illustrations. The provided badge PNGs are simplified artwork. No Android screenshot comparison has been performed. Small landscape displays may need spacing adjustments after on-device review.

One manifest/pack is active at a time. Prior successful app-owned imports remain on disk until Clear Library. There is no automatic motion detection, Android Auto media-browsing integration, theme-file registry, checksum verification, download resume, or OAuth. These are optional or later-stage features except where noted in the manifest contract.

Downloads show transferred size and percentage when the server supplies a total. Retrying is manual. Local audio is checked for existence during reload; codec compatibility and remote assets are checked at first use. No actual soundtrack music is included. Live status reflects player state, not an invented now-playing song title.

## Required device acceptance pass

1. Build with the workflow. Resolve all compile/lint/test failures before distribution. Install the resulting debug APK on Android API 29 and a recent API level.
2. Play all five demo stations, leave one playing across its loop boundary, and verify no silence/crash at wrap.
3. In Live, pause for three minutes and resume; compare the offset modulo the known duration. Repeat after force-stop/relaunch. Travel/time-zone changes must not alter UTC positioning.
4. In Archive, seek, pause, switch stations, restore, and relaunch; confirm saved positions. Switch from Live to Archive and ensure live position did not overwrite archive state.
5. Send headset/Bluetooth Next and Previous at the first/last station. Verify wrapping and world scope. Confirm notification controls and background/screen-off playback.
6. Rotate phone and tablet, inspect all screens at 360dp portrait and a representative head-unit landscape size. Test increased font size and TalkBack descriptions. Ensure play controls remain comfortably reachable.
7. Import valid ZIP, nested-root ZIP, missing-audio ZIP, invalid JSON, traversal ZIP, oversized ZIP, duplicate path ZIP, and a low-space case. Confirm failed imports retain the previous active pack.
8. Select a SAF folder from internal storage and USB. Reboot. Test revoked permission and unplugged USB; no crash is acceptable.
9. Select external JSON, add/remove a station, reload, then return to built-in content. Verify media selection and filters contain no stale station references.
10. Test remote audio, mixed sources, PNG/JPEG/WebP/SVG/GIF, invalid images, expired Drive links, HTML confirmation pages, offline reuse, and interrupted downloads.
11. Confirm car lock prevents browsing/settings until parked confirmation. Verify Clear Library retains original source files and removes only app-owned copies.

## Technical references

The media service/session approach follows [Android's background playback guidance](https://developer.android.com/media/media3/session/background-playback) and [MediaSession control documentation](https://developer.android.com/media/media3/session/control-playback). Storage follows [app-specific storage](https://developer.android.com/training/data-storage/app-specific); actual OEM behavior still needs testing.
