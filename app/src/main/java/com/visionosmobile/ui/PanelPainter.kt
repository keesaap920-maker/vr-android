package com.visionosmobile.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextUtils

/** Every panel is a Canvas-textured GLES2 rectangle; all UI hit areas share PanelLayout mapping. */
object PanelPainter {
    private const val W=1024
    private const val H=720
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    fun render(panel: WindowPanel, activeKey:String, web:Bitmap? = null, gallery:Bitmap? = null):Bitmap {
        val bmp=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888)
        val c=Canvas(bmp)
        paint.shader=LinearGradient(0f,0f,W.toFloat(),H.toFloat(),
            intArrayOf(Color.argb(244,29,41,60),Color.argb(238,12,20,37)),null,Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(7f,7f,W-7f,H-7f),48f,48f,paint)
        paint.shader=null
        paint.color=Color.argb(100,180,204,249)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=2f
        c.drawRoundRect(RectF(8f,8f,W-8f,H-8f),46f,46f,paint)
        paint.style=Paint.Style.FILL
        paint.color=Color.argb(170,33,50,76)
        c.drawRoundRect(RectF(12f,12f,W-12f,128f),42f,42f,paint)
        paint.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        paint.color=Color.WHITE
        paint.textSize=48f
        c.drawText(panel.title.take(27),45f,82f,paint)
        paint.textSize=25f
        paint.color=Color.rgb(157,209,255)
        c.drawText("≡ bewegen",W-320f,78f,paint)
        drawCircleButton(c,W-82f,76f,"×")
        drawCircleButton(c,W-154f,76f,"+")
        when(panel.id) {
            "browser" -> drawBrowser(c,panel,activeKey,web)
            "gallery" -> drawGallery(c,panel,gallery)
            else -> drawGrid(c,panel,activeKey)
        }
        return bmp
    }
    private fun drawCircleButton(c:Canvas,x:Float,y:Float,t:String) {
        paint.color=Color.argb(170,69,85,111)
        c.drawCircle(x,y,25f,paint)
        paint.color=Color.WHITE;paint.textSize=31f;paint.textAlign=Paint.Align.CENTER
        c.drawText(t,x,y+10f,paint)
        paint.textAlign=Paint.Align.LEFT
    }
    private fun drawGrid(c:Canvas,p:WindowPanel,key:String) {
        val left=0.03f*W;val top=0.18f*H;val right=0.97f*W;val bottom=0.92f*H
        val cols=p.columns;val rows=p.rows
        val cellW=(right-left)/cols
        val cellH=(bottom-top)/rows
        val max=cols*rows
        for(index in 0 until minOf(max,p.tiles.size)) {
            val item=p.tiles[index]
            val row=index/cols;val col=index%cols
            val x=left+cellW*col;val y=top+cellH*row
            val highlight=key=="${p.id}:${index+1}"
            paint.color=if(highlight) Color.argb(245,65,126,196) else Color.argb(155,44,64,93)
            val margin=if(cols>=8) 4f else 10f
            val rect=RectF(x+margin,y+margin,x+cellW-margin,y+cellH-margin)
            c.drawRoundRect(rect,if(cols>=8) 11f else 24f,if(cols>=8) 11f else 24f,paint)
            paint.style=Paint.Style.STROKE;paint.color=Color.argb(95,166,206,242);paint.strokeWidth=2f
            c.drawRoundRect(rect,if(cols>=8) 11f else 24f,if(cols>=8) 11f else 24f,paint)
            paint.style=Paint.Style.FILL
            if(item.icon!=null) {
                val iconSize=minOf(cellH*0.53f,cellW*0.48f)
                val iconRect=RectF(x+cellW/2-iconSize/2,y+cellH*0.07f,
                    x+cellW/2+iconSize/2,y+cellH*0.07f+iconSize)
                c.drawBitmap(item.icon,null,iconRect,paint)
            } else {
                paint.color=if(highlight) Color.WHITE else Color.rgb(160,201,251)
                paint.textSize=if(cols>=8) 27f else 46f
                paint.textAlign=Paint.Align.CENTER
                c.drawText(symbol(item.action),x+cellW/2,y+cellH*(if(rows>=8)0.56f else 0.54f),paint)
            }
            paint.textAlign=Paint.Align.CENTER
            paint.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
            paint.color=Color.WHITE
            paint.textSize=when {
                cols>=8 -> 16f
                rows>=8 -> 21f
                else -> 29f
            }
            c.drawText(trim(item.text,if(cols>=8)10 else if(cols>=4)15 else 19),x+cellW/2,
                y+cellH*0.85f,paint)
            paint.textAlign=Paint.Align.LEFT
        }
        if(p.info.isNotBlank() && p.id!="keyboard") drawInfo(c,p.info)
        if(p.id=="keyboard") {
            paint.textSize=20f;paint.color=Color.rgb(162,199,236)
            c.drawText(p.info.take(80),45f,H-18f,paint)
        }
    }
    private fun drawInfo(c:Canvas,value:String){
        paint.color=Color.argb(215,163,192,229)
        paint.textSize=22f
        value.split('\n').take(2).forEachIndexed{ i,line -> c.drawText(trim(line,87),40f,H-34f+i*26f,paint) }
    }
    private fun drawBrowser(c:Canvas,p:WindowPanel,key:String,web:Bitmap?) {
        val cols=6
        val toolbarY=145f
        p.tiles.take(6).forEachIndexed { i,item ->
            val x=28f+i*161f
            paint.color=if(key=="${p.id}:${i+1}") Color.rgb(56,128,195) else Color.argb(230,49,65,90)
            c.drawRoundRect(RectF(x,toolbarY,x+151f,242f),16f,16f,paint)
            paint.color=Color.WHITE;paint.textAlign=Paint.Align.CENTER;paint.textSize=25f
            c.drawText(item.text,x+75f,203f,paint)
        }
        paint.textAlign=Paint.Align.LEFT
        if(web!=null) {
            paint.color=Color.WHITE
            c.drawRoundRect(RectF(48f,256f,W-48f,H-40f),20f,20f,paint)
            c.save();c.clipRect(RectF(52f,259f,W-52f,H-42f))
            c.drawBitmap(web,null,RectF(52f,259f,W-52f,H-42f),paint)
            c.restore()
        } else {
            paint.color=Color.argb(245,240,247,255)
            c.drawRoundRect(RectF(50f,256f,W-50f,H-40f),18f,18f,paint)
            paint.textSize=30f;paint.color=Color.DKGRAY
            c.drawText("Browser laden...",95f,365f,paint)
        }
    }
    private fun drawGallery(c:Canvas,p:WindowPanel,img:Bitmap?) {
        if(img!=null) {
            paint.color=Color.argb(255,10,15,23)
            c.drawRoundRect(RectF(52f,145f,W-52f,H-65f),18f,18f,paint)
            val maxW=(W-110f)/img.width;val maxH=(H-225f)/img.height
            val scale=minOf(maxW,maxH)
            val w=img.width*scale;val h=img.height*scale
            c.drawBitmap(img,null,RectF(W/2-w/2,155f+(H-230f-h)/2,W/2+w/2,155f+(H-230f+h)/2),paint)
        } else drawInfo(c,p.info)
    }
    private fun trim(s:String,n:Int):String=if(s.length>n)s.take(n-1)+"…" else s
    private fun symbol(action:String):String=when {
        action=="open:apps" -> "▦"
        action=="open:browser" -> "◎"
        action=="open:media" -> "▶"
        action=="open:files" -> "▤"
        action=="open:settings" -> "⚙"
        action=="open:recent" -> "◴"
        action=="open:help" -> "?"
        action.startsWith("setting:ipd") -> "↔"
        action.startsWith("setting:lens") -> "◉"
        action.startsWith("key:") -> ""
        action=="sys:recenter" -> "⌖"
        else -> "◈"
    }
}
