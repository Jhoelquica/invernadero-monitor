package com.invernadero.monitor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityThresholdTest {

    @Test
    fun `temperatura por debajo del umbral no se considera alta`() {
        assertFalse(MainActivity.isAboveThreshold(25.0, threshold = 30.0))
    }

    @Test
    fun `temperatura igual al umbral no se considera alta`() {
        assertFalse(MainActivity.isAboveThreshold(30.0, threshold = 30.0))
    }

    @Test
    fun `temperatura por encima del umbral se considera alta`() {
        assertTrue(MainActivity.isAboveThreshold(31.5, threshold = 30.0))
    }

    @Test
    fun `usa el umbral por defecto cuando no se especifica uno`() {
        assertTrue(MainActivity.isAboveThreshold(MainActivity.DEFAULT_THRESHOLD_CELSIUS + 0.1))
        assertFalse(MainActivity.isAboveThreshold(MainActivity.DEFAULT_THRESHOLD_CELSIUS))
    }

    @Test
    fun `no dispara alerta si no hay lectura previa y la temperatura esta dentro del rango`() {
        assertFalse(
            MainActivity.shouldTriggerAlert(previousTemperature = null, currentTemperature = 20.0, threshold = 30.0)
        )
    }

    @Test
    fun `dispara alerta la primera vez que la temperatura cruza el umbral`() {
        assertTrue(
            MainActivity.shouldTriggerAlert(previousTemperature = 28.0, currentTemperature = 31.0, threshold = 30.0)
        )
    }

    @Test
    fun `no repite la alerta si la temperatura se mantiene alta`() {
        assertFalse(
            MainActivity.shouldTriggerAlert(previousTemperature = 31.0, currentTemperature = 32.0, threshold = 30.0)
        )
    }

    @Test
    fun `dispara nuevamente si la temperatura baja y vuelve a subir`() {
        assertFalse(
            MainActivity.shouldTriggerAlert(previousTemperature = 31.0, currentTemperature = 29.0, threshold = 30.0)
        )
        assertTrue(
            MainActivity.shouldTriggerAlert(previousTemperature = 29.0, currentTemperature = 31.0, threshold = 30.0)
        )
    }
}
