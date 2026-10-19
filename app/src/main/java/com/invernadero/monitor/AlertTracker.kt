package com.invernadero.monitor

/**
 * Alertas de temperatura con histéresis. Cada alerta se emite una sola vez al cruzar su límite
 * y solo se rearma cuando la temperatura se aleja de él al menos [hysteresis] grados, para que
 * una lectura que oscila alrededor del umbral no genere una notificación en cada subida.
 */
class AlertTracker(private val hysteresis: Double = DEFAULT_HYSTERESIS) {

    private var highArmed = true
    private var lowArmed = true

    /** true si corresponde notificar que la temperatura superó [threshold]. */
    fun checkHigh(temperature: Double, threshold: Double): Boolean {
        if (temperature <= threshold - hysteresis) highArmed = true
        if (temperature > threshold && highArmed) {
            highArmed = false
            return true
        }
        return false
    }

    /** true si corresponde notificar riesgo de helada: la temperatura bajó de [minimum]. */
    fun checkLow(temperature: Double, minimum: Double): Boolean {
        if (temperature >= minimum + hysteresis) lowArmed = true
        if (temperature < minimum && lowArmed) {
            lowArmed = false
            return true
        }
        return false
    }

    companion object {
        const val DEFAULT_HYSTERESIS = 1.0
        const val DEFAULT_FROST_CELSIUS = 5
    }
}
