package com.invernadero.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BluetoothHelperParseTest {

    @Test
    fun `parsea una linea valida`() {
        assertEquals(24.5, BluetoothHelper.parseTemperatureLine("T:24.50")!!, 0.0)
    }

    @Test
    fun `ignora retorno de carro y espacios`() {
        assertEquals(24.5, BluetoothHelper.parseTemperatureLine(" T:24.50\r")!!, 0.0)
    }

    @Test
    fun `acepta temperaturas negativas dentro de rango`() {
        assertEquals(-5.0, BluetoothHelper.parseTemperatureLine("T:-5.00")!!, 0.0)
    }

    @Test
    fun `rechaza lineas sin prefijo T`() {
        assertNull(BluetoothHelper.parseTemperatureLine("24.50"))
        assertNull(BluetoothHelper.parseTemperatureLine("H:60.0"))
    }

    @Test
    fun `rechaza valores no numericos y nan`() {
        assertNull(BluetoothHelper.parseTemperatureLine("T:abc"))
        assertNull(BluetoothHelper.parseTemperatureLine("T:nan"))
        assertNull(BluetoothHelper.parseTemperatureLine("T:"))
    }

    @Test
    fun `rechaza valores fuera del rango del sensor`() {
        assertNull(BluetoothHelper.parseTemperatureLine("T:120.0"))
        assertNull(BluetoothHelper.parseTemperatureLine("T:-99.0"))
    }

    @Test
    fun `comandos de LED son los bytes 1 y 0`() {
        assertEquals('1'.code, BluetoothHelper.LED_ON)
        assertEquals('0'.code, BluetoothHelper.LED_OFF)
    }
}
