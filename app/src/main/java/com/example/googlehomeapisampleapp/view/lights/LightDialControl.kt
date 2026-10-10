package com.example.googlehomeapisampleapp.view.lights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlin.math.atan2
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
) {
  val currentBrightness = (brightnessTrait?.currentLevel?.toInt() ?: 127)
    .coerceIn(0, 254)
    .toFloat() / 254f
  var brightness by remember(brightnessTrait?.currentLevel) { mutableFloatStateOf(currentBrightness) }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp, vertical = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text("燈光控制", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text("拖曳旋鈕調整亮度", style = MaterialTheme.typography.bodyMedium)
    BrightnessDial(
      value = brightness,
      enabled = isEnabled && brightnessTrait != null,
      onValueChange = { brightness = it },
      onValueChangeFinished = { newBrightness ->
        val trait = brightnessTrait ?: return@BrightnessDial
        scope.launch {
          try {
            trait.moveToLevelWithOnOff(
              level = (newBrightness * 254).roundToInt().toUByte(),
              transitionTime = null,
              optionsMask = LevelControlTrait.OptionsBitmap(),
              optionsOverride = LevelControlTrait.OptionsBitmap(),
            )
          } catch (error: HomeException) {
            onError("亮度調整失敗：${error.message}")
          }
        }
      },
    )

    if (colorTrait != null) {
      Text(
        text = "燈色",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
      )
      ColorPalette(
        enabled = isEnabled,
        onColorSelected = { hue, saturation ->
          scope.launch {
            try {
              colorTrait.moveToColorHsv(hue = hue, saturation = saturation, value = 1f)
            } catch (error: HomeException) {
              onError("燈色調整失敗：${error.message}")
            }
          }
        },
      )
    }
  }
}

@Composable
private fun BrightnessDial(
  value: Float,
  enabled: Boolean,
  onValueChange: (Float) -> Unit,
  onValueChangeFinished: (Float) -> Unit,
) {
  val accent = Color(0xFFFFC857)
  Box(
    modifier = Modifier
      .padding(top = 12.dp)
      .size(190.dp)
      .pointerInput(enabled) {
        if (!enabled) return@pointerInput
        detectDragGestures(
          onDragStart = { offset -> onValueChange(dialValue(offset, size.width.toFloat(), size.height.toFloat())) },
          onDrag = { change, _ ->
            change.consume()
            onValueChange(dialValue(change.position, size.width.toFloat(), size.height.toFloat()))
          },
          onDragEnd = { onValueChangeFinished(value) },
        )
      },
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.matchParentSize()) {
      val stroke = 18.dp.toPx()
      val diameter = size.minDimension - stroke
      val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
      drawArc(
        color = Color(0xFF324038),
        startAngle = 135f,
        sweepAngle = 270f,
        useCenter = false,
        topLeft = topLeft,
        size = androidx.compose.ui.geometry.Size(diameter, diameter),
        style = Stroke(stroke, cap = StrokeCap.Round),
      )
      drawArc(
        color = accent,
        startAngle = 135f,
        sweepAngle = 270f * value,
        useCenter = false,
        topLeft = topLeft,
        size = androidx.compose.ui.geometry.Size(diameter, diameter),
        style = Stroke(stroke, cap = StrokeCap.Round),
      )
      val angle = Math.toRadians((135f + 270f * value).toDouble())
      val center = Offset(size.width / 2, size.height / 2)
      val radius = diameter / 2
      drawCircle(
        color = accent,
        radius = stroke / 2,
        center = center + Offset(cos(angle).toFloat() * radius, sin(angle).toFloat() * radius),
      )
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text("${(value * 100).roundToInt()}%", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
      Text("亮度", style = MaterialTheme.typography.bodyMedium)
    }
  }
}

private fun dialValue(offset: Offset, width: Float, height: Float): Float {
  val angle = (Math.toDegrees(atan2(offset.y - height / 2, offset.x - width / 2).toDouble()) + 450.0) % 360.0
  return ((angle - 135.0) / 270.0).toFloat().coerceIn(0f, 1f)
}

@Composable
private fun ColorPalette(enabled: Boolean, onColorSelected: (Float, Float) -> Unit) {
  val colors = listOf(
    "暖白" to Pair(36f, 0.32f),
    "紅" to Pair(0f, 0.95f),
    "黃" to Pair(50f, 0.9f),
    "綠" to Pair(125f, 0.85f),
    "藍" to Pair(220f, 0.9f),
    "紫" to Pair(280f, 0.85f),
  )
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    colors.chunked(3).forEach { row ->
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        row.forEach { (name, hsv) ->
          Button(
            enabled = enabled,
            onClick = { onColorSelected(hsv.first, hsv.second) },
            modifier = Modifier.width(86.dp).height(42.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.hsv(hsv.first, hsv.second, 0.9f)),
          ) {
            Text(name, color = Color.Black)
          }
        }
      }
    }
  }
}
