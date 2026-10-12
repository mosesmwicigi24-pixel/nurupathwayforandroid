// "Remind me when we're live" (iOS RadioPlayerView.RemindMeCTA parity) — a
// one-time WorkManager job scheduled to fire at the next program's
// scheduledAt, posting a local notification on the "nuru_radio" channel.
// WorkManager (not AlarmManager) so no exact-alarm permission is needed; the
// toggle itself persists per-program in AppPrefs so re-opening the player
// shows the right state (a new show up next naturally resets it — new id).
package org.nuruplace.member.feature.radio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import org.nuruplace.member.MainActivity
import org.nuruplace.member.R
import org.nuruplace.member.data.AppPrefs
import org.nuruplace.member.data.net.RadioProgram
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

object RadioReminder {
    const val CHANNEL_ID = "nuru_radio"
    private const val WORK_PREFIX = "radio-remind-"
    private const val KEY_PROGRAM_ID = "programId"
    private const val KEY_TITLE = "title"

    fun isSet(programId: String): Boolean = AppPrefs.isRadioReminderSet(programId)

    /** When a reminder for [program] would fire: its start, if readable and
     *  still ahead of [now]; null when it can't be reminded (no time, an
     *  unreadable one, or a start already past — that used to fire "Nuru
     *  Radio is live" a second after the tap). */
    fun remindAt(program: RadioProgram, now: Instant = Instant.now()): Instant? =
        parseScheduledAt(program.scheduledAt)?.takeIf { it.isAfter(now) }

    /** The next program a reminder can be set for — the soonest readable
     *  future start. It sorted by the raw text, so a program with no time
     *  sorted first and got the button. */
    fun nextRemindable(programs: List<RadioProgram>, now: Instant = Instant.now()): RadioProgram? =
        programs.filter { it.status == "scheduled" }
            .mapNotNull { p -> remindAt(p, now)?.let { p to it } }
            .minByOrNull { it.second }?.first

    /** Toggle the reminder for [program]; returns the new on/off state. It is
     *  marked on only once the phone has actually scheduled it (EXPERIENCE.md
     *  §7.4: no success before it is true) — it used to be marked on even when
     *  nothing was scheduled. */
    fun toggle(context: Context, program: RadioProgram): Boolean {
        if (isSet(program.id)) {
            cancel(context, program.id)
            AppPrefs.setRadioReminder(program.id, false)
            return false
        }
        val scheduled = schedule(context, program)
        AppPrefs.setRadioReminder(program.id, scheduled)
        return scheduled
    }

    /** Schedules the reminder; false when there is nothing to schedule. */
    private fun schedule(context: Context, program: RadioProgram): Boolean {
        val at = remindAt(program) ?: return false
        val delayMs = (at.toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(1_000L)
        ensureChannel(context)
        val request = OneTimeWorkRequestBuilder<RadioReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
            .setInputData(workDataOf(KEY_PROGRAM_ID to program.id, KEY_TITLE to program.title))
            .addTag(WORK_PREFIX + program.id)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_PREFIX + program.id, ExistingWorkPolicy.REPLACE, request)
        return true
    }

    private fun cancel(context: Context, programId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_PREFIX + programId)
    }

    private fun parseScheduledAt(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
            ?: runCatching { Instant.parse(raw) }.getOrNull()
    }

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Nuru Radio", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Live broadcast reminders"
                },
            )
        }
    }

    /** Fires the "Nuru Radio is live" notification when the scheduled work runs. */
    class RadioReminderWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            val programId = inputData.getString(KEY_PROGRAM_ID) ?: return Result.success()
            val title = inputData.getString(KEY_TITLE).orEmpty()
            // The member may have cancelled the reminder since it was queued.
            if (!AppPrefs.isRadioReminderSet(programId)) return Result.success()
            ensureChannel(applicationContext)
            val intent = Intent(applicationContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("nuru.dest", "radio")
            val pending = PendingIntent.getActivity(
                applicationContext, programId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Nuru Radio is live")
                .setContentText(if (title.isNotBlank()) "$title is starting — tune in now." else "Tune in now.")
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
            (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(programId.hashCode(), notif)
            AppPrefs.setRadioReminder(programId, false)
            return Result.success()
        }
    }
}
