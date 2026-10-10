package com.example.googlehomeapisampleapp.widget

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.googlehomeapisampleapp.BuildConfig
import com.example.googlehomeapisampleapp.HomeClientProvider
import com.example.googlehomeapisampleapp.MainActivity
import com.example.googlehomeapisampleapp.group.DeviceGroupController
import com.example.googlehomeapisampleapp.ui.theme.GoogleHomeAPISampleAppTheme
import com.example.googlehomeapisampleapp.view.lights.BrightnessDial
import com.example.googlehomeapisampleapp.view.lights.brightnessDialAngleForValue
import com.example.googlehomeapisampleapp.view.lights.brightnessLevelFromDialValue
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@AndroidEntryPoint
class WidgetBrightnessOverlayActivity : ComponentActivity() {
  @Inject lateinit var homeClientProvider: HomeClientProvider

  private val debugSimulationEnabled: Boolean
    get() = BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_DEBUG_SIMULATION, false)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    configureWidgetOverlayWindow(TAG)
    val initialState = LightWidgetStore.load(applicationContext)

    setContent {
      GoogleHomeAPISampleAppTheme {
        WidgetBrightnessOverlayScreen(
          initialState = initialState,
          onLoadState = { loadCurrentState() },
          onSaveLevel = { brightness, operationId ->
            // Once sent, the command and its complete/fail bookkeeping must finish even if the
            // overlay is dismissed (e.g. Home pressed), otherwise the widget is rolled back.
            withContext(NonCancellable) { saveBrightness(brightness, operationId) }
          },
          onAuthorize = { openPermissionFlow() },
          onClose = { finish() },
        )
      }
    }
  }

  private suspend fun loadCurrentState(): OverlayLoadResult {
    val savedState = LightWidgetStore.load(applicationContext)
    if (savedState.deviceIds.isEmpty() && savedState.deviceId == null) {
      return OverlayLoadResult(
        state = savedState,
        canControl = false,
        message = "請先在 App 選擇房間或群組",
      )
    }
    if (debugSimulationEnabled) {
      return OverlayLoadResult(
        state = savedState,
        canControl = true,
        message = "Debug 模擬模式：不連線 Google Home",
      )
    }
    if (!homeClientProvider.hasWidgetPermissions()) {
      return OverlayLoadResult(
        state = savedState,
        canControl = false,
        message = "請先授權 Google Home 存取權",
      )
    }

    return runCatching {
      val refreshedState = homeClientProvider.getClient().readWidgetState(
        context = applicationContext,
        state = savedState,
        forceRefresh = true,
      )
      Log.i(
        TAG,
        "Overlay state loaded: isOn=${refreshedState.isOn}, " +
          "brightness=${refreshedState.brightnessLevel}, stateVersion=${refreshedState.stateVersion}",
      )
      OverlayLoadResult(refreshedState, canControl = true, message = null)
    }.getOrElse { error ->
      Log.e(TAG, "Overlay state read failed; using last valid state", error)
      OverlayLoadResult(
        state = savedState,
        canControl = true,
        message = "無法讀取最新狀態，先使用上次有效資料",
      )
    }
  }

  private suspend fun saveBrightness(
    brightness: Float,
    operationId: String,
  ): OverlaySaveResult {
    var commandToken: WidgetCommandToken? = null
    return try {
      val state = LightWidgetStore.load(applicationContext)
      if (state.deviceIds.isEmpty() && state.deviceId == null) {
        return OverlaySaveResult(false, "Widget 尚未設定控制目標")
      }
      val normalizedBrightness = brightness.coerceIn(0f, 1f)
      val level = brightnessLevelFromDialValue(normalizedBrightness)
      if (debugSimulationEnabled) {
        return simulateBrightness(state, level, operationId)
      }
      if (!homeClientProvider.hasWidgetPermissions()) {
        return OverlaySaveResult(false, "Google Home 尚未授權")
      }

      val client = homeClientProvider.getClient()
      val devices = client.resolveWidgetDevices(applicationContext, state)
      if (devices.isEmpty()) error("Widget 控制目標已不存在或目前不可用")

      val token = checkNotNull(
        WidgetCommandCoordinator.begin(
          context = applicationContext,
          reason = "widget_brightness",
          operationIdOverride = operationId,
          expectedIsOn = level > 0,
          expectedBrightnessLevel = level,
        ),
      )
      commandToken = token
      Log.i(
        TAG,
        "Home brightness command started: operationId=$operationId, " +
          "deviceIds=${devices.map { it.id.id }}, level=$level",
      )
      val failures = DeviceGroupController.setBrightness(devices, normalizedBrightness)
      Log.i(
        TAG,
        "Home brightness command completed: operationId=$operationId, failures=${failures.size}",
      )
      if (failures.size == devices.size) {
        WidgetCommandCoordinator.fail(
          applicationContext,
          token,
          "${failures.size} 個燈具亮度調整失敗",
        )
        OverlaySaveResult(false, "所有燈具亮度調整失敗")
      } else {
        val persistedState = WidgetCommandCoordinator.complete(
          context = applicationContext,
          homeClient = client,
          token = token,
          partialFailure = failures.takeIf { it.isNotEmpty() }?.let {
            "${it.size} 個燈具亮度調整失敗"
          },
        )
        val errorMessage = persistedState?.lastSyncError
        if (persistedState == null || persistedState.syncStatus == WidgetSyncStatus.ERROR) {
          OverlaySaveResult(false, errorMessage ?: "亮度已送出，但同步狀態不明")
        } else {
          OverlaySaveResult(true, null)
        }
      }
    } catch (error: CancellationException) {
      throw error
    } catch (error: Exception) {
      Log.e(TAG, "Overlay brightness command failed: operationId=$operationId", error)
      commandToken?.let {
        WidgetCommandCoordinator.fail(
          context = applicationContext,
          token = it,
          errorMessage = error.message ?: error.javaClass.simpleName,
        )
      }
      OverlaySaveResult(false, "亮度調整失敗：${error.message ?: error.javaClass.simpleName}")
    }
  }

  private suspend fun simulateBrightness(
    previousState: LightWidgetState,
    level: Int,
    operationId: String,
  ): OverlaySaveResult {
    Log.i(
      TAG,
      "Debug simulated Home command started: operationId=$operationId, level=$level",
    )
    val optimisticState = createOptimisticWidgetState(
      previousState = previousState,
      operationId = operationId,
      expectedIsOn = level > 0,
      expectedBrightnessLevel = level,
    )
    val persistedState = LightWidgetStore.save(
      applicationContext,
      optimisticState.copy(
        syncStatus = WidgetSyncStatus.CONNECTED,
        lastSyncError = null,
        source = WidgetStateSource.DEBUG,
        pendingIsOn = null,
        pendingBrightnessLevel = null,
      ),
    )
    updateLightDialWidgets(applicationContext, reason = "debug_overlay_brightness:$operationId")
    Log.i(
      TAG,
      "Debug simulated Home command completed: operationId=$operationId, " +
        "stateVersion=${persistedState.stateVersion}, brightness=${persistedState.brightnessLevel}",
    )
    return OverlaySaveResult(true, null)
  }

  private fun openPermissionFlow() {
    startActivity(
      Intent(this, MainActivity::class.java).apply {
        action = MainActivity.ACTION_REQUEST_HOME_PERMISSIONS
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
      },
    )
    finish()
  }

  companion object {
    const val EXTRA_DEBUG_SIMULATION = "debugSimulation"
    private const val TAG = "WidgetBrightnessOverlay"
  }
}

private data class OverlayLoadResult(
  val state: LightWidgetState,
  val canControl: Boolean,
  val message: String?,
)

private data class OverlaySaveResult(
  val success: Boolean,
  val message: String?,
)

@Composable
private fun WidgetBrightnessOverlayScreen(
  initialState: LightWidgetState,
  onLoadState: suspend () -> OverlayLoadResult,
  onSaveLevel: suspend (Float, String) -> OverlaySaveResult,
  onAuthorize: () -> Unit,
  onClose: () -> Unit,
) {
  var currentState by remember(initialState) { mutableStateOf(initialState) }
  var brightness by remember(initialState) {
    mutableFloatStateOf((initialState.brightnessLevel ?: 0).coerceIn(0, 254) / 254f)
  }
  var isLoading by remember { mutableStateOf(true) }
  var isSaving by remember { mutableStateOf(false) }
  var canControl by remember { mutableStateOf(false) }
  var statusMessage by remember { mutableStateOf<String?>(null) }
  var dragOperationId by remember { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()
  val appContext = LocalContext.current.applicationContext
  val previewState = currentState.copy(
    brightnessLevel = brightnessLevelFromDialValue(brightness),
  )
  val previewColor = widgetDisplayColor(previewState)

  BackHandler(enabled = isSaving) {}

  LaunchedEffect(Unit) {
    val result = runCatching { onLoadState() }.getOrElse { error ->
      Log.e("WidgetBrightnessOverlay", "Overlay initial state load failed", error)
      OverlayLoadResult(
        state = initialState,
        canControl = false,
        message = "無法載入亮度狀態",
      )
    }
    currentState = result.state
    result.state.brightnessLevel?.let {
      brightness = it.coerceIn(0, 254) / 254f
    }
    canControl = result.canControl
    statusMessage = result.message
    isLoading = false
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black.copy(alpha = 0.46f))
      .pointerInput(isSaving) {
        detectTapGestures(onTap = { if (!isSaving) onClose() })
      },
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .clickable(enabled = !isSaving, onClick = {}),
      shape = RoundedCornerShape(28.dp),
      color = Color(0xFF213236).copy(alpha = 0.98f),
      tonalElevation = 8.dp,
      shadowElevation = 16.dp,
    ) {
      Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "調整亮度",
              color = Color.White,
              style = MaterialTheme.typography.headlineSmall,
            )
            Text(
              text = currentState.displayName,
              color = Color(0xFFC7D6CB),
              style = MaterialTheme.typography.bodyMedium,
            )
          }
          Box(
            modifier = Modifier
              .size(22.dp)
              .background(previewColor, CircleShape),
          )
        }
        Text(
          text = "拖曳圓弧，放開後套用到 Google Home",
          color = Color(0xFFC7D6CB),
          style = MaterialTheme.typography.bodyMedium,
        )
        BrightnessDial(
          value = brightness,
          enabled = !isLoading && !isSaving && canControl,
          accentColor = previewColor,
          onValueChange = { brightness = it.coerceIn(0f, 1f) },
          onDragStart = { position, value, angle ->
            val operationId = "widget-brightness-${UUID.randomUUID()}"
            dragOperationId = operationId
            Log.i(
              "WidgetBrightnessOverlay",
              "drag start: operationId=$operationId, position=$position, angle=$angle, " +
                "percent=${(value * 100f).roundToInt()}%",
            )
          },
          onDragMove = { position, value, angle ->
            Log.i(
              "WidgetBrightnessOverlay",
              "drag move: operationId=${dragOperationId ?: "none"}, position=$position, " +
                "angle=$angle, percent=${(value * 100f).roundToInt()}%",
            )
          },
          onDragCancel = {
            Log.i(
              "WidgetBrightnessOverlay",
              "drag cancel: operationId=${dragOperationId ?: "none"}",
            )
            dragOperationId = null
          },
          onValueChangeFinished = { value ->
            val operationId = dragOperationId ?: "widget-brightness-${UUID.randomUUID()}"
            Log.i(
              "WidgetBrightnessOverlay",
              "drag end: operationId=$operationId, angle=${brightnessDialAngleForValue(value)}, " +
                "percent=${(value * 100f).roundToInt()}%",
            )
            dragOperationId = null
            if (!isSaving) {
              isSaving = true
              statusMessage = "正在同步 Google Home…"
              scope.launch {
                val result = runCatching {
                  onSaveLevel(value.coerceIn(0f, 1f), operationId)
                }.getOrElse { error ->
                  OverlaySaveResult(false, "亮度調整失敗：${error.message ?: error.javaClass.simpleName}")
                }
                if (result.success) {
                  currentState = LightWidgetStore.load(appContext)
                  statusMessage = "已同步到 Google Home"
                  isSaving = false
                  onClose()
                } else {
                  statusMessage = result.message ?: "亮度調整失敗"
                  isSaving = false
                }
              }
            }
          },
        )
        Text(
          text = "${(brightness * 100f).roundToInt()}%",
          color = previewColor,
          style = MaterialTheme.typography.headlineSmall,
        )
        if (statusMessage != null) {
          Text(
            text = statusMessage!!,
            color = if (canControl) Color(0xFFC7D6CB) else Color(0xFFFFB4AB),
            style = MaterialTheme.typography.bodyMedium,
          )
        }
        if (!canControl && !isLoading && statusMessage?.contains("授權") == true) {
          Button(
            onClick = onAuthorize,
            colors = ButtonDefaults.buttonColors(
              containerColor = Color(0xFF9FE6C8),
              contentColor = Color(0xFF10251E),
            ),
            shape = RoundedCornerShape(16.dp),
          ) {
            Text("前往授權")
          }
        }
        if (isLoading) {
          Text("讀取 Google Home 狀態…", color = Color(0xFFC7D6CB))
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          TextButton(onClick = onClose, enabled = !isSaving) {
            Text("取消")
          }
        }
      }
    }
  }
}
