package com.astro.sunanalemma

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Handles exporting the calculated Sun Analemma plot and ephemeris summary table
 * to high-resolution PNG and vector PDF files, and sharing/saving via Android FileProvider.
 */
object ExportHelper {

    fun exportToPng(context: Context, plotView: AnalemmaPlotView, result: AnalemmaResult): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val fileName = "Sun_Analemma_${result.startDate.year}_Lat${"%.2f".format(result.location.latitudeDeg)}.png"
        val outFile = File(exportDir, fileName)

        val bmpWidth = 1600
        val bmpHeight = 1800
        val bitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        plotView.renderAnalemmaToCanvas(canvas, bmpWidth.toFloat(), bmpHeight.toFloat(), includeSelectionRing = false)

        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
        }
        return outFile
    }

    fun exportToPdf(context: Context, plotView: AnalemmaPlotView, result: AnalemmaResult): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val fileName = "Sun_Analemma_${result.startDate.year}_Lat${"%.2f".format(result.location.latitudeDeg)}.pdf"
        val outFile = File(exportDir, fileName)

        val pdfDocument = PdfDocument()
        // A4 Portrait at 150 DPI equivalent: 1240 x 1754 points
        val pageInfo = PdfDocument.PageInfo.Builder(1240, 1754, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // Top 1250px: Vector Analemma Plot
        plotView.renderAnalemmaToCanvas(canvas, 1240f, 1240f, includeSelectionRing = false)

        // Bottom section: Ephemeris Key Dates Summary Table (Solstices, Equinoxes, Sample 1st of Month)
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 22f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#334155")
            textSize = 18f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }

        var y = 1290f
        canvas.drawText(
            "Planetary Ephemeris Summary (TAI-UTC = %.0f s, TT-TAI = 32.184 s)".format(
                result.allPoints.firstOrNull()?.deltaAtSec ?: 37.0
            ),
            70f,
            y,
            headerPaint
        )
        y += 34f
        canvas.drawText(
            "Event / Date (UTC)       MJD(UTC)    JD(TDB)        dUT1(s)   xp(arcsec)/yp(arcsec)       Azimuth(°)  Elevation(°)",
            70f,
            y,
            rowPaint
        )
        y += 26f

        val keyPoints = (result.equinoxSolsticePoints + result.firstOfMonthPoints.take(6)).distinctBy { it.utc.formatIsoDate() }
        for (pt in keyPoints) {
            val tag = (pt.label ?: pt.utc.formatIsoDate()).padEnd(22).take(22)
            val xpArcsec = pt.polarMotionRad[0] * (180.0 * 3600.0 / Math.PI)
            val ypArcsec = pt.polarMotionRad[1] * (180.0 * 3600.0 / Math.PI)
            val line = "%-22s %9.2f  %12.3f  %+7.4f   %5.3f/%5.3f   %9.3f°   %9.3f°".format(
                tag,
                pt.mjdUtc,
                pt.jdTdb,
                pt.deltaUt1Sec,
                xpArcsec,
                ypArcsec,
                pt.azimuthDeg,
                pt.elevationDeg
            )
            canvas.drawText(line, 70f, y, rowPaint)
            y += 26f
            if (y > 1710f) break
        }

        pdfDocument.finishPage(page)
        FileOutputStream(outFile).use { fos ->
            pdfDocument.writeTo(fos)
            fos.flush()
        }
        pdfDocument.close()
        return outFile
    }

    fun shareExportedFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Sun Analemma Ephemeris Export (${file.name})")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export / Share ${file.name}"))
    }
}
