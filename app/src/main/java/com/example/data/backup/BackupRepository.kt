package com.example.data.backup

import android.content.Context
import android.net.Uri
import com.example.data.local.DataUsageDao
import com.example.data.local.DiagnosticsDao
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Phase 4 (#13) - manual JSON backup/restore of every Room table, via Storage Access Framework:
 * the caller launches `ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT` and passes the resulting
 * `content://` [Uri] here - never a raw file path, for scoped-storage compliance.
 */
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataUsageDao: DataUsageDao,
    private val diagnosticsDao: DiagnosticsDao,
) {
    // Codegen-generated adapters (@JsonClass(generateAdapter = true) on BackupPayload and every
    // entity it references, in data/local) - not the reflection-based KotlinJsonAdapterFactory,
    // which needs a `kotlin-reflect` runtime dependency this project doesn't have. Codegen is
    // already wired project-wide via the moshi-kotlin-codegen KSP processor, so this needs nothing new.
    private val moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter(BackupPayload::class.java)

    suspend fun exportTo(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val payload = BackupPayload(
                exportedAtMillis = System.currentTimeMillis(),
                dailyDataUsage = dataUsageDao.getAllDailyDataUsage(),
                appDataUsage = dataUsageDao.getAllAppDataUsage(),
                speedHistory = diagnosticsDao.getAllSpeedHistory(),
                networkDowntimeLog = diagnosticsDao.getAllDowntimeLogs(),
                signalStrengthSample = diagnosticsDao.getAllSignalStrengthSamples(),
                connectionTypeTransition = diagnosticsDao.getAllConnectionTypeTransitions(),
                batteryLevelSample = diagnosticsDao.getAllBatteryLevelSamples(),
            )
            val json = adapter.toJson(payload)
            val stream = context.contentResolver.openOutputStream(uri)
                ?: return@withContext Result.failure(IllegalStateException("Could not open output stream for $uri"))
            stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            Result.success(payload.totalRowCount())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importFrom(uri: Uri): Result<ImportSummary> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IllegalStateException("Could not open input stream for $uri"))
            val json = inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            val payload = adapter.fromJson(json)
                ?: return@withContext Result.failure(IllegalArgumentException("Backup file is empty or invalid"))
            if (payload.backupFormatVersion != BACKUP_FORMAT_VERSION) {
                return@withContext Result.failure(
                    IllegalArgumentException(
                        "Backup format v${payload.backupFormatVersion} isn't compatible with this app's v$BACKUP_FORMAT_VERSION"
                    )
                )
            }

            dataUsageDao.insertAllDailyDataUsage(payload.dailyDataUsage)
            dataUsageDao.insertAppDataUsage(payload.appDataUsage)
            diagnosticsDao.insertAllSpeedHistory(payload.speedHistory)
            diagnosticsDao.insertAllDowntimeLogs(payload.networkDowntimeLog)
            diagnosticsDao.insertAllSignalStrengthSamples(payload.signalStrengthSample)
            diagnosticsDao.insertAllConnectionTypeTransitions(payload.connectionTypeTransition)
            diagnosticsDao.insertAllBatteryLevelSamples(payload.batteryLevelSample)

            Result.success(
                ImportSummary(
                    dailyDataUsageRows = payload.dailyDataUsage.size,
                    appDataUsageRows = payload.appDataUsage.size,
                    speedHistoryRows = payload.speedHistory.size,
                    networkDowntimeLogRows = payload.networkDowntimeLog.size,
                    signalStrengthSampleRows = payload.signalStrengthSample.size,
                    connectionTypeTransitionRows = payload.connectionTypeTransition.size,
                    batteryLevelSampleRows = payload.batteryLevelSample.size,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun BackupPayload.totalRowCount(): Int =
        dailyDataUsage.size + appDataUsage.size + speedHistory.size +
            networkDowntimeLog.size + signalStrengthSample.size + connectionTypeTransition.size + batteryLevelSample.size

    companion object {
        const val BACKUP_FORMAT_VERSION = 1
    }
}
