package com.invernadero.monitor

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class HistoryActivity : AppCompatActivity() {

    private lateinit var adapter: HistoryAdapter
    private lateinit var emptyView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarHistory)
        toolbar.setNavigationOnClickListener { finish() }

        emptyView = findViewById(R.id.tvEmptyHistory)
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
                    adapter.submitList(readings)
                    emptyView.visibility = if (readings.isEmpty()) View.VISIBLE else View.GONE
                }

                override fun onCancelled(error: DatabaseError) {
                    // Se conserva la última lista cargada.
                }
            })
    }

    companion object {
        private const val MAX_ITEMS = 200
    }
}
