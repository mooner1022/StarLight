/*
 * PlatformApi.kt created by Minki Moon(mooner1022) on 3/5/24, 8:27 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.original

import dev.mooner.starlight.core.ApplicationSession
import dev.mooner.starlight.core.GlobalApplication
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.utils.getPackageInfo

class PlatformApi: Api<PlatformApi.Platform>() {

    override val name: String = "Platform"

    override val objects: List<ApiObject> =
        getApiObjects<Platform>()

    override val instanceClass: Class<Platform> =
        Platform::class.java

    override val instanceType: InstanceType =
        InstanceType.CLASS

    override fun getInstance(project: Project): Any =
        Platform::class.java

    class Platform {
        companion object {

            @JvmStatic
            fun getName(): String =
                "StarLight"

            @JvmStatic
            fun getVersion(): String =
                GlobalApplication
                    .requireContext()
                    .getPackageInfo()
                    .versionName ?: "Unknown"

            @JvmStatic
            fun getUptime(): Long =
                System.currentTimeMillis() - ApplicationSession.initMillis
        }
    }
}