package dev.mooner.starlight.api.original

import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiFunction
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.logger.ProjectLogger
import dev.mooner.starlight.plugincore.project.Project

@Suppress("unused")
class ProjectLoggerApi: Api<ProjectLogger>() {

    override val name: String = "Log"

    override val instanceType: InstanceType = InstanceType.OBJECT

    override val instanceClass: Class<ProjectLogger> = ProjectLogger::class.java
    
    override val objects: List<ApiFunction> = listOf(
        function {
            name = "i"
            args = arrayOf(String::class.java)
        },
        function {
            name = "i"
            args = arrayOf(String::class.java, String::class.java)
        },
        function {
            name = "e"
            args = arrayOf(String::class.java)
        },
        function {
            name = "e"
            args = arrayOf(String::class.java, String::class.java)
        },
        function {
            name = "w"
            args = arrayOf(String::class.java)
        },
        function {
            name = "e"
            args = arrayOf(String::class.java, String::class.java)
        },
        function {
            name = "d"
            args = arrayOf(String::class.java)
        },
        function {
            name = "d"
            args = arrayOf(String::class.java, String::class.java)
        },
        function {
            name = "v"
            args = arrayOf(String::class.java)
        },
        function {
            name = "v"
            args = arrayOf(String::class.java, String::class.java)
        },
        function {
            name = "wtf"
            args = arrayOf(String::class.java)
        },
        function {
            name = "wtf"
            args = arrayOf(String::class.java, String::class.java)
        },
    )

    override fun getInstance(project: Project): Any = project.logger
}