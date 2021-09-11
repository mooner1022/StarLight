package com.mooner.starlight.api.helper

import com.mooner.starlight.plugincore.method.Method
import com.mooner.starlight.plugincore.method.MethodFunction
import com.mooner.starlight.plugincore.method.MethodType
import com.mooner.starlight.plugincore.project.JobLocker
import com.mooner.starlight.plugincore.project.Project
import java.util.*
import java.util.function.Consumer
import kotlin.concurrent.schedule
import kotlin.reflect.jvm.internal.impl.load.kotlin.JvmType

class TimerMethod: Method<TimerMethod.Timer>() {

    class Timer {
        fun schedule(millis: Long, callback: Consumer<JvmType.Object?>): java.util.Timer {
            /*
            locker.acquire -> run                   -> if(Timer.isRunning()) else -> locker.reease()
                                |--   Timer.schedule() --|->       await          ->-|
             */
            val jobName = Thread.currentThread().name
            JobLocker.requestLock(jobName)
            return java.util.Timer().apply {
                schedule(millis) {
                    callback.accept(null)
                    JobLocker.requestRelease(jobName)
                }
            }
        }
    }

    override val name: String = "Timer"
    override val type: MethodType = MethodType.OBJECT
    override val functions: List<MethodFunction> = listOf(
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