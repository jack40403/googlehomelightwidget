package com.example.googlehomeapisampleapp.widget

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.home.PermissionsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class WidgetStateSyncWorker(
  appContext: Context,
  workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
  override suspend fun doWork(): Result {
    val savedState = LightWidgetStore.load(applicationContext)
    if (savedState.deviceIds.isEmpty() && savedState.deviceId == null) {
      Log.i(TAG, "Skipping background sync because no widget target is configured")
      return Result.success()
    }
    if (savedState.hasPendingWidgetCommand()) {
      Log.i(
        TAG,
        "Skipping background sync while command is pending: operationId=${savedState.operationId}",
      )
      return Result.success()
    }

    return try {
      val client = widgetHomeClientProvider(applicationContext).getClient()
      val permissionState = withTimeoutOrNull(PERMISSION_TIMEOUT_MILLIS) {
        client.hasPermissions().first {
          it != PermissionsState.PERMISSIONS_STATE_UNINITIALIZED
        }
      } ?: return retry("Timed out while reading Home permissions")

      if (permissionState != PermissionsState.GRANTED) {
        Log.w(TAG, "Skipping background sync because Home permissions are $permissionState")
        publishStatusIfCurrent(
          expectedState = savedState,
          savedState.copy(
            syncStatus = WidgetSyncStatus.UNKNOWN,
            lastSyncError = "Google Home 尚未授權",
            source = WidgetStateSource.WORKER,
          ),
        )
        return Result.success()
      }

      val refreshedState = withTimeoutOrNull(READ_TIMEOUT_MILLIS) {
        client.readWidgetState(applicationContext, savedState)
      } ?: return retry("Timed out while reading Google Home device state")

      val persistedState = LightWidgetStore.saveObservation(
        context = applicationContext,
        observedState = refreshedState.copy(lastUpdatedAt = System.currentTimeMillis()),
        source = WidgetStateSource.WORKER,
        expectedStateVersion = savedState.stateVersion,
      )
      if (persistedState != null) {
        updateLightDialWidgets(applicationContext, reason = "worker")
      }
      Log.i(
        TAG,
        "Background sync completed: isOn=${refreshedState.isOn}, " +
          "brightness=${refreshedState.brightnessLevel}, devices=${refreshedState.deviceIds.size}",
      )
      Result.success()
    } catch (error: CancellationException) {
      throw error
    } catch (error: Exception) {
      Log.e(TAG, "Background Google Home widget sync failed", error)
      publishStatusIfCurrent(
        expectedState = savedState,
        savedState.copy(
          syncStatus = WidgetSyncStatus.ERROR,
          lastSyncError = error.message ?: error.javaClass.simpleName,
          source = WidgetStateSource.WORKER,
        ),
      )
      Result.retry()
    }
  }

  private suspend fun retry(reason: String): Result {
    Log.w(TAG, reason)
    return Result.retry()
  }

  private suspend fun publishStatusIfCurrent(
    expectedState: LightWidgetState,
    state: LightWidgetState,
  ) {
    val persistedState = LightWidgetStore.saveIfCurrent(applicationContext, expectedState, state)
    if (persistedState == null) {
      Log.i(TAG, "Skipped stale background sync result because widget state changed during read")
      return
    }
    updateLightDialWidgets(applicationContext, reason = "worker")
  }

  companion object {
    private const val TAG = "WidgetStateSyncWorker"
    private const val PERMISSION_TIMEOUT_MILLIS = 10_000L
    private const val READ_TIMEOUT_MILLIS = 20_000L
  }
}
