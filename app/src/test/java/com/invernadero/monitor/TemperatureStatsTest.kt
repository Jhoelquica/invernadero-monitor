package com.invernadero.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.TimeZone

class TemperatureStatsTest {

    private val readings = listOf(
        SensorReading(value = 20.0, timestamp = 1_000),
        SensorReading(value = 30.0, timestamp = 2_000),
        SensorReading(value = 25.0, timestamp = 3_000)
    )

    @Test
    fun `calcula minimo maximo y promedio`() {
        val stats = TemperatureStats.compute(readings, sinceMillis = 0)!!
        assertEquals(20.0, stats.min, 0.0)
        assertEquals(30.0, stats.max, 0.0)
        assertEquals(25.0, stats.avg, 0.0)
        assertEquals(3, stats.count)
    }

    @Test
    fun `solo considera lecturas desde el inicio indicado`() {
        val stats = TemperatureStats.compute(readings, sinceMillis = 2_000)!!
        assertEquals(2, stats.count)
        assertEquals(25.0, stats.min, 0.0)
        assertEquals(27.5, stats.avg, 0.0)
    }

    @Test
    fun `devuelve null si no hay lecturas en el periodo`() {
        assertNull(TemperatureStats.compute(readings, sinceMillis = 10_000))
        assertNull(TemperatureStats.compute(emptyList(), sinceMillis = 0))
    }

    @Test
    fun `inicio del dia es medianoche local`() {
        val utc = TimeZone.getTimeZone("UTC")
        val noon = 12 * 3_600_000L + 34 * 60_000L
        assertEquals(0L, TemperatureStats.startOfDay(noon, utc))
        assertEquals(86_400_000L, TemperatureStats.startOfDay(86_400_000L + noon, utc))
    }

    @Test
    fun `nivel respecto al umbral`() {
        assertEquals(0, TemperatureStats.level(25.0, 30.0))
        assertEquals(1, TemperatureStats.level(28.5, 30.0))
        assertEquals(0, TemperatureStats.level(28.0, 30.0))
        assertEquals(1, TemperatureStats.level(30.0, 30.0))
        assertEquals(2, TemperatureStats.level(30.1, 30.0))
    }
}
