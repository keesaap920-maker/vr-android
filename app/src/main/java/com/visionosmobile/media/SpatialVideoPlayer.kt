package com.visionosmobile.media

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.opengl.GLES11Ext
import android.opengl.GLES20 as G
import android.opengl.Matrix
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean

/** MediaPlayer video decoded into a real external GL texture inside a stereo 3D plane. */
class SpatialVideoPlayer(private val context:Context) {
    @Volatile var hasVideo=false
        private set
    private var texture=0
    private var surfaceTexture:SurfaceTexture?=null
    private var mediaPlayer:MediaPlayer?=null
    private var program=0
    private val dirty=AtomicBoolean(false)
    private val vertex=ByteBuffer.allocateDirect(16*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply{
        put(floatArrayOf(-0.5f,-0.5f,0f,1f, 0.5f,-0.5f,1f,1f,
            -0.5f,0.5f,0f,0f, 0.5f,0.5f,1f,0f));position(0)
    }
    fun initGL() {
        val a=IntArray(1);G.glGenTextures(1,a,0);texture=a[0]
        G.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture)
        G.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,G.GL_TEXTURE_MIN_FILTER,G.GL_LINEAR)
        G.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,G.GL_TEXTURE_MAG_FILTER,G.GL_LINEAR)
        G.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,G.GL_TEXTURE_WRAP_S,G.GL_CLAMP_TO_EDGE)
        G.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,G.GL_TEXTURE_WRAP_T,G.GL_CLAMP_TO_EDGE)
        surfaceTexture=SurfaceTexture(texture).also{it.setOnFrameAvailableListener{dirty.set(true)}}
        val v=shader(G.GL_VERTEX_SHADER,"""attribute vec2 aPos;attribute vec2 aUv;varying vec2 vUv;uniform mat4 uMvp;void main(){vUv=aUv;gl_Position=uMvp*vec4(aPos,0.0,1.0);}""")
        val f=shader(G.GL_FRAGMENT_SHADER,"""#extension GL_OES_EGL_image_external : require
precision mediump float;varying vec2 vUv;uniform samplerExternalOES uTex;void main(){gl_FragColor=texture2D(uTex,vUv);}""")
        program=G.glCreateProgram();G.glAttachShader(program,v);G.glAttachShader(program,f);G.glLinkProgram(program)
        G.glDeleteShader(v);G.glDeleteShader(f)
    }
    /** Called on the active GL thread after initGL. Media callbacks may arrive on a handler thread. */
    fun open(uri:Uri) {
        releasePlayer()
        try {
            val mp=MediaPlayer()
            mediaPlayer=mp
            mp.setDataSource(context,uri)
            val surface=Surface(surfaceTexture)
            mp.setSurface(surface)
            surface.release()
            mp.isLooping=false
            mp.setOnPreparedListener{player->hasVideo=true;player.start()}
            mp.setOnErrorListener{_,what,extra->Log.e("VisionVideo","MediaPlayer error $what / $extra");hasVideo=false;true}
            mp.prepareAsync()
        } catch(t:Throwable) {Log.e("VisionVideo","Cannot open",t);hasVideo=false}
    }
    fun play(){mediaPlayer?.start()}
    fun pause(){mediaPlayer?.let {if(it.isPlaying)it.pause()}}
    fun stop(){mediaPlayer?.let {runCatching{it.seekTo(0);it.pause()}}}
    fun draw(projection:FloatArray,view:FloatArray){
        if(!hasVideo)return
        if(dirty.getAndSet(false))runCatching{surfaceTexture?.updateTexImage()}
        val model=FloatArray(16);Matrix.setIdentityM(model,0)
        Matrix.translateM(model,0,0f,0.36f,-3.35f)
        Matrix.scaleM(model,0,4.2f,2.3f,1f)
        val pv=FloatArray(16);val mvp=FloatArray(16)
        Matrix.multiplyMM(pv,0,projection,0,view,0)
        Matrix.multiplyMM(mvp,0,pv,0,model,0)
        G.glUseProgram(program)
        G.glUniformMatrix4fv(G.glGetUniformLocation(program,"uMvp"),1,false,mvp,0)
        val pos=G.glGetAttribLocation(program,"aPos")
        val uv=G.glGetAttribLocation(program,"aUv")
        vertex.position(0);G.glVertexAttribPointer(pos,2,G.GL_FLOAT,false,16,vertex);G.glEnableVertexAttribArray(pos)
        vertex.position(2);G.glVertexAttribPointer(uv,2,G.GL_FLOAT,false,16,vertex);G.glEnableVertexAttribArray(uv)
        G.glActiveTexture(G.GL_TEXTURE0);G.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture)
        G.glUniform1i(G.glGetUniformLocation(program,"uTex"),0)
        G.glDrawArrays(G.GL_TRIANGLE_STRIP,0,4)
    }
    private fun shader(type:Int,src:String):Int=G.glCreateShader(type).also{
        G.glShaderSource(it,src);G.glCompileShader(it)
    }
    private fun releasePlayer(){
        hasVideo=false
        mediaPlayer?.let {runCatching{it.stop()};runCatching{it.release()}}
        mediaPlayer=null
    }
    fun release(){releasePlayer();surfaceTexture?.release();surfaceTexture=null}
}
