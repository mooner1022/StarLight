/*
 * ClassApi.kt created by Minki Moon(mooner1022) on 1/12/24, 3:01 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.original

import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.Project

class JavaClassApi: Api<JavaClassApi.Java>() {

    override val instanceClass: Class<Java> =
        Java::class.java

    override val instanceType: InstanceType =
        InstanceType.CLASS

    override val name: String =
        "Java"

    override val objects: List<ApiObject> =
        getApiObjects<Java>()

    override fun getInstance(project: Project): Any =
        Java::class.java

    class Java {

        companion object {
            @JvmStatic
            fun type(name: String): Class<*> {
                return Class.forName(name)
            }
        }
    }
}