package com.invernadero.monitor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

class HistoryAdapter(
    private var readings: List<SensorReading> = emptyList()
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvValue: TextView = view.findViewById(R.id.tvItemValue)
        val tvTimestamp: TextView = view.findViewById(R.id.tvItemTimestamp)
    }

    fun submitList(newReadings: List<SensorReading>) {
        readings = newReadings
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val reading = readings[position]
        holder.tvValue.text = holder.itemView.context.getString(
            R.string.main_temp_format,
            reading.value
        )
        holder.tvTimestamp.text = dateFormat.format(reading.timestamp)
    }

    override fun getItemCount(): Int = readings.size
}
