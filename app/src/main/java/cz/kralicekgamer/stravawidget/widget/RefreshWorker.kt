package cz.kralicekgamer.stravawidget.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cz.kralicekgamer.stravawidget.data.MenuRepository
import java.util.concurrent.TimeUnit

/** Zhruba každou hodinu stáhne jídelníček a překreslí widget. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Bez připojení zůstanou poslední data; refresh widget i tak překreslí (přepnutí na další den).
        MenuRepository.refresh(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "strava_refresh"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<RefreshWorker>(1, TimeUnit.HOURS).build(),
            )
        }
    }
}
