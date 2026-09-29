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

package com.example.googlehomeapisampleapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.example.googlehomeapisampleapp.cloudlinking.CloudLinkingAuthCoordinator
import com.example.googlehomeapisampleapp.cloudlinking.CurrentStructureRepository
import com.example.googlehomeapisampleapp.view.HomeAppView
import com.example.googlehomeapisampleapp.viewmodel.HomeAppViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The main activity of the Google Home API Sample App. This activity is responsible for
 * initializing the [HomeApp] and displaying the [HomeAppView].
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  @Inject lateinit var homeClientProvider: HomeClientProvider
  @Inject lateinit var currentStructureRepository: CurrentStructureRepository
  @Inject lateinit var authCoordinator: CloudLinkingAuthCoordinator
  lateinit var homeAppVM: HomeAppViewModel

  /**
   * Called when the activity is first created.
   *
   * @param savedInstanceState If the activity is being re-initialized after previously being shut
   *   down then this Bundle contains the data it most recently supplied in [onSaveInstanceState].
   *   Otherwise it is null.
   */
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.d(TAG, "onCreate of MainActivity instance of MainActivity ${this@MainActivity}")
    // Initialize logger for logging and displaying messages:
    logger = Logger(this)

    // Initialize the main app class to interact with the APIs:
    val homeApp = HomeApp(baseContext, lifecycleScope, this, homeClientProvider)
    Log.d(TAG, "homeApp created")
    homeAppVM = HomeAppViewModel(homeApp, currentStructureRepository)
    Log.d(TAG, "homeAppVM created")

    // Call to make the app allocate the entire screen:
    enableEdgeToEdge()
    // Set the content of the screen to display the app:
    setContent { HomeAppView(homeAppVM) }

    authCoordinator.onOAuthRedirect(intent)
    handleIntent(intent)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    Log.d(TAG, "onNewIntent received")
    setIntent(intent)
    authCoordinator.onOAuthRedirect(intent)
    handleIntent(intent)
  }

  private fun handleIntent(intent: Intent) {
    if (intent.action == ACTION_REQUEST_HOME_PERMISSIONS) {
      intent.action = null
      homeAppVM.homeApp.permissionsManager.requestPermissions()
    }
  }

  companion object {
    const val TAG = "MainActivity"
    const val ACTION_REQUEST_HOME_PERMISSIONS =
      "com.example.googlehomeapisampleapp.action.REQUEST_HOME_PERMISSIONS"
    private lateinit var logger: Logger

    /**
     * Shows an error message to the user and logs it.
     *
     * @param caller The object calling this function.
     * @param message The error message to display.
     */
    fun showError(caller: Any, message: String) {
      logger.log(caller, message, Logger.LogLevel.ERROR)
    }

    /**
     * Shows a warning message to the user and logs it.
     *
     * @param caller The object calling this function.
     * @param message The warning message to display.
     */
    fun showWarning(caller: Any, message: String) {
      logger.log(caller, message, Logger.LogLevel.WARNING)
    }

    /**
     * Shows an info message to the user and logs it.
     *
     * @param caller The object calling this function.
     * @param message The info message to display.
     */
    fun showInfo(caller: Any, message: String) {
      logger.log(caller, message, Logger.LogLevel.INFO)
    }

    /**
     * Logs a debug message.
     *
     * @param caller The object calling this function.
     * @param message The debug message to log.
     */
    fun showDebug(caller: Any, message: String) {
      logger.log(caller, message, Logger.LogLevel.DEBUG)
    }
  }
}

/*  Logger - Utility class for logging and displaying messages
 *   This helps us to communicate unexpected states on screen, as well as to record them appropriately
 *   so when it comes you to report an issue we can make sure the states are captured in adb logs.
 *  */
class Logger(val activity: ComponentActivity) {

  enum class LogLevel {
    ERROR,
    WARNING,
    INFO,
    DEBUG,
  }

  fun log(caller: Any, message: String, level: LogLevel) {
    // Log the message in accordance to its level:
    when (level) {
      LogLevel.ERROR -> Log.e(caller.javaClass.name, message)
      LogLevel.WARNING -> Log.w(caller.javaClass.name, message)
      LogLevel.INFO -> Log.i(caller.javaClass.name, message)
      LogLevel.DEBUG -> Log.d(caller.javaClass.name, message)
    }
    // For levels above debug, Also show the message on screen:
    if (level != LogLevel.DEBUG)
      Toast.makeText(activity.baseContext, message, Toast.LENGTH_LONG).show()
  }
}
