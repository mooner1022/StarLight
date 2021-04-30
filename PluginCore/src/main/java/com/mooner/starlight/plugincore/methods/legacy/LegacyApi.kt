package com.mooner.starlight.plugincore.methods.legacy

import android.content.Context

class LegacyApi {
    companion object {
        /*
        fun getContext(): Context {

        }

        fun reload(): Boolean {

        }

        fun reload(scriptName: String, throwOnError: Boolean = false) {

        }

        fun compile(): Boolean {

        }

        fun compile(scriptName: String, throwOnError: Boolean = false): Boolean {

        }
 */
        fun gc() {
            System.gc()
        }


    }
}