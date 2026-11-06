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

    /**
     * Facts about the app running the script: its name, version and uptime.
     *
     * They are static methods, such as `Platform.getVersion()`; NeonJS also reads them as properties
     * (`Platform.version`). The class isn't meant to be constructed.
     */
    class Platform {
        companion object {

            /** The app's name: always `StarLight`. */
            @JvmStatic
            fun getName(): String =
                "StarLight"

            /**
             * The version of the StarLight app, such as `1.0.1`, with the suffix of its build.
             *
             * Release builds add their build date, as in `1.0.1-261106`. `Unknown` if Android doesn't report a version.
             */
            @JvmStatic
            fun getVersion(): String =
                GlobalApplication
                    .requireContext()
                    .getPackageInfo()
                    .versionName ?: "Unknown"

            /** How long StarLight has been running, in milliseconds since it last started. */
            @JvmStatic
            fun getUptime(): Long =
                System.currentTimeMillis() - ApplicationSession.initMillis
        }
    }
}