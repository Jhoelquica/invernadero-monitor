package com.invernadero.monitor

import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pruebas de sistema (UI) de la pantalla de login. Solo cubren validacion
 * en el cliente: no dependen de red ni de un backend disponible.
 */
@RunWith(AndroidJUnit4::class)
class LoginActivityTest {

    @Before
    fun signOutBeforeEachTest() {
        FirebaseAuth.getInstance().signOut()
    }

    @get:Rule
    val activityRule = ActivityScenarioRule(LoginActivity::class.java)

    @Test
    fun elementosPrincipalesSonVisibles() {
        onView(withId(R.id.etEmail)).check(matches(isDisplayed()))
        onView(withId(R.id.etPassword)).check(matches(isDisplayed()))
        onView(withId(R.id.btnEmailAction)).check(matches(isDisplayed()))
        onView(withId(R.id.btnGoogleSignIn)).check(matches(isDisplayed()))
        onView(withId(R.id.tvToggleMode)).check(matches(isDisplayed()))
    }

    @Test
    fun formularioVacio_alTocarIniciarSesion_muestraErroresDeCampo() {
        onView(withId(R.id.btnEmailAction)).perform(click())

        onView(withId(R.id.tilEmail)).check(matches(hasNonNullError()))
        onView(withId(R.id.tilPassword)).check(matches(hasNonNullError()))
    }

    @Test
    fun correoInvalido_muestraErrorSoloEnEmail() {
        onView(withId(R.id.etEmail)).perform(replaceText("no-es-un-correo"), closeSoftKeyboard())
        onView(withId(R.id.etPassword)).perform(replaceText("123456"), closeSoftKeyboard())
        onView(withId(R.id.btnEmailAction)).perform(click())

        onView(withId(R.id.tilEmail)).check(matches(hasNonNullError()))
        onView(withId(R.id.tilPassword)).check(matches(hasNoError()))
    }

    @Test
    fun contrasenaCorta_muestraErrorSoloEnPassword() {
        onView(withId(R.id.etEmail)).perform(replaceText("prueba@correo.com"), closeSoftKeyboard())
        onView(withId(R.id.etPassword)).perform(replaceText("123"), closeSoftKeyboard())
        onView(withId(R.id.btnEmailAction)).perform(click())

        onView(withId(R.id.tilEmail)).check(matches(hasNoError()))
        onView(withId(R.id.tilPassword)).check(matches(hasNonNullError()))
    }

    @Test
    fun alTocarRegistrate_cambianLosTextosDeModo() {
        onView(withId(R.id.tvToggleMode)).perform(click())

        onView(withId(R.id.btnEmailAction))
            .check(matches(withText(R.string.login_btn_register)))
        onView(withId(R.id.tvToggleMode))
            .check(matches(withText(R.string.login_toggle_to_signin)))
    }

    private fun hasNonNullError(): Matcher<View> = object : TypeSafeMatcher<View>() {
        override fun describeTo(description: Description) {
            description.appendText("TextInputLayout con un error visible")
        }

        override fun matchesSafely(item: View): Boolean =
            item is TextInputLayout && !item.error.isNullOrEmpty()
    }

    private fun hasNoError(): Matcher<View> = object : TypeSafeMatcher<View>() {
        override fun describeTo(description: Description) {
            description.appendText("TextInputLayout sin error")
        }

        override fun matchesSafely(item: View): Boolean =
            item is TextInputLayout && item.error.isNullOrEmpty()
    }
}
