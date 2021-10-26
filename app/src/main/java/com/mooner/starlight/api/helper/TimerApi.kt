package com.mooner.starlight.api.helper

import com.mooner.starlight.plugincore.api.Api
import com.mooner.starlight.plugincore.api.ApiFunction
import com.mooner.starlight.plugincore.api.InstanceType
import com.mooner.starlight.plugincore.project.JobLocker
import com.mooner.starlight.plugincore.project.Project
import java.util.function.Consumer
import kotlin.concurrent.schedule

class TimerApi: Api<TimerApi.Timer>() {

    class Timer {
        fun schedule(millis: Long): java.util.Timer {
            /*
            locker.acquire -> run                   -> if(Timer.isRunning()) else -> locker.reease()
                                |--   Timer.schedule() --|->       await          ->-|
             */
            val jobName = Thread.currentThread().name
            JobLocker.requestLock(jobName)
            return java.util.Timer().apply {
                schedule(millis) {
                    //callback.accept(null)
                    println("timer called")
                    JobLocker.requestRelease(jobName)
                }
            }
        }

        /*
         * 메모리 존나샘
         */
        fun schedule(initialDelay: Long, period: Long): java.util.Timer {
            val jobName = Thread.currentThread().name
            JobLocker.requestLock(jobName)
            return java.util.Timer().apply {
                schedule(initialDelay, period) {
                    //callback.accept(null)
                    println("timer called")
                }
            }
        }
    }

    override val name: String = "Timer"
    override val instanceType: InstanceType = InstanceType.OBJECT
    override val functions: List<ApiFunction> = listOf(
        function {
            name = "schedule"
            args = arrayOf(Long::class.java, Consumer::class.java)
            returns = java.util.Timer::class.java
        }
    )
    override val instanceClass: Class<Timer> = Timer::class.java

    override fun getInstance(project: Project): Any {
        return Timer()
    }

}