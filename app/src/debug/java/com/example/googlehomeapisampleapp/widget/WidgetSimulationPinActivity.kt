package com.example.googlehomeapisampleapp.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout

class WidgetSimulationPinActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(
      LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        addView(
          Button(this@WidgetSimulationPinActivity).apply {
            text = "Pin simulated light widget"
            setOnClickListener { requestWidgetPin() }
          },
        )
        addView(
          Button(this@WidgetSimulationPinActivity).apply {
            text = "Open simulated brightness panel"
            setOnClickListener {
              startActivity(
                Intent(
                  this@WidgetSimulationPinActivity,
                  WidgetBrightnessOverlayActivity::class.java,
                ).putExtra(WidgetBrightnessOverlayActivity.EXTRA_DEBUG_SIMULATION, true),
              )
            }
          },
        )
        addView(
          Button(this@WidgetSimulationPinActivity).apply {
            text = "Open simulated color panel"
            setOnClickListener {
              startActivity(
                Intent(
                  this@WidgetSimulationPinActivity,
                  WidgetColorPickerActivity::class.java,
                ).putExtra(WidgetColorPickerActivity.EXTRA_DEBUG_SIMULATION, true),
              )
            }
          },
        )
      },
    )
  }

  private fun requestWidgetPin() {
    val manager = AppWidgetManager.getInstance(applicationContext)
    val provider = ComponentName(applicationContext, LightDialWidgetReceiver::class.java)
    val supported = manager.isRequestPinAppWidgetSupported
    val requested = supported && manager.requestPinAppWidget(provider, null, null)
    Log.i(TAG, "Requested debug widget pin: supported=$supported, requested=$requested")
  }

  companion object {
    private const val TAG = "WidgetSimulation"
  }
}
