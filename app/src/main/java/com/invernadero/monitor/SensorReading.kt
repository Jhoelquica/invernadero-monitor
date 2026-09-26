package com.invernadero.monitor

/**
 * Espejo de un nodo hijo de "temperature/history" en Firebase Realtime Database.
 * Los valores por defecto son obligatorios para que el SDK de Firebase pueda
 * deserializar el objeto con snapshot.getValue(SensorReading::class.java).
 */
data class SensorReading(
    val value: Double = 0.0,
    val timestamp: Long = 0L
)
