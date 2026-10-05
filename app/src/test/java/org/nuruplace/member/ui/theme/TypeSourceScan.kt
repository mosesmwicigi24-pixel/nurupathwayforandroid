// The typography source check (EXPERIENCE.md §8.3, "the scale holds"): reads
// every Kotlin file under app/src/main/java and finds each place a text SIZE
// or a text FACE is decided, so a test can prove both are the app's own.
//
// What counts as a size (a "sink"):
//   • `fontSize = …` anywhere (TextStyle, SpanStyle, .copy, Text's argument);
//   • the size argument of a type helper — nuruSans/nuruSerif, and every
//     function that forwards one of its parameters into a sink (gInter,
//     giSerif, PW.t, EVOverline's `size`, ChatCircleAvatar's `textSize`, …),
//     found to a fixpoint so a NEW helper is checked the day it is written;
//   • a forwarded parameter's default value.
// A size expression must be a literal, an if/when whose branches are
// literals, a local `val` that is one of those, a forwarded parameter (its
// callers are checked instead), or a call into `TypeScale` (whose outputs
// TypeScaleTest proves are on the scale). Anything else — arithmetic on a
// diameter, a percentage of a base — is a "computed size" and fails.
//
// What counts as a face: FontFamily.Default/SansSerif/Serif/Monospace/
// Cursive, Typeface.DEFAULT/SERIF/…/create("…"), and a `TextStyle(…)` that
// names no fontFamily (Compose draws it in the system face).
package org.nuruplace.member.ui.theme

import java.io.File

internal object TypeSourceScan {

    /** §8.1 rule 3's scale, in sp. Nothing under 11. */
    val SCALE: Set<Double> = setOf(11.0, 12.0, 13.0, 14.0, 15.0, 16.0, 18.0, 22.0, 26.0, 28.0)

    data class Finding(val file: String, val line: Int, val detail: String) {
        override fun toString() = "$file:$line  $detail"
    }

    class Source(val path: String, raw: String) {
        /** Comments and string-literal contents blanked to spaces; offsets and lines kept. */
        val code: String = codeOnly(raw)
        private val lineStarts: IntArray = run {
            val starts = ArrayList<Int>(); starts += 0
            raw.forEachIndexed { i, c -> if (c == '\n') starts += i + 1 }
            starts.toIntArray()
        }
        fun lineOf(offset: Int): Int {
            var lo = 0; var hi = lineStarts.size - 1
            while (lo < hi) { val mid = (lo + hi + 1) / 2; if (lineStarts[mid] <= offset) lo = mid else hi = mid - 1 }
            return lo + 1
        }
    }

    // ── Loading ──────────────────────────────────────────────────────────────

    fun mainJavaRoot(): File = listOf(File("src/main/java"), File("app/src/main/java"))
        .firstOrNull { it.isDirectory } ?: error("app/src/main/java not found from ${File(".").absolutePath}")

    fun load(root: File = mainJavaRoot()): List<Source> = root.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .sortedBy { it.path }
        .map { Source(it.relativeTo(root).invariantSeparatorsPath, it.readText()) }
        .toList()

    // ── Lexing: blank comments and string contents ─────────────────────────

    /** Kotlin source with comments and string-literal contents replaced by spaces
     *  (newlines kept), so structure can be parsed without a quote or a "//" in
     *  a URL or a brace in a sentence throwing it off. Templates (`${…}`) inside
     *  a string are blanked with it. */
    fun codeOnly(s: String): String {
        val out = StringBuilder(s)
        // Mode stack: 'N' code, 'S' string, 'R' raw string, 'T' template code.
        val modes = ArrayDeque<Char>().apply { addLast('N') }
        val braces = ArrayDeque<Int>()
        var i = 0
        fun inString() = modes.any { it == 'S' || it == 'R' }
        fun blank(at: Int) { if (s[at] != '\n') out.setCharAt(at, ' ') }
        while (i < s.length) {
            val c = s[i]
            val n = if (i + 1 < s.length) s[i + 1] else '\u0000'
            when (modes.last()) {
                'N', 'T' -> {
                    val blankHere = inString()
                    if (c == '/' && n == '/') {
                        while (i < s.length && s[i] != '\n') { blank(i); i++ }
                        continue
                    }
                    if (c == '/' && n == '*') {
                        var depth = 0
                        while (i < s.length) {
                            if (s.startsWith("/*", i)) { depth++; blank(i); blank(i + 1); i += 2; continue }
                            if (s.startsWith("*/", i)) { depth--; blank(i); blank(i + 1); i += 2; if (depth == 0) break; continue }
                            blank(i); i++
                        }
                        continue
                    }
                    if (s.startsWith("\"\"\"", i)) { if (blankHere) { blank(i); blank(i + 1); blank(i + 2) }; modes.addLast('R'); i += 3; continue }
                    if (c == '"') { if (blankHere) blank(i); modes.addLast('S'); i++; continue }
                    if (c == '\'') {
                        val start = i; i++
                        while (i < s.length && s[i] != '\'' && s[i] != '\n') { if (s[i] == '\\') i++; i++ }
                        i++
                        for (k in start + 1 until minOf(i - 1, s.length)) blank(k)
                        continue
                    }
                    if (modes.last() == 'T') {
                        if (c == '{') braces.addLast(braces.removeLast() + 1)
                        else if (c == '}') {
                            val d = braces.removeLast()
                            if (d == 0) { blank(i); modes.removeLast(); i++; continue } else braces.addLast(d - 1)
                        }
                    }
                    if (blankHere) blank(i)
                    i++
                }
                'S' -> {
                    if (c == '\\') { blank(i); if (i + 1 < s.length) blank(i + 1); i += 2; continue }
                    if (c == '"') { modes.removeLast(); if (inString()) blank(i); i++; continue }
                    if (c == '$' && n == '{') { blank(i); blank(i + 1); modes.addLast('T'); braces.addLast(0); i += 2; continue }
                    blank(i); i++
                }
                'R' -> {
                    if (s.startsWith("\"\"\"", i)) {
                        var j = i + 3
                        while (j < s.length && s[j] == '"') { blank(j - 3); j++ }
                        modes.removeLast()
                        if (inString()) for (k in i until j) blank(k)
                        i = j; continue
                    }
                    if (c == '$' && n == '{') { blank(i); blank(i + 1); modes.addLast('T'); braces.addLast(0); i += 2; continue }
                    blank(i); i++
                }
            }
        }
        return out.toString()
    }

    // ── Structure: functions, parameters, calls ─────────────────────────────

    data class Param(val index: Int, val name: String, val type: String, val default: String?) {
        /** A parameter that can carry a text size (Int, Float, Double, Number, TextUnit). */
        val canCarrySize: Boolean get() = type.removeSuffix("?").trim() in setOf("Int", "Float", "Double", "Number", "TextUnit")
    }

    class Fn(
        val src: Source,
        val name: String,
        val nameOffset: Int,
        val params: List<Param>,
        val bodyStart: Int,
        val bodyEnd: Int,
    ) {
        val body: String get() = src.code.substring(bodyStart, bodyEnd)
    }

    private val FUN = Regex("""\bfun\s+(?:<[^>]*>\s*)?(?:[A-Za-z_][\w.]*\.)?([A-Za-z_]\w*)\s*\(""")

    fun functions(src: Source): List<Fn> {
        val s = src.code
        val out = ArrayList<Fn>()
        for (m in FUN.findAll(s)) {
            val open = m.range.last
            val close = matching(s, open) ?: continue
            val params = splitTopLevel(s, open + 1, close, angle = true).mapIndexedNotNull { idx, (a, b) ->
                parseParam(s, idx, a, b)
            }
            var j = close + 1
            // optional ": ReturnType" (may carry generics or a nullable mark)
            j = skipWs(s, j)
            if (j < s.length && s[j] == ':') {
                j++
                var depth = 0
                while (j < s.length) {
                    val c = s[j]
                    if (c == '<' || c == '(') depth++
                    else if (c == '>' || c == ')') depth--
                    else if (depth == 0 && (c == '=' || c == '{' || c == '\n')) break
                    j++
                }
            }
            j = skipWs(s, j)
            if (j >= s.length) continue
            if (s[j] == '{') {
                val end = matching(s, j) ?: continue
                out += Fn(src, m.groupValues[1], m.range.first, params, j + 1, end)
            } else if (s[j] == '=') {
                val end = expressionEnd(s, j + 1, indentOf(s, m.range.first))
                out += Fn(src, m.groupValues[1], m.range.first, params, j + 1, end)
            }
        }
        return out
    }

    private fun parseParam(s: String, index: Int, a: Int, b: Int): Param? {
        val text = s.substring(a, b)
        val colon = topLevelIndexOf(text, ':') ?: return null
        val head = text.substring(0, colon).trim()
        val name = Regex("""([A-Za-z_]\w*)\s*$""").find(head)?.groupValues?.get(1) ?: return null
        val rest = text.substring(colon + 1)
        val eq = topLevelIndexOf(rest, '=')
        val type = (if (eq == null) rest else rest.substring(0, eq)).trim()
        val default = eq?.let { rest.substring(it + 1).trim() }
        return Param(index, name, type, default)
    }

    private fun topLevelIndexOf(text: String, ch: Char): Int? {
        var depth = 0
        text.forEachIndexed { i, c ->
            when (c) {
                '(', '[', '{', '<' -> depth++
                ')', ']', '}', '>' -> depth--
                else -> if (c == ch && depth == 0) {
                    // "->" is a function type's arrow, not an assignment
                    if (ch == '=' && (text.getOrNull(i + 1) == '=' || text.getOrNull(i - 1) in setOf('!', '<', '>', '='))) return@forEachIndexed
                    return i
                }
            }
        }
        return null
    }

    /** Ranges [a, b) of the top-level comma-separated items between [from] and [to]. */
    fun splitTopLevel(s: String, from: Int, to: Int, angle: Boolean = false): List<Pair<Int, Int>> {
        val items = ArrayList<Pair<Int, Int>>()
        var depth = 0
        var start = from
        var i = from
        while (i < to) {
            val c = s[i]
            when {
                c == '(' || c == '[' || c == '{' -> depth++
                c == ')' || c == ']' || c == '}' -> depth--
                angle && c == '<' -> depth++
                angle && c == '>' && s.getOrNull(i - 1) != '-' -> depth--
                c == ',' && depth == 0 -> { items += start to i; start = i + 1 }
            }
            i++
        }
        if (s.substring(start, to).isNotBlank()) items += start to to
        return items
    }

    /** The index of the bracket closing the one at [open], or null. */
    fun matching(s: String, open: Int): Int? {
        val o = s[open]
        val c = when (o) { '(' -> ')'; '{' -> '}'; '[' -> ']'; else -> return null }
        var depth = 0
        var i = open
        while (i < s.length) {
            if (s[i] == o) depth++ else if (s[i] == c) { depth--; if (depth == 0) return i }
            i++
        }
        return null
    }

    private fun skipWs(s: String, from: Int): Int { var j = from; while (j < s.length && s[j].isWhitespace()) j++; return j }

    private fun indentOf(s: String, offset: Int): Int {
        val lineStart = s.lastIndexOf('\n', offset - 1) + 1
        var k = lineStart
        while (k < s.length && (s[k] == ' ' || s[k] == '\t')) k++
        return k - lineStart
    }

    /** End of an expression that starts at [from]: the first newline at bracket
     *  depth 0 whose next non-blank line is indented no deeper than [indent]
     *  (a declaration's own indentation) and doesn't continue it with `.`/`?:`. */
    fun expressionEnd(s: String, from: Int, indent: Int): Int {
        var depth = 0
        var i = from
        var sawCode = false
        while (i < s.length) {
            val c = s[i]
            when (c) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> { if (depth == 0) return i; depth-- }
                '\n' -> if (depth == 0 && sawCode) {
                    var k = i + 1
                    while (k < s.length && (s[k] == '\n' || s[k] == ' ' || s[k] == '\t')) k++
                    val nextIndent = indentOf(s, k)
                    val cont = s.startsWith(".", k) || s.startsWith("?:", k) || s.startsWith("?.", k) ||
                        s.startsWith("+", k) || s.startsWith("-", k) || s.startsWith("*", k) || s.startsWith("&&", k) || s.startsWith("||", k)
                    if (nextIndent <= indent && !cont) return i
                }
                ';' -> if (depth == 0) return i
                else -> if (!c.isWhitespace()) sawCode = true
            }
            i++
        }
        return s.length
    }

    data class Call(val src: Source, val name: String, val offset: Int, val open: Int, val close: Int)

    private val callCache = HashMap<Pair<String, String>, List<Call>>()

    fun calls(src: Source, name: String): List<Call> = synchronized(callCache) {
        callCache.getOrPut(src.path + "@" + System.identityHashCode(src) to name) { findCalls(src, name) }
    }

    private fun findCalls(src: Source, name: String): List<Call> {
        val s = src.code
        val re = Regex("""(?<![\w])${Regex.escape(name)}\s*\(""")
        return re.findAll(s).mapNotNull { m ->
            val before = s.substring(maxOf(0, m.range.first - 6), m.range.first)
            if (Regex("""fun\s+$""").containsMatchIn(before) || Regex("""fun\s+[\w.]*\.$""").containsMatchIn(before)) return@mapNotNull null
            val open = m.range.last
            val close = matching(s, open) ?: return@mapNotNull null
            Call(src, name, m.range.first, open, close)
        }.toList()
    }

    /** The argument expression a call passes for [param], or null when it relies on the default. */
    fun argumentFor(call: Call, param: Param): Pair<String, Int>? {
        val s = call.src.code
        val items = splitTopLevel(s, call.open + 1, call.close)
        var positional = 0
        for ((a, b) in items) {
            val text = s.substring(a, b)
            val named = Regex("""^\s*([A-Za-z_]\w*)\s*=(?!=)""").find(text)
            if (named != null) {
                if (named.groupValues[1] == param.name) {
                    val exprStart = a + named.range.last + 1
                    return s.substring(exprStart, b).trim() to exprStart
                }
            } else {
                if (positional == param.index) return text.trim() to a
                positional++
            }
        }
        return null
    }

    // ── Helpers: functions that forward a parameter into a text size ────────

    data class Helper(val fn: Fn, val param: Param)

    /** Every type helper in the code base, found to a fixpoint from nuruSans /
     *  nuruSerif and `fontSize = <param>`. Keyed by function name. */
    fun helpers(sources: List<Source>, excluded: Set<String> = emptySet()): Map<String, List<Helper>> {
        val all = sources.flatMap { functions(it) }
        val found = LinkedHashMap<String, MutableList<Helper>>()
        var changed = true
        while (changed) {
            changed = false
            for (fn in all) {
                if (fn.name in excluded) continue
                for (p in fn.params) {
                    if (!p.canCarrySize) continue
                    if (found[fn.name]?.any { it.fn === fn && it.param.name == p.name } == true) continue
                    if (forwards(fn, p, found)) {
                        found.getOrPut(fn.name) { mutableListOf() } += Helper(fn, p)
                        changed = true
                    }
                }
            }
        }
        return found
    }

    private fun forwards(fn: Fn, p: Param, known: Map<String, List<Helper>>): Boolean {
        val body = fn.body
        val ident = Regex.escape(p.name)
        if (Regex("""fontSize\s*=\s*\(?\s*$ident(\.sp|\.toFloat\(\)\.sp)?\b(?!\s*[*/+-])""").containsMatchIn(body)) return true
        for ((name, hs) in known) {
            if (!body.contains(name)) continue
            for (c in calls(fn.src, name)) {
                if (c.offset < fn.bodyStart || c.offset >= fn.bodyEnd) continue
                for (h in hs) {
                    val arg = argumentFor(c, h.param)?.first ?: continue
                    if (arg == p.name || arg == "${p.name}.sp") return true
                }
            }
        }
        return false
    }

    // ── Evaluating a size expression ────────────────────────────────────────

    sealed interface Value {
        data class Sizes(val values: List<Double>) : Value
        object Forwarded : Value
        object Scale : Value
        /** A dp or other non-text measure that happens to share a parameter name. */
        object NotText : Value
        data class Computed(val expr: String) : Value
    }

    private val LITERAL = Regex("""^\(?\s*(\d+(?:\.\d+)?)[fF]?(?:\.sp)?\s*\)?$""")

    fun evaluate(expr: String, at: Int, src: Source, fns: List<Fn>, forwardedHere: Set<String>, depth: Int = 0): Value {
        val e = expr.trim().removeSuffix(".sp").trim()
        LITERAL.find(e)?.let { return Value.Sizes(listOf(it.groupValues[1].toDouble())) }
        if (Regex("""\.dp\b""").containsMatchIn(e) || e.endsWith("Dp") || e == "null") return Value.NotText
        if (depth > 4) return Value.Computed(expr)
        if (e.startsWith("TypeScale.")) return Value.Scale
        if (e.startsWith("(") && matching(e, 0) == e.length - 1) return evaluate(e.substring(1, e.length - 1), at, src, fns, forwardedHere, depth + 1)
        // if (…) A else B
        if (e.startsWith("if")) {
            val open = e.indexOf('(')
            val close = if (open >= 0) matching(e, open) else null
            if (close != null) {
                val rest = e.substring(close + 1)
                val elseAt = Regex("""\belse\b""").find(rest)?.range?.first
                if (elseAt != null) {
                    val a = evaluate(rest.substring(0, elseAt), at, src, fns, forwardedHere, depth + 1)
                    val b = evaluate(rest.substring(elseAt + 4), at, src, fns, forwardedHere, depth + 1)
                    return merge(listOf(a, b), expr)
                }
            }
        }
        // when { … -> A; … -> B }
        if (e.startsWith("when")) {
            val open = e.indexOf('{')
            val close = if (open >= 0) matching(e, open) else null
            if (close != null) {
                val inner = e.substring(open + 1, close)
                val results = Regex("""->\s*([^;\n}]+)""").findAll(inner).map { it.groupValues[1] }.toList()
                if (results.isNotEmpty()) return merge(results.map { evaluate(it, at, src, fns, forwardedHere, depth + 1) }, expr)
            }
        }
        if (Regex("""^[A-Za-z_]\w*$""").matches(e)) {
            if (e in forwardedHere) return Value.Forwarded
            // A local `val e = …` earlier in the enclosing function.
            val fn = enclosing(fns, at)
            val scopeStart = fn?.bodyStart ?: 0
            val scope = src.code.substring(scopeStart, at)
            val decl = Regex("""\bval\s+${Regex.escape(e)}\s*(?::[^=\n]+)?=\s*""").findAll(scope).lastOrNull()
            if (decl != null) {
                val start = scopeStart + decl.range.last + 1
                val end = expressionEnd(src.code, start, indentOf(src.code, scopeStart + decl.range.first))
                return evaluate(src.code.substring(start, end), start, src, fns, forwardedHere, depth + 1)
            }
        }
        return Value.Computed(expr)
    }

    private fun merge(parts: List<Value>, expr: String): Value {
        if (parts.any { it is Value.Computed }) return Value.Computed(expr)
        val sizes = parts.filterIsInstance<Value.Sizes>().flatMap { it.values }
        return if (sizes.isEmpty()) parts.first() else Value.Sizes(sizes)
    }

    fun enclosing(fns: List<Fn>, offset: Int): Fn? =
        fns.filter { offset >= it.bodyStart && offset < it.bodyEnd }.minByOrNull { it.bodyEnd - it.bodyStart }

    // ── The checks ───────────────────────────────────────────────────────────

    data class SizeSite(val src: Source, val offset: Int, val what: String, val value: Value)

    /** Every place a text size is decided (outside [excludedDirs]). */
    fun sizeSites(sources: List<Source>, excludedDirs: List<String>, excludedHelpers: Set<String>): List<SizeSite> {
        val scanned = sources.filter { s -> excludedDirs.none { s.path.startsWith(it) } }
        val helpers = helpers(scanned, excludedHelpers)
        val fnsBySrc = scanned.associateWith { functions(it) }
        val sites = ArrayList<SizeSite>()
        fun forwardedAt(src: Source, offset: Int): Set<String> {
            val fn = enclosing(fnsBySrc.getValue(src), offset) ?: return emptySet()
            return helpers[fn.name].orEmpty().filter { it.fn.src === fn.src && it.fn.nameOffset == fn.nameOffset }.map { it.param.name }.toSet()
        }
        for (src in scanned) {
            val fns = fnsBySrc.getValue(src)
            // 1. fontSize = … (an excluded route's own body is the route, not a use of it)
            for (m in Regex("""\bfontSize\s*=(?!=)\s*""").findAll(src.code)) {
                val start = m.range.last + 1
                if (enclosing(fns, start)?.name in excludedHelpers) continue
                val items = argumentExtent(src.code, start)
                val expr = src.code.substring(start, items).trim()
                sites += SizeSite(src, m.range.first, "fontSize = $expr", evaluate(expr, start, src, fns, forwardedAt(src, start)))
            }
            // 2. helper calls
            for ((name, hs) in helpers) {
                for (c in calls(src, name)) {
                    if (enclosing(fns, c.offset)?.name in excludedHelpers) continue
                    for (h in hs.distinctBy { it.param.name to it.param.index }) {
                        val (expr, at) = argumentFor(c, h.param) ?: continue
                        sites += SizeSite(src, c.offset, "$name(${h.param.name} = $expr)", evaluate(expr, at, src, fns, forwardedAt(src, at)))
                    }
                }
            }
        }
        // 3. forwarded parameters' defaults
        for ((name, hs) in helpers) for (h in hs) {
            val d = h.param.default ?: continue
            val fns = fnsBySrc[h.fn.src] ?: continue
            sites += SizeSite(h.fn.src, h.fn.nameOffset, "$name default ${h.param.name} = $d", evaluate(d, h.fn.nameOffset, h.fn.src, fns, emptySet()))
        }
        return sites
    }

    /** End of a named argument / assignment's expression starting at [from]. */
    private fun argumentExtent(s: String, from: Int): Int {
        var depth = 0
        var i = from
        while (i < s.length) {
            val c = s[i]
            when (c) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> { if (depth == 0) return i; depth-- }
                ',' -> if (depth == 0) return i
                '\n' -> if (depth == 0) {
                    var k = i + 1
                    while (k < s.length && (s[k] == ' ' || s[k] == '\t')) k++
                    if (!(s.startsWith("else", k) || s.startsWith(".", k) || s.startsWith("?:", k))) return i
                }
                ';' -> if (depth == 0) return i
            }
            i++
        }
        return s.length
    }

    fun offScale(sites: List<SizeSite>): List<Finding> = sites.flatMap { site ->
        when (val v = site.value) {
            is Value.Sizes -> v.values.filter { it !in SCALE }.map {
                Finding(site.src.path, site.src.lineOf(site.offset), "${fmt(it)} sp is off the scale — ${site.what}")
            }
            is Value.Computed -> listOf(Finding(site.src.path, site.src.lineOf(site.offset), "computed size — route it through TypeScale: ${site.what}"))
            else -> emptyList()
        }
    }.distinct()

    private fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()

    private val SYSTEM_FACE = listOf(
        Regex("""\bFontFamily\.(Default|SansSerif|Serif|Monospace|Cursive)\b"""),
        Regex("""\bTypeface\.(DEFAULT|DEFAULT_BOLD|SANS_SERIF|SERIF|MONOSPACE)\b"""),
        Regex("""\bTypeface\.create\(\s*"""),
        Regex("""\bTypeface\.defaultFromStyle\("""),
    )

    /** Every system or default face used for text, outside [excludedPaths]. */
    fun systemFaces(sources: List<Source>, excludedPaths: List<String>): List<Finding> {
        val out = ArrayList<Finding>()
        for (src in sources) {
            if (excludedPaths.any { src.path.startsWith(it) }) continue
            for (re in SYSTEM_FACE) for (m in re.findAll(src.code)) {
                // Typeface.create(existing, style) restyles a face it was given — only a NAMED family is a system face.
                if (m.value.startsWith("Typeface.create") && !src.code.substring(m.range.last + 1).trimStart().startsWith("\"")) continue
                out += Finding(src.path, src.lineOf(m.range.first), "system face: ${m.value.trimEnd('(', ' ')}")
            }
            for (c in calls(src, "TextStyle")) {
                val args = src.code.substring(c.open + 1, c.close)
                if (!Regex("""\bfontFamily\s*=""").containsMatchIn(args)) {
                    out += Finding(src.path, src.lineOf(c.offset), "TextStyle(…) with no fontFamily — drawn in the system face")
                }
            }
        }
        return out
    }
}
