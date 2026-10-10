/* Copyright 2025 Google LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.example.googlehomeapisampleapp.viewmodel

import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.googlehomeapisampleapp.group.DeviceGroupController
import com.example.googlehomeapisampleapp.HomeApp
import com.example.googlehomeapisampleapp.HomeModule_ProvideSupportedTraitsFactory
import com.example.googlehomeapisampleapp.MainActivity
import com.example.googlehomeapisampleapp.cloudlinking.CurrentStructureRepository
import com.example.googlehomeapisampleapp.repository.AutomationsRepository
import com.example.googlehomeapisampleapp.viewmodel.automations.ActionViewModel
import com.example.googlehomeapisampleapp.viewmodel.automations.AutomationViewModel
import com.example.googlehomeapisampleapp.viewmodel.automations.CandidateViewModel
import com.example.googlehomeapisampleapp.viewmodel.automations.DraftViewModel
import com.example.googlehomeapisampleapp.viewmodel.devices.DeviceViewModel
import com.example.googlehomeapisampleapp.viewmodel.hubs.HubDiscoveryViewModel
import com.example.googlehomeapisampleapp.viewmodel.structures.RoomViewModel
import com.example.googlehomeapisampleapp.viewmodel.structures.StructureViewModel
import com.example.googlehomeapisampleapp.widget.GoogleHomeGroupOption
import com.example.googlehomeapisampleapp.widget.HiddenDevicesStore
import com.example.googlehomeapisampleapp.widget.LightWidgetStore
import com.example.googlehomeapisampleapp.widget.WidgetCommandCoordinator
import com.example.googlehomeapisampleapp.widget.WidgetStateMonitor
import com.example.googlehomeapisampleapp.widget.WidgetTargetKind
import com.example.googlehomeapisampleapp.widget.WidgetTargetOption
import com.example.googlehomeapisampleapp.widget.updateLightDialWidgets
import com.example.googlehomeapisampleapp.widget.widgetSnapshotFromViewModels
import com.google.home.Structure
import com.google.home.DeviceGroup
import com.google.home.devices
import com.google.home.featureConsentStatus
import com.google.home.ConsentStatus
import com.google.home.annotation.HomeExperimentalApi
import com.google.home.annotation.HomeExperimentalGenericApi
import com.google.home.automation.CommandCandidate
import com.google.home.automation.DraftAutomation
import com.google.home.automation.NodeCandidate
import com.google.home.automation.UnknownDeviceType
import com.google.home.userPresenceSettings
import com.google.home.deleteHistory
import com.google.home.google.AreaAttendanceState
import com.google.home.google.AreaAttendanceStateTrait
import com.google.home.google.AreaPresenceState
import com.google.home.google.AreaPresenceStateTrait
import com.google.home.google.Group
import com.google.home.google.GroupManagement
import com.google.home.google.UserPresenceSettings
import com.google.home.google.UserPresenceSettingsTrait
import com.example.googlehomeapisampleapp.widget.FavoriteDevicesStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HomeAppViewModel(
  val homeApp: HomeApp,
  val currentStructureRepository: CurrentStructureRepository,
) : ViewModel() {

  // Tabs showing main capabilities of the app:
  enum class NavigationTab {
    DEVICES,
    AUTOMATIONS,
  }

  companion object {
    const val TAG = "HomeAppViewModel"
    private const val FEATURE_PRESENCE_SENSING_NAME = "FEATURE_PRESENCE_SENSING"
    private const val FEATURE_PRESENCE_SENSING_ID = 3L
  }

  // Container tracking the active navigation tab:
  var selectedTab: MutableStateFlow<NavigationTab> = MutableStateFlow(NavigationTab.DEVICES)

  private val _showCloudLinkingSheet = MutableStateFlow(false)
  val showCloudLinkingSheet = _showCloudLinkingSheet.asStateFlow()

  fun openCloudLinkingSheet() {
    _showCloudLinkingSheet.value = true
  }

  fun closeCloudLinkingSheet() {
    _showCloudLinkingSheet.value = false
  }

  // Containers tracking the active object being edited:
  val selectedStructureVM: StateFlow<StructureViewModel?> =
    currentStructureRepository.selectedStructureVM

  private val _presenceRefreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  fun refreshPresenceSettings() {
    _presenceRefreshTrigger.tryEmit(Unit)
  }

  private val _resolvedConsentStatus = MutableStateFlow<ConsentStatus>(ConsentStatus.UNSPECIFIED)

  @OptIn(ExperimentalCoroutinesApi::class, HomeExperimentalApi::class)
  val selectedStructureFeatureConsentStatus: StateFlow<ConsentStatus> =
    combine(
      selectedStructureVM.filterNotNull(),
      _presenceRefreshTrigger.onStart { emit(Unit) }
    ) { structureVM, _ -> structureVM }
      .flatMapLatest { structureVM ->
        structureVM.structure.featureConsentStatus()
          .map { consentMap ->
            val entry = consentMap.entries.find {
              it.key.id.toLong() == FEATURE_PRESENCE_SENSING_ID ||
              it.key.name == FEATURE_PRESENCE_SENSING_NAME
            }
            entry?.value ?: ConsentStatus.UNSPECIFIED
          }
          .catch { e ->
            Log.w("HomeAppViewModel", "featureConsentStatus flow error: ${e.message}")
            emit(ConsentStatus.UNSPECIFIED)
          }
          .scan(_resolvedConsentStatus.value) { previousStatus, rawStatus ->
            val resolvedStatus = when (rawStatus) {
              ConsentStatus.CONSENTED, ConsentStatus.NOT_CONSENTED -> rawStatus
              ConsentStatus.UNSPECIFIED -> {
                if (previousStatus != ConsentStatus.UNSPECIFIED) {
                  previousStatus
                } else {
                  ConsentStatus.UNSPECIFIED
                }
              }
            }
            _resolvedConsentStatus.value = resolvedStatus
            resolvedStatus
          }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConsentStatus.UNSPECIFIED)

  @OptIn(ExperimentalCoroutinesApi::class, HomeExperimentalApi::class)
  val selectedStructureUserPresenceSettings: StateFlow<UserPresenceSettings?> =
    combine(
      selectedStructureVM,
      selectedStructureFeatureConsentStatus
    ) { structureVM, consentStatus ->
      Pair(structureVM, consentStatus)
    }
      .flatMapLatest { (structureVM, consentStatus) ->
        if (structureVM != null && consentStatus == ConsentStatus.CONSENTED) {
          structureVM.structure.userPresenceSettings()
            .catch<UserPresenceSettings?> { e ->
              Log.w("HomeAppViewModel", "UserPresenceSettings flow error: ${e.message}")
              emit(null)
            }
        } else {
          flowOf(null)
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

  @OptIn(ExperimentalCoroutinesApi::class)
  val selectedStructureAreaPresenceState: StateFlow<AreaPresenceState?> =
    combine(
      selectedStructureVM,
      selectedStructureFeatureConsentStatus
    ) { structureVM, consentStatus ->
      Pair(structureVM, consentStatus)
    }
      .flatMapLatest { (structureVM, consentStatus) ->
        if (structureVM != null && consentStatus == ConsentStatus.CONSENTED) {
          structureVM.structure.trait(AreaPresenceState)
            .catch<AreaPresenceState?> { e ->
              Log.w("HomeAppViewModel", "AreaPresenceState flow error: ${e.message}")
              emit(null)
            }
        } else {
          flowOf(null)
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

  @OptIn(ExperimentalCoroutinesApi::class)
  val selectedStructureAreaAttendanceState: StateFlow<AreaAttendanceState?> =
    combine(
      selectedStructureVM,
      selectedStructureFeatureConsentStatus
    ) { structureVM, consentStatus ->
      Pair(structureVM, consentStatus)
    }
      .flatMapLatest { (structureVM, consentStatus) ->
        if (structureVM != null && consentStatus == ConsentStatus.CONSENTED) {
          structureVM.structure.trait(AreaAttendanceState)
            .catch<AreaAttendanceState?> { e ->
              Log.w("HomeAppViewModel", "AreaAttendanceState flow error: ${e.message}")
              emit(null)
            }
        } else {
          flowOf(null)
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

  fun setPresenceOptIn(optIn: Boolean) {
    val structure = selectedStructureVM.value?.structure ?: return
    viewModelScope.launch {
      try {
        val consentStatus = selectedStructureFeatureConsentStatus.value
        if (optIn) {
          if (consentStatus != ConsentStatus.CONSENTED) {
            try {
              val result = homeApp.homeClient.updateFeatureConsent(
                listOf(com.google.home.FeatureConsentType(FEATURE_PRESENCE_SENSING_NAME, FEATURE_PRESENCE_SENSING_ID.toInt())),
                structure.id.id
              )
              refreshPresenceSettings()
              if (!result.granted) {
                return@launch
              }
            } catch (e: Exception) {
              Log.w("HomeAppViewModel", "Feature consent request failed: ${e.message}")
              refreshPresenceSettings()
              return@launch
            }
          }
        } else {
          if (consentStatus == ConsentStatus.CONSENTED) {
            try {
              val result = homeApp.homeClient.updateFeatureConsent(
                listOf(com.google.home.FeatureConsentType(FEATURE_PRESENCE_SENSING_NAME, FEATURE_PRESENCE_SENSING_ID.toInt())),
                structure.id.id
              )
              refreshPresenceSettings()
            } catch (e: Exception) {
              Log.w("HomeAppViewModel", "Feature consent OFF request failed: ${e.message}")
            }
          }
        }

        val settings = structure.userPresenceSettings().firstOrNull()
        if (settings != null) {
          if (settings.presenceOptIn != optIn) {
            settings.update {
              setPresenceOptIn(optIn)
            }
          }
        } else {
          Log.w("HomeAppViewModel", "userPresenceSettings returned null")
        }
        refreshPresenceSettings()
      } catch (e: Exception) {
        Log.e("HomeAppViewModel", "Error setting presence opt-in: ${e.message}", e)
      }
    }
  }

  fun deleteSelectedStructureHistory() {
    val structure = selectedStructureVM.value?.structure ?: return
    viewModelScope.launch {
      try {
        structure.deleteHistory(emptyList())
        Log.d("HomeAppViewModel", "Successfully deleted structure history")
      } catch (e: Exception) {
        Log.e("HomeAppViewModel", "Error deleting structure history: ${e.message}", e)
      }
    }
  }

  fun setSelectedStructure(structure: StructureViewModel?) {
    currentStructureRepository.setSelectedStructure(structure)
    refreshDeviceGroups()
  }

  var selectedDeviceVM: MutableStateFlow<DeviceViewModel?> = MutableStateFlow(null)
  var selectedAutomationVM: MutableStateFlow<AutomationViewModel?> = MutableStateFlow(null)
  var selectedDraftVM: MutableStateFlow<DraftViewModel?> = MutableStateFlow(null)
  var selectedCandidateVMs: MutableStateFlow<List<CandidateViewModel>?> = MutableStateFlow(null)

  private val _favoriteDeviceIds = MutableStateFlow(FavoriteDevicesStore.load(homeApp.context))
  val favoriteDeviceIds: StateFlow<Set<String>> = _favoriteDeviceIds.asStateFlow()

  private val _hiddenDeviceIds = MutableStateFlow(HiddenDevicesStore.load(homeApp.context))
  val hiddenDeviceIds: StateFlow<Set<String>> = _hiddenDeviceIds.asStateFlow()

  private val _deviceGroups = MutableStateFlow<List<GoogleHomeGroupOption>>(emptyList())
  val deviceGroups: StateFlow<List<GoogleHomeGroupOption>> = _deviceGroups.asStateFlow()

  // Container to store returned structures from the app:
  var structureVMs: MutableStateFlow<List<StructureViewModel>> = MutableStateFlow(mutableListOf())

  private var hubDiscoveryVM: HubDiscoveryViewModel? = null
  private var widgetStateMonitor: WidgetStateMonitor? = null
  val hubDiscoveryViewModel: HubDiscoveryViewModel
    get() = hubDiscoveryVM!!

  private val selectedStructureFlow: Flow<Structure> =
    selectedStructureVM
      .filterNotNull()
      .map { it.structure }
      .shareIn(scope = viewModelScope, started = SharingStarted.Eagerly, replay = 1)

  init {
    Log.i(TAG, "HomeAppViewModel init")
    // Assign active structure ID provider
    homeApp.permissionsManager.currentStructureIdProvider = {
      selectedStructureVM.value?.structure?.id?.id
    }
    val errorsEmitter: MutableSharedFlow<Exception> =
      MutableSharedFlow(replay = 0, extraBufferCapacity = 0)
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    // HubDiscoveryViewModel now consumes the derived selectedStructureFlow
    hubDiscoveryVM =
      HubDiscoveryViewModel(
        structureFlow = selectedStructureFlow,
        viewModelScope = viewModelScope,
        errorsEmitter = errorsEmitter,
        ioDispatcher = ioDispatcher,
      )

    viewModelScope.launch {
      var structuresJob: Job? = null
      // Resubscribe or cancel subscription when permission is updated
      homeApp.permissionsManager.permissionUpdatedEvent
        .map { homeApp.permissionsManager.isSignedIn.value }
        .collect { isSignedIn ->
          Log.i(TAG, "Sign-in state changed: $isSignedIn")
          structuresJob?.cancel()
          widgetStateMonitor?.stop()
          if (isSignedIn) {
            structuresJob = viewModelScope.launch { subscribeToStructures() }
            widgetStateMonitor = WidgetStateMonitor(
              context = homeApp.context,
              homeClient = homeApp.homeClient,
              scope = viewModelScope,
            ).also { it.start() }
          } else {
            Log.d(TAG, "Cancel the job to subscribe to structure")
          }
          viewModelScope.launch { updateLightDialWidgets(homeApp.context) }
        }
    }

    viewModelScope.launch {
      selectedStructureVM
        .filterNotNull()
        .flatMapLatest { it.deviceVMs }
        .collect {
          refreshDeviceGroups().join()
        }
    }
  }

  private suspend fun subscribeToStructures() {
    // Subscribe to structures returned by the Structures API:
    homeApp.homeClient.structures().collect { structureSet ->
      val structureVMList: MutableList<StructureViewModel> = mutableListOf()
      // Store structures in container ViewModels:
      for (structure in structureSet) {
        structureVMList.add(StructureViewModel(structure))
      }
      // Store the ViewModels:
      structureVMs.emit(structureVMList)

      // If a structure isn't selected yet, select the first structure from the list:
      if (selectedStructureVM.value == null && structureVMList.isNotEmpty()) {
        currentStructureRepository.setSelectedStructure(structureVMList.first())
      }
      refreshDeviceGroups()
    }
  }

  @OptIn(HomeExperimentalApi::class, HomeExperimentalGenericApi::class)
  fun refreshDeviceGroups(): Job = viewModelScope.launch {
    try {
      val structureDeviceIds = selectedStructureVM.value?.deviceVMs?.value
        ?.map { it.id }
        ?.toSet()
        .orEmpty()
      val groups = homeApp.homeClient.entities(DeviceGroup).first().mapNotNull { group ->
        val memberIds = runCatching {
          group.devices(enableMultipartDevices = true).first().map { it.id.id }.toSet()
        }.getOrDefault(emptySet())
        if (memberIds.isEmpty() || (structureDeviceIds.isNotEmpty() && memberIds.none { it in structureDeviceIds })) {
          return@mapNotNull null
        }
        val groupTrait: Group? = runCatching { group.trait(Group) }.getOrNull()
        val groupName = groupTrait?.let { trait ->
          sequenceOf("getName", "getCurrentName")
            .mapNotNull { methodName ->
              runCatching {
                trait.javaClass.getMethod(methodName).invoke(trait) as? String
              }.getOrNull()
            }
            .firstOrNull()
        }
          ?.takeIf { it.isNotBlank() }
          ?: "Google Home 群組"
        GoogleHomeGroupOption(group.id.id, groupName, memberIds)
      }.sortedBy { it.name }
      _deviceGroups.emit(groups)
    } catch (error: Exception) {
      Log.w(TAG, "Unable to load Google Home device groups: ${error.message}")
      _deviceGroups.emit(emptyList())
    }
  }

  @OptIn(HomeExperimentalApi::class, HomeExperimentalGenericApi::class)
  fun createDeviceGroup(name: String, deviceIds: Set<String>): Job = viewModelScope.launch {
    val structure = selectedStructureVM.value?.structure ?: return@launch
    try {
      val groupManagement = structure.trait(GroupManagement).firstOrNull()
        ?: error("Google Home 群組管理目前不可用")
      val trimmedName = name.trim()
      require(trimmedName.isNotEmpty()) { "群組名稱不可為空白" }
      require(deviceIds.isNotEmpty()) { "至少選擇一台裝置" }
      groupManagement.createUserDefinedGroup(trimmedName, deviceIds.toList())
      refreshDeviceGroups().join()
    } catch (error: Exception) {
      MainActivity.showError(this@HomeAppViewModel, "建立群組失敗：${error.message}")
    }
  }

  @OptIn(HomeExperimentalApi::class, HomeExperimentalGenericApi::class)
  fun updateDeviceGroup(group: GoogleHomeGroupOption, name: String, deviceIds: Set<String>): Job = viewModelScope.launch {
    val structure = selectedStructureVM.value?.structure ?: return@launch
    try {
      val groupManagement = structure.trait(GroupManagement).firstOrNull()
        ?: error("Google Home 群組管理目前不可用")
      require(name.trim().isNotEmpty()) { "群組名稱不可為空白" }
      require(deviceIds.isNotEmpty()) { "至少選擇一台裝置" }
      val additions = (deviceIds - group.deviceIds).toList()
      val removals = (group.deviceIds - deviceIds).toList()
      if (additions.isNotEmpty()) groupManagement.addGroupMembers(group.id, additions)
      if (removals.isNotEmpty()) groupManagement.removeGroupMembers(group.id, removals)
      val entity = homeApp.homeClient.entities(DeviceGroup).first().firstOrNull { it.id.id == group.id }
      entity?.trait(Group)?.update(
        optimisticReturn = {},
        init = { setName(name.trim()) },
      )
      refreshDeviceGroups().join()
    } catch (error: Exception) {
      MainActivity.showError(this@HomeAppViewModel, "更新群組失敗：${error.message}")
    }
  }

  @OptIn(HomeExperimentalApi::class, HomeExperimentalGenericApi::class)
  fun deleteDeviceGroup(group: GoogleHomeGroupOption): Job = viewModelScope.launch {
    val structure = selectedStructureVM.value?.structure ?: return@launch
    try {
      structure.trait(GroupManagement).firstOrNull()?.deleteGroup(group.id)
      refreshDeviceGroups().join()
    } catch (error: Exception) {
      MainActivity.showError(this@HomeAppViewModel, "刪除群組失敗：${error.message}")
    }
  }

  fun selectWidgetTarget(target: WidgetTargetOption) {
    val visibleTargetDeviceIds = target.deviceIds - hiddenDeviceIds.value
    val devices = selectedStructureVM.value?.deviceVMs?.value.orEmpty()
      .filter { it.id in visibleTargetDeviceIds }
    val snapshot = widgetSnapshotFromViewModels(devices)
    LightWidgetStore.selectTarget(
      context = homeApp.context,
      kind = target.kind,
      targetId = target.id,
      displayName = target.name,
      deviceIds = visibleTargetDeviceIds,
      isOn = snapshot.isOn,
      brightnessLevel = snapshot.brightnessLevel,
      colorHue = snapshot.hue,
      colorSaturation = snapshot.saturation,
      colorName = snapshot.colorName,
    )
    widgetStateMonitor?.restart()
    viewModelScope.launch { updateLightDialWidgets(homeApp.context) }
  }

  fun hideDevice(deviceId: String) {
    val updatedIds = _hiddenDeviceIds.value + deviceId
    _hiddenDeviceIds.value = updatedIds
    HiddenDevicesStore.save(homeApp.context, updatedIds)

    if (selectedDeviceVM.value?.id == deviceId) {
      selectedDeviceVM.value = null
    }

    val updatedFavorites = _favoriteDeviceIds.value - deviceId
    _favoriteDeviceIds.value = updatedFavorites
    FavoriteDevicesStore.save(homeApp.context, updatedFavorites)
    updateWidgetTargetForVisibility()
  }

  fun showDevice(deviceId: String) {
    val updatedIds = _hiddenDeviceIds.value - deviceId
    _hiddenDeviceIds.value = updatedIds
    HiddenDevicesStore.save(homeApp.context, updatedIds)
    updateWidgetTargetForVisibility()
  }

  private fun updateWidgetTargetForVisibility() {
    // The configured widget target keeps hidden devices; they are only skipped when the widget
    // resolves devices to command or read. Restart the monitor so it observes the new visible set.
    widgetStateMonitor?.restart()
    viewModelScope.launch { updateLightDialWidgets(homeApp.context) }
  }

  /**
   * Reports an error message to the UI layer via the main logger. This is used when an error occurs
   * outside of the standard flow emission (e.g., in onActivityResult).
   *
   * @param resultCode The result code of the failed activation.
   */
  fun handleActivationFailure(resultCode: Int) {
    val errorMessage = "Hub activation failed with result code: $resultCode"
    MainActivity.showError(this, errorMessage)
  }

  /** Starts the hub discovery process. */
  fun startHubDiscovery() {
    hubDiscoveryVM?.startDiscovery()
  }

  /** Shows automation candidates for the selected structure. */
  @OptIn(HomeExperimentalApi::class)
  fun showCandidates() {
    viewModelScope.launch {
      val candidateVMList: MutableList<CandidateViewModel> = mutableListOf()

      // Retrieve automation candidates for every device present in the selected structure:
      for (deviceVM in selectedStructureVM.value!!.deviceVMs.value) {

        // Check whether the device has a known type:
        if (deviceVM.type.value is UnknownDeviceType) continue
        // Retrieve a set of initial automation candidates from the device:
        val candidates: Set<NodeCandidate> = deviceVM.device.candidates().first()

        for (candidate in candidates) {
          // Check whether the candidate trait is supported:
          if (candidate.trait !in HomeModule_ProvideSupportedTraitsFactory().get()) continue
          // Check whether the candidate type is supported:
          when (candidate) {
            // Command candidate type:
            is CommandCandidate -> {
              // Check whether the command candidate has a supported command:
              if (candidate.commandDescriptor !in ActionViewModel.commandMap) continue
            }
            // Other candidate types are currently unsupported:
            else -> {
              continue
            }
          }
          candidateVMList.add(CandidateViewModel(candidate, deviceVM))
        }
      }

      // Store the ViewModels:
      selectedCandidateVMs.emit(candidateVMList)
    }
  }

  /**
   * Creates an automation from the currently selected draft.
   *
   * @param isPending A [MutableState] to track if the automation creation is in progress.
   */
  fun createAutomation(isPending: MutableState<Boolean>) {
    viewModelScope.launch {
      val structure: Structure = selectedStructureVM.value?.structure!!
      val draft: DraftAutomation = selectedDraftVM.value?.getDraftAutomation()!!
      isPending.value = true

      // Call Automations API to create an automation from a draft:
      try {
        structure.createAutomation(draft)
      } catch (e: Exception) {
        MainActivity.showError(this, e.toString())
        isPending.value = false
        return@launch
      }

      // Scrap the draft and automation candidates used in the process:
      selectedCandidateVMs.emit(null)
      selectedDraftVM.emit(null)
      isPending.value = false
    }
  }

  /** Create a room on the currently selected structure. */
  fun createRoomInSelectedStructure(name: String): Job = viewModelScope.launch {
    val vm = selectedStructureVM.value ?: return@launch
    vm.createRoom(name)
  }

  /** Delete a room from the currently selected structure. */
  fun deleteRoomFromSelectedStructure(roomVM: RoomViewModel): Job = viewModelScope.launch {
    val structureVM = selectedStructureVM.value ?: return@launch
    structureVM.deleteRoom(roomVM)
  }

  /**
   * Move a device into the given (non-null) room for the selected structure.
   *
   * @param device The [DeviceViewModel] of the device to move.
   * @param room The [RoomViewModel] of the room to move the device to.
   */
  fun moveDeviceToRoom(device: DeviceViewModel, room: RoomViewModel): Job = viewModelScope.launch {
    val vm = selectedStructureVM.value ?: return@launch
    vm.moveDeviceToRoom(device, room)
  }

  fun toggleFavorite(deviceId: String) {
    val nextIds = _favoriteDeviceIds.value.toMutableSet().apply {
      if (!add(deviceId)) remove(deviceId)
    }.toSet()
    _favoriteDeviceIds.value = nextIds
    FavoriteDevicesStore.save(homeApp.context, nextIds)
  }

  fun setGroupPower(deviceVMs: List<DeviceViewModel>, enabled: Boolean): Job = viewModelScope.launch {
    val deviceIds = deviceVMs.map { it.id }
    val commandToken = WidgetCommandCoordinator.begin(
      context = homeApp.context,
      reason = "app_group_power",
      actionDeviceIds = deviceIds,
      expectedIsOn = enabled,
    )
    val failures = DeviceGroupController.setPower(deviceVMs.map { it.device }, enabled)
    if (commandToken != null) {
      if (failures.size == deviceVMs.size) {
        WidgetCommandCoordinator.fail(
          homeApp.context,
          commandToken,
          "${failures.size} 台裝置控制失敗",
        )
      } else {
        WidgetCommandCoordinator.complete(
          context = homeApp.context,
          homeClient = homeApp.homeClient,
          token = commandToken,
          partialFailure = failures.takeIf { it.isNotEmpty() }?.let {
            "${it.size} 台裝置控制失敗"
          },
        )
      }
    }
    if (failures.isNotEmpty()) {
      MainActivity.showWarning(
        this@HomeAppViewModel,
        "${failures.size} 台裝置操作失敗：${failures.joinToString("、")}",
      )
    }
  }

  fun setGroupBrightness(deviceVMs: List<DeviceViewModel>, brightness: Float): Job = viewModelScope.launch {
    val brightnessLevel = (brightness * 254f).toInt().coerceIn(0, 254)
    val commandToken = WidgetCommandCoordinator.begin(
      context = homeApp.context,
      reason = "app_group_brightness",
      actionDeviceIds = deviceVMs.map { it.id },
      expectedIsOn = brightnessLevel > 0,
      expectedBrightnessLevel = brightnessLevel,
    )
    val failures = DeviceGroupController.setBrightness(deviceVMs.map { it.device }, brightness)
    if (commandToken != null) {
      if (failures.size == deviceVMs.size) {
        WidgetCommandCoordinator.fail(
          homeApp.context,
          commandToken,
          "${failures.size} 台燈具亮度調整失敗",
        )
      } else {
        WidgetCommandCoordinator.complete(
          context = homeApp.context,
          homeClient = homeApp.homeClient,
          token = commandToken,
          partialFailure = failures.takeIf { it.isNotEmpty() }?.let {
            "${it.size} 台燈具亮度調整失敗"
          },
        )
      }
    }
    if (failures.isNotEmpty()) {
      MainActivity.showWarning(
        this@HomeAppViewModel,
        "${failures.size} 台燈具亮度調整失敗：${failures.joinToString("、")}",
      )
    }
  }

  suspend fun syncWidgetAfterDeviceAction(
    deviceIds: Collection<String>,
    isOn: Boolean? = null,
    brightnessLevel: Int? = null,
    colorHue: Float? = null,
    colorSaturation: Float? = null,
    syncError: String? = null,
  ) {
    val context = homeApp.context
    val state = LightWidgetStore.load(context)
    val targetDeviceIds = state.deviceIds.ifEmpty { setOfNotNull(state.deviceId) }
    val matchesTarget = deviceIds.any { it in targetDeviceIds }
    if (targetDeviceIds.isEmpty() || !matchesTarget) {
      Log.w(
        TAG,
        "Widget action ignored because it does not match the configured target: " +
          "target=${state.targetKind}:${state.targetId}, targetDevices=$targetDeviceIds, actionDevices=$deviceIds",
      )
      return
    }

    runCatching {
      val commandToken = WidgetCommandCoordinator.begin(
        context = context,
        reason = "app_device_action",
        actionDeviceIds = deviceIds,
        expectedIsOn = isOn,
        expectedBrightnessLevel = brightnessLevel,
        expectedHue = colorHue,
        expectedSaturation = colorSaturation,
      ) ?: return
      WidgetCommandCoordinator.complete(
        context = context,
        homeClient = homeApp.homeClient,
        token = commandToken,
        partialFailure = syncError,
      )
    }.onFailure { error ->
      Log.w(TAG, "Unable to publish device action to the widget", error)
    }
  }

  /**
   * Creates and shows a predefined draft for an On/Off light automation.
   *
   * This draft requires at least two OnOff-capable lights in the selected structure. If fewer than
   * two are available, an error message is shown instead.
   */
  fun showPredefinedOnOffDraft() {
    viewModelScope.launch {
      val structureVM = selectedStructureVM.value ?: return@launch
      val repository = AutomationsRepository()

      val draftVM = repository.createOnOffLightAutomationDraft(structureVM.deviceVMs.value)

      if (draftVM == null) {
        MainActivity.showError(this, "Need at least two OnOff-capable lights in this structure.")
        return@launch
      }

      selectedDraftVM.emit(draftVM)
    }
  }

  /**
   * Creates and shows a predefined draft for the "Speaker and Fan" automation.
   *
   * This draft requires a speaker, fan, and plug in the selected structure. If any required device
   * is missing, an error message is shown instead.
   */
  fun showPredefinedSpeakerAndFanDraft() {
    viewModelScope.launch {
      val structureVM = selectedStructureVM.value ?: return@launch
      val repository = AutomationsRepository()

      // Pass the structure from selectedStructureVM
      val draftVM =
        repository.createSpeakerAndFanAutomationDraft(
          structureVM.deviceVMs.value,
          structureVM.structure,
        )

      if (draftVM == null) {
        MainActivity.showError(
          this,
          "This automation requires:\n• 1 Speaker\n• 1 Fan\n• 1 Smart Outlet\n\nPlease add these devices and try again.",
        )
        return@launch
      }

      selectedDraftVM.emit(draftVM)
    }
  }

  /**
   * Shows the predefined light and thermostat automation draft This creates a draft that turns on
   * lights and sets thermostat to auto when door is unlocked
   */
  suspend fun showPredefinedLightAndThermostatDraft() {
    val structureVM = selectedStructureVM.value ?: return
    val repository = AutomationsRepository()

    val draftVM = repository.createLightAndThermostatAutomationDraft(structureVM.deviceVMs.value)
    if (draftVM != null) {
      selectedDraftVM.emit(draftVM)
    }
  }

  /**
   * Creates and shows a predefined draft for the Window Covering automation.
   *
   * This draft requires a temperature sensor (or thermostat) and a window covering device. The
   * automation closes the window covering when temperature drops below 15°C and it's dark outside.
   */
  fun showPredefinedWindowCoveringDraft() {
    viewModelScope.launch {
      val structureVM = selectedStructureVM.value ?: return@launch
      val repository = AutomationsRepository()

      val draftVM =
        repository.createWindowCoveringAutomationDraft(
          structureVM.deviceVMs.value,
          structureVM.structure,
        )

      if (draftVM == null) {
        MainActivity.showError(
          this,
          "This automation requires:\n• 1 Temperature Sensor (or Thermostat)\n• 1 Window Covering\n\nPlease add these devices and try again.",
        )
        return@launch
      }

      selectedDraftVM.emit(draftVM)
    }
  }

  fun showPredefinedLightAndTVPeriodicDraft() {
    viewModelScope.launch {
      val structureVM = selectedStructureVM.value ?: return@launch
      val repository = AutomationsRepository()

      val draftVM =
        repository.createLightAndTVPeriodicAutomationDraft(
          structureVM.deviceVMs.value,
          structureVM.structure,
        )

      if (draftVM == null) {
        MainActivity.showError(
          this,
          "This automation requires:\n• At least 1 Light\n• 1 Occupancy Sensor\n• 1 Google TV\n\nPlease add these devices and try again.",
        )
        return@launch
      }

      selectedDraftVM.emit(draftVM)
    }
  }
}
