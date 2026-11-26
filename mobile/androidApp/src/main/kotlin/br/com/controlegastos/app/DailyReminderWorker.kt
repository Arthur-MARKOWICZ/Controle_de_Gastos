package br.com.controlegastos.app

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * No horário do lembrete, pergunta à API se houve gasto hoje e só notifica se não
 * houve (spec ui-mobile/lembrete-diario). Sem sessão ou sem resposta, não notifica.
 * Depois agenda o disparo do dia seguinte.
 */
class DailyReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val time = inputData.getString(AndroidReminderScheduler.KEY_TIME) ?: return Result.success()
        val client = SharedApiClient.get(applicationContext)
        if (!client.hasStoredSession) return Result.success()

        val hasExpense = runCatching { AndroidApiGateway(client, applicationContext.cacheDir).hasExpenseToday() }.getOrNull()
        if (hasExpense == false && AndroidReminderScheduler.canNotify(applicationContext)) {
            notifyNoExpense()
        }
        AndroidReminderScheduler.scheduleDailyReminder(applicationContext, time)
        return Result.success()
    }

    @SuppressLint("MissingPermission") // canNotify() confere a permissão antes.
    private fun notifyNoExpense() {
        val manager = NotificationManagerCompat.from(applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Lembrete diário", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Aviso quando nenhum gasto foi lançado no dia" },
            )
        }
        val open = Intent(applicationContext, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_QUICK_EXPENSE, true)
        val pending = PendingIntent.getActivity(
            applicationContext,
            0,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Nenhum lançamento hoje")
            .setContentText("Toque para registrar os gastos do dia.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL_ID = "daily_reminder"
        const val NOTIFICATION_ID = 1001
    }
}
