/*
 * RuntimeClassLoader.kt created by Minki Moon(mooner1022) on 6/27/23, 12:18 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore

class RuntimeClassLoader(
    parent: ClassLoader?
): ClassLoader(parent) {

    private val classes: MutableMap<String, Class<*>> = hashMapOf()

    override fun findClass(name: String): Class<*> {
        return findClass(name, checkPlugins = true)
    }

    fun findClass(name: String, checkPlugins: Boolean = true): Class<*> {
        val result: Class<*>? = classes[name]

        return (result ?: let {
            if (checkPlugins)
                Session.pluginLoader.getClass(name)
            else
                null
        } ?: let {
            super.findClass(name)
        } ?: throw ClassNotFoundException(name)).also {
            classes[name] = it
        }
    }

    override fun loadClass(name: String?): Class<*> {
        return super.loadClass(name)
    }
}