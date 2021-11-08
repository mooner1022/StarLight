package com.mooner.starlight.models

data class DebugRoomMessage(
    val sender: String,
    val message: String,
    var viewType: Int
)