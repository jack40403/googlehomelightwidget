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

package com.example.googlehomeapisampleapp.view.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.googlehomeapisampleapp.viewmodel.HomeAppViewModel

@Composable
fun TabbedMenuView(homeAppVM: HomeAppViewModel) {
  val selectedTab = homeAppVM.selectedTab.collectAsState().value

  Column(Modifier.imePadding()) {
    NavigationBar(
      containerColor = MaterialTheme.colorScheme.surface,
      tonalElevation = 4.dp,
    ) {
      NavigationBarItem(
        selected = selectedTab == HomeAppViewModel.NavigationTab.DEVICES,
        onClick = { homeAppVM.selectedTab.value = HomeAppViewModel.NavigationTab.DEVICES },
        icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
        label = { Text("Devices") },
      )
      NavigationBarItem(
        selected = selectedTab == HomeAppViewModel.NavigationTab.AUTOMATIONS,
        onClick = { homeAppVM.selectedTab.value = HomeAppViewModel.NavigationTab.AUTOMATIONS },
        icon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
        label = { Text("Automations") },
      )
    }
    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
  }
}
