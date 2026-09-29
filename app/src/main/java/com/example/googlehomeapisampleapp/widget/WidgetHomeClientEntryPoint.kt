package com.example.googlehomeapisampleapp.widget

import com.example.googlehomeapisampleapp.HomeClientProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetHomeClientEntryPoint {
  fun homeClientProvider(): HomeClientProvider
}

fun widgetHomeClientProvider(context: android.content.Context): HomeClientProvider =
  EntryPointAccessors.fromApplication(
    context.applicationContext,
    WidgetHomeClientEntryPoint::class.java,
  ).homeClientProvider()
