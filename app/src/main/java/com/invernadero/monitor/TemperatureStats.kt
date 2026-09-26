package com.invernadero.monitor

import java.util.Calendar
import java.util.TimeZone

data class DayStats(val min: Double, val max: Double, val avg: Double, val count: Int)

object TemperatureStats {

    fun compute(readings: List<SensorReading>, sinceMillis: Long): DayStats? {
        val values = readings.filter { it.timestamp >= sinceMillis }.map { it.value }
        if (values.isEmpty()) return null
        return DayStats(
            min = values.min(),
            max = values.max(),
            avg = values.average(),
            count = values.size
        )
    }

    fun startOfDay(nowMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): Long {
        val calendar = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    /** Estado visual de una temperatura respecto al umbral: 0 normal, 1 cerca, 2 alto. */
    fun level(temperature: Double, threshold: Double, nearMargin: Double = 2.0): Int = when {
        temperature > threshold -> 2
        temperature > threshold - nearMargin -> 1
        else -> 0
    }
}
