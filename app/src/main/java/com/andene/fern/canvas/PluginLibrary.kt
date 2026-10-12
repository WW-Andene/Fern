package com.andene.fern.canvas

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * The plugins on this device (roadmap item 111): the built-in ones below, and those installed
 * from .js files, kept under the app's private `plugins/` directory, shared by every document.
 */
object PluginLibrary {
    private const val DIR_NAME = "plugins"

    private fun dir(context: Context) = File(context.filesDir, DIR_NAME).also { if (!it.exists()) it.mkdirs() }

    /** Every plugin: built-in first, then installed ones by name. */
    fun list(context: Context): List<Plugin> {
        val installed = dir(context).listFiles { f -> f.extension == "js" }.orEmpty().mapNotNull { file ->
            runCatching { parsePlugin(file.nameWithoutExtension, file.readText(), file.nameWithoutExtension, builtIn = false) }.getOrNull()
        }.sortedBy { it.name.lowercase() }
        return BUILT_IN + installed
    }

    /** Installs [source] as a new plugin and returns it. */
    fun install(context: Context, source: String, fallbackName: String): Plugin {
        val id = UUID.randomUUID().toString()
        File(dir(context), "$id.js").writeText(source)
        return parsePlugin(id, source, fallbackName, builtIn = false)
    }

    /** Removes installed plugin [id]; built-in ones stay. */
    fun remove(context: Context, id: String) {
        if (id.matches(Regex("[A-Za-z0-9-]+"))) File(dir(context), "$id.js").delete()
    }

    val BUILT_IN: List<Plugin> = listOf(
        """
        // @name Scatter dots
        // @description Dots scattered over the selection, or the view when nothing is selected.
        // @param count number 60 How many dots
        // @param size number 6 Dot size
        function run(doc, params) {
          var area = doc.bounds || doc.view;
          var add = [];
          for (var i = 0; i < params.count; i++) {
            var x = area.x + Math.random() * area.width, y = area.y + Math.random() * area.height;
            add.push({ points: [[x, y], [x + 0.01, y]], width: params.size });
          }
          return { add: add };
        }
        """,
        """
        // @name Spiral
        // @description A spiral in the middle of the view.
        // @param turns number 6 Turns
        // @param spacing number 12 Distance between turns
        function run(doc, params) {
          var cx = doc.view.x + doc.view.width / 2, cy = doc.view.y + doc.view.height / 2;
          var points = [];
          var steps = Math.round(params.turns * 72);
          for (var i = 0; i <= steps; i++) {
            var a = i / 72 * 2 * Math.PI, r = params.spacing * a / (2 * Math.PI);
            points.push([cx + r * Math.cos(a), cy + r * Math.sin(a)]);
          }
          return { add: [{ points: points }] };
        }
        """,
        """
        // @name Wobble
        // @description Shakes the selected strokes' points, for a hand-drawn look.
        // @param amount number 2 How far points move
        function run(doc, params) {
          return { update: doc.selection.map(function (s, i) {
            return { index: i, points: s.points.map(function (p) {
              return [p[0] + (Math.random() - 0.5) * 2 * params.amount, p[1] + (Math.random() - 0.5) * 2 * params.amount];
            }) };
          }) };
        }
        """,
        """
        // @name Random colours
        // @description Gives each selected stroke a random colour.
        function run(doc) {
          function channel() { var v = Math.floor(Math.random() * 256).toString(16); return v.length < 2 ? '0' + v : v; }
          return { update: doc.selection.map(function (s, i) { return { index: i, color: '#' + channel() + channel() + channel() }; }) };
        }
        """,
        """
        // @name Grid of copies
        // @description Copies of the selection laid out in a grid to its right and below.
        // @param columns number 3 Columns
        // @param rows number 3 Rows
        // @param gap number 16 Gap
        function run(doc, params) {
          var b = doc.bounds;
          if (!b) return {};
          var add = [];
          for (var r = 0; r < params.rows; r++) for (var c = 0; c < params.columns; c++) {
            if (r === 0 && c === 0) continue;
            var dx = c * (b.width + params.gap), dy = r * (b.height + params.gap);
            doc.selection.forEach(function (s) {
              add.push({ points: s.points.map(function (p) { return [p[0] + dx, p[1] + dy]; }), color: s.color, opacity: s.opacity, width: s.width, filled: s.filled, closed: s.closed });
            });
          }
          return { add: add };
        }
        """,
    ).mapIndexed { i, source -> parsePlugin("builtin-$i", source.trimIndent(), "Plugin ${i + 1}", builtIn = true) }
}
