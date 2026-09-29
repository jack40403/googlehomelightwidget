package com.example.googlehomeapisampleapp.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.graphics.toArgb
import android.graphics.Color as AndroidColor

class WidgetStatePublicationPolicyTest {
  @Test
  fun sameVersionCanPublishReadback() {
    val expected = state(version = 4L, updatedAt = 100L)
    val current = expected.copy(isOn = true)

    assertTrue(isWidgetStateCurrent(expected, current))
  }

  @Test
  fun newerUserActionRejectsOlderReadback() {
    val expected = state(version = 4L, updatedAt = 100L)
    val current = state(version = 5L, updatedAt = 200L)

    assertFalse(isWidgetStateCurrent(expected, current))
  }

  @Test
  fun legacyTimestampStillProtectsStateBeforeMigration() {
    val expected = state(version = 0L, updatedAt = 100L)
    val current = state(version = 0L, updatedAt = 200L)

    assertFalse(isWidgetStateCurrent(expected, current))
  }

  @Test
  fun optimisticCommandIsPublishedWithPendingExpectation() {
    val optimistic = createOptimisticWidgetState(
      previousState = state(version = 4L, updatedAt = 100L),
      operationId = "operation-1",
      expectedIsOn = true,
      expectedBrightnessLevel = 210,
      nowMillis = 200L,
    )

    assertTrue(optimistic.isOn)
    assertEquals(210, optimistic.brightnessLevel)
    assertEquals(WidgetSyncStatus.SYNCING, optimistic.syncStatus)
    assertEquals(WidgetStateSource.COMMAND, optimistic.source)
    assertEquals("operation-1", optimistic.operationId)
    assertEquals(true, optimistic.pendingIsOn)
    assertEquals(210, optimistic.pendingBrightnessLevel)
  }

  @Test
  fun staleHomeFlowCannotOverwritePendingCommand() {
    val current = createOptimisticWidgetState(
      previousState = state(version = 4L, updatedAt = 100L),
      operationId = "operation-1",
      expectedIsOn = true,
      expectedBrightnessLevel = 210,
      nowMillis = 200L,
    )
    val staleObservation = current.copy(isOn = false, brightnessLevel = 127)

    assertFalse(isWidgetObservationAcceptable(current, staleObservation, nowMillis = 300L))
  }

  @Test
  fun matchingHomeFlowConfirmsPendingCommand() {
    val current = createOptimisticWidgetState(
      previousState = state(version = 4L, updatedAt = 100L),
      operationId = "operation-1",
      expectedIsOn = true,
      expectedBrightnessLevel = 210,
      nowMillis = 200L,
    )
    val matchingObservation = current.copy(isOn = true, brightnessLevel = 208)

    assertTrue(isWidgetObservationAcceptable(current, matchingObservation, nowMillis = 300L))
  }

  @Test
  fun manualRefreshCanAuthoritativelyReplacePendingState() {
    val current = createOptimisticWidgetState(
      previousState = state(version = 4L, updatedAt = 100L),
      operationId = "operation-1",
      expectedIsOn = true,
      nowMillis = 200L,
    )
    val observed = current.copy(isOn = false)

    assertTrue(
      isWidgetObservationAcceptable(
        currentState = current,
        observedState = observed,
        authoritative = true,
        nowMillis = 300L,
      ),
    )
  }

  @Test
  fun missingTraitsRetainLastKnownValues() {
    val current = state(version = 4L, updatedAt = 100L).copy(
      brightnessLevel = 210,
      colorHue = 220f,
      colorSaturation = 0.8f,
      colorName = "Blue",
    )
    val observed = current.copy(
      brightnessLevel = null,
      colorHue = null,
      colorSaturation = null,
      colorName = null,
    )

    val merged = retainKnownWidgetValues(current, observed)

    assertEquals(210, merged.brightnessLevel)
    assertEquals(220f, merged.colorHue)
    assertEquals(0.8f, merged.colorSaturation)
    assertEquals("Blue", merged.colorName)
  }

  @Test
  fun everyGlanceIdIsUpdated() = runBlocking {
    val updatedIds = mutableListOf<Int>()

    updateEveryWidgetId(listOf(11, 12, 13)) { updatedIds += it }

    assertEquals(setOf(11, 12, 13), updatedIds.toSet())
    assertEquals(3, updatedIds.size)
  }

  @Test
  fun clearingPendingCommandRemovesEveryExpectation() {
    val current = createOptimisticWidgetState(
      previousState = state(version = 4L, updatedAt = 100L),
      operationId = "operation-1",
      expectedIsOn = true,
      expectedBrightnessLevel = 210,
      expectedHue = 220f,
      expectedSaturation = 0.8f,
      nowMillis = 200L,
    )

    val cleared = current.clearPendingWidgetCommand()

    assertNull(cleared.pendingIsOn)
    assertNull(cleared.pendingBrightnessLevel)
    assertNull(cleared.pendingColorHue)
    assertNull(cleared.pendingColorSaturation)
  }

  @Test
  fun widgetColorUsesCurrentHueWhenLightIsOnAndDimsWhenOff() {
    val redState = state(version = 4L, updatedAt = 100L).copy(
      isOn = true,
      brightnessLevel = 254,
      colorHue = 0f,
      colorSaturation = 1f,
    )

    val onColor = widgetDisplayColor(redState).toArgb()
    val offColor = widgetDisplayColor(redState.copy(isOn = false)).toArgb()

    assertEquals(AndroidColor.RED, onColor)
    assertEquals(0xFF53656B.toInt(), offColor)
  }

  @Test
  fun directPowerActionFlipsOnlyPowerState() {
    val current = state(version = 4L, updatedAt = 100L).copy(
      isOn = false,
      brightnessLevel = 200,
      colorHue = 220f,
      colorSaturation = 0.8f,
    )

    val next = createOptimisticWidgetState(
      previousState = current,
      operationId = "power-operation",
      expectedIsOn = !current.isOn,
      expectedBrightnessLevel = current.brightnessLevel,
      expectedHue = current.colorHue,
      expectedSaturation = current.colorSaturation,
      nowMillis = 200L,
    )

    assertTrue(next.isOn)
    assertEquals(current.brightnessLevel, next.brightnessLevel)
    assertEquals(current.colorHue, next.colorHue)
    assertEquals(current.colorSaturation, next.colorSaturation)
  }

  private fun state(version: Long, updatedAt: Long): LightWidgetState = LightWidgetState(
    deviceId = "device-1",
    displayName = "Test light",
    isOn = false,
    brightnessLevel = 127,
    lastUpdatedAt = updatedAt,
    stateVersion = version,
  )
}
