package com.visionosmobile

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.OpenableColumns
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import com.visionosmobile.browser.BrowserBridge
import com.visionosmobile.tracking.HeadTracker
import com.visionosmobile.tracking.MediaPipeHandTracker
import com.visionosmobile.ui.Workspace
import com.visionosmobile.ui.WorkspaceHost
import com.visionosmobile.vr.StereoRenderer
import com.visionosmobile.vr.StereoSurfaceView
import android.os.Handler
import android.os.Looper

class VisionActivity:Activity() {
    private lateinit var workspace:Workspace
    private lateinit var head:HeadTracker
    private lateinit var hand:MediaPipeHandTracker
    private lateinit var browser:BrowserBridge
    private lateinit var renderer:StereoRenderer
    private lateinit var stereo:StereoSurfaceView
    private val ui=Handler(Looper.getMainLooper())
    private val fileRequest=701
    private val cameraRequest=702
    private var lastFileMime="*/*"
    private var ready=false
    private var isVisible=false

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN)
        if(Build.VERSION.SDK_INT>=28){
            val params=window.attributes
            params.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes=params
        }
        immersive()
        head=HeadTracker(this)
        browser=BrowserBridge(this)
        hand=MediaPipeHandTracker(this) { message->runOnUiThread {workspace.updateHandStatus(message)} }
        workspace=Workspace(this,object:WorkspaceHost {
            override fun launchPackage(packageName:String) = runOnUiThread {
                val launch=packageManager.getLaunchIntentForPackage(packageName)
                if(launch!=null)runCatching {startActivity(launch)}.onFailure{toast("App kan niet starten")}
                else toast("Geen launch-intent: $packageName")
            }
            override fun selectFile(mimeType:String) = runOnUiThread {
                lastFileMime=mimeType
                val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type=mimeType
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching {startActivityForResult(intent,fileRequest)}.onFailure{toast("Bestandskiezer niet beschikbaar")}
            }
            override fun openExternal(url:String)=runOnUiThread {
                runCatching{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}
            }
            override fun navigateBrowser(url:String){browser.navigate(url);browser.setActive(true)}
            override fun browserTap(u:Float,v:Float){browser.tap(u,v)}
            override fun browserScroll(down:Boolean){browser.scroll(down)}
            override fun browserActivate(enabled:Boolean){browser.setActive(enabled)}
            override fun browserBack(){browser.back()}
            override fun browserForward(){browser.forward()}
            override fun browserType(char:String){browser.type(char)}
            override fun recenter(){head.recenter()}
            override fun setHandtracking(enabled:Boolean)=runOnUiThread {enableHandtracking(enabled)}
            override fun playVideo(command:String){if(ready)stereo.queueEvent {renderer.videoCommand(command)}}
            override fun exitVR()=runOnUiThread {finish()}
        })
        renderer=StereoRenderer(this,workspace,head,hand,browser)
        stereo=StereoSurfaceView(this,renderer)
        val root=FrameLayout(this)
        // Keep WebView attached and painted by Chromium underneath the GLSurfaceView.
        // GLSurfaceView itself remains the visible headset output.
        root.addView(browser.webView,FrameLayout.LayoutParams(900,560))
        root.addView(stereo,FrameLayout.LayoutParams(-1,-1))
        setContentView(root)
        ready=true
        ui.postDelayed({ workspace.refreshApplications() },900)
        if(!getSharedPreferences("visionos_settings",MODE_PRIVATE).getBoolean("intro_seen",false))showIntro()
        monitorThermals()
    }
    private fun immersive(){
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }
    private fun showIntro() {
        AlertDialog.Builder(this)
            .setTitle("VisionOS Mobile · prototype")
            .setMessage("Gebruik alleen een VR-houder waarin de volledig opengeklapte Galaxy Z Flip5 veilig past.\n\n"+
                "• Volume+ = selecteren; Volume- = opnieuw centreren.\n"+
                "• 1,7 seconden naar een knop kijken = selecteren.\n"+
                "• Handtracking is optioneel en vereist de MediaPipe-modelbundel en een vrije achtercamera.\n"+
                "• Gewone Android-apps worden buiten VR geopend.\n\n"+
                "Ga zitten tijdens de eerste kalibratie. Stop onmiddellijk bij duizeligheid of dubbelzien.")
            .setPositiveButton("Start VR") { _,_->
                getSharedPreferences("visionos_settings",MODE_PRIVATE).edit().putBoolean("intro_seen",true).apply()
                immersive();head.recenter()
            }
            .setCancelable(false).show()
    }
    private fun enableHandtracking(enabled:Boolean){
        if(!enabled){hand.stop();workspace.updateHandStatus("Uitgeschakeld");return}
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(arrayOf(Manifest.permission.CAMERA),cameraRequest)
            workspace.updateHandStatus("Cameratoestemming nodig")
        } else hand.start()
    }
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==cameraRequest){
            if(grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED && workspace.handEnabled)hand.start()
            else workspace.updateHandStatus("Cameratoegang geweigerd • gaze actief")
        }
    }
    @Deprecated("Legacy Activity Result kept to avoid requiring AndroidX")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=fileRequest || resultCode!=RESULT_OK)return
        val uri=data?.data ?: return
        val mime=contentResolver.getType(uri) ?: lastFileMime
        val filename=queryName(uri)
        workspace.updateFilename(filename)
        when {
            mime.startsWith("image/") -> {
                workspace.open("gallery")
                Thread {
                    val bitmap=loadImage(uri)
                    runOnUiThread { if(ready)stereo.queueEvent{renderer.setGallery(bitmap)} }
                }.start()
            }
            mime.startsWith("video/") -> {
                workspace.open("media")
                if(ready)stereo.queueEvent {renderer.openVideo(uri)}
            }
            else -> {
                val view=Intent(Intent.ACTION_VIEW).setDataAndType(uri,mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runCatching{startActivity(view)}.onFailure{toast("Geen geschikte bestandsviewer geïnstalleerd")}
            }
        }
    }
    private fun queryName(uri:Uri):String {
        return runCatching {
            contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{
                if(it.moveToFirst())it.getString(0) else "Geselecteerd bestand"
            } ?: "Geselecteerd bestand"
        }.getOrDefault("Geselecteerd bestand")
    }
    private fun loadImage(uri:Uri):Bitmap?=runCatching{
        val options=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,options)}
        var sample=1
        while(options.outWidth/sample>1800 || options.outHeight/sample>1400)sample*=2
        options.inJustDecodeBounds=false;options.inSampleSize=sample
        contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,options)}
    }.getOrNull()
    override fun onResume(){
        super.onResume();isVisible=true;immersive()
        if(ready){stereo.onResume();head.start();if(workspace.handEnabled)enableHandtracking(true)}
    }
    override fun onPause(){
        isVisible=false
        if(ready){hand.stop();head.stop();stereo.onPause()}
        super.onPause()
    }
    override fun onDestroy(){
        isVisible=false
        hand.stop();browser.destroy()
        super.onDestroy()
    }
    override fun onWindowFocusChanged(hasFocus:Boolean){
        super.onWindowFocusChanged(hasFocus)
        if(hasFocus)immersive()
    }
    override fun onKeyDown(keyCode:Int,event:KeyEvent?):Boolean = when(keyCode){
        KeyEvent.KEYCODE_VOLUME_UP -> {workspace.click();true}
        KeyEvent.KEYCODE_VOLUME_DOWN -> {head.recenter();toast("Gecentreerd");true}
        else -> super.onKeyDown(keyCode,event)
    }
    private fun toast(message:String)=Toast.makeText(this,message,Toast.LENGTH_SHORT).show()
    private fun monitorThermals(){
        ui.postDelayed({
            if(!isFinishing && !isDestroyed){
                if(Build.VERSION.SDK_INT>=29){
                    val pm=getSystemService(POWER_SERVICE) as PowerManager
                    if(pm.currentThermalStatus>=PowerManager.THERMAL_STATUS_SEVERE){
                        workspace.updateHandStatus("Waarschuwing: telefoon wordt te warm")
                        if(workspace.handEnabled)hand.stop()
                    }
                }
                monitorThermals()
            }
        },5000)
    }
}
