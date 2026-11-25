package br.com.controlegastos.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred

/**
 * Agenda o lembrete diário com WorkManager (ADR-0033). O disparo é inexato: o
 * sistema pode atrasá-lo alguns minutos, o que dispensa `SCHEDULE_EXACT_ALARM`.
 * Precisa ser criado no `onCreate` da activity, por causa do pedido de permissão.
 */
class AndroidReminderScheduler(activity: ComponentActivity) : ReminderScheduler {

    private val context: Context = activity.applicationContext
    private var pendingPermission: CompletableDeferred<Boolean>? = null
    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> pendingPermission?.complete(granted) }

    override fun canNotify(): Boolean = canNotify(context)

    override suspend fun requestPermission(): Boolean {
        if (canNotify()) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val result = CompletableDeferred<Boolean>().also { pendingPermission = it }
        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        return result.await() && canNotify()
    }

    override fun schedule(time: String) = scheduleDailyReminder(context, time)

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    companion object {
        const val WORK_NAME = "daily-reminder"
        const val KEY_TIME = "time"
        private val ZONE: ZoneId = ZoneId.of("America/Sao_Paulo")

        fun canNotify(context: Context): Boolean {
            val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        /** Agenda (ou reagenda) o próximo disparo no horário de Brasília. */
        fun scheduleDailyReminder(context: Context, time: String) {
            val now = ZonedDateTime.now(ZONE)
            val minutes = minutesUntilNextReminder(now.hour * 60 + now.minute, time)
            val delayMillis = TimeUnit.MINUTES.toMillis(minutes.toLong()) -
                TimeUnit.SECONDS.toMillis(now.second.toLong())
            val request = OneTimeWorkRequestBuilder<DailyReminderWorker>()
                .setInitialDelay(delayMillis.coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(KEY_TIME to time))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
