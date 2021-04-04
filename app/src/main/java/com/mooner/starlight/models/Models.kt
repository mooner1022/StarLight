package com.mooner.starlight.models

data class Message (
        val message : String,
        val roomName: String,
        var viewType : Int
)