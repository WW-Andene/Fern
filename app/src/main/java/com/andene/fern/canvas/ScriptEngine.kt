package com.andene.fern.canvas

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

/** An error in a script, and the line it's on (1-based). */
class ScriptError(val line: Int, message: String) : Exception(message)

/**
 * What a script runs in (roadmap item 164): the commands and functions it adds to the language,
 * and the names it lets the script read. A host is the only way out of the sandbox.
 */
interface ScriptHost {
    /** The host's commands by lowercase name, with how many values each takes. */
    val commands: Map<String, Int>

    /** The host's functions by lowercase name, with how many values each takes. */
    val functions: Map<String, Int>

    fun command(name: String, args: List<Any>, line: Int)

    fun function(name: String, args: List<Any>, line: Int): Any

    /** The value of the host's read-only name [name] (a number or a text), or null when it has none. */
    fun variable(name: String): Any?
}

/** A parsed script, ready to run any number of times. */
class ScriptProgram internal constructor(internal val body: List<ScriptEngine.Stmt>, internal val procedures: Map<String, ScriptEngine.Proc>)

/**
 * The scripting language shared by drawing scripts and game scripts (roadmap item 164), run in
 * a sandbox: it reaches nothing but its [ScriptHost], and stops after a set number of steps.
 *
 * - Statements: one host command per line, or several separated by spaces; `//` starts a comment.
 * - `set name value`: a variable. Variables keep their value between runs of a game script.
 * - `repeat n [ ... ]`, `while condition [ ... ]`, `if condition [ ... ] else [ ... ]`
 *   (`else if` chains).
 * - `to name a b [ ... ]` defines a procedure called as `name 1 2`; `stop` leaves it (or the script).
 * - Values are numbers or "texts"; `#RRGGBB` is a colour text. Operators: `+ - * / %`,
 *   `== != < > <= >=` (`=` also tests equality), `and`, `or`, `not`; true is 1, false is 0.
 *   `+` joins texts.
 * - Functions, called with brackets right after the name: abs, sqrt, sin, cos, tan (degrees),
 *   atan2(y, x), min, max, round, floor, ceil, pow, random(a, b), plus the host's.
 *
 * A minus written straight against a number after a space (`move 3 -5`) is that number's sign,
 * so that commands can take several values; subtraction is written `a - b`.
 */
object ScriptEngine {
    internal sealed interface Expr { val line: Int }
    private data class Num(override val line: Int, val value: Double) : Expr
    private data class Text(override val line: Int, val value: String) : Expr
    private data class Name(override val line: Int, val name: String) : Expr
    private data class Call(override val line: Int, val name: String, val args: List<Expr>) : Expr
    private data class Unary(override val line: Int, val op: String, val operand: Expr) : Expr
    private data class Binary(override val line: Int, val op: String, val left: Expr, val right: Expr) : Expr

    internal sealed interface Stmt { val line: Int }
    private data class Command(override val line: Int, val name: String, val args: List<Expr>) : Stmt
    private data class Assign(override val line: Int, val name: String, val value: Expr) : Stmt
    private data class Repeat(override val line: Int, val times: Expr, val body: List<Stmt>) : Stmt
    private data class While(override val line: Int, val condition: Expr, val body: List<Stmt>) : Stmt
    private data class If(override val line: Int, val condition: Expr, val then: List<Stmt>, val otherwise: List<Stmt>) : Stmt
    private data class Stop(override val line: Int) : Stmt

    internal class Proc(val params: List<String>, val body: List<Stmt>)

    private enum class Kind { NUMBER, TEXT, WORD, SYMBOL }

    private data class Token(val kind: Kind, val text: String, val line: Int, val spaceBefore: Boolean)

    private val keywords = setOf("to", "if", "else", "while", "repeat", "set", "stop", "and", "or", "not", "true", "false")

    private val builtins = mapOf(
        "abs" to 1, "sqrt" to 1, "sin" to 1, "cos" to 1, "tan" to 1, "atan2" to 2, "min" to 2, "max" to 2,
        "round" to 1, "floor" to 1, "ceil" to 1, "pow" to 2, "random" to 2,
    )

    // Procedures calling procedures this deep stop the script, so recursion can't overflow the stack.
    private const val MAX_DEPTH = 100

    /** Parses [source] for a host offering [commands] (name to value count); throws [ScriptError]. */
    fun parse(source: String, commands: Map<String, Int>, functions: Map<String, Int>): ScriptProgram {
        val tokens = tokenize(source)
        val arities = HashMap(commands)
        val procedures = HashMap<String, Proc>()
        // Procedures may be called before they're defined, so their value counts are read first.
        for (i in tokens.indices) {
            if (tokens[i].kind != Kind.WORD || tokens[i].text != "to") continue
            val name = tokens.getOrNull(i + 1)?.takeIf { it.kind == Kind.WORD } ?: throw ScriptError(tokens[i].line, "to needs a procedure name")
            if (name.text in keywords || name.text in commands) throw ScriptError(name.line, "'${name.text}' can't be a procedure name")
            if (name.text in procedures || arities.containsKey(name.text) && name.text !in commands) throw ScriptError(name.line, "'${name.text}' is defined twice")
            var k = i + 2
            while (tokens.getOrNull(k)?.kind == Kind.WORD) k++
            arities[name.text] = k - (i + 2)
        }
        val parser = Parser(tokens, arities, functions, procedures)
        return ScriptProgram(parser.program(), procedures)
    }

    /**
     * Runs [program] with [host]; [variables] are the script's own and keep what it sets.
     * Throws [ScriptError], also once [maxSteps] statements and loop passes have run.
     */
    fun run(program: ScriptProgram, host: ScriptHost, variables: MutableMap<String, Any>, maxSteps: Int, random: Random = Random.Default) {
        Runner(program, host, variables, maxSteps, random).top()
    }

    private fun tokenize(source: String): List<Token> {
        val tokens = ArrayList<Token>()
        for ((index, raw) in source.lines().withIndex()) {
            val line = index + 1
            var i = 0
            var space = true
            fun add(kind: Kind, text: String) {
                tokens.add(Token(kind, text, line, space))
                space = false
            }
            while (i < raw.length) {
                val c = raw[i]
                val next = raw.getOrNull(i + 1)
                when {
                    c.isWhitespace() -> { space = true; i++ }
                    c == '/' && next == '/' -> i = raw.length
                    c == '"' -> {
                        val end = raw.indexOf('"', i + 1)
                        if (end < 0) throw ScriptError(line, "a text is missing its closing \"")
                        add(Kind.TEXT, raw.substring(i + 1, end))
                        i = end + 1
                    }
                    c == '#' -> {
                        var j = i + 1
                        while (j < raw.length && raw[j].isLetterOrDigit()) j++
                        add(Kind.TEXT, raw.substring(i, j))
                        i = j
                    }
                    c.isDigit() || c == '.' && next?.isDigit() == true ||
                        c == '-' && (next?.isDigit() == true || next == '.') && negativeLiteral(tokens.lastOrNull(), line, space) -> {
                        var j = i + 1
                        while (j < raw.length && (raw[j].isDigit() || raw[j] == '.')) j++
                        add(Kind.NUMBER, raw.substring(i, j))
                        i = j
                    }
                    c.isLetter() || c == '_' -> {
                        var j = i + 1
                        while (j < raw.length && (raw[j].isLetterOrDigit() || raw[j] == '_')) j++
                        add(Kind.WORD, raw.substring(i, j).lowercase())
                        i = j
                    }
                    else -> {
                        val two = if (next != null) "$c$next" else ""
                        if (two in setOf("<=", ">=", "==", "!=")) {
                            add(Kind.SYMBOL, two)
                            i += 2
                        } else if (c in "[](),+-*/%<>=") {
                            add(Kind.SYMBOL, c.toString())
                            i++
                        } else {
                            throw ScriptError(line, "unexpected '$c'")
                        }
                    }
                }
            }
        }
        return tokens
    }

    // Whether a minus starts a negative number: at the start of a value, or after a space with the digit against it.
    private fun negativeLiteral(previous: Token?, line: Int, space: Boolean): Boolean =
        previous == null || previous.line != line || space ||
            previous.kind == Kind.SYMBOL && previous.text != ")" && previous.text != "]" ||
            previous.kind == Kind.WORD && previous.text in keywords

    private class Parser(
        private val tokens: List<Token>,
        private val arities: Map<String, Int>,
        private val functions: Map<String, Int>,
        private val procedures: MutableMap<String, Proc>,
    ) {
        private var i = 0

        fun program(): List<Stmt> = block(nested = false, openLine = 0)

        private fun peek() = tokens.getOrNull(i)

        private fun lastLine() = tokens.getOrNull(i - 1)?.line ?: tokens.lastOrNull()?.line ?: 1

        private fun expectOpen(what: String): Int {
            val open = tokens.getOrNull(i++)
            if (open?.kind != Kind.SYMBOL || open.text != "[") throw ScriptError(open?.line ?: lastLine(), "$what needs [ ... ]")
            return open.line
        }

        private fun block(nested: Boolean, openLine: Int): List<Stmt> {
            val statements = ArrayList<Stmt>()
            while (i < tokens.size) {
                val token = tokens[i]
                if (token.kind == Kind.SYMBOL && token.text == "]") {
                    i++
                    if (nested) return statements
                    throw ScriptError(token.line, "unexpected ]")
                }
                statement()?.let(statements::add)
            }
            if (nested) throw ScriptError(openLine, "missing ]")
            return statements
        }

        private fun statement(): Stmt? {
            val token = tokens[i++]
            if (token.kind != Kind.WORD) throw ScriptError(token.line, "expected a command, found '${token.text}'")
            return when (token.text) {
                "set" -> {
                    val name = tokens.getOrNull(i++)?.takeIf { it.kind == Kind.WORD && it.text !in keywords } ?: throw ScriptError(token.line, "set needs a name and a value")
                    if (name.text in arities) throw ScriptError(name.line, "'${name.text}' is a command, not a variable")
                    Assign(token.line, name.text, expression())
                }
                "repeat" -> {
                    val times = expression()
                    Repeat(token.line, times, block(nested = true, openLine = expectOpen("repeat")))
                }
                "while" -> {
                    val condition = expression()
                    While(token.line, condition, block(nested = true, openLine = expectOpen("while")))
                }
                "if" -> ifStatement(token)
                "stop" -> Stop(token.line)
                "to" -> {
                    val name = tokens[i++].text
                    val params = ArrayList<String>()
                    while (peek()?.kind == Kind.WORD) params.add(tokens[i++].text)
                    procedures[name] = Proc(params, block(nested = true, openLine = expectOpen("to $name")))
                    null
                }
                "else" -> throw ScriptError(token.line, "else needs an if before it")
                else -> {
                    val n = arities[token.text] ?: throw ScriptError(token.line, "unknown command '${token.text}'")
                    Command(token.line, token.text, (0 until n).map {
                        if (i >= tokens.size) throw ScriptError(token.line, "${token.text} needs $n value(s)")
                        expression()
                    })
                }
            }
        }

        private fun ifStatement(token: Token): Stmt {
            val condition = expression()
            val then = block(nested = true, openLine = expectOpen("if"))
            val next = peek()
            if (next?.kind != Kind.WORD || next.text != "else") return If(token.line, condition, then, emptyList())
            i++
            val after = peek()
            val otherwise = if (after?.kind == Kind.WORD && after.text == "if") {
                i++
                listOf(ifStatement(after))
            } else {
                block(nested = true, openLine = expectOpen("else"))
            }
            return If(token.line, condition, then, otherwise)
        }

        fun expression(): Expr = or()

        private fun isSymbol(text: String) = peek()?.let { it.kind == Kind.SYMBOL && it.text == text } == true

        private fun isWord(text: String) = peek()?.let { it.kind == Kind.WORD && it.text == text } == true

        private fun or(): Expr {
            var left = and()
            while (isWord("or")) {
                val line = tokens[i++].line
                left = Binary(line, "or", left, and())
            }
            return left
        }

        private fun and(): Expr {
            var left = not()
            while (isWord("and")) {
                val line = tokens[i++].line
                left = Binary(line, "and", left, not())
            }
            return left
        }

        private fun not(): Expr {
            if (!isWord("not")) return comparison()
            val line = tokens[i++].line
            return Unary(line, "not", not())
        }

        private fun comparison(): Expr {
            var left = additive()
            while (peek()?.let { it.kind == Kind.SYMBOL && it.text in setOf("==", "=", "!=", "<", ">", "<=", ">=") } == true) {
                val op = tokens[i++]
                left = Binary(op.line, if (op.text == "=") "==" else op.text, left, additive())
            }
            return left
        }

        private fun additive(): Expr {
            var left = multiplicative()
            while (isSymbol("+") || isSymbol("-")) {
                val op = tokens[i++]
                left = Binary(op.line, op.text, left, multiplicative())
            }
            return left
        }

        private fun multiplicative(): Expr {
            var left = unary()
            while (isSymbol("*") || isSymbol("/") || isSymbol("%")) {
                val op = tokens[i++]
                left = Binary(op.line, op.text, left, unary())
            }
            return left
        }

        private fun unary(): Expr {
            if (!isSymbol("-")) return primary()
            val line = tokens[i++].line
            return Unary(line, "-", unary())
        }

        private fun primary(): Expr {
            val token = tokens.getOrNull(i++) ?: throw ScriptError(lastLine(), "a value is missing")
            return when (token.kind) {
                Kind.NUMBER -> Num(token.line, token.text.toDoubleOrNull() ?: throw ScriptError(token.line, "'${token.text}' isn't a number"))
                Kind.TEXT -> Text(token.line, token.text)
                Kind.SYMBOL -> {
                    if (token.text != "(") throw ScriptError(token.line, "unexpected '${token.text}'")
                    val inner = expression()
                    if (!isSymbol(")")) throw ScriptError(token.line, "missing )")
                    i++
                    inner
                }
                Kind.WORD -> when {
                    token.text == "true" -> Num(token.line, 1.0)
                    token.text == "false" -> Num(token.line, 0.0)
                    token.text in keywords -> throw ScriptError(token.line, "'${token.text}' can't be used as a value")
                    isSymbol("(") && peek()?.spaceBefore == false -> call(token)
                    else -> Name(token.line, token.text)
                }
            }
        }

        private fun call(name: Token): Expr {
            i++ // (
            val arity = builtins[name.text] ?: functions[name.text] ?: throw ScriptError(name.line, "unknown function '${name.text}'")
            val args = ArrayList<Expr>()
            if (!isSymbol(")")) {
                args.add(expression())
                while (isSymbol(",")) {
                    i++
                    args.add(expression())
                }
            }
            if (!isSymbol(")")) throw ScriptError(name.line, "missing ) after ${name.text}(")
            i++
            if (args.size != arity) throw ScriptError(name.line, "${name.text} takes $arity value(s)")
            return Call(name.line, name.text, args)
        }
    }

    private class StopSignal : RuntimeException(null, null, false, false)

    private class Runner(
        private val program: ScriptProgram,
        private val host: ScriptHost,
        private val globals: MutableMap<String, Any>,
        private val maxSteps: Int,
        private val random: Random,
    ) {
        private var steps = 0
        private var depth = 0

        fun top() {
            try {
                runAll(program.body, null)
            } catch (stopped: StopSignal) {
            }
        }

        private fun count(line: Int) {
            if (++steps > maxSteps) throw ScriptError(line, "the script ran more than $maxSteps steps")
        }

        private fun runAll(statements: List<Stmt>, locals: MutableMap<String, Any>?) {
            for (s in statements) {
                count(s.line)
                when (s) {
                    is Assign -> {
                        if (host.variable(s.name) != null) throw ScriptError(s.line, "'${s.name}' can't be set")
                        val value = eval(s.value, locals)
                        if (locals != null && s.name in locals) locals[s.name] = value else globals[s.name] = value
                    }
                    // Each pass costs a step too, so even an empty loop can't run forever.
                    is Repeat -> {
                        val times = number(eval(s.times, locals), s.line)
                        if (times < 0 || times != floor(times)) throw ScriptError(s.line, "repeat count must be a whole number")
                        var k = 0L
                        while (k < times.toLong()) {
                            count(s.line)
                            runAll(s.body, locals)
                            k++
                        }
                    }
                    is While -> while (truthy(eval(s.condition, locals))) {
                        count(s.line)
                        runAll(s.body, locals)
                    }
                    is If -> runAll(if (truthy(eval(s.condition, locals))) s.then else s.otherwise, locals)
                    is Stop -> throw StopSignal()
                    is Command -> {
                        val args = s.args.map { eval(it, locals) }
                        val procedure = program.procedures[s.name]
                        if (procedure == null) {
                            host.command(s.name, args, s.line)
                        } else {
                            if (++depth > MAX_DEPTH) throw ScriptError(s.line, "procedures call each other more than $MAX_DEPTH deep")
                            try {
                                runAll(procedure.body, HashMap(procedure.params.zip(args).toMap()))
                            } catch (stopped: StopSignal) {
                            } finally {
                                depth--
                            }
                        }
                    }
                }
            }
        }

        private fun eval(e: Expr, locals: Map<String, Any>?): Any = when (e) {
            is Num -> e.value
            is Text -> e.value
            is Name -> locals?.get(e.name) ?: globals[e.name] ?: host.variable(e.name) ?: throw ScriptError(e.line, "unknown name '${e.name}'")
            is Unary -> if (e.op == "not") bool(!truthy(eval(e.operand, locals))) else finite(-number(eval(e.operand, locals), e.line), e.line)
            is Binary -> binary(e, locals)
            is Call -> {
                val args = e.args.map { eval(it, locals) }
                if (e.name in builtins) builtin(e.name, args, e.line) else host.function(e.name, args, e.line)
            }
        }

        private fun binary(e: Binary, locals: Map<String, Any>?): Any {
            if (e.op == "and") return bool(truthy(eval(e.left, locals)) && truthy(eval(e.right, locals)))
            if (e.op == "or") return bool(truthy(eval(e.left, locals)) || truthy(eval(e.right, locals)))
            val a = eval(e.left, locals)
            val b = eval(e.right, locals)
            return when (e.op) {
                "==" -> bool(same(a, b))
                "!=" -> bool(!same(a, b))
                "+" -> if (a is String || b is String) show(a) + show(b) else finite(number(a, e.line) + number(b, e.line), e.line)
                else -> {
                    val x = number(a, e.line)
                    val y = number(b, e.line)
                    when (e.op) {
                        "-" -> finite(x - y, e.line)
                        "*" -> finite(x * y, e.line)
                        "/" -> if (y == 0.0) throw ScriptError(e.line, "division by zero") else finite(x / y, e.line)
                        "%" -> if (y == 0.0) throw ScriptError(e.line, "division by zero") else finite(x - floor(x / y) * y, e.line)
                        "<" -> bool(x < y)
                        ">" -> bool(x > y)
                        "<=" -> bool(x <= y)
                        else -> bool(x >= y)
                    }
                }
            }
        }

        private fun builtin(name: String, args: List<Any>, line: Int): Any {
            val a = number(args[0], line)
            val b = args.getOrNull(1)?.let { number(it, line) } ?: 0.0
            val radians = a * PI / 180
            return finite(
                when (name) {
                    "abs" -> abs(a)
                    "sqrt" -> if (a < 0) throw ScriptError(line, "sqrt of a negative number") else sqrt(a)
                    "sin" -> sin(radians)
                    "cos" -> cos(radians)
                    "tan" -> tan(radians)
                    "atan2" -> atan2(a, b) * 180 / PI
                    "min" -> minOf(a, b)
                    "max" -> maxOf(a, b)
                    "round" -> round(a)
                    "floor" -> floor(a)
                    "ceil" -> ceil(a)
                    "pow" -> a.pow(b)
                    else -> if (b <= a) a else random.nextDouble(a, b) // random(a, b)
                },
                line,
            )
        }
    }

    /** [value] as a number, or a [ScriptError] naming what it is instead. */
    fun number(value: Any, line: Int): Double = value as? Double ?: throw ScriptError(line, "\"$value\" isn't a number")

    /** [value] as a text, or a [ScriptError]. */
    fun text(value: Any, line: Int): String = value as? String ?: throw ScriptError(line, "expected a text in quotes, found ${show(value)}")

    private fun finite(x: Double, line: Int): Double = if (x.isFinite()) x else throw ScriptError(line, "the number is too big")

    private fun truthy(value: Any) = if (value is Double) value != 0.0 else (value as String).isNotEmpty()

    private fun bool(b: Boolean) = if (b) 1.0 else 0.0

    private fun same(a: Any, b: Any) = if (a is Double && b is Double) abs(a - b) < 1e-9 else a == b

    /** [value] as text: whole numbers without decimals. */
    fun show(value: Any): String = if (value is Double) plainNumber(value) else value as String
}
