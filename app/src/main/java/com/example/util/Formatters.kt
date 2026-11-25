package com.example.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Formatters {
    private val turkishLocale = Locale("tr", "TR")

    fun formatCurrency(amount: Double): String {
        val nf = NumberFormat.getNumberInstance(turkishLocale).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        return "${nf.format(amount)} ₺"
    }

    fun formatCompactCurrency(amount: Double): String {
        val nf = NumberFormat.getNumberInstance(turkishLocale).apply {
            minimumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
            maximumFractionDigits = 2
        }
        return "${nf.format(amount)} ₺"
    }

    fun formatDateTime(millis: Long): String {
        if (millis <= 0L) return "-"
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", turkishLocale)
        return sdf.format(Date(millis))
    }

    fun formatTime(millis: Long): String {
        if (millis <= 0L) return "-"
        val sdf = SimpleDateFormat("HH:mm", turkishLocale)
        return sdf.format(Date(millis))
    }

    fun formatBusinessDateLabel(businessDate: String): String {
        val today = todayBusinessDate()
        val yesterday = yesterdayBusinessDate()
        return when (businessDate) {
            today -> "Bugün ($businessDate)"
            yesterday -> "Dün ($businessDate)"
            else -> businessDate
        }
    }

    fun todayBusinessDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    fun yesterdayBusinessDate(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(cal.time)
    }

    fun formatElapsedDuration(openedAtMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        if (openedAtMillis <= 0L) return "Yeni"
        val diffMinutes = ((nowMillis - openedAtMillis).coerceAtLeast(0L)) / 60_000L
        return when {
            diffMinutes < 1L -> "Az önce"
            diffMinutes < 60L -> "$diffMinutes dk"
            else -> {
                val hours = diffMinutes / 60L
                val mins = diffMinutes % 60L
                "${hours} sa ${mins} dk"
            }
        }
    }
}
