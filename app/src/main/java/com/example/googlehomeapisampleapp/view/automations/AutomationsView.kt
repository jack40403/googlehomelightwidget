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

package com.example.googlehomeapisampleapp.view.automations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.googlehomeapisampleapp.R
import com.example.googlehomeapisampleapp.view.devices.DevicesAccountButton
import com.example.googlehomeapisampleapp.view.shared.TabbedMenuView
import com.example.googlehomeapisampleapp.viewmodel.HomeAppViewModel
import com.example.googlehomeapisampleapp.viewmodel.automations.AutomationViewModel
import com.example.googlehomeapisampleapp.viewmodel.structures.StructureViewModel
import com.google.home.automation.Action
import com.google.home.automation.Starter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun AutomationsAccountButton(
  homeAppVM: HomeAppViewModel,
  onNavigateToUserManagement: () -> Unit = {},
  onNavigateToPresenceSettings: () -> Unit = {},
) {
  DevicesAccountButton(
    homeAppVM = homeAppVM,
    onNavigateToUserManagement = onNavigateToUserManagement,
    onNavigateToPresenceSettings = onNavigateToPresenceSettings,
  )
}

@Composable
fun AutomationsView(
  homeAppVM: HomeAppViewModel,
  onNavigateToUserManagement: () -> Unit = {},
  onNavigateToPresenceSettings: () -> Unit = {},
) {
  var structureMenuExpanded by remember { mutableStateOf(false) }
  val structureVMs = homeAppVM.structureVMs.collectAsState().value
  val selectedStructureVM = homeAppVM.selectedStructureVM.collectAsState().value
  val structureName = selectedStructureVM?.name ?: stringResource(R.string.automations_text_loading)

  Column(
    modifier = Modifier.fillMaxHeight().background(MaterialTheme.colorScheme.background),
  ) {
    AutomationsTopBar(
      title = "Automations",
      buttons = listOf {
        AutomationsAccountButton(
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
          Text("Make your home work for you", style = MaterialTheme.typography.headlineSmall)
          Text(
            "Create routines for the moments you repeat every day.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Box {
          OutlinedButton(
            onClick = { structureMenuExpanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
          ) {
            Text(
              structureName,
              modifier = Modifier.weight(1f),
              style = MaterialTheme.typography.titleMedium,
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
          }
          DropdownMenu(
            expanded = structureMenuExpanded,
            onDismissRequest = { structureMenuExpanded = false },
          ) {
            structureVMs.forEach { structure ->
              val presence by structure.presenceState.collectAsState()
              DropdownMenuItem(
                text = { Text("${structure.name} · $presence") },
                onClick = {
                  homeAppVM.setSelectedStructure(structure)
                  structureMenuExpanded = false
                },
              )
            }
          }
        }

        AutomationListComponent(homeAppVM)
      }

      ExtendedFloatingActionButton(
        onClick = { homeAppVM.showCandidates() },
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        icon = { Icon(Icons.Default.Add, contentDescription = null) },
        text = { Text("Create automation") },
      )
    }

    TabbedMenuView(homeAppVM)
  }
}

@Composable
fun AutomationListItem(automationVM: AutomationViewModel, homeAppVM: HomeAppViewModel) {
  val scope: CoroutineScope = rememberCoroutineScope()
  val automationName = automationVM.name.collectAsState().value
  val automationStarters: List<Starter> = automationVM.starters.collectAsState().value
  val automationActions: List<Action> = automationVM.actions.collectAsState().value

  Card(
    modifier = Modifier.fillMaxWidth().clickable {
      scope.launch { homeAppVM.selectedAutomationVM.emit(automationVM) }
    },
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(Modifier.fillMaxWidth().padding(18.dp)) {
      Text(automationName, style = MaterialTheme.typography.titleMedium)
      Text(
        "${automationStarters.size} starters · ${automationActions.size} actions",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
fun AutomationListComponent(homeAppVM: HomeAppViewModel) {
  val selectedStructureVM = homeAppVM.selectedStructureVM.collectAsState().value ?: return
  val selectedAutomationVMs = selectedStructureVM.automationVMs.collectAsState().value

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      stringResource(R.string.automations_title),
      modifier = Modifier.padding(start = 4.dp),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
    )
    if (selectedAutomationVMs.isEmpty()) {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Text(
          "No automations yet. Create one to get started.",
          modifier = Modifier.padding(20.dp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else {
      selectedAutomationVMs.forEach { automationVM ->
        AutomationListItem(automationVM, homeAppVM)
      }
    }
  }
}

@Composable
fun AutomationsTopBar(title: String, buttons: List<@Composable () -> Unit>) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 2.dp,
  ) {
    Box(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 8.dp)) {
      Text(
        title,
        modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
        style = MaterialTheme.typography.titleLarge,
      )
      Row(
        modifier = Modifier.align(Alignment.CenterEnd),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        buttons.forEach { button -> button() }
      }
    }
  }
}
