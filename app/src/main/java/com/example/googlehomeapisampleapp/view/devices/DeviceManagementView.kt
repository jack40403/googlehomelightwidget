package com.example.googlehomeapisampleapp.view.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DevicesOther
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.googlehomeapisampleapp.viewmodel.HomeAppViewModel
import com.example.googlehomeapisampleapp.viewmodel.devices.DeviceViewModel
import com.example.googlehomeapisampleapp.viewmodel.structures.StructureViewModel

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DeviceManagementBottomSheet(
  homeAppVM: HomeAppViewModel,
  structureVM: StructureViewModel,
  onDismiss: () -> Unit,
) {
  val devices by structureVM.deviceVMs.collectAsState()
  val hiddenDeviceIds by homeAppVM.hiddenDeviceIds.collectAsState()
  var showHidden by rememberSaveable { mutableStateOf(false) }
  var deleteCandidate by rememberSaveable { mutableStateOf<String?>(null) }
  val deleteDevice = devices.firstOrNull { it.id == deleteCandidate }
  val displayedDevices = devices
    .filter { (it.id in hiddenDeviceIds) == showHidden }
    .sortedBy { it.name.value.lowercase() }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp)
        .verticalScroll(rememberScrollState()).padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text("裝置管理", style = MaterialTheme.typography.headlineSmall)
      Text(
        "隱藏只會從本程式移除，之後可以恢復。永久刪除會要求 Google Home 移除裝置。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
          selected = !showHidden,
          onClick = { showHidden = false },
          label = { Text("目前顯示 (${devices.count { it.id !in hiddenDeviceIds }})") },
        )
        FilterChip(
          selected = showHidden,
          onClick = { showHidden = true },
          label = { Text("已隱藏 (${hiddenDeviceIds.count { id -> devices.any { it.id == id } }})") },
        )
      }

      if (displayedDevices.isEmpty()) {
        Text(
          if (showHidden) "目前沒有隱藏裝置。" else "目前沒有可管理的裝置。",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      displayedDevices.forEach { device ->
        DeviceManagementCard(
          device = device,
          hidden = showHidden,
          onHide = { homeAppVM.hideDevice(device.id) },
          onShow = { homeAppVM.showDevice(device.id) },
          onDelete = { deleteCandidate = device.id },
        )
      }

      TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
        Text("關閉")
      }
    }
  }

  deleteDevice?.let { device ->
    val name by device.name.collectAsState()
    AlertDialog(
      onDismissRequest = { deleteCandidate = null },
      title = { Text("從 Google Home 移除？") },
      text = { Text("確定要永久移除「$name」嗎？這不是隱藏，之後可能需要重新設定裝置。") },
      confirmButton = {
        TextButton(
          onClick = {
            deleteCandidate = null
            device.deleteDevice { success ->
              if (success) homeAppVM.showDevice(device.id)
            }
          },
        ) {
          Text("永久移除")
        }
      },
      dismissButton = {
        TextButton(onClick = { deleteCandidate = null }) { Text("取消") }
      },
    )
  }
}

@Composable
private fun DeviceManagementCard(
  device: DeviceViewModel,
  hidden: Boolean,
  onHide: () -> Unit,
  onShow: () -> Unit,
  onDelete: () -> Unit,
) {
  val name by device.name.collectAsState()
  val typeName by device.typeName.collectAsState()
  val status by device.status.collectAsState()

  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(Icons.Outlined.DevicesOther, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
      Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
        Text(name, style = MaterialTheme.typography.titleMedium)
        Text(
          "$typeName · $status",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Column(horizontalAlignment = Alignment.End) {
        TextButton(onClick = if (hidden) onShow else onHide) {
          Text(if (hidden) "顯示" else "隱藏")
        }
        if (!hidden) {
          TextButton(onClick = onDelete) { Text("永久刪除") }
        }
      }
    }
  }
}
