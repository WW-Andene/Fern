package com.andene.fern.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import com.andene.fern.FernScale
import com.andene.fern.FernSpace

private val KEYWORDS = setOf("to", "if", "else", "while", "repeat", "set", "stop", "and", "or", "not", "true", "false")

/**
 * [source] coloured for reading (design roadmap D33): keywords, numbers, "texts", #colours and
 * // comments, in the theme's colours.
 */
fun highlightScript(source: String, keyword: Color, number: Color, text: Color, comment: Color): AnnotatedString = buildAnnotatedString {
    append(source)
    var i = 0
    while (i < source.length) {
        val c = source[i]
        when {
            c == '/' && source.getOrNull(i + 1) == '/' -> {
                val end = source.indexOf('\n', i).let { if (it < 0) source.length else it }
                addStyle(SpanStyle(color = comment), i, end)
                i = end
            }
            c == '"' -> {
                val end = source.indexOf('"', i + 1).let { if (it < 0) source.length else it + 1 }
                addStyle(SpanStyle(color = text), i, end)
                i = end
            }
            c == '#' -> {
                var end = i + 1
                while (end < source.length && source[end].isLetterOrDigit()) end++
                addStyle(SpanStyle(color = text), i, end)
                i = end
            }
            c.isDigit() -> {
                var end = i + 1
                while (end < source.length && (source[end].isDigit() || source[end] == '.')) end++
                addStyle(SpanStyle(color = number), i, end)
                i = end
            }
            c.isLetter() || c == '_' -> {
                var end = i + 1
                while (end < source.length && (source[end].isLetterOrDigit() || source[end] == '_')) end++
                if (source.substring(i, end).lowercase() in KEYWORDS) addStyle(SpanStyle(color = keyword, fontWeight = FontWeight.Bold), i, end)
                i = end
            }
            else -> i++
        }
    }
}

/**
 * A code editor for scripts (design roadmap D33): line numbers, highlighting, and the line of
 * [error] marked with its message under the code.
 */
@Composable
fun CodeEditor(source: String, onChange: (String) -> Unit, error: ScriptError?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, color = scheme.onSurface)
    val lines = source.count { it == '\n' } + 1
    val highlighter = VisualTransformation { text ->
        TransformedText(highlightScript(text.text, scheme.primary, scheme.tertiary, scheme.secondary, scheme.outline), OffsetMapping.Identity)
    }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().background(scheme.surfaceVariant, MaterialTheme.shapes.small).padding(FernSpace.sm)) {
            // Line numbers, the error's line in the error colour.
            Column(Modifier.padding(end = FernSpace.sm)) {
                for (n in 1..lines) {
                    Text(
                        n.toString(),
                        style = style.copy(color = if (error?.line == n) scheme.error else scheme.outline, textAlign = TextAlign.End),
                    )
                }
            }
            BasicTextField(
                value = source,
                onValueChange = onChange,
                textStyle = style,
                cursorBrush = SolidColor(scheme.primary),
                visualTransformation = highlighter,
                modifier = Modifier.weight(1f).heightIn(min = FernScale.s128),
            )
        }
        error?.let { Text(stringResourceLine(it), color = scheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = FernScale.s4)) }
    }
}

@Composable
private fun stringResourceLine(e: ScriptError) = androidx.compose.ui.res.stringResource(com.andene.fern.R.string.script_error, e.line, e.message.orEmpty())
