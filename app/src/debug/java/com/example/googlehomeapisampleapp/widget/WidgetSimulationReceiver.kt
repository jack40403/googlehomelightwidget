package com.example.googlehomeapisampleapp.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WidgetSimulationReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val pendingResult = goAsync()
    val appContext = context.applicationContext
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
      val operationId = intent.getStringExtra(EXTRA_OPERATION_ID) ?: UUID.randomUUID().toString()
      try {
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_SNAPSHOT
        val brightnessPercent = intent.getIntExtra(EXTRA_BRIGHTNESS_PERCENT, 50).coerceIn(0, 100)
        val brightnessLevel = (brightnessPercent / 100f * 254f).roundToInt().coerceIn(0, 254)
        val hue = intent.getFloatExtra(EXTRA_HUE, 210f)
        val saturation = intent.getFloatExtra(EXTRA_SATURATION, 0.85f).coerceIn(0f, 1f)
        val deviceCount = intent.getIntExtra(EXTRA_DEVICE_COUNT, 6).coerceAtLeast(1)
        val deviceIds = (1..deviceCount).mapTo(linkedSetOf()) { "simulated-light-$it" }
        val currentState = LightWidgetStore.load(appContext)
        val isOn = if (mode == MODE_POWER) !currentState.isOn else {
          intent.getBooleanExtra(EXTRA_IS_ON, brightnessPercent > 0)
        }
        Log.i(
          TAG,
          "[$operationId] simulation received: mode=$mode, beforeVersion=${currentState.stateVersion}, " +
            "on=$isOn, brightnessPercent=$brightnessPercent, hue=$hue, devices=$deviceCount",
        )
        val baseState = currentState.copy(
            targetKind = WidgetTargetKind.GROUP,
            targetId = SIMULATED_TARGET_ID,
            deviceId = deviceIds.first(),
            deviceIds = deviceIds,
            displayName = "模擬燈群",
        )
        val persistedState = when (mode) {
          MODE_POWER -> {
            val powerState = createOptimisticWidgetState(
              previousState = baseState,
              operationId = operationId,
              expectedIsOn = isOn,
              expectedBrightnessLevel = baseState.brightnessLevel ?: brightnessLevel,
              expectedHue = baseState.colorHue ?: hue,
              expectedSaturation = baseState.colorSaturation ?: saturation,
            )
            Log.i(TAG, "[$operationId] direct power action simulated without Activity navigation")
            LightWidgetStore.save(
              appContext,
              powerState.copy(
                syncStatus = WidgetSyncStatus.CONNECTED,
                source = WidgetStateSource.DEBUG,
                pendingIsOn = null,
                pendingBrightnessLevel = null,
                pendingColorHue = null,
                pendingColorSaturation = null,
              ),
            )
          }
          MODE_COMMAND -> LightWidgetStore.save(
            appContext,
            createOptimisticWidgetState(
              previousState = baseState,
              operationId = operationId,
              expectedIsOn = isOn,
              expectedBrightnessLevel = brightnessLevel,
              expectedHue = hue,
              expectedSaturation = saturation,
            ),
          )
          MODE_OBSERVATION -> LightWidgetStore.saveObservation(
            context = appContext,
            observedState = baseState.copy(
              isOn = isOn,
              brightnessLevel = brightnessLevel,
              colorHue = hue,
              colorSaturation = saturation,
              colorName = "模擬色",
              lastUpdatedAt = System.currentTimeMillis(),
            ),
            source = WidgetStateSource.HOME_FLOW,
            expectedStateVersion = currentState.stateVersion,
          )
          else -> LightWidgetStore.save(
            appContext,
            baseState.copy(
            isOn = isOn,
            brightnessLevel = brightnessLevel,
            colorHue = hue,
            colorSaturation = saturation,
            colorName = "模擬色",
            lastUpdatedAt = System.currentTimeMillis(),
            syncStatus = WidgetSyncStatus.CONNECTED,
            lastSyncError = null,
            source = WidgetStateSource.DEBUG,
            operationId = operationId,
            pendingIsOn = null,
            pendingBrightnessLevel = null,
            pendingColorHue = null,
            pendingColorSaturation = null,
            ),
          )
        }
        if (persistedState == null) {
          Log.i(TAG, "[$operationId] simulation observation rejected by stale-state guard")
          return@launch
        }
        Log.i(TAG, "[$operationId] simulation persisted: stateVersion=${persistedState.stateVersion}")
        updateLightDialWidgets(appContext, reason = "debug_simulation:$mode:$operationId")
        Log.i(TAG, "[$operationId] simulation widget update completed")
      } catch (error: Exception) {
        Log.e(TAG, "[$operationId] simulation failed", error)
      } finally {
        pendingResult.finish()
      }
    }
  }

  companion object {
    const val ACTION_SIMULATE_WIDGET = "com.example.googlehomeapisampleapp.DEBUG_SIMULATE_WIDGET"
    const val EXTRA_OPERATION_ID = "operationId"
    const val EXTRA_MODE = "mode"
    const val EXTRA_IS_ON = "isOn"
    const val EXTRA_BRIGHTNESS_PERCENT = "brightnessPercent"
    const val EXTRA_HUE = "hue"
    const val EXTRA_SATURATION = "saturation"
    const val EXTRA_DEVICE_COUNT = "deviceCount"

    const val MODE_SNAPSHOT = "snapshot"
    const val MODE_COMMAND = "command"
    const val MODE_POWER = "power"
    const val MODE_OBSERVATION = "observation"

    private const val SIMULATED_TARGET_ID = "debug-simulated-group"
    private const val TAG = "WidgetSimulation"
  }
}
