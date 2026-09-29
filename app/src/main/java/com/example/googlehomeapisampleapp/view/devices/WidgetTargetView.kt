package com.example.googlehomeapisampleapp.view.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.googlehomeapisampleapp.widget.GoogleHomeGroupOption
import com.example.googlehomeapisampleapp.widget.LightWidgetStore
import com.example.googlehomeapisampleapp.widget.WidgetTargetKind
import com.example.googlehomeapisampleapp.widget.WidgetTargetOption
import com.example.googlehomeapisampleapp.viewmodel.HomeAppViewModel
import com.example.googlehomeapisampleapp.viewmodel.devices.DeviceViewModel
import com.example.googlehomeapisampleapp.viewmodel.structures.RoomViewModel
import com.example.googlehomeapisampleapp.viewmodel.structures.StructureViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetTargetBottomSheet(
  homeAppVM: HomeAppViewModel,
  structureVM: StructureViewModel,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val rooms by structureVM.roomVMs.collectAsState()
  val allDevices by structureVM.deviceVMs.collectAsState()
  val hiddenDeviceIds by homeAppVM.hiddenDeviceIds.collectAsState()
  val favorites by homeAppVM.favoriteDeviceIds.collectAsState()
  val groups by homeAppVM.deviceGroups.collectAsState()
  val visibleDevices = allDevices.filterNot { it.id in hiddenDeviceIds }
  var source by rememberSaveable { mutableIntStateOf(0) }
  var editorGroup by remember { mutableStateOf<GoogleHomeGroupOption?>(null) }
  var showEditor by remember { mutableStateOf(false) }
  val savedTarget = remember(context) { LightWidgetStore.load(context) }

  val options = when (source) {
    0 -> rooms.mapNotNull { room ->
      val deviceIds = visibleDevices.filter { it.device.roomId?.id == room.id }.map { it.id }.toSet()
      if (deviceIds.isEmpty()) null
      else WidgetTargetOption(WidgetTargetKind.ROOM, room.id, room.name.value, deviceIds)
    }
    1 -> groups.mapNotNull { group ->
      val visibleIds = group.deviceIds - hiddenDeviceIds
      if (visibleIds.isEmpty()) null
      else WidgetTargetOption(WidgetTargetKind.GROUP, group.id, group.name, visibleIds)
    }
    2 -> listOf(
      WidgetTargetOption(
        kind = WidgetTargetKind.FAVORITES,
        id = "favorites",
        name = "我的最愛",
        deviceIds = visibleDevices.filter { it.id in favorites }.map { it.id }.toSet(),
      ),
    )
    else -> visibleDevices.map { device ->
      WidgetTargetOption(
        kind = WidgetTargetKind.DEVICE,
        id = device.id,
        name = device.name.value,
        deviceIds = setOf(device.id),
      )
    }
  }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp)
        .verticalScroll(rememberScrollState()).padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text("Widget 控制目標", style = MaterialTheme.typography.headlineSmall)
          Text(
            "可選房間、Google Home 群組或我的最愛；旋鈕與燈色會套用到其中支援的燈具。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
      }

      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilterChip(selected = source == 0, onClick = { source = 0 }, label = { Text("房間") })
        FilterChip(selected = source == 1, onClick = { source = 1 }, label = { Text("Google 群組") })
        FilterChip(selected = source == 2, onClick = { source = 2 }, label = { Text("我的最愛") })
        FilterChip(selected = source == 3, onClick = { source = 3 }, label = { Text("單一裝置") })
      }

      if (source == 1) {
        Button(
          onClick = {
            editorGroup = null
            showEditor = true
          },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Icon(Icons.Default.Add, contentDescription = null)
          Text("在 App 建立 Google Home 群組", modifier = Modifier.padding(start = 6.dp))
        }
      }

      if (options.isEmpty()) {
        Text(
          when (source) {
            0 -> "目前沒有房間"
            1 -> "目前沒有 Google Home 群組"
            2 -> "目前沒有我的最愛裝置"
            else -> "目前沒有可加入的裝置"
          },
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      options.forEach { option ->
        val selected = savedTarget.targetKind == option.kind && savedTarget.targetId == option.id
        Card(
          colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
          ),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(Modifier.weight(1f)) {
              Text(option.name, style = MaterialTheme.typography.titleMedium)
              Text(
                "${option.deviceIds.size} 台裝置",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            if (option.kind == WidgetTargetKind.GROUP) {
              IconButton(onClick = {
                editorGroup = groups.firstOrNull { it.id == option.id }
                showEditor = editorGroup != null
              }) {
                Icon(Icons.Default.Edit, contentDescription = "編輯群組")
              }
            }
            TextButton(
              onClick = {
                homeAppVM.selectWidgetTarget(option)
                onDismiss()
              },
            ) {
              Text(if (selected) "目前使用" else "加入 Widget")
            }
          }
        }
      }
      TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("關閉") }
    }
  }

  if (showEditor) {
    WidgetGroupEditorDialog(
      devices = visibleDevices,
      initialGroup = editorGroup,
      onDismiss = { showEditor = false },
      onCreate = { name, ids ->
        homeAppVM.createDeviceGroup(name, ids)
        showEditor = false
      },
      onUpdate = { group, name, ids ->
        homeAppVM.updateDeviceGroup(group, name, ids)
        showEditor = false
      },
      onDelete = { group ->
        homeAppVM.deleteDeviceGroup(group)
        showEditor = false
      },
    )
  }
}

@Composable
private fun WidgetGroupEditorDialog(
  devices: List<DeviceViewModel>,
  initialGroup: GoogleHomeGroupOption?,
  onDismiss: () -> Unit,
  onCreate: (String, Set<String>) -> Unit,
  onUpdate: (GoogleHomeGroupOption, String, Set<String>) -> Unit,
  onDelete: (GoogleHomeGroupOption) -> Unit,
) {
  var name by remember(initialGroup?.id) { mutableStateOf(initialGroup?.name.orEmpty()) }
  var selectedIds by remember(initialGroup?.id) { mutableStateOf(initialGroup?.deviceIds.orEmpty()) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (initialGroup == null) "建立 Google Home 群組" else "編輯 Google Home 群組") },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("群組名稱") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        devices.forEach { device ->
          val deviceName by device.name.collectAsState()
          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
              checked = device.id in selectedIds,
              onCheckedChange = { checked ->
                selectedIds = if (checked) selectedIds + device.id else selectedIds - device.id
              },
            )
            Text(deviceName)
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          if (initialGroup == null) onCreate(name, selectedIds)
          else onUpdate(initialGroup, name, selectedIds)
        },
        enabled = name.trim().isNotEmpty() && selectedIds.isNotEmpty(),
      ) { Text("儲存") }
    },
    dismissButton = {
      Row {
        if (initialGroup != null) {
          TextButton(onClick = { onDelete(initialGroup) }) { Text("刪除") }
        }
        TextButton(onClick = onDismiss) { Text("取消") }
      }
    },
  )
}
