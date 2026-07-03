// Room database for the offline layer — currently the single mutation queue.
package org.nuruplace.member.data.offline

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PendingMutation::class], version = 1, exportSchema = false)
abstract class OfflineDb : RoomDatabase() {
    abstract fun mutations(): MutationDao

    companion object {
        @Volatile private var instance: OfflineDb? = null

        fun get(context: Context): OfflineDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, OfflineDb::class.java, "nuru_offline.db")
                .build()
                .also { instance = it }
        }
    }
}
