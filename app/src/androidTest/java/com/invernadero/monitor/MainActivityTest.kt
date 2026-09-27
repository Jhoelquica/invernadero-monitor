package com.invernadero.monitor

import android.Manifest
import android.os.Build
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.google.firebase.auth.FirebaseAuth
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * MainActivity exige sesion iniciada, asi que estas pruebas primero entran
 * con una cuenta de prueba dedicada (correo/contraseña) antes de lanzar la
 * actividad. No depende del Arduino/Bluetooth real: la app debe verse y
 * comportarse bien aunque el HC-05 no este disponible (como en el emulador).
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    private val permissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        GrantPermissionRule.grant(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        GrantPermissionRule.grant()
    }

    private val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(permissionRule).around(activityRule)

    @Test
    fun elementosPrincipalesSonVisibles() {
        onView(withId(R.id.tvTitle)).check(matches(isDisplayed()))
        onView(withId(R.id.chipBt)).check(matches(isDisplayed()))
        onView(withId(R.id.tvCurrentTemp)).check(matches(isDisplayed()))
        onView(withId(R.id.switchLed)).check(matches(isDisplayed()))
        onView(withId(R.id.switchAuto)).check(matches(isDisplayed()))
        onView(withId(R.id.sliderBrightness)).check(matches(isDisplayed()))
        onView(withId(R.id.sliderThreshold)).check(matches(isDisplayed()))
        // El grafico esta mas abajo en el NestedScrollView: hay que desplazarse antes de comprobarlo.
        onView(withId(R.id.chartTemperature)).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withId(R.id.bottomNav)).check(matches(isDisplayed()))
    }

    @Test
    fun tocarHistorialEnLaNavegacion_abreHistoryActivity() {
        onView(withId(R.id.nav_history)).perform(click())
        onView(withId(R.id.tvHistoryTitle)).check(matches(isDisplayed()))
    }

    companion object {
        private const val TEST_EMAIL = "pruebas.instrumentadas@invernadero-monitor.test"
        private const val TEST_PASSWORD = "Pruebas123"

        @JvmStatic
        @BeforeClass
        fun signInWithTestAccount() {
            val auth = FirebaseAuth.getInstance()
            val latch = CountDownLatch(1)

            auth.signInWithEmailAndPassword(TEST_EMAIL, TEST_PASSWORD)
                .addOnCompleteListener { signInTask ->
                    if (signInTask.isSuccessful) {
                        latch.countDown()
                    } else {
                        auth.createUserWithEmailAndPassword(TEST_EMAIL, TEST_PASSWORD)
                            .addOnCompleteListener { latch.countDown() }
                    }
                }

            latch.await(20, TimeUnit.SECONDS)
        }
    }
}
