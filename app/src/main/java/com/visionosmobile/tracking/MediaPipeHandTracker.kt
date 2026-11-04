package com.visionosmobile.tracking

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.visionosmobile.core.PinchGate
import java.io.File
import java.nio.MappedByteBuffer
import java.io.FileInputStream
import kotlin.math.*

data class HandSignal(val visible:Boolean, val x:Float, val y:Float, val pinch:Boolean,
                      val timestampMs:Long)

/** Runs hand landmark inference entirely on-device. Requires the official task model in assets. */
class MediaPipeHandTracker(
    private val context: Context,
    private val callback: (String) -> Unit
) {
    @Volatile var signal = HandSignal(false,0.5f,0.5f,false,0L)
        private set
    private var landmarker:HandLandmarker? = null
    private var worker:HandlerThread? = null
    private var handler:Handler? = null
    private var camera:CameraDevice? = null
    private var session:CameraCaptureSession? = null
    private var reader:ImageReader? = null
    private val pinch = PinchGate(0.060f,0.095f)
    private var lastFrame = 0L
    private var sensorOrientation = 90
    private var running = false

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        running = true
        try {
            if (!context.assets.list("").orEmpty().contains("hand_landmarker.task")) {
                callback("Model ontbreekt: voer scripts/get-hand-model.ps1 uit vóór build")
                running = false
                return
            }
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build())
                .setNumHands(2)
                .setMinHandDetectionConfidence(0.60f)
                .setMinHandPresenceConfidence(0.60f)
                .setMinTrackingConfidence(0.55f)
                .setRunningMode(RunningMode.IMAGE).build()
            landmarker = HandLandmarker.createFromOptions(context,options)
            val thread = HandlerThread("VisionHandTracking").apply { start() }
            worker = thread; handler=Handler(thread.looper)
            val service = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val choices = service.cameraIdList.filter {
                service.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
            if (choices.isEmpty()) { callback("Geen achtercamera gevonden");stop();return }
            val chosen = choices.minByOrNull { id ->
                val c = service.getCameraCharacteristics(id)
                val f = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.minOrNull() ?: 100f
                val physical = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                f/(physical?.width ?: 1f)
            } ?: choices.first()
            sensorOrientation = service.getCameraCharacteristics(chosen)
                .get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            reader = ImageReader.newInstance(320,240,ImageFormat.YUV_420_888,2)
            reader?.setOnImageAvailableListener({ r ->
                val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                try { process(image) } catch (t:Throwable) { callback("Handtracking: ${t.javaClass.simpleName}") }
                finally { image.close() }
            },handler)
            service.openCamera(chosen,object:CameraDevice.StateCallback(){
                override fun onOpened(device:CameraDevice) {
                    camera=device
                    val output=reader?.surface ?: return
                    @Suppress("DEPRECATION")
                    device.createCaptureSession(listOf(output),object:CameraCaptureSession.StateCallback(){
                        override fun onConfigured(s:CameraCaptureSession){
                            if (!running) return
                            session=s
                            val req=device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                addTarget(output)
                                set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                            }
                            s.setRepeatingRequest(req.build(),null,handler)
                            callback("Camera actief • handtracking")
                        }
                        override fun onConfigureFailed(s:CameraCaptureSession){ callback("Camerasessie mislukt") }
                    },handler)
                }
                override fun onDisconnected(device:CameraDevice) { device.close();camera=null;callback("Camera losgekoppeld") }
                override fun onError(device:CameraDevice,error:Int) { device.close();camera=null;callback("Camera fout $error") }
            },handler)
        } catch(t:Throwable) {
            callback("Handtracking niet beschikbaar: ${t.javaClass.simpleName}: ${t.message.orEmpty().take(55)}")
            stop()
        }
    }

    private fun process(image:Image) {
        if (!running) return
        val now=SystemClock.uptimeMillis()
        if (now-lastFrame<100) return
        lastFrame=now
        var frame=yuvToBitmap(image)
        if (sensorOrientation!=0) {
            val mat=Matrix().apply { postRotate(sensorOrientation.toFloat()) }
            val rotated=Bitmap.createBitmap(frame,0,0,frame.width,frame.height,mat,true)
            frame.recycle();frame=rotated
        }
        val result=landmarker?.detect(BitmapImageBuilder(frame).build())
        frame.recycle()
        val hand=result?.landmarks()?.firstOrNull()
        if (hand==null || hand.size<21) {
            pinch.reset();signal=HandSignal(false,0.5f,0.5f,false,now)
            return
        }
        val thumb=hand[4];val index=hand[8]
        val d=hypot(thumb.x()-index.x(),thumb.y()-index.y())
        val pressed=pinch.update(d)
        val prior=signal
        val x = (prior.x*0.45f+index.x()*0.55f).coerceIn(0f,1f)
        val y = (prior.y*0.45f+index.y()*0.55f).coerceIn(0f,1f)
        signal=HandSignal(true,x,y,pressed,now)
    }

    private fun yuvToBitmap(image:Image):Bitmap {
        val w=image.width; val h=image.height
        val p=image.planes
        val yb=p[0].buffer; val ub=p[1].buffer; val vb=p[2].buffer
        val pixels=IntArray(w*h)
        var i=0
        for (row in 0 until h) {
            val yRow=row*p[0].rowStride
            val uvRow=(row/2)*p[1].rowStride
            val vRow=(row/2)*p[2].rowStride
            for (col in 0 until w) {
                val yy=(yb.get(yRow+col*p[0].pixelStride).toInt() and 0xff)
                val uu=(ub.get(uvRow+(col/2)*p[1].pixelStride).toInt() and 0xff)-128
                val vv=(vb.get(vRow+(col/2)*p[2].pixelStride).toInt() and 0xff)-128
                val r=(yy+1.402f*vv).toInt().coerceIn(0,255)
                val g=(yy-0.344f*uu-0.714f*vv).toInt().coerceIn(0,255)
                val b=(yy+1.772f*uu).toInt().coerceIn(0,255)
                pixels[i++]=0xff000000.toInt() or (r shl 16) or (g shl 8) or b
            }
        }
        return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888)
    }

    fun stop() {
        running=false
        signal=HandSignal(false,0.5f,0.5f,false,SystemClock.uptimeMillis())
        runCatching{session?.close()};session=null
        runCatching{camera?.close()};camera=null
        runCatching{reader?.close()};reader=null
        runCatching{landmarker?.close()};landmarker=null
        worker?.quitSafely();worker=null;handler=null
        pinch.reset()
    }
}
