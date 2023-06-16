/*
 * ProjectLifecycleOwner.kt created by Minki Moon(mooner1022) on 4/22/23, 3:55 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.project.lifecycle

interface ProjectLifecycleOwner {

    fun getLifecycle(): ProjectLifecycle
}