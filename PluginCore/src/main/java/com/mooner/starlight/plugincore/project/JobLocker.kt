package com.mooner.starlight.plugincore.project

import com.mooner.starlight.plugincore.logger.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

object JobLocker {

    /*
     * lock -> 프로세스 실행 -> release 체크 -> locked 시 release 까지 기다림
     *
     */

    private const val T = "JobLocker"

    private val parents: ConcurrentMap<String, Parent> = ConcurrentHashMap()

    fun withParent(name: String): Parent {
        return if (parents.containsKey(name)) {
            parents[name]!!
        } else {
            val parent = Parent(name)
            parents[name] = parent
            parent
        }
    }

    class Parent(
        val name: String
    ) {

        private data class LockedJob(
            val job: Job,
            val listener: (Throwable?) -> Unit,
            var releaseCounter: Short = 0
        )

        private val runningJobs: ConcurrentMap<String, LockedJob> = ConcurrentHashMap()

        fun registerJob(
            key: String = Thread.currentThread().name,
            job: Job,
            onRelease: (Throwable?) -> Unit
        ): String {

            val data = LockedJob(
                job = job,
                listener = onRelease
            )
            if (!runningJobs.containsKey(key)) {
                runningJobs[key] = data
            }
            Logger.v(T, "Registered job $key")
            job.invokeOnCompletion {
                if (it != null) {
                    Logger.v(T, "Released job $key with exception")
                    runningJobs.remove(key)
                    onRelease(it)
                } else {
                    if (data.releaseCounter <= 0) {
                        runningJobs.remove(key)
                        onRelease(null)
                        Logger.v(T, "Released job $key")
                    } else {
                        Logger.v(T, "Postponed release of job $key")
                    }
                }
            }
            return key
        }

        fun requestLock(key: String = Thread.currentThread().name) {
            if (runningJobs.containsKey(key)) {
                runningJobs[key]!!.releaseCounter++
                Logger.v(T, "Locked job $key")
            } else {
                Logger.w(T, "Failed to lock job: job $key is not registered or already released")
            }
        }

        fun requestRelease(key: String = Thread.currentThread().name) {
            if (runningJobs.containsKey(key)) {
                var isReleased = false
                runningJobs[key]!!.also {
                    it.releaseCounter--
                    if (it.releaseCounter <= 0) {
                        it.listener(null)
                        isReleased = true
                    }
                }
                if (isReleased) {
                    runningJobs.remove(key)
                    Logger.v(T, "Released job $key")
                }
            } else {
                Logger.w(T, "Failed to release job: job $key is not registered or already released")
            }
        }

        fun forceRelease(key: String = Thread.currentThread().name) {
            if (runningJobs.containsKey(key)) {
                runningJobs[key]!!.also {
                    if (it.job.isActive) {
                        it.job.cancel("Job force released")
                    }
                    it.listener(ForceReleasedException("Job force released"))
                }
                runningJobs.remove(key)
                Logger.w(T, "Force released job $key, this might not be a normal behavior")
            }
        }

        fun activeJobs(): Int = runningJobs.size

        fun purge() {
            if (runningJobs.isNotEmpty()) {
                for ((_, data) in runningJobs) {
                    if (data.job.isActive) {
                        data.job.cancel("Job force released")
                    }
                    data.listener(ForceReleasedException("Job force released"))
                }
                runningJobs.clear()
                Logger.w(T, "Released all jobs of parent $name")
            }
        }

        /*
        fun addOnReleaseListener(key: String, listener: (Throwable?) -> Unit) {
            if (runningJobs.containsKey(key)) {
                onReleaseListeners[key]!! += listener
            } else {
                Logger.w("ThreadLocker", "Could not find job $key")
            }
        }
         */
    }
}