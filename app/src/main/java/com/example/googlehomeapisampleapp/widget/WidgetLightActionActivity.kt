package com.example.googlehomeapisampleapp.widget

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.googlehomeapisampleapp.HomeClientProvider
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WidgetLightActionActivity : ComponentActivity() {
  @Inject lateinit var homeClientProvider: HomeClientProvider

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    lifecycleScope.launch {
      try {
        if (!homeClientProvider.ensureWidgetPermissions(this@WidgetLightActionActivity)) {
          return@launch
        }
        executeWidgetPowerToggle(applicationContext, homeClientProvider.getClient())
      } catch (error: Exception) {
        Log.e(TAG, "Widget light action failed", error)
        Toast.makeText(
          this@WidgetLightActionActivity,
          "Google Home 控制失敗：${error.message ?: error.javaClass.simpleName}",
          Toast.LENGTH_SHORT,
        ).show()
      } finally {
        finish()
      }
    }
  }

  companion object {
    private const val TAG = "WidgetLightAction"
  }
}
