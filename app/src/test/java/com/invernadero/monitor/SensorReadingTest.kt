package com.invernadero.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SensorReadingTest {

    @Test
    fun `constructor sin argumentos usa valores por defecto para deserializacion de Firebase`() {
        val reading = SensorReading()
        assertEquals(0.0, reading.value, 0.0)
        assertEquals(0L, reading.timestamp)
    }

    @Test
    fun `dos lecturas con los mismos datos son iguales`() {
        val a = SensorReading(value = 24.5, timestamp = 1_723_000_000_000)
        val b = SensorReading(value = 24.5, timestamp = 1_723_000_000_000)
        assertEquals(a, b)
    }

    @Test
    fun `lecturas con distinto valor no son iguales`() {
        val a = SensorReading(value = 24.5, timestamp = 1_723_000_000_000)
        val b = SensorReading(value = 25.0, timestamp = 1_723_000_000_000)
        assertNotEquals(a, b)
    }

    @Test
    fun `copy permite generar una variante manteniendo el resto de campos`() {
        val original = SensorReading(value = 24.5, timestamp = 1_723_000_000_000)
        val copia = original.copy(value = 30.0)
        assertEquals(30.0, copia.value, 0.0)
        assertEquals(original.timestamp, copia.timestamp)
    }
}
