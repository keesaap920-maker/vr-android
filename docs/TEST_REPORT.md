# Verification report (2026-11-03)

## Execution environment

* OpenJDK 21 available; Kotlin compiler available.
* No Android SDK (`android.jar`), Build Tools (`aapt2`/`d8`) or Gradle installation available locally.
* External package hosting could not be resolved by the current execution environment (`dl.google.com` DNS failure).
* No Android hardware, emulator, camera or phone mounted.
* Therefore, **no Android APK was compiled or installed**.

## Tests actually performed

| Test | Status | Evidence |
|---|---|---|
| 3D ray intersection picks closest panel | PASS | `scripts/test-core.sh` |
| 3D ray UV correspondence | PASS | `scripts/test-core.sh` |
| Outward-pointing ray does not hit | PASS | `scripts/test-core.sh` |
| Head forward direction at zero angles | PASS | `scripts/test-core.sh` |
| Orientation vector normalization | PASS | `scripts/test-core.sh` |
| Pinch activation threshold | PASS | `scripts/test-core.sh` |
| Pinch hysteresis/debounce threshold | PASS | `scripts/test-core.sh` |
| Grid tile mapping | PASS | `scripts/test-core.sh` |
| Grid out-of-bounds rejection | PASS | `scripts/test-core.sh` |
| Kotlin workspace logic typecheck | PASS with stubs | Android platform objects replaced by temporary local stubs to check Kotlin syntax |
| Android manifest well-formedness | PASS | `scripts/verify-project.py` |
| Required renderer/tracker modules present | PASS (structural) | `scripts/verify-project.py` |
| Android Studio/Gradle APK compilation | NOT TESTED | Android SDK & build dependencies missing |
| Physical Galaxy Z Flip5 test | NOT TESTED | No hardware available |

The structural checks do not establish runtime correctness. The stub typecheck does not establish Android API compatibility.

## User-requested acceptance tests

| # | Requirement | Result |
|---:|---|---|
| 1 | APK installs and launches | Not tested |
| 2 | Distinct left/right stereoscopic perspective | Implemented in source; not runtime tested |
| 3 | Head rotation changes view smoothly | Implemented in source; not runtime tested |
| 4 | Recenter | Implemented in source; not runtime tested |
| 5 | Open floating window | Implemented in source; not runtime tested |
| 6 | Move, resize, close spatial window | Implemented in source; not runtime tested |
| 7 | Camera feeds a hand model | Implemented behind model installation, permissions, and camera configuration; not runtime tested |
| 8 | Pinch selects UI item | Implemented in source; not runtime tested |
| 9 | Gaze works without camera | Implemented in source; not runtime tested |
| 10 | Browser loads and interacts with websites | Experimental WebView code; not runtime tested |
| 11 | Spatial keyboard enters text | Implemented in source; not runtime tested |
| 12 | Library lists real installed apps | Implemented using PackageManager; not runtime tested |
| 13 | Selecting ordinary app launches it | Implemented using launch intent; not runtime tested |
| 14 | Video plays in spatial screen | MediaPlayer + SurfaceTexture code; not runtime tested |
| 15 | Lifecycle reliability | Pause/resume handling implemented; not runtime tested |
| 16 | Valid APK build | Not tested; APK not produced |

## Mandatory remaining verification

1. Resolve Gradle project sync and build errors in a real Android SDK environment.
2. Run `gradlew.bat :app:testDebugUnitTest`, then `gradlew.bat :app:assembleDebug`.
3. Install and launch on unfolded Z Flip5 and verify actual stereo orientation, gaze direction and IPD.
4. Validate Camera2 exposes an unobstructed useful rear wide-angle camera and correctly rotates frames to MediaPipe input.
5. Verify camera permission denial and fallback, multiple windows, 3D video playback and browser snapshots.
6. Measure frame time and thermal performance for at least 20 minutes to evaluate comfort and stability.
7. Test all headset lens parameters to avoid eyestrain or double vision.

**No above device checks are claimed to have been passed.**
