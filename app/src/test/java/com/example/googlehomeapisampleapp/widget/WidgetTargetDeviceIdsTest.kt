package com.example.googlehomeapisampleapp.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetTargetDeviceIdsTest {
  @Test
  fun deviceTargetKeepsConfiguredIdsWhenReadResolvesFewerDevices() {
    val state = state(WidgetTargetKind.ROOM, deviceIds = setOf("a", "b", "hidden"))

    assertEquals(setOf("a", "b", "hidden"), configuredWidgetDeviceIdsAfterRead(state, groupMemberIds = null))
  }

  @Test
  fun groupTargetRefreshesMembershipFromGoogleHome() {
    val state = state(WidgetTargetKind.GROUP, deviceIds = setOf("a", "b"))

    assertEquals(setOf("a", "c"), configuredWidgetDeviceIdsAfterRead(state, groupMemberIds = setOf("a", "c")))
  }

  @Test
  fun groupTargetKeepsConfiguredIdsWhenGroupWasNotFound() {
    val state = state(WidgetTargetKind.GROUP, deviceIds = setOf("a", "b"))

    assertEquals(setOf("a", "b"), configuredWidgetDeviceIdsAfterRead(state, groupMemberIds = null))
    assertEquals(setOf("a", "b"), configuredWidgetDeviceIdsAfterRead(state, groupMemberIds = emptySet()))
  }

  @Test
  fun nonGroupTargetIgnoresGroupMembership() {
    val state = state(WidgetTargetKind.FAVORITES, deviceIds = setOf("a"))

    assertEquals(setOf("a"), configuredWidgetDeviceIdsAfterRead(state, groupMemberIds = setOf("x")))
  }

  @Test
  fun legacySingleDeviceStateIsPreserved() {
    val state = LightWidgetState(deviceId = "legacy", displayName = "Legacy", isOn = false, brightnessLevel = null)

    assertEquals(setOf("legacy"), configuredWidgetDeviceIdsAfterRead(state, groupMemberIds = null))
  }

  private fun state(kind: WidgetTargetKind, deviceIds: Set<String>): LightWidgetState = LightWidgetState(
    targetKind = kind,
    targetId = "target",
    deviceId = deviceIds.firstOrNull(),
    deviceIds = deviceIds,
    displayName = "Target",
    isOn = true,
    brightnessLevel = 127,
  )
}
