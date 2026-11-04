package com.visionosmobile.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

/** Browser capture is experimental: support depends on Android WebView's offscreen drawing implementation. */
class BrowserBridge(context:Context) {
    private val ui=Handler(Looper.getMainLooper())
    val webView:WebView=WebView(context)
    @Volatile var revision=0
        private set
    @Volatile var status="Klaar"
        private set
    private val lock=Any()
    private var frame:Bitmap?=null
    private var active=false
    private var captureScheduled=false

    init {
        webView.settings.javaScriptEnabled=true
        webView.settings.domStorageEnabled=true
        webView.settings.setSupportZoom(true)
        webView.settings.builtInZoomControls=false
        webView.overScrollMode=View.OVER_SCROLL_NEVER
        webView.webViewClient=object:WebViewClient() {
            override fun onPageFinished(view:WebView?,url:String?) { status=url.orEmpty();scheduleCapture() }
            override fun shouldOverrideUrlLoading(view:WebView?,request:WebResourceRequest?):Boolean {
                val scheme=request?.url?.scheme
                return scheme!="https" && scheme!="http"
            }
        }
        // Attached in the Activity below the VR surface so chromium can layout and render.
        webView.measure(View.MeasureSpec.makeMeasureSpec(900,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(560,View.MeasureSpec.EXACTLY))
        webView.layout(0,0,900,560)
    }
    fun setActive(value:Boolean) { ui.post { active=value; if(value) scheduleCapture() } }
    fun navigate(text:String) {
        ui.post {
            val raw=text.trim()
            val url=when {
                raw.startsWith("https://") || raw.startsWith("http://") -> raw
                raw.contains(".") && !raw.contains(" ") -> "https://$raw"
                else -> "https://www.google.com/search?q=${android.net.Uri.encode(raw)}"
            }
            if (url.startsWith("https://") || url.startsWith("http://")) {
                status="Laden: $url";webView.loadUrl(url);active=true;scheduleCapture()
            }
        }
    }
    fun back() { ui.post { if(webView.canGoBack()) webView.goBack() } }
    fun forward() { ui.post { if(webView.canGoForward()) webView.goForward() } }
    fun scroll(down:Boolean) { ui.post { webView.scrollBy(0,if(down) 320 else -320);scheduleCapture() } }
    fun tap(u:Float,v:Float) {
        ui.post {
            val x=((u-0.05f)/0.90f*900).coerceIn(0f,899f)
            val y=((v-0.35f)/0.60f*560).coerceIn(0f,559f)
            val now=android.os.SystemClock.uptimeMillis()
            val down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0)
            val up=MotionEvent.obtain(now,now+80,MotionEvent.ACTION_UP,x,y,0)
            try { webView.dispatchTouchEvent(down);webView.dispatchTouchEvent(up) }
            finally { down.recycle();up.recycle() }
            scheduleCapture()
        }
    }
    fun type(text:String) {
        ui.post {
            val script=if(text=="BACK") """(function(){var e=document.activeElement;if(e&&'value'in e){e.value=e.value.slice(0,-1);e.dispatchEvent(new Event('input',{bubbles:true}));}})();"""
            else """(function(){var e=document.activeElement;if(e&&'value'in e){var s=${JSONObject.quote(text)};e.value+=s;e.dispatchEvent(new Event('input',{bubbles:true}));}})();"""
            webView.evaluateJavascript(script,null)
            scheduleCapture()
        }
    }
    /** Call from the GL thread; result is a defensive copy. */
    fun snapshot():Pair<Int,Bitmap?> = synchronized(lock){ revision to frame?.copy(Bitmap.Config.ARGB_8888,false) }
    private fun scheduleCapture() {
        if(captureScheduled || !active) return
        captureScheduled=true
        ui.postDelayed({
            captureScheduled=false
            if(active) {
                runCatching {
                    val b=Bitmap.createBitmap(900,560,Bitmap.Config.ARGB_8888)
                    val canvas=Canvas(b)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    webView.draw(canvas)
                    synchronized(lock){frame?.recycle();frame=b;revision++}
                }.onFailure { status="WebView capture: ${it.javaClass.simpleName}" }
                scheduleCapture()
            }
        },400)
    }
    fun destroy() {
        active=false
        ui.removeCallbacksAndMessages(null)
        webView.stopLoading();webView.destroy()
        synchronized(lock) { frame?.recycle();frame=null }
    }
}
