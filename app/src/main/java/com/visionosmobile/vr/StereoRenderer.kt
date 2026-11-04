package com.visionosmobile.vr

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20 as G
import android.opengl.GLUtils
import android.opengl.Matrix
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.util.Log
import com.visionosmobile.browser.BrowserBridge
import com.visionosmobile.media.SpatialVideoPlayer
import android.net.Uri
import com.visionosmobile.core.*
import com.visionosmobile.tracking.HeadTracker
import com.visionosmobile.tracking.MediaPipeHandTracker
import com.visionosmobile.ui.PanelPainter
import com.visionosmobile.ui.Workspace
import com.visionosmobile.ui.WindowPanel
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

/** Real dual-view GLES2 geometry + separate stereo offscreen eye textures and lens correction. */
class StereoRenderer(
    private val context:Context,
    private val workspace:Workspace,
    private val head:HeadTracker,
    private val hand:MediaPipeHandTracker,
    private val browser:BrowserBridge
):GLSurfaceView.Renderer {
    private var width=1;private var height=1
    private var quadProgram=0;private var distortionProgram=0;private var lineProgram=0
    private val quad=buffer(floatArrayOf(
        -0.5f,-0.5f,0f,1f, 0.5f,-0.5f,1f,1f,
        -0.5f,0.5f,0f,0f, 0.5f,0.5f,1f,0f
    ))
    private val identity=FloatArray(16).also { Matrix.setIdentityM(it,0) }
    private val textures=mutableMapOf<String,Int>()
    private val textureVersions=mutableMapOf<String,Int>()
    private val eyeBuffers=arrayOf(Eye(),Eye())
    private val grid=makeGrid()
    private val video=SpatialVideoPlayer(context)
    private var oldPinch=false
    private var oldScene=-1
    private var oldBrowserRevision=-1
    private var cachedWeb:Bitmap?=null
    @Volatile private var gallery:Bitmap?=null
    private var lastRender=0L
    private var frameCount=0
    private var lastFpsMark=SystemClock.uptimeMillis()
    @Volatile private var surfaceReady=false

    data class Eye(var fbo:Int=0,var texture:Int=0,var depth:Int=0,var w:Int=0,var h:Int=0)

    override fun onSurfaceCreated(gl:GL10?,config:EGLConfig?) {
        G.glDisable(G.GL_CULL_FACE)
        G.glDisable(G.GL_DEPTH_TEST)
        G.glEnable(G.GL_BLEND)
        G.glBlendFunc(G.GL_SRC_ALPHA,G.GL_ONE_MINUS_SRC_ALPHA)
        quadProgram=program(VERT,FRAG)
        distortionProgram=program(SCREEN_VERT,DISTORT_FRAG)
        lineProgram=program(LINE_VERT,LINE_FRAG)
        textures.clear();textureVersions.clear()
        video.initGL()
        surfaceReady=true
    }
    override fun onSurfaceChanged(gl:GL10?,w:Int,h:Int) {
        width=w;height=h
        recreateEyes()
    }
    override fun onDrawFrame(gl:GL10?) {
        if(!surfaceReady)return
        val start=SystemClock.uptimeMillis()
        val panelList=workspace.snapshot()
        val handSignal=hand.signal
        val useHand=workspace.handEnabled && handSignal.visible && start-handSignal.timestampMs<500
        val cursorX=if(useHand) 1f-handSignal.x else 0.5f
        val cursorY=if(useHand) handSignal.y else 0.5f
        val direction=GazeMath.pointerRay(head.yaw,head.pitch,cursorX,cursorY)
        val hit=Raycaster.hit(Vec3(0f,0f,0f),direction,panelList.map{it.rectangle()})
        workspace.updatePointer(hit,start,!useHand)
        if(useHand && handSignal.pinch && !oldPinch) workspace.click()
        if(workspace.isMoving()) workspace.movePointer(direction)
        if(!handSignal.pinch && oldPinch) workspace.endMove()
        oldPinch=useHand && handSignal.pinch
        val focusKey=workspace.focusKey()
        if(browser.revision!=oldBrowserRevision) {
            val (rev,bitmap)=browser.snapshot()
            cachedWeb?.recycle();cachedWeb=bitmap
            oldBrowserRevision=rev
            textureVersions.remove("browser")
        }
        val existing=panelList.map{it.id}.toSet()
        for(id in textures.keys.toList())if(id !in existing) {
            G.glDeleteTextures(1,intArrayOf(textures.remove(id)!!),0)
            textureVersions.remove(id)
        }
        for(panel in panelList) {
            val selected=if(focusKey.startsWith("${panel.id}:")) focusKey else ""
            val version=panel.revision*10000 + selected.hashCode() +
                (if(panel.id=="browser") oldBrowserRevision*29 else 0)
            if(textures[panel.id]==null || textureVersions[panel.id]!=version){
                val bmp=PanelPainter.render(panel,selected,if(panel.id=="browser") cachedWeb else null,
                    if(panel.id=="gallery")gallery else null)
                val texture=textures.getOrPut(panel.id){newTexture()}
                G.glBindTexture(G.GL_TEXTURE_2D,texture)
                GLUtils.texImage2D(G.GL_TEXTURE_2D,0,bmp,0)
                bmp.recycle()
                textureVersions[panel.id]=version
            }
        }
        val scale=workspace.renderScale
        val expectedW=maxOf(256,(width/2*scale).toInt())
        val expectedH=maxOf(256,(height*scale).toInt())
        if(eyeBuffers[0].w!=expectedW || eyeBuffers[0].h!=expectedH) recreateEyes()
        for(eye in 0..1) {
            val e=eyeBuffers[eye]
            G.glBindFramebuffer(G.GL_FRAMEBUFFER,e.fbo)
            G.glViewport(0,0,e.w,e.h)
            val mode=workspace.sceneMode
            when(mode){
                0->G.glClearColor(0.020f,0.036f,0.085f,1f)
                1->G.glClearColor(0.40f,0.53f,0.68f,1f)
                else->G.glClearColor(0.14f,0.24f,0.32f,1f)
            }
            G.glClear(G.GL_COLOR_BUFFER_BIT or G.GL_DEPTH_BUFFER_BIT)
            val projection=FloatArray(16)
            Matrix.perspectiveM(projection,0,82f,(width/2f)/height,0.04f,70f)
            val view=FloatArray(16)
            val forward=GazeMath.forward(head.yaw,head.pitch)
            val eyeX=(if(eye==0) -1f else 1f)*workspace.ipdMeters*0.5f
            Matrix.setLookAtM(view,0,eyeX,0f,0f,
                eyeX+forward.x,forward.y,forward.z,0f,1f,0f)
            if(head.roll!=0f) Matrix.rotateM(view,0,-head.roll*180f/PI.toFloat(),0f,0f,1f)
            drawGrid(projection,view,mode)
            if(panelList.any{it.id=="media"})video.draw(projection,view)
            for(panel in panelList) {
                val tex=textures[panel.id] ?: continue
                val model=FloatArray(16);Matrix.setIdentityM(model,0)
                Matrix.translateM(model,0,panel.x,panel.y,panel.z)
                Matrix.scaleM(model,0,panel.width,panel.height,1f)
                val pv=FloatArray(16);val mvp=FloatArray(16)
                Matrix.multiplyMM(pv,0,projection,0,view,0)
                Matrix.multiplyMM(mvp,0,pv,0,model,0)
                drawQuad(tex,mvp)
            }
            G.glBindFramebuffer(G.GL_FRAMEBUFFER,0)
        }
        G.glViewport(0,0,width,height)
        G.glClearColor(0f,0f,0f,1f);G.glClear(G.GL_COLOR_BUFFER_BIT)
        for(eye in 0..1){
            G.glViewport(eye*(width/2),0,width/2,height)
            drawDistortedEye(eyeBuffers[eye].texture,workspace.distortion)
            drawReticle(cursorX,cursorY,useHand,if(hit!=null) true else false)
        }
        frameCount++
        if(start-lastFpsMark>1200){
            workspace.latestFps=frameCount*1000f/(start-lastFpsMark).toFloat()
            frameCount=0;lastFpsMark=start
        }
        lastRender=start
    }
    private fun drawQuad(tex:Int,mvp:FloatArray){
        G.glUseProgram(quadProgram)
        bindQuad(quadProgram)
        val u=G.glGetUniformLocation(quadProgram,"uMvp")
        G.glUniformMatrix4fv(u,1,false,mvp,0)
        G.glActiveTexture(G.GL_TEXTURE0);G.glBindTexture(G.GL_TEXTURE_2D,tex)
        G.glUniform1i(G.glGetUniformLocation(quadProgram,"uTex"),0)
        G.glDrawArrays(G.GL_TRIANGLE_STRIP,0,4)
    }
    private fun drawDistortedEye(tex:Int,k:Float){
        G.glUseProgram(distortionProgram)
        bindQuad(distortionProgram)
        G.glUniform1f(G.glGetUniformLocation(distortionProgram,"uK"),k)
        G.glActiveTexture(G.GL_TEXTURE0);G.glBindTexture(G.GL_TEXTURE_2D,tex)
        G.glUniform1i(G.glGetUniformLocation(distortionProgram,"uTex"),0)
        G.glDrawArrays(G.GL_TRIANGLE_STRIP,0,4)
    }
    private fun bindQuad(program:Int){
        val pos=G.glGetAttribLocation(program,"aPos")
        val uv=G.glGetAttribLocation(program,"aUv")
        quad.position(0);G.glVertexAttribPointer(pos,2,G.GL_FLOAT,false,16,quad)
        G.glEnableVertexAttribArray(pos)
        quad.position(2);G.glVertexAttribPointer(uv,2,G.GL_FLOAT,false,16,quad)
        G.glEnableVertexAttribArray(uv)
    }
    private fun drawGrid(proj:FloatArray,view:FloatArray,mode:Int){
        val pv=FloatArray(16);Matrix.multiplyMM(pv,0,proj,0,view,0)
        G.glUseProgram(lineProgram)
        G.glUniformMatrix4fv(G.glGetUniformLocation(lineProgram,"uMvp"),1,false,pv,0)
        val color=when(mode){
            0->floatArrayOf(0.11f,0.27f,0.38f,0.42f)
            1->floatArrayOf(0.90f,0.94f,0.98f,0.35f)
            else->floatArrayOf(0.30f,0.55f,0.58f,0.27f)
        }
        G.glUniform4fv(G.glGetUniformLocation(lineProgram,"uColor"),1,color,0)
        val a=G.glGetAttribLocation(lineProgram,"aPos")
        grid.position(0);G.glVertexAttribPointer(a,3,G.GL_FLOAT,false,12,grid)
        G.glEnableVertexAttribArray(a)
        G.glLineWidth(1f);G.glDrawArrays(G.GL_LINES,0,grid.capacity()/3)
    }
    private fun drawReticle(x:Float,y:Float,hand:Boolean,hit:Boolean) {
        val posX=(x-.5f)*1.85f
        val posY=(.5f-y)*1.85f
        val r=if(hand)0.026f else 0.018f
        val v=buffer(floatArrayOf(
            posX-r,posY,0f,posX+r,posY,0f,
            posX,posY-r,0f,posX,posY+r,0f
        ))
        G.glUseProgram(lineProgram)
        G.glUniformMatrix4fv(G.glGetUniformLocation(lineProgram,"uMvp"),1,false,identity,0)
        G.glUniform4fv(G.glGetUniformLocation(lineProgram,"uColor"),1,
            if(hit)floatArrayOf(0.35f,0.87f,1f,0.95f) else floatArrayOf(1f,1f,1f,0.8f),0)
        val a=G.glGetAttribLocation(lineProgram,"aPos")
        v.position(0);G.glVertexAttribPointer(a,3,G.GL_FLOAT,false,12,v)
        G.glEnableVertexAttribArray(a)
        G.glLineWidth(3f);G.glDrawArrays(G.GL_LINES,0,4)
    }
    private fun recreateEyes(){
        for(e in eyeBuffers) {
            if(e.fbo!=0) G.glDeleteFramebuffers(1,intArrayOf(e.fbo),0)
            if(e.texture!=0) G.glDeleteTextures(1,intArrayOf(e.texture),0)
            if(e.depth!=0) G.glDeleteRenderbuffers(1,intArrayOf(e.depth),0)
            e.w=maxOf(256,(width/2*workspace.renderScale).toInt())
            e.h=maxOf(256,(height*workspace.renderScale).toInt())
            val tex=IntArray(1);G.glGenTextures(1,tex,0);e.texture=tex[0]
            G.glBindTexture(G.GL_TEXTURE_2D,e.texture)
            G.glTexParameteri(G.GL_TEXTURE_2D,G.GL_TEXTURE_MIN_FILTER,G.GL_LINEAR)
            G.glTexParameteri(G.GL_TEXTURE_2D,G.GL_TEXTURE_MAG_FILTER,G.GL_LINEAR)
            G.glTexParameteri(G.GL_TEXTURE_2D,G.GL_TEXTURE_WRAP_S,G.GL_CLAMP_TO_EDGE)
            G.glTexParameteri(G.GL_TEXTURE_2D,G.GL_TEXTURE_WRAP_T,G.GL_CLAMP_TO_EDGE)
            G.glTexImage2D(G.GL_TEXTURE_2D,0,G.GL_RGBA,e.w,e.h,0,G.GL_RGBA,G.GL_UNSIGNED_BYTE,null)
            val depth=IntArray(1);G.glGenRenderbuffers(1,depth,0);e.depth=depth[0]
            G.glBindRenderbuffer(G.GL_RENDERBUFFER,e.depth)
            G.glRenderbufferStorage(G.GL_RENDERBUFFER,G.GL_DEPTH_COMPONENT16,e.w,e.h)
            val fbo=IntArray(1);G.glGenFramebuffers(1,fbo,0);e.fbo=fbo[0]
            G.glBindFramebuffer(G.GL_FRAMEBUFFER,e.fbo)
            G.glFramebufferTexture2D(G.GL_FRAMEBUFFER,G.GL_COLOR_ATTACHMENT0,G.GL_TEXTURE_2D,e.texture,0)
            G.glFramebufferRenderbuffer(G.GL_FRAMEBUFFER,G.GL_DEPTH_ATTACHMENT,G.GL_RENDERBUFFER,e.depth)
            val status=G.glCheckFramebufferStatus(G.GL_FRAMEBUFFER)
            if(status!=G.GL_FRAMEBUFFER_COMPLETE)Log.e("VisionRenderer","Framebuffer incomplete: $status")
        }
        G.glBindFramebuffer(G.GL_FRAMEBUFFER,0)
    }
    fun setGallery(bmp:Bitmap?) { gallery=bmp;textureVersions.remove("gallery") }
    fun openVideo(uri:Uri) {video.open(uri)}
    fun videoCommand(command:String) { when(command){"play"->video.play();"pause"->video.pause();"stop"->video.stop()} }
    fun manualClick() { workspace.click() }
    private fun newTexture():Int {
        val a=IntArray(1);G.glGenTextures(1,a,0)
        G.glBindTexture(G.GL_TEXTURE_2D,a[0])
        G.glTexParameteri(G.GL_TEXTURE_2D,G.GL_TEXTURE_MIN_FILTER,G.GL_LINEAR)
        G.glTexParameteri(G.GL_TEXTURE_2D,G.GL_TEXTURE_MAG_FILTER,G.GL_LINEAR)
        G.glTexParameteri(G.GL_TEXTURE_2D,G.GL_TEXTURE_WRAP_S,G.GL_CLAMP_TO_EDGE)
        G.glTexParameteri(G.GL_TEXTURE_2D,G.GL_TEXTURE_WRAP_T,G.GL_CLAMP_TO_EDGE)
        return a[0]
    }
    private fun program(v:String,f:String):Int {
        val vp=shader(G.GL_VERTEX_SHADER,v)
        val fp=shader(G.GL_FRAGMENT_SHADER,f)
        val p=G.glCreateProgram();G.glAttachShader(p,vp);G.glAttachShader(p,fp);G.glLinkProgram(p)
        val result=IntArray(1);G.glGetProgramiv(p,G.GL_LINK_STATUS,result,0)
        if(result[0]==0) Log.e("VisionRenderer","Program link error: ${G.glGetProgramInfoLog(p)}")
        G.glDeleteShader(vp);G.glDeleteShader(fp)
        return p
    }
    private fun shader(type:Int,src:String):Int {
        val id=G.glCreateShader(type);G.glShaderSource(id,src);G.glCompileShader(id)
        val result=IntArray(1);G.glGetShaderiv(id,G.GL_COMPILE_STATUS,result,0)
        if(result[0]==0)Log.e("VisionRenderer","Shader failed: ${G.glGetShaderInfoLog(id)}")
        return id
    }
    private fun makeGrid():FloatBuffer {
        val a=mutableListOf<Float>()
        fun line(x:Float,y:Float,z:Float,x2:Float,y2:Float,z2:Float){a.addAll(listOf(x,y,z,x2,y2,z2))}
        for(i in -18..18) line(i*0.5f,-1.42f,-1f,i*0.5f,-1.42f,-25f)
        for(i in 2..50) line(-9f,-1.42f,-i*0.5f,9f,-1.42f,-i*0.5f)
        return buffer(a.toFloatArray())
    }
    companion object {
        private fun buffer(data:FloatArray):FloatBuffer = ByteBuffer.allocateDirect(data.size*4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(data);position(0) }
        private const val VERT="""attribute vec2 aPos;attribute vec2 aUv;uniform mat4 uMvp;varying vec2 vUv;void main(){vUv=aUv;gl_Position=uMvp*vec4(aPos,0.0,1.0);}"""
        private const val FRAG="""precision mediump float;uniform sampler2D uTex;varying vec2 vUv;void main(){gl_FragColor=texture2D(uTex,vUv);}"""
        private const val SCREEN_VERT="""attribute vec2 aPos;attribute vec2 aUv;varying vec2 vUv;void main(){vUv=aUv;gl_Position=vec4(aPos*2.0,0.0,1.0);}"""
        private const val DISTORT_FRAG="""precision mediump float;varying vec2 vUv;uniform sampler2D uTex;uniform float uK;void main(){vec2 d=vUv-vec2(0.5);float r=dot(d,d);vec2 uv=vec2(0.5)+d*(1.0+uK*r+0.15*uK*r*r);if(uv.x<0.0||uv.x>1.0||uv.y<0.0||uv.y>1.0)gl_FragColor=vec4(0.0,0.0,0.0,1.0);else gl_FragColor=texture2D(uTex,uv);}"""
        private const val LINE_VERT="""attribute vec3 aPos;uniform mat4 uMvp;void main(){gl_Position=uMvp*vec4(aPos,1.0);}"""
        private const val LINE_FRAG="""precision mediump float;uniform vec4 uColor;void main(){gl_FragColor=uColor;}"""
    }
}
