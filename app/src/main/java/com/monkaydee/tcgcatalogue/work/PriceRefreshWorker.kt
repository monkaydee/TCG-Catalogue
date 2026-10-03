package com.monkaydee.tcgcatalogue.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.monkaydee.tcgcatalogue.TcgApp
import java.util.concurrent.TimeUnit

/** Refreshes all prices and records the day's portfolio value. */
class PriceRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as TcgApp).repository
        return runCatching { repo.refreshPrices() }
            .fold(
                onSuccess = {
                    AlertNotifier.show(applicationContext, repo.lastAlerts)
                    Result.success()
                },
                onFailure = { if (runAttemptCount < 3) Result.retry() else Result.failure() },
            )
    }

    companion object {
        private const val DAILY = "daily-price-refresh"
        private const val NOW = "price-refresh-now"
        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun scheduleDaily(context: Context) {
            val request = PeriodicWorkRequestBuilder<PriceRefreshWorker>(24, TimeUnit.HOURS)
                .setConstraints(network)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<PriceRefreshWorker>().setConstraints(network).build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, request)
        }

        fun observeNow(context: Context) = WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(NOW)
    }
}
