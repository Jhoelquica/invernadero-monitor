package com.invernadero.monitor

import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var adapter: HistoryAdapter
    private lateinit var emptyView: View
    private val dayFormat = SimpleDateFormat("EEEE d 'de' MMMM", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_history)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.history_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        NavHelper.setup(this, findViewById<BottomNavigationView>(R.id.bottomNav), R.id.nav_history)

        emptyView = findViewById(R.id.emptyHistory)
        adapter = HistoryAdapter()

        findViewById<RecyclerView>(R.id.rvHistory).apply {
            layoutManager = LinearLayoutManager(this@HistoryActivity)
            adapter = this@HistoryActivity.adapter
        }

        observeHistory()
    }

    private fun observeHistory() {
        FirebaseDatabase.getInstance()
            .getReference("temperature/history")
            .limitToLast(MAX_ITEMS)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val readings = snapshot.children
                        .mapNotNull { it.getValue(SensorReading::class.java) }
                        .sortedByDescending { it.timestamp }
                    adapter.submitList(
                        groupByDay(readings),
                        AppPrefs.getThreshold(this@HistoryActivity).toDouble()
                    )
                    emptyView.visibility = if (readings.isEmpty()) View.VISIBLE else View.GONE
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun groupByDay(readings: List<SensorReading>): List<HistoryItem> {
        val items = mutableListOf<HistoryItem>()
        var lastDay = Long.MIN_VALUE
        for (reading in readings) {
            val day = TemperatureStats.startOfDay(reading.timestamp)
            if (day != lastDay) {
                lastDay = day
                items += HistoryItem.Header(dayTitle(reading.timestamp))
            }
            items += HistoryItem.Row(reading)
        }
        return items
    }

    private fun dayTitle(timestamp: Long): String = when {
        DateUtils.isToday(timestamp) -> getString(R.string.history_today)
        DateUtils.isToday(timestamp + DateUtils.DAY_IN_MILLIS) -> getString(R.string.history_yesterday)
        else -> dayFormat.format(timestamp).replaceFirstChar { it.uppercase() }
    }

    companion object {
        private const val MAX_ITEMS = 300
    }
}
