package com.example.domain.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.example.domain.model.UsageSummaryCardData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * Phase 4 (#15) - saves the generated card to cache and builds a share [Intent]. Always a
 * [FileProvider] `content://` Uri with [Intent.FLAG_GRANT_READ_URI_PERMISSION] - never a raw
 * `file://` Uri, which `FileUriExposedException` blocks on API 24+ anyway.
 */
class ShareUsageCard @Inject constructor(
    @ApplicationContext private val context: Context,
    private val generator: UsageSummaryCardGenerator,
) {
    suspend fun buildShareIntent(data: UsageSummaryCardData): Intent = withContext(Dispatchers.IO) {
        val bitmap = generator.generate(data)
        val file = writeToCache(bitmap)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun writeToCache(bitmap: Bitmap): File {
        val dir = File(context.cacheDir, "shared_cards").apply { mkdirs() }
        val file = File(dir, "usage_summary_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        return file
    }
}
