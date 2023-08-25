/*
 * LegacyApi.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.legacy

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import dev.mooner.starlight.R
import dev.mooner.starlight.api.original.NotificationApi
import dev.mooner.starlight.core.GlobalApplication
import dev.mooner.starlight.listener.NotificationListener
import dev.mooner.starlight.plugincore.Session.projectManager
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Callable

private val LOG = LoggerFactory.logger {  }

@Suppress("unused")
class LegacyApi: Api<LegacyApi.Api>() {

    override val name: String = "Api"

    override val objects: List<ApiObject> =
        getApiObjects<Api>()

    override val instanceClass: Class<Api> =
        Api::class.java

    override val instanceType: InstanceType =
        InstanceType.OBJECT

    override fun getInstance(project: Project): Any =
        Api(project)

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

        @JvmOverloads
        fun reload(throwOnError: Boolean = false): Boolean {
            var res = false
            for (project in projectManager.getProjects()) {
                val cRes = project.compile(throwOnError)
                if (!res && cRes)
                    res = true
            }
            return res
        }

        @JvmOverloads
        fun reload(scriptName: String, throwOnError: Boolean = false): Boolean {
            val mProject = if (scriptName == project.info.name)
                project
            else
                projectManager.getProject(scriptName)?: return false
            return mProject.compile(throwException = throwOnError)
        }

        @JvmOverloads
        fun compile(throwOnError: Boolean = false): Boolean =
            reload(throwOnError)

        @JvmOverloads
        fun compile(scriptName: String, throwOnError: Boolean = false): Boolean =
            reload(scriptName, throwOnError)

        fun prepare(scriptName: String): Int {
            val project = projectManager.getProject(scriptName)
                ?: return STATE_PROJECT_NOT_FOUND
            if (project.isCompiled)
                return STATE_ALREADY_COMPILED
            project.compile(throwException = true)
            return STATE_COMPILE_SUCCESS
        }

        fun unload(scriptName: String): Boolean {
            val project = projectManager.getProject(scriptName)
                ?: return false
            if (!project.isCompiled)
                return false
            project.destroy(requestUpdate = true)
            return true
        }

        fun off(): Boolean {
            for (project in projectManager.getEnabledProjects())
                project.setEnabled(false)
            return true
        }

        fun off(scriptName: String): Boolean {
            val project = projectManager.getProject(scriptName)
                ?: return false
            project.setEnabled(false)
            return true
        }

        fun on(): Boolean {
            for (project in projectManager.getProjects())
                project.setEnabled(true)
            return true
        }

        fun on(scriptName: String): Boolean {
            val project = projectManager.getProject(scriptName)
                ?: return false
            project.setEnabled(true)
            return true
        }

        fun isOn(scriptName: String): Boolean {
            val project = projectManager.getProject(scriptName)
                ?: return false
            return project.info.isEnabled
        }

        fun isCompiled(scriptName: String): Boolean {
            val project = projectManager.getProject(scriptName)
                ?: return false
            return project.isCompiled
        }

        fun isCompiling(scriptName: String): Boolean {
            /* TODO implement feature */
            return false
        }

        fun getScriptNames(): Array<String> {
            return projectManager.getProjects().map { it.info.name }.toTypedArray()
        }


        @JvmOverloads
        fun replyRoom(room: String, message: String, hideToast: Boolean = false): Boolean {
            val result = NotificationListener.sendTo(room, message)
            if (!hideToast && !result) {
                val context = GlobalApplication.requireContext()
                val content = context
                    .getString(R.string.api_cannot_send_to_room)
                    .format(room)

                Toast.makeText(context, content, Toast.LENGTH_LONG).show()
                LOG.warn { content }
            }
            return result
        }

        fun canReply(room: String): Boolean {
            return NotificationListener.hasRoom(room)
        }

        fun showToast(content: String, length: Int) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(GlobalApplication.requireContext(), content, length).show()
            }
        }

        /*
         * TODO: papagoTranslate?
         */

        @JvmOverloads
        fun makeNoti(title: String, content: String, id: Int? = null) {
            NotificationApi.NotificationBuilder(id ?: 0).apply {
                setTitle(title)
                setText(content)
            }.build()
        }

        fun gc() {
            LOG.warn {
                translate {
                    Locale.ENGLISH { "Java Virtual Machine automatically makes garbage collection call.\nPlease be sure on what you're doing." }
                    Locale.KOREAN  { "Java 가상 머신은 자동으로 가비지 컬렉션을 수행합니다.\n뭘 하고 계신건지 정확히 알고 실행해 주세요." }
                }
            }
            System.gc()
        }

        fun UIThread(func: Callable<Any>, onComplete: (error: Throwable?, result: Any?) -> Unit) {
            runBlocking {
                flow<Any> {
                    emit(func.call()) }
                    .flowOn(Dispatchers.Main)
                    .catch { e ->
                        onComplete(e, null)
                    }
                    .collect { result ->
                        onComplete(null, result)
                    }
            }
        }

        fun getActiveThreadsCount(): Int {
            return getActiveThreadsCount(project.info.name)
        }

        fun getActiveThreadsCount(scriptName: String): Int {
            val project = projectManager.getProject(scriptName)
                ?: return 0
            return project.activeJobs()
        }

        fun interruptThreads(): Boolean {
            return interruptThreads(project.info.name)
        }

        fun interruptThreads(scriptName: String): Boolean {
            val project = projectManager.getProject(scriptName)
                ?: return false
            project.stopAllJobs()
            return true
        }

        fun isTerminated(): Boolean {
            return isTerminated(project.info.name)
        }

        fun isTerminated(scriptName: String): Boolean {
            val project = projectManager.getProject(scriptName)
                ?: return false
            return project.activeJobs() == 0
        }

        @JvmOverloads
        fun markAsRead(room: String? = null, packageName: String? = null): Boolean {
            return if (room == null)
                NotificationListener.markAsRead()
            else
                NotificationListener.markAsRead(room)
        }
    }
}