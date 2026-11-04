# VisionOS Mobile — experimental Android VR workspace

**Status: source-code engineering prototype, not a verified APK.**

A native Android smartphone VR environment targeted at the **unfolded Samsung Galaxy Z Flip5**. It draws genuinely different left-eye/right-eye views in OpenGL ES 2.0. It uses an Android game rotation vector for 3DoF headtracking and gaze dwell/volume button selection. It includes a spatial desktop, spatial windows, Android app launcher, Camera2 + MediaPipe handtracking integration, experimental browser projection, Android SAF file picker, 3D virtual keyboard, image gallery, and 3D video surface.

It is **not** a replacement Android operating system. It cannot generally capture or embed arbitrary third-party Android app windows. The project has **not** been compiled with the Android SDK and has **not** been tested on a physical Z Flip5; behavior and performance remain to be verified.

## Build on Windows 10/11

1. Download and unzip this project to a folder such as `C:\Projects\VisionOSMobile`.
2. Install [Android Studio](https://developer.android.com/studio). In **Tools > SDK Manager**, install Android SDK Platform 35, Android SDK Build Tools 35.0.0, and SDK Platform-Tools. Use JDK 17 (Android Studio can manage an appropriate JDK).
3. Open `VisionOSMobile` as a project in Android Studio. Wait for Gradle project synchronization and download of the Gradle Android plugin, Kotlin plugin, and MediaPipe Tasks Vision dependency.
4. For **handtracking**, open PowerShell in the project folder and run:

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\scripts\get-hand-model.ps1
   ```

   The script downloads the official hand landmark model into `app\src\main\assets\hand_landmarker.task`. It can also be imported manually from the official URL listed in the script. **The application can be built and used with gaze controls without this optional model.**
5. Build via **Android Studio > Build > Build APK(s)** or run:

   ```powershell
   .\gradlew.bat :app:assembleDebug
   ```

   On first use, `gradlew.bat` fetches the official Gradle 8.9 wrapper JAR and verifies its SHA-256 checksum. Internet access is required for this first build and for downloading project dependencies.
6. If compilation succeeds, the APK will be created at:

   `app\build\outputs\apk\debug\app-debug.apk`

   An APK **has not been created or tested in this environment**. Resolve real Gradle errors before installing. See `docs/TEST_REPORT.md`.

### USB installation to Galaxy Z Flip5

Enable **Developer options** and **USB debugging** on the phone. Connect it to your Windows computer and approve the USB debugging prompt. From the Android SDK Platform-Tools directory, run:

```powershell
adb install -r "C:\Projects\VisionOSMobile\app\build\outputs\apk\debug\app-debug.apk"
```

Alternatively, copy the APK to the phone and allow APK installation from the relevant source when Android asks. Installation from unknown sources carries security risks; build from code you trust.

### Headset use and calibration

* The **Z Flip5 must remain unfolded**. Verify that your particular headset physically accommodates an unfolded device approx. **165.1 × 71.9 × 6.9 mm**. Do not force it into the holder or apply pressure near the folding screen.
* Check that lenses line up with the center of each half of the full 2640×1080 landscape display. Lens center, viewer field of view, and distortion vary by headset; the supplied calibration is a basic starting point, not a certified optical profile.
* Launch VisionOS Mobile, acknowledge the safety onboarding, insert the phone securely and then put on the headset while seated.
* **Volume+** selects the focused VR item; **Volume-** recenters. Holding gaze on a button for approx. **1.7 seconds** selects it. A touchscreen tap also selects the current focus while the phone is outside the viewer.
* The Settings window changes IPD (52–75 mm), distortion, render resolution scale and environment. The home screen lets you open apps, browser, media, files, keyboard and recent apps.
* To enable handtracking, bundle the model **before building**, grant camera permission, open Hand input in the home panel, and ensure the phone's rear camera can actually see your hands. Pinching triggers selection when MediaPipe produces hand landmarks; gaze controls remain available if tracking fails.
* Stop using the viewer immediately if you develop eye strain, dizziness, visual discomfort or nausea. Do not walk around while using it. Monocular camera passthrough is **not enabled**.

### Implemented source-level modules

| Module | Source | Notes |
|---|---|---|
| Dual-eye stereo rendering, adjustable lens distortion | `vr/StereoRenderer.kt` | Real independent eye matrices and eye framebuffers; not Z Flip5 validated |
| 3DoF headtracking | `tracking/HeadTracker.kt` | Uses Android rotation vector, recenters |
| Gaze/dwell/volume selection | `ui/Workspace.kt`, `core/Geometry.kt` | 1.7-second dwell |
| Hand detection/gesture input | `tracking/MediaPipeHandTracker.kt` | Camera2 & MediaPipe; optional local model; untested on real device |
| Spatial windows | `ui/Workspace.kt`, `ui/PanelPainter.kt` | Open, move, scale, close; no cross-app embedding |
| Installed apps | `app/AppCatalog.kt` | Launcher intents, icon display, pagination |
| Browser | `browser/BrowserBridge.kt` | Android WebView offscreen rendering is experimental |
| VR virtual keyboard | `ui/Workspace.kt` | Gaze and pinch text selection; limited WebView text injection |
| Video cinema | `media/SpatialVideoPlayer.kt` | Android MediaPlayer decoded to OES texture, 3D plane |
| Local files/gallery | `VisionActivity.kt` | SAF picker; image decode; PDFs delegate to other apps |
| Environments/settings | `vr/StereoRenderer.kt` | Three basic scene color schemes with 3D grid |

## Validation and limitations

Source-level numeric raycast and pinch tests have been executed successfully using the local Kotlin compiler, and the Android manifest has been parsed successfully. Android-specific compilation, runtime tests and actual headset ergonomics have **not** been verified. Details: `docs/TEST_REPORT.md`.

The Windows build **may require bug fixes** because Android-specific source cannot be typechecked without SDK dependencies in the current execution environment.

No user environment video recording, network camera upload or root access is implemented. The camera is read locally for MediaPipe inference, and the embedded browser accesses the web only when used. App launching uses documented Android intents, not privileged APIs.

## Known constraints

* Ordinary WhatsApp, YouTube, Chrome and other apps can be **launched from VR** but cannot generally be displayed inside a floating VR window in a standard Android app. They switch to the normal Android UI.
* A smartphone-only camera is not equivalent to Vision Pro hand tracking or binocular color passthrough. If the rear camera is blocked or hands are outside its field of view, use gaze/volume controls.
* Handtracking requires the actual MediaPipe model file bundled in the APK. The model is not included in this ZIP to keep distribution explicit.
* Headtracking is 3DoF, **not positional 6DoF**. Lens profile, comfort and camera orientation must be calibrated against an actual headset.
* Browser offscreen capture, texture-driven streaming video, thermal behavior, Android 15+ permissions, and varied installed-app launch workflows may require device-specific fixes.

## Open-source code

The original project code in this package is provided under the MIT license (see `LICENSE`). Third-party dependencies retain their own licenses.


GitHub Actions: build the prototype using `.github/workflows/android.yml` (Android APK workflow).
