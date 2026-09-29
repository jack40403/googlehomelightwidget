package com.example.googlehomeapisampleapp.widget

import com.example.googlehomeapisampleapp.viewmodel.devices.DeviceViewModel
import com.google.home.google.ExtendedColorControl
import com.google.home.matter.standard.LevelControl
import com.google.home.matter.standard.OnOff

enum class WidgetTargetKind(val value: String) {
  DEVICE("device"),
  ROOM("room"),
  GROUP("group"),
  FAVORITES("favorites");

  companion object {
    fun from(value: String?): WidgetTargetKind = entries.firstOrNull { it.value == value } ?: DEVICE
  }
}

data class WidgetTargetOption(
  val kind: WidgetTargetKind,
  val id: String,
  val name: String,
  val deviceIds: Set<String>,
)

data class GoogleHomeGroupOption(
  val id: String,
  val name: String,
  val deviceIds: Set<String>,
)

data class WidgetStateSnapshot(
  val isOn: Boolean,
  val brightnessLevel: Int?,
  val hue: Float?,
  val saturation: Float?,
  val colorName: String?,
)

fun friendlyColorName(hue: Float?, saturation: Float?, apiName: String? = null): String? {
  if (!apiName.isNullOrBlank()) return apiName
  val normalizedHue = hue?.let { ((it % 360f) + 360f) % 360f } ?: return null
  if ((saturation ?: 1f) < 0.2f) return "暖白"
  return when {
    normalizedHue < 15f || normalizedHue >= 345f -> "紅色"
    normalizedHue < 40f -> "暖白"
    normalizedHue < 75f -> "黃色"
    normalizedHue < 170f -> "綠色"
    normalizedHue < 255f -> "藍色"
    normalizedHue < 315f -> "紫色"
    else -> "紅色"
  }
}

fun widgetSnapshotFromViewModels(devices: Collection<DeviceViewModel>): WidgetStateSnapshot {
  val traits = devices.flatMap { it.traits.value }
  val onOffTraits = traits.filterIsInstance<OnOff>()
  val brightness = traits.filterIsInstance<LevelControl>()
    .mapNotNull { it.currentLevel?.toInt() }
    .average()
    .takeIf { !it.isNaN() }
    ?.toInt()
    ?.coerceIn(0, 254)
  val color = traits.filterIsInstance<ExtendedColorControl>().firstOrNull()
  return WidgetStateSnapshot(
    isOn = onOffTraits.any { it.onOff == true },
    brightnessLevel = brightness,
    hue = color?.currentHue,
    saturation = color?.currentSaturation,
    colorName = friendlyColorName(color?.currentHue, color?.currentSaturation, color?.currentName),
  )
}
