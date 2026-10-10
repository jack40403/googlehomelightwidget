package com.example.googlehomeapisampleapp.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.example.googlehomeapisampleapp.group.DeviceGroupController
import com.google.home.HomeClient
import java.util.UUID

class WidgetPowerToggleAction : ActionCallback {
  override suspend fun onAction(
    context: Context,
    glanceId: GlanceId,
    parameters: ActionParameters,
  ) {
    val appContext = context.applicationContext
    val operationId = "widget_power-${UUID.randomUUID()}"
    Log.i(
      TAG,
      "Power button clicked: operationId=$operationId, glanceId=$glanceId, " +
        "target=${LightWidgetStore.load(appContext).targetKind}:${LightWidgetStore.load(appContext).targetId}",
    )
    try {
      val provider = widgetHomeClientProvider(appContext)
      if (!provider.hasWidgetPermissions()) {
        persistWidgetPowerError(
          context = appContext,
          operationId = operationId,
          message = "Google Home 尚未取得控制權限，請先在 App 授權",
        )
        return
      }
      executeWidgetPowerToggle(appContext, provider.getClient())
    } catch (error: Exception) {
      Log.e(TAG, "Direct widget power action failed: operationId=$operationId", error)
      persistWidgetPowerError(
        context = appContext,
        operationId = operationId,
        message = "Google Home 控制失敗：${error.message ?: error.javaClass.simpleName}",
      )
    }
  }

  companion object {
    private const val TAG = "WidgetPowerAction"
  }
}

internal suspend fun executeWidgetPowerToggle(
  context: Context,
  homeClient: HomeClient,
) {
  val appContext = context.applicationContext
  val savedState = LightWidgetStore.load(appContext)
  val devices = homeClient.resolveWidgetDevices(appContext, savedState)
  if (devices.isEmpty()) error("Widget 控制目標已不存在或目前不可用")

  val token = checkNotNull(
    WidgetCommandCoordinator.begin(
      context = appContext,
      reason = "widget_power",
      expectedIsOn = !savedState.isOn,
    ),
  )
  Log.i(
    TAG,
    "Home power command started: operationId=${token.operationId}, " +
      "deviceIds=${devices.map { it.id.id }}, targetIsOn=${token.expectedIsOn}",
  )
  try {
    val failures = DeviceGroupController.setPower(devices, !savedState.isOn)
    Log.i(
      TAG,
      "Home power command completed: operationId=${token.operationId}, failures=${failures.size}",
    )
    if (failures.size == devices.size) {
      WidgetCommandCoordinator.fail(
        appContext,
        token,
        "${failures.size} 個燈具控制失敗",
      )
    } else {
      WidgetCommandCoordinator.complete(
        context = appContext,
        homeClient = homeClient,
        token = token,
        partialFailure = failures.takeIf { it.isNotEmpty() }?.let {
          "${it.size} 個燈具控制失敗"
        },
      )
    }
  } catch (error: Exception) {
    WidgetCommandCoordinator.fail(
      context = appContext,
      token = token,
      errorMessage = error.message ?: error.javaClass.simpleName,
    )
    throw error
  }
}

internal suspend fun persistWidgetPowerError(
  context: Context,
  operationId: String,
  message: String,
) {
  val appContext = context.applicationContext
  val currentState = LightWidgetStore.load(appContext)
  LightWidgetStore.save(
    appContext,
    currentState.clearPendingWidgetCommand().copy(
      lastUpdatedAt = System.currentTimeMillis(),
      syncStatus = WidgetSyncStatus.ERROR,
      lastSyncError = message,
      source = WidgetStateSource.COMMAND,
      operationId = operationId,
    ),
  )
  updateLightDialWidgets(appContext, reason = "widget_power_error")
}

private const val TAG = "WidgetPowerAction"
