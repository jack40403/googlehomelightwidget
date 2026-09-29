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

package com.example.googlehomeapisampleapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

// Dark theme default color palette:
private val DarkColorScheme = darkColorScheme(
  primary = TealPrimaryDark,
  onPrimary = Color(0xFF003737),
  primaryContainer = TealPrimaryContainerDark,
  onPrimaryContainer = Color(0xFF6FF7F5),
  secondary = SlateSecondaryDark,
  tertiary = CoralTertiaryDark,
  background = AppBackgroundDark,
  surface = AppSurfaceDark,
  surfaceVariant = Color(0xFF3F484A),
)

// Light theme default color palette:
private val LightColorScheme = lightColorScheme(
  primary = TealPrimary,
  onPrimary = Color.White,
  primaryContainer = TealPrimaryContainer,
  onPrimaryContainer = Color(0xFF002020),
  secondary = SlateSecondary,
  tertiary = CoralTertiary,
  background = AppBackground,
  surface = Color.White,
  surfaceVariant = AppSurfaceVariant,
)

private val AppShapes = Shapes(
  extraSmall = RoundedCornerShape(8.dp),
  small = RoundedCornerShape(12.dp),
  medium = RoundedCornerShape(20.dp),
  large = RoundedCornerShape(28.dp),
  extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun GoogleHomeAPISampleAppTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  MaterialTheme(
    colorScheme = colorScheme,
    shapes = AppShapes,
    typography = Typography,
    content = content
  )
}
