// Home-screen widget snapshot bridge — the Android twin of iOS's (parked)
// `writeWidgetSnapshot` idea. The app writes a small denormalized snapshot
// (Pathway progress + Radio on-air state + church-live flag) into plain
// SharedPreferences on the same events the Home/Pathway/Radio screens already
// load their own state; the Glance widgets ONLY ever read this snapshot —
// never network, never touch Net.client directly. That keeps widget renders
// instant and battery-safe, matching the "never do network in the widget"
// rule for this feature.
package org.nuruplace.member.widget

import android.content.Context
import android.content.SharedPreferences

/** Denormalized read model the widgets render from. Every field has a safe
 *  default so a cold install (no snapshot written yet) still renders a
 *  sensible "open the app" state instead of blank widgets. */
data class WidgetSnapshot(
    val currentLevel: Int = 1,
    val levelTitle: String = "",
    val completedModules: Int = 0,
    val totalModules: Int = 0,
    val nextModuleTitle: String? = null,
    val streak: Int = 0,
    val radioOnAir: Boolean = false,
    val radioProgramTitle: String? = null,
    val radioHost: String? = null,
    val radioListeners: Int? = null,
    val radioNextProgramTitle: String? = null,
    val churchLive: Boolean = false,
    val updatedAtMillis: Long = 0L,
) {
    /** 0-100, clamped — the Pathway widget's progress ring. */
    val modulePct: Int get() = if (totalModules > 0) ((completedModules * 100) / totalModules).coerceIn(0, 100) else 0
}

object WidgetSnapshotStore {
    private const val FILE = "nuru_widget_snapshot"

    private const val KEY_LEVEL = "level"
    private const val KEY_LEVEL_TITLE = "levelTitle"
    private const val KEY_COMPLETED = "completedModules"
    private const val KEY_TOTAL = "totalModules"
    private const val KEY_NEXT_MODULE = "nextModuleTitle"
    private const val KEY_STREAK = "streak"
    private const val KEY_RADIO_ON_AIR = "radioOnAir"
    private const val KEY_RADIO_TITLE = "radioProgramTitle"
    private const val KEY_RADIO_HOST = "radioHost"
    private const val KEY_RADIO_LISTENERS = "radioListeners"
    private const val KEY_RADIO_NEXT = "radioNextProgramTitle"
    private const val KEY_CHURCH_LIVE = "churchLive"
    private const val KEY_UPDATED_AT = "updatedAtMillis"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun read(context: Context): WidgetSnapshot {
        val p = prefs(context)
        return WidgetSnapshot(
            currentLevel = p.getInt(KEY_LEVEL, 1),
            levelTitle = p.getString(KEY_LEVEL_TITLE, "") ?: "",
            completedModules = p.getInt(KEY_COMPLETED, 0),
            totalModules = p.getInt(KEY_TOTAL, 0),
            nextModuleTitle = p.getString(KEY_NEXT_MODULE, null),
            streak = p.getInt(KEY_STREAK, 0),
            radioOnAir = p.getBoolean(KEY_RADIO_ON_AIR, false),
            radioProgramTitle = p.getString(KEY_RADIO_TITLE, null),
            radioHost = p.getString(KEY_RADIO_HOST, null),
            radioListeners = p.getInt(KEY_RADIO_LISTENERS, -1).takeIf { it >= 0 },
            radioNextProgramTitle = p.getString(KEY_RADIO_NEXT, null),
            churchLive = p.getBoolean(KEY_CHURCH_LIVE, false),
            updatedAtMillis = p.getLong(KEY_UPDATED_AT, 0L),
        )
    }

    /** Called from PathwayHubScreen once the active level + resume module are
     *  known (mirrors iOS's intended progress-ring/streak/next-module trigger
     *  points). Refreshes both widgets immediately after persisting. */
    suspend fun writePathway(
        context: Context,
        currentLevel: Int,
        levelTitle: String,
        completedModules: Int,
        totalModules: Int,
        nextModuleTitle: String?,
        streak: Int,
    ) {
        prefs(context).edit()
            .putInt(KEY_LEVEL, currentLevel)
            .putString(KEY_LEVEL_TITLE, levelTitle)
            .putInt(KEY_COMPLETED, completedModules)
            .putInt(KEY_TOTAL, totalModules)
            .putString(KEY_NEXT_MODULE, nextModuleTitle)
            .putInt(KEY_STREAK, streak)
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
        WidgetRefresher.updateAll(context)
    }

    /** Called from Home (radioNowPlaying) and LiveRadioScreen (its richer
     *  now/nextScheduled load) — whichever screen loaded most recently wins,
     *  which is fine since both read the same GET /radio/now-playing shape. */
    suspend fun writeRadio(
        context: Context,
        onAir: Boolean,
        programTitle: String?,
        host: String?,
        listeners: Int?,
        nextProgramTitle: String?,
    ) {
        prefs(context).edit()
            .putBoolean(KEY_RADIO_ON_AIR, onAir)
            .putString(KEY_RADIO_TITLE, programTitle)
            .putString(KEY_RADIO_HOST, host)
            .apply {
                if (listeners != null) putInt(KEY_RADIO_LISTENERS, listeners) else remove(KEY_RADIO_LISTENERS)
            }
            .putString(KEY_RADIO_NEXT, nextProgramTitle)
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
        WidgetRefresher.updateAll(context)
    }

    /** Called from Home's church-live poll (GET /live/now, scope == "church"). */
    suspend fun writeChurchLive(context: Context, live: Boolean) {
        val p = prefs(context)
        if (p.getBoolean(KEY_CHURCH_LIVE, false) == live) return // no-op: skip a pointless widget repaint
        p.edit().putBoolean(KEY_CHURCH_LIVE, live).putLong(KEY_UPDATED_AT, System.currentTimeMillis()).apply()
        WidgetRefresher.updateAll(context)
    }
}
