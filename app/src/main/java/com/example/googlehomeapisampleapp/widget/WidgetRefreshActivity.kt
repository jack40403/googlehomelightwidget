package com.example.googlehomeapisampleapp.widget

import android.os.Bundle
import androidx.activity.ComponentActivity

class WidgetRefreshActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WidgetSyncScheduler.enqueueNow(applicationContext)

        finish()
    }
}
