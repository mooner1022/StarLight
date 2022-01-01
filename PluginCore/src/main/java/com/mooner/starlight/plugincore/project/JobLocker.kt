package com.mooner.starlight.plugincore.project

import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.utils.currentThread
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.CountDownLatch

object JobLocker {

    /*
     * lock -> 프로세스 실행 -> release 체크 -> locked 시 release 까지 기다림
     *
     */

    private const val T = "JobLocker"

    private val parents: ConcurrentMap<String, Parent> = ConcurrentHashMap()

    fun withLock(parentName: String, task: () -> Unit) {
        val parent = withParent(parentName)
        parent.registerJob()
        task()
        parent.awaitRelease()
    }

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
        //internal val jobName: ThreadLocal<String> = ThreadLocal()
        private val defaultKey get() = currentThread.name

        private data class LockedJob(
            val latch: CountDownLatch = CountDownLatch(1),
            var releaseCounter: Short = 0
        ) {
            val canRelease get() = releaseCounter <= 0
        }

        private val runningJobs: ConcurrentMap<String, LockedJob> = ConcurrentHashMap()

        fun registerJob() {
            val data = LockedJob()
            val key = defaultKey
            runningJobs[key] = data

            Logger.v(T, "Registered job $key")
        }

        fun awaitRelease() {
            val key = defaultKey

            if (key !in runningJobs) {
                Logger.w("Failed to await for release: task $key is not registered or already released")
                return
            }
            val data = runningJobs[key]!!

            if (!data.canRelease) {
                Logger.v("Postponed release of job $key")
                data.latch.await()
            }
            Logger.v(T, "Released job $key")
        }

        /*
        fun registerJob(
            key: String = UUID.randomUUID().toString(),
            job: Job,
            onRelease: (Throwable?) -> Unit
        ): String {

            val data = LockedJob(
                job = job,
                listener = onRelease
            )
            if (key !in runningJobs) {
                runningJobs[key] = data
            }
            Logger.v(T, "Registered job $key")
            job.invokeOnCompletion {
                if (it != null) {
                    Logger.v(T, "Released job $key with exception")
                    runningJobs -= key
                    jobName.remove()
                    onRelease(it)
                } else {
                    if (data.releaseCounter <= 0) {
                        runningJobs -= key
                        jobName.remove()
                        onRelease(null)
                        Logger.v(T, "Released job $key")
                    } else {
                        Logger.v(T, "Postponed release of job $key")
                    }
                }
            }
            return key
        }
         */

        fun requestLock(key: String = defaultKey): String {
            if (key in runningJobs) {
                runningJobs[key]!!.releaseCounter++
                Logger.v(T, "Locked job $key")
            } else {
                Logger.w(T, "Failed to lock job: job $key is not registered or already released")
            }
            return key
        }

        fun requestRelease(key: String = defaultKey) {
            if (key in runningJobs) {
                var isReleased = false
                runningJobs[key]!!.also {
                    it.releaseCounter--
                    if (it.canRelease) {
                        Logger.v(T, "Releasing locked job $key")
                        //it.listener(null)
                        it.latch.countDown()
                        isReleased = true
                    }
                }
                if (isReleased)
                    runningJobs -= key
            } else {
                Logger.w(T, "Failed to release job: job $key is not registered or already released")
            }
        }

        fun forceRelease(key: String = defaultKey) {
            if (key in runningJobs) {
                runningJobs[key]!!.also {
                    it.latch.countDown()
                    /*
                    if (it.job.isActive) {
                        it.job.cancel("Job force released")
                    }
                    it.listener(ForceReleasedException("Job force released"))
                     */
                }
                runningJobs -= key
                Logger.w(T, "Force released job $key, this might not be a normal behavior")
            }
        }

        fun activeJobs(): Int = runningJobs.size

        fun purge() {
            if (runningJobs.isNotEmpty()) {
                for ((_, data) in runningJobs) {
                    data.latch.countDown()
                    /*
                    if (data.job.isActive) {
                        data.job.cancel("Job force released")
                    }
                    data.listener(ForceReleasedException("Job force released"))
                     */
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