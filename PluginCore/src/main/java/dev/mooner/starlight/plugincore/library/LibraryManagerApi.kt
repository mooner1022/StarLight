/*
 * LibraryManagerApi.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.library

import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.Project

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
        return dev.mooner.starlight.plugincore.Session.libraryManager!!
    }
}