package com.example.googlehomeapisampleapp.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPendingCommandTest {
  @Test
  fun freshPendingCommandIsReported() {
    val pending = pendingState(updatedAt = 1_000L)

    assertTrue(pending.hasPendingWidgetCommand(nowMillis = 1_000L + PENDING_COMMAND_MAX_AGE_MILLIS - 1L))
  }

  @Test
  fun pendingCommandExpiresAfterMaxAge() {
    val pending = pendingState(updatedAt = 1_000L)

    assertFalse(pending.hasPendingWidgetCommand(nowMillis = 1_000L + PENDING_COMMAND_MAX_AGE_MILLIS))
  }

  @Test
  fun expiredPendingCommandAcceptsAnyObservation() {
    val pending = pendingState(updatedAt = 1_000L)
    val contradicting = pending.copy(isOn = false)

    assertFalse(isWidgetObservationAcceptable(pending, contradicting, nowMillis = 2_000L))
    assertTrue(
      isWidgetObservationAcceptable(
        pending,
        contradicting,
        nowMillis = 1_000L + PENDING_COMMAND_MAX_AGE_MILLIS,
      ),
    )
  }

  @Test
  fun clearedPendingCommandIsNotReportedEvenWithOperationId() {
    val cleared = pendingState(updatedAt = 1_000L).clearPendingWidgetCommand()

    assertFalse(cleared.hasPendingWidgetCommand(nowMillis = 1_000L))
  }

  private fun pendingState(updatedAt: Long): LightWidgetState = createOptimisticWidgetState(
    previousState = LightWidgetState(
      deviceId = "device-1",
      displayName = "Test light",
      isOn = false,
      brightnessLevel = 127,
      lastUpdatedAt = 0L,
      stateVersion = 3L,
    ),
    operationId = "operation-1",
    expectedIsOn = true,
    nowMillis = updatedAt,
  )
}
