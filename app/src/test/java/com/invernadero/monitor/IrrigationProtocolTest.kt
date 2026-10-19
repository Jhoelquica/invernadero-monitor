package com.invernadero.monitor

import com.invernadero.monitor.ArduinoProtocol.IrrigationEvent
import com.invernadero.monitor.ArduinoProtocol.Message
import com.invernadero.monitor.ArduinoProtocol.StopReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IrrigationProtocolTest {

    private fun status(line: String) = (ArduinoProtocol.parseLine(line) as Message.Status).status

    @Test
    fun `parsea los campos de riego de la lectura periodica`() {
        val s = status("T:24.5,H:60,L:1,M:0,B:255,S:30,W:45,P:1,R:1,U:35,Z:1")
        assertEquals(24.5, s.temperature!!, 0.0)
        assertEquals(45, s.soilMoisture)
        assertEquals(true, s.pumpOn)
        assertEquals(true, s.irrigationAuto)
        assertEquals(35, s.soilThreshold)
        assertEquals(true, s.simulated)
    }

    @Test
    fun `sin campo Z la lectura no se marca como simulada`() {
        val s = status("L:0,M:0,B:255,S:30,W:20,P:0,R:0,U:40")
        assertNull(s.simulated)
        assertEquals(false, s.pumpOn)
        assertEquals(40, s.soilThreshold)
    }

    @Test
    fun `descarta valores de riego fuera de rango pero conserva los validos`() {
        val s = status("W:120,P:2,U:5,L:1")
        assertNull(s.soilMoisture)
        assertNull(s.pumpOn)
        assertNull(s.soilThreshold)
        assertEquals(true, s.ledOn)
    }

    @Test
    fun `reconoce el fin de un riego`() {
        assertEquals(
            Message.Irrigation(IrrigationEvent(true, 12, 33, 46, StopReason.THRESHOLD_REACHED)),
            ArduinoProtocol.parseLine("EV:1,12,33,46,U\r")
        )
        assertEquals(
            Message.Irrigation(IrrigationEvent(false, 4, 0, 12, StopReason.STOPPED)),
            ArduinoProtocol.parseLine("EV:0,4,0,12,M")
        )
    }

    @Test
    fun `rechaza eventos de riego mal formados`() {
        assertNull(ArduinoProtocol.parseLine("EV:1,12,33,U"))
        assertNull(ArduinoProtocol.parseLine("EV:2,12,33,46,U"))
        assertNull(ArduinoProtocol.parseLine("EV:1,-3,33,46,U"))
        assertNull(ArduinoProtocol.parseLine("EV:1,12,33,146,U"))
        assertNull(ArduinoProtocol.parseLine("EV:1,12,33,46,X"))
        assertNull(ArduinoProtocol.parseLine("EV:"))
    }

    @Test
    fun `reconoce el error del sensor de suelo`() {
        assertEquals(Message.SensorError(ArduinoProtocol.SOIL_SENSOR), ArduinoProtocol.parseLine("E:SUELO"))
    }

    @Test
    fun `los comandos de riego usan el formato del protocolo`() {
        assertEquals("PUMP:1", ArduinoProtocol.pumpCommand(true))
        assertEquals("PUMP:0", ArduinoProtocol.pumpCommand(false))
        assertEquals("RIEGO:1", ArduinoProtocol.irrigationAutoCommand(true))
        assertEquals("SOIL:35", ArduinoProtocol.soilThresholdCommand(35))
        assertEquals("FALLA:1", ArduinoProtocol.faultCommand(true))
    }

    @Test
    fun `el umbral de suelo se limita al rango permitido`() {
        assertEquals("SOIL:80", ArduinoProtocol.soilThresholdCommand(95))
        assertEquals("SOIL:10", ArduinoProtocol.soilThresholdCommand(0))
    }
}
