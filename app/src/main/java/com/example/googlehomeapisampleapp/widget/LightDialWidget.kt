package com.example.googlehomeapisampleapp.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.WorkManager
import com.example.googlehomeapisampleapp.HomeClientProvider
import com.example.googlehomeapisampleapp.MainActivity
import com.google.home.HomeClient
import com.google.home.matter.standard.OnOff
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val PERIODIC_WIDGET_SYNC = "google_home_widget_state_sync"
private const val IMMEDIATE_WIDGET_SYNC = "google_home_widget_state_sync_now"

@AndroidEntryPoint
class LightDialWidgetReceiver : GlanceAppWidgetReceiver() {
  @Inject lateinit var homeClientProvider: HomeClientProvider

  override val glanceAppWidget: GlanceAppWidget
    get() = LightDialWidget(homeClientProvider.getClient())

  override fun onEnabled(context: Context) {
    super.onEnabled(context)
    WidgetSyncScheduler.schedule(context)
  }

  override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
    super.onUpdate(context, appWidgetManager, appWidgetIds)
    // Also create the periodic request here so existing widgets get background sync after app updates.
    WidgetSyncScheduler.schedule(context)
  }

  override fun onDisabled(context: Context) {
    WidgetSyncScheduler.cancel(context)
    super.onDisabled(context)
  }
}

internal object WidgetSyncScheduler {
  fun schedule(context: Context) {
    val manager = WorkManager.getInstance(context.applicationContext)
    val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    val periodic = PeriodicWorkRequestBuilder<WidgetStateSyncWorker>(15, TimeUnit.MINUTES)
      .setConstraints(constraints)
      .build()
    manager.enqueueUniquePeriodicWork(PERIODIC_WIDGET_SYNC, ExistingPeriodicWorkPolicy.KEEP, periodic)
    enqueueNow(context)
  }

  fun enqueueNow(context: Context) {
    val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    val request = OneTimeWorkRequestBuilder<WidgetStateSyncWorker>()
      .setConstraints(constraints)
      .build()
    WorkManager.getInstance(context.applicationContext)
      .enqueueUniqueWork(IMMEDIATE_WIDGET_SYNC, ExistingWorkPolicy.REPLACE, request)
  }

  fun cancel(context: Context) {
    WorkManager.getInstance(context.applicationContext).apply {
      cancelUniqueWork(PERIODIC_WIDGET_SYNC)
      cancelUniqueWork(IMMEDIATE_WIDGET_SYNC)
    }
  }
}

internal class LightDialWidget(private val homeClient: HomeClient) : GlanceAppWidget() {
  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val stateCache = context.getSharedPreferences("light_widget_state", Context.MODE_PRIVATE)
    val cachedLightName = stateCache.getString("light_name", "房間燈光") ?: "房間燈光"
    val cachedLightId = stateCache.getString("device_id", null)
    val cachedStatusText = stateCache.getString("status_text", "No lights found") ?: "No lights found"
    val cachedHasLight = stateCache.getBoolean("has_light", cachedLightId != null)
    val cachedIsPoweredOn = stateCache.getBoolean("is_powered_on", false)
    val hasCachedState = stateCache.getLong("synced_at", 0L) > 0L
    var lightName = cachedLightName
    var lightId = cachedLightId
    var statusText = cachedStatusText
    var hasLight = cachedHasLight
    var isPoweredOn = cachedIsPoweredOn
    var hasError = false

    try {
      val structures = homeClient.structures().first()
      lightName = "房間燈光"
      lightId = null
      statusText = "No lights found"
      hasLight = false
      isPoweredOn = false
      search@ for (structure in structures) {
        for (device in structure.devices().first()) {
          val onOff = device.types().first().flatMap { it.traits() }
            .filterIsInstance<OnOff>()
            .firstOrNull() ?: continue
          lightName = device.name
          lightId = device.id.toString()
          hasLight = true
          val value = onOff.onOff
          isPoweredOn = value == true
          statusText = when (value) {
            true -> "On"
            false -> "Off"
            null -> "Unknown state"
          }
          break@search
        }
      }
      stateCache.edit()
        .putString("light_name", lightName)
        .putString("device_id", lightId)
        .putString("status_text", statusText)
        .putBoolean("has_light", hasLight)
        .putBoolean("is_powered_on", isPoweredOn)
        .putLong("synced_at", System.currentTimeMillis())
        .apply()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e("LightDialWidget", "Failed to fetch light state", e)
      hasError = true
      lightName = cachedLightName
      lightId = cachedLightId
      statusText = if (hasCachedState) "顯示上次同步狀態 · $cachedStatusText" else "Sync failed"
      hasLight = cachedHasLight
      isPoweredOn = cachedIsPoweredOn
    }

    provideContent {
      LightDialWidgetContent(lightName, lightId, statusText, hasLight, isPoweredOn, hasError)
    }
  }
}

@Composable
private fun LightDialWidgetContent(
  lightName: String,
  lightId: String?,
  statusText: String,
  hasLight: Boolean,
  isPoweredOn: Boolean,
  hasError: Boolean,
) {
  val iconColor = if (isPoweredOn) Color(0xFFFFD166) else if (hasError || !hasLight) Color(0xFF888888) else Color(0xFF555555)

  Column(
    modifier = GlanceModifier
      .fillMaxSize()
      .background(ColorProvider(Color(0xFF16201B)))
      .clickable(actionStartActivity<MainActivity>())
      .padding(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(if (hasError || !hasLight) "Google Home" else lightName, style = TextStyle(color = ColorProvider(Color.White)))
    Spacer(GlanceModifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = if (hasError) "⚠" else "◉",
        style = TextStyle(color = ColorProvider(iconColor)),
      )
      Spacer(GlanceModifier.width(12.dp))
      Column {
        Text(statusText, style = TextStyle(color = ColorProvider(Color.White)))
        Text("狀態", style = TextStyle(color = ColorProvider(Color(0xFFC7D6CB))))
      }
    }

    if (lightId != null) {
      val deviceParams = actionParametersOf(WidgetPowerToggleAction.DEVICE_ID_KEY to lightId)
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = if (isPoweredOn) "關燈" else "開燈",
          modifier = GlanceModifier.clickable(actionRunCallback<WidgetPowerToggleAction>(deviceParams)).padding(8.dp),
          style = TextStyle(color = ColorProvider(Color.White)),
        )
        Text(
          text = "亮度／色彩",
          modifier = GlanceModifier.clickable(
            actionStartActivity<WidgetBrightnessDialActivity>(
              actionParametersOf(WidgetBrightnessDialActivity.DEVICE_ID_KEY to lightId)
            )
          ).padding(8.dp),
          style = TextStyle(color = ColorProvider(Color.White)),
        )
        Text(
          text = "色彩",
          modifier = GlanceModifier.clickable(
            actionStartActivity<WidgetColorPickerActivity>(
              actionParametersOf(WidgetColorPickerActivity.DEVICE_ID_KEY to lightId)
            )
          ).padding(8.dp),
          style = TextStyle(color = ColorProvider(Color.White)),
        )
      }
    }

    Text(
      text = if (hasError) "同步失敗 · 點此重試" else "立即同步",
      modifier = GlanceModifier.clickable(actionStartActivity<WidgetRefreshActivity>()).padding(8.dp),
      style = TextStyle(color = ColorProvider(Color(0xFFC7D6CB))),
    )
  }
}
