package com.visionosmobile.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build

data class AndroidApp(val label: String, val packageName: String, val icon: Bitmap?)

object AppCatalog {
    fun load(context: Context): List<AndroidApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val results = if (Build.VERSION.SDK_INT >= 33)
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        else @Suppress("DEPRECATION") pm.queryIntentActivities(intent, 0)
        return results.mapNotNull { item ->
            val pkg = item.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == context.packageName) return@mapNotNull null
            AndroidApp(
                item.loadLabel(pm).toString().take(28), pkg,
                runCatching { item.loadIcon(pm).toBitmap(100) }.getOrNull()
            )
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }

    private fun Drawable.toBitmap(size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val previous = bounds
        setBounds(0, 0, size, size)
        draw(canvas)
        bounds = previous
        return bitmap
    }
}
