package com.example.googlehomeapisampleapp.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.example.googlehomeapisampleapp.HomeClientProvider
import com.google.home.matter.standard.OnOff
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull

class WidgetPowerToggleAction : ActionCallback {
    companion object {
        val DEVICE_ID_KEY = ActionParameters.Key<String>("device_id")
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PowerToggleEntryPoint {
        fun homeClientProvider(): HomeClientProvider
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val deviceId = parameters[DEVICE_ID_KEY] ?: return

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            PowerToggleEntryPoint::class.java
        )
        val homeClient = entryPoint.homeClientProvider().getClient()

        try {
            val structures = homeClient.structures().first()
            var targetTrait: OnOff? = null
            for (structure in structures) {
                val devices = structure.devices().first()
                val device = devices.find { it.id.toString() == deviceId }
                if (device != null) {
                    val types = device.types().first()
                    targetTrait = types.flatMap { it.traits() }.filterIsInstance<OnOff>().firstOrNull()
                    break
                }
            }

            if (targetTrait != null) {
                val currentState = targetTrait.onOff ?: return
                if (currentState) {
                    targetTrait.off()
                } else {
                    targetTrait.on()
                }
            }
        } catch (e: Exception) {
            Log.e("WidgetPowerToggle", "Failed to toggle Google Home light", e)
        } finally {
            runCatching { LightDialWidget(homeClient).updateAll(context.applicationContext) }
                .onFailure { Log.e("WidgetPowerToggle", "Failed to refresh widget after action", it) }
            WidgetSyncScheduler.enqueueNow(context)
        }
    }
}
