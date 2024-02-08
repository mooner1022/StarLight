package dev.mooner.starlight.plugincore.project

import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.utils.currentThread
import dev.mooner.starlight.plugincore.utils.debugTranslated
import dev.mooner.starlight.plugincore.utils.infoTranslated
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.CountDownLatch

object JobLocker {

    /*
     * lock -> 프로세스 실행 -> release 체크 -> locked 시 release 까지 기다림
     *
     */

    private val LOG = LoggerFactory.logger {  }

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

            LOG.verbose { "Registered job $key" }
        }

        fun awaitRelease() {
            val key = defaultKey

            if (key !in runningJobs) {
                LOG.infoTranslated {
                    Locale.ENGLISH { "Failed to await for release: task $key is not registered or already released" }
                    Locale.KOREAN  { "작업 release를 기다리지 못함: 작업 $key 가 등록되지 않았거나 이미 release됨" }
                }
                return
            }
            val data = runningJobs[key]!!

            if (!data.canRelease) {
                LOG.verbose { "Postponed release of job $key" }
                data.latch.await()
            }
            runningJobs -= key
            LOG.verbose { "Released job $key" }
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
                LOG.verbose { "Locked job $key" }
            } else {
                LOG.infoTranslated {
                    Locale.ENGLISH { "Failed to lock job: job $key is not registered or already released" }
                    Locale.KOREAN  { "작업을 lock 하지 못함: 작업 $key 가 등록되지 않았거나 이미 release 됨" }
                }
            }
            return key
        }

        fun tryRelease(key: String = defaultKey) {
            if (key !in runningJobs)
                return
            requestRelease(key)
        }

        fun requestRelease(key: String = defaultKey) {
            if (key in runningJobs) {
                //var isReleased = false
                runningJobs[key]!!.also {
                    it.releaseCounter--
                    if (it.canRelease) {
                        LOG.verbose { "Releasing locked job $key" }
                        //it.listener(null)
                        it.latch.countDown()
                        //isReleased = true
                    }
                }
                //if (isReleased)
                //    runningJobs -= key
            } else {
                LOG.infoTranslated {
                    Locale.ENGLISH { "Failed to lock job: job $key is not registered or already released" }
                    Locale.KOREAN  { "작업을 lock 하지 못함: 작업 $key 가 등록되지 않았거나 이미 release 됨" }
                }
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
                //runningJobs -= key
                LOG.infoTranslated {
                    Locale.ENGLISH { "Force released job $key, this might not be a normal behavior" }
                    Locale.KOREAN  { "작업 $key 를 강제로 release 함, 이것은 정상적이거나 의도된 행동이 아닐 수 있습니다." }
                }
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
                LOG.debugTranslated {
                    Locale.ENGLISH { "Released all jobs of parent $name" }
                    Locale.KOREAN  { "부모 $name 의 모든 작업 release 완료" }
                }
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

fun JobLocker.withProject(project: Project): JobLocker.Parent =
    withParent(project.threadPoolName!!)