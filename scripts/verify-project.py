from pathlib import Path
from lxml import etree
p = Path(__file__).resolve().parent.parent
manifest = p / 'app/src/main/AndroidManifest.xml'
etree.parse(str(manifest))
assert (p / 'gradle/wrapper/gradle-wrapper.properties').exists()
assert 'com.google.mediapipe:tasks-vision' in (p / 'app/build.gradle.kts').read_text()
assert 'CameraManager' in (p / 'app/src/main/java/com/visionosmobile/tracking/MediaPipeHandTracker.kt').read_text()
assert 'glFramebufferTexture2D' in (p / 'app/src/main/java/com/visionosmobile/vr/StereoRenderer.kt').read_text()
files=list((p / 'app/src/main/java').rglob('*.kt'))
assert len(files)>=10
print('PASS Android manifest parses')
print('PASS Gradle config and hand inference dependency present')
print('PASS Camera2 pipeline and stereo FBO implementation present')
print(f'PASS {len(files)} Kotlin source files present')
print('NOTE: these are structural checks, not an Android compile or device test')
