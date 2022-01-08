/*
 * LegacyApi.kt created by Minki Moon(mooner1022) on 22. 1. 8. 오후 7:23
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.legacy

import android.content.Context
import android.widget.Toast
import dev.mooner.starlight.core.GlobalApplication
import dev.mooner.starlight.listener.NotificationListener
import dev.mooner.starlight.plugincore.Session.projectManager
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.logger.Logger
import dev.mooner.starlight.plugincore.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Callable

class LegacyApi: Api<LegacyApi.Api>() {

    class Api(
        private val project: Project
    ) {

        companion object {
            const val STATE_PROJECT_NOT_FOUND = 0
            const val STATE_COMPILE_SUCCESS   = 1
            const val STATE_ALREADY_COMPILED  = 2
        }

        fun getContext(): Context {
            return GlobalApplication.requireContext()
        }

        fun reload(scriptName: String, throwOnError: Boolean = false): Boolean = compile(scriptName, throwOnError)

        fun compile(scriptName: String, throwOnError: Boolean = false): Boolean {
            val project = projectManager.getProject(scriptName)?: return false
            return project.compile(throwException = throwOnError)
        }

        fun prepare(scriptName: String): Int {
            val project = projectManager.getProject(scriptName)?: return STATE_PROJECT_NOT_FOUND
            if (project.isCompiled) return STATE_ALREADY_COMPILED
            project.compile(throwException = true)
            return STATE_COMPILE_SUCCESS
        }

        fun unload(scriptName: String): Boolean {
            val project = projectManager.getProject(scriptName)?: return false
            if (!project.isCompiled) return false
            project.destroy(requestUpdate = true)
            return true
        }

        fun off(): Boolean {
            for (project in projectManager.getEnabledProjects())
                project.setEnabled(false)
            return true
        }

        fun off(scriptName: String): Boolean {
            val project = projectManager.getProject(scriptName)?: return false
            project.setEnabled(false)
            return true
        }

        fun on(): Boolean {
            for (project in projectManager.getProjects()) {
                project.setEnabled(true)
            }
            return true
        }

        fun on(scriptName: String): Boolean {
            val project = projectManager.getProject(scriptName)?: return false
            project.setEnabled(true)
            return true
        }

        fun isOn(scriptName: String): Boolean {
            val project = projectManager.getProject(scriptName)?: return false
            return project.info.isEnabled
        }

        fun isCompiled(scriptName: String): Boolean {
            val project = projectManager.getProject(scriptName)?: return false
            return project.isCompiled
        }

        fun isCompiling(scriptName: String): Boolean {
            /* TODO implement feature */
            return false
        }

        fun getScriptNames(): Array<String> {
            return projectManager.getProjects().map { it.info.name }.toTypedArray()
        }

        fun replyRoom(room: String, message: String): Boolean {
            return replyRoom(room, message, false)
        }

        fun replyRoom(room: String, message: String, hideToast: Boolean = false): Boolean {
            val result = NotificationListener.sendTo(room, message)
            if (!hideToast && !result) Toast.makeText(GlobalApplication.requireContext(), "메세지가 수신되지 않은 방 '$room' 에 메세지를 보낼 수 없습니다.", Toast.LENGTH_LONG).show()
            return result
        }

        fun canReply(room: String): Boolean {
            return NotificationListener.hasRoom(room)
        }

        fun showToast(content: String, length: Int) {
            Toast.makeText(GlobalApplication.requireContext(), content, length).show()
        }

        /*
         * TODO: makeNoti, papagoTranslate
         */

        fun gc() {
            Logger.w("LegacyApi", "Java 가상 머신은 자동으로 가비지 컬렉션을 수행합니다.\n뭘 하고 계신건지 정확히 알고 실행해 주세요.")
            System.gc()
        }

        fun UIThread(func: Callable<Any>, onComplete: (error: Throwable?, result: Any?) -> Unit) {
            runBlocking {
                val flow = flow<Any> {
                    try {
                        emit(func.call())
                    } catch (e: Throwable) {
                        emit(e)
                    }
                }.flowOn(Dispatchers.Main)
                flow.collect { result ->
                    if (result is Throwable) {
                        onComplete(result, null)
                    } else {
                        onComplete(null, result)
                    }
                }
            }
        }

        fun getActiveThreadsCount(): Int {
            return getActiveThreadsCount(project.info.name)
        }

        fun getActiveThreadsCount(scriptName: String): Int {
            val project = projectManager.getProject(scriptName)?: return 0
            return project.activeJobs()
        }

        fun interruptThreads(): Boolean {
            return interruptThreads(project.info.name)
        }

        fun interruptThreads(scriptName: String): Boolean {
            val project = projectManager.getProject(scriptName)?: return false
            project.stopAllJobs()
            return true
        }

        fun isTerminated(): Boolean {
            return isTerminated(project.info.name)
        }

        fun isTerminated(scriptName: String): Boolean {
            val project = projectManager.getProject(scriptName)?: return false
            return project.activeJobs() == 0
        }

        fun markAsRead(room: String? = null, packageName: String? = null): Boolean {
            return if (room == null)
                NotificationListener.markAsRead()
            else
                NotificationListener.markAsRead(room)
        }
    }

    override val name: String = "Api"

    override val objects: List<ApiObject> = listOf(

    )

    override val instanceClass: Class<Api> = Api::class.java

    override val instanceType: InstanceType = InstanceType.OBJECT

    override fun getInstance(project: Project): Any = Api(project)
}