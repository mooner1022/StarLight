/*
 * EnvironmentApi.kt created by Minki Moon(mooner1022) on 6/27/23, 12:18 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.original

import dev.mooner.starlight.core.GlobalApplication
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.utils.getPackageInfo

class EnvironmentApi: Api<EnvironmentApi.Environment>() {

    override val name: String =
        "Env"

    override val instanceType: InstanceType =
        InstanceType.CLASS

    override val instanceClass: Class<Environment> =
        Environment::class.java

    override val objects: List<ApiObject> =
        getApiObjects<Environment>()

    override fun getInstance(project: Project): Any =
        Environment::class.java

    class Environment {

        companion object {

            @JvmStatic
            fun getRuntimeName(): String =
                "StarLight"

            @JvmStatic
            fun getRuntimeVersion(): String =
                GlobalApplication
                    .requireContext()
                    .getPackageInfo()
                    .versionName
        }
    }
}