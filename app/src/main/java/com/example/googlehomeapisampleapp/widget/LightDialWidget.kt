package com.example.googlehomeapisampleapp.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.wrapContentHeight
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.example.googlehomeapisampleapp.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class LightDialWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = LightDialWidget()

  override fun onEnabled(context: Context) {
    super.onEnabled(context)
    WidgetStateSyncScheduler.schedule(context)
    WidgetStateSyncScheduler.requestNow(context)
  }

  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray,
  ) {
    super.onUpdate(context, appWidgetManager, appWidgetIds)
    WidgetStateSyncScheduler.schedule(context)
  }

  override fun onDisabled(context: Context) {
    super.onDisabled(context)
    WidgetStateSyncScheduler.cancel(context)
  }
}

class LightDialWidget : GlanceAppWidget() {
  override val stateDefinition = PreferencesGlanceStateDefinition

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    provideContent {
      val publishedVersion = currentState(WIDGET_STATE_VERSION_KEY) ?: -1L
      val state = LightWidgetStore.load(context)
      Log.i(
        TAG,
      "Rendering widget id=$id: publishedVersion=$publishedVersion, " +
        "stateVersion=${state.stateVersion}, isOn=${state.isOn}, brightness=${state.brightnessLevel}, " +
        "hue=${state.colorHue}, saturation=${state.colorSaturation}, " +
        "displayColorArgb=${widgetDisplayColor(state).toArgb()}, " +
        "lastUpdatedAt=${state.lastUpdatedAt}, source=${state.source}, operationId=${state.operationId}",
      )
      LightDialWidgetContent(state)
    }
  }
}

suspend fun updateLightDialWidgets(
  context: Context,
  reason: String = "unspecified",
) {
  widgetUpdateMutex.withLock {
    val appContext = context.applicationContext
    val state = LightWidgetStore.load(appContext)
    val provider = ComponentName(appContext, LightDialWidgetReceiver::class.java)
    val widgetIds = AppWidgetManager.getInstance(appContext).getAppWidgetIds(provider)
    if (widgetIds.isEmpty()) {
      Log.i(TAG, "Skipped widget publication: no instances, reason=$reason")
      return@withLock
    }

    val widget = LightDialWidget()
    val glanceIds = GlanceAppWidgetManager(appContext).getGlanceIds(widget.javaClass)
    Log.i(
      TAG,
      "Publishing widget state: reason=$reason, stateVersion=${state.stateVersion}, " +
        "widgetIds=${widgetIds.size}, glanceIds=${glanceIds.size}, " +
        "isOn=${state.isOn}, brightness=${state.brightnessLevel}, " +
        "source=${state.source}, operationId=${state.operationId}, appWidgetIds=${widgetIds.joinToString()}",
    )
    if (glanceIds.isNotEmpty()) {
      updateEveryWidgetId(glanceIds) { glanceId ->
        val updateStartedAt = SystemClock.elapsedRealtime()
        Log.i(TAG, "Preparing Glance widget id=$glanceId, reason=$reason")
        updateAppWidgetState(appContext, glanceId) { preferences ->
          preferences[WIDGET_STATE_VERSION_KEY] = state.stateVersion
        }
        Log.i(TAG, "Updating Glance widget id=$glanceId, reason=$reason")
        widget.update(appContext, glanceId)
        Log.i(
          TAG,
          "Updated Glance widget id=$glanceId, reason=$reason, " +
            "durationMs=${SystemClock.elapsedRealtime() - updateStartedAt}",
        )
      }
    } else {
      Log.w(TAG, "Glance ids were empty; sending provider update fallback")
      appContext.sendBroadcast(
        Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
          component = provider
          putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
        },
      )
    }
    Log.i(TAG, "Published state to ${widgetIds.size} Light Dial widget instance(s)")
  }
}

private const val TAG = "LightDialWidget"
private val widgetUpdateMutex = Mutex()
private val WIDGET_STATE_VERSION_KEY = longPreferencesKey("widget_state_version")

internal suspend fun <T> updateEveryWidgetId(
  widgetIds: List<T>,
  update: suspend (T) -> Unit,
) {
  coroutineScope {
    widgetIds.map { widgetId ->
      async { update(widgetId) }
    }.awaitAll()
  }
}

@Composable
private fun LightDialWidgetContent(state: LightWidgetState) {
  val context = LocalContext.current
  val openAppComponent = ComponentName(context, MainActivity::class.java)

  if (state.deviceIds.isEmpty() && state.deviceId == null) {
    Column(
      modifier = GlanceModifier
        .fillMaxSize()
        .background(ColorProvider(Color(0xFF16201B)))
        .clickable(actionStartActivity(openAppComponent))
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("Google Home", style = TextStyle(color = ColorProvider(Color.White)))
      Spacer(GlanceModifier.height(8.dp))
      Text(
        "請先在 App 選擇 Widget 控制目標",
        style = TextStyle(color = ColorProvider(Color(0xFFC7D6CB))),
      )
    }
    return
  }

  val dialComponent = ComponentName(context, WidgetBrightnessOverlayActivity::class.java)
  val colorComponent = ComponentName(context, WidgetColorPickerActivity::class.java)
  val refreshComponent = ComponentName(context, WidgetRefreshActivity::class.java)
  val brightness = state.brightnessLevel?.coerceIn(0, 254)?.div(254f) ?: 0f
  val brightnessText = state.brightnessLevel?.let { "${(brightness * 100).roundToInt()}%" } ?: "--%"
  val displayColor = widgetDisplayColor(state)
  val syncText = when (state.syncStatus) {
    WidgetSyncStatus.SYNCING -> "正在同步 Google Home"
    WidgetSyncStatus.CONNECTED -> "Google Home 已同步"
    WidgetSyncStatus.ERROR -> "Google Home 同步失敗"
    WidgetSyncStatus.UNKNOWN -> "Google Home 尚未同步"
  }
  val syncTextColor = when (state.syncStatus) {
    WidgetSyncStatus.SYNCING -> Color(0xFFFFD166)
    WidgetSyncStatus.CONNECTED -> Color(0xFF9FE6C8)
    WidgetSyncStatus.ERROR -> Color(0xFFFFB4AB)
    WidgetSyncStatus.UNKNOWN -> Color(0xFFC7D6CB)
  }

  Column(
    modifier = GlanceModifier
      .fillMaxWidth()
      .wrapContentHeight()
      .background(ColorProvider(Color(0xFF213236)))
      .padding(6.dp),
  ) {
    Column(
      modifier = GlanceModifier.fillMaxWidth(),
      horizontalAlignment = Alignment.End,
    ) {
      Text(
        text = "↻ ${formatLastUpdatedAt(state.lastUpdatedAt)}",
        modifier = GlanceModifier
          .background(ColorProvider(if (state.isOn) Color(0xFF2A5661) else Color(0xFF283538)))
          .padding(horizontal = 8.dp, vertical = 4.dp)
          .clickable(actionStartActivity(refreshComponent)),
        style = TextStyle(color = ColorProvider(displayColor)),
      )
    }
    Text(
      state.displayName,
      modifier = GlanceModifier.clickable(actionStartActivity(openAppComponent)),
      style = TextStyle(color = ColorProvider(Color.White)),
    )
      Spacer(GlanceModifier.height(2.dp))
    Row(
      modifier = GlanceModifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
         modifier = GlanceModifier.size(148.dp),
        contentAlignment = Alignment.Center,
      ) {
        Image(
          provider = ImageProvider(createBrightnessDialBitmap(brightness, displayColor)),
          contentDescription = "亮度",
          modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(dialComponent)),
        )
        Row(
           modifier = GlanceModifier.padding(top = 84.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Image(
            provider = ImageProvider(createPowerButtonBitmap(state.isOn)),
            contentDescription = "開關",
            modifier = GlanceModifier
             .size(44.dp)
              .clickable(actionRunCallback<WidgetPowerToggleAction>()),
          )
           Spacer(GlanceModifier.width(10.dp))
          Image(
            provider = ImageProvider(createColorButtonBitmap(displayColor, state.isOn)),
            contentDescription = "燈色",
            modifier = GlanceModifier
             .size(44.dp)
              .clickable(actionStartActivity(colorComponent)),
          )
        }
      }
       Spacer(GlanceModifier.width(4.dp))
      Column(modifier = GlanceModifier.clickable(actionStartActivity(dialComponent))) {
        Text(
          text = brightnessText,
          style = TextStyle(color = ColorProvider(displayColor)),
        )
        Text(
          text = if (state.isOn) "已開啟 · ${state.deviceIds.size} 個燈具" else "已關閉 · ${state.deviceIds.size} 個燈具",
          style = TextStyle(color = ColorProvider(Color(0xFFC7D6CB))),
        )
        Text(
          state.colorName ?: "目前燈色",
          style = TextStyle(color = ColorProvider(displayColor)),
        )
        Text(
          syncText,
          style = TextStyle(color = ColorProvider(syncTextColor)),
        )
      }
    }
  }
}

private fun formatLastUpdatedAt(timestamp: Long?): String {
  if (timestamp == null) return "--:--"
  return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private fun createBrightnessDialBitmap(brightness: Float, color: Color): Bitmap {
  val size = 300
  val stroke = 24f
  val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
  val canvas = Canvas(bitmap)
  val center = size / 2f
  val radius = center - stroke
  val bounds = RectF(center - radius, center - radius, center + radius, center + radius)
  val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = stroke
    strokeCap = Paint.Cap.ROUND
    this.color = 0x5AFFFFFF.toInt()
  }
  val active = Paint(track).apply {
    val components = floatArrayOf(0f, 0f, 0f)
    android.graphics.Color.colorToHSV(color.toArgb(), components)
    this.color = android.graphics.Color.HSVToColor(floatArrayOf(components[0], components[1], components[2]))
  }
  canvas.drawArc(bounds, 135f, 270f, false, track)
  canvas.drawArc(bounds, 135f, 270f * brightness, false, active)
  val angle = Math.toRadians((135f + 270f * brightness).toDouble())
  val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = active.color }
  canvas.drawCircle(
    center + cos(angle).toFloat() * radius,
    center + sin(angle).toFloat() * radius,
    stroke / 2f,
    knob,
  )
  return bitmap
}

private fun createPowerButtonBitmap(isOn: Boolean): Bitmap {
  val size = 144
  val center = size / 2f
  val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
  val canvas = Canvas(bitmap)
  val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = if (isOn) 0xFF294A50.toInt() else 0xFF273238.toInt()
  }
  val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = if (isOn) 0xFFF5FFFD.toInt() else 0xFF91A6A8.toInt()
    style = Paint.Style.STROKE
    strokeWidth = 12f
    strokeCap = Paint.Cap.ROUND
  }
  canvas.drawCircle(center, center, center - 5f, background)
  canvas.drawArc(RectF(31f, 31f, size - 31f, size - 31f), 315f, 270f, false, icon)
  canvas.drawLine(center, 25f, center, center + 17f, icon)
  return bitmap
}

internal fun widgetDisplayColor(state: LightWidgetState): Color {
  if (!state.isOn) return Color(0xFF53656B)
  val brightness = state.brightnessLevel?.coerceIn(0, 254)?.div(254f) ?: 1f
  val hue = state.colorHue?.let { normalizeWidgetHue(it) } ?: 42f
  val saturation = state.colorSaturation?.coerceIn(0f, 1f) ?: 0.82f
  return Color.hsv(hue, saturation, brightness.coerceAtLeast(0.14f))
}

private fun normalizeWidgetHue(hue: Float): Float =
  ((hue % 360f) + 360f) % 360f

private fun createColorButtonBitmap(displayColor: Color, isOn: Boolean): Bitmap {
  val size = 144
  val center = size / 2f
  val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
  val canvas = Canvas(bitmap)
  val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFF294A50.toInt() }
  val colorRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = 10f
    strokeCap = Paint.Cap.BUTT
  }
  val colors = intArrayOf(
    0xFF61C7FF.toInt(),
    0xFF756BFF.toInt(),
    0xFFDF6CFF.toInt(),
    0xFFFF749E.toInt(),
    0xFFFFB35F.toInt(),
    0xFFFFE17A.toInt(),
    0xFF7DE6B0.toInt(),
  )
  canvas.drawCircle(center, center, center - 5f, background)
  colors.forEachIndexed { index, chipColor ->
    colorRing.color = chipColor
    val startAngle = -90f + index * 360f / colors.size + 3f
    canvas.drawArc(
      RectF(30f, 30f, size - 30f, size - 30f),
      startAngle,
      360f / colors.size - 6f,
      false,
      colorRing,
    )
  }
  val centerRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = 5f
    color = if (isOn) 0xFFF5FFFD.toInt() else 0xFF91A6A8.toInt()
  }
  canvas.drawCircle(center, center, 25f, centerRing)
  val selected = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = if (isOn) displayColor.toArgb() else 0xFF91A6A8.toInt()
  }
  canvas.drawCircle(center, center, 18f, selected)
  return bitmap
}
