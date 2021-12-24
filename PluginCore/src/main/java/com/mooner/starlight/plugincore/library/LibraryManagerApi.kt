package com.mooner.starlight.plugincore.library

import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.api.Api
import com.mooner.starlight.plugincore.api.ApiObject
import com.mooner.starlight.plugincore.api.InstanceType
import com.mooner.starlight.plugincore.project.Project

class LibraryManagerApi: Api<LibraryManager>() {

    override val name: String = LibraryManager::class.simpleName!!

    override val objects: List<ApiObject> = LibraryManager::class.java.methods.map {
        function {
            name = it.name
            args = it.parameterTypes
            returns = it.returnType
        }
    }

    override val instanceClass: Class<LibraryManager> = LibraryManager::class.java

    override val instanceType: InstanceType = InstanceType.OBJECT

    override fun getInstance(project: Project): Any {
        return Session.libraryManager!!
    }
}