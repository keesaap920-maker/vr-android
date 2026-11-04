package com.visionosmobile.tracking

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import kotlin.math.*

/** Three-degrees-of-freedom orientation in the physical headset's landscape coordinate system. */
class HeadTracker(context:Context):SensorEventListener {
    private val manager=context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor=manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val latest=FloatArray(9)
    private val zero=FloatArray(9)
    private var initialized=false
    private val rotation=runCatching {context.display?.rotation ?: Surface.ROTATION_90}
        .getOrDefault(Surface.ROTATION_90)
    private val right=if(rotation==Surface.ROTATION_270) floatArrayOf(0f,-1f,0f)
        else floatArrayOf(0f,1f,0f)
    private val up=if(rotation==Surface.ROTATION_270) floatArrayOf(1f,0f,0f)
        else floatArrayOf(-1f,0f,0f)
    private val forward=floatArrayOf(0f,0f,-1f)
    @Volatile var yaw=0f
        private set
    @Volatile var pitch=0f
        private set
    @Volatile var roll=0f
        private set
    @Volatile var hasTracking=false
        private set
    fun start() {sensor?.let{manager.registerListener(this,it,SensorManager.SENSOR_DELAY_FASTEST)}}
    fun stop(){manager.unregisterListener(this)}
    @Synchronized fun recenter(){latest.copyInto(zero);initialized=true;yaw=0f;pitch=0f;roll=0f}
    override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int){}
    @Synchronized override fun onSensorChanged(event:SensorEvent){
        if(event.sensor.type!=Sensor.TYPE_GAME_ROTATION_VECTOR && event.sensor.type!=Sensor.TYPE_ROTATION_VECTOR)return
        SensorManager.getRotationMatrixFromVector(latest,event.values)
        if(!initialized){latest.copyInto(zero);initialized=true}
        // Relative rotation = transpose(R at recenter) × R now, both device→world.
        val rel=FloatArray(9)
        for(row in 0..2)for(col in 0..2) {
            rel[row*3+col]=(0..2).sumOf{ k->(zero[k*3+row]*latest[k*3+col]).toDouble() }.toFloat()
        }
        fun transform(v:FloatArray)=floatArrayOf(
            rel[0]*v[0]+rel[1]*v[1]+rel[2]*v[2],
            rel[3]*v[0]+rel[4]*v[1]+rel[5]*v[2],
            rel[6]*v[0]+rel[7]*v[1]+rel[8]*v[2]
        )
        fun dot(a:FloatArray,b:FloatArray)=a[0]*b[0]+a[1]*b[1]+a[2]*b[2]
        val f=transform(forward)
        val x=dot(f,right)
        val y=dot(f,up)
        val z=dot(f,forward)
        yaw=atan2(x,z)
        pitch=atan2(y,sqrt(x*x+z*z)).coerceIn(-1.45f,1.45f)
        val movedUp=transform(up)
        roll=atan2(dot(movedUp,right),dot(movedUp,up))
        hasTracking=true
    }
}
