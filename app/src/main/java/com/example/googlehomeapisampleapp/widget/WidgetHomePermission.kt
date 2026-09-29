package com.example.googlehomeapisampleapp.widget

import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import com.example.googlehomeapisampleapp.HomeClientProvider
import com.example.googlehomeapisampleapp.MainActivity
import com.google.home.PermissionsState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

internal suspend fun HomeClientProvider.ensureWidgetPermissions(
  activity: ComponentActivity,
): Boolean {
  if (hasWidgetPermissions()) return true

  activity.startActivity(
    Intent(activity, MainActivity::class.java).apply {
      action = MainActivity.ACTION_REQUEST_HOME_PERMISSIONS
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    },
  )
  activity.finish()
  return false
}

internal suspend fun HomeClientProvider.hasWidgetPermissions(): Boolean {
  val client = getClient()
  val permissionState = withTimeoutOrNull(10_000L) {
    client.hasPermissions().first {
      it != PermissionsState.PERMISSIONS_STATE_UNINITIALIZED
    }
  } ?: PermissionsState.PERMISSIONS_STATE_UNINITIALIZED
  Log.i("WidgetHomePermission", "Widget permission state=$permissionState")
  return permissionState == PermissionsState.GRANTED
}
