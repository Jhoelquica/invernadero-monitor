package com.invernadero.monitor

/**
 * Protocolo de líneas de texto entre el Arduino (HC-05) y la app.
 *
 * Arduino -> app:
 *   "T:24.5,H:60,L:1,M:0,B:255,S:30,W:45,P:0,R:1,U:35"  lectura periódica (cualquier subconjunto de campos)
 *   "L:1,M:0,B:128,S:30,W:45,P:0,R:1,U:35"              eco de estado tras un comando (sin T/H)
 *   "...,Z:1"                                           el firmware simula la humedad del suelo
 *   "EV:1,12,33,46,U"                                   fin de un riego (ver [IrrigationEvent])
 *   "E:DHT" | "E:SUELO"                                 fallo de un sensor
 *   "PONG"                                              respuesta a PING
 * App -> Arduino: LED:1 | LED:0 | BRI:0-255 | AUTO:1 | AUTO:0 | SET:n | PING
 *                 PUMP:1 | PUMP:0 | RIEGO:1 | RIEGO:0 | SOIL:n | FALLA:1 | FALLA:0
 */
object ArduinoProtocol {

    const val MIN_VALID_CELSIUS = -40.0
    const val MAX_VALID_CELSIUS = 80.0
    const val MIN_THRESHOLD = 10
    const val MAX_THRESHOLD = 60
    const val MIN_SOIL_THRESHOLD = 10
    const val MAX_SOIL_THRESHOLD = 80
    const val MAX_IRRIGATION_SECONDS = 3600

    const val CMD_PING = "PING"
    const val SOIL_SENSOR = "SUELO"

    data class DeviceStatus(
        val temperature: Double? = null,
        val humidity: Double? = null,
        val ledOn: Boolean? = null,
        val autoMode: Boolean? = null,
        val brightness: Int? = null,
        val threshold: Int? = null,
        val soilMoisture: Int? = null,
        val pumpOn: Boolean? = null,
        val irrigationAuto: Boolean? = null,
        val soilThreshold: Int? = null,
        val simulated: Boolean? = null
    )

    /** Motivo por el que terminó un riego, con la letra que envía el firmware. */
    enum class StopReason(val code: Char) {
        THRESHOLD_REACHED('U'),
        MAX_TIME('T'),
        STOPPED('M'),
        SENSOR_FAILURE('E');

        companion object {
            fun fromCode(code: Char): StopReason? = entries.firstOrNull { it.code == code }
        }
    }

    /** Un riego terminado: humedad del suelo (%) al inicio y al final, duración y motivo de corte. */
    data class IrrigationEvent(
        val automatic: Boolean,
        val durationSeconds: Int,
        val soilBefore: Int,
        val soilAfter: Int,
        val reason: StopReason
    )

    sealed interface Message {
        data class Status(val status: DeviceStatus) : Message
        data class SensorError(val sensor: String) : Message
        data class Irrigation(val event: IrrigationEvent) : Message
        data object Pong : Message
    }

    fun ledCommand(on: Boolean) = "LED:${if (on) 1 else 0}"
    fun autoCommand(on: Boolean) = "AUTO:${if (on) 1 else 0}"
    fun brightnessCommand(value: Int) = "BRI:${value.coerceIn(0, 255)}"
    fun thresholdCommand(value: Int) = "SET:${value.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)}"
    fun pumpCommand(on: Boolean) = "PUMP:${if (on) 1 else 0}"
    fun irrigationAutoCommand(on: Boolean) = "RIEGO:${if (on) 1 else 0}"
    fun soilThresholdCommand(value: Int) =
        "SOIL:${value.coerceIn(MIN_SOIL_THRESHOLD, MAX_SOIL_THRESHOLD)}"
    fun faultCommand(on: Boolean) = "FALLA:${if (on) 1 else 0}"

    fun parseLine(line: String): Message? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed == "PONG") return Message.Pong
        if (trimmed.startsWith("EV:")) return parseIrrigationEvent(trimmed.substring(3))
        if (trimmed.startsWith("E:")) {
            val sensor = trimmed.substring(2).trim()
            return if (sensor.isEmpty()) null else Message.SensorError(sensor)
        }

        var status = DeviceStatus()
        var anyField = false
        for (field in trimmed.split(',')) {
            val separator = field.indexOf(':')
            if (separator <= 0) continue
            val key = field.substring(0, separator).trim()
            val value = field.substring(separator + 1).trim()
            val updated = when (key) {
                "T" -> value.toDoubleOrNull()
                    ?.takeIf { !it.isNaN() && it in MIN_VALID_CELSIUS..MAX_VALID_CELSIUS }
                    ?.let { status.copy(temperature = it) }
                "H" -> value.toDoubleOrNull()
                    ?.takeIf { !it.isNaN() && it in 0.0..100.0 }
                    ?.let { status.copy(humidity = it) }
                "L" -> parseFlag(value)?.let { status.copy(ledOn = it) }
                "M" -> parseFlag(value)?.let { status.copy(autoMode = it) }
                "B" -> value.toIntOrNull()?.takeIf { it in 0..255 }?.let { status.copy(brightness = it) }
                "S" -> value.toIntOrNull()
                    ?.takeIf { it in MIN_THRESHOLD..MAX_THRESHOLD }
                    ?.let { status.copy(threshold = it) }
                "W" -> value.toIntOrNull()?.takeIf { it in 0..100 }?.let { status.copy(soilMoisture = it) }
                "P" -> parseFlag(value)?.let { status.copy(pumpOn = it) }
                "R" -> parseFlag(value)?.let { status.copy(irrigationAuto = it) }
                "U" -> value.toIntOrNull()
                    ?.takeIf { it in MIN_SOIL_THRESHOLD..MAX_SOIL_THRESHOLD }
                    ?.let { status.copy(soilThreshold = it) }
                "Z" -> parseFlag(value)?.let { status.copy(simulated = it) }
                else -> null
            }
            if (updated != null) {
                status = updated
                anyField = true
            }
        }
        return if (anyField) Message.Status(status) else null
    }

    /** "1,12,33,46,U": automático, duración (s), suelo antes (%), suelo después (%), motivo. */
    private fun parseIrrigationEvent(body: String): Message? {
        val parts = body.split(',').map { it.trim() }
        if (parts.size != 5) return null
        val automatic = parseFlag(parts[0]) ?: return null
        val duration = parts[1].toIntOrNull()?.takeIf { it in 0..MAX_IRRIGATION_SECONDS } ?: return null
        val before = parts[2].toIntOrNull()?.takeIf { it in 0..100 } ?: return null
        val after = parts[3].toIntOrNull()?.takeIf { it in 0..100 } ?: return null
        val reason = parts[4].singleOrNull()?.let { StopReason.fromCode(it) } ?: return null
        return Message.Irrigation(IrrigationEvent(automatic, duration, before, after, reason))
    }

    private fun parseFlag(value: String): Boolean? = when (value) {
        "1" -> true
        "0" -> false
        else -> null
    }
}
