/*
 * EngineLinters.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.script.lint

import dev.mooner.neonjs.lint.Diagnostic
import dev.mooner.neonjs.lint.EngineGlobals
import dev.mooner.neonjs.lint.GlobalKind
import dev.mooner.neonjs.lint.Globals
import dev.mooner.neonjs.lint.LintOptions
import dev.mooner.neonjs.lint.NeonLint
import dev.mooner.neonjs.lint.RuleSetting
import dev.mooner.neonjs.lint.Severity
import dev.mooner.neonjs.lint.SourceType
import dev.mooner.starlight.languages.rhino.JSRhino
import dev.mooner.starlight.languages.rhino.RhinoGlobalObject
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.project.Project
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject

/**
 * The linters of the editor's JavaScript, all NeonJS's linter (`neonjs-lint`): it reads past syntax errors, knows the
 * names a project's scripts are given, and tells of mistakes that are valid code. This file is the only one that knows
 * that library.
 *
 * - Rhino: the rules as a script; but what is a syntax error is Rhino's own parser's to say ([RhinoSyntax]), which
 *   reads less than NeonJS and a few things NeonJS doesn't.
 * - Every other JavaScript, NeonJS's and a plugin's engine's as V8: a module of the language as its standard has it.
 *   For NeonJS the linter reads with the engine's own parser, so what it accepts compiles.
 */
internal object EngineLinters {

    const val RHINO_ID = "JS_RHINO"

    fun install() {
        ScriptLint.default = ScriptLint.Linter { request ->
            lintNeon(request.source, request.project?.let(::apiNames), request.isCancelled)
        }
        ScriptLint.register(RHINO_ID) { request ->
            val project = request.project
            val rhino = project?.let { runCatching { it.getLanguage() }.getOrNull() } as? JSRhino
            lintRhino(
                source = request.source,
                globals = project?.let { rhinoGlobals(rhino, it) + apiNames(it) },
                languageVersion = rhino?.configuredLanguageVersion() ?: Context.VERSION_ES6,
                isCancelled = request.isCancelled,
            )
        }
    }

    // ---- NeonJS -------------------------------------------------------------------------------------------------

    /** As JSNeon builds its engines: the web globals, `fetch` and `WebSocket`, `Java`; and what it adds itself. */
    private val neonGlobals: Globals =
        EngineGlobals.of(console = true, webGlobals = true, java = true, net = true) + Globals.of(listOf("toast"))

    /**
     * A file of a project that isn't Rhino's, which is a module.
     *
     * @param apis The names of the API objects the project's levels give its scripts; null for a file of no project,
     * which turns the check of names off.
     */
    fun lintNeon(source: String, apis: Collection<String>?, isCancelled: () -> Boolean = { false }): List<ScriptLint.Diagnostic> =
        NeonLint.lint(source, LintOptions(
            sourceType = SourceType.MODULE,
            globals = apis?.let { neonGlobals + Globals.of(it) },
            isCancelled = isCancelled,
        )).diagnostics.map(::convert)

    // ---- Rhino --------------------------------------------------------------------------------------------------

    /**
     * A Rhino project's file, a script.
     *
     * @param globals The names its scripts may use without declaring them; null turns the check of names off.
     * @param languageVersion Rhino's, as the project's scripts are compiled with.
     */
    fun lintRhino(
        source: String,
        globals: Collection<String>?,
        languageVersion: Int,
        isCancelled: () -> Boolean = { false },
    ): List<ScriptLint.Diagnostic> {
        val found = NeonLint.lint(source, LintOptions(
            sourceType = SourceType.SCRIPT,
            globals = globals?.let { Globals.of(it, GlobalKind.WRITABLE) },
            // Rhino has no temporal dead zone, and outside strict code it passes over an assignment to a const: both
            // are still mistakes, but nothing throws.
            rules = mapOf("const-assign" to RuleSetting.WARNING, "use-before-define" to RuleSetting.WARNING),
            isCancelled = isCancelled,
        )).diagnostics
        val (syntax, rules) = found.partition { it.code.startsWith("syntax/") }
        val result = rules.mapTo(ArrayList(), ::convert)

        // What doesn't parse is Rhino's to say.
        // - Where the linter found an error on the same line, its finding is shown: it names the mistake better.
        // - Where it found none there, the line is valid JavaScript that is newer than Rhino: the linter reads on past
        //   its errors, so a line it has no finding on did parse.
        // - Right after a line with an error of the linter's, Rhino's may only follow from that one: shown as Rhino
        //   words it.
        // - An error only the linter found is of something Rhino reads, as `for each`: not shown.
        val byLine = syntax.groupBy { RhinoSyntax.lineOf(source, it.start) }
        for (error in RhinoSyntax.errors(source, languageVersion)) {
            val line = RhinoSyntax.lineOf(source, error.start)
            val known = byLine[line]?.firstOrNull()
            if (known != null) {
                result += convert(known)
                continue
            }
            val code = if (line - 1 in byLine) LintMessages.RHINO_SYNTAX else LintMessages.RHINO_UNSUPPORTED
            result += ScriptLint.Diagnostic(
                error.start, error.end, ScriptLint.Severity.ERROR,
                LintMessages.text(code, emptyList(), error.message), code, listOf(error.message),
            )
        }
        return result.distinctBy { Triple(it.start, it.end, it.code) }.sortedBy { it.start }
    }

    /** The names of Rhino's global object for a project's scripts: the standard objects, Java's packages, the timers. */
    @Volatile
    private var rhinoScopeNames: Set<String>? = null

    private fun rhinoGlobals(rhino: JSRhino?, project: Project): Set<String> {
        rhinoScopeNames?.let { return it }
        if (rhino == null)
            return RHINO_FALLBACK
        val names = try {
            val context = rhino.enterContext()
            try {
                namesOf(context.initStandardObjects(RhinoGlobalObject(context, project)))
            } finally {
                Context.exit()
            }
        } catch (_: Throwable) {
            return RHINO_FALLBACK
        }
        return (names + RHINO_EXTRA).also { rhinoScopeNames = it }
    }

    /** Every name reachable as a global in [scope]: its own and its prototypes'. */
    fun namesOf(scope: Scriptable): Set<String> {
        val names = HashSet<String>()
        var current: Scriptable? = scope
        var depth = 0
        while (current != null && depth++ < 8) {
            val ids = (current as? ScriptableObject)?.allIds ?: current.ids
            ids.filterIsInstanceTo(names)
            current = current.prototype
        }
        return names
    }

    /** What JSRhino adds after the scope is made, and what a function has without declaring it. */
    private val RHINO_EXTRA = setOf("require", "module", "exports", "arguments", "undefined", "globalThis")

    /** For when the scope can't be asked: enough that common scripts aren't told of the engine's own names. */
    private val RHINO_FALLBACK = EngineGlobals.ECMASCRIPT + RHINO_EXTRA + setOf(
        "Packages", "java", "javax", "org", "com", "edu", "net", "android", "JavaImporter", "JavaAdapter", "importClass",
        "importPackage", "getClass", "toast", "setTimeout", "setInterval", "clearTimeout", "clearInterval",
    )

    // ---- Shared -------------------------------------------------------------------------------------------------

    private fun apiNames(project: Project): List<String> =
        runCatching { Session.apiManager.getApis(project).map { it.name } }.getOrDefault(emptyList())

    private fun convert(diagnostic: Diagnostic): ScriptLint.Diagnostic =
        ScriptLint.Diagnostic(
            start = diagnostic.start,
            end = diagnostic.end,
            severity = if (diagnostic.severity == Severity.ERROR) ScriptLint.Severity.ERROR else ScriptLint.Severity.WARNING,
            message = LintMessages.text(diagnostic.code, diagnostic.args, diagnostic.message),
            code = diagnostic.code,
            args = diagnostic.args,
        )
}
