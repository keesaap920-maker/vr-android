# Engineering architecture

The app is an ordinary, unprivileged Android application, not an Android firmware replacement.

- `VisionActivity`: lifecycle, SAF, installed-app launches, permissions, camera activation, file Uri delivery.
- `HeadTracker`: Android game-rotation-vector sensor, orientation relative to calibration frame, 3DoF only.
- `MediaPipeHandTracker`: Camera2 YUV frame input, optional official MediaPipe Hand Landmarker inference, normalized fingertip position and pinch gate. Hand gestures only when the built-in model exists and device geometry permits tracking.
- `Workspace`: UI domain layer, per-panel position and dimensions, state of app pages, gesture/gaze selection, settings persistence and action dispatch through host interface.
- `StereoRenderer`: two different view matrices at ±IPD/2, per-eye framebuffer, 3D floor grid and textured rectangular interfaces; lens warp during final compositing. It is not a full VR runtime with asynchronous reprojection or position tracking.
- `PanelPainter`: renders Android Canvas interfaces onto GL textures; this is the native app's own UI, not Android's unrestricted external window compositor.
- `BrowserBridge`: offscreen WebView snapshot into a spatial panel and mapped tap/scroll commands. Device-specific WebView drawing behavior is **experimental**.
- `SpatialVideoPlayer`: Android MediaPlayer output to `SurfaceTexture` and external GLES texture on a 3D plane.
- `AppCatalog`: `ACTION_MAIN + CATEGORY_LAUNCHER` discovery without broad QUERY_ALL_PACKAGES permission. Uses `getLaunchIntentForPackage` on selection. Launch-only outside the spatial compositor.

## Security boundary

The project does not use accessibility privileges, root, input injection into third-party apps, screen capture without permission, MediaProjection, custom ROMs, or OEM privileged task management.

## Next technical milestones

1. Complete official Android SDK build and fix compiler errors, if any.
2. Device camera-field tests for Z Flip5 viewer configurations and actual hand detection accuracy.
3. Correct optical center distortion profiles per individual headset and measured device geometry.
4. Native spatial UI rendering of web content using verified SurfaceTexture or texture-layer transport instead of brittle software screenshot capture.
5. True persistence of window coordinates, context-aware virtual keyboard, scrollable list widgets, thermal-adaptive quality, frame latency instrumentation.
6. Formal QA on a Z Flip5 and repeat on multiple supported Android versions.
