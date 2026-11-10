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

    /**
     * Looks up Java classes by name; the `Java` global of Rhino projects.
     *
     * NeonJS projects don't get it: their `Java` global is NeonJS's own, with `Java.type()`, `Java.from()` and more.
     * The class isn't meant to be constructed.
     *
     * @see guide:engines/rhino#differences-from-neonjs
     * @see guide:engines/neonjs#java-access
     */
    class Java {

        companion object {
            /**
             * The Java class named [name], as a `java.lang.Class` object.
             *
             * In Rhino the result isn't a class to construct with `new` or to call static members on: reach those
             * through `Packages`, as in `Packages.java.util.ArrayList`. In the Google Play build, reflection on the
             * result, such as `newInstance()` and `getMethod()`, is blocked.
             *
             * @param name The class's binary name, such as `java.util.ArrayList`; nested classes take `$`, as in
             *   `android.os.Build$VERSION`.
             * @throws ClassNotFoundException If there is no class of that name.
             * @see guide:play-build#java-access-limits
             */
            @JvmStatic
            fun type(name: String): Class<*> {
                return Class.forName(name)
            }
        }
    }
}