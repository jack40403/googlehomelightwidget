package com.example.googlehomeapisampleapp.view.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.DevicesOther
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.googlehomeapisampleapp.view.lights.BrightnessDial
import com.example.googlehomeapisampleapp.viewmodel.HomeAppViewModel
import com.example.googlehomeapisampleapp.viewmodel.devices.DeviceViewModel
import com.example.googlehomeapisampleapp.viewmodel.structures.StructureViewModel
import com.google.home.DeviceType
import com.google.home.Trait
import com.google.home.matter.standard.FanControl
import com.google.home.matter.standard.LevelControl
import com.google.home.matter.standard.OnOff
import com.google.home.matter.standard.SpeakerDevice
import com.google.home.matter.standard.Thermostat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupControlBottomSheet(
  homeAppVM: HomeAppViewModel,
  structureVM: StructureViewModel,
  onDismiss: () -> Unit,
) {
  val rooms by structureVM.roomVMs.collectAsState()
  val allDevices by structureVM.deviceVMs.collectAsState()
  val hiddenDeviceIds by homeAppVM.hiddenDeviceIds.collectAsState()
  val favoriteDeviceIds by homeAppVM.favoriteDeviceIds.collectAsState()
  var source by rememberSaveable { mutableIntStateOf(0) }
  var roomMenuExpanded by remember { mutableStateOf(false) }
  var selectedRoomId by remember(structureVM.id, rooms) {
    mutableStateOf(rooms.firstOrNull()?.id)
  }

  val selectedRoom = rooms.firstOrNull { it.id == selectedRoomId } ?: rooms.firstOrNull()
  val selectedRoomName = selectedRoom?.name?.collectAsState()?.value
  val roomDevices = selectedRoom?.deviceVMs?.collectAsState()?.value
    ?.filterNot { it.id in hiddenDeviceIds }
    ?: emptyList()
  val visibleAllDevices = allDevices.filterNot { it.id in hiddenDeviceIds }
  val groupDevices = if (source == 0) roomDevices else visibleAllDevices.filter {
    it.id in favoriteDeviceIds
  }
  val deviceTraits = groupDevices.associate { deviceVM ->
    deviceVM.id to deviceVM.traits.collectAsState().value
  }
  val deviceTypes = groupDevices.associate { deviceVM ->
    deviceVM.id to deviceVM.type.collectAsState().value
  }
  val powerDevices = groupDevices.filter { deviceVM ->
    supportsGroupPower(deviceTraits[deviceVM.id].orEmpty())
  }
  val brightnessDevices = groupDevices.filter { deviceVM ->
    supportsGroupBrightness(deviceTypes[deviceVM.id], deviceTraits[deviceVM.id].orEmpty())
  }
  val brightnessDeviceIds = brightnessDevices.map { it.id }
  val averageBrightness = brightnessDevices.mapNotNull { deviceVM ->
    deviceTraits[deviceVM.id].orEmpty().filterIsInstance<LevelControl>().firstOrNull()
      ?.currentLevel?.toInt()
  }.average().takeIf { !it.isNaN() }?.toFloat()?.div(254f) ?: 0.5f
  var brightness by remember(brightnessDeviceIds) { mutableFloatStateOf(averageBrightness) }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(Modifier.weight(1f)) {
          Text("Group controls", style = MaterialTheme.typography.headlineSmall)
          Text(
            "Apply an action to a room or your favorites.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Icon(Icons.Outlined.DevicesOther, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
          selected = source == 0,
          onClick = { source = 0 },
          label = { Text("Room") },
        )
        FilterChip(
          selected = source == 1,
          onClick = { source = 1 },
          label = { Text("Favorites") },
          leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
        )
      }

      if (source == 0) {
        Box {
          FilterChip(
            selected = roomMenuExpanded,
            onClick = { roomMenuExpanded = true },
            label = { Text(selectedRoomName ?: "Choose a room") },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
          )
          DropdownMenu(
            expanded = roomMenuExpanded,
            onDismissRequest = { roomMenuExpanded = false },
          ) {
            rooms.forEach { room ->
              DropdownMenuItem(
                text = {
                  val roomName by room.name.collectAsState()
                  Text(roomName)
                },
                onClick = {
                  selectedRoomId = room.id
                  roomMenuExpanded = false
                },
              )
            }
          }
        }
      } else {
        Text(
          "Favorite devices",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
          Text(
            "${groupDevices.size} devices",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          if (groupDevices.isEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
              if (source == 0) "This room has no controllable devices."
              else "Add devices to favorites to control them here.",
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            groupDevices.forEach { deviceVM ->
              val deviceName by deviceVM.name.collectAsState()
              Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  Icons.Outlined.DevicesOther,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
                Text(deviceName, modifier = Modifier.padding(start = 10.dp))
              }
            }
          }
        }
      }

      if (powerDevices.isNotEmpty()) {
        Text("Power", style = MaterialTheme.typography.titleMedium)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Button(
            onClick = { homeAppVM.setGroupPower(powerDevices, true) },
            modifier = Modifier.weight(1f),
          ) {
            Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
            Text("Turn on", modifier = Modifier.padding(start = 6.dp))
          }
          Button(
            onClick = { homeAppVM.setGroupPower(powerDevices, false) },
            modifier = Modifier.weight(1f),
          ) {
            Text("Turn off")
          }
        }
      }

      if (brightnessDevices.isNotEmpty()) {
        Text("Brightness", style = MaterialTheme.typography.titleMedium)
        BrightnessDial(
          value = brightness,
          enabled = true,
          onValueChange = { brightness = it },
          onValueChangeFinished = { value ->
            brightness = value
            homeAppVM.setGroupBrightness(brightnessDevices, value)
          },
        )
      }

      TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
        Text("Close")
      }
      Spacer(Modifier.height(8.dp))
    }
  }
}

private fun supportsGroupPower(traits: List<Trait>): Boolean {
  return traits.any { trait ->
    trait is OnOff || trait is FanControl || trait is Thermostat
  }
}

private fun supportsGroupBrightness(type: DeviceType?, traits: List<Trait>): Boolean {
  return type?.factory != SpeakerDevice && traits.any { it is LevelControl }
}
