package com.example.googlehomeapisampleapp.widget

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.googlehomeapisampleapp.HomeClientProvider
import com.example.googlehomeapisampleapp.group.DeviceGroupController
import com.example.googlehomeapisampleapp.ui.theme.GoogleHomeAPISampleAppTheme
import com.example.googlehomeapisampleapp.view.lights.brightnessLevelFromDialValue
import com.example.googlehomeapisampleapp.view.lights.BrightnessDial
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WidgetBrightnessDialActivity : ComponentActivity() {
  @Inject lateinit var homeClientProvider: HomeClientProvider

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {
      GoogleHomeAPISampleAppTheme {
        WidgetBrightnessDialScreen(
          onLoadLevel = { loadCurrentLevel() },
          onSaveLevel = { brightness -> saveBrightness(brightness) },
          onClose = { finish() },
        )
      }
    }
  }

  private suspend fun loadCurrentLevel(): Int? {
    val state = LightWidgetStore.load(applicationContext)
    if (state.deviceIds.isEmpty() && state.deviceId == null) return null
    if (!homeClientProvider.ensureWidgetPermissions(this)) {
      return null
    }
    return homeClientProvider.getClient()
      .readWidgetState(applicationContext, state, forceRefresh = true)
      .brightnessLevel
  }

  private suspend fun saveBrightness(brightness: Float): Boolean {
    var commandToken: WidgetCommandToken? = null
    return try {
      val state = LightWidgetStore.load(applicationContext)
      if (state.deviceIds.isEmpty() && state.deviceId == null) error("No target is selected for the widget")
      if (!homeClientProvider.ensureWidgetPermissions(this)) {
        return false
      }
      val normalizedBrightness = brightness.coerceIn(0f, 1f)
      val level = brightnessLevelFromDialValue(normalizedBrightness)
      val client = homeClientProvider.getClient()
      val devices = client.resolveWidgetDevices(applicationContext, state)
      if (devices.isEmpty()) error("The widget target is no longer available")
      val token = checkNotNull(
        WidgetCommandCoordinator.begin(
          context = applicationContext,
          reason = "widget_brightness",
          expectedIsOn = level > 0,
          expectedBrightnessLevel = level,
        ),
      )
      commandToken = token
      Log.i(
        TAG,
        "Home command started: operationId=${token.operationId}, brightness=$level",
      )
      val failures = DeviceGroupController.setBrightness(devices, normalizedBrightness)
      Log.i(
        TAG,
        "Home command completed: operationId=${token.operationId}, failures=${failures.size}",
      )
      if (failures.size == devices.size) {
        WidgetCommandCoordinator.fail(
          applicationContext,
          token,
          "${failures.size} 個燈具亮度調整失敗",
        )
      } else {
        WidgetCommandCoordinator.complete(
          context = applicationContext,
          homeClient = client,
          token = token,
          partialFailure = failures.takeIf { it.isNotEmpty() }?.let {
            "${it.size} 個燈具亮度調整失敗"
          },
        )
      }
      if (failures.isNotEmpty()) {
        Toast.makeText(this@WidgetBrightnessDialActivity, "${failures.size} 個燈具亮度調整失敗", Toast.LENGTH_SHORT).show()
      }
      finish()
      true
    } catch (error: Exception) {
      Log.e(TAG, "Widget brightness control failed", error)
      commandToken?.let {
        WidgetCommandCoordinator.fail(
          applicationContext,
          it,
          error.message ?: error.javaClass.simpleName,
        )
      }
      Toast.makeText(this@WidgetBrightnessDialActivity, "亮度調整失敗", Toast.LENGTH_SHORT).show()
      false
    }
  }

  companion object {
    private const val TAG = "WidgetBrightnessDial"
  }
}

@Composable
private fun WidgetBrightnessDialScreen(
  onLoadLevel: suspend () -> Int?,
  onSaveLevel: suspend (Float) -> Boolean,
  onClose: () -> Unit,
) {
  val context = LocalContext.current
  val savedState = remember(context) { LightWidgetStore.load(context) }
  var brightness by remember {
    mutableFloatStateOf((savedState.brightnessLevel ?: 0).coerceIn(0, 254) / 254f)
  }
  var isLoading by remember { mutableStateOf(true) }
  var isSaving by remember { mutableStateOf(false) }
  var permissionReady by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()

  LaunchedEffect(Unit) {
    val currentLevel = try {
      val level = onLoadLevel()
      permissionReady = savedState.deviceIds.isNotEmpty() || savedState.deviceId != null
      level
    } catch (_: Exception) {
      permissionReady = false
      null
    }
    if (currentLevel != null) {
      brightness = currentLevel.coerceIn(0, 254) / 254f
    }
    isLoading = false
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF16201B))
      .padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(savedState.displayName, color = Color.White, style = MaterialTheme.typography.titleLarge)
    Text("拖曳旋鈕調整亮度", color = Color(0xFFC7D6CB))
    BrightnessDial(
      value = brightness,
      enabled = !isLoading && !isSaving && permissionReady,
      onValueChange = { brightness = it },
      onValueChangeFinished = {
        if (!isSaving) {
          isSaving = true
          scope.launch {
            if (!onSaveLevel(it)) {
              isSaving = false
            }
          }
        }
      },
    )
    when {
      isLoading -> CircularProgressIndicator(color = Color(0xFFFFC857))
      isSaving -> Text("套用中…", color = Color(0xFFC7D6CB))
      savedState.deviceIds.isEmpty() && savedState.deviceId == null -> Text("請先在 App 選擇房間或群組", color = Color(0xFFC7D6CB))
      !permissionReady -> Text("請先授權 Google Home 存取權", color = Color(0xFFFFB4AB))
      else -> Text("放開手指後套用到 Google Home", color = Color(0xFFC7D6CB))
    }
    androidx.compose.material3.TextButton(onClick = onClose, enabled = !isSaving) {
      Text("取消")
    }
  }
}
