package com.wavora.appdata.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.wavora.common.DB_NAME
import com.wavora.appdata.io.appHomeFolder
import com.wavora.logger.Logger
import java.io.File

private const val DB_TAG = "MusicDatabase"

// Измерено: Music Database = 15 МБ, а Music Database-wal = 534 МБ (WAL по умолчанию
// не уменьшает файл после checkpoint без journal_size_limit). Ограничиваем WAL и
// один раз за запуск делаем TRUNCATE-checkpoint; результат пишем в лог для замера.
private const val WAL_SIZE_LIMIT_BYTES = 64L * 1024 * 1024

actual fun getDatabaseBuilder(
    converters: Converters
): RoomDatabase.Builder<MusicDatabase> {
    return Room.databaseBuilder<MusicDatabase>(
        name = getDatabasePath()
    ).addTypeConverter(converters)
        .addCallback(
            object : RoomDatabase.Callback() {
                override fun onOpen(connection: SQLiteConnection) {
                    try {
                        connection.execSQL("PRAGMA journal_size_limit = $WAL_SIZE_LIMIT_BYTES")
                        connection.prepare("PRAGMA wal_checkpoint(TRUNCATE)").use { st ->
                            if (st.step()) {
                                Logger.i(
                                    DB_TAG,
                                    "WAL checkpoint(TRUNCATE) on open: busy=${st.getLong(0)} " +
                                        "walFrames=${st.getLong(1)} checkpointed=${st.getLong(2)}",
                                )
                            }
                        }
                    } catch (e: Throwable) {
                        Logger.w(DB_TAG, "WAL maintenance failed: ${e.message}")
                    }
                }
            },
        )
}

actual fun getDatabasePath(): String {
    val dbFile = File(File(appHomeFolder(), "db"), DB_NAME)
    return dbFile.absolutePath
}