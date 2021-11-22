package com.mooner.starlight.api.helper

import com.mooner.starlight.plugincore.api.Api
import com.mooner.starlight.plugincore.api.ApiFunction
import com.mooner.starlight.plugincore.api.InstanceType
import com.mooner.starlight.plugincore.project.JobLocker
import com.mooner.starlight.plugincore.project.Project
import kotlin.concurrent.schedule

class TimerApi: Api<TimerApi.Timer>() {

    interface TimerCallback {
        fun run()
    }

    class Timer {
        fun schedule(millis: Long, callback: TimerCallback): java.util.Timer {
            /*
            locker.acquire -> run                   -> if(Timer.isRunning()) else -> locker.reease()
                                |--   Timer.schedule() --|->       await          ->-|
             */

            val threadName = Thread.currentThread().name
            JobLocker.withParent(threadName).requestLock()
            return java.util.Timer().apply {
                schedule(millis) {
                    println("timer called")
                    callback.run()
                    JobLocker.withParent(threadName).requestRelease()
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
            args = arrayOf(Long::class.java, Function0::class.java)
            returns = java.util.Timer::class.java
        }
    )
    override val instanceClass: Class<Timer> = Timer::class.java

    override fun getInstance(project: Project): Any {
        return Timer()
    }

}