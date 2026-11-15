package com.astro.sunanalemma

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.time.LocalDate
import kotlin.math.PI

/**
 * Main Activity providing:
 * 1) User inputs for Start Date & End Date range (defaulting to full year Jan 1 .. Dec 31 at 12:00 UTC)
 * 2) Geopotential coordinates input: Latitude (°N/S), Longitude (°E/W), and Altitude (m)
 *    + Quick presets for Royal Observatory Greenwich (MathWorks reference) and Saint Petersburg
 * 3) Optional TAI-UTC (dAT) override or automatic IERS leap-second lookup
 * 4) Interactive Sun Analemma Canvas plot + live telemetry inspector for steps 2-5
 *    (JD_TDB, Sun ECI vector, deltaUT1, Polar Motion [xp, yp], CIP displacement [dX, dY], and AER)
 * 5) One-tap export to vector PDF and high-resolution PNG
 */
class MainActivity : AppCompatActivity() {

    private lateinit var startDateInput: EditText
    private lateinit var endDateInput: EditText
    private lateinit var utcHourInput: EditText
    private lateinit var latInput: EditText
    private lateinit var lonInput: EditText
    private lateinit var altInput: EditText
    private lateinit var datInput: EditText
    private lateinit var telemetryText: TextView
    private lateinit var plotView: AnalemmaPlotView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFFF1F5F9.toInt())
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(14)
            setPadding(pad, pad, pad, pad)
        }
        scrollView.addView(root)

        // App Header
        val headerTitle = TextView(this).apply {
            text = "Sun Analemma Planetary Ephemeris Estimator"
            textSize = 20f
            setTextColor(0xFF0F172A.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val headerSub = TextView(this).apply {
            text = "UTC → TAI → TT → TDB → DE405/VSOP87 Sun ECI → IERS (ΔUT1, Polar Motion, ΔCIP) → ECI2AER"
            textSize = 12.5f
            setTextColor(0xFF475569.toInt())
            setPadding(0, dp(2), 0, dp(10))
        }
        root.addView(headerTitle)
        root.addView(headerSub)

        // Preset Buttons Row
        val presetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }
        val btnGreenwich = Button(this).apply {
            text = "Preset: Greenwich (2014)"
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                startDateInput.setText("2014-01-01")
                endDateInput.setText("2014-12-31")
                utcHourInput.setText("12:00")
                latInput.setText("51.48")
                lonInput.setText("-0.0015")
                altInput.setText("0.0")
                datInput.setText("35.0")
                recomputeAndPlot()
            }
        }
        val btnSpb = Button(this).apply {
            text = "Preset: St. Petersburg (2026)"
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                startDateInput.setText("2026-01-01")
                endDateInput.setText("2026-12-31")
                utcHourInput.setText("12:00")
                latInput.setText("59.9343")
                lonInput.setText("30.3351")
                altInput.setText("12.0")
                datInput.setText("37.0")
                recomputeAndPlot()
            }
        }
        presetRow.addView(btnGreenwich)
        presetRow.addView(btnSpb)
        root.addView(presetRow)

        // Step 1 & Step 5 Input Card
        startDateInput = createEditText("2014-01-01", InputType.TYPE_CLASS_TEXT)
        endDateInput = createEditText("2014-12-31", InputType.TYPE_CLASS_TEXT)
        utcHourInput = createEditText("12:00", InputType.TYPE_CLASS_TEXT)

        latInput = createEditText("51.48", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED)
        lonInput = createEditText("-0.0015", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED)
        altInput = createEditText("0.0", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED)
        datInput = createEditText("35.0", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)

        root.addView(makeLabeledRow("1) Start Date (YYYY-MM-DD):", startDateInput, "End Date:", endDateInput))
        root.addView(makeLabeledRow("UTC Time (HH:MM):", utcHourInput, "TAI-UTC dAT (s):", datInput))
        root.addView(makeLabeledRow("5) Latitude (°N+ / °S-):", latInput, "Longitude (°E+ / °W-):", lonInput))
        root.addView(makeSingleLabeledRow("Geopotential Altitude (m above WGS-84):", altInput))

        // Action Buttons: Estimate + Export PDF + Export PNG
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
            setPadding(0, dp(6), 0, dp(6))
        }
        val btnCalculate = Button(this).apply {
            text = "Estimate & Plot"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { recomputeAndPlot() }
        }
        val btnExportPdf = Button(this).apply {
            text = "Export PDF"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                val res = plotView.analemmaResult ?: return@setOnClickListener
                val pdfFile = ExportHelper.exportToPdf(this@MainActivity, plotView, res)
                Toast.makeText(this@MainActivity, "Saved PDF: ${pdfFile.name}", Toast.LENGTH_SHORT).show()
                ExportHelper.shareExportedFile(this@MainActivity, pdfFile, "application/pdf")
            }
        }
        val btnExportPng = Button(this).apply {
            text = "Export PNG"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                val res = plotView.analemmaResult ?: return@setOnClickListener
                val pngFile = ExportHelper.exportToPng(this@MainActivity, plotView, res)
                Toast.makeText(this@MainActivity, "Saved PNG: ${pngFile.name}", Toast.LENGTH_SHORT).show()
                ExportHelper.shareExportedFile(this@MainActivity, pngFile, "image/png")
            }
        }
        actionRow.addView(btnCalculate)
        actionRow.addView(btnExportPdf)
        actionRow.addView(btnExportPng)
        root.addView(actionRow)

        // Step 6: Custom Interactive Analemma Plot View
        plotView = AnalemmaPlotView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(460)
            )
            onPointSelectedListener = { pt ->
                updateTelemetryCard(pt)
            }
        }
        root.addView(plotView)

        // Telemetry Readout Card (Steps 2, 3, 4, 5 Inspector)
        telemetryText = TextView(this).apply {
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(0xFF1E293B.toInt())
            setBackgroundColor(0xFFFFFFFF.toInt())
            val p = dp(12)
            setPadding(p, p, p, p)
        }
        root.addView(telemetryText)

        setContentView(scrollView)
        recomputeAndPlot()
    }

    private fun recomputeAndPlot() {
        try {
            val startDate = LocalDate.parse(startDateInput.text.toString().trim())
            val endDate = LocalDate.parse(endDateInput.text.toString().trim())
            val timeParts = utcHourInput.text.toString().trim().split(":")
            val hour = timeParts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 12
            val minute = timeParts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0

            val lat = latInput.text.toString().trim().toDoubleOrNull() ?: 51.48
            val lon = lonInput.text.toString().trim().toDoubleOrNull() ?: -0.0015
            val alt = altInput.text.toString().trim().toDoubleOrNull() ?: 0.0
            val customDat = datInput.text.toString().trim().toDoubleOrNull()

            val result = AnalemmaEphemerisEngine.calculateAnalemma(
                startDate = if (endDate.isBefore(startDate)) endDate else startDate,
                endDate = if (endDate.isBefore(startDate)) startDate else endDate,
                utcHour = hour,
                utcMinute = minute,
                location = GeopotentialLocation(lat, lon, alt),
                customDeltaAtSec = customDat
            )

            plotView.analemmaResult = result
            plotView.selectedPoint?.let { updateTelemetryCard(it) }
        } catch (e: Exception) {
            Toast.makeText(this, "Check date/coordinate format: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateTelemetryCard(pt: DailyAnalemmaPoint) {
        val rad2arcsec = 180.0 * 3600.0 / PI
        val rad2mas = rad2arcsec * 1000.0
        val info = buildString {
            appendLine("=== Selected Day Ephemeris Breakdown (Tap Plot to Inspect) ===")
            appendLine("1) Date (UTC)       : ${pt.utc.formatIsoDate()} %02d:%02d:00 UTC (${pt.label ?: "Regular Day"})".format(pt.utc.hour, pt.utc.minute))
            appendLine("2) TAI-UTC (dAT)    : %.1f s | secTT = dAT + 32.184 = %.3f s".format(pt.deltaAtSec, pt.secTt))
            appendLine("   JD(TT) / JD(TDB) : %.6f / %.6f".format(pt.jdTt, pt.jdTdb))
            appendLine("   Sun ECI (m)      : [%.3e, %.3e, %.3e]".format(pt.sunPosEciMeters[0], pt.sunPosEciMeters[1], pt.sunPosEciMeters[2]))
            appendLine("3) MJD(UTC) & dUT1  : MJD %.2f | deltaUT1 = %+.4f s".format(pt.mjdUtc, pt.deltaUt1Sec))
            appendLine("4) Polar Motion     : xp = %.4f arcsec, yp = %.4f arcsec".format(pt.polarMotionRad[0] * rad2arcsec, pt.polarMotionRad[1] * rad2arcsec))
            appendLine("   CIP Offset (dCIP): dX = %+.2f mas, dY = %+.2f mas".format(pt.deltaCipRad[0] * rad2mas, pt.deltaCipRad[1] * rad2mas))
            append("5) Topocentric AER  : Azimuth = %.3f°, Elevation = %.3f°, Range = %.4f AU".format(
                pt.azimuthDeg,
                pt.elevationDeg,
                pt.slantRangeMeters / 149597870700.0
            ))
        }
        telemetryText.text = info
    }

    private fun createEditText(defaultText: String, inputTypeFlags: Int): EditText {
        return EditText(this).apply {
            setText(defaultText)
            inputType = inputTypeFlags
            textSize = 13.5f
            setSingleLine(true)
        }
    }

    private fun makeLabeledRow(label1: String, edit1: EditText, label2: String, edit2: EditText): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            val col1 = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(this@MainActivity).apply { text = label1; textSize = 11.5f; setTextColor(0xFF334155.toInt()) })
                addView(edit1)
            }
            val col2 = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(this@MainActivity).apply { text = label2; textSize = 11.5f; setTextColor(0xFF334155.toInt()) })
                addView(edit2)
            }
            addView(col1)
            addView(col2)
        }
    }

    private fun makeSingleLabeledRow(label: String, edit: EditText): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MainActivity).apply { text = label; textSize = 11.5f; setTextColor(0xFF334155.toInt()) })
            addView(edit)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
