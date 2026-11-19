package com.visionosmobile

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Build
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Native 2D diagnostic entry point. It makes startup failures visible even when
 * the stereo GLSurfaceView cannot draw a frame on a specific Android device.
 */
class LauncherActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(8,16,34)
        window.navigationBarColor = Color.rgb(8,16,34)

        val outer = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(8,16,34))
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(26), dp(24), dp(26), dp(32))
        }
        outer.addView(layout)
        fun label(text: String, size: Float, color: Int): TextView =
            TextView(this).apply {
                this.text = text
                textSize = size
                setTextColor(color)
                setPadding(0, dp(8), 0, dp(8))
            }

        layout.addView(label("VisionOS Mobile", 28f, Color.WHITE))
        layout.addView(label(
            "Testversie voor de uitgeklapte Galaxy Z Flip5. " +
            "Dit startscherm is gewoon 2D en moet altijd zichtbaar zijn. " +
            "Na 'Start VR' verschijnen twee beelden naast elkaar.",
            15f, Color.rgb(189,207,231)
        ))

        fun action(title: String, callback: () -> Unit) {
            val button = Button(this).apply {
                text = title
                isAllCaps = false
                setOnClickListener { callback() }
            }
            layout.addView(button, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58)
            ).apply { topMargin = dp(12) })
        }
        action("Start VR") {
            startActivity(Intent(this, VisionActivity::class.java))
        }
        action("Herstel VR-instellingen") {
            getSharedPreferences("visionos_settings", MODE_PRIVATE)
                .edit().clear().apply()
            updateStatus("VR-instellingen hersteld. Tik op Start VR.")
        }
        layout.addView(label("Toestelcontrole", 20f, Color.WHITE))
        status = label("", 15f, Color.rgb(197,221,246))
        layout.addView(status)
        setContentView(outer)
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) updateStatus()
    }

    private fun updateStatus(extra: String = "") {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val gl = am.deviceConfigurationInfo.reqGlEsVersion
        val sm = getSystemService(SENSOR_SERVICE) as SensorManager
        val gyro = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
        val motion = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) != null ||
            sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null
        val abi = Build.SUPPORTED_ABIS.joinToString(", ")
        status.text = """
            App gestart: ja
            Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
            Model: ${Build.MODEL}
            OpenGL ES: ${gl shr 16}.${gl and 65535}
            Gyroscoop: ${if (gyro) "aanwezig" else "ontbreekt"}
            Rotatietracking: ${if (motion) "beschikbaar" else "ontbreekt"}
            Processor: $abi

            $extra

            Zie je na Start VR zwart of niets? Druk op Terug om naar
            dit testscherm terug te keren. Maak een screenshot.
        """.trimIndent()
    }

    private fun dp(px: Int) = (resources.displayMetrics.density * px + .5f).toInt()
}
