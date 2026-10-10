package com.example.googlehomeapisampleapp.widget

import android.content.Context
import android.util.Log
import kotlin.math.abs
import kotlin.math.min

data class LightWidgetState(
  val targetKind: WidgetTargetKind = WidgetTargetKind.DEVICE,
  val targetId: String? = null,
  val deviceId: String?,
  val deviceIds: Set<String> = emptySet(),
  val displayName: String,
  val isOn: Boolean,
  val brightnessLevel: Int?,
  val colorHue: Float? = null,
  val colorSaturation: Float? = null,
  val colorName: String? = null,
  val lastUpdatedAt: Long? = null,
  val syncStatus: WidgetSyncStatus = WidgetSyncStatus.UNKNOWN,
  val lastSyncError: String? = null,
  val stateVersion: Long = 0L,
  val source: WidgetStateSource = WidgetStateSource.UNKNOWN,
  val operationId: String? = null,
  val pendingIsOn: Boolean? = null,
  val pendingBrightnessLevel: Int? = null,
  val pendingColorHue: Float? = null,
  val pendingColorSaturation: Float? = null,
)

enum class WidgetSyncStatus {
  UNKNOWN,
  SYNCING,
  CONNECTED,
  ERROR,
}

enum class WidgetStateSource {
  UNKNOWN,
  SELECTION,
  COMMAND,
  HOME_FLOW,
  MANUAL_REFRESH,
  WORKER,
  DEBUG,
}

object LightWidgetStore {
  private const val PREFERENCES_NAME = "light_widget"
  private const val KEY_TARGET_KIND = "target_kind"
  private const val KEY_TARGET_ID = "target_id"
  private const val KEY_DEVICE_ID = "device_id"
  private const val KEY_DEVICE_IDS = "device_ids"
  private const val KEY_DISPLAY_NAME = "display_name"
  private const val KEY_IS_ON = "is_on"
  private const val KEY_BRIGHTNESS_LEVEL = "brightness_level"
  private const val KEY_COLOR_HUE = "color_hue"
  private const val KEY_COLOR_SATURATION = "color_saturation"
  private const val KEY_COLOR_NAME = "color_name"
  private const val KEY_LAST_UPDATED_AT = "last_updated_at"
  private const val KEY_SYNC_STATUS = "sync_status"
  private const val KEY_LAST_SYNC_ERROR = "last_sync_error"
  private const val KEY_STATE_VERSION = "state_version"
  private const val KEY_SOURCE = "source"
  private const val KEY_OPERATION_ID = "operation_id"
  private const val KEY_PENDING_IS_ON = "pending_is_on"
  private const val KEY_PENDING_BRIGHTNESS_LEVEL = "pending_brightness_level"
  private const val KEY_PENDING_COLOR_HUE = "pending_color_hue"
  private const val KEY_PENDING_COLOR_SATURATION = "pending_color_saturation"

  @Synchronized
  fun load(context: Context): LightWidgetState {
    val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    return load(preferences)
  }

  private fun load(preferences: android.content.SharedPreferences): LightWidgetState {
    val deviceId = preferences.getString(KEY_DEVICE_ID, null)
    val storedDeviceIds = preferences.getStringSet(KEY_DEVICE_IDS, emptySet()).orEmpty().toSet()
    val deviceIds = if (storedDeviceIds.isEmpty() && deviceId != null) setOf(deviceId) else storedDeviceIds
    return LightWidgetState(
      targetKind = WidgetTargetKind.from(preferences.getString(KEY_TARGET_KIND, null)),
      targetId = preferences.getString(KEY_TARGET_ID, deviceId),
      deviceId = deviceId,
      deviceIds = deviceIds,
      displayName = preferences.getString(KEY_DISPLAY_NAME, "Google Home 燈光")!!,
      isOn = preferences.getBoolean(KEY_IS_ON, false),
      brightnessLevel = if (preferences.contains(KEY_BRIGHTNESS_LEVEL)) {
        preferences.getInt(KEY_BRIGHTNESS_LEVEL, 0).coerceIn(0, 254)
      } else {
        null
      },
      colorHue = if (preferences.contains(KEY_COLOR_HUE)) preferences.getFloat(KEY_COLOR_HUE, 0f) else null,
      colorSaturation = if (preferences.contains(KEY_COLOR_SATURATION)) {
        preferences.getFloat(KEY_COLOR_SATURATION, 1f)
      } else {
        null
      },
      colorName = preferences.getString(KEY_COLOR_NAME, null),
      lastUpdatedAt = if (preferences.contains(KEY_LAST_UPDATED_AT)) {
        preferences.getLong(KEY_LAST_UPDATED_AT, 0L)
      } else {
        null
      },
      syncStatus = runCatching {
        WidgetSyncStatus.valueOf(preferences.getString(KEY_SYNC_STATUS, null).orEmpty())
      }.getOrDefault(WidgetSyncStatus.UNKNOWN),
      lastSyncError = preferences.getString(KEY_LAST_SYNC_ERROR, null),
      stateVersion = preferences.getLong(KEY_STATE_VERSION, 0L),
      source = runCatching {
        WidgetStateSource.valueOf(preferences.getString(KEY_SOURCE, null).orEmpty())
      }.getOrDefault(WidgetStateSource.UNKNOWN),
      operationId = preferences.getString(KEY_OPERATION_ID, null),
      pendingIsOn = if (preferences.contains(KEY_PENDING_IS_ON)) {
        preferences.getBoolean(KEY_PENDING_IS_ON, false)
      } else {
        null
      },
      pendingBrightnessLevel = if (preferences.contains(KEY_PENDING_BRIGHTNESS_LEVEL)) {
        preferences.getInt(KEY_PENDING_BRIGHTNESS_LEVEL, 0).coerceIn(0, 254)
      } else {
        null
      },
      pendingColorHue = if (preferences.contains(KEY_PENDING_COLOR_HUE)) {
        preferences.getFloat(KEY_PENDING_COLOR_HUE, 0f)
      } else {
        null
      },
      pendingColorSaturation = if (preferences.contains(KEY_PENDING_COLOR_SATURATION)) {
        preferences.getFloat(KEY_PENDING_COLOR_SATURATION, 0f).coerceIn(0f, 1f)
      } else {
        null
      },
    )
  }

  fun select(
    context: Context,
    deviceId: String,
    displayName: String,
    isOn: Boolean,
    brightnessLevel: Int?,
  ) {
    selectTarget(
      context = context,
      kind = WidgetTargetKind.DEVICE,
      targetId = deviceId,
      displayName = displayName,
      deviceIds = setOf(deviceId),
      isOn = isOn,
      brightnessLevel = brightnessLevel,
    )
  }

  fun selectTarget(
    context: Context,
    kind: WidgetTargetKind,
    targetId: String,
    displayName: String,
    deviceIds: Set<String>,
    isOn: Boolean,
    brightnessLevel: Int?,
    colorHue: Float? = null,
    colorSaturation: Float? = null,
    colorName: String? = null,
  ) {
    save(
      context,
      LightWidgetState(
        targetKind = kind,
        targetId = targetId,
        deviceId = deviceIds.firstOrNull(),
        deviceIds = deviceIds,
        displayName = displayName,
        isOn = isOn,
        brightnessLevel = brightnessLevel,
        colorHue = colorHue,
        colorSaturation = colorSaturation,
        colorName = colorName,
        lastUpdatedAt = System.currentTimeMillis(),
        syncStatus = WidgetSyncStatus.UNKNOWN,
        lastSyncError = null,
        source = WidgetStateSource.SELECTION,
      ),
    )
  }

  @Synchronized
  fun save(context: Context, state: LightWidgetState): LightWidgetState {
    val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    val currentVersion = preferences.getLong(KEY_STATE_VERSION, 0L)
    val persistedState = state.copy(
      stateVersion = maxOf(state.stateVersion, currentVersion + 1L),
    )
    write(preferences, persistedState)
    Log.i(
      TAG,
      "State persisted: operationId=${persistedState.operationId}, source=${persistedState.source}, " +
        "stateVersion=${persistedState.stateVersion}, isOn=${persistedState.isOn}, " +
        "brightness=${persistedState.brightnessLevel}, syncStatus=${persistedState.syncStatus}",
    )
    return persistedState
  }

  @Synchronized
  fun saveIfCurrent(
    context: Context,
    expectedState: LightWidgetState,
    state: LightWidgetState,
  ): LightWidgetState? {
    val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    val currentState = load(preferences)
    if (!isWidgetStateCurrent(expectedState, currentState)) {
      Log.i(
        TAG,
        "State write rejected as stale: operationId=${state.operationId}, " +
          "expectedVersion=${expectedState.stateVersion}, currentVersion=${currentState.stateVersion}",
      )
      return null
    }
    val persistedState = state.copy(
      stateVersion = maxOf(state.stateVersion, currentState.stateVersion + 1L),
    )
    write(preferences, persistedState)
    Log.i(
      TAG,
      "Current state replaced: operationId=${persistedState.operationId}, source=${persistedState.source}, " +
        "stateVersion=${persistedState.stateVersion}, isOn=${persistedState.isOn}, " +
        "brightness=${persistedState.brightnessLevel}, syncStatus=${persistedState.syncStatus}",
    )
    return persistedState
  }

  @Synchronized
  fun saveObservation(
    context: Context,
    observedState: LightWidgetState,
    source: WidgetStateSource,
    expectedStateVersion: Long? = null,
    operationId: String? = null,
    authoritative: Boolean = false,
    syncError: String? = null,
  ): LightWidgetState? {
    val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    val currentState = load(preferences)
    val operationMatches = operationId == null || currentState.operationId == operationId
    val versionMatches = expectedStateVersion == null || currentState.stateVersion == expectedStateVersion
    if (!operationMatches || (!versionMatches && operationId == null)) {
      Log.i(
        TAG,
        "Observation rejected as stale: operationId=$operationId, currentOperationId=${currentState.operationId}, " +
          "expectedVersion=$expectedStateVersion, currentVersion=${currentState.stateVersion}, source=$source",
      )
      return null
    }
    if (!isWidgetObservationAcceptable(currentState, observedState, authoritative)) {
      Log.i(
        TAG,
        "Observation rejected while command is pending: operationId=${currentState.operationId}, " +
          "source=$source, observedOn=${observedState.isOn}, " +
          "observedBrightness=${observedState.brightnessLevel}",
      )
      return null
    }
    val mergedObservation = retainKnownWidgetValues(currentState, observedState)
    val persistedState = mergedObservation.copy(
      targetKind = currentState.targetKind,
      targetId = currentState.targetId,
      displayName = currentState.displayName,
      lastUpdatedAt = observedState.lastUpdatedAt ?: System.currentTimeMillis(),
      syncStatus = if (syncError == null) WidgetSyncStatus.CONNECTED else WidgetSyncStatus.ERROR,
      lastSyncError = syncError,
      stateVersion = currentState.stateVersion + 1L,
      source = source,
      operationId = operationId ?: currentState.operationId,
      pendingIsOn = null,
      pendingBrightnessLevel = null,
      pendingColorHue = null,
      pendingColorSaturation = null,
    )
    write(preferences, persistedState)
    Log.i(
      TAG,
      "Observation persisted: operationId=${persistedState.operationId}, source=$source, " +
        "stateVersion=${persistedState.stateVersion}, isOn=${persistedState.isOn}, " +
        "brightness=${persistedState.brightnessLevel}, authoritative=$authoritative",
    )
    return persistedState
  }

  @Synchronized
  fun saveIfOperationCurrent(
    context: Context,
    operationId: String,
    state: LightWidgetState,
  ): LightWidgetState? {
    val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    val currentState = load(preferences)
    if (currentState.operationId != operationId) {
      Log.i(
        TAG,
        "Operation write rejected as stale: operationId=$operationId, " +
          "currentOperationId=${currentState.operationId}",
      )
      return null
    }
    val persistedState = state.copy(
      stateVersion = currentState.stateVersion + 1L,
      operationId = operationId,
    )
    write(preferences, persistedState)
    Log.i(
      TAG,
      "Operation state persisted: operationId=$operationId, source=${persistedState.source}, " +
        "stateVersion=${persistedState.stateVersion}, syncStatus=${persistedState.syncStatus}",
    )
    return persistedState
  }

  private fun write(
    preferences: android.content.SharedPreferences,
    state: LightWidgetState,
  ) {
    val editor = preferences
      .edit()
      .putString(KEY_TARGET_KIND, state.targetKind.value)
      .putString(KEY_TARGET_ID, state.targetId)
      .putString(KEY_DEVICE_ID, state.deviceId)
      .putStringSet(KEY_DEVICE_IDS, state.deviceIds)
      .putString(KEY_DISPLAY_NAME, state.displayName)
      .putBoolean(KEY_IS_ON, state.isOn)
    editor.apply {
      if (state.brightnessLevel == null) {
        remove(KEY_BRIGHTNESS_LEVEL)
      } else {
        putInt(KEY_BRIGHTNESS_LEVEL, state.brightnessLevel.coerceIn(0, 254))
      }
      if (state.colorHue == null) remove(KEY_COLOR_HUE) else putFloat(KEY_COLOR_HUE, state.colorHue)
      if (state.colorSaturation == null) {
        remove(KEY_COLOR_SATURATION)
      } else {
        putFloat(KEY_COLOR_SATURATION, state.colorSaturation)
      }
      if (state.colorName == null) remove(KEY_COLOR_NAME) else putString(KEY_COLOR_NAME, state.colorName)
      if (state.lastUpdatedAt == null) {
        remove(KEY_LAST_UPDATED_AT)
      } else {
        putLong(KEY_LAST_UPDATED_AT, state.lastUpdatedAt)
      }
      putString(KEY_SYNC_STATUS, state.syncStatus.name)
      putString(KEY_SOURCE, state.source.name)
      if (state.operationId == null) remove(KEY_OPERATION_ID) else putString(KEY_OPERATION_ID, state.operationId)
      if (state.lastSyncError == null) {
        remove(KEY_LAST_SYNC_ERROR)
      } else {
        putString(KEY_LAST_SYNC_ERROR, state.lastSyncError)
      }
      if (state.pendingIsOn == null) {
        remove(KEY_PENDING_IS_ON)
      } else {
        putBoolean(KEY_PENDING_IS_ON, state.pendingIsOn)
      }
      if (state.pendingBrightnessLevel == null) {
        remove(KEY_PENDING_BRIGHTNESS_LEVEL)
      } else {
        putInt(KEY_PENDING_BRIGHTNESS_LEVEL, state.pendingBrightnessLevel.coerceIn(0, 254))
      }
      if (state.pendingColorHue == null) {
        remove(KEY_PENDING_COLOR_HUE)
      } else {
        putFloat(KEY_PENDING_COLOR_HUE, state.pendingColorHue)
      }
      if (state.pendingColorSaturation == null) {
        remove(KEY_PENDING_COLOR_SATURATION)
      } else {
        putFloat(KEY_PENDING_COLOR_SATURATION, state.pendingColorSaturation.coerceIn(0f, 1f))
      }
      putLong(KEY_STATE_VERSION, state.stateVersion)
    }
    if (!editor.commit()) {
      Log.e(TAG, "Unable to persist the widget state")
    }
  }

  private const val TAG = "LightWidgetStore"
}

internal fun isWidgetStateCurrent(
  expectedState: LightWidgetState,
  currentState: LightWidgetState,
): Boolean = if (expectedState.stateVersion > 0L || currentState.stateVersion > 0L) {
  expectedState.stateVersion == currentState.stateVersion
} else {
  expectedState.lastUpdatedAt == currentState.lastUpdatedAt
}

internal fun LightWidgetState.hasPendingWidgetCommand(
  nowMillis: Long = System.currentTimeMillis(),
): Boolean =
  operationId != null &&
    (
      pendingIsOn != null ||
        pendingBrightnessLevel != null ||
        pendingColorHue != null ||
        pendingColorSaturation != null
      ) &&
    !isPendingWidgetCommandExpired(nowMillis)

private fun LightWidgetState.isPendingWidgetCommandExpired(nowMillis: Long): Boolean {
  val pendingAge = lastUpdatedAt?.let { nowMillis - it } ?: return false
  return pendingAge >= PENDING_COMMAND_MAX_AGE_MILLIS
}

internal fun LightWidgetState.clearPendingWidgetCommand(): LightWidgetState = copy(
  pendingIsOn = null,
  pendingBrightnessLevel = null,
  pendingColorHue = null,
  pendingColorSaturation = null,
)

internal fun retainKnownWidgetValues(
  currentState: LightWidgetState,
  observedState: LightWidgetState,
): LightWidgetState = observedState.copy(
  brightnessLevel = observedState.brightnessLevel ?: currentState.brightnessLevel,
  colorHue = observedState.colorHue ?: currentState.colorHue,
  colorSaturation = observedState.colorSaturation ?: currentState.colorSaturation,
  colorName = observedState.colorName ?: currentState.colorName,
)

internal fun isWidgetObservationAcceptable(
  currentState: LightWidgetState,
  observedState: LightWidgetState,
  authoritative: Boolean = false,
  nowMillis: Long = System.currentTimeMillis(),
): Boolean {
  if (authoritative || !currentState.hasPendingWidgetCommand(nowMillis)) return true

  val pendingIsOn = currentState.pendingIsOn
  val pendingBrightness = currentState.pendingBrightnessLevel
  val pendingHue = currentState.pendingColorHue
  val pendingSaturation = currentState.pendingColorSaturation
  val powerMatches = pendingIsOn == null || observedState.isOn == pendingIsOn
  val brightnessMatches = pendingBrightness == null ||
    observedState.brightnessLevel?.let { abs(it - pendingBrightness) <= 3 } == true
  val hueMatches = pendingHue == null ||
    observedState.colorHue?.let { hueDistance(it, pendingHue) <= 6f } == true
  val saturationMatches = pendingSaturation == null ||
    observedState.colorSaturation?.let { abs(it - pendingSaturation) <= 0.08f } == true
  return powerMatches && brightnessMatches && hueMatches && saturationMatches
}

private fun hueDistance(first: Float, second: Float): Float {
  val difference = abs((((first - second) % 360f) + 360f) % 360f)
  return min(difference, 360f - difference)
}

internal const val PENDING_COMMAND_MAX_AGE_MILLIS = 15_000L
