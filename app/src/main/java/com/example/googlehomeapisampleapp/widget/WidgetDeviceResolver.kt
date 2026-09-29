package com.example.googlehomeapisampleapp.widget

import android.content.Context
import android.util.Log
import com.example.googlehomeapisampleapp.HomeClientProvider
import com.google.home.DeviceGroup
import com.google.home.DeviceType
import com.google.home.HomeClient
import com.google.home.HomeDevice
import com.google.home.annotation.HomeExperimentalApi
import com.google.home.devices
import com.google.home.google.ExtendedColorControl
import com.google.home.matter.standard.LevelControl
import com.google.home.matter.standard.OnOff
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.min

@OptIn(HomeExperimentalApi::class)
suspend fun HomeClient.resolveWidgetDevices(
  context: Context,
  state: LightWidgetState,
): List<HomeDevice> {
  val hiddenDeviceIds = HiddenDevicesStore.load(context)
  if (state.targetKind == WidgetTargetKind.GROUP && state.targetId != null) {
    val group = entities(DeviceGroup).first().firstOrNull { it.id.id == state.targetId }
    if (group != null) {
      val devices = group.devices(enableMultipartDevices = true).first()
        .filterNot { it.id.id in hiddenDeviceIds }
      Log.i(TAG, "Resolved Google Home group ${state.targetId} to ${devices.size} device(s)")
      return devices
    }
    Log.w(TAG, "Google Home group ${state.targetId} was not found")
  }
  val deviceIds = state.deviceIds.ifEmpty { setOfNotNull(state.deviceId) }
  val resolvedDevices = devices().first().filter { it.id.id in deviceIds && it.id.id !in hiddenDeviceIds }
  Log.i(TAG, "Resolved widget target ${state.targetKind}:${state.targetId} to ${resolvedDevices.size} device(s)")
  return resolvedDevices
}

suspend fun HomeClient.readWidgetState(
  context: Context,
  state: LightWidgetState,
  forceRefresh: Boolean = false,
): LightWidgetState {
  val devices = resolveWidgetDevices(context, state)
  if (devices.isEmpty()) {
    Log.w(TAG, "Cannot read widget state because the target resolved to zero devices")
    if (state.deviceIds.isNotEmpty() || state.deviceId != null) {
      error("Widget target resolved to zero Google Home devices")
    }
    return state
  }
  val snapshots = coroutineScope {
    devices.map { device ->
      async {
        var readError: Throwable? = null
        val types = runCatching {
          device.readWidgetTypes(forceRefresh, state.operationId)
        }.getOrElse { error ->
          readError = error
          Log.e(
            TAG,
            "Unable to refresh device ${device.id.id}; retaining its latest Flow snapshot",
            error,
          )
          device.types().first().toList()
        }
        WidgetDeviceSnapshot(device, types, types.flatMap { it.traits() }, readError)
      }
    }.awaitAll()
  }
  val types = snapshots.flatMap { it.types }
  val connectivity = types
    .groupingBy { it.metadata.sourceConnectivity.connectivityState }
    .eachCount()
  Log.i(TAG, "Read widget state for ${devices.size} device(s); connectivity=$connectivity")
  val traits = snapshots.flatMap { it.traits }
  val activeSnapshots = snapshots.filter { snapshot ->
    snapshot.traits.filterIsInstance<OnOff>().any { it.onOff == true }
  }
  val levels = activeSnapshots
    .flatMap { it.traits.filterIsInstance<LevelControl>() }
    .mapNotNull { it.currentLevel?.toInt() }
  val color = activeSnapshots
    .asSequence()
    .flatMap { it.traits.asSequence() }
    .filterIsInstance<ExtendedColorControl>()
    .firstOrNull()
    ?: traits.filterIsInstance<ExtendedColorControl>().firstOrNull()
  val hue = color?.currentHue ?: state.colorHue
  val saturation = color?.currentSaturation ?: state.colorSaturation
  val onOffValues = traits.filterIsInstance<OnOff>().mapNotNull { it.onOff }
  val isOn = onOffValues.takeIf { it.isNotEmpty() }?.any { it } ?: state.isOn
  val brightness = levels.average().takeIf { !it.isNaN() }?.toInt()?.coerceIn(0, 254)
    ?: state.brightnessLevel
  Log.i(
    TAG,
    "Widget state values: isOn=$isOn, brightness=$brightness, " +
      "onOffTraits=${traits.count { it is OnOff }}, levelTraits=${traits.count { it is LevelControl }}, " +
      "colorTraits=${traits.count { it is ExtendedColorControl }}, forceRefresh=$forceRefresh, " +
      "operationId=${state.operationId}",
  )
  return state.copy(
    deviceId = devices.firstOrNull()?.id?.id ?: state.deviceId,
    deviceIds = devices.map { it.id.id }.toSet(),
    isOn = isOn,
    brightnessLevel = brightness,
    colorHue = hue,
    colorSaturation = saturation,
    colorName = friendlyColorName(hue, saturation, color?.currentName ?: state.colorName),
    lastSyncError = snapshots.count { it.error != null }.takeIf { it > 0 }?.let {
      "$it 個裝置狀態讀取失敗"
    },
  )
}

suspend fun HomeClient.readWidgetStateAfterCommand(
  context: Context,
  state: LightWidgetState,
  expectedIsOn: Boolean? = null,
  expectedBrightnessLevel: Int? = null,
  expectedHue: Float? = null,
  expectedSaturation: Float? = null,
): LightWidgetState? {
  val latestState = readWidgetState(context, state, forceRefresh = true)
  if (latestState.matchesWidgetExpectation(
      expectedIsOn,
      expectedBrightnessLevel,
      expectedHue,
      expectedSaturation,
    )
  ) {
    return latestState
  }
  Log.w(
    TAG,
    "Google Home readback did not reach the expected state: " +
      "expectedOn=$expectedIsOn, expectedBrightness=$expectedBrightnessLevel, " +
      "expectedHue=$expectedHue, expectedSaturation=$expectedSaturation, " +
      "actualOn=${latestState.isOn}, actualBrightness=${latestState.brightnessLevel}, " +
      "actualHue=${latestState.colorHue}, actualSaturation=${latestState.colorSaturation}",
  )
  return null
}

private suspend fun HomeDevice.readWidgetTypes(
  forceRefresh: Boolean,
  operationId: String?,
): List<DeviceType> = coroutineScope {
  val registeredTypes = types().first()
  registeredTypes.map { registeredType ->
    async {
      val typeFlow = type(registeredType.factory)
      val currentType = runCatching { typeFlow.first() }.getOrDefault(registeredType)
      if (!forceRefresh) return@async currentType

      val refreshableTraits = currentType.traits().filter {
        it is OnOff || it is LevelControl || it is ExtendedColorControl
      }
      if (refreshableTraits.isEmpty()) return@async currentType

      val latestType = AtomicReference(currentType)
      val emissionCount = AtomicInteger(0)
      val refreshedType = async(start = CoroutineStart.UNDISPATCHED) {
        withTimeoutOrNull(FORCE_READ_TIMEOUT_MILLIS) {
          typeFlow.drop(1).take(refreshableTraits.size).collect { emittedType ->
            latestType.set(emittedType)
            emissionCount.incrementAndGet()
          }
        }
        latestType.get()
      }
      Log.i(
        TAG,
        "Home force read started: operationId=$operationId, deviceId=${id.id}, " +
          "type=${registeredType.factory}, traits=${refreshableTraits.size}",
      )
      refreshableTraits.forEach { trait ->
        when (trait) {
          is OnOff -> trait.forceRead()
          is LevelControl -> trait.forceRead()
          is ExtendedColorControl -> trait.forceRead()
        }
      }
      refreshedType.await().also {
        Log.i(
          TAG,
          "Home force read completed: operationId=$operationId, deviceId=${id.id}, " +
            "emissions=${emissionCount.get()}/${refreshableTraits.size}",
        )
      }
    }
  }.awaitAll()
}

private data class WidgetDeviceSnapshot(
  val device: HomeDevice,
  val types: List<DeviceType>,
  val traits: List<Any>,
  val error: Throwable?,
)

private fun LightWidgetState.matchesWidgetExpectation(
  expectedIsOn: Boolean?,
  expectedBrightnessLevel: Int?,
  expectedHue: Float?,
  expectedSaturation: Float?,
): Boolean {
  val powerMatches = expectedIsOn == null || isOn == expectedIsOn
  val brightnessMatches = expectedBrightnessLevel == null ||
    brightnessLevel?.let { abs(it - expectedBrightnessLevel) <= 3 } == true
  val hueMatches = expectedHue == null || colorHue?.let { hueDistance(it, expectedHue) <= 6f } == true
  val saturationMatches = expectedSaturation == null ||
    colorSaturation?.let { abs(it - expectedSaturation) <= 0.08f } == true
  return powerMatches && brightnessMatches && hueMatches && saturationMatches
}

private fun hueDistance(first: Float, second: Float): Float {
  val difference = abs((((first - second) % 360f) + 360f) % 360f)
  return min(difference, 360f - difference)
}

suspend fun refreshWidgetState(
  context: Context,
  provider: HomeClientProvider,
  state: LightWidgetState,
): LightWidgetState = provider.getClient().readWidgetState(context, state, forceRefresh = true)

private const val TAG = "WidgetDeviceResolver"
private const val FORCE_READ_TIMEOUT_MILLIS = 3_000L
