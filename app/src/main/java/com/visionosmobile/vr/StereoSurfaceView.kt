package com.visionosmobile.vr

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent

class StereoSurfaceView(context:Context, val vrRenderer:StereoRenderer):GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause=true
        setRenderer(vrRenderer)
        renderMode=RENDERMODE_CONTINUOUSLY
        setEGLConfigChooser(8,8,8,8,16,0)
    }
    override fun onTouchEvent(event:MotionEvent):Boolean {
        if(event.actionMasked==MotionEvent.ACTION_UP){vrRenderer.manualClick();return true}
        return true
    }
}
