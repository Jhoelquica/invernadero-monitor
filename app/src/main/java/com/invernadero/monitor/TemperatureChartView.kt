package com.invernadero.monitor

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Gráfico de línea minimalista dibujado con Canvas, sin dependencias externas,
 * para visualizar el histórico reciente de temperatura.
 */
class TemperatureChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var readings: List<SensorReading> = emptyList()

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5722")
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5722")
        style = Paint.Style.FILL
    }

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B0BEC5")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#607D8B")
        textSize = 28f
    }

    fun setData(newReadings: List<SensorReading>) {
        readings = newReadings.sortedBy { it.timestamp }
        invalidate()
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)

        val paddingLeft = 60f
        val paddingRight = 24f
        val paddingTop = 24f
        val paddingBottom = 40f

        val chartLeft = paddingLeft
        val chartRight = width - paddingRight
        val chartTop = paddingTop
        val chartBottom = height - paddingBottom

        canvas.drawLine(chartLeft, chartTop, chartLeft, chartBottom, axisPaint)
        canvas.drawLine(chartLeft, chartBottom, chartRight, chartBottom, axisPaint)

        if (readings.isEmpty()) return

        val values = readings.map { it.value }
        val minValue = values.min()
        val maxValue = values.max()
        val range = (maxValue - minValue).let { if (it < 1.0) 1.0 else it }

        canvas.drawText(
            String.format("%.1f", maxValue),
            4f,
            chartTop + labelPaint.textSize,
            labelPaint
        )
        canvas.drawText(
            String.format("%.1f", minValue),
            4f,
            chartBottom,
            labelPaint
        )

        if (readings.size < 2) return

        val stepX = (chartRight - chartLeft) / (readings.size - 1)
        var previousX = chartLeft
        var previousY = chartBottom - ((readings[0].value - minValue) / range * (chartBottom - chartTop)).toFloat()

        readings.forEachIndexed { index, reading ->
            val x = chartLeft + stepX * index
            val y = chartBottom - ((reading.value - minValue) / range * (chartBottom - chartTop)).toFloat()
            if (index > 0) {
                canvas.drawLine(previousX, previousY, x, y, linePaint)
            }
            canvas.drawCircle(x, y, 6f, pointPaint)
            previousX = x
            previousY = y
        }
    }
}
