package com.example.googlehomeapisampleapp.widget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

class WidgetColorPickerActivity : ComponentActivity() {
    companion object {
        val DEVICE_ID_KEY = androidx.glance.action.ActionParameters.Key<String>("device_id")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deviceId = intent.getStringExtra("device_id")

        val overlayIntent = Intent(this, WidgetBrightnessOverlayActivity::class.java).apply {
            putExtra("device_id", deviceId)
            flags = Intent.FLAG_ACTIVITY_FORWARD_RESULT
        }
        startActivity(overlayIntent)
        finish()
    }
}
