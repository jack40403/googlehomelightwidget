package com.example.googlehomeapisampleapp.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetActionCoverageTest {
  @Test
  fun actionOnEveryTargetDeviceCoversTarget() {
    assertTrue(actionCoversWidgetTarget(setOf("a", "b"), listOf("b", "a")))
  }

  @Test
  fun actionOnSupersetCoversTarget() {
    assertTrue(actionCoversWidgetTarget(setOf("a"), listOf("a", "b", "c")))
  }

  @Test
  fun actionOnSingleLightOfGroupDoesNotCoverTarget() {
    val group = (1..6).mapTo(linkedSetOf()) { "light-$it" }

    assertFalse(actionCoversWidgetTarget(group, listOf("light-3")))
  }

  @Test
  fun emptyTargetIsNeverCovered() {
    assertFalse(actionCoversWidgetTarget(emptySet(), listOf("a")))
  }
}
