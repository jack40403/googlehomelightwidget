package com.example.googlehomeapisampleapp.widget

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.googlehomeapisampleapp.BuildConfig
import com.example.googlehomeapisampleapp.HomeClientProvider
import com.example.googlehomeapisampleapp.MainActivity
import com.example.googlehomeapisampleapp.group.DeviceGroupController
import com.example.googlehomeapisampleapp.ui.theme.GoogleHomeAPISampleAppTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@AndroidEntryPoint
class WidgetColorPickerActivity : ComponentActivity() {
  @Inject lateinit var homeClientProvider: HomeClientProvider

  private val debugSimulationEnabled: Boolean
    get() = BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_DEBUG_SIMULATION, false)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    configureWidgetOverlayWindow(TAG)
    val initialState = LightWidgetStore.load(applicationContext)
    setContent {
      GoogleHomeAPISampleAppTheme {
        WidgetColorPickerScreen(
          initialState = initialState,
          onApplyColor = { hue, saturation ->
            // Once sent, the command and its complete/fail bookkeeping must finish even if the
            // picker is dismissed (e.g. Home pressed), otherwise the widget is rolled back.
            withContext(NonCancellable) { applyColor(hue, saturation) }
          },
          onAuthorize = { openPermissionFlow() },
          onClose = { finish() },
        )
      }
    }
  }

  private suspend fun applyColor(hue: Float, saturation: Float): ColorApplyResult {
    val state = LightWidgetStore.load(applicationContext)
    if (state.deviceIds.isEmpty() && state.deviceId == null) {
      return ColorApplyResult(false, "Widget 尚未設定控制目標")
    }

    val normalizedHue = ((hue % 360f) + 360f) % 360f
    val normalizedSaturation = saturation.coerceIn(0f, 1f)
    val operationId = "widget-color-${UUID.randomUUID()}"
    Log.i(
      TAG,
      "Color action received: operationId=$operationId, target=" +
        "${state.targetKind}:${state.targetId}, deviceIds=${state.deviceIds}, " +
        "hue=$normalizedHue, saturation=$normalizedSaturation",
    )

    if (debugSimulationEnabled) {
      return simulateColor(state, normalizedHue, normalizedSaturation, operationId)
    }
    if (!homeClientProvider.hasWidgetPermissions()) {
      return ColorApplyResult(false, "Google Home 尚未授權", requiresAuthorization = true)
    }

    var commandToken: WidgetCommandToken? = null
    return try {
      val client = homeClientProvider.getClient()
      val devices = client.resolveWidgetDevices(applicationContext, state)
      if (devices.isEmpty()) error("Widget 控制目標已不存在或目前不可用")

      val token = checkNotNull(
        WidgetCommandCoordinator.begin(
          context = applicationContext,
          reason = "widget_color",
          operationIdOverride = operationId,
          expectedHue = normalizedHue,
          expectedSaturation = normalizedSaturation,
        ),
      )
      commandToken = token
      Log.i(
        TAG,
        "Home color command started: operationId=$operationId, " +
          "deviceIds=${devices.map { it.id.id }}, hue=$normalizedHue, saturation=$normalizedSaturation",
      )
      val failures = DeviceGroupController.setColor(devices, normalizedHue, normalizedSaturation)
      Log.i(
        TAG,
        "Home color command completed: operationId=$operationId, failures=${failures.size}",
      )
      if (failures.size == devices.size) {
        WidgetCommandCoordinator.fail(
          context = applicationContext,
          token = token,
          errorMessage = "${failures.size} 個燈具換色失敗",
        )
        ColorApplyResult(false, "所有燈具換色失敗")
      } else {
        val persistedState = WidgetCommandCoordinator.complete(
          context = applicationContext,
          homeClient = client,
          token = token,
          partialFailure = failures.takeIf { it.isNotEmpty() }?.let {
            "${it.size} 個燈具換色失敗"
          },
        )
        val errorMessage = persistedState?.lastSyncError
        when {
          persistedState == null || persistedState.syncStatus == WidgetSyncStatus.ERROR -> {
            ColorApplyResult(false, errorMessage ?: "燈色已送出，但同步狀態不明")
          }
          failures.isNotEmpty() -> ColorApplyResult(false, "${failures.size} 個燈具換色失敗")
          else -> ColorApplyResult(true, null)
        }
      }
    } catch (error: CancellationException) {
      throw error
    } catch (error: Exception) {
      Log.e(TAG, "Widget color control failed: operationId=$operationId", error)
      commandToken?.let {
        WidgetCommandCoordinator.fail(
          context = applicationContext,
          token = it,
          errorMessage = error.message ?: error.javaClass.simpleName,
        )
      }
      ColorApplyResult(false, "燈色更新失敗：${error.message ?: error.javaClass.simpleName}")
    }
  }

  private suspend fun simulateColor(
    previousState: LightWidgetState,
    hue: Float,
    saturation: Float,
    operationId: String,
  ): ColorApplyResult {
    Log.i(
      TAG,
      "Debug simulated Home color command started: operationId=$operationId, " +
        "hue=$hue, saturation=$saturation",
    )
    val optimisticState = createOptimisticWidgetState(
      previousState = previousState,
      operationId = operationId,
      expectedIsOn = previousState.isOn,
      expectedBrightnessLevel = previousState.brightnessLevel,
      expectedHue = hue,
      expectedSaturation = saturation,
    )
    val persistedState = LightWidgetStore.save(
      applicationContext,
      optimisticState.copy(
        syncStatus = WidgetSyncStatus.CONNECTED,
        lastSyncError = null,
        source = WidgetStateSource.DEBUG,
        pendingIsOn = null,
        pendingBrightnessLevel = null,
        pendingColorHue = null,
        pendingColorSaturation = null,
      ),
    )
    updateLightDialWidgets(applicationContext, reason = "debug_overlay_color:$operationId")
    Log.i(
      TAG,
      "Debug simulated Home color command completed: operationId=$operationId, " +
        "stateVersion=${persistedState.stateVersion}, hue=${persistedState.colorHue}, " +
        "saturation=${persistedState.colorSaturation}",
    )
    return ColorApplyResult(true, null)
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
    private const val TAG = "WidgetColorPicker"
  }
}

private data class ColorApplyResult(
  val success: Boolean,
  val message: String?,
  val requiresAuthorization: Boolean = false,
)

@Composable
private fun WidgetColorPickerScreen(
  initialState: LightWidgetState,
  onApplyColor: suspend (Float, Float) -> ColorApplyResult,
  onAuthorize: () -> Unit,
  onClose: () -> Unit,
) {
  var hue by remember { mutableFloatStateOf(initialState.colorHue ?: 210f) }
  var saturation by remember { mutableFloatStateOf(initialState.colorSaturation ?: 0.72f) }
  var isSaving by remember { mutableStateOf(false) }
  var statusMessage by remember { mutableStateOf<String?>(null) }
  var requiresAuthorization by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  val selectedColor = Color.hsv(hue, saturation, 1f)

  BackHandler(enabled = isSaving) {}

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
              text = "選擇燈色",
              color = Color.White,
              style = MaterialTheme.typography.headlineSmall,
            )
            Text(
              text = initialState.displayName,
              color = Color(0xFFC7D6CB),
              style = MaterialTheme.typography.bodyMedium,
            )
          }
          Box(
            modifier = Modifier
              .size(24.dp)
              .background(selectedColor, CircleShape),
          )
        }
        ColorWheel(
          hue = hue,
          saturation = saturation,
          enabled = !isSaving,
          onColorChange = { selectedHue, selectedSaturation ->
            hue = selectedHue
            saturation = selectedSaturation
            statusMessage = null
            requiresAuthorization = false
          },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(20.dp)
              .background(selectedColor, CircleShape),
          )
          Spacer(Modifier.size(10.dp))
          Text(
            "H ${hue.roundToInt()}° · S ${(saturation * 100).roundToInt()}%",
            color = Color.White,
          )
        }
        Text("拖曳色盤選擇任意顏色", color = Color(0xFFC7D6CB))
        statusMessage?.let {
          Text(
            text = it,
            color = Color(0xFFFFB4AB),
            style = MaterialTheme.typography.bodyMedium,
          )
        }
        if (requiresAuthorization) {
          Button(
            onClick = onAuthorize,
            enabled = !isSaving,
            colors = ButtonDefaults.buttonColors(
              containerColor = Color(0xFF9FE6C8),
              contentColor = Color(0xFF10251E),
            ),
            shape = RoundedCornerShape(16.dp),
          ) {
            Text("前往授權")
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          TextButton(onClick = onClose, enabled = !isSaving) {
            Text("取消")
          }
          Spacer(Modifier.weight(1f))
          Button(
            onClick = {
              isSaving = true
              statusMessage = null
              scope.launch {
                val result = runCatching {
                  onApplyColor(hue, saturation)
                }.getOrElse { error ->
                  ColorApplyResult(false, "燈色更新失敗：${error.message ?: error.javaClass.simpleName}")
                }
                if (result.success) {
                  onClose()
                } else {
                  isSaving = false
                  statusMessage = result.message ?: "燈色更新失敗"
                  requiresAuthorization = result.requiresAuthorization
                }
              }
            },
            enabled = !isSaving,
            colors = ButtonDefaults.buttonColors(
              containerColor = selectedColor,
              contentColor = Color.Black,
            ),
            shape = RoundedCornerShape(16.dp),
          ) {
            Text(if (isSaving) "同步中…" else "套用")
          }
        }
      }
    }
  }
}

@Composable
private fun ColorWheel(
  hue: Float,
  saturation: Float,
  enabled: Boolean,
  onColorChange: (Float, Float) -> Unit,
) {
  val wheelBitmap = remember { createColorWheelBitmap(640).asImageBitmap() }
  Canvas(
    modifier = Modifier
      .size(280.dp)
      .pointerInput(enabled) {
        if (!enabled) return@pointerInput
        detectTapGestures { offset ->
          val selection = colorWheelSelection(offset, size)
          onColorChange(selection.first, selection.second)
        }
      }
      .pointerInput(enabled) {
        if (!enabled) return@pointerInput
        detectDragGestures(
          onDragStart = { offset ->
            val selection = colorWheelSelection(offset, size)
            onColorChange(selection.first, selection.second)
          },
          onDrag = { change, _ ->
            change.consume()
            val selection = colorWheelSelection(change.position, size)
            onColorChange(selection.first, selection.second)
          },
        )
      },
  ) {
    drawImage(
      image = wheelBitmap,
      dstSize = IntSize(size.width.toInt(), size.height.toInt()),
    )
    val radius = min(size.width, size.height) / 2f
    val angle = Math.toRadians(hue.toDouble())
    val selectionCenter = Offset(
      x = size.width / 2f + cos(angle).toFloat() * saturation * radius,
      y = size.height / 2f + sin(angle).toFloat() * saturation * radius,
    )
    drawCircle(Color.White, radius = 11.dp.toPx(), center = selectionCenter, style = Stroke(3.dp.toPx()))
    drawCircle(Color.Black, radius = 15.dp.toPx(), center = selectionCenter, style = Stroke(2.dp.toPx()))
  }
}

private fun colorWheelSelection(offset: Offset, size: IntSize): Pair<Float, Float> {
  val centerX = size.width / 2f
  val centerY = size.height / 2f
  val deltaX = offset.x - centerX
  val deltaY = offset.y - centerY
  val radius = min(size.width, size.height) / 2f
  val saturation = (hypot(deltaX, deltaY) / radius).coerceIn(0f, 1f)
  val hue = ((Math.toDegrees(atan2(deltaY, deltaX).toDouble()).toFloat() + 360f) % 360f)
  return hue to saturation
}

private fun createColorWheelBitmap(size: Int): Bitmap {
  val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
  val pixels = IntArray(size * size)
  val center = size / 2f
  val radius = size / 2f
  for (y in 0 until size) {
    for (x in 0 until size) {
      val deltaX = x - center
      val deltaY = y - center
      val distance = hypot(deltaX, deltaY) / radius
      val index = y * size + x
      pixels[index] = if (distance > 1f) {
        AndroidColor.TRANSPARENT
      } else {
        val hue = ((Math.toDegrees(atan2(deltaY, deltaX).toDouble()).toFloat() + 360f) % 360f)
        AndroidColor.HSVToColor(floatArrayOf(hue, distance, 1f))
      }
    }
  }
  bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
  return bitmap
}
