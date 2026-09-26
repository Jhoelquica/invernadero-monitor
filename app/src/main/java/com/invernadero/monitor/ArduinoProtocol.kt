package com.invernadero.monitor

/**
 * Protocolo de líneas de texto entre el Arduino (HC-05) y la app.
 *
 * Arduino -> app:
 *   "T:24.5,H:60,L:1,M:0,B:255,S:30"  lectura periódica (cualquier subconjunto de campos)
 *   "L:1,M:0,B:128,S:30"              eco de estado tras un comando (sin T/H)
 *   "E:DHT"                           fallo del sensor
 *   "PONG"                            respuesta a PING
 * App -> Arduino: LED:1 | LED:0 | BRI:0-255 | AUTO:1 | AUTO:0 | SET:n | PING
 */
object ArduinoProtocol {

    const val MIN_VALID_CELSIUS = -40.0
    const val MAX_VALID_CELSIUS = 80.0
    const val MIN_THRESHOLD = 10
    const val MAX_THRESHOLD = 60

    const val CMD_PING = "PING"

    data class DeviceStatus(
        val temperature: Double? = null,
        val humidity: Double? = null,
        val ledOn: Boolean? = null,
        val autoMode: Boolean? = null,
        val brightness: Int? = null,
        val threshold: Int? = null
    )

    sealed interface Message {
        data class Status(val status: DeviceStatus) : Message
        data class SensorError(val sensor: String) : Message
        data object Pong : Message
    }

    fun ledCommand(on: Boolean) = "LED:${if (on) 1 else 0}"
    fun autoCommand(on: Boolean) = "AUTO:${if (on) 1 else 0}"
    fun brightnessCommand(value: Int) = "BRI:${value.coerceIn(0, 255)}"
    fun thresholdCommand(value: Int) = "SET:${value.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)}"

    fun parseLine(line: String): Message? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed == "PONG") return Message.Pong
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
                else -> null
            }
            if (updated != null) {
                status = updated
                anyField = true
            }
        }
        return if (anyField) Message.Status(status) else null
    }

    private fun parseFlag(value: String): Boolean? = when (value) {
        "1" -> true
        "0" -> false
        else -> null
    }
}
