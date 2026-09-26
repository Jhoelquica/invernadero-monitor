package com.invernadero.monitor

import com.invernadero.monitor.ArduinoProtocol.DeviceStatus
import com.invernadero.monitor.ArduinoProtocol.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothHelperParseTest {

    private fun status(line: String): DeviceStatus =
        (ArduinoProtocol.parseLine(line) as Message.Status).status

    @Test
    fun `parsea una linea completa de lectura periodica`() {
        val s = status("T:24.50,H:60,L:1,M:0,B:255,S:30")
        assertEquals(24.5, s.temperature!!, 0.0)
        assertEquals(60.0, s.humidity!!, 0.0)
        assertEquals(true, s.ledOn)
        assertEquals(false, s.autoMode)
        assertEquals(255, s.brightness)
        assertEquals(30, s.threshold)
    }

    @Test
    fun `mantiene compatibilidad con el formato antiguo solo temperatura`() {
        val s = status("T:24.50")
        assertEquals(24.5, s.temperature!!, 0.0)
        assertNull(s.humidity)
    }

    @Test
    fun `parsea el eco de estado sin temperatura`() {
        val s = status("L:0,M:1,B:128,S:28")
        assertNull(s.temperature)
        assertEquals(false, s.ledOn)
        assertEquals(true, s.autoMode)
        assertEquals(128, s.brightness)
    }

    @Test
    fun `ignora retorno de carro y espacios`() {
        assertEquals(24.5, status(" T:24.50\r").temperature!!, 0.0)
    }

    @Test
    fun `acepta temperaturas negativas dentro de rango`() {
        assertEquals(-5.0, status("T:-5.00").temperature!!, 0.0)
    }

    @Test
    fun `reconoce PONG y error de sensor`() {
        assertEquals(Message.Pong, ArduinoProtocol.parseLine("PONG\r"))
        assertEquals(Message.SensorError("DHT"), ArduinoProtocol.parseLine("E:DHT"))
    }

    @Test
    fun `rechaza lineas sin campos validos`() {
        assertNull(ArduinoProtocol.parseLine(""))
        assertNull(ArduinoProtocol.parseLine("hola"))
        assertNull(ArduinoProtocol.parseLine("24.50"))
        assertNull(ArduinoProtocol.parseLine("T:abc"))
        assertNull(ArduinoProtocol.parseLine("T:nan"))
        assertNull(ArduinoProtocol.parseLine("E:"))
    }

    @Test
    fun `descarta campos fuera de rango pero conserva los validos`() {
        val s = status("T:120.0,H:150,L:1,B:300,S:5")
        assertNull(s.temperature)
        assertNull(s.humidity)
        assertNull(s.brightness)
        assertNull(s.threshold)
        assertEquals(true, s.ledOn)
    }

    @Test
    fun `una linea con solo campos invalidos se descarta`() {
        assertNull(ArduinoProtocol.parseLine("T:120.0"))
        assertTrue(ArduinoProtocol.parseLine("T:120.0,L:1") is Message.Status)
    }

    @Test
    fun `los comandos usan el formato del protocolo`() {
        assertEquals("LED:1", ArduinoProtocol.ledCommand(true))
        assertEquals("LED:0", ArduinoProtocol.ledCommand(false))
        assertEquals("AUTO:1", ArduinoProtocol.autoCommand(true))
        assertEquals("BRI:128", ArduinoProtocol.brightnessCommand(128))
        assertEquals("SET:30", ArduinoProtocol.thresholdCommand(30))
        assertEquals("PING", ArduinoProtocol.CMD_PING)
    }

    @Test
    fun `los comandos limitan los valores al rango permitido`() {
        assertEquals("BRI:255", ArduinoProtocol.brightnessCommand(999))
        assertEquals("BRI:0", ArduinoProtocol.brightnessCommand(-4))
        assertEquals("SET:60", ArduinoProtocol.thresholdCommand(500))
        assertEquals("SET:10", ArduinoProtocol.thresholdCommand(0))
    }
}
