package com.invernadero.monitor

/**
 * Verificación del riego automático: con la humedad del suelo antes y después de cada riego
 * decide si el agua llegó a la tierra, para detectar una bomba averiada o un depósito vacío.
 */
object IrrigationLogic {

    /** Puntos de humedad que debe subir el suelo para dar por efectivo un riego. */
    const val MIN_SOIL_RISE = 5

    /** Un riego más corto que esto, detenido a mano, no permite sacar conclusiones. */
    const val MIN_CONCLUSIVE_SECONDS = 5

    /** El firmware termina el riego automático en umbral + este margen (%). */
    const val SOIL_HYSTERESIS = 10

    const val DEFAULT_SOIL_THRESHOLD = 35

    enum class Verdict { VERIFIED, NO_EFFECT, INCONCLUSIVE }

    data class Summary(val total: Int, val verified: Int, val noEffect: Int)

    fun verify(event: ArduinoProtocol.IrrigationEvent): Verdict = when {
        event.reason == ArduinoProtocol.StopReason.SENSOR_FAILURE -> Verdict.INCONCLUSIVE
        event.soilAfter - event.soilBefore >= MIN_SOIL_RISE -> Verdict.VERIFIED
        event.durationSeconds < MIN_CONCLUSIVE_SECONDS -> Verdict.INCONCLUSIVE
        else -> Verdict.NO_EFFECT
    }

    /** Estado del suelo respecto al umbral de riego: 0 seco, 1 adecuado, 2 húmedo. */
    fun soilLevel(soil: Int, threshold: Int): Int = when {
        soil < threshold -> 0
        soil < threshold + SOIL_HYSTERESIS -> 1
        else -> 2
    }

    /** [simulated] marca los riegos del modo simulación para no mezclarlos con datos reales. */
    fun toRecord(event: ArduinoProtocol.IrrigationEvent, timestamp: Long, simulated: Boolean = false) = IrrigationRecord(
        timestamp = timestamp,
        automatic = event.automatic,
        durationSeconds = event.durationSeconds,
        soilBefore = event.soilBefore,
        soilAfter = event.soilAfter,
        reason = event.reason.code.toString(),
        verdict = verify(event).name,
        simulated = simulated
    )

    /** Cuenta los riegos registrados desde [sinceMillis] y cuántos se verificaron o no tuvieron efecto. */
    fun summarize(records: List<IrrigationRecord>, sinceMillis: Long): Summary {
        val period = records.filter { it.timestamp >= sinceMillis }
        return Summary(
            total = period.size,
            verified = period.count { it.verdict == Verdict.VERIFIED.name },
            noEffect = period.count { it.verdict == Verdict.NO_EFFECT.name }
        )
    }
}
