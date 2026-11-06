/*
 * EnvironmentApi.kt created by Minki Moon(mooner1022) on 6/27/23, 12:18 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.original

import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.utils.DotenvParser
import dev.mooner.starlight.utils.EnvMap
import java.io.File
import java.util.Collections

class EnvironmentApi: Api<EnvironmentApi.Environment>() {

    override val name: String =
        "Env"

    override val instanceType: InstanceType =
        InstanceType.OBJECT

    override val instanceClass: Class<Environment> =
        Environment::class.java

    override val objects: List<ApiObject> =
        getApiObjects<Environment>()

    override fun getInstance(project: Project): Any {
        var targetFile: File? = null
        for (fileName in ENV_FILE_NAME) {
            val file = project.directory.resolve(fileName)
            if (file.exists()) {
                targetFile = file
                break
            }
        }
        if (targetFile == null)
            return Environment(emptyMap())

        val parsedMap = try {
            val fileContent = targetFile.readText(Charsets.UTF_8)
            DotenvParser.parseString(fileContent)
        } catch (e: Exception) {
            project.logger.error(translate {
                Locale.ENGLISH { "Failed to parse environment file from path ${targetFile.path}: $e" }
                Locale.KOREAN  { "환경 변수 파일(${targetFile.path}) 로드 실패: $e" }
            })
            return Environment(emptyMap())
        }
        return Environment(parsedMap)
    }

    companion object {

        private val ENV_FILE_NAME = arrayOf(".env", ".ENV")
    }

    /**
     * The variables of the project's `.env` file, as a read-only map from names to string values.
     *
     * Read one as `Env.get("TOKEN")`, which gives `null` when it is missing, or as `Env.TOKEN` or `Env["TOKEN"]`. A
     * variable named like a member of the map, such as `size`, `keys`, `values`, `entries`, `empty` or `get`, clashes
     * with it: in NeonJS `Env.size` is the member, so read the variable with `get`; in Rhino it is the variable, which
     * also hides the member (with a variable named `get`, `Env.get(…)` fails). The map is read-only:
     * assigning to it throws, a `TypeError` in NeonJS and an `UnsupportedOperationException` in Rhino.
     *
     * The file is `.env`, or `.ENV`, in the project folder. It is read when the project compiles, so compile again
     * after editing it. When it can't be parsed, the project logs an error and `Env` is empty. The format:
     * ```
     * # A comment line.
     * TOKEN=abc123
     * NAME=StarBot
     * GREETING="Hello from ${NAME}"
     * RAW='kept as written: ${NAME}'
     * NOTE="""
     * several
     * lines
     * """
     * ```
     * - A name is letters, digits and `_`, not starting with a digit.
     * - An unquoted value ends at `#`, keeping the spaces before it. A quoted one can't contain its own quote.
     * - `${NAME}` takes another variable's value in unquoted, double-quoted and multi-line `"""` values, not in
     *   single-quoted ones or a one-line `"""…"""`.
     *   Naming a variable that doesn't exist is a parse error.
     * - A multi-line value starts with `"""` or `'''`; put the same three quotes alone on the line that ends it.
     *
     * @see guide:projects#files
     */
    class Environment(
        envMap: EnvMap,
    ): MutableMap<String, String> by Collections.unmodifiableMap(LinkedHashMap(envMap)) {

        // A MutableMap that can't change: NeonJS assigns through MutableMap and, given a read-only Kotlin map, fails on
        // the cast with an internal error. This gives both engines the same error, with its reason.
        override fun put(key: String, value: String): String? = throw readOnly()
        override fun putAll(from: Map<out String, String>) = throw readOnly()
        override fun remove(key: String): String? = throw readOnly()
        override fun clear() = throw readOnly()

        private fun readOnly() =
            UnsupportedOperationException("Env is read-only: change the .env file and compile the project again")

        companion object {

            // Demo API
            @JvmStatic
            fun parseFile(path: String): EnvMap =
                DotenvParser.parseString(File(path).readText())

            @JvmStatic
            fun parseString(content: String): EnvMap =
                DotenvParser.parseString(content)
        }
    }
}