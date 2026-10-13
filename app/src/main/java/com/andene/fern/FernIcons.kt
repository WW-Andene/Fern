package com.andene.fern

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes

/*
 * Fern's icon set (design roadmap D58): every icon from Lucide (https://lucide.dev, ISC licence,
 * notice below), drawn on one 24-unit grid with one 2-unit round stroke, instead of a mix of
 * Material's filled and outlined glyphs. Each entry is named after the Material icon it replaced,
 * so a call site reads `FernIcons.Delete`, and holds Lucide's own SVG markup for the icon named
 * beside it; it is turned into an ImageVector the first time it's used. To add an icon, copy the
 * elements of its SVG from Lucide into a new entry.
 *
 * A few entries are drawn filled ([lucide]'s `filled`) where the fill is the meaning: a favourite
 * star, a pinned pin, the record dot, the stop square. Entries for arrows and other directional
 * glyphs mirror in right-to-left languages, as Material's auto-mirrored icons did.
 *
 * Lucide licence:
 * ISC License. Copyright (c) 2026 Lucide Icons and Contributors. Permission to use, copy, modify,
 * and/or distribute this software for any purpose with or without fee is hereby granted, provided
 * that the above copyright notice and this permission notice appear in all copies. THE SOFTWARE IS
 * PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS SOFTWARE INCLUDING
 * ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM
 * LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS
 * ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 * Some Lucide icons derive from Feather (https://feathericons.com), MIT licence,
 * Copyright (c) 2013-2023 Cole Bemis.
 */
object FernIcons {
    val AccessibilityNew: ImageVector by lazy { lucide("AccessibilityNew", "person-standing", """<circle cx="12" cy="5" r="1"/> <path d="m9 20 3-6 3 6"/> <path d="m6 8 6 2 6-2"/> <path d="M12 10v4"/>""") }
    val AccountTree: ImageVector by lazy { lucide("AccountTree", "network", """<rect x="16" y="16" width="6" height="6" rx="1"/> <rect x="2" y="16" width="6" height="6" rx="1"/> <rect x="9" y="2" width="6" height="6" rx="1"/> <path d="M5 16v-3a1 1 0 0 1 1-1h12a1 1 0 0 1 1 1v3"/> <path d="M12 12V8"/>""") }
    val Add: ImageVector by lazy { lucide("Add", "plus", """<path d="M5 12h14"/> <path d="M12 5v14"/>""") }
    val AddBox: ImageVector by lazy { lucide("AddBox", "square-plus", """<rect width="18" height="18" x="3" y="3" rx="2"/> <path d="M8 12h8"/> <path d="M12 8v8"/>""") }
    val AddCircleOutline: ImageVector by lazy { lucide("AddCircleOutline", "circle-plus", """<circle cx="12" cy="12" r="10"/> <path d="M8 12h8"/> <path d="M12 8v8"/>""") }
    val Adjust: ImageVector by lazy { lucide("Adjust", "circle-dot", """<circle cx="12" cy="12" r="1"/> <circle cx="12" cy="12" r="10"/>""") }
    val Air: ImageVector by lazy { lucide("Air", "wind", """<path d="M12.8 19.6A2 2 0 1 0 14 16H2"/> <path d="M17.5 8a2.5 2.5 0 1 1 2 4H2"/> <path d="M9.8 4.4A2 2 0 1 1 11 8H2"/>""") }
    val AlignHorizontalLeft: ImageVector by lazy { lucide("AlignHorizontalLeft", "align-horizontal-justify-start", """<rect width="6" height="14" x="6" y="5" rx="2"/> <rect width="6" height="10" x="16" y="7" rx="2"/> <path d="M2 2v20"/>""") }
    val Animation: ImageVector by lazy { lucide("Animation", "film", """<rect width="18" height="18" x="3" y="3" rx="2"/> <path d="M7 3v18"/> <path d="M3 7.5h4"/> <path d="M3 12h18"/> <path d="M3 16.5h4"/> <path d="M17 3v18"/> <path d="M17 7.5h4"/> <path d="M17 16.5h4"/>""") }
    val Approval: ImageVector by lazy { lucide("Approval", "stamp", """<path d="M14 13V8.5C14 7 15 7 15 5a3 3 0 0 0-6 0c0 2 1 2 1 3.5V13"/> <path d="M20 15.5a2.5 2.5 0 0 0-2.5-2.5h-11A2.5 2.5 0 0 0 4 15.5V17a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1z"/> <path d="M5 22h14"/>""") }
    val Apps: ImageVector by lazy { lucide("Apps", "layout-grid", """<rect width="7" height="7" x="3" y="3" rx="1"/> <rect width="7" height="7" x="14" y="3" rx="1"/> <rect width="7" height="7" x="14" y="14" rx="1"/> <rect width="7" height="7" x="3" y="14" rx="1"/>""") }
    val ArrowBack: ImageVector by lazy { lucide("ArrowBack", "arrow-left", """<path d="m12 19-7-7 7-7"/> <path d="M19 12H5"/>""", autoMirror = true) }
    val ArrowDownward: ImageVector by lazy { lucide("ArrowDownward", "arrow-down", """<path d="M12 5v14"/> <path d="m19 12-7 7-7-7"/>""") }
    val ArrowForward: ImageVector by lazy { lucide("ArrowForward", "arrow-right", """<path d="M5 12h14"/> <path d="m12 5 7 7-7 7"/>""", autoMirror = true) }
    val ArrowUpward: ImageVector by lazy { lucide("ArrowUpward", "arrow-up", """<path d="m5 12 7-7 7 7"/> <path d="M12 19V5"/>""") }
    val AspectRatio: ImageVector by lazy { lucide("AspectRatio", "ratio", """<rect width="12" height="20" x="6" y="2" rx="2"/> <rect width="20" height="12" x="2" y="6" rx="2"/>""") }
    val AutoAwesomeMotion: ImageVector by lazy { lucide("AutoAwesomeMotion", "gallery-horizontal-end", """<path d="M2 7v10"/> <path d="M6 5v14"/> <rect width="12" height="18" x="10" y="3" rx="2"/>""") }
    val AutoFixHigh: ImageVector by lazy { lucide("AutoFixHigh", "wand-sparkles", """<path d="m21.64 3.64-1.28-1.28a1.21 1.21 0 0 0-1.72 0L2.36 18.64a1.21 1.21 0 0 0 0 1.72l1.28 1.28a1.2 1.2 0 0 0 1.72 0L21.64 5.36a1.2 1.2 0 0 0 0-1.72"/> <path d="m14 7 3 3"/> <path d="M5 6v4"/> <path d="M19 14v4"/> <path d="M10 2v2"/> <path d="M7 8H3"/> <path d="M21 16h-4"/> <path d="M11 3H9"/>""") }
    val AutoFixNormal: ImageVector by lazy { lucide("AutoFixNormal", "wand", """<path d="M15 4V2"/> <path d="M15 16v-2"/> <path d="M8 9h2"/> <path d="M20 9h2"/> <path d="M17.8 11.8 19 13"/> <path d="M15 9h.01"/> <path d="M17.8 6.2 19 5"/> <path d="m3 21 9-9"/> <path d="M12.2 6.2 11 5"/>""") }
    val AutoStories: ImageVector by lazy { lucide("AutoStories", "book-open", """<path d="M12 5v16"/> <path d="M20.001 19A2 2 0 0022 17V5a2 2 0 00-1.999-2L16 3.002A5 5 0 0012 5a5 5 0 00-4-2H4a2 2 0 00-2 2v12a2 2 0 001.999 2H8a5 5 0 014 2 5 5 0 014-2z"/>""") }
    val Backspace: ImageVector by lazy { lucide("Backspace", "delete", """<path d="M10 5a2 2 0 0 0-1.344.519l-6.328 5.74a1 1 0 0 0 0 1.481l6.328 5.741A2 2 0 0 0 10 19h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2z"/> <path d="m12 9 6 6"/> <path d="m18 9-6 6"/>""", autoMirror = true) }
    val BlurLinear: ImageVector by lazy { lucide("BlurLinear", "blinds", """<path d="M3 3h18"/> <path d="M20 7H8"/> <path d="M20 11H8"/> <path d="M10 19h10"/> <path d="M8 15h12"/> <path d="M4 3v14"/> <circle cx="4" cy="19" r="2"/>""") }
    val BlurOn: ImageVector by lazy { lucide("BlurOn", "spray-can", """<path d="M3 3h.01"/> <path d="M7 5h.01"/> <path d="M11 7h.01"/> <path d="M3 7h.01"/> <path d="M7 9h.01"/> <path d="M3 11h.01"/> <rect width="4" height="4" x="15" y="5"/> <path d="m19 9 2 2v10c0 .6-.4 1-1 1h-6c-.6 0-1-.4-1-1V11l2-2"/> <path d="m13 14 8-2"/> <path d="m13 19 8-2"/>""") }
    val BorderColor: ImageVector by lazy { lucide("BorderColor", "pen-line", """<path d="M13 21h8"/> <path d="M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z"/>""") }
    val Brush: ImageVector by lazy { lucide("Brush", "brush", """<path d="m11 10 3 3"/> <path d="M6.5 21A3.5 3.5 0 1 0 3 17.5a2.62 2.62 0 0 1-.708 1.792A1 1 0 0 0 3 21z"/> <path d="M9.969 17.031 21.378 5.624a1 1 0 0 0-3.002-3.002L6.967 14.031"/>""") }
    val Cable: ImageVector by lazy { lucide("Cable", "cable", """<path d="M17 19a1 1 0 0 1-1-1v-2a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2a1 1 0 0 1-1 1z"/> <path d="M17 21v-2"/> <path d="M19 14V6.5a1 1 0 0 0-7 0v11a1 1 0 0 1-7 0V10"/> <path d="M21 21v-2"/> <path d="M3 5V3"/> <path d="M4 10a2 2 0 0 1-2-2V6a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2a2 2 0 0 1-2 2z"/> <path d="M7 5V3"/>""") }
    val CallMerge: ImageVector by lazy { lucide("CallMerge", "merge", """<path d="m8 6 4-4 4 4"/> <path d="M12 2v10.3a4 4 0 0 1-1.172 2.872L4 22"/> <path d="m20 22-5-5"/>""", autoMirror = true) }
    val Category: ImageVector by lazy { lucide("Category", "shapes", """<path d="M8.3 10a.7.7 0 0 1-.626-1.079L11.4 3a.7.7 0 0 1 1.198-.043L16.3 8.9a.7.7 0 0 1-.572 1.1Z"/> <rect x="3" y="14" width="7" height="7" rx="1"/> <circle cx="17.5" cy="17.5" r="3.5"/>""") }
    val CenterFocusStrong: ImageVector by lazy { lucide("CenterFocusStrong", "focus", """<circle cx="12" cy="12" r="3"/> <path d="M3 7V5a2 2 0 0 1 2-2h2"/> <path d="M17 3h2a2 2 0 0 1 2 2v2"/> <path d="M21 17v2a2 2 0 0 1-2 2h-2"/> <path d="M7 21H5a2 2 0 0 1-2-2v-2"/>""") }
    val ChatBubbleOutline: ImageVector by lazy { lucide("ChatBubbleOutline", "message-circle", """<path d="M2.992 16.342a2 2 0 0 1 .094 1.167l-1.065 3.29a1 1 0 0 0 1.236 1.168l3.413-.998a2 2 0 0 1 1.099.092 10 10 0 1 0-4.777-4.719"/>""") }
    val Check: ImageVector by lazy { lucide("Check", "check", """<path d="M20 6 9 17l-5-5"/>""") }
    val Circle: ImageVector by lazy { lucide("Circle", "circle", """<circle cx="12" cy="12" r="10"/>""", filled = true) }
    val CleaningServices: ImageVector by lazy { lucide("CleaningServices", "brush-cleaning", """<path d="m16 22-1-4"/> <path d="M19 14a1 1 0 0 0 1-1v-1a2 2 0 0 0-2-2h-3a1 1 0 0 1-1-1V4a2 2 0 0 0-4 0v5a1 1 0 0 1-1 1H6a2 2 0 0 0-2 2v1a1 1 0 0 0 1 1"/> <path d="M19 14H5l-1.973 6.767A1 1 0 0 0 4 22h16a1 1 0 0 0 .973-1.233z"/> <path d="m8 22 1-4"/>""") }
    val Close: ImageVector by lazy { lucide("Close", "x", """<path d="M18 6 6 18"/> <path d="m6 6 12 12"/>""") }
    val Cloud: ImageVector by lazy { lucide("Cloud", "cloud", """<path d="M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z"/>""") }
    val Code: ImageVector by lazy { lucide("Code", "code", """<path d="m16 18 6-6-6-6"/> <path d="m8 6-6 6 6 6"/>""") }
    val ColorLens: ImageVector by lazy { lucide("ColorLens", "palette", """<path d="M12 22a1 1 0 0 1 0-20 10 9 0 0 1 10 9 5 5 0 0 1-5 5h-2.25a1.75 1.75 0 0 0-1.4 2.8l.3.4a1.75 1.75 0 0 1-1.4 2.8z"/> <circle cx="13.5" cy="6.5" r=".5" fill="currentColor"/> <circle cx="17.5" cy="10.5" r=".5" fill="currentColor"/> <circle cx="6.5" cy="12.5" r=".5" fill="currentColor"/> <circle cx="8.5" cy="7.5" r=".5" fill="currentColor"/>""") }
    val Colorize: ImageVector by lazy { lucide("Colorize", "pipette", """<path d="m12 9-8.414 8.414A2 2 0 0 0 3 18.828v1.344a2 2 0 0 1-.586 1.414A2 2 0 0 1 3.828 21h1.344a2 2 0 0 0 1.414-.586L15 12"/> <path d="m18 9 .4.4a1 1 0 1 1-3 3l-3.8-3.8a1 1 0 1 1 3-3l.4.4 3.4-3.4a1 1 0 1 1 3 3z"/> <path d="m2 22 .414-.414"/>""") }
    val ContentCopy: ImageVector by lazy { lucide("ContentCopy", "copy", """<rect width="14" height="14" x="8" y="8" rx="2" ry="2"/> <path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/>""") }
    val ContentCut: ImageVector by lazy { lucide("ContentCut", "scissors", """<circle cx="6" cy="6" r="3"/> <path d="M8.12 8.12 12 12"/> <path d="M20 4 8.12 15.88"/> <circle cx="6" cy="18" r="3"/> <path d="M14.8 14.8 20 20"/>""") }
    val ContentPaste: ImageVector by lazy { lucide("ContentPaste", "clipboard", """<rect width="8" height="4" x="8" y="2" rx="1" ry="1"/> <path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/>""") }
    val ContentPasteGo: ImageVector by lazy { lucide("ContentPasteGo", "clipboard-paste", """<path d="M11 14h10"/> <path d="M16 4h2a2 2 0 0 1 2 2v1.344"/> <path d="m17 18 4-4-4-4"/> <path d="M8 4H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 1.793-1.113"/> <rect x="8" y="2" width="8" height="4" rx="1"/>""") }
    val Contrast: ImageVector by lazy { lucide("Contrast", "contrast", """<circle cx="12" cy="12" r="10"/> <path d="M12 18a6 6 0 0 0 0-12v12z"/>""") }
    val CopyAll: ImageVector by lazy { lucide("CopyAll", "copy-plus", """<line x1="15" x2="15" y1="12" y2="18"/> <line x1="12" x2="18" y1="15" y2="15"/> <rect width="14" height="14" x="8" y="8" rx="2" ry="2"/> <path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/>""") }
    val Create: ImageVector by lazy { lucide("Create", "pencil", """<path d="M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z"/> <path d="m15 5 4 4"/>""") }
    val CropFree: ImageVector by lazy { lucide("CropFree", "scan", """<path d="M3 7V5a2 2 0 0 1 2-2h2"/> <path d="M17 3h2a2 2 0 0 1 2 2v2"/> <path d="M21 17v2a2 2 0 0 1-2 2h-2"/> <path d="M7 21H5a2 2 0 0 1-2-2v-2"/>""") }
    val CropSquare: ImageVector by lazy { lucide("CropSquare", "square", """<rect width="18" height="18" x="3" y="3" rx="2"/>""") }
    val Cyclone: ImageVector by lazy { lucide("Cyclone", "tornado", """<path d="M21 4H3"/> <path d="M18 8H6"/> <path d="M19 12H9"/> <path d="M16 16h-6"/> <path d="M11 20H9"/>""") }
    val Dashboard: ImageVector by lazy { lucide("Dashboard", "layout-dashboard", """<rect width="7" height="9" x="3" y="3" rx="1"/> <rect width="7" height="5" x="14" y="3" rx="1"/> <rect width="7" height="9" x="14" y="12" rx="1"/> <rect width="7" height="5" x="3" y="16" rx="1"/>""") }
    val Delete: ImageVector by lazy { lucide("Delete", "trash-2", """<path d="M10 11v6"/> <path d="M14 11v6"/> <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/> <path d="M3 6h18"/> <path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>""") }
    val DeleteForever: ImageVector by lazy { lucide("DeleteForever", "trash-2", """<path d="M10 11v6"/> <path d="M14 11v6"/> <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/> <path d="M3 6h18"/> <path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>""") }
    val DeleteOutline: ImageVector by lazy { lucide("DeleteOutline", "trash", """<path d="M10 11v6"/> <path d="M14 11v6"/> <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/> <path d="M3 6h18"/> <path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>""") }
    val DeleteSweep: ImageVector by lazy { lucide("DeleteSweep", "list-x", """<path d="M16 5H3"/> <path d="M11 12H3"/> <path d="M16 19H3"/> <path d="m15.5 9.5 5 5"/> <path d="m20.5 9.5-5 5"/>""") }
    val Description: ImageVector by lazy { lucide("Description", "file-text", """<path d="M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z"/> <path d="M14 2v5a1 1 0 0 0 1 1h5"/> <path d="M10 9H8"/> <path d="M16 13H8"/> <path d="M16 17H8"/>""") }
    val Deselect: ImageVector by lazy { lucide("Deselect", "square-dashed", """<path d="M5 3a2 2 0 0 0-2 2"/> <path d="M19 3a2 2 0 0 1 2 2"/> <path d="M21 19a2 2 0 0 1-2 2"/> <path d="M5 21a2 2 0 0 1-2-2"/> <path d="M9 3h1"/> <path d="M9 21h1"/> <path d="M14 3h1"/> <path d="M14 21h1"/> <path d="M3 9v1"/> <path d="M21 9v1"/> <path d="M3 14v1"/> <path d="M21 14v1"/>""") }
    val DonutLarge: ImageVector by lazy { lucide("DonutLarge", "chart-pie", """<path d="M21 12c.552 0 1.005-.449.95-.998a10 10 0 0 0-8.953-8.951c-.55-.055-.998.398-.998.95v8a1 1 0 0 0 1 1z"/> <path d="M21.21 15.89A10 10 0 1 1 8 2.83"/>""") }
    val DragHandle: ImageVector by lazy { lucide("DragHandle", "grip-horizontal", """<circle cx="12" cy="9" r="1"/> <circle cx="19" cy="9" r="1"/> <circle cx="5" cy="9" r="1"/> <circle cx="12" cy="15" r="1"/> <circle cx="19" cy="15" r="1"/> <circle cx="5" cy="15" r="1"/>""") }
    val Draw: ImageVector by lazy { lucide("Draw", "pen-tool", """<path d="m18 13-.654-4.736a4 4 0 00-2.756-3.266l-9.163-2.9a2 2 0 00-2.017.493l-.82.819a2 2 0 00-.492 2.017l2.9 9.163a4 4 0 003.266 2.756L13 18"/> <path d="M18.648 12.352a1.205 1.205 0 011.704 0l1.296 1.296a1.205 1.205 0 010 1.704l-6.296 6.296a1.205 1.205 0 01-1.704 0l-1.296-1.296a1.205 1.205 0 010-1.704z"/> <path d="m3 3 6.586 6.586"/> <circle cx="11" cy="11" r="2"/>""") }
    val DriveFileMove: ImageVector by lazy { lucide("DriveFileMove", "folder-input", """<path d="M2 9V5a2 2 0 0 1 2-2h3.9a2 2 0 0 1 1.69.9l.81 1.2a2 2 0 0 0 1.67.9H20a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-1"/> <path d="M2 13h10"/> <path d="m9 16 3-3-3-3"/>""") }
    val Edit: ImageVector by lazy { lucide("Edit", "pencil", """<path d="M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z"/> <path d="m15 5 4 4"/>""") }
    val ExpandLess: ImageVector by lazy { lucide("ExpandLess", "chevron-up", """<path d="m18 15-6-6-6 6"/>""") }
    val ExpandMore: ImageVector by lazy { lucide("ExpandMore", "chevron-down", """<path d="m6 9 6 6 6-6"/>""") }
    val Explore: ImageVector by lazy { lucide("Explore", "compass", """<circle cx="12" cy="12" r="10"/> <path d="m16.24 7.76-1.804 5.411a2 2 0 0 1-1.265 1.265L7.76 16.24l1.804-5.411a2 2 0 0 1 1.265-1.265z"/>""") }
    val Extension: ImageVector by lazy { lucide("Extension", "puzzle", """<path d="M15.39 4.39a1 1 0 0 0 1.68-.474 2.5 2.5 0 1 1 3.014 3.015 1 1 0 0 0-.474 1.68l1.683 1.682a2.414 2.414 0 0 1 0 3.414L19.61 15.39a1 1 0 0 1-1.68-.474 2.5 2.5 0 1 0-3.014 3.015 1 1 0 0 1 .474 1.68l-1.683 1.682a2.414 2.414 0 0 1-3.414 0L8.61 19.61a1 1 0 0 0-1.68.474 2.5 2.5 0 1 1-3.014-3.015 1 1 0 0 0 .474-1.68l-1.683-1.682a2.414 2.414 0 0 1 0-3.414L4.39 8.61a1 1 0 0 1 1.68.474 2.5 2.5 0 1 0 3.014-3.015 1 1 0 0 1-.474-1.68l1.683-1.682a2.414 2.414 0 0 1 3.414 0z"/>""") }
    val Fence: ImageVector by lazy { lucide("Fence", "fence", """<path d="M4 3 2 5v15c0 .6.4 1 1 1h2c.6 0 1-.4 1-1V5Z"/> <path d="M6 8h4"/> <path d="M6 18h4"/> <path d="m12 3-2 2v15c0 .6.4 1 1 1h2c.6 0 1-.4 1-1V5Z"/> <path d="M14 8h4"/> <path d="M14 18h4"/> <path d="m20 3-2 2v15c0 .6.4 1 1 1h2c.6 0 1-.4 1-1V5Z"/>""") }
    val FiberManualRecord: ImageVector by lazy { lucide("FiberManualRecord", "circle", """<circle cx="12" cy="12" r="10"/>""", filled = true) }
    val FileOpen: ImageVector by lazy { lucide("FileOpen", "file-input", """<path d="M4 11V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.706.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-1"/> <path d="M14 2v5a1 1 0 0 0 1 1h5"/> <path d="M2 15h10"/> <path d="m9 18 3-3-3-3"/>""") }
    val FitScreen: ImageVector by lazy { lucide("FitScreen", "scan-search", """<path d="M3 7V5a2 2 0 0 1 2-2h2"/> <path d="M17 3h2a2 2 0 0 1 2 2v2"/> <path d="M21 17v2a2 2 0 0 1-2 2h-2"/> <path d="M7 21H5a2 2 0 0 1-2-2v-2"/> <circle cx="12" cy="12" r="3"/> <path d="m16 16-1.9-1.9"/>""") }
    val Flare: ImageVector by lazy { lucide("Flare", "sparkle", """<path d="M11.017 2.814a1 1 0 0 1 1.966 0l1.051 5.558a2 2 0 0 0 1.594 1.594l5.558 1.051a1 1 0 0 1 0 1.966l-5.558 1.051a2 2 0 0 0-1.594 1.594l-1.051 5.558a1 1 0 0 1-1.966 0l-1.051-5.558a2 2 0 0 0-1.594-1.594l-5.558-1.051a1 1 0 0 1 0-1.966l5.558-1.051a2 2 0 0 0 1.594-1.594z"/>""") }
    val Flip: ImageVector by lazy { lucide("Flip", "flip-horizontal-2", """<path d="M10 12H8"/> <path d="M16 12h-2"/> <path d="M22 12h-2"/> <path d="M4 12H2"/> <path d="M7.298 20.288A1 1 0 008 22h8a1 1 0 00.703-1.712l-3.991-3.99a1 1 0 00-1.424-.001z"/> <path d="M7.298 3.712A1 1 0 018 2h8a1 1 0 01.703 1.712l-3.991 3.99a1 1 0 01-1.424.001z"/>""") }
    val FlipToBack: ImageVector by lazy { lucide("FlipToBack", "send-to-back", """<rect x="14" y="14" width="8" height="8" rx="2"/> <rect x="2" y="2" width="8" height="8" rx="2"/> <path d="M7 14v1a2 2 0 0 0 2 2h1"/> <path d="M14 7h1a2 2 0 0 1 2 2v1"/>""") }
    val FlipToFront: ImageVector by lazy { lucide("FlipToFront", "bring-to-front", """<rect x="8" y="8" width="8" height="8" rx="2"/> <path d="M4 10a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2"/> <path d="M14 20a2 2 0 0 0 2 2h4a2 2 0 0 0 2-2v-4a2 2 0 0 0-2-2"/>""") }
    val Folder: ImageVector by lazy { lucide("Folder", "folder", """<path d="M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z"/>""") }
    val FormatColorFill: ImageVector by lazy { lucide("FormatColorFill", "paint-bucket", """<path d="M11 7 6 2"/> <path d="M18.992 12H2.041"/> <path d="M21.145 18.38A3.34 3.34 0 0 1 20 16.5a3.3 3.3 0 0 1-1.145 1.88c-.575.46-.855 1.02-.855 1.595A2 2 0 0 0 20 22a2 2 0 0 0 2-2.025c0-.58-.285-1.13-.855-1.595"/> <path d="m8.5 4.5 2.148-2.148a1.205 1.205 0 0 1 1.704 0l7.296 7.296a1.205 1.205 0 0 1 0 1.704l-7.592 7.592a3.615 3.615 0 0 1-5.112 0l-3.888-3.888a3.615 3.615 0 0 1 0-5.112L5.67 7.33"/>""") }
    val FormatPaint: ImageVector by lazy { lucide("FormatPaint", "paint-roller", """<rect width="16" height="6" x="2" y="2" rx="2"/> <path d="M10 16v-2a2 2 0 0 1 2-2h8a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2"/> <rect width="4" height="6" x="8" y="16" rx="1"/>""") }
    val FormatShapes: ImageVector by lazy { lucide("FormatShapes", "square-dashed-mouse-pointer", """<path d="M12.034 12.681a.498.498 0 0 1 .647-.647l9 3.5a.5.5 0 0 1-.033.943l-3.444 1.068a1 1 0 0 0-.66.66l-1.067 3.443a.5.5 0 0 1-.943.033z"/> <path d="M5 3a2 2 0 0 0-2 2"/> <path d="M19 3a2 2 0 0 1 2 2"/> <path d="M5 21a2 2 0 0 1-2-2"/> <path d="M9 3h1"/> <path d="M9 21h2"/> <path d="M14 3h1"/> <path d="M3 9v1"/> <path d="M21 9v2"/> <path d="M3 14v1"/>""") }
    val FormatSize: ImageVector by lazy { lucide("FormatSize", "type", """<path d="M12 4v16"/> <path d="M4 7V5a1 1 0 0 1 1-1h14a1 1 0 0 1 1 1v2"/> <path d="M9 20h6"/>""") }
    val FullscreenExit: ImageVector by lazy { lucide("FullscreenExit", "minimize", """<path d="M8 3v3a2 2 0 0 1-2 2H3"/> <path d="M21 8h-3a2 2 0 0 1-2-2V3"/> <path d="M3 16h3a2 2 0 0 1 2 2v3"/> <path d="M16 21v-3a2 2 0 0 1 2-2h3"/>""") }
    val Gamepad: ImageVector by lazy { lucide("Gamepad", "gamepad", """<line x1="6" x2="10" y1="12" y2="12"/> <line x1="8" x2="8" y1="10" y2="14"/> <line x1="15" x2="15.01" y1="13" y2="13"/> <line x1="18" x2="18.01" y1="11" y2="11"/> <rect width="20" height="12" x="2" y="6" rx="2"/>""") }
    val Gesture: ImageVector by lazy { lucide("Gesture", "spline", """<circle cx="19" cy="5" r="2"/> <circle cx="5" cy="19" r="2"/> <path d="M5 17A12 12 0 0 1 17 5"/>""") }
    val Gradient: ImageVector by lazy { lucide("Gradient", "rainbow", """<path d="M22 17a10 10 0 0 0-20 0"/> <path d="M6 17a6 6 0 0 1 12 0"/> <path d="M10 17a2 2 0 0 1 4 0"/>""") }
    val GridOn: ImageVector by lazy { lucide("GridOn", "grid-3x3", """<rect width="18" height="18" x="3" y="3" rx="2"/> <path d="M3 9h18"/> <path d="M3 15h18"/> <path d="M9 3v18"/> <path d="M15 3v18"/>""") }
    val GridView: ImageVector by lazy { lucide("GridView", "layout-grid", """<rect width="7" height="7" x="3" y="3" rx="1"/> <rect width="7" height="7" x="14" y="3" rx="1"/> <rect width="7" height="7" x="14" y="14" rx="1"/> <rect width="7" height="7" x="3" y="14" rx="1"/>""") }
    val GroupWork: ImageVector by lazy { lucide("GroupWork", "group", """<path d="M3 7V5c0-1.1.9-2 2-2h2"/> <path d="M17 3h2c1.1 0 2 .9 2 2v2"/> <path d="M21 17v2c0 1.1-.9 2-2 2h-2"/> <path d="M7 21H5c-1.1 0-2-.9-2-2v-2"/> <rect width="7" height="5" x="7" y="7" rx="1"/> <rect width="7" height="5" x="10" y="12" rx="1"/>""") }
    val Hexagon: ImageVector by lazy { lucide("Hexagon", "hexagon", """<path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>""") }
    val Highlight: ImageVector by lazy { lucide("Highlight", "highlighter", """<path d="m9 11-6 6v3h9l3-3"/> <path d="m22 12-4.6 4.6a2 2 0 0 1-2.8 0l-5.2-5.2a2 2 0 0 1 0-2.8L14 4"/>""") }
    val HighlightAlt: ImageVector by lazy { lucide("HighlightAlt", "square-dashed-mouse-pointer", """<path d="M12.034 12.681a.498.498 0 0 1 .647-.647l9 3.5a.5.5 0 0 1-.033.943l-3.444 1.068a1 1 0 0 0-.66.66l-1.067 3.443a.5.5 0 0 1-.943.033z"/> <path d="M5 3a2 2 0 0 0-2 2"/> <path d="M19 3a2 2 0 0 1 2 2"/> <path d="M5 21a2 2 0 0 1-2-2"/> <path d="M9 3h1"/> <path d="M9 21h2"/> <path d="M14 3h1"/> <path d="M3 9v1"/> <path d="M21 9v2"/> <path d="M3 14v1"/>""") }
    val History: ImageVector by lazy { lucide("History", "history", """<path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/> <path d="M3 3v5h5"/> <path d="M12 7v5l4 2"/>""") }
    val Image: ImageVector by lazy { lucide("Image", "image", """<rect width="18" height="18" x="3" y="3" rx="2" ry="2"/> <circle cx="9" cy="9" r="2"/> <path d="m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"/>""") }
    val Interests: ImageVector by lazy { lucide("Interests", "shapes", """<path d="M8.3 10a.7.7 0 0 1-.626-1.079L11.4 3a.7.7 0 0 1 1.198-.043L16.3 8.9a.7.7 0 0 1-.572 1.1Z"/> <rect x="3" y="14" width="7" height="7" rx="1"/> <circle cx="17.5" cy="17.5" r="3.5"/>""") }
    val InvertColors: ImageVector by lazy { lucide("InvertColors", "droplet", """<path d="M12 22a7 7 0 0 0 7-7c0-2-1-3.9-3-5.5s-3.5-4-4-6.5c-.5 2.5-2 4.9-4 6.5C6 11.1 5 13 5 15a7 7 0 0 0 7 7z"/>""") }
    val IosShare: ImageVector by lazy { lucide("IosShare", "share", """<path d="M12 2v13"/> <path d="m16 6-4-4-4 4"/> <path d="M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8"/>""") }
    val JoinFull: ImageVector by lazy { lucide("JoinFull", "squares-unite", """<path d="M4 16a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v3a1 1 0 0 0 1 1h3a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H10a2 2 0 0 1-2-2v-3a1 1 0 0 0-1-1z"/>""") }
    val KeyboardArrowDown: ImageVector by lazy { lucide("KeyboardArrowDown", "chevron-down", """<path d="m6 9 6 6 6-6"/>""") }
    val KeyboardArrowUp: ImageVector by lazy { lucide("KeyboardArrowUp", "chevron-up", """<path d="m18 15-6-6-6 6"/>""") }
    val Label: ImageVector by lazy { lucide("Label", "tag", """<path d="M12.586 2.586A2 2 0 0 0 11.172 2H4a2 2 0 0 0-2 2v7.172a2 2 0 0 0 .586 1.414l8.704 8.704a2.426 2.426 0 0 0 3.42 0l6.58-6.58a2.426 2.426 0 0 0 0-3.42z"/> <circle cx="7.5" cy="7.5" r=".5" fill="currentColor"/>""", autoMirror = true) }
    val Layers: ImageVector by lazy { lucide("Layers", "layers", """<path d="M12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83z"/> <path d="M2 12a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 12"/> <path d="M2 17a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 17"/>""") }
    val LayersClear: ImageVector by lazy { lucide("LayersClear", "layers-2", """<path d="M13 13.74a2 2 0 0 1-2 0L2.5 8.87a1 1 0 0 1 0-1.74L11 2.26a2 2 0 0 1 2 0l8.5 4.87a1 1 0 0 1 0 1.74z"/> <path d="m20 14.285 1.5.845a1 1 0 0 1 0 1.74L13 21.74a2 2 0 0 1-2 0l-8.5-4.87a1 1 0 0 1 0-1.74l1.5-.845"/>""") }
    val LineStyle: ImageVector by lazy { lucide("LineStyle", "equal-approximately", """<path d="M5 15a6.5 6.5 0 0 1 7 0 6.5 6.5 0 0 0 7 0"/> <path d="M5 9a6.5 6.5 0 0 1 7 0 6.5 6.5 0 0 0 7 0"/>""") }
    val LineWeight: ImageVector by lazy { lucide("LineWeight", "align-justify", """<path d="M3 5h18"/> <path d="M3 12h18"/> <path d="M3 19h18"/>""") }
    val LinearScale: ImageVector by lazy { lucide("LinearScale", "git-commit-horizontal", """<circle cx="12" cy="12" r="3"/> <line x1="3" x2="9" y1="12" y2="12"/> <line x1="15" x2="21" y1="12" y2="12"/>""") }
    val Link: ImageVector by lazy { lucide("Link", "link", """<path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/> <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/>""") }
    val Lock: ImageVector by lazy { lucide("Lock", "lock", """<rect width="18" height="11" x="3" y="11" rx="2" ry="2"/> <path d="M7 11V7a5 5 0 0 1 10 0v4"/>""") }
    val LockOpen: ImageVector by lazy { lucide("LockOpen", "lock-open", """<rect width="18" height="11" x="3" y="11" rx="2" ry="2"/> <path d="M7 11V7a5 5 0 0 1 9.9-1"/>""") }
    val Map: ImageVector by lazy { lucide("Map", "map", """<path d="M14.106 5.553a2 2 0 0 0 1.788 0l3.659-1.83A1 1 0 0 1 21 4.619v12.764a1 1 0 0 1-.553.894l-4.553 2.277a2 2 0 0 1-1.788 0l-4.212-2.106a2 2 0 0 0-1.788 0l-3.659 1.83A1 1 0 0 1 3 19.381V6.618a1 1 0 0 1 .553-.894l4.553-2.277a2 2 0 0 1 1.788 0z"/> <path d="M15 5.764v15"/> <path d="M9 3.236v15"/>""") }
    val Merge: ImageVector by lazy { lucide("Merge", "merge", """<path d="m8 6 4-4 4 4"/> <path d="M12 2v10.3a4 4 0 0 1-1.172 2.872L4 22"/> <path d="m20 22-5-5"/>""") }
    val MergeType: ImageVector by lazy { lucide("MergeType", "git-merge", """<circle cx="18" cy="18" r="3"/> <circle cx="6" cy="6" r="3"/> <path d="M6 21V9a9 9 0 0 0 9 9"/>""") }
    val Mic: ImageVector by lazy { lucide("Mic", "mic", """<path d="M12 19v3"/> <path d="M19 10v2a7 7 0 0 1-14 0v-2"/> <rect x="9" y="2" width="6" height="13" rx="3"/>""") }
    val MoreHoriz: ImageVector by lazy { lucide("MoreHoriz", "ellipsis", """<circle cx="12" cy="12" r="1"/> <circle cx="19" cy="12" r="1"/> <circle cx="5" cy="12" r="1"/>""") }
    val Movie: ImageVector by lazy { lucide("Movie", "clapperboard", """<path d="m12.296 3.464 3.02 3.956"/> <path d="M20.2 6 3 11l-.9-2.4c-.3-1.1.3-2.2 1.3-2.5l13.5-4c1.1-.3 2.2.3 2.5 1.3z"/> <path d="M3 11h18v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/> <path d="m6.18 5.276 3.1 3.899"/>""") }
    val Opacity: ImageVector by lazy { lucide("Opacity", "droplet", """<path d="M12 22a7 7 0 0 0 7-7c0-2-1-3.9-3-5.5s-3.5-4-4-6.5c-.5 2.5-2 4.9-4 6.5C6 11.1 5 13 5 15a7 7 0 0 0 7 7z"/>""") }
    val OpenWith: ImageVector by lazy { lucide("OpenWith", "move", """<path d="M12 2v20"/> <path d="m15 19-3 3-3-3"/> <path d="m19 9 3 3-3 3"/> <path d="M2 12h20"/> <path d="m5 9-3 3 3 3"/> <path d="m9 5 3-3 3 3"/>""") }
    val Palette: ImageVector by lazy { lucide("Palette", "palette", """<path d="M12 22a1 1 0 0 1 0-20 10 9 0 0 1 10 9 5 5 0 0 1-5 5h-2.25a1.75 1.75 0 0 0-1.4 2.8l.3.4a1.75 1.75 0 0 1-1.4 2.8z"/> <circle cx="13.5" cy="6.5" r=".5" fill="currentColor"/> <circle cx="17.5" cy="10.5" r=".5" fill="currentColor"/> <circle cx="6.5" cy="12.5" r=".5" fill="currentColor"/> <circle cx="8.5" cy="7.5" r=".5" fill="currentColor"/>""") }
    val PanTool: ImageVector by lazy { lucide("PanTool", "hand", """<path d="M18 11V6a2 2 0 0 0-2-2a2 2 0 0 0-2 2"/> <path d="M14 10V4a2 2 0 0 0-2-2a2 2 0 0 0-2 2v2"/> <path d="M10 10.5V6a2 2 0 0 0-2-2a2 2 0 0 0-2 2v8"/> <path d="M18 8a2 2 0 1 1 4 0v6a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15"/>""") }
    val PanoramaFishEye: ImageVector by lazy { lucide("PanoramaFishEye", "circle", """<circle cx="12" cy="12" r="10"/>""") }
    val Pause: ImageVector by lazy { lucide("Pause", "pause", """<rect x="14" y="3" width="5" height="18" rx="1"/> <rect x="5" y="3" width="5" height="18" rx="1"/>""") }
    val Pentagon: ImageVector by lazy { lucide("Pentagon", "pentagon", """<path d="M10.83 2.38a2 2 0 0 1 2.34 0l8 5.74a2 2 0 0 1 .73 2.25l-3.04 9.26a2 2 0 0 1-1.9 1.37H7.04a2 2 0 0 1-1.9-1.37L2.1 10.37a2 2 0 0 1 .73-2.25z"/>""") }
    val PictureAsPdf: ImageVector by lazy { lucide("PictureAsPdf", "file-type", """<path d="M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z"/> <path d="M14 2v5a1 1 0 0 0 1 1h5"/> <path d="M11 18h2"/> <path d="M12 12v6"/> <path d="M9 13v-.5a.5.5 0 0 1 .5-.5h5a.5.5 0 0 1 .5.5v.5"/>""") }
    val PlayArrow: ImageVector by lazy { lucide("PlayArrow", "play", """<path d="M5 5a2 2 0 0 1 3.008-1.728l11.997 6.998a2 2 0 0 1 .003 3.458l-12 7A2 2 0 0 1 5 19z"/>""") }
    val PlayCircle: ImageVector by lazy { lucide("PlayCircle", "circle-play", """<path d="M9 9.003a1 1 0 0 1 1.517-.859l4.997 2.997a1 1 0 0 1 0 1.718l-4.997 2.997A1 1 0 0 1 9 14.996z"/> <circle cx="12" cy="12" r="10"/>""") }
    val Polyline: ImageVector by lazy { lucide("Polyline", "spline-pointer", """<path d="M12.034 12.681a.498.498 0 0 1 .647-.647l9 3.5a.5.5 0 0 1-.033.943l-3.444 1.068a1 1 0 0 0-.66.66l-1.067 3.443a.5.5 0 0 1-.943.033z"/> <path d="M5 17A12 12 0 0 1 17 5"/> <circle cx="19" cy="5" r="2"/> <circle cx="5" cy="19" r="2"/>""") }
    val PushPin: ImageVector by lazy { lucide("PushPin", "pin", """<path d="M12 17v5"/> <path d="M9 10.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24V16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V7a1 1 0 0 1 1-1 2 2 0 0 0 0-4H8a2 2 0 0 0 0 4 1 1 0 0 1 1 1z"/>""", filled = true) }
    val QrCode2: ImageVector by lazy { lucide("QrCode2", "qr-code", """<rect width="5" height="5" x="3" y="3" rx="1"/> <rect width="5" height="5" x="16" y="3" rx="1"/> <rect width="5" height="5" x="3" y="16" rx="1"/> <path d="M21 16h-3a2 2 0 0 0-2 2v3"/> <path d="M21 21v.01"/> <path d="M12 7v3a2 2 0 0 1-2 2H7"/> <path d="M3 12h.01"/> <path d="M12 3h.01"/> <path d="M12 16v.01"/> <path d="M16 12h1"/> <path d="M21 12v.01"/> <path d="M12 21v-1"/>""") }
    val QueryStats: ImageVector by lazy { lucide("QueryStats", "chart-line", """<path d="M3 3v16a2 2 0 0 0 2 2h16"/> <path d="m19 9-5 5-4-4-3 3"/>""") }
    val Rectangle: ImageVector by lazy { lucide("Rectangle", "rectangle-horizontal", """<rect width="20" height="12" x="2" y="6" rx="2"/>""") }
    val Redo: ImageVector by lazy { lucide("Redo", "redo-2", """<path d="m15 14 5-5-5-5"/> <path d="M20 9H9.5A5.5 5.5 0 0 0 4 14.5A5.5 5.5 0 0 0 9.5 20H13"/>""", autoMirror = true) }
    val Refresh: ImageVector by lazy { lucide("Refresh", "refresh-cw", """<path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"/> <path d="M21 3v5h-5"/> <path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"/> <path d="M8 16H3v5"/>""") }
    val Remove: ImageVector by lazy { lucide("Remove", "minus", """<path d="M5 12h14"/>""") }
    val RemoveCircleOutline: ImageVector by lazy { lucide("RemoveCircleOutline", "circle-minus", """<circle cx="12" cy="12" r="10"/> <path d="M8 12h8"/>""") }
    val Reorder: ImageVector by lazy { lucide("Reorder", "menu", """<path d="M4 5h16"/> <path d="M4 12h16"/> <path d="M4 19h16"/>""") }
    val Repeat: ImageVector by lazy { lucide("Repeat", "repeat", """<path d="m17 2 4 4-4 4"/> <path d="M3 11v-1a4 4 0 0 1 4-4h14"/> <path d="m7 22-4-4 4-4"/> <path d="M21 13v1a4 4 0 0 1-4 4H3"/>""") }
    val Replay: ImageVector by lazy { lucide("Replay", "rotate-ccw", """<path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/> <path d="M3 3v5h5"/>""") }
    val RestartAlt: ImageVector by lazy { lucide("RestartAlt", "rotate-ccw", """<path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/> <path d="M3 3v5h5"/>""") }
    val Restore: ImageVector by lazy { lucide("Restore", "archive-restore", """<rect width="20" height="5" x="2" y="3" rx="1"/> <path d="M4 8v11a2 2 0 0 0 2 2h2"/> <path d="M20 8v11a2 2 0 0 1-2 2h-2"/> <path d="m9 15 3-3 3 3"/> <path d="M12 12v9"/>""") }
    val RotateRight: ImageVector by lazy { lucide("RotateRight", "rotate-cw", """<path d="M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8"/> <path d="M21 3v5h-5"/>""") }
    val Route: ImageVector by lazy { lucide("Route", "route", """<circle cx="6" cy="19" r="3"/> <path d="M9 19h8.5a3.5 3.5 0 0 0 0-7h-11a3.5 3.5 0 0 1 0-7H15"/> <circle cx="18" cy="5" r="3"/>""") }
    val Save: ImageVector by lazy { lucide("Save", "save", """<path d="M15.2 3a2 2 0 0 1 1.4.6l3.8 3.8a2 2 0 0 1 .6 1.4V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z"/> <path d="M17 21v-7a1 1 0 0 0-1-1H8a1 1 0 0 0-1 1v7"/> <path d="M7 3v4a1 1 0 0 0 1 1h7"/>""") }
    val Schema: ImageVector by lazy { lucide("Schema", "workflow", """<rect width="8" height="8" x="3" y="3" rx="2"/> <path d="M7 11v4a2 2 0 0 0 2 2h4"/> <rect width="8" height="8" x="13" y="13" rx="2"/>""") }
    val Search: ImageVector by lazy { lucide("Search", "search", """<path d="m21 21-4.34-4.34"/> <circle cx="11" cy="11" r="8"/>""") }
    val SelectAll: ImageVector by lazy { lucide("SelectAll", "square-dashed", """<path d="M5 3a2 2 0 0 0-2 2"/> <path d="M19 3a2 2 0 0 1 2 2"/> <path d="M21 19a2 2 0 0 1-2 2"/> <path d="M5 21a2 2 0 0 1-2-2"/> <path d="M9 3h1"/> <path d="M9 21h1"/> <path d="M14 3h1"/> <path d="M14 21h1"/> <path d="M3 9v1"/> <path d="M21 9v1"/> <path d="M3 14v1"/> <path d="M21 14v1"/>""") }
    val Settings: ImageVector by lazy { lucide("Settings", "settings", """<path d="M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915"/> <circle cx="12" cy="12" r="3"/>""") }
    val ShowChart: ImageVector by lazy { lucide("ShowChart", "chart-spline", """<path d="M3 3v16a2 2 0 0 0 2 2h16"/> <path d="M7 16c.5-2 1.5-7 4-7 2 0 2 3 4 3 2.5 0 4.5-5 5-7"/>""") }
    val Speed: ImageVector by lazy { lucide("Speed", "gauge", """<path d="m12 14 4-4"/> <path d="M3.34 19a10 10 0 1 1 17.32 0"/>""") }
    val SportsEsports: ImageVector by lazy { lucide("SportsEsports", "gamepad-2", """<line x1="6" x2="10" y1="11" y2="11"/> <line x1="8" x2="8" y1="9" y2="13"/> <line x1="15" x2="15.01" y1="12" y2="12"/> <line x1="18" x2="18.01" y1="10" y2="10"/> <path d="M17.32 5H6.68a4 4 0 0 0-3.978 3.59c-.006.052-.01.101-.017.152C2.604 9.416 2 14.456 2 16a3 3 0 0 0 3 3c1 0 1.5-.5 2-1l1.414-1.414A2 2 0 0 1 9.828 16h4.344a2 2 0 0 1 1.414.586L17 18c.5.5 1 1 2 1a3 3 0 0 0 3-3c0-1.545-.604-6.584-.685-7.258-.007-.05-.011-.1-.017-.151A4 4 0 0 0 17.32 5z"/>""") }
    val SportsGymnastics: ImageVector by lazy { lucide("SportsGymnastics", "person-standing", """<circle cx="12" cy="5" r="1"/> <path d="m9 20 3-6 3 6"/> <path d="m6 8 6 2 6-2"/> <path d="M12 10v4"/>""") }
    val SquareFoot: ImageVector by lazy { lucide("SquareFoot", "ruler", """<path d="M21.3 15.3a2.4 2.4 0 0 1 0 3.4l-2.6 2.6a2.4 2.4 0 0 1-3.4 0L2.7 8.7a2.41 2.41 0 0 1 0-3.4l2.6-2.6a2.41 2.41 0 0 1 3.4 0Z"/> <path d="m14.5 12.5 2-2"/> <path d="m11.5 9.5 2-2"/> <path d="m8.5 6.5 2-2"/> <path d="m17.5 15.5 2-2"/>""") }
    val Star: ImageVector by lazy { lucide("Star", "star", """<path d="M11.525 2.295a.53.53 0 0 1 .95 0l2.31 4.679a2.123 2.123 0 0 0 1.595 1.16l5.166.756a.53.53 0 0 1 .294.904l-3.736 3.638a2.123 2.123 0 0 0-.611 1.878l.882 5.14a.53.53 0 0 1-.771.56l-4.618-2.428a2.122 2.122 0 0 0-1.973 0L6.396 21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.122 2.122 0 0 0-.611-1.879L2.16 9.795a.53.53 0 0 1 .294-.906l5.165-.755a2.122 2.122 0 0 0 1.597-1.16z"/>""", filled = true) }
    val StarBorder: ImageVector by lazy { lucide("StarBorder", "star", """<path d="M11.525 2.295a.53.53 0 0 1 .95 0l2.31 4.679a2.123 2.123 0 0 0 1.595 1.16l5.166.756a.53.53 0 0 1 .294.904l-3.736 3.638a2.123 2.123 0 0 0-.611 1.878l.882 5.14a.53.53 0 0 1-.771.56l-4.618-2.428a2.122 2.122 0 0 0-1.973 0L6.396 21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.122 2.122 0 0 0-.611-1.879L2.16 9.795a.53.53 0 0 1 .294-.906l5.165-.755a2.122 2.122 0 0 0 1.597-1.16z"/>""") }
    val StarOutline: ImageVector by lazy { lucide("StarOutline", "star", """<path d="M11.525 2.295a.53.53 0 0 1 .95 0l2.31 4.679a2.123 2.123 0 0 0 1.595 1.16l5.166.756a.53.53 0 0 1 .294.904l-3.736 3.638a2.123 2.123 0 0 0-.611 1.878l.882 5.14a.53.53 0 0 1-.771.56l-4.618-2.428a2.122 2.122 0 0 0-1.973 0L6.396 21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.122 2.122 0 0 0-.611-1.879L2.16 9.795a.53.53 0 0 1 .294-.906l5.165-.755a2.122 2.122 0 0 0 1.597-1.16z"/>""") }
    val Stop: ImageVector by lazy { lucide("Stop", "square", """<rect width="18" height="18" x="3" y="3" rx="2"/>""", filled = true) }
    val Straighten: ImageVector by lazy { lucide("Straighten", "ruler", """<path d="M21.3 15.3a2.4 2.4 0 0 1 0 3.4l-2.6 2.6a2.4 2.4 0 0 1-3.4 0L2.7 8.7a2.41 2.41 0 0 1 0-3.4l2.6-2.6a2.41 2.41 0 0 1 3.4 0Z"/> <path d="m14.5 12.5 2-2"/> <path d="m11.5 9.5 2-2"/> <path d="m8.5 6.5 2-2"/> <path d="m17.5 15.5 2-2"/>""") }
    val Style: ImageVector by lazy { lucide("Style", "swatch-book", """<path d="M11 17a4 4 0 0 1-8 0V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2Z"/> <path d="M16.7 13H19a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2H7"/> <path d="M 7 17h.01"/> <path d="m11 8 2.3-2.3a2.4 2.4 0 0 1 3.404.004L18.6 7.6a2.4 2.4 0 0 1 .026 3.434L9.9 19.8"/>""") }
    val SubdirectoryArrowRight: ImageVector by lazy { lucide("SubdirectoryArrowRight", "corner-down-right", """<path d="m15 10 5 5-5 5"/> <path d="M4 4v7a4 4 0 0 0 4 4h12"/>""") }
    val SwapHoriz: ImageVector by lazy { lucide("SwapHoriz", "arrow-left-right", """<path d="M8 3 4 7l4 4"/> <path d="M4 7h16"/> <path d="m16 21 4-4-4-4"/> <path d="M20 17H4"/>""") }
    val SyncAlt: ImageVector by lazy { lucide("SyncAlt", "arrow-right-left", """<path d="m16 3 4 4-4 4"/> <path d="M20 7H4"/> <path d="m8 21-4-4 4-4"/> <path d="M4 17h16"/>""") }
    val TableRows: ImageVector by lazy { lucide("TableRows", "rows-3", """<rect width="18" height="18" x="3" y="3" rx="2"/> <path d="M21 9H3"/> <path d="M21 15H3"/>""") }
    val ToggleTool: ImageVector by lazy { lucide("ToggleTool", "toggle-left", """<circle cx="9" cy="12" r="3"/> <rect width="20" height="14" x="2" y="5" rx="7"/>""") }
    val TextFields: ImageVector by lazy { lucide("TextFields", "type", """<path d="M12 4v16"/> <path d="M4 7V5a1 1 0 0 1 1-1h14a1 1 0 0 1 1 1v2"/> <path d="M9 20h6"/>""") }
    val TextRotationAngleup: ImageVector by lazy { lucide("TextRotationAngleup", "text-cursor", """<path d="M17 22h-1a4 4 0 0 1-4-4V6a4 4 0 0 1 4-4h1"/> <path d="M7 22h1a4 4 0 0 0 4-4"/> <path d="M7 2h1a4 4 0 0 1 4 4"/>""") }
    val Texture: ImageVector by lazy { lucide("Texture", "grid-2x2", """<path d="M12 3v18"/> <path d="M3 12h18"/> <rect x="3" y="3" width="18" height="18" rx="2"/>""") }
    val Timeline: ImageVector by lazy { lucide("Timeline", "git-commit-horizontal", """<circle cx="12" cy="12" r="3"/> <line x1="3" x2="9" y1="12" y2="12"/> <line x1="15" x2="21" y1="12" y2="12"/>""") }
    val Tonality: ImageVector by lazy { lucide("Tonality", "blend", """<circle cx="15" cy="9" r="7"/> <circle cx="9" cy="15" r="7"/>""") }
    val Toys: ImageVector by lazy { lucide("Toys", "toy-brick", """<rect width="18" height="12" x="3" y="8" rx="1"/> <path d="M10 8V5c0-.6-.4-1-1-1H6a1 1 0 0 0-1 1v3"/> <path d="M19 8V5c0-.6-.4-1-1-1h-3a1 1 0 0 0-1 1v3"/>""") }
    val Transform: ImageVector by lazy { lucide("Transform", "scaling", """<path d="M12 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/> <path d="M14 15H9v-5"/> <path d="M16 3h5v5"/> <path d="M21 3 9 15"/>""") }
    val TripOrigin: ImageVector by lazy { lucide("TripOrigin", "circle-dot", """<circle cx="12" cy="12" r="1"/> <circle cx="12" cy="12" r="10"/>""") }
    val Tune: ImageVector by lazy { lucide("Tune", "sliders-horizontal", """<path d="M10 5H3"/> <path d="M12 19H3"/> <path d="M14 3v4"/> <path d="M16 17v4"/> <path d="M21 12h-9"/> <path d="M21 19h-5"/> <path d="M21 5h-7"/> <path d="M8 10v4"/> <path d="M8 12H3"/>""") }
    val Undo: ImageVector by lazy { lucide("Undo", "undo-2", """<path d="M9 14 4 9l5-5"/> <path d="M4 9h10.5a5.5 5.5 0 0 1 5.5 5.5a5.5 5.5 0 0 1-5.5 5.5H11"/>""", autoMirror = true) }
    val VerticalSplit: ImageVector by lazy { lucide("VerticalSplit", "columns-2", """<rect width="18" height="18" x="3" y="3" rx="2"/> <path d="M12 3v18"/>""") }
    val VideoFile: ImageVector by lazy { lucide("VideoFile", "file-video", """<path d="M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z"/> <path d="M14 2v5a1 1 0 0 0 1 1h5"/> <path d="M15.033 13.44a.647.647 0 0 1 0 1.12l-4.065 2.352a.645.645 0 0 1-.968-.56v-4.704a.645.645 0 0 1 .967-.56z"/>""") }
    val ViewColumn: ImageVector by lazy { lucide("ViewColumn", "columns-3", """<rect width="18" height="18" x="3" y="3" rx="2"/> <path d="M9 3v18"/> <path d="M15 3v18"/>""") }
    val ViewInAr: ImageVector by lazy { lucide("ViewInAr", "box", """<path d="M21 8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16Z"/> <path d="m3.3 7 8.7 5 8.7-5"/> <path d="M12 22V12"/>""") }
    val ViewModule: ImageVector by lazy { lucide("ViewModule", "layout-grid", """<rect width="7" height="7" x="3" y="3" rx="1"/> <rect width="7" height="7" x="14" y="3" rx="1"/> <rect width="7" height="7" x="14" y="14" rx="1"/> <rect width="7" height="7" x="3" y="14" rx="1"/>""") }
    val ViewQuilt: ImageVector by lazy { lucide("ViewQuilt", "layout-panel-left", """<rect width="7" height="18" x="3" y="3" rx="1"/> <rect width="7" height="7" x="14" y="3" rx="1"/> <rect width="7" height="7" x="14" y="14" rx="1"/>""") }
    val ViewTimeline: ImageVector by lazy { lucide("ViewTimeline", "gantt-chart", """<path d="M6 5h12"/> <path d="M4 12h10"/> <path d="M12 19h8"/>""") }
    val Visibility: ImageVector by lazy { lucide("Visibility", "eye", """<path d="M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0"/> <circle cx="12" cy="12" r="3"/>""") }
    val VisibilityOff: ImageVector by lazy { lucide("VisibilityOff", "eye-off", """<path d="M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49"/> <path d="M14.084 14.158a3 3 0 0 1-4.242-4.242"/> <path d="M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143"/> <path d="m2 2 20 20"/>""") }
    val VolumeOff: ImageVector by lazy { lucide("VolumeOff", "volume-x", """<path d="M11 4.702a.7.7 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 .997.413l3.383 3.384A.7.7 0 0 0 11 19.298z"/> <path d="m16.5 14.5 5-5"/> <path d="m16.5 9.5 5 5"/>""", autoMirror = true) }
    val VolumeUp: ImageVector by lazy { lucide("VolumeUp", "volume-2", """<path d="M11 4.702a.705.705 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 .997.413l3.383 3.384A.705.705 0 0 0 11 19.298z"/> <path d="M16 9a5 5 0 0 1 0 6"/> <path d="M19.364 18.364a9 9 0 0 0 0-12.728"/>""", autoMirror = true) }
    val Waves: ImageVector by lazy { lucide("Waves", "waves", """<path d="M2 12q2.5 2 5 0t5 0 5 0 5 0"/> <path d="M2 19q2.5 2 5 0t5 0 5 0 5 0"/> <path d="M2 5q2.5 2 5 0t5 0 5 0 5 0"/>""") }
    val WbSunny: ImageVector by lazy { lucide("WbSunny", "sun", """<circle cx="12" cy="12" r="4"/> <path d="M12 2v2"/> <path d="M12 20v2"/> <path d="m4.93 4.93 1.41 1.41"/> <path d="m17.66 17.66 1.41 1.41"/> <path d="M2 12h2"/> <path d="M20 12h2"/> <path d="m6.34 17.66-1.41 1.41"/> <path d="m19.07 4.93-1.41 1.41"/>""") }
    val ZoomOutMap: ImageVector by lazy { lucide("ZoomOutMap", "maximize", """<path d="M8 3H5a2 2 0 0 0-2 2v3"/> <path d="M21 8V5a2 2 0 0 0-2-2h-3"/> <path d="M3 16v3a2 2 0 0 0 2 2h3"/> <path d="M16 21h3a2 2 0 0 0 2-2v-3"/>""") }
    val PushPinOutline: ImageVector by lazy { lucide("PushPinOutline", "pin", """<path d="M12 17v5"/> <path d="M9 10.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24V16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V7a1 1 0 0 1 1-1 2 2 0 0 0 0-4H8a2 2 0 0 0 0 4 1 1 0 0 1 1 1z"/>""") }
}

/** Lucide's grid: 24 x 24 units, a 2-unit stroke. */
private const val GRID = 24f
private const val STROKE = 2f

/**
 * [markup] (the elements of a Lucide SVG: path, circle, ellipse, rect, line, polyline, polygon)
 * as an ImageVector [FernSize.icon] across. Lines are stroked with round caps and joins, as Lucide
 * draws them; [filled] fills every shape as well, and an element Lucide marks
 * `fill="currentColor"` (the dots in the palette) is always filled. The colour is black: `Icon`
 * tints it like any other.
 */
internal fun lucide(name: String, source: String, markup: String, filled: Boolean = false, autoMirror: Boolean = false): ImageVector {
    val builder = ImageVector.Builder(
        name = "$name ($source)",
        defaultWidth = FernSize.icon,
        defaultHeight = FernSize.icon,
        viewportWidth = GRID,
        viewportHeight = GRID,
        autoMirror = autoMirror,
    )
    for (element in ELEMENT.findAll(markup)) {
        val tag = element.groupValues[1]
        val attributes = ATTRIBUTE.findAll(element.groupValues[2]).associate { it.groupValues[1] to it.groupValues[2] }
        val fill = filled || attributes["fill"] == "currentColor"
        builder.addPath(
            pathData = addPathNodes(svgPathData(tag, attributes)),
            fill = if (fill) SolidColor(Color.Black) else null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return builder.build()
}

private val ELEMENT = Regex("""<(path|circle|ellipse|rect|line|polyline|polygon)\b([^>]*?)/>""")
private val ATTRIBUTE = Regex("""([\w-]+)="([^"]*)"""")

/** One SVG shape element as path data. */
internal fun svgPathData(tag: String, a: Map<String, String>): String {
    fun n(key: String, default: Float = 0f) = a[key]?.toFloat() ?: default
    return when (tag) {
        "path" -> a["d"].orEmpty()
        "circle" -> ellipse(n("cx"), n("cy"), n("r"), n("r"))
        "ellipse" -> ellipse(n("cx"), n("cy"), n("rx"), n("ry"))
        "line" -> "M${n("x1")} ${n("y1")}L${n("x2")} ${n("y2")}"
        "polyline", "polygon" -> {
            val points = a["points"].orEmpty().trim().split(Regex("""[\s,]+""")).chunked(2).filter { it.size == 2 }
            points.mapIndexed { i, (x, y) -> (if (i == 0) "M" else "L") + "$x $y" }.joinToString("") + if (tag == "polygon") "Z" else ""
        }
        "rect" -> {
            val x = n("x")
            val y = n("y")
            val w = n("width")
            val h = n("height")
            // An unset ry takes rx's value and the reverse (SVG 2), each at most half the side.
            val rx = (a["rx"] ?: a["ry"])?.toFloat()?.coerceAtMost(w / 2) ?: 0f
            val ry = (a["ry"] ?: a["rx"])?.toFloat()?.coerceAtMost(h / 2) ?: 0f
            if (rx == 0f || ry == 0f) {
                "M$x ${y}H${x + w}V${y + h}H${x}Z"
            } else {
                "M${x + rx} ${y}H${x + w - rx}A$rx $ry 0 0 1 ${x + w} ${y + ry}V${y + h - ry}" +
                    "A$rx $ry 0 0 1 ${x + w - rx} ${y + h}H${x + rx}A$rx $ry 0 0 1 $x ${y + h - ry}" +
                    "V${y + ry}A$rx $ry 0 0 1 ${x + rx} ${y}Z"
            }
        }
        else -> ""
    }
}

/** A full ellipse as two half arcs (one arc can't start and end at the same point). */
private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float): String =
    "M${cx - rx} ${cy}A$rx $ry 0 1 0 ${cx + rx} ${cy}A$rx $ry 0 1 0 ${cx - rx} ${cy}Z"
