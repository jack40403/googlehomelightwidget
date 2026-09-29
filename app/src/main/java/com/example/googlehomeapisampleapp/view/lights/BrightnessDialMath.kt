package com.example.googlehomeapisampleapp.view.lights

import kotlin.math.atan2
import kotlin.math.roundToInt

internal const val BRIGHTNESS_DIAL_START_ANGLE = 135f
internal const val BRIGHTNESS_DIAL_SWEEP_ANGLE = 270f
internal const val BRIGHTNESS_DIAL_FULL_CIRCLE = 360f

internal fun brightnessDialRawAngle(
  x: Float,
  y: Float,
  width: Float,
  height: Float,
): Float {
  if (width <= 0f || height <= 0f) return 0f

  val rawAngle = Math.toDegrees(
    atan2(y - height / 2f, x - width / 2f).toDouble(),
  ).toFloat()
  return ((rawAngle % BRIGHTNESS_DIAL_FULL_CIRCLE) + BRIGHTNESS_DIAL_FULL_CIRCLE) % BRIGHTNESS_DIAL_FULL_CIRCLE
}

internal fun brightnessDialValue(
  x: Float,
  y: Float,
  width: Float,
  height: Float,
): Float {
  if (width <= 0f || height <= 0f) return 0f

  val normalizedAngle = brightnessDialRawAngle(x, y, width, height)
  val distanceFromStart =
    ((normalizedAngle - BRIGHTNESS_DIAL_START_ANGLE) + BRIGHTNESS_DIAL_FULL_CIRCLE) % BRIGHTNESS_DIAL_FULL_CIRCLE

  if (distanceFromStart <= BRIGHTNESS_DIAL_SWEEP_ANGLE) {
    return (distanceFromStart / BRIGHTNESS_DIAL_SWEEP_ANGLE).coerceIn(0f, 1f)
  }

  val distanceToStart = BRIGHTNESS_DIAL_FULL_CIRCLE - distanceFromStart
  val distanceToEnd = distanceFromStart - BRIGHTNESS_DIAL_SWEEP_ANGLE
  return if (distanceToEnd <= distanceToStart) 1f else 0f
}

internal fun brightnessDialAngleForValue(value: Float): Float =
  BRIGHTNESS_DIAL_START_ANGLE + BRIGHTNESS_DIAL_SWEEP_ANGLE * value.coerceIn(0f, 1f)

internal fun brightnessLevelFromDialValue(value: Float): Int =
  (value.coerceIn(0f, 1f) * 254f).roundToInt().coerceIn(0, 254)
