package com.example.googlehomeapisampleapp.widget

import android.content.Context
import android.util.Log
import com.google.home.HomeClient
import com.google.home.HomeDevice
import com.google.home.annotation.HomeExperimentalApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(HomeExperimentalApi::class, ExperimentalCoroutinesApi::class)
class WidgetStateMonitor(
  private val context: Context,
  private val homeClient: HomeClient,
  private val scope: CoroutineScope,
) {
  private var monitorJob: Job? = null

  fun start() {
    if (monitorJob?.isActive == true) return
    monitorJob = scope.launch(Dispatchers.IO) { monitor() }
  }

  fun restart() {
    monitorJob?.cancel()
    monitorJob = null
    start()
  }

  fun stop() {
    monitorJob?.cancel()
    monitorJob = null
  }

  private suspend fun monitor() {
    while (currentCoroutineContext().isActive) {
      try {
        val state = LightWidgetStore.load(context)
        if (state.deviceIds.isEmpty() && state.deviceId == null) {
          delay(RETRY_DELAY_MILLIS)
          continue
        }

        val devices = homeClient.resolveWidgetDevices(context, state)
        if (devices.isEmpty()) {
          Log.w(TAG, "Widget target resolved to zero devices")
          delay(RETRY_DELAY_MILLIS)
          continue
        }

        Log.i(TAG, "Monitoring ${devices.size} Google Home device(s) for widget updates")
        merge(*devices.map(::deviceStateFlow).toTypedArray()).collect {
          syncWidgetState()
        }
      } catch (error: CancellationException) {
        throw error
      } catch (error: Exception) {
        Log.e(TAG, "Google Home widget monitor failed; retrying", error)
        delay(RETRY_DELAY_MILLIS)
      }
    }
  }

  private fun deviceStateFlow(device: HomeDevice): Flow<Unit> =
    device.types().flatMapLatest { types ->
      val typeFlows = types.map { type ->
        device.type(type.factory).map { Unit }
      }
      if (typeFlows.isEmpty()) flowOf(Unit) else merge(*typeFlows.toTypedArray())
    }

  private suspend fun syncWidgetState() {
    val savedState = LightWidgetStore.load(context)
    Log.i(
      TAG,
      "Home Flow snapshot received: operationId=${savedState.operationId}, " +
        "stateVersion=${savedState.stateVersion}",
    )
    val refreshedState = homeClient.readWidgetState(context, savedState)
      .copy(
        lastUpdatedAt = System.currentTimeMillis(),
      )
    val persistedState = LightWidgetStore.saveObservation(
      context = context,
      observedState = refreshedState,
      source = WidgetStateSource.HOME_FLOW,
      expectedStateVersion = savedState.stateVersion,
    )
    if (persistedState == null) {
      Log.i(
        TAG,
        "Skipped stale Home Flow result: operationId=${savedState.operationId}, " +
          "observedOn=${refreshedState.isOn}, observedBrightness=${refreshedState.brightnessLevel}",
      )
      return
    }
    updateLightDialWidgets(context.applicationContext, reason = "home_flow")
  }

  companion object {
    private const val TAG = "WidgetStateMonitor"
    private const val RETRY_DELAY_MILLIS = 15_000L
  }
}
