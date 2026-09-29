package com.example.googlehomeapisampleapp.widget

import android.content.Context
import android.util.Log
import com.google.home.HomeClient
import java.util.UUID

data class WidgetCommandToken(
  val operationId: String,
  val previousState: LightWidgetState,
  val optimisticState: LightWidgetState,
  val expectedIsOn: Boolean?,
  val expectedBrightnessLevel: Int?,
  val expectedHue: Float?,
  val expectedSaturation: Float?,
)

object WidgetCommandCoordinator {
  suspend fun begin(
    context: Context,
    reason: String,
    actionDeviceIds: Collection<String>? = null,
    operationIdOverride: String? = null,
    expectedIsOn: Boolean? = null,
    expectedBrightnessLevel: Int? = null,
    expectedHue: Float? = null,
    expectedSaturation: Float? = null,
  ): WidgetCommandToken? {
    val appContext = context.applicationContext
    val previousState = LightWidgetStore.load(appContext)
    val targetDeviceIds = previousState.deviceIds.ifEmpty { setOfNotNull(previousState.deviceId) }
    if (actionDeviceIds != null && actionDeviceIds.none { it in targetDeviceIds }) {
      Log.i(
        TAG,
        "Action ignored because it does not target the widget: reason=$reason, " +
          "targetDeviceIds=$targetDeviceIds, actionDeviceIds=$actionDeviceIds",
      )
      return null
    }

    val operationId = operationIdOverride ?: "$reason-${UUID.randomUUID()}"
    val optimisticState = LightWidgetStore.save(
      appContext,
      createOptimisticWidgetState(
        previousState = previousState,
        operationId = operationId,
        expectedIsOn = expectedIsOn,
        expectedBrightnessLevel = expectedBrightnessLevel,
        expectedHue = expectedHue,
        expectedSaturation = expectedSaturation,
      ),
    )
    Log.i(
      TAG,
      "Action received: operationId=$operationId, reason=$reason, " +
        "target=${previousState.targetKind}:${previousState.targetId}, deviceIds=$targetDeviceIds, " +
        "optimisticOn=${optimisticState.isOn}, optimisticBrightness=${optimisticState.brightnessLevel}",
    )
    updateLightDialWidgets(appContext, reason = "${reason}_optimistic")
    return WidgetCommandToken(
      operationId = operationId,
      previousState = previousState,
      optimisticState = optimisticState,
      expectedIsOn = expectedIsOn,
      expectedBrightnessLevel = expectedBrightnessLevel,
      expectedHue = expectedHue,
      expectedSaturation = expectedSaturation,
    )
  }

  suspend fun complete(
    context: Context,
    homeClient: HomeClient,
    token: WidgetCommandToken,
    partialFailure: String? = null,
  ): LightWidgetState? {
    val appContext = context.applicationContext
    Log.i(TAG, "Home readback started: operationId=${token.operationId}")
    val readback = runCatching {
      if (partialFailure == null) {
        homeClient.readWidgetStateAfterCommand(
          context = appContext,
          state = token.optimisticState,
          expectedIsOn = token.expectedIsOn,
          expectedBrightnessLevel = token.expectedBrightnessLevel,
          expectedHue = token.expectedHue,
          expectedSaturation = token.expectedSaturation,
        )
      } else {
        homeClient.readWidgetState(
          context = appContext,
          state = token.optimisticState,
          forceRefresh = true,
        )
      }
    }.onFailure { error ->
      Log.e(TAG, "Home readback failed: operationId=${token.operationId}", error)
    }.getOrNull()

    if (readback != null) {
      val persistedState = LightWidgetStore.saveObservation(
        context = appContext,
        observedState = readback.copy(lastUpdatedAt = System.currentTimeMillis()),
        source = WidgetStateSource.COMMAND,
        operationId = token.operationId,
        authoritative = partialFailure != null,
        syncError = partialFailure ?: readback.lastSyncError,
      )
      if (persistedState != null) {
        Log.i(
          TAG,
          "Home readback completed: operationId=${token.operationId}, " +
            "isOn=${persistedState.isOn}, brightness=${persistedState.brightnessLevel}",
        )
        updateLightDialWidgets(appContext, reason = "command_readback")
      }
      return persistedState
    }

    val currentState = LightWidgetStore.load(appContext)
    val pendingState = LightWidgetStore.saveIfOperationCurrent(
      appContext,
      token.operationId,
      currentState.copy(
        syncStatus = WidgetSyncStatus.ERROR,
        lastSyncError = partialFailure ?: "Google Home 已收到指令，但尚未回傳最新狀態",
        source = WidgetStateSource.COMMAND,
      ),
    )
    if (pendingState != null) {
      updateLightDialWidgets(appContext, reason = "command_readback_pending")
    }
    return pendingState
  }

  suspend fun fail(
    context: Context,
    token: WidgetCommandToken,
    errorMessage: String,
  ): LightWidgetState? {
    val appContext = context.applicationContext
    Log.e(TAG, "Home command failed: operationId=${token.operationId}, error=$errorMessage")
    val failedState = LightWidgetStore.saveIfOperationCurrent(
      appContext,
      token.operationId,
      token.previousState.copy(
        lastUpdatedAt = System.currentTimeMillis(),
        syncStatus = WidgetSyncStatus.ERROR,
        lastSyncError = errorMessage,
        source = WidgetStateSource.COMMAND,
        operationId = token.operationId,
      ).clearPendingWidgetCommand(),
    )
    if (failedState != null) {
      updateLightDialWidgets(appContext, reason = "command_failed")
    }
    return failedState
  }

  suspend fun manualRefresh(
    context: Context,
    homeClient: HomeClient,
  ): LightWidgetState {
    val appContext = context.applicationContext
    val operationId = "manual-refresh-${UUID.randomUUID()}"
    val savedState = LightWidgetStore.load(appContext)
    val refreshingState = LightWidgetStore.save(
      appContext,
      savedState.clearPendingWidgetCommand().copy(
        syncStatus = WidgetSyncStatus.SYNCING,
        lastSyncError = null,
        source = WidgetStateSource.MANUAL_REFRESH,
        operationId = operationId,
      ),
    )
    Log.i(
      TAG,
      "Manual refresh clicked: operationId=$operationId, " +
        "target=${savedState.targetKind}:${savedState.targetId}, deviceIds=${savedState.deviceIds}",
    )
    updateLightDialWidgets(appContext, reason = "manual_refresh_started")

    return try {
      Log.i(TAG, "Home read started: operationId=$operationId")
      val observedState = homeClient.readWidgetState(
        context = appContext,
        state = refreshingState,
        forceRefresh = true,
      )
      val persistedState = checkNotNull(
        LightWidgetStore.saveObservation(
          context = appContext,
          observedState = observedState.copy(lastUpdatedAt = System.currentTimeMillis()),
          source = WidgetStateSource.MANUAL_REFRESH,
          operationId = operationId,
          authoritative = true,
          syncError = observedState.lastSyncError,
        ),
      ) { "Manual refresh was superseded by a newer widget action" }
      Log.i(
        TAG,
        "Home read completed and state persisted: operationId=$operationId, " +
          "stateVersion=${persistedState.stateVersion}",
      )
      updateLightDialWidgets(appContext, reason = "manual_refresh_completed")
      persistedState
    } catch (error: Exception) {
      val currentState = LightWidgetStore.load(appContext)
      LightWidgetStore.saveIfOperationCurrent(
        appContext,
        operationId,
        currentState.clearPendingWidgetCommand().copy(
          syncStatus = WidgetSyncStatus.ERROR,
          lastSyncError = error.message ?: error.javaClass.simpleName,
          source = WidgetStateSource.MANUAL_REFRESH,
        ),
      )?.let {
        updateLightDialWidgets(appContext, reason = "manual_refresh_failed")
      }
      Log.e(TAG, "Manual refresh failed: operationId=$operationId", error)
      throw error
    }
  }

  private const val TAG = "WidgetCommand"
}

internal fun createOptimisticWidgetState(
  previousState: LightWidgetState,
  operationId: String,
  expectedIsOn: Boolean? = null,
  expectedBrightnessLevel: Int? = null,
  expectedHue: Float? = null,
  expectedSaturation: Float? = null,
  nowMillis: Long = System.currentTimeMillis(),
): LightWidgetState = previousState.copy(
  isOn = expectedIsOn ?: previousState.isOn,
  brightnessLevel = expectedBrightnessLevel?.coerceIn(0, 254) ?: previousState.brightnessLevel,
  colorHue = expectedHue ?: previousState.colorHue,
  colorSaturation = expectedSaturation ?: previousState.colorSaturation,
  colorName = if (expectedHue != null) "自訂色" else previousState.colorName,
  lastUpdatedAt = nowMillis,
  syncStatus = WidgetSyncStatus.SYNCING,
  lastSyncError = null,
  source = WidgetStateSource.COMMAND,
  operationId = operationId,
  pendingIsOn = expectedIsOn,
  pendingBrightnessLevel = expectedBrightnessLevel?.coerceIn(0, 254),
  pendingColorHue = expectedHue,
  pendingColorSaturation = expectedSaturation?.coerceIn(0f, 1f),
)
