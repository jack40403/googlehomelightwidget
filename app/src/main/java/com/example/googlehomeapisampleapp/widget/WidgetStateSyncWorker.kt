package com.example.googlehomeapisampleapp.widget

import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.googlehomeapisampleapp.HomeClientProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit

class WidgetStateSyncWorker(
    appContext: android.content.Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncWorkerEntryPoint {
        fun homeClientProvider(): HomeClientProvider
    }

    override suspend fun doWork(): Result {
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                SyncWorkerEntryPoint::class.java
            )
            val homeClient = entryPoint.homeClientProvider().getClient()
            withTimeout(TimeUnit.SECONDS.toMillis(20)) {
                LightDialWidget(homeClient).updateAll(applicationContext)
            }
            return Result.success()
        } catch (e: TimeoutCancellationException) {
            // Our own 20 s timeout: a slow Home API read, not a cancelled worker.
            Log.w("WidgetStateSyncWorker", "Widget refresh timed out; WorkManager will retry", e)
            return Result.retry()
        } catch (e: CancellationException) {
            // WorkManager stopped the worker (constraints lost, REPLACE, cancel): not a sync failure.
            throw e
        } catch (e: Exception) {
            Log.w("WidgetStateSyncWorker", "Widget refresh failed; WorkManager will retry", e)
            return Result.retry()
        }
    }
}
