package com.invernadero.monitor

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Pruebas de integracion reales contra Firebase (Auth + Realtime Database),
 * usando el SDK de Android tal como lo hace la app, no HTTP directo como JMeter.
 * Requieren conexion a internet desde el dispositivo/emulador.
 */
@RunWith(AndroidJUnit4::class)
class FirebaseIntegrationTest {

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()
    private var testTimestamp = 0L

    @Before
    fun signIn() {
        val latch = CountDownLatch(1)
        auth.signInWithEmailAndPassword(TEST_EMAIL, TEST_PASSWORD)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    latch.countDown()
                } else {
                    auth.createUserWithEmailAndPassword(TEST_EMAIL, TEST_PASSWORD)
                        .addOnCompleteListener { latch.countDown() }
                }
            }
        assertTrue("No se pudo iniciar sesion de prueba", latch.await(20, TimeUnit.SECONDS))
        testTimestamp = System.currentTimeMillis()
    }

    @After
    fun cleanupTestNode() {
        val latch = CountDownLatch(1)
        database.getReference("temperature/history/$testTimestamp")
            .removeValue()
            .addOnCompleteListener { latch.countDown() }
        latch.await(10, TimeUnit.SECONDS)
    }

    @Test
    fun escrituraYLecturaAutenticada_coincideConElOriginal() {
        val original = SensorReading(value = 27.3, timestamp = testTimestamp, humidity = 55.0)

        val writeLatch = CountDownLatch(1)
        var writeOk = false
        database.getReference("temperature/history/$testTimestamp").setValue(original)
            .addOnCompleteListener { task ->
                writeOk = task.isSuccessful
                writeLatch.countDown()
            }
        assertTrue("Timeout esperando la escritura", writeLatch.await(15, TimeUnit.SECONDS))
        assertTrue("La escritura autenticada deberia aceptarse", writeOk)

        val readLatch = CountDownLatch(1)
        var readValue: SensorReading? = null
        database.getReference("temperature/history/$testTimestamp").get()
            .addOnCompleteListener { task ->
                readValue = task.result?.getValue(SensorReading::class.java)
                readLatch.countDown()
            }
        assertTrue("Timeout esperando la lectura", readLatch.await(15, TimeUnit.SECONDS))
        assertEquals(original, readValue)
    }

    @Test
    fun escrituraSinAutenticar_esRechazadaPorLasReglas() {
        auth.signOut()
        // El SDK de Realtime Database renegocia su token de forma asincrona tras
        // signOut(); sin esta espera, una escritura inmediata puede colarse todavia
        // autenticada por la conexion anterior (falso negativo en la prueba).
        Thread.sleep(1500)

        val latch = CountDownLatch(1)
        var succeeded = true
        database.getReference("temperature/current").setValue(999.0)
            .addOnCompleteListener { task ->
                succeeded = task.isSuccessful
                latch.countDown()
            }
        assertTrue("Timeout esperando el rechazo", latch.await(15, TimeUnit.SECONDS))
        assertFalse("Firebase no deberia aceptar escrituras sin autenticar", succeeded)
    }

    companion object {
        private const val TEST_EMAIL = "pruebas.instrumentadas@invernadero-monitor.test"
        private const val TEST_PASSWORD = "Pruebas123"
    }
}
