package com.invernadero.monitor

/**
 * Espejo de un nodo hijo de "irrigation/events" en Firebase Realtime Database.
 * Los valores por defecto son obligatorios para que el SDK de Firebase pueda
 * deserializar el objeto con snapshot.getValue(IrrigationRecord::class.java).
 */
data class IrrigationRecord(
    val timestamp: Long = 0L,
    val automatic: Boolean = false,
    val durationSeconds: Int = 0,
    val soilBefore: Int = 0,
    val soilAfter: Int = 0,
    val reason: String = "",
    val verdict: String = "",
    val simulated: Boolean = false
)
