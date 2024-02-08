/*
 * VersionChecker.kt created by Minki Moon(mooner1022) on 9/8/23, 7:54 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.utils

import dev.mooner.starlight.plugincore.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonNames
import org.jsoup.Jsoup

private typealias VersionMap = Map<VersionChecker.Channel, VersionChecker.VersionInfo>

class VersionChecker {

    fun fetchVersion(channel: Channel): VersionInfo? =
        fetchVersion().getOrNull()?.get(channel)

    private fun fetchVersion(): Result<VersionMap> {
        return runBlocking(Dispatchers.IO) {
            Jsoup.connect(VERSION_REMOTE_URL)
                .get()
                .text()
                .runCatching<String, VersionMap> {
                    let(Session.json::decodeFromString)
                }
                .onFailure {
                    it.printStackTrace()
                }
        }
    }

    companion object {
        private const val VERSION_REMOTE_URL = "https://raw.githubusercontent.com/mooner1022/starlight-version/master/version.json"
    }

    @Serializable
    data class VersionInfo(
        val version     : String,
        val versionCode : Int,
        val downloadUrl : String,
    )

    @Serializable
    @OptIn(ExperimentalSerializationApi::class)
    enum class Channel {
        @JsonNames("stable", "release")
        STABLE,
        @JsonNames("beta")
        BETA,
        @JsonNames("snapshot")
        SNAPSHOT,
    }
}