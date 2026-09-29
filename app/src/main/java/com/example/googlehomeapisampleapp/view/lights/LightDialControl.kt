package com.example.googlehomeapisampleapp.view.lights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.home.HomeException
import com.google.home.google.ExtendedColorControl
import com.google.home.matter.standard.LevelControl
import com.google.home.matter.standard.LevelControlTrait
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun LightDialControl(
  brightnessTrait: LevelControl?,
  colorTrait: ExtendedColorControl?,
  isEnabled: Boolean,
  scope: CoroutineScope,
  onError: (String) -> Unit,
  onBrightnessChanged: suspend (Int) -> Unit = {},
  onColorChanged: suspend (Float, Float) -> Unit = { _, _ -> },
) {
  val currentBrightness = (brightnessTrait?.currentLevel?.toInt() ?: 0)
    .coerceIn(0, 254).toFloat() / 254f
  var brightness by remember(brightnessTrait?.currentLevel) { mutableFloatStateOf(currentBrightness) }
  val currentColorValue = (
    brightnessTrait?.currentLevel?.toInt()?.let { it / 254f }
      ?: colorTrait?.currentValue
      ?: 1f
    ).coerceIn(0f, 1f)

  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text("Brightness", style = MaterialTheme.typography.titleLarge)
    Text(
      "Drag the dial to adjust the light level",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    BrightnessDial(
      value = brightness,
      enabled = isEnabled && brightnessTrait != null,
      onValueChange = { brightness = it },
      onValueChangeFinished = { newBrightness ->
        val trait = brightnessTrait ?: return@BrightnessDial
        scope.launch {
          try {
            val level = (newBrightness * 254).roundToInt().coerceIn(0, 254)
            trait.moveToLevelWithOnOff(
              level = level.toUByte(),
              transitionTime = null,
              optionsMask = LevelControlTrait.OptionsBitmap(),
              optionsOverride = LevelControlTrait.OptionsBitmap(),
            )
            onBrightnessChanged(level)
          } catch (error: HomeException) {
            onError("Brightness update failed: ${error.message}")
          }
        }
      },
    )

    if (colorTrait != null) {
      Text(
        text = "Light color",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
      )
      ColorPalette(
        enabled = isEnabled,
        onColorSelected = { hue, saturation ->
          scope.launch {
            try {
              val normalizedHue = normalizeHue(hue)
              val normalizedSaturation = saturation.coerceIn(0f, 1f)
              colorTrait.moveToColorHsv(
                hue = normalizedHue,
                saturation = normalizedSaturation,
                value = currentColorValue,
              )
              onColorChanged(normalizedHue, normalizedSaturation)
            } catch (error: HomeException) {
              onError("Color update failed: ${error.message}")
            }
          }
        },
      )
    }
  }
}

@Composable
fun BrightnessDial(
  value: Float,
  enabled: Boolean,
  onValueChange: (Float) -> Unit,
  onValueChangeFinished: (Float) -> Unit,
  accentColor: Color? = null,
  onDragStart: (Offset, Float, Float) -> Unit = { _, _, _ -> },
  onDragMove: (Offset, Float, Float) -> Unit = { _, _, _ -> },
  onDragCancel: () -> Unit = {},
) {
  val accent = accentColor ?: MaterialTheme.colorScheme.primary
  val track = MaterialTheme.colorScheme.surfaceVariant
  val surface = MaterialTheme.colorScheme.surface
  val displayValue = value.coerceIn(0f, 1f)
  val latestValue = rememberUpdatedState(value)
  val latestOnValueChange = rememberUpdatedState(onValueChange)
  val latestOnValueChangeFinished = rememberUpdatedState(onValueChangeFinished)
  val latestOnDragStart = rememberUpdatedState(onDragStart)
  val latestOnDragMove = rememberUpdatedState(onDragMove)
  val latestOnDragCancel = rememberUpdatedState(onDragCancel)
  Box(
    modifier = Modifier.padding(top = 16.dp).size(210.dp).pointerInput(enabled) {
      if (!enabled) return@pointerInput
      var draggedValue = latestValue.value
      detectDragGestures(
        onDragStart = { offset ->
          draggedValue = brightnessDialValue(
            offset.x,
            offset.y,
            size.width.toFloat(),
            size.height.toFloat(),
          )
          latestOnDragStart.value(
            offset,
            draggedValue,
            brightnessDialRawAngle(
              offset.x,
              offset.y,
              size.width.toFloat(),
              size.height.toFloat(),
            ),
          )
          latestOnValueChange.value(draggedValue)
        },
        onDrag = { change, _ ->
          change.consume()
          draggedValue = brightnessDialValue(
            change.position.x,
            change.position.y,
            size.width.toFloat(),
            size.height.toFloat(),
          )
          latestOnDragMove.value(
            change.position,
            draggedValue,
            brightnessDialRawAngle(
              change.position.x,
              change.position.y,
              size.width.toFloat(),
              size.height.toFloat(),
            ),
          )
          latestOnValueChange.value(draggedValue)
        },
        onDragEnd = { latestOnValueChangeFinished.value(draggedValue) },
        onDragCancel = { latestOnDragCancel.value() },
      )
    },
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val stroke = 20.dp.toPx()
      val diameter = size.minDimension - stroke
      val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
      val dialSize = androidx.compose.ui.geometry.Size(diameter, diameter)
      drawCircle(
        color = surface,
        radius = diameter / 2 - stroke / 2,
        center = Offset(size.width / 2, size.height / 2),
      )
      drawArc(
        color = track,
        startAngle = BRIGHTNESS_DIAL_START_ANGLE,
        sweepAngle = BRIGHTNESS_DIAL_SWEEP_ANGLE,
        useCenter = false,
        topLeft = topLeft,
        size = dialSize,
        style = Stroke(stroke, cap = StrokeCap.Round),
      )
      drawArc(
        color = if (enabled) accent else track,
        startAngle = BRIGHTNESS_DIAL_START_ANGLE,
        sweepAngle = BRIGHTNESS_DIAL_SWEEP_ANGLE * displayValue,
        useCenter = false,
        topLeft = topLeft,
        size = dialSize,
        style = Stroke(stroke, cap = StrokeCap.Round),
      )
      val angle = Math.toRadians(brightnessDialAngleForValue(displayValue).toDouble())
      val center = Offset(size.width / 2, size.height / 2)
      val radius = diameter / 2
      drawCircle(
        color = if (enabled) accent else track,
        radius = stroke / 2,
        center = center + Offset(cos(angle).toFloat() * radius, sin(angle).toFloat() * radius),
      )
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
         "${(displayValue * 100).roundToInt()}%",
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        color = if (enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        if (enabled) "Brightness" else "Offline",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

private fun normalizeHue(hue: Float): Float =
  ((hue % BRIGHTNESS_DIAL_FULL_CIRCLE) + BRIGHTNESS_DIAL_FULL_CIRCLE) % BRIGHTNESS_DIAL_FULL_CIRCLE

@Composable
private fun ColorPalette(enabled: Boolean, onColorSelected: (Float, Float) -> Unit) {
  val colors = listOf(
    "Warm" to (36f to 0.32f),
    "Red" to (0f to 0.95f),
    "Yellow" to (50f to 0.9f),
    "Green" to (125f to 0.85f),
    "Blue" to (220f to 0.9f),
    "Violet" to (280f to 0.85f),
  )
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    colors.chunked(3).forEach { row ->
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        row.forEach { (name, hsv) ->
          val buttonColor = Color.hsv(hsv.first, hsv.second, 0.9f)
          Button(
            enabled = enabled,
            onClick = { onColorSelected(hsv.first, hsv.second) },
            modifier = Modifier.width(86.dp).height(42.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = buttonColor,
              contentColor = Color.Black,
            ),
          ) {
            Text(name)
          }
        }
      }
    }
  }
}
