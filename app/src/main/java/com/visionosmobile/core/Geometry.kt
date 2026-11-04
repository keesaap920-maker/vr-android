package com.visionosmobile.core

import kotlin.math.*

data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(b: Vec3) = Vec3(x + b.x, y + b.y, z + b.z)
    operator fun minus(b: Vec3) = Vec3(x - b.x, y - b.y, z - b.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    fun dot(b: Vec3) = x * b.x + y * b.y + z * b.z
    fun length() = sqrt(dot(this))
    fun normalized(): Vec3 = if (length() < 1e-7f) Vec3(0f, 0f, -1f) else this * (1f / length())
}

data class SpatialRectangle(
    val id: String, val center: Vec3, val width: Float, val height: Float
)

data class RayHit(val panelId: String, val u: Float, val v: Float, val distance: Float)

/** Flat wall-facing 3D surfaces. The renderer uses exactly the same panel dimensions. */
object Raycaster {
    fun hit(origin: Vec3, direction: Vec3, rectangles: Iterable<SpatialRectangle>): RayHit? {
        if (direction.z >= -1e-5f) return null
        var closest: RayHit? = null
        for (panel in rectangles) {
            val distance = (panel.center.z - origin.z) / direction.z
            if (distance <= 0.05f || (closest != null && closest.distance <= distance)) continue
            val impact = origin + direction * distance
            val u = (impact.x - panel.center.x) / panel.width + 0.5f
            val v = 0.5f - (impact.y - panel.center.y) / panel.height
            if (u in 0f..1f && v in 0f..1f) closest = RayHit(panel.id, u, v, distance)
        }
        return closest
    }
}

object GazeMath {
    /** Yaw/pitch are relative to the recentered device orientation, in radians. */
    fun forward(yaw: Float, pitch: Float): Vec3 = Vec3(
        sin(yaw) * cos(pitch),
        sin(pitch),
        -cos(yaw) * cos(pitch)
    ).normalized()

    fun pointerRay(yaw: Float, pitch: Float, x: Float, y: Float): Vec3 {
        val forward = forward(yaw, pitch)
        val right = Vec3(cos(yaw), 0f, sin(yaw))
        val up = Vec3(-sin(yaw) * sin(pitch), cos(pitch), cos(yaw) * sin(pitch))
        return (forward + right * ((x - 0.5f) * 1.05f) + up * ((0.5f - y) * 0.75f)).normalized()
    }
}

/** Pinch detection uses normalized image positions with hysteresis to avoid repeat clicks. */
class PinchGate(private val enter: Float = 0.055f, private val leave: Float = 0.085f) {
    var pressed = false
        private set
    fun update(distance: Float): Boolean {
        pressed = if (pressed) distance < leave else distance < enter
        return pressed
    }
    fun reset() { pressed = false }
}

object PanelLayout {
    /** 0 = header, 1 = first grid tile, 2 = second tile, etc; -1 = blank. */
    fun tileAt(u: Float, v: Float, columns: Int, rows: Int): Int {
        if (v < 0.18f) return 0
        if (v > 0.92f || u < 0.03f || u > 0.97f) return -1
        val col = ((u - 0.03f) / 0.94f * columns).toInt().coerceIn(0, columns - 1)
        val row = ((v - 0.18f) / 0.74f * rows).toInt().coerceIn(0, rows - 1)
        return 1 + row * columns + col
    }
}
