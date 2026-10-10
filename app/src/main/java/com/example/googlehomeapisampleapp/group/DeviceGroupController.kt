package com.example.googlehomeapisampleapp.group

import android.util.Log
import com.google.home.HomeDevice
import com.google.home.google.ExtendedColorControl
import com.google.home.matter.standard.FanControl
import com.google.home.matter.standard.FanControlTrait
import com.google.home.matter.standard.LevelControl
import com.google.home.matter.standard.LevelControlTrait
import com.google.home.matter.standard.OnOff
import com.google.home.matter.standard.Thermostat
import com.google.home.matter.standard.ThermostatTrait
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

object DeviceGroupController {
  suspend fun setPower(devices: List<HomeDevice>, enabled: Boolean): List<String> {
    val failures = mutableListOf<String>()
    for (device in devices) {
      try {
        setDevicePower(device, enabled)
      } catch (error: CancellationException) {
        throw error
      } catch (error: Exception) {
        Log.e(TAG, "Power command failed for ${device.id.id} (${device.name})", error)
        failures += device.name
      }
    }
    return failures
  }

  suspend fun setBrightness(devices: List<HomeDevice>, brightness: Float): List<String> {
    val failures = mutableListOf<String>()
    val normalizedBrightness = brightness.coerceIn(0f, 1f)
    val level = (normalizedBrightness * 254f).roundToInt().coerceIn(0, 254).toUByte()
    for (device in devices) {
      try {
        val traits = device.types().first().flatMap { it.traits() }
        val levelControl = traits.filterIsInstance<LevelControl>().firstOrNull()
          ?: error("No level control")
        levelControl.moveToLevelWithOnOff(
          level = level,
          transitionTime = null,
          optionsMask = LevelControlTrait.OptionsBitmap(),
          optionsOverride = LevelControlTrait.OptionsBitmap(),
        )
      } catch (error: CancellationException) {
        throw error
      } catch (error: Exception) {
        Log.e(TAG, "Brightness command failed for ${device.id.id} (${device.name})", error)
        failures += device.name
      }
    }
    return failures
  }

  suspend fun setColor(devices: List<HomeDevice>, hue: Float, saturation: Float): List<String> {
    val failures = mutableListOf<String>()
    val normalizedHue = ((hue % 360f) + 360f) % 360f
    val normalizedSaturation = saturation.coerceIn(0f, 1f)
    for (device in devices) {
      try {
        val traits = device.types().first().flatMap { it.traits() }
        val colorControl = traits.filterIsInstance<ExtendedColorControl>().firstOrNull()
          ?: error("No color control")
        val levelValue = traits.filterIsInstance<LevelControl>()
          .firstOrNull()
          ?.currentLevel
          ?.toInt()
          ?.let { it / 254f }
        val value = (levelValue ?: colorControl.currentValue ?: 1f).coerceIn(0f, 1f)
        colorControl.moveToColorHsv(
          hue = normalizedHue,
          saturation = normalizedSaturation,
          value = value,
        )
      } catch (error: CancellationException) {
        throw error
      } catch (error: Exception) {
        Log.e(TAG, "Color command failed for ${device.id.id} (${device.name})", error)
        failures += device.name
      }
    }
    return failures
  }

  private suspend fun setDevicePower(device: HomeDevice, enabled: Boolean) {
    val types = device.types().first()
    val fanControl = types.asSequence().mapNotNull { it.trait(FanControl) }.firstOrNull()
    val thermostat = types.asSequence().mapNotNull { it.trait(Thermostat) }.firstOrNull()
    val onOff = types.asSequence().mapNotNull { it.trait(OnOff) }.firstOrNull()

    when {
      fanControl != null -> {
        if (fanControl.percentSetting != null) {
          fanControl.update { setPercentSetting((if (enabled) 25 else 0).toUByte()) }
        } else {
          fanControl.update {
            setFanMode(
              if (enabled) FanControlTrait.FanModeEnum.Low
              else FanControlTrait.FanModeEnum.Off,
            )
          }
        }
      }
      thermostat != null -> {
        thermostat.update {
          setSystemMode(
            if (enabled) ThermostatTrait.SystemModeEnum.Auto
            else ThermostatTrait.SystemModeEnum.Off,
          )
        }
      }
      onOff != null -> {
        if (enabled) onOff.on() else onOff.off()
      }
      else -> error("No group power control")
    }
  }

  private const val TAG = "DeviceGroupController"
}
