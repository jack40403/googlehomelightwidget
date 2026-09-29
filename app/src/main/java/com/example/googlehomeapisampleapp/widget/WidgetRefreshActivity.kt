package com.example.googlehomeapisampleapp.widget

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.googlehomeapisampleapp.HomeClientProvider
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WidgetRefreshActivity : ComponentActivity() {
  @Inject lateinit var homeClientProvider: HomeClientProvider

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.i(TAG, "Manual widget refresh clicked")

    lifecycleScope.launch {
      try {
        if (!homeClientProvider.ensureWidgetPermissions(this@WidgetRefreshActivity)) {
          val currentState = LightWidgetStore.load(applicationContext)
          LightWidgetStore.save(
            applicationContext,
            currentState.copy(
              syncStatus = WidgetSyncStatus.ERROR,
              lastSyncError = "Google Home permission is not granted",
              source = WidgetStateSource.MANUAL_REFRESH,
            ),
          )
          updateLightDialWidgets(applicationContext, reason = "manual_refresh_permission_denied")
          Toast.makeText(
            this@WidgetRefreshActivity,
            "Google Home permission is not granted",
            Toast.LENGTH_SHORT,
          ).show()
          return@launch
        }
        val refreshedState = WidgetCommandCoordinator.manualRefresh(
          context = applicationContext,
          homeClient = homeClientProvider.getClient(),
        )
        Log.i(
          TAG,
          "Manual widget refresh completed: isOn=${refreshedState.isOn}, " +
            "brightness=${refreshedState.brightnessLevel}, devices=${refreshedState.deviceIds.size}",
        )
        Toast.makeText(this@WidgetRefreshActivity, "已同步 Google Home", Toast.LENGTH_SHORT).show()
      } catch (error: Exception) {
        Log.e(TAG, "Widget refresh failed", error)
        Toast.makeText(
          this@WidgetRefreshActivity,
          "Google Home 同步失敗：${error.message ?: error.javaClass.simpleName}",
          Toast.LENGTH_SHORT,
        ).show()
      } finally {
        finish()
      }
    }
  }

  companion object {
    private const val TAG = "WidgetRefresh"
  }
}
