package com.andene.fern.canvas

import android.graphics.Path
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

/** How much of a QR code can be damaged and still read (roadmap item 144): about 7, 15, 25 or 30 %. */
enum class QrErrorCorrection(val level: ErrorCorrectionLevel) {
    LOW(ErrorCorrectionLevel.L), MEDIUM(ErrorCorrectionLevel.M), QUARTILE(ErrorCorrectionLevel.Q), HIGH(ErrorCorrectionLevel.H),
}

/**
 * A QR code's dark modules (roadmap item 144) as filled outlines: the modules merged into as few
 * outlines as their shapes allow, holes bridged (see [shapesOf]), in module units with the
 * top-left module at (0, 0). [size] is the number of modules per side.
 */
class QrOutlines(val outlines: List<List<WorldPoint>>, val size: Int)

/**
 * Encodes [text] (UTF-8) as a QR code with ZXing, the smallest version that fits at
 * [correction]. Null when the text is empty or too long for any QR code version.
 */
fun qrOutlines(text: String, correction: QrErrorCorrection): QrOutlines? {
    if (text.isEmpty()) return null
    val matrix = try {
        Encoder.encode(text, correction.level, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8")).matrix
    } catch (_: WriterException) {
        return null
    }
    val path = Path()
    for (y in 0 until matrix.height) {
        // Runs of dark modules along the row, as one rectangle each.
        var x = 0
        while (x < matrix.width) {
            if (matrix.get(x, y).toInt() != 1) {
                x++
                continue
            }
            val start = x
            while (x < matrix.width && matrix.get(x, y).toInt() == 1) x++
            path.addRect(start.toFloat(), y.toFloat(), x.toFloat(), (y + 1).toFloat(), Path.Direction.CW)
        }
    }
    // A union with nothing merges the touching rectangles into whole outlines.
    path.op(Path(), Path.Op.UNION)
    return QrOutlines(shapesOf(path, WorldPoint.Zero, bridgeHoles = true), matrix.width)
}

/** The light margin around a QR code that readers need, in modules (the standard's four). */
const val QR_QUIET_ZONE_MODULES = 4
