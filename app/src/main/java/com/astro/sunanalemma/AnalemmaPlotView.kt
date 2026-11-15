package com.astro.sunanalemma

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

/**
 * Custom Canvas View that renders the Sun Analemma figure:
 * - Whole-year daily points (blue dots / curve)
 * - First day of each month (`1/1` .. `12/1`, open black circles + date labels)
 * - Summer & Winter Solstices, Spring & Fall Equinoxes (black squares + callout labels)
 * - Interactive touch selection to inspect any day's ECI, dUT1, Polar Motion, dCIP, and AER values
 * - Direct rendering onto both screen Canvas, high-res Bitmap (for PNG export), and PdfDocument Canvas (for vector PDF export).
 */
class AnalemmaPlotView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var analemmaResult: AnalemmaResult? = null
        set(value) {
            field = value
            selectedPoint = value?.equinoxSolsticePoints?.firstOrNull() ?: value?.allPoints?.firstOrNull()
            invalidate()
        }

    var selectedPoint: DailyAnalemmaPoint? = null
        private set

    var onPointSelectedListener: ((DailyAnalemmaPoint) -> Unit)? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        style = Paint.Style.FILL
    }
    private val plotFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#334155")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E2E8F0")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val dailyDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0284C7") // MATLAB-style primary blue dots
        style = Paint.Style.FILL
    }
    private val firstOfMonthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A") // Black open circles 'ko'
        style = Paint.Style.STROKE
        strokeWidth = 3.0f
    }
    private val solsticeEquinoxFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DC2626") // Highlight fill inside black square
        style = Paint.Style.FILL
    }
    private val solsticeEquinoxStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A") // Black square border 'ks'
        style = Paint.Style.STROKE
        strokeWidth = 3.2f
    }
    private val selectedHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F59E0B")
        style = Paint.Style.STROKE
        strokeWidth = 4.5f
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A")
        textSize = 32f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#475569")
        textSize = 23f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val axisLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E293B")
        textSize = 26f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val tickTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#334155")
        textSize = 21f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    }
    private val pointLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A")
        textSize = 22f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val seasonalLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#991B1B")
        textSize = 21f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val legendBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F8FAFC")
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        renderAnalemmaToCanvas(canvas, width.toFloat(), height.toFloat(), includeSelectionRing = true)
    }

    /**
     * Renders the complete publication-grade Analemma plot onto any target Canvas
     * (screen View, high-res PNG Bitmap, or vector PDF page).
     */
    fun renderAnalemmaToCanvas(
        canvas: Canvas,
        canvasWidth: Float,
        canvasHeight: Float,
        includeSelectionRing: Boolean = false
    ) {
        canvas.drawRect(0f, 0f, canvasWidth, canvasHeight, bgPaint)
        val res = analemmaResult ?: return
        if (res.allPoints.isEmpty()) return

        val scale = min(canvasWidth / 1000f, canvasHeight / 1100f).coerceAtLeast(0.55f)
        titlePaint.textSize = 30f * scale
        subtitlePaint.textSize = 21f * scale
        axisLabelPaint.textSize = 24f * scale
        tickTextPaint.textSize = 19f * scale
        pointLabelPaint.textSize = 20f * scale
        seasonalLabelPaint.textSize = 19f * scale

        val padLeft = 115f * scale
        val padRight = 55f * scale
        val padTop = 125f * scale
        val padBottom = 165f * scale

        val plotRect = RectF(
            padLeft,
            padTop,
            canvasWidth - padRight,
            canvasHeight - padBottom
        )

        // Header Title & Geopotential Location Subtitle
        val titleText = "Sun Analemma (${res.startDate} to ${res.endDate} @ %02d:%02d UTC)".format(res.utcHour, res.utcMinute)
        val locText = "Geopotential LLA: Lat %.4f°, Lon %.4f°, Alt %.1f m | DE405/VSOP87 + IERS EOP".format(
            res.location.latitudeDeg,
            res.location.longitudeDeg,
            res.location.altitudeMeters
        )
        canvas.drawText(titleText, canvasWidth / 2f, 48f * scale, titlePaint)
        canvas.drawText(locText, canvasWidth / 2f, 84f * scale, subtitlePaint)

        // Compute Azimuth & Elevation bounds (handling 0/360 wrap-around gracefully for Southern Hemisphere)
        val rawAzimuths = res.allPoints.map { it.azimuthDeg }
        val unwrapAroundNorth = (rawAzimuths.maxOrNull() ?: 0.0) - (rawAzimuths.minOrNull() ?: 0.0) > 180.0
        fun displayAz(az: Double): Double = if (unwrapAroundNorth && az > 180.0) az - 360.0 else az

        val azList = res.allPoints.map { displayAz(it.azimuthDeg) }
        val elList = res.allPoints.map { it.elevationDeg }

        val minAzData = azList.minOrNull() ?: 170.0
        val maxAzData = azList.maxOrNull() ?: 190.0
        val minElData = elList.minOrNull() ?: 10.0
        val maxElData = elList.maxOrNull() ?: 65.0

        val azMargin = max((maxAzData - minAzData) * 0.16, 1.5)
        val elMargin = max((maxElData - minElData) * 0.12, 3.0)

        val minAz = floor(minAzData - azMargin)
        val maxAz = ceil(maxAzData + azMargin)
        val minEl = floor(minElData - elMargin)
        val maxEl = ceil(maxElData + elMargin)

        fun azToX(az: Double): Float {
            val dAz = displayAz(az)
            return (plotRect.left + ((dAz - minAz) / (maxAz - minAz)) * plotRect.width()).toFloat()
        }

        fun elToY(el: Double): Float {
            return (plotRect.bottom - ((el - minEl) / (maxEl - minEl)) * plotRect.height()).toFloat()
        }

        // Draw Grid Lines and Tick Labels
        val numAzTicks = 6
        tickTextPaint.textAlign = Paint.Align.CENTER
        for (i in 0..numAzTicks) {
            val azVal = minAz + i * (maxAz - minAz) / numAzTicks
            val x = (plotRect.left + (i.toFloat() / numAzTicks) * plotRect.width())
            canvas.drawLine(x, plotRect.top, x, plotRect.bottom, gridPaint)
            val normAz = ((azVal % 360.0) + 360.0) % 360.0
            canvas.drawText("%.1f°".format(normAz), x, plotRect.bottom + 32f * scale, tickTextPaint)
        }

        val numElTicks = 6
        tickTextPaint.textAlign = Paint.Align.RIGHT
        for (i in 0..numElTicks) {
            val elVal = minEl + i * (maxEl - minEl) / numElTicks
            val y = (plotRect.bottom - (i.toFloat() / numElTicks) * plotRect.height())
            canvas.drawLine(plotRect.left, y, plotRect.right, y, gridPaint)
            canvas.drawText("%.1f°".format(elVal), plotRect.left - 14f * scale, y + 7f * scale, tickTextPaint)
        }

        // Draw Plot Border
        canvas.drawRect(plotRect, plotFramePaint)

        // Axis Titles
        canvas.drawText(
            "Azimuth (degrees)",
            plotRect.centerX(),
            plotRect.bottom + 74f * scale,
            axisLabelPaint
        )
        canvas.save()
        val yLabelX = 34f * scale
        val yLabelY = plotRect.centerY()
        canvas.rotate(-90f, yLabelX, yLabelY)
        canvas.drawText("Elevation (degrees)", yLabelX, yLabelY, axisLabelPaint)
        canvas.restore()

        // 1. Plot all daily points of the year ('.')
        val dotRadius = 3.8f * scale
        for (pt in res.allPoints) {
            val x = azToX(pt.azimuthDeg)
            val y = elToY(pt.elevationDeg)
            canvas.drawCircle(x, y, dotRadius, dailyDotPaint)
        }

        // 2. Plot First Day of Each Month ('ko' + 'M/1' labels)
        val circleRadius = 9.0f * scale
        pointLabelPaint.textAlign = Paint.Align.LEFT
        for (pt in res.firstOfMonthPoints) {
            val x = azToX(pt.azimuthDeg)
            val y = elToY(pt.elevationDeg)
            canvas.drawCircle(x, y, circleRadius, firstOfMonthPaint)
            val labelText = "  ${pt.utc.month}/${pt.utc.day}"
            canvas.drawText(labelText, x + 6f * scale, y + 6f * scale, pointLabelPaint)
        }

        // 3. Plot Solstices and Equinoxes ('ks' + labels)
        val sqHalf = 9.5f * scale
        for (pt in res.equinoxSolsticePoints) {
            val x = azToX(pt.azimuthDeg)
            val y = elToY(pt.elevationDeg)
            val sqRect = RectF(x - sqHalf, y - sqHalf, x + sqHalf, y + sqHalf)
            canvas.drawRect(sqRect, solsticeEquinoxFillPaint)
            canvas.drawRect(sqRect, solsticeEquinoxStrokePaint)

            val shortTag = when (pt.specialDayType) {
                SpecialDayType.SPRING_EQUINOX -> "Spring Eq (${pt.utc.formatMonthDay()})"
                SpecialDayType.SUMMER_SOLSTICE -> "Summer Sol (${pt.utc.formatMonthDay()})"
                SpecialDayType.FALL_EQUINOX -> "Fall Eq (${pt.utc.formatMonthDay()})"
                SpecialDayType.WINTER_SOLSTICE -> "Winter Sol (${pt.utc.formatMonthDay()})"
                else -> pt.utc.formatMonthDay()
            }
            seasonalLabelPaint.textAlign = if (x > plotRect.centerX()) Paint.Align.RIGHT else Paint.Align.LEFT
            val labelOffsetX = if (x > plotRect.centerX()) -14f * scale else 14f * scale
            canvas.drawText(shortTag, x + labelOffsetX, y - 12f * scale, seasonalLabelPaint)
        }

        // Optional selected point ring
        if (includeSelectionRing) {
            selectedPoint?.let { sel ->
                val sx = azToX(sel.azimuthDeg)
                val sy = elToY(sel.elevationDeg)
                canvas.drawCircle(sx, sy, 15f * scale, selectedHighlightPaint)
            }
        }

        // 4. Draw Bottom Legend Bar
        val legendTop = plotRect.bottom + 94f * scale
        val legendRect = RectF(plotRect.left, legendTop, plotRect.right, canvasHeight - 16f * scale)
        canvas.drawRoundRect(legendRect, 10f * scale, 10f * scale, legendBoxPaint)
        canvas.drawRoundRect(legendRect, 10f * scale, 10f * scale, gridPaint)

        val legY = legendRect.centerY() + 6f * scale
        val colW = legendRect.width() / 3f

        // Legend Item 1: Whole Year Daily Points
        val c1 = legendRect.left + 28f * scale
        canvas.drawCircle(c1, legY - 6f * scale, 5f * scale, dailyDotPaint)
        tickTextPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Daily Sun Position", c1 + 14f * scale, legY, tickTextPaint)

        // Legend Item 2: First Day of Month (ko)
        val c2 = legendRect.left + colW + 18f * scale
        canvas.drawCircle(c2, legY - 6f * scale, 8f * scale, firstOfMonthPaint)
        canvas.drawText("1st of Month (M/1)", c2 + 16f * scale, legY, tickTextPaint)

        // Legend Item 3: Equinoxes & Solstices (ks)
        val c3 = legendRect.left + 2f * colW + 12f * scale
        val lSq = 8f * scale
        val lRect = RectF(c3 - lSq, legY - 6f * scale - lSq, c3 + lSq, legY - 6f * scale + lSq)
        canvas.drawRect(lRect, solsticeEquinoxFillPaint)
        canvas.drawRect(lRect, solsticeEquinoxStrokePaint)
        canvas.drawText("Solstice / Equinox", c3 + 16f * scale, legY, tickTextPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
            val res = analemmaResult ?: return false
            if (res.allPoints.isEmpty()) return false

            val scale = min(width / 1000f, height / 1100f).coerceAtLeast(0.55f)
            val plotRect = RectF(
                115f * scale,
                125f * scale,
                width - 55f * scale,
                height - 165f * scale
            )

            val rawAzimuths = res.allPoints.map { it.azimuthDeg }
            val unwrapAroundNorth = (rawAzimuths.maxOrNull() ?: 0.0) - (rawAzimuths.minOrNull() ?: 0.0) > 180.0
            fun displayAz(az: Double): Double = if (unwrapAroundNorth && az > 180.0) az - 360.0 else az

            val azList = res.allPoints.map { displayAz(it.azimuthDeg) }
            val elList = res.allPoints.map { it.elevationDeg }
            val minAz = floor((azList.minOrNull() ?: 170.0) - max(((azList.maxOrNull() ?: 190.0) - (azList.minOrNull() ?: 170.0)) * 0.16, 1.5))
            val maxAz = ceil((azList.maxOrNull() ?: 190.0) + max(((azList.maxOrNull() ?: 190.0) - (azList.minOrNull() ?: 170.0)) * 0.16, 1.5))
            val minEl = floor((elList.minOrNull() ?: 10.0) - max(((elList.maxOrNull() ?: 65.0) - (elList.minOrNull() ?: 10.0)) * 0.12, 3.0))
            val maxEl = ceil((elList.maxOrNull() ?: 65.0) + max(((elList.maxOrNull() ?: 65.0) - (elList.minOrNull() ?: 10.0)) * 0.12, 3.0))

            var bestPt: DailyAnalemmaPoint? = null
            var bestDistSq = Float.MAX_VALUE
            for (pt in res.allPoints) {
                val px = (plotRect.left + ((displayAz(pt.azimuthDeg) - minAz) / (maxAz - minAz)) * plotRect.width()).toFloat()
                val py = (plotRect.bottom - ((pt.elevationDeg - minEl) / (maxEl - minEl)) * plotRect.height()).toFloat()
                val dx = event.x - px
                val dy = event.y - py
                val d2 = dx * dx + dy * dy
                if (d2 < bestDistSq) {
                    bestDistSq = d2
                    bestPt = pt
                }
            }
            if (bestPt != null) {
                selectedPoint = bestPt
                onPointSelectedListener?.invoke(bestPt)
                invalidate()
            }
            return true
        }
        return super.onTouchEvent(event)
    }
}
