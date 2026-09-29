package com.example.googlehomeapisampleapp.view.lights

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class BrightnessDialMathTest {
  @Test
  fun mapsDialEndpointsAndIntermediateValues() {
    assertEquals(0f, valueAtAngle(BRIGHTNESS_DIAL_START_ANGLE), 0.001f)
    assertEquals(0.5f, valueAtAngle(270f), 0.001f)
    assertEquals(0.83f, valueAtAngle(BRIGHTNESS_DIAL_START_ANGLE + 270f * 0.83f), 0.001f)
    assertEquals(1f, valueAtAngle(BRIGHTNESS_DIAL_START_ANGLE + BRIGHTNESS_DIAL_SWEEP_ANGLE), 0.001f)
  }

  @Test
  fun clampsOutsideTheBottomOpeningToTheNearestEndpoint() {
    assertEquals(1f, valueAtAngle(50f), 0.001f)
    assertEquals(0f, valueAtAngle(120f), 0.001f)
  }

  @Test
  fun preservesTheWrapAroundAtTheEndOfTheArc() {
    val endValue = valueAtAngle(45f)

    assertEquals(1f, endValue, 0.001f)
    assertEquals(1f, valueAtAngle(50f), 0.001f)
  }

  @Test
  fun convertsDialValueToGoogleHomeLevel() {
    assertEquals(0, brightnessLevelFromDialValue(0f))
    assertEquals(127, brightnessLevelFromDialValue(0.5f))
    assertEquals(211, brightnessLevelFromDialValue(0.83f))
    assertEquals(254, brightnessLevelFromDialValue(1f))
    assertEquals(0, brightnessLevelFromDialValue(-1f))
    assertEquals(254, brightnessLevelFromDialValue(2f))
  }

  private fun valueAtAngle(angle: Float): Float {
    val radius = 100f
    val center = 100f
    val radians = Math.toRadians(angle.toDouble())
    return brightnessDialValue(
      x = center + cos(radians).toFloat() * radius,
      y = center + sin(radians).toFloat() * radius,
      width = 200f,
      height = 200f,
    )
  }
}
