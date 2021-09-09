package com.mooner.starlight.api.helper

class TimerMethod {

    companion object {
        fun schedule(millis: Long, callback: () -> Unit) {
            /*
            v8.locker.acquire -> run                   -> if(Timer.isRunning()) else -> v8.locker.reease()
                                 |--   Timer.schedule() --|->       await          ->-|
             */
        }
    }
}