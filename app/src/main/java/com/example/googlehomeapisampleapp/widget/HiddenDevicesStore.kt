package com.example.googlehomeapisampleapp.widget

import android.content.Context

object HiddenDevicesStore {
  private const val PREFERENCES_NAME = "hidden_devices"
  private const val KEY_DEVICE_IDS = "device_ids"

  fun load(context: Context): Set<String> {
    return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
      .getStringSet(KEY_DEVICE_IDS, emptySet())
      ?.toSet()
      ?: emptySet()
  }

  fun save(context: Context, deviceIds: Set<String>) {
    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
      .edit()
      .putStringSet(KEY_DEVICE_IDS, deviceIds)
      .apply()
  }
}
