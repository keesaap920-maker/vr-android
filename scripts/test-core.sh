#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/core-tests
kotlinc app/src/main/java/com/visionosmobile/core/Geometry.kt -d build/core-tests/geometry.jar
cat > build/core-tests/Runner.kt <<'KOTLIN'
import com.visionosmobile.core.*
import kotlin.math.abs
fun check(name:String, ok:Boolean) { require(ok) { "FAIL $name" }; println("PASS $name") }
fun main() {
    val panels=listOf(SpatialRectangle("far",Vec3(0f,0f,-4f),2f,2f), SpatialRectangle("near",Vec3(0f,0f,-2f),1f,1f))
    val hit=Raycaster.hit(Vec3(0f,0f,0f),Vec3(0f,0f,-1f),panels)
    check("nearest spatial surface",hit?.panelId=="near")
    check("correct UV mapping", abs((hit?.u ?: 0f)-0.5f)<1e-5f && abs((hit?.v ?: 0f)-0.5f)<1e-5f)
    check("miss for backwards ray",Raycaster.hit(Vec3(0f,0f,0f),Vec3(0f,0f,1f),panels)==null)
    check("forward orientation",GazeMath.forward(0f,0f).z == -1f)
    check("orientation normalized",abs(GazeMath.forward(0.4f,0.3f).length()-1f)<1e-5f)
    val gate=PinchGate()
    check("pinch onset",!gate.update(0.1f) && gate.update(0.03f))
    check("pinch hysteresis",gate.update(0.07f) && !gate.update(0.12f))
    check("tiles",PanelLayout.tileAt(0.1f,0.2f,3,2)==1 && PanelLayout.tileAt(0.9f,0.2f,3,2)==3)
    check("keyboard tile reject",PanelLayout.tileAt(0.99f,0.5f,3,2)==-1)
}
KOTLIN
kotlinc -classpath build/core-tests/geometry.jar build/core-tests/Runner.kt -include-runtime -d build/core-tests/runner.jar
java -cp build/core-tests/runner.jar:build/core-tests/geometry.jar RunnerKt
