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

package com.example.googlehomeapisampleapp.view.devices

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.googlehomeapisampleapp.R
import com.example.googlehomeapisampleapp.view.shared.TabbedMenuView
import com.example.googlehomeapisampleapp.viewmodel.HomeAppViewModel
import com.example.googlehomeapisampleapp.viewmodel.devices.DeviceViewModel
import com.example.googlehomeapisampleapp.viewmodel.structures.RoomViewModel
import com.example.googlehomeapisampleapp.viewmodel.structures.StructureViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

const val TAG = "DevicesView"

@Composable
fun DevicesAccountButton(
  homeAppVM: HomeAppViewModel,
  onNavigateToUserManagement: () -> Unit = {},
  onNavigateToPresenceSettings: () -> Unit = {},
) {
  val context = LocalContext.current
  var expanded by remember { mutableStateOf(false) }

  Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
    IconButton(
      onClick = {
        homeAppVM.homeApp.permissionsManager.requestPermissions(isForceRefresh = true)
      },
    ) {
      Icon(
        imageVector = Icons.Default.AccountCircle,
        contentDescription = "Refresh permissions",
        tint = MaterialTheme.colorScheme.primary,
      )
    }
    IconButton(onClick = { expanded = true }) {
      Icon(Icons.Default.MoreVert, contentDescription = "More options")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      DropdownMenuItem(
        text = { Text("Revoke permissions") },
        onClick = {
          expanded = false
          val intent = Intent(
            Intent.ACTION_VIEW,
            "https://myaccount.google.com/u/2/connections?utm_source=3p".toUri(),
          )
          context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        },
      )
      DropdownMenuItem(
        text = { Text("Link GHP Playground") },
        onClick = {
          expanded = false
          homeAppVM.openCloudLinkingSheet()
        },
      )
      DropdownMenuItem(
        text = { Text("User management") },
        onClick = {
          expanded = false
          onNavigateToUserManagement()
        },
      )
      DropdownMenuItem(
        text = { Text("Presence settings") },
        onClick = {
          expanded = false
          onNavigateToPresenceSettings()
        },
      )
    }
  }
}

@Composable
fun DevicesView(
  homeAppVM: HomeAppViewModel,
  onRequestCreateRoom: () -> Unit = {},
  onRequestRoomSettings: (RoomViewModel) -> Unit = {},
  onRequestMoveDevice: (DeviceViewModel) -> Unit = {},
  onRequestAddHub: () -> Unit = {},
  onNavigateToUserManagement: () -> Unit = {},
  onNavigateToPresenceSettings: () -> Unit = {},
) {
  val scope: CoroutineScope = rememberCoroutineScope()
  val structureVMs = homeAppVM.structureVMs.collectAsState().value
  val selectedStructureVM = homeAppVM.selectedStructureVM.collectAsState().value
  val structureName = selectedStructureVM?.name ?: stringResource(R.string.devices_structure_loading)
  var structurePickerExpanded by remember { mutableStateOf(false) }
  var plusMenuExpanded by remember { mutableStateOf(false) }
  var showGroupControl by remember { mutableStateOf(false) }
  var showDeviceManagement by remember { mutableStateOf(false) }
  var showWidgetTargets by remember { mutableStateOf(false) }

  Column(
    modifier = Modifier.fillMaxHeight().background(MaterialTheme.colorScheme.background),
  ) {
    DevicesTopBar(
      title = "Devices",
      leftButton = {
        IconButton(onClick = { plusMenuExpanded = true }) {
          Icon(Icons.Default.Add, contentDescription = "Add")
        }
        DropdownMenu(
          expanded = plusMenuExpanded,
          onDismissRequest = { plusMenuExpanded = false },
        ) {
          DropdownMenuItem(
            text = { Text("Add room") },
            onClick = {
              plusMenuExpanded = false
              onRequestCreateRoom()
            },
          )
          DropdownMenuItem(
            text = { Text("Add hub") },
            onClick = {
              plusMenuExpanded = false
              onRequestAddHub()
            },
          )
        }
      },
      rightButtons = listOf {
        DevicesAccountButton(
          homeAppVM = homeAppVM,
          onNavigateToUserManagement = onNavigateToUserManagement,
          onNavigateToPresenceSettings = onNavigateToPresenceSettings,
        )
      },
    )

    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Column(Modifier.padding(horizontal = 4.dp)) {
          Text("Your home", style = MaterialTheme.typography.headlineSmall)
          Text(
            "Control lights, climate and connected devices in one place.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Box {
          OutlinedButton(
            onClick = { structurePickerExpanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
          ) {
            Icon(Icons.Outlined.Home, contentDescription = null)
            Text(
              text = structureName,
              modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
              style = MaterialTheme.typography.titleMedium,
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
          }
          DropdownMenu(
            expanded = structurePickerExpanded,
            onDismissRequest = { structurePickerExpanded = false },
          ) {
            structureVMs.forEach { structure ->
              val presence by structure.presenceState.collectAsState()
              DropdownMenuItem(
                text = { Text("${structure.name} · $presence") },
                onClick = {
                  homeAppVM.setSelectedStructure(structure)
                  structurePickerExpanded = false
                },
              )
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Button(
            onClick = { showGroupControl = true },
            enabled = selectedStructureVM != null,
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.medium,
          ) {
            Icon(Icons.Default.Tune, contentDescription = null)
            Text("群組控制", modifier = Modifier.padding(start = 6.dp))
          }
          OutlinedButton(
            onClick = { showDeviceManagement = true },
            enabled = selectedStructureVM != null,
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.medium,
          ) {
            Icon(Icons.Default.Settings, contentDescription = null)
            Text("裝置管理", modifier = Modifier.padding(start = 6.dp))
          }
        }

        OutlinedButton(
          onClick = { showWidgetTargets = true },
          enabled = selectedStructureVM != null,
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.medium,
        ) {
          Icon(Icons.Outlined.Home, contentDescription = null)
          Text("選擇 Widget 房間或群組", modifier = Modifier.padding(start = 8.dp))
        }

        DeviceListComponent(
          homeAppVM = homeAppVM,
          onRoomClick = onRequestRoomSettings,
          onDeviceLongPress = onRequestMoveDevice,
        )
      }
    }

    if (showGroupControl) {
      selectedStructureVM?.let { structure ->
        GroupControlBottomSheet(
          homeAppVM = homeAppVM,
          structureVM = structure,
          onDismiss = { showGroupControl = false },
        )
      }
    }

    if (showDeviceManagement) {
      selectedStructureVM?.let { structure ->
        DeviceManagementBottomSheet(
          homeAppVM = homeAppVM,
          structureVM = structure,
          onDismiss = { showDeviceManagement = false },
        )
      }
    }

    if (showWidgetTargets) {
      selectedStructureVM?.let { structure ->
        WidgetTargetBottomSheet(
          homeAppVM = homeAppVM,
          structureVM = structure,
          onDismiss = { showWidgetTargets = false },
        )
      }
    }

    TabbedMenuView(homeAppVM)
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeviceListItem(
  deviceVM: DeviceViewModel,
  homeAppVM: HomeAppViewModel,
  onLongPress: (DeviceViewModel) -> Unit,
) {
  val scope = rememberCoroutineScope()
  val deviceStatus = deviceVM.status.collectAsState().value
  val deviceName = deviceVM.name.collectAsState().value
  val favoriteDeviceIds by homeAppVM.favoriteDeviceIds.collectAsState()
  val isFavorite = deviceVM.id in favoriteDeviceIds

  Card(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).combinedClickable(
      onClick = { scope.launch { homeAppVM.selectedDeviceVM.emit(deviceVM) } },
      onLongClick = { onLongPress(deviceVM) },
    ),
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        modifier = Modifier.size(44.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
      ) {
        Icon(
          Icons.Outlined.Home,
          contentDescription = null,
          modifier = Modifier.padding(10.dp),
          tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }
      Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
        Text(
          deviceName,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
        )
        Text(
          deviceStatus,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
        )
      }
      IconButton(onClick = { homeAppVM.toggleFavorite(deviceVM.id) }) {
        Icon(
          imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
          contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
          tint = if (isFavorite) MaterialTheme.colorScheme.tertiary
          else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
fun RoomListItem(roomVM: RoomViewModel, onClick: (RoomViewModel) -> Unit) {
  val roomName by roomVM.name.collectAsState()
  Surface(
    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceVariant,
    onClick = { onClick(roomVM) },
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(Icons.Outlined.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
      Text(
        roomName,
        modifier = Modifier.weight(1f).padding(start = 12.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
      Text("Room", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
fun DeviceListComponent(
  homeAppVM: HomeAppViewModel,
  onRoomClick: (RoomViewModel) -> Unit,
  onDeviceLongPress: (DeviceViewModel) -> Unit,
) {
  val selectedStructureVM = homeAppVM.selectedStructureVM.collectAsState().value ?: return
  val hiddenDeviceIds = homeAppVM.hiddenDeviceIds.collectAsState().value
  val allDevices = selectedStructureVM.deviceVMs.collectAsState().value
  val selectedRoomVMs = selectedStructureVM.roomVMs.collectAsState().value
  val selectedDeviceVMsWithoutRooms = selectedStructureVM.deviceVMsWithoutRooms.collectAsState().value

  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    val visibleUnassignedDevices = selectedDeviceVMsWithoutRooms.filterNot { it.id in hiddenDeviceIds }
    if (allDevices.none { it.id !in hiddenDeviceIds }) {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Text(
          "No devices found yet.",
          modifier = Modifier.padding(20.dp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    selectedRoomVMs.forEach { roomVM ->
      val deviceVMsInRoom = roomVM.deviceVMs.collectAsState().value
        .filterNot { it.id in hiddenDeviceIds }
      if (deviceVMsInRoom.isNotEmpty()) {
        RoomListItem(roomVM, onClick = onRoomClick)
        deviceVMsInRoom.forEach { deviceVM ->
          DeviceListItem(deviceVM, homeAppVM, onLongPress = onDeviceLongPress)
        }
      }
    }

    if (visibleUnassignedDevices.isNotEmpty()) {
      Text(
        "Not assigned to a room",
        modifier = Modifier.padding(start = 8.dp, top = 18.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleMedium,
      )
      visibleUnassignedDevices.forEach { deviceVM ->
        DeviceListItem(deviceVM, homeAppVM, onLongPress = onDeviceLongPress)
      }
    }
  }
}

@Composable
fun DevicesTopBar(
  title: String,
  leftButton: (@Composable () -> Unit)? = null,
  rightButtons: List<@Composable () -> Unit>,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 2.dp,
  ) {
    Box(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 8.dp)) {
      Row(
        modifier = Modifier.align(Alignment.CenterStart),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        leftButton?.invoke()
      }
      Text(
        title,
        modifier = Modifier.align(Alignment.Center),
        style = MaterialTheme.typography.titleLarge,
      )
      Row(
        modifier = Modifier.align(Alignment.CenterEnd),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        rightButtons.forEach { button -> button() }
      }
    }
  }
}
