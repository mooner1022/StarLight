package dev.mooner.starlight.api.unused

import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiFunction
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.JobLocker
import dev.mooner.starlight.plugincore.project.Project
import kotlin.concurrent.schedule

/*
 * TODO: 콜백 함수 구현 문제 해결. 각 언어별 Implementation?
 */

class TimerApi: Api<TimerApi.Timer>() {

    class Timer(
        private val project: Project
    ) {
        fun schedule(millis: Long, callback: Runnable): java.util.Timer {
            /*
             * locker.acquire -> run                   -> if(Timer.isRunning()) else -> locker.release()
             *                   |--   Timer.schedule() --|->       await          ->-|
             */

            val parentName = project.threadPoolName!!
            val key = JobLocker.withParent(parentName).requestLock()
            return java.util.Timer().apply {
                schedule(millis) {
                    callback.run()
                    JobLocker.withParent(parentName).requestRelease(key)
                }
            }
        }

        /*
         * 메모리 존나샘
         */
        /*
        fun schedule(initialDelay: Long, period: Long): java.util.Timer {
            /*
            val jobName = Thread.currentThread().name
            JobLocker.requestLock(jobName)
            return java.util.Timer().apply {
                schedule(initialDelay, period) {
                    //callback.accept(null)
                    println("timer called")
                }
            }

             */
            return java.util.Timer()
        }
         */
    }

    override val name: String = "Timer"
    override val instanceType: InstanceType = InstanceType.OBJECT
    override val objects: List<ApiFunction> = listOf(
        function {
            name = "schedule"
            args = arrayOf(Long::class.java, Runnable::class.java)
            returns = java.util.Timer::class.java
        }
    )
    override val instanceClass: Class<Timer> = Timer::class.java

    override fun getInstance(project: Project): Any = Timer(project)

}