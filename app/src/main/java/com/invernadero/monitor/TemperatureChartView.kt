package com.invernadero.monitor

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Gráfico de línea dibujado con Canvas, sin dependencias externas, para el
 * histórico reciente de temperatura. Usa los colores del tema (claro/oscuro).
 */
class TemperatureChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var readings: List<SensorReading> = emptyList()
    private var threshold: Double? = null

    private val density = resources.displayMetrics.density
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private val primary = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
    private val outline = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline)
    private val onSurfaceVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant)
    private val badColor = ContextCompat.getColor(context, R.color.status_bad)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primary
        strokeWidth = 3f * density
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primary
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ColorUtils.setAlphaComponent(outline, 160)
        strokeWidth = 1f * density
    }
    private val thresholdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = badColor
        strokeWidth = 1.5f * density
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f * density, 6f * density), 0f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = onSurfaceVariant
        textSize = 11f * density
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = onSurfaceVariant
        textSize = 14f * density
        textAlign = Paint.Align.CENTER
    }

    init {
        contentDescription = context.getString(R.string.main_chart_title)
    }

    fun setData(newReadings: List<SensorReading>) {
        readings = newReadings.sortedBy { it.timestamp }
        invalidate()
    }

    fun setThreshold(value: Double?) {
        threshold = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val left = 44f * density
        val right = width - 8f * density
        val top = 8f * density
        val bottom = height - 24f * density

        if (readings.isEmpty()) {
            canvas.drawText(
                context.getString(R.string.main_chart_empty),
                width / 2f,
                height / 2f,
                emptyPaint
            )
            return
        }

        val values = readings.map { it.value }
        var minValue = minOf(values.min(), threshold ?: Double.MAX_VALUE)
        var maxValue = maxOf(values.max(), threshold ?: -Double.MAX_VALUE)
        if (maxValue - minValue < 2.0) {
            minValue -= 1.0
            maxValue += 1.0
        }
        val pad = (maxValue - minValue) * 0.1
        minValue -= pad
        maxValue += pad
        val range = maxValue - minValue

        fun yFor(v: Double) = bottom - ((v - minValue) / range * (bottom - top)).toFloat()

        for (i in 0..2) {
            val fraction = i / 2f
            val y = top + (bottom - top) * fraction
            canvas.drawLine(left, y, right, y, gridPaint)
            val value = maxValue - range * fraction
            canvas.drawText(String.format(Locale.US, "%.0f°", value), 4f * density, y + 4f * density, labelPaint)
        }

        threshold?.let {
            val y = yFor(it)
            canvas.drawLine(left, y, right, y, thresholdPaint)
        }

        val stepX = if (readings.size > 1) (right - left) / (readings.size - 1) else 0f
        fun xFor(index: Int) = if (readings.size > 1) left + stepX * index else (left + right) / 2f

        if (readings.size > 1) {
            val linePath = Path()
            val areaPath = Path()
            readings.forEachIndexed { index, reading ->
                val x = xFor(index)
                val y = yFor(reading.value)
                if (index == 0) {
                    linePath.moveTo(x, y)
                    areaPath.moveTo(x, bottom)
                    areaPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    areaPath.lineTo(x, y)
                }
            }
            areaPath.lineTo(xFor(readings.size - 1), bottom)
            areaPath.close()

            fillPaint.shader = LinearGradient(
                0f, top, 0f, bottom,
                ColorUtils.setAlphaComponent(primary, 90),
                ColorUtils.setAlphaComponent(primary, 0),
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(areaPath, fillPaint)
            canvas.drawPath(linePath, linePaint)
        }

        val lastIndex = readings.size - 1
        canvas.drawCircle(xFor(lastIndex), yFor(readings[lastIndex].value), 5f * density, pointPaint)

        val timeY = height - 6f * density
        labelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(timeFormat.format(readings.first().timestamp), left, timeY, labelPaint)
        if (readings.size > 1) {
            labelPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(timeFormat.format(readings.last().timestamp), right, timeY, labelPaint)
            labelPaint.textAlign = Paint.Align.LEFT
        }
    }
}
