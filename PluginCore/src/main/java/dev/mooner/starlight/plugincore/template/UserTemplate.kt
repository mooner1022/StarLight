/*
 * UserTemplate.kt created by Minki Moon(mooner1022) on 22. 1. 2. 오후 8:45
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.template

data class UserTemplate(
    override val name: String,
    override val defaultCode: String
): Template()