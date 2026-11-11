package com.monkaydee.tcgcatalogue.work

import android.content.Context
import androidx.work.*
import com.monkaydee.tcgcatalogue.TcgApp
import com.monkaydee.tcgcatalogue.data.SharedLearning
import java.util.concurrent.TimeUnit

class LearningWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as TcgApp).repository
        SharedLearning.retryDeletion(repo)
        SharedLearning.flush(repo)
        return if (SharedLearning.state.value.error != null) Result.retry() else Result.success()
    }
    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("recognition-learning", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<LearningWorker>(24, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }
    }
}
