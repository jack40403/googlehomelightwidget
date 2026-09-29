package com.example.googlehomeapisampleapp.widget

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WidgetStateSyncScheduler {
  private const val PERIODIC_WORK_NAME = "google_home_widget_state_sync"
  private const val IMMEDIATE_WORK_NAME = "google_home_widget_state_sync_now"
  private const val SYNC_INTERVAL_MINUTES = 15L

  fun schedule(context: Context) {
    val request = PeriodicWorkRequestBuilder<WidgetStateSyncWorker>(
      SYNC_INTERVAL_MINUTES,
      TimeUnit.MINUTES,
    )
      .setConstraints(networkConstraints())
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
      .build()

    WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
      PERIODIC_WORK_NAME,
      ExistingPeriodicWorkPolicy.KEEP,
      request,
    )
  }

  fun requestNow(context: Context) {
    val request = OneTimeWorkRequestBuilder<WidgetStateSyncWorker>()
      .setConstraints(networkConstraints())
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
      .build()

    WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
      IMMEDIATE_WORK_NAME,
      ExistingWorkPolicy.REPLACE,
      request,
    )
  }

  fun cancel(context: Context) {
    WorkManager.getInstance(context.applicationContext).cancelUniqueWork(PERIODIC_WORK_NAME)
    WorkManager.getInstance(context.applicationContext).cancelUniqueWork(IMMEDIATE_WORK_NAME)
  }

  private fun networkConstraints(): Constraints = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build()
}
