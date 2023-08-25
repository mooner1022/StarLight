/*
 * Constants.kt created by Minki Moon(mooner1022) on 6/27/23, 12:18 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight

const val PACKAGE_KAKAO_TALK = "com.kakao.talk"

const val WIDGET_DEF_STRING = """
    ["widget_uptime_default","widget_logs"]
"""

const val ID_VIEW_ITEM_PROJECT = 0
const val ID_VIEW_ITEM_LOG     = 1

const val CORE_PLUGIN_ID = "system"

// Config
const val CA_WIDGETS   = "widgets"
const val CA_PLUGIN    = "plugin"
const val CA_GENERAL   = "general"
const val CA_LEGACY    = "legacy"
const val CA_BETA_FEAT = "beta_features"
const val CA_DEV_MODE  = "dev_mode_config"

const val CF_IDS                = "ids"
const val CF_SAFE_MODE          = "safe_mode"
const val CF_WRITE_INTERNAL_LOG = "write_internal_log"
const val CF_LOAD_EXT_DEX_LIB   = "load_external_dex_libs"

sealed class Conf(
    val name: String
) {

    sealed class Widgets(
        val key: String
    ): Conf("widgets") companion object {

        const val IDS = "ids"
    }

    sealed class Plugin(
        val key: String
    ): Conf("plugin") {

        object SafeMode: Plugin("safe_mode")
    }
}