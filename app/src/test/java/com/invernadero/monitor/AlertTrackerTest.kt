package com.invernadero.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertTrackerTest {

    @Test
    fun `alerta la primera vez que se supera el umbral`() {
        val alerts = AlertTracker()
        assertFalse(alerts.checkHigh(30.0, threshold = 30.0))
        assertTrue(alerts.checkHigh(31.0, threshold = 30.0))
    }

    @Test
    fun `no repite la alerta mientras la temperatura sigue alta`() {
        val alerts = AlertTracker()
        assertTrue(alerts.checkHigh(31.0, threshold = 30.0))
        assertFalse(alerts.checkHigh(32.0, threshold = 30.0))
    }

    @Test
    fun `no repite la alerta si la temperatura oscila alrededor del umbral`() {
        val alerts = AlertTracker()
        val readings = listOf(30.1, 29.9, 30.1, 29.9, 30.1)
        assertEquals(listOf(true, false, false, false, false), readings.map { alerts.checkHigh(it, threshold = 30.0) })
    }

    @Test
    fun `se rearma cuando la temperatura baja un grado del umbral`() {
        val alerts = AlertTracker()
        assertTrue(alerts.checkHigh(31.0, threshold = 30.0))
        assertFalse(alerts.checkHigh(29.0, threshold = 30.0))
        assertTrue(alerts.checkHigh(31.0, threshold = 30.0))
    }

    @Test
    fun `alerta de helada con histeresis`() {
        val alerts = AlertTracker()
        val readings = listOf(6.0, 4.5, 4.0, 5.5, 4.5, 6.0, 4.9)
        assertEquals(
            listOf(false, true, false, false, false, false, true),
            readings.map { alerts.checkLow(it, minimum = 5.0) }
        )
    }

    @Test
    fun `las alertas de calor y de helada son independientes`() {
        val alerts = AlertTracker()
        assertTrue(alerts.checkLow(3.0, minimum = 5.0))
        assertTrue(alerts.checkHigh(31.0, threshold = 30.0))
        assertFalse(alerts.checkLow(3.0, minimum = 5.0))
    }
}
