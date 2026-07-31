// Fan-out helper: one call updates every installed instance of both widgets.
// Cheap and safe to call with zero widgets pinned (Glance's updateAll is a
// no-op then) — so app screens can call it after every snapshot write
// without checking whether anyone actually has a widget on their home screen.
package org.nuruplace.member.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

object WidgetRefresher {
    private const val PERIODIC_WORK_NAME = "nuru-widget-periodic-refresh"

    suspend fun updateAll(context: Context) {
        PathwayGlanceWidget().updateAll(context)
        RadioGlanceWidget().updateAll(context)
    }

    /** Belt-and-suspenders timeline refresh — 15 minutes is WorkManager's own
     *  periodic-work floor, which conveniently matches iOS's intended 15-min
     *  widget timeline cadence. Re-renders from whatever snapshot is already
     *  on disk; still never touches the network (the app's own screens are
     *  what keep the snapshot fresh whenever they're open). Safe to call on
     *  every app start — KEEP means a second enqueue is a no-op. */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class WidgetRefreshWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        WidgetRefresher.updateAll(applicationContext)
        return Result.success()
    }
}
