/*
 * RhinoConsts.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.languages.rhino

import org.mozilla.javascript.CompilerEnvirons
import org.mozilla.javascript.Parser
import org.mozilla.javascript.ast.ExpressionStatement
import org.mozilla.javascript.ast.Name
import org.mozilla.javascript.ast.VariableDeclaration

/**
 * The names [code] declares with a `const` at its top level, read as Rhino of [languageVersion] reads it. A script that
 * doesn't parse gives none: its compile fails anyway, and says why.
 *
 * Rhino refuses such a declaration when the global object already has the name ("redeclaration of var"), so the
 * compile leaves an API of that name out for the script.
 */
internal fun topLevelConsts(code: String, languageVersion: Int): Set<String> =
    try {
        val environment = CompilerEnvirons().apply { this.languageVersion = languageVersion }
        val root = Parser(environment).parse(code, "consts", 1)
        buildSet {
            for (node in root) {
                val declaration = node as? VariableDeclaration
                    ?: (node as? ExpressionStatement)?.expression as? VariableDeclaration
                    ?: continue
                if (declaration.isConst)
                    declaration.variables.mapNotNullTo(this) { (it.target as? Name)?.identifier }
            }
        }
    } catch (_: Exception) {
        emptySet()
    }
