package com.invernadero.monitor

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

sealed interface HistoryItem {
    data class Header(val title: String) : HistoryItem
    data class Row(val reading: SensorReading) : HistoryItem
}

class HistoryAdapter(
    private var items: List<HistoryItem> = emptyList(),
    private var threshold: Double = MainActivity.DEFAULT_THRESHOLD_CELSIUS
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvHeader)
    }

    class RowHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconBg: FrameLayout = view.findViewById(R.id.iconBg)
        val icon: ImageView = view.findViewById(R.id.ivLevel)
        val value: TextView = view.findViewById(R.id.tvItemValue)
        val time: TextView = view.findViewById(R.id.tvItemTimestamp)
        val humidity: TextView = view.findViewById(R.id.tvItemHumidity)
    }

    fun submitList(newItems: List<HistoryItem>, newThreshold: Double) {
        items = newItems
        threshold = newThreshold
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int =
        if (items[position] is HistoryItem.Header) TYPE_HEADER else TYPE_ROW

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(inflater.inflate(R.layout.item_history_header, parent, false))
        } else {
            RowHolder(inflater.inflate(R.layout.item_history, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is HistoryItem.Header -> (holder as HeaderHolder).title.text = item.title
            is HistoryItem.Row -> bindRow(holder as RowHolder, item.reading)
        }
    }

    private fun bindRow(holder: RowHolder, reading: SensorReading) {
        val context = holder.itemView.context
        val (colorRes, containerRes) = when (TemperatureStats.level(reading.value, threshold)) {
            2 -> R.color.status_bad to R.color.status_bad_container
            1 -> R.color.status_warn to R.color.status_warn_container
            else -> R.color.status_ok to R.color.status_ok_container
        }
        ViewCompat.setBackgroundTintList(
            holder.iconBg,
            ColorStateList.valueOf(ContextCompat.getColor(context, containerRes))
        )
        ImageViewCompat.setImageTintList(
            holder.icon,
            ColorStateList.valueOf(ContextCompat.getColor(context, colorRes))
        )
        holder.value.text = context.getString(R.string.main_temp_format, reading.value)
        holder.time.text = timeFormat.format(reading.timestamp)
        val humidity = reading.humidity
        if (humidity != null) {
            holder.humidity.visibility = View.VISIBLE
            holder.humidity.text = context.getString(R.string.history_humidity, humidity)
        } else {
            holder.humidity.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = items.size

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ROW = 1
    }
}
