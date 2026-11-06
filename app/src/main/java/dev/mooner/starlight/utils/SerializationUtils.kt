/*
 * SerializationUtils.kt created by Minki Moon(mooner1022) on 6/27/23, 12:18 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.utils

import kotlinx.serialization.json.Json


inline fun <reified T> Json.decodeIfNotBlank(string: String): T? {
    return if (string.isBlank())
        null
    else
        decodeFromString(string)
}