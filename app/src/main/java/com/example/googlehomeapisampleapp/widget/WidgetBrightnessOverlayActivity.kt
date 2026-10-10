package com.example.googlehomeapisampleapp.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.googlehomeapisampleapp.HomeClientProvider
import com.example.googlehomeapisampleapp.ui.theme.GoogleHomeAPISampleAppTheme
import com.example.googlehomeapisampleapp.view.lights.LightDialControl
import com.google.home.google.ExtendedColorControl
import com.google.home.matter.standard.LevelControl
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

@AndroidEntryPoint
class WidgetBrightnessOverlayActivity : ComponentActivity() {

    @Inject
    lateinit var homeClientProvider: HomeClientProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deviceId = intent.getStringExtra("device_id")

        setContent {
            val scope = rememberCoroutineScope()
            var brightnessTrait by remember { mutableStateOf<LevelControl?>(null) }
            var colorTrait by remember { mutableStateOf<ExtendedColorControl?>(null) }
            var isLoaded by remember { mutableStateOf(false) }
            var loadError by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(deviceId) {
                try {
                    if (deviceId == null) {
                        loadError = "找不到 widget 控制目標"
                    } else {
                        val client = homeClientProvider.getClient()
                        val structures = client.structures().first()
                        for (structure in structures) {
                            val devices = structure.devices().first()
                            val device = devices.find { it.id.toString() == deviceId }
                            if (device != null) {
                                val types = device.types().first()
                                brightnessTrait = types.flatMap { it.traits() }.filterIsInstance<LevelControl>().firstOrNull()
                                colorTrait = types.flatMap { it.traits() }.filterIsInstance<ExtendedColorControl>().firstOrNull()
                                if (brightnessTrait == null && colorTrait == null) {
                                    loadError = "此裝置不支援亮度或顏色控制"
                                }
                                break
                            }
                        }
                        if (brightnessTrait == null && colorTrait == null && loadError == null) {
                            loadError = "找不到此燈具，請先同步 widget"
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    loadError = e.message ?: "無法讀取 Google Home 裝置"
                    android.util.Log.e("WidgetBrightnessOverlay", "Failed to load light controls", e)
                } finally {
                    isLoaded = true
                }
            }

            GoogleHomeAPISampleAppTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isLoaded) {
                        Text("Loading...", color = Color.White)
                    } else if (loadError != null) {
                        Text(loadError.orEmpty(), color = Color.White)
                    } else {
                        LightDialControl(
                            brightnessTrait = brightnessTrait,
                            colorTrait = colorTrait,
                            isEnabled = true,
                            scope = scope,
                            onError = { message ->
                                android.widget.Toast.makeText(this@WidgetBrightnessOverlayActivity, message, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        WidgetSyncScheduler.enqueueNow(applicationContext)
    }
}
