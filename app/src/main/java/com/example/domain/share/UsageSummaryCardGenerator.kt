package com.example.domain.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.example.domain.model.UsageSummaryCardData
import com.example.domain.util.formatBytesCompact
import javax.inject.Inject

/**
 * Phase 4 (#15) - draws the shareable usage summary card as a plain [android.graphics.Canvas]
 * bitmap (not a Compose `ImageBitmap` capture) - this runs from a plain repository/ViewModel call
 * with no Composable in scope, so Canvas is the simpler, dependency-free option here.
 */
class UsageSummaryCardGenerator @Inject constructor() {

    fun generate(data: UsageSummaryCardData): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(Color.parseColor("#0B0D14")) // BackgroundDark

        val padding = 64f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Header
        paint.color = Color.parseColor("#6E9BFF") // BluePrimaryDark
        paint.textSize = 40f
        paint.isFakeBoldText = true
        canvas.drawText("NET MONITOR", padding, 120f, paint)

        paint.color = Color.parseColor("#9AA0B4") // TextSecondaryDark
        paint.textSize = 32f
        paint.isFakeBoldText = false
        canvas.drawText(data.periodLabel, padding, 170f, paint)

        // Big total
        paint.color = Color.parseColor("#E8EAF2") // OnBackgroundDark
        paint.textSize = 110f
        paint.isFakeBoldText = true
        canvas.drawText(formatBytesCompact(data.totalBytes), padding, 320f, paint)
        paint.textSize = 36f
        paint.isFakeBoldText = false
        canvas.drawText("total data used", padding, 370f, paint)

        // Wi-Fi / Mobile split bar
        val barTop = 440f
        val barHeight = 48f
        val barWidth = WIDTH - padding * 2
        val total = (data.wifiBytes + data.mobileBytes).coerceAtLeast(1L)
        val wifiFraction = data.wifiBytes.toFloat() / total
        val wifiWidth = barWidth * wifiFraction

        paint.color = Color.parseColor("#FF6A3D") // MobileOrange (full-width base)
        canvas.drawRoundRect(RectF(padding, barTop, padding + barWidth, barTop + barHeight), 16f, 16f, paint)
        if (wifiWidth > 0f) {
            paint.color = Color.parseColor("#2563EB") // WifiBlue
            canvas.drawRoundRect(RectF(padding, barTop, padding + wifiWidth, barTop + barHeight), 16f, 16f, paint)
        }

        paint.textSize = 30f
        paint.color = Color.parseColor("#2563EB")
        canvas.drawText("Wi-Fi ${formatBytesCompact(data.wifiBytes)}", padding, barTop + barHeight + 50f, paint)
        paint.color = Color.parseColor("#FF6A3D")
        val mobileLabel = "Mobile ${formatBytesCompact(data.mobileBytes)}"
        val mobileLabelWidth = paint.measureText(mobileLabel)
        canvas.drawText(mobileLabel, padding + barWidth - mobileLabelWidth, barTop + barHeight + 50f, paint)

        // Top app
        if (data.topAppName != null) {
            paint.color = Color.parseColor("#9AA0B4")
            paint.textSize = 30f
            canvas.drawText("Top app", padding, 660f, paint)
            paint.color = Color.parseColor("#E8EAF2")
            paint.textSize = 42f
            paint.isFakeBoldText = true
            canvas.drawText("${data.topAppName} - ${formatBytesCompact(data.topAppBytes)}", padding, 715f, paint)
        }

        return bitmap
    }

    companion object {
        private const val WIDTH = 1080
        private const val HEIGHT = 1080
    }
}
