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

    class Environment(
        envMap: EnvMap,
    ): Map<String, String> by envMap {

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