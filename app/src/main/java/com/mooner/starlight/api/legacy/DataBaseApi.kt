package com.mooner.starlight.api.legacy

import com.mooner.starlight.plugincore.api.Api
import com.mooner.starlight.plugincore.api.ApiObject
import com.mooner.starlight.plugincore.api.InstanceType
import com.mooner.starlight.plugincore.project.Project
import java.io.File

class DataBaseApi: Api<DataBaseApi.DataBase>() {

    class DataBase(
        private val project: Project
    ) {

        private fun getFile(fileName: String): File = project.getDataDirectory().resolve(fileName)

        fun getDataBase(fileName: String): String = getFile(fileName).readText()

        fun setDataBase(fileName: String, data: String): String {
            getFile(fileName).writeText(data)
            return data
        }

        fun appendDataBase(fileName: String, data: String) = with(getFile(fileName)) {
            val org = readText()
            val appended = org + data
            writeText(appended)
            appended
        }

        fun removeDataBase(fileName: String): Boolean = getFile(fileName).delete()
    }

    override val name: String = "DataBase"

    override val objects: List<ApiObject> = listOf(
        function {
            name = "getDataBase"
            args = arrayOf(String::class.java)
            returns = String::class.java
        },
        function {
            name = "setDataBase"
            args = arrayOf(String::class.java, String::class.java)
            returns = String::class.java
        },
        function {
            name = "appendDataBase"
            args = arrayOf(String::class.java, String::class.java)
            returns = String::class.java
        },
        function {
            name = "removeDataBase"
            args = arrayOf(String::class.java, String::class.java)
            returns = Boolean::class.java
        }
    )

    override val instanceClass: Class<DataBase> = DataBase::class.java

    override val instanceType: InstanceType = InstanceType.OBJECT

    override fun getInstance(project: Project): Any = DataBase(project)
}