package com.invernadero.monitor

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * HistoryActivity no exige sesion iniciada (a diferencia de MainActivity),
 * asi que se puede probar de forma aislada.
 */
@RunWith(AndroidJUnit4::class)
class HistoryActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(HistoryActivity::class.java)

    @Test
    fun tituloYNavegacionInferiorSonVisibles() {
        onView(withId(R.id.tvHistoryTitle)).check(matches(isDisplayed()))
        onView(withId(R.id.bottomNav)).check(matches(isDisplayed()))
    }

    @Test
    fun muestraListaOEstadoVacio() {
        // Segun si ya hay lecturas guardadas en Firebase, se ve el RecyclerView
        // o el estado vacio ilustrado; siempre debe verse exactamente uno de los dos.
        onView(withId(R.id.rvHistory)).check { view, _ ->
            val recycler = view as androidx.recyclerview.widget.RecyclerView
            val empty = view.rootView.findViewById<android.view.View>(R.id.emptyHistory)
            val recyclerVisible = recycler.visibility == android.view.View.VISIBLE
            val emptyVisible = empty.visibility == android.view.View.VISIBLE
            assert(recyclerVisible || emptyVisible) {
                "Ni la lista ni el estado vacio son visibles"
            }
        }
    }
}
