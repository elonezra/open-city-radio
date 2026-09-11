# Open City Radio

Native Android source implementation of the supplied v0.2 specification. Kotlin UI, content management, and Media3 playback service; two small Java utilities keep the live clock and ZIP reader independently testable. Android 10+ (API 29), target/compile API 35.

**Delivery status:** source prepared; Android compilation, lint, installation, UI rendering, Bluetooth controls, and head-unit behavior have not been verified. This package is not an APK or a production release. The source includes the build pipeline needed to produce a debug APK in GitHub Actions.

## Build an APK with GitHub

1. Put the contents of this directory at the root of the chosen repository. Do not upload the sibling `private-content` directory from the delivery bundle.
2. Open **Actions → Android APK → Run workflow** (or push a commit).
3. The workflow installs Java 17, Android SDK, and Gradle 8.10.2, runs core checks, pack validation, unit tests, Android lint, and `assembleDebug`.
4. If all checks pass, download the **open-city-radio-debug-apk** artifact, unzip it, and install `app-debug.apk` on your Android device. Installation from the chosen file manager must be allowed on that device.

The workflow has been authored but not executed in this delivery. No GitHub repository was modified: the only matching installed radio repository was an unrelated Python system.

## Build locally

Install JDK 17, Android SDK Platform 35, and Gradle 8.10.2. Set `ANDROID_HOME` to the SDK directory (or `sdk.dir` in an untracked `local.properties`). From this directory:

```sh
gradle wrapper --gradle-version 8.10.2
./gradlew testDebugUnitTest lintDebug assembleDebug
```

On Windows use `gradlew.bat` after generating the wrapper. The wrapper JAR is not bundled because build-tool downloads were unavailable in the authoring environment. You can also configure Android Studio to use a local Gradle 8.10.2 distribution. APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Try the app

The built-in library contains five original synthesized demonstration loops across three fictional worlds. They are functional test audio, not commercial songs or game broadcasts.

- Choose **Try demo stations**, select a world, then play a station.
- **Live:** opening, switching, or resuming tunes to the current wall-clock position; pause does not freeze the simulated broadcast.
- **Archive:** resumes a saved position; seeking is available. Positions are saved every five seconds, on pause, on station change, and on orderly service shutdown. Abrupt termination can lose up to the last save interval.
- Choosing a world enables **Road Trip**: Next/Previous cycle only through that world's stations. Choose **All worlds** to clear it.
- Favorites, last station, world, year/universe filters, mode, and display settings persist locally.
- Car layout selects larger controls on wide devices, or can be forced on/off in settings. Car lock is a manual parked-use control, not a vehicle-motion sensor.

## Use the supplied design assets

The separate `private-content/reference-design-demo-pack.zip` in the delivery bundle contains the uploaded Flash FM, Fever 105, Wave 103, and V-Rock badge PNGs plus generated demo audio. Import it through **Library → Import ZIP**. It is deliberately outside this code directory.

The supplied PNG sprites are simplified badges and do not contain the elaborate neon logos, leather textures, city art, or phone frames illustrated inside the reference screenshots. The app uses those actual sprites, native controls, vector-like Canvas fallback art, and a neon palette. **Pixel-for-pixel fidelity to the mockups has not been achieved or verified.** Supply standalone background/logo files in your private pack for richer artwork. The neutral playback-button sprites provided with the request are used directly in the app.

## Import your content

- **ZIP:** include one `manifest.json` and relative asset paths. A single enclosing folder is allowed. Extraction occurs in a new staging directory. Malformed structure never replaces the active pack. Missing local audio appears in Pack Status; a failed station presents an error while the library remains available.
- **Folder:** select a folder containing `manifest.json` through Android's Storage Access Framework. Audio stays in the original folder (including accessible USB/SD storage). Keep the selected volume attached.
- **External JSON:** select a JSON file and grant persistent read access. Pack-relative assets use the selected content folder or the last app-owned pack root. To change both manifest and root, use **Choose content folder** or import a ZIP.
- **Remote assets:** `url` or `drive` sources download to app-private storage before playback/decoding. Successful copies are reused offline. The cache key includes pack ID, pack version, and URL; bump the pack version when changing remote bytes at the same URL.
- **Drive:** direct downloadable links only. Links requiring sign-in, confirmation, OAuth, or browser cookies need manual download followed by ZIP/folder import.
- **Reload / rescan:** rereads the active manifest without rebuilding the APK.
- **Clear library:** double confirmation deletes app-owned imports and downloaded assets. Original picked ZIPs and external folders are retained.

HTTPS downloads have timeouts, bounded redirects, atomic temporary files, download-size checks, free-space checks, and HTML/sign-in-page rejection. ZIPs reject absolute/escaping paths and canonical duplicates; maximum 20,000 entries, 2 GiB per file, and 8 GiB extracted total. Downloads do not implement authenticated OAuth or automatic partial-download resumption. Retry by selecting the operation again.

## Manifest and tests

See [manifest documentation](docs/manifest.md), [implementation status and QA](docs/validation.md), and the neutral [sample manifest](sample-pack/manifest.json).

```sh
tools/check-core.sh
python3 tools/make_demo_pack.py
python3 tools/validate_pack.py demo-pack.zip
```

The first command uses Java's compiler module directly and runs the production clock/ZIP code. The Python commands regenerate original demo audio and validate pack references/WAV durations. Manifest-parser JUnit tests require the Android Gradle toolchain.

No account, API key, advertising, or telemetry is required. App backup is disabled so persisted file permissions and private download links are not included in Android cloud backup. Content remains external to the generic codebase. Official Android Auto browsing integration is outside this MVP, as specified; this app targets phones/tablets and Android head units running apps directly.
