package com.invernadero.monitor

import com.invernadero.monitor.ArduinoProtocol.IrrigationEvent
import com.invernadero.monitor.ArduinoProtocol.StopReason
import com.invernadero.monitor.IrrigationLogic.Verdict
import org.junit.Assert.assertEquals
import org.junit.Test

class IrrigationLogicTest {

    private fun event(auto: Boolean, seconds: Int, before: Int, after: Int, reason: StopReason) =
        IrrigationEvent(auto, seconds, before, after, reason)

    @Test
    fun `un riego que humedece el suelo queda verificado`() {
        assertEquals(Verdict.VERIFIED, IrrigationLogic.verify(event(true, 4, 34, 46, StopReason.THRESHOLD_REACHED)))
    }

    @Test
    fun `un riego que no sube la humedad se marca sin efecto`() {
        assertEquals(Verdict.NO_EFFECT, IrrigationLogic.verify(event(true, 30, 16, 1, StopReason.MAX_TIME)))
        assertEquals(Verdict.NO_EFFECT, IrrigationLogic.verify(event(true, 30, 30, 34, StopReason.MAX_TIME)))
    }

    @Test
    fun `una subida igual al minimo cuenta como verificada`() {
        assertEquals(Verdict.VERIFIED, IrrigationLogic.verify(event(true, 30, 30, 35, StopReason.MAX_TIME)))
    }

    @Test
    fun `un riego manual muy corto no es concluyente`() {
        assertEquals(Verdict.INCONCLUSIVE, IrrigationLogic.verify(event(false, 2, 30, 31, StopReason.STOPPED)))
    }

    @Test
    fun `una falla del sensor durante el riego no es concluyente`() {
        assertEquals(Verdict.INCONCLUSIVE, IrrigationLogic.verify(event(true, 10, 30, 50, StopReason.SENSOR_FAILURE)))
    }

    @Test
    fun `nivel del suelo respecto al umbral`() {
        assertEquals(0, IrrigationLogic.soilLevel(20, 35))
        assertEquals(1, IrrigationLogic.soilLevel(35, 35))
        assertEquals(1, IrrigationLogic.soilLevel(44, 35))
        assertEquals(2, IrrigationLogic.soilLevel(45, 35))
    }

    @Test
    fun `el registro guarda motivo y veredicto`() {
        val record = IrrigationLogic.toRecord(event(true, 4, 34, 46, StopReason.THRESHOLD_REACHED), 1_000L)
        assertEquals(IrrigationRecord(1_000L, true, 4, 34, 46, "U", "VERIFIED", simulated = false), record)
    }

    @Test
    fun `el registro de un riego simulado queda marcado`() {
        val record = IrrigationLogic.toRecord(event(true, 30, 16, 1, StopReason.MAX_TIME), 2_000L, simulated = true)
        assertEquals(true, record.simulated)
        assertEquals("NO_EFFECT", record.verdict)
    }

    @Test
    fun `el resumen cuenta solo los riegos del periodo`() {
        val records = listOf(
            IrrigationRecord(timestamp = 500, verdict = "VERIFIED"),
            IrrigationRecord(timestamp = 2_000, verdict = "VERIFIED"),
            IrrigationRecord(timestamp = 3_000, verdict = "NO_EFFECT"),
            IrrigationRecord(timestamp = 4_000, verdict = "INCONCLUSIVE")
        )
        assertEquals(IrrigationLogic.Summary(total = 3, verified = 1, noEffect = 1), IrrigationLogic.summarize(records, 1_000))
    }
}
