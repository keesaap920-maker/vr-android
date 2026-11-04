package com.visionosmobile.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class CoreMathTest {
    @Test fun raycastFindsCenter() {
        val rect = SpatialRectangle("home", Vec3(0f,0f,-3f), 2.4f,1.6f)
        val hit = Raycaster.hit(Vec3(0f,0f,0f),Vec3(0f,0f,-1f),listOf(rect))!!
        assertEquals("home",hit.panelId)
        assertEquals(0.5f,hit.u,0.001f)
        assertEquals(0.5f,hit.v,0.001f)
    }
    @Test fun nearestWindowWins() {
        val rects=listOf(
            SpatialRectangle("back",Vec3(0f,0f,-4f),3f,3f),
            SpatialRectangle("front",Vec3(0f,0f,-2f),1f,1f)
        )
        assertEquals("front",Raycaster.hit(Vec3(0f,0f,0f),Vec3(0f,0f,-1f),rects)?.panelId)
    }
    @Test fun invalidRayDoesNotHit() {
        assertNull(Raycaster.hit(Vec3(0f,0f,0f),Vec3(0f,0f,1f),listOf(
            SpatialRectangle("p",Vec3(0f,0f,-3f),2f,2f)
        )))
    }
    @Test fun headDirectionIsNormalized() {
        val d=GazeMath.forward(0f,0f)
        assertEquals(-1f,d.z,0.0001f)
        assertEquals(1f,GazeMath.forward(0.5f,0.1f).length(),0.0001f)
    }
    @Test fun pinchHysteresisPreventsJitter() {
        val g=PinchGate()
        assertFalse(g.update(0.07f))
        assertTrue(g.update(0.04f))
        assertTrue(g.update(0.07f))
        assertFalse(g.update(0.11f))
    }
    @Test fun gridTapMapsCorrectly() {
        assertEquals(1,PanelLayout.tileAt(0.1f,0.2f,3,2))
        assertEquals(3,PanelLayout.tileAt(0.9f,0.2f,3,2))
        assertEquals(4,PanelLayout.tileAt(0.1f,0.72f,3,2))
        assertEquals(-1,PanelLayout.tileAt(0.99f,0.5f,3,2))
    }
}
