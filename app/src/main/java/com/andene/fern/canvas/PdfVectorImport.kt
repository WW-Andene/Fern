package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater
import kotlin.math.abs
import kotlin.math.sqrt

/*
 * Vector import of PDF and PDF-compatible Adobe Illustrator files (roadmap item 103): the
 * pages' paths, with their fill and stroke colours, widths, caps, joins and dashes, as Fern
 * strokes - where the PDF import of item 59 places pages as pictures. Reads the file itself
 * (ISO 32000-1): objects found by scanning (so damaged cross-reference tables don't matter),
 * object streams, Flate-compressed streams, the page tree with inherited resources, form
 * XObjects, and the path, colour and graphics-state operators of content streams. Text, images,
 * shadings and clipping are skipped; encrypted files aren't read.
 */

/** One painted path from a page: its subpaths in page points (y up), and how it was painted. */
class ImportedPath(
    /** Each subpath's points, curves flattened, with whether it was closed. */
    val subpaths: List<Pair<List<WorldPoint>, Boolean>>,
    /** The fill colour (alpha included), or null when not filled. */
    val fill: Color?,
    /** Whether the fill uses the even-odd rule rather than non-zero winding. */
    val evenOdd: Boolean,
    /** The stroke colour (alpha included), or null when not stroked. */
    val stroke: Color?,
    /** Line width in page points, the transform's scale applied. */
    val width: Double,
    val cap: LineCap,
    val join: LineJoin,
    val dashed: Boolean,
    /** The PDF layer (optional content group) name the path is marked with, or null. */
    val layer: String?,
)

/** A page's paths and size in points. */
class ImportedVectorPage(val width: Double, val height: Double, val paths: List<ImportedPath>)

/**
 * A filled path's outlines (roadmap item 103): its [subpaths] closed and combined by the
 * even-odd or non-zero rule, holes bridged into their outline so each comes back as one
 * filled polygon (see [shapesOf]).
 */
fun importedFillOutlines(subpaths: List<List<WorldPoint>>, evenOdd: Boolean): List<List<WorldPoint>> {
    val usable = subpaths.filter { it.size >= 3 }
    if (usable.isEmpty()) return emptyList()
    val origin = usable[0][0]
    val path = android.graphics.Path()
    path.fillType = if (evenOdd) android.graphics.Path.FillType.EVEN_ODD else android.graphics.Path.FillType.WINDING
    for (subpath in usable) path.addPath(pathOf(subpath, origin))
    // A union with nothing resolves overlaps and the fill rule into plain outlines.
    path.op(android.graphics.Path(), android.graphics.Path.Op.UNION)
    return shapesOf(path, origin, bridgeHoles = true)
}

/** Reads every page's paths from the PDF or .ai [bytes]; null when it isn't a PDF, is encrypted, or has no page. */
fun readPdfVectors(bytes: ByteArray, maxPages: Int = MAX_VECTOR_PAGES): List<ImportedVectorPage>? {
    val document = PdfDocumentReader.parse(bytes) ?: return null
    val pages = document.pages().take(maxPages)
    if (pages.isEmpty()) return null
    return pages.map { page ->
        val box = (document.resolve(page["MediaBox"]) as? List<*>)?.map { (document.resolve(it) as? Number)?.toDouble() ?: 0.0 }
        val left = box?.getOrNull(0) ?: 0.0
        val bottom = box?.getOrNull(1) ?: 0.0
        val width = (box?.getOrNull(2) ?: DEFAULT_PAGE_SIZE) - left
        val height = (box?.getOrNull(3) ?: DEFAULT_PAGE_SIZE) - bottom
        val interpreter = ContentInterpreter(document)
        val resources = document.resolve(page["Resources"]) as? Map<*, *>
        // The media box's corner becomes the origin.
        interpreter.run(document.contentOf(page), resources, doubleArrayOf(1.0, 0.0, 0.0, 1.0, -left, -bottom), depth = 0)
        ImportedVectorPage(width, height, interpreter.paths)
    }
}

private const val MAX_VECTOR_PAGES = 64
private const val DEFAULT_PAGE_SIZE = 612.0 // US Letter width, points
// Form XObjects nested deeper than this are skipped (a guard against cycles).
private const val MAX_FORM_DEPTH = 8
// Segments a Bézier curve is flattened into.
private const val CURVE_SEGMENTS = 16

/** A PDF indirect reference. */
private data class PdfRef(val number: Int, val generation: Int)

/** A PDF name object (written /Name), kept apart from strings. */
private data class PdfName(val name: String)

/** A stream object: its dictionary and its raw (still encoded) bytes. */
private class PdfStream(val dictionary: Map<String, Any?>, val raw: ByteArray)

/** An operator keyword in a content stream (or obj, R, stream... while parsing the file). */
private data class PdfKeyword(val word: String)

/** A tokenizer and object parser over PDF syntax (ISO 32000-1, 7.2-7.3). */
private class PdfLexer(val data: ByteArray, var pos: Int = 0) {
    private fun isWhite(b: Int) = b == 0 || b == 9 || b == 10 || b == 12 || b == 13 || b == 32
    private fun isDelimiter(b: Int) = b == '('.code || b == ')'.code || b == '<'.code || b == '>'.code || b == '['.code ||
        b == ']'.code || b == '{'.code || b == '}'.code || b == '/'.code || b == '%'.code

    private fun peek(): Int = if (pos < data.size) data[pos].toInt() and 0xFF else -1

    fun skipWhitespace() {
        while (pos < data.size) {
            val b = peek()
            if (isWhite(b)) pos++
            else if (b == '%'.code) { while (pos < data.size && peek() != 10 && peek() != 13) pos++ }
            else return
        }
    }

    /** The next object or keyword, or null at the end. Arrays and dictionaries come whole. */
    fun next(): Any? {
        skipWhitespace()
        if (pos >= data.size) return null
        val b = peek()
        return when {
            b == '/'.code -> { pos++; PdfName(readRegular()) }
            b == '('.code -> { pos++; readLiteralString() }
            b == '<'.code && pos + 1 < data.size && data[pos + 1].toInt() == '<'.code -> { pos += 2; readDictionary() }
            b == '<'.code -> { pos++; readHexString() }
            b == '['.code -> { pos++; readArray() }
            b == ']'.code || b == '>'.code || b == '}'.code || b == '{'.code || b == ')'.code -> { pos++; PdfKeyword(b.toChar().toString()) }
            else -> {
                val word = readRegular()
                if (word.isEmpty()) { pos++; return PdfKeyword("") }
                word.toDoubleOrNull() ?: when (word) {
                    "true" -> true
                    "false" -> false
                    "null" -> null
                    else -> PdfKeyword(word)
                }
            }
        }
    }

    private fun readRegular(): String {
        val start = pos
        while (pos < data.size && !isWhite(peek()) && !isDelimiter(peek())) pos++
        val raw = String(data, start, pos - start, Charsets.ISO_8859_1)
        // Names may escape characters as #xx.
        return if ('#' in raw) Regex("#([0-9A-Fa-f]{2})").replace(raw) { it.groupValues[1].toInt(16).toChar().toString() } else raw
    }

    private fun readLiteralString(): ByteArray {
        val out = ByteArrayOutputStream()
        var depth = 1
        while (pos < data.size) {
            val c = peek()
            pos++
            when (c) {
                '('.code -> { depth++; out.write(c) }
                ')'.code -> { depth--; if (depth == 0) return out.toByteArray(); out.write(c) }
                '\\'.code -> {
                    val e = peek()
                    pos++
                    when (e) {
                        'n'.code -> out.write(10)
                        'r'.code -> out.write(13)
                        't'.code -> out.write(9)
                        'b'.code -> out.write(8)
                        'f'.code -> out.write(12)
                        in '0'.code..'7'.code -> {
                            var value = e - '0'.code
                            repeat(2) { if (peek() in '0'.code..'7'.code) { value = value * 8 + (peek() - '0'.code); pos++ } }
                            out.write(value and 0xFF)
                        }
                        10 -> {}
                        13 -> if (peek() == 10) pos++
                        else -> out.write(e)
                    }
                }
                else -> out.write(c)
            }
        }
        return out.toByteArray()
    }

    private fun readHexString(): ByteArray {
        val digits = StringBuilder()
        while (pos < data.size && peek() != '>'.code) {
            val c = peek().toChar()
            if (c.isLetterOrDigit()) digits.append(c)
            pos++
        }
        pos++
        if (digits.length % 2 == 1) digits.append('0')
        return ByteArray(digits.length / 2) { i -> digits.substring(2 * i, 2 * i + 2).toIntOrNull(16)?.toByte() ?: 0 }
    }

    private fun readArray(): List<Any?> {
        val items = ArrayList<Any?>()
        while (true) {
            skipWhitespace()
            if (pos >= data.size) return items
            if (peek() == ']'.code) { pos++; return reduceRefs(items) }
            items.add(next())
        }
    }

    private fun readDictionary(): Map<String, Any?> {
        val items = ArrayList<Any?>()
        while (true) {
            skipWhitespace()
            if (pos >= data.size) break
            if (peek() == '>'.code && pos + 1 < data.size && data[pos + 1].toInt() == '>'.code) { pos += 2; break }
            items.add(next())
        }
        val reduced = reduceRefs(items)
        val map = LinkedHashMap<String, Any?>()
        var i = 0
        while (i + 1 < reduced.size) {
            val key = reduced[i] as? PdfName
            if (key == null) { i++; continue }
            map[key.name] = reduced[i + 1]
            i += 2
        }
        return map
    }

    /** Folds "n g R" triples into references. */
    private fun reduceRefs(items: List<Any?>): List<Any?> {
        val out = ArrayList<Any?>(items.size)
        var i = 0
        while (i < items.size) {
            val a = items[i]
            if (a is Double && i + 2 < items.size && items[i + 1] is Double && items[i + 2] == PdfKeyword("R")) {
                out.add(PdfRef(a.toInt(), (items[i + 1] as Double).toInt()))
                i += 3
            } else {
                out.add(a)
                i++
            }
        }
        return out
    }
}

/** The objects of one PDF file, found by scanning, and the page tree. */
private class PdfDocumentReader private constructor(private val data: ByteArray) {
    private val objects = HashMap<Int, Any?>()

    companion object {
        private val OBJECT_HEADER = Regex("(\\d+)\\s+(\\d+)\\s+obj\\b")

        fun parse(bytes: ByteArray): PdfDocumentReader? {
            val text = String(bytes, Charsets.ISO_8859_1)
            if (!text.startsWith("%PDF") && text.indexOf("%PDF").let { it < 0 || it > 1024 }) return null
            val reader = PdfDocumentReader(bytes)
            for (match in OBJECT_HEADER.findAll(text)) {
                val number = match.groupValues[1].toIntOrNull() ?: continue
                val parsed = runCatching { reader.parseObjectAt(match.range.last + 1, text) }.getOrNull() ?: continue
                // Later definitions (incremental updates) replace earlier ones.
                reader.objects[number] = parsed.value
            }
            if (reader.objects.values.any { it is Map<*, *> && it.containsKey("Encrypt") } ||
                reader.objects.values.any { it is PdfStream && it.dictionary.containsKey("Encrypt") } ||
                Regex("trailer\\s*<<[^>]*?/Encrypt").containsMatchIn(text)
            ) return null
            reader.expandObjectStreams()
            return reader
        }
    }

    private class Parsed(val value: Any?)

    private fun parseObjectAt(offset: Int, text: String): Parsed {
        val lexer = PdfLexer(data, offset)
        val value = lexer.next()
        lexer.skipWhitespace()
        val save = lexer.pos
        val keyword = lexer.next()
        if (value is Map<*, *> && keyword == PdfKeyword("stream")) {
            // The data starts after the end of line following the keyword.
            var start = lexer.pos
            if (start < data.size && data[start].toInt() == 13) start++
            if (start < data.size && data[start].toInt() == 10) start++
            @Suppress("UNCHECKED_CAST")
            val dictionary = value as Map<String, Any?>
            val declared = (dictionary["Length"] as? Double)?.toInt()
            // The declared length when "endstream" follows it; otherwise (an indirect or wrong
            // length) the data runs to the next "endstream".
            val end = if (declared != null && declared >= 0 && start + declared <= data.size &&
                text.substring(start + declared, minOf(data.size, start + declared + 16)).trimStart().startsWith("endstream")
            ) start + declared else text.indexOf("endstream", start).let { if (it < 0) data.size else it }
            return Parsed(PdfStream(dictionary, data.copyOfRange(start, end)))
        }
        lexer.pos = save
        return Parsed(value)
    }

    /** Adds the objects packed in object streams (PDF 1.5+), unless defined directly. */
    private fun expandObjectStreams() {
        for (stream in objects.values.filterIsInstance<PdfStream>().toList()) {
            if ((stream.dictionary["Type"] as? PdfName)?.name != "ObjStm") continue
            val decoded = decode(stream) ?: continue
            val count = (resolve(stream.dictionary["N"]) as? Double)?.toInt() ?: continue
            val first = (resolve(stream.dictionary["First"]) as? Double)?.toInt() ?: continue
            val header = PdfLexer(decoded)
            val entries = (0 until count).mapNotNull {
                val number = (header.next() as? Double)?.toInt()
                val offset = (header.next() as? Double)?.toInt()
                if (number != null && offset != null) number to offset else null
            }
            for ((number, offset) in entries) {
                if (objects.containsKey(number) || first + offset >= decoded.size) continue
                objects[number] = runCatching { PdfLexer(decoded, first + offset).next() }.getOrNull()
            }
        }
    }

    /** [value] with references followed (a few levels at most). */
    fun resolve(value: Any?): Any? {
        var current = value
        repeat(8) {
            if (current !is PdfRef) return current
            current = objects[(current as PdfRef).number]
        }
        return null
    }

    fun dictionaryOf(value: Any?): Map<*, *>? = when (val v = resolve(value)) {
        is Map<*, *> -> v
        is PdfStream -> v.dictionary
        else -> null
    }

    /** A stream's decoded bytes: Flate and no filter are supported; null for anything else. */
    fun decode(stream: PdfStream): ByteArray? {
        val filters = when (val f = resolve(stream.dictionary["Filter"])) {
            null -> emptyList()
            is PdfName -> listOf(f.name)
            is List<*> -> f.map { (resolve(it) as? PdfName)?.name }
            else -> return null
        }
        var bytes = stream.raw
        for (filter in filters) {
            bytes = when (filter) {
                "FlateDecode", "Fl" -> inflate(bytes) ?: return null
                else -> return null
            }
        }
        val parms = dictionaryOf(stream.dictionary["DecodeParms"])
        val predictor = (resolve(parms?.get("Predictor")) as? Double)?.toInt() ?: 1
        if (predictor >= 10) {
            val columns = (resolve(parms?.get("Columns")) as? Double)?.toInt() ?: 1
            bytes = unpredictPng(bytes, columns)
        }
        return bytes
    }

    /** Every page dictionary in order, with inherited Resources and MediaBox filled in. */
    fun pages(): List<Map<String, Any?>> {
        val catalog = objects.values.lastOrNull { dictionaryOf(it)?.let { d -> (d["Type"] as? PdfName)?.name == "Catalog" } == true }
        val root = dictionaryOf(dictionaryOf(catalog)?.get("Pages")) ?: return emptyList()
        val pages = ArrayList<Map<String, Any?>>()
        fun walk(node: Map<*, *>, inherited: Map<String, Any?>, depth: Int) {
            if (depth > 32) return
            val here = HashMap(inherited)
            for (key in listOf("Resources", "MediaBox")) node[key]?.let { here[key] = it }
            val kids = resolve(node["Kids"]) as? List<*>
            if (kids == null || (node["Type"] as? PdfName)?.name == "Page") {
                @Suppress("UNCHECKED_CAST")
                pages.add((node as Map<String, Any?>) + here)
                return
            }
            for (kid in kids) dictionaryOf(kid)?.let { walk(it, here, depth + 1) }
        }
        walk(root, emptyMap(), 0)
        return pages
    }

    /** A page's content streams, decoded and joined. */
    fun contentOf(page: Map<String, Any?>): ByteArray {
        val streams = when (val c = resolve(page["Contents"])) {
            is PdfStream -> listOf(c)
            is List<*> -> c.mapNotNull { resolve(it) as? PdfStream }
            else -> emptyList()
        }
        val out = ByteArrayOutputStream()
        for (stream in streams) {
            decode(stream)?.let { out.write(it) }
            out.write('\n'.code)
        }
        return out.toByteArray()
    }

    fun streamOf(value: Any?): PdfStream? = resolve(value) as? PdfStream
}

private fun inflate(bytes: ByteArray): ByteArray? {
    val inflater = Inflater()
    return try {
        inflater.setInput(bytes)
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(65536)
        while (!inflater.finished()) {
            val n = inflater.inflate(chunk)
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            out.write(chunk, 0, n)
        }
        out.toByteArray()
    } catch (_: java.util.zip.DataFormatException) {
        null
    } finally {
        inflater.end()
    }
}

/** Undoes PNG row predictors (cross-reference and object streams use them), one byte per column. */
private fun unpredictPng(bytes: ByteArray, columns: Int): ByteArray {
    val row = columns + 1
    val out = ByteArrayOutputStream()
    val previous = ByteArray(columns)
    var i = 0
    while (i + row <= bytes.size) {
        val type = bytes[i].toInt()
        val current = ByteArray(columns)
        for (c in 0 until columns) {
            val raw = bytes[i + 1 + c].toInt() and 0xFF
            val left = if (c > 0) current[c - 1].toInt() and 0xFF else 0
            val up = previous[c].toInt() and 0xFF
            val upLeft = if (c > 0) previous[c - 1].toInt() and 0xFF else 0
            val value = when (type) {
                1 -> raw + left
                2 -> raw + up
                3 -> raw + (left + up) / 2
                4 -> {
                    val p = left + up - upLeft
                    val pa = abs(p - left); val pb = abs(p - up); val pc = abs(p - upLeft)
                    raw + if (pa <= pb && pa <= pc) left else if (pb <= pc) up else upLeft
                }
                else -> raw
            }
            current[c] = value.toByte()
        }
        out.write(current)
        current.copyInto(previous)
        i += row
    }
    return out.toByteArray()
}

/** Runs content streams, collecting the painted paths. */
private class ContentInterpreter(private val document: PdfDocumentReader) {
    val paths = ArrayList<ImportedPath>()

    private class GraphicsState(
        var ctm: DoubleArray,
        var fill: Color = Color.Black,
        var stroke: Color = Color.Black,
        var fillSpaceComponents: Int = 1,
        var strokeSpaceComponents: Int = 1,
        var fillAlpha: Float = 1f,
        var strokeAlpha: Float = 1f,
        var width: Double = 1.0,
        var cap: LineCap = LineCap.BUTT,
        var join: LineJoin = LineJoin.MITER,
        var dashed: Boolean = false,
    ) {
        fun copy() = GraphicsState(ctm.copyOf(), fill, stroke, fillSpaceComponents, strokeSpaceComponents, fillAlpha, strokeAlpha, width, cap, join, dashed)
    }

    private val layerStack = ArrayList<String?>()

    fun run(content: ByteArray, resources: Map<*, *>?, ctm: DoubleArray, depth: Int) {
        if (depth > MAX_FORM_DEPTH) return
        var state = GraphicsState(ctm)
        val saved = ArrayList<GraphicsState>()
        val operands = ArrayList<Any?>()
        val subpaths = ArrayList<Pair<MutableList<WorldPoint>, Boolean>>()
        var current: MutableList<WorldPoint>? = null
        var currentPoint = WorldPoint.Zero
        var subpathStart = WorldPoint.Zero
        val lexer = PdfLexer(content)

        fun num(i: Int) = (operands.getOrNull(i) as? Double) ?: 0.0
        fun map(x: Double, y: Double): WorldPoint {
            val m = state.ctm
            return WorldPoint(m[0] * x + m[2] * y + m[4], m[1] * x + m[3] * y + m[5])
        }
        fun moveTo(p: WorldPoint) {
            // An open subpath of a single point (a bare moveto) paints nothing.
            current?.let { if (it.size >= 2) subpaths.add(it to false) }
            current = mutableListOf(p)
            currentPoint = p
            subpathStart = p
        }
        fun lineTo(p: WorldPoint) {
            (current ?: mutableListOf(currentPoint).also { current = it }).add(p)
            currentPoint = p
        }
        fun curveTo(c1: WorldPoint, c2: WorldPoint, end: WorldPoint) {
            val p0 = currentPoint
            for (k in 1..CURVE_SEGMENTS) {
                val t = k.toDouble() / CURVE_SEGMENTS
                val u = 1 - t
                lineTo(WorldPoint(
                    u * u * u * p0.x + 3 * u * u * t * c1.x + 3 * u * t * t * c2.x + t * t * t * end.x,
                    u * u * u * p0.y + 3 * u * u * t * c1.y + 3 * u * t * t * c2.y + t * t * t * end.y,
                ))
            }
        }
        fun closePath() {
            current?.let {
                if (it.isNotEmpty()) subpaths.add(it to true)
                current = null
                currentPoint = subpathStart
            }
        }
        fun paint(fill: Boolean, stroke: Boolean, evenOdd: Boolean) {
            current?.let { if (it.size >= 2) subpaths.add(it to false) }
            current = null
            val collected = subpaths.map { (points, closed) -> points.toList() to closed }.filter { it.first.isNotEmpty() }
            subpaths.clear()
            if (collected.isEmpty() || (!fill && !stroke)) return
            // Line width scales with the transform (its mean axis scale).
            val m = state.ctm
            val scale = sqrt(abs(m[0] * m[3] - m[1] * m[2]))
            paths.add(
                ImportedPath(
                    subpaths = collected,
                    fill = if (fill) state.fill.copy(alpha = state.fill.alpha * state.fillAlpha) else null,
                    evenOdd = evenOdd,
                    stroke = if (stroke) state.stroke.copy(alpha = state.stroke.alpha * state.strokeAlpha) else null,
                    width = (state.width * scale).coerceAtLeast(MIN_LINE_WIDTH),
                    cap = state.cap,
                    join = state.join,
                    dashed = state.dashed,
                    layer = layerStack.lastOrNull { it != null },
                ),
            )
        }
        fun colour(count: Int): Color? {
            val values = operands.filterIsInstance<Double>()
            if (values.size < count) return null
            val v = values.takeLast(count).map { it.toFloat().coerceIn(0f, 1f) }
            return when (count) {
                1 -> Color(v[0], v[0], v[0])
                3 -> Color(v[0], v[1], v[2])
                4 -> Color((1 - v[0]) * (1 - v[3]), (1 - v[1]) * (1 - v[3]), (1 - v[2]) * (1 - v[3]))
                else -> null
            }
        }
        fun componentsOf(space: Any?): Int {
            val resolved = document.resolve(space)
            val name = (resolved as? PdfName)?.name
            when (name) {
                "DeviceGray", "G", "CalGray" -> return 1
                "DeviceRGB", "RGB", "CalRGB", "Lab" -> return 3
                "DeviceCMYK", "CMYK" -> return 4
                "Pattern" -> return 0
            }
            if (name != null) {
                // A named space from the resources.
                val named = (document.dictionaryOf(resources?.get("ColorSpace")))?.get(name)
                if (named != null && named != space) return componentsOf(named)
                return 1
            }
            val array = resolved as? List<*> ?: return 1
            return when ((document.resolve(array.firstOrNull()) as? PdfName)?.name) {
                "ICCBased" -> (document.resolve(document.dictionaryOf(array.getOrNull(1))?.get("N")) as? Double)?.toInt() ?: 3
                "Separation" -> -1 // one tint, drawn in the alternate space's black-to-colour range (approximated)
                "DeviceN" -> -((array.getOrNull(1) as? List<*>)?.size ?: 1)
                "Indexed" -> 0
                "CalRGB", "Lab" -> 3
                "CalGray" -> 1
                else -> 1
            }
        }
        fun tintColour(): Color {
            // A spot or DeviceN tint: shown as grey of the same darkness (its real mix needs the tint transform).
            val t = operands.filterIsInstance<Double>().firstOrNull()?.toFloat()?.coerceIn(0f, 1f) ?: 1f
            return Color(1 - t, 1 - t, 1 - t)
        }
        fun setColour(fill: Boolean) {
            val components = if (fill) state.fillSpaceComponents else state.strokeSpaceComponents
            val c = when {
                components < 0 -> tintColour()
                components == 0 -> null // patterns and indexed colours: kept as they were
                else -> colour(components)
            } ?: return
            if (fill) state.fill = c else state.stroke = c
        }

        while (true) {
            val token = lexer.next() ?: break
            if (token !is PdfKeyword) {
                operands.add(token)
                continue
            }
            when (token.word) {
                "q" -> saved.add(state.copy())
                "Q" -> if (saved.isNotEmpty()) state = saved.removeAt(saved.lastIndex)
                "cm" -> if (operands.size >= 6) {
                    val a = DoubleArray(6) { num(it) }
                    val m = state.ctm
                    state.ctm = doubleArrayOf(
                        a[0] * m[0] + a[1] * m[2], a[0] * m[1] + a[1] * m[3],
                        a[2] * m[0] + a[3] * m[2], a[2] * m[1] + a[3] * m[3],
                        a[4] * m[0] + a[5] * m[2] + m[4], a[4] * m[1] + a[5] * m[3] + m[5],
                    )
                }
                "w" -> state.width = num(0)
                "J" -> state.cap = when (num(0).toInt()) { 1 -> LineCap.ROUND; 2 -> LineCap.SQUARE; else -> LineCap.BUTT }
                "j" -> state.join = when (num(0).toInt()) { 1 -> LineJoin.ROUND; 2 -> LineJoin.BEVEL; else -> LineJoin.MITER }
                "d" -> state.dashed = (operands.firstOrNull() as? List<*>)?.isNotEmpty() == true
                "m" -> moveTo(map(num(0), num(1)))
                "l" -> lineTo(map(num(0), num(1)))
                "c" -> curveTo(map(num(0), num(1)), map(num(2), num(3)), map(num(4), num(5)))
                "v" -> curveTo(currentPoint, map(num(0), num(1)), map(num(2), num(3)))
                "y" -> map(num(2), num(3)).let { end -> curveTo(map(num(0), num(1)), end, end) }
                "h" -> closePath()
                "re" -> {
                    val x = num(0); val y = num(1); val w = num(2); val hh = num(3)
                    moveTo(map(x, y)); lineTo(map(x + w, y)); lineTo(map(x + w, y + hh)); lineTo(map(x, y + hh)); closePath()
                }
                "S" -> paint(fill = false, stroke = true, evenOdd = false)
                "s" -> { closePath(); paint(fill = false, stroke = true, evenOdd = false) }
                "f", "F" -> paint(fill = true, stroke = false, evenOdd = false)
                "f*" -> paint(fill = true, stroke = false, evenOdd = true)
                "B" -> paint(fill = true, stroke = true, evenOdd = false)
                "B*" -> paint(fill = true, stroke = true, evenOdd = true)
                "b" -> { closePath(); paint(fill = true, stroke = true, evenOdd = false) }
                "b*" -> { closePath(); paint(fill = true, stroke = true, evenOdd = true) }
                "n" -> { subpaths.clear(); current = null }
                "g" -> { state.fillSpaceComponents = 1; colour(1)?.let { state.fill = it } }
                "G" -> { state.strokeSpaceComponents = 1; colour(1)?.let { state.stroke = it } }
                "rg" -> { state.fillSpaceComponents = 3; colour(3)?.let { state.fill = it } }
                "RG" -> { state.strokeSpaceComponents = 3; colour(3)?.let { state.stroke = it } }
                "k" -> { state.fillSpaceComponents = 4; colour(4)?.let { state.fill = it } }
                "K" -> { state.strokeSpaceComponents = 4; colour(4)?.let { state.stroke = it } }
                "cs" -> { state.fillSpaceComponents = componentsOf(operands.firstOrNull()); state.fill = Color.Black }
                "CS" -> { state.strokeSpaceComponents = componentsOf(operands.firstOrNull()); state.stroke = Color.Black }
                "sc", "scn" -> setColour(fill = true)
                "SC", "SCN" -> setColour(fill = false)
                "gs" -> {
                    val name = (operands.firstOrNull() as? PdfName)?.name
                    val gs = document.dictionaryOf(document.dictionaryOf(resources?.get("ExtGState"))?.get(name))
                    (document.resolve(gs?.get("ca")) as? Double)?.let { state.fillAlpha = it.toFloat().coerceIn(0f, 1f) }
                    (document.resolve(gs?.get("CA")) as? Double)?.let { state.strokeAlpha = it.toFloat().coerceIn(0f, 1f) }
                    (document.resolve(gs?.get("LW")) as? Double)?.let { state.width = it }
                }
                "BDC" -> {
                    // Marked content: an /OC tag names the layer its paths belong to.
                    val tag = (operands.firstOrNull() as? PdfName)?.name
                    val property = operands.getOrNull(1)
                    val ocg = if (tag == "OC") {
                        val named = (property as? PdfName)?.name
                        document.dictionaryOf(if (named != null) document.dictionaryOf(resources?.get("Properties"))?.get(named) else property)
                    } else {
                        null
                    }
                    layerStack.add((document.resolve(ocg?.get("Name")) as? ByteArray)?.let(::decodeTextString))
                }
                "BMC" -> layerStack.add(null)
                "EMC" -> if (layerStack.isNotEmpty()) layerStack.removeAt(layerStack.lastIndex)
                "Do" -> {
                    val name = (operands.firstOrNull() as? PdfName)?.name
                    val xobject = document.streamOf(document.dictionaryOf(resources?.get("XObject"))?.get(name))
                    if (xobject != null && (xobject.dictionary["Subtype"] as? PdfName)?.name == "Form") {
                        val formMatrix = (document.resolve(xobject.dictionary["Matrix"]) as? List<*>)?.mapNotNull { document.resolve(it) as? Double }
                        val m = state.ctm
                        val a = if (formMatrix != null && formMatrix.size == 6) formMatrix.toDoubleArray() else doubleArrayOf(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
                        val combined = doubleArrayOf(
                            a[0] * m[0] + a[1] * m[2], a[0] * m[1] + a[1] * m[3],
                            a[2] * m[0] + a[3] * m[2], a[2] * m[1] + a[3] * m[3],
                            a[4] * m[0] + a[5] * m[2] + m[4], a[4] * m[1] + a[5] * m[3] + m[5],
                        )
                        val formResources = document.dictionaryOf(xobject.dictionary["Resources"]) ?: resources
                        document.decode(xobject)?.let { run(it, formResources, combined, depth + 1) }
                    }
                }
                "BI" -> skipInlineImage(lexer, content)
                else -> {} // text, shading, clipping and other operators aren't imported
            }
            operands.clear()
        }
    }

    /** Skips an inline image's binary data, up to its "EI" on its own. */
    private fun skipInlineImage(lexer: PdfLexer, content: ByteArray) {
        // Past the dictionary to the ID keyword...
        while (true) {
            val token = lexer.next() ?: return
            if (token == PdfKeyword("ID")) break
        }
        var i = lexer.pos + 1
        while (i + 1 < content.size) {
            val before = if (i > 0) content[i - 1].toInt() else 32
            val after = if (i + 2 < content.size) content[i + 2].toInt() else 32
            if (content[i] == 'E'.code.toByte() && content[i + 1] == 'I'.code.toByte() &&
                (before == 32 || before == 10 || before == 13 || before == 9) && (after == 32 || after == 10 || after == 13 || after == 9 || i + 2 >= content.size)
            ) {
                lexer.pos = i + 2
                return
            }
            i++
        }
        lexer.pos = content.size
    }
}

/** A PDF text string: UTF-16BE when it starts with a byte order mark, otherwise (close enough to) PDFDocEncoding as Latin-1. */
private fun decodeTextString(bytes: ByteArray): String =
    if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
    else String(bytes, Charsets.ISO_8859_1)

// Hairlines (width 0 draws the thinnest line a device can) come in at a visible width, in points.
private const val MIN_LINE_WIDTH = 0.25
