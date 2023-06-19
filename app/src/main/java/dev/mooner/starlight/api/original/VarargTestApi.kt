/*
 * VarargTestApi.kt created by Minki Moon(mooner1022) on 1/20/23, 6:18 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.original

import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.Project

class VarargTestApi: Api<VarargTestApi.VarargTest>() {

    override val name: String = "VarargTest"

    override val objects: List<ApiObject> =
        getApiObjects<VarargTest>()

    override val instanceClass: Class<VarargTest> =
        VarargTest::class.java

    override val instanceType: InstanceType =
        InstanceType.CLASS

    override fun getInstance(project: Project): Any =
        VarargTest::class.java

    class VarargTest {

        companion object {

            @JvmStatic
            fun test(vararg args: Any): String {
                return args.joinToString(" ") { it.toString() }
            }

            @JvmStatic
            fun addListener(eventName: String, listener: (Array<Any>) -> Unit) {
                listener(arrayOf(1, 2, 3))
            }
        }
    }
}